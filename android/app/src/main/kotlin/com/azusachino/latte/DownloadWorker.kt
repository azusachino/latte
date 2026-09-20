package com.azusachino.latte

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class DownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    private val displayName = inputData.getString(KEY_DISPLAY_NAME).orEmpty()
    private val notificationId = displayName.hashCode() and Int.MAX_VALUE
    private val resultNotificationId = notificationId xor RESULT_NOTIFICATION_MASK

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        return ForegroundInfo(
            notificationId,
            notificationBuilder("Downloading image", displayName)
                .setOngoing(true)
                .setProgress(0, 0, true)
                .build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (displayName.isBlank()) return@withContext Result.failure()
        createNotificationChannel()
        setForeground(getForegroundInfo())

        try {
            val receipt = saveImage(
                sourceUrl = inputData.getString(KEY_SOURCE_URL).orEmpty(),
                displayName = displayName,
                mimeType = inputData.getString(KEY_MIME_TYPE).orEmpty(),
            )
            showDownloadResult(receipt)
            Result.success(
                workDataOf(
                    "status" to receipt.status,
                    "contentUri" to receipt.contentUri,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            if (runAttemptCount < MAX_RETRY_COUNT) {
                Result.retry()
            } else {
                showDownloadFailure(error.message ?: "Image save failed")
                Result.failure()
            }
        } catch (error: Exception) {
            showDownloadFailure(error.message ?: "Image save failed")
            Result.failure()
        }
    }

    private suspend fun saveImage(
        sourceUrl: String,
        displayName: String,
        mimeType: String,
    ): DownloadReceipt {
        require(sourceUrl.isNotBlank()) { "Image URL is empty" }
        require(mimeType.isNotBlank()) { "Image MIME type is empty" }

        val resolver = applicationContext.contentResolver
        val existing = findMediaStoreItem(resolver, displayName, pending = false)
        if (existing != null) {
            return DownloadReceipt(status = "already_saved", contentUri = existing.uri.toString())
        }

        val pending = findMediaStoreItem(resolver, displayName, pending = true)
        val uri = pending?.uri ?: resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, "$album/")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            },
        ) ?: error("Latte could not create a MediaStore item")

        downloadToMediaStore(uri, sourceUrl)

        // Close the race with another worker that published the same identity
        // while this worker was transferring bytes.
        val collision = findMediaStoreItem(resolver, displayName, pending = false)
        if (collision != null) {
            resolver.delete(uri, null, null)
            return DownloadReceipt(status = "already_saved", contentUri = collision.uri.toString())
        }
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
            null,
            null,
        )
        return DownloadReceipt(status = "completed", contentUri = uri.toString())
    }

    private suspend fun downloadToMediaStore(uri: Uri, sourceUrl: String) {
        val resolver = applicationContext.contentResolver
        val url = URL(sourceUrl)
        require(url.protocol == "https") { "Image source must use HTTPS" }
        var offset = pendingSize(resolver, uri)

        while (true) {
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", "Latte/1.0")
                if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
            }
            try {
                val responseCode = connection.responseCode
                if (responseCode == 416 && offset > 0) {
                    offset = 0
                    continue
                }
                val append = offset > 0 && responseCode == HttpURLConnection.HTTP_PARTIAL
                if (responseCode !in 200..299) {
                    throw IOException("Image server returned HTTP $responseCode")
                }
                val start = if (append) offset else 0L
                val totalBytes = connection.contentLengthLong.takeIf { it > 0 }?.let { it + start } ?: -1L
                val outputMode = if (append) "wa" else "w"
                connection.inputStream.use { input ->
                    resolver.openOutputStream(uri, outputMode)?.use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var bytesCopied = start
                        var lastUpdateNanos = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            bytesCopied += count
                            val now = System.nanoTime()
                            if (
                                bytesCopied == totalBytes ||
                                now - lastUpdateNanos >= 250_000_000L
                            ) {
                                showDownloadProgress(bytesCopied, totalBytes)
                                setProgress(
                                    workDataOf(
                                        "bytesCopied" to bytesCopied,
                                        "totalBytes" to totalBytes,
                                    ),
                                )
                                lastUpdateNanos = now
                            }
                        }
                    } ?: error("Latte could not open the MediaStore item")
                }
                return
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun pendingSize(resolver: android.content.ContentResolver, uri: Uri): Long =
        resolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))
            } else {
                0L
            }
        } ?: 0L

    private fun findMediaStoreItem(
        resolver: android.content.ContentResolver,
        displayName: String,
        pending: Boolean,
    ): MediaStoreItem? = resolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Images.Media._ID, MediaStore.MediaColumns.SIZE),
        "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND " +
            "${MediaStore.Images.Media.RELATIVE_PATH} = ? AND " +
            "${MediaStore.Images.Media.IS_PENDING} = ?",
        arrayOf(displayName, "$album/", if (pending) "1" else "0"),
        null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        MediaStoreItem(
            uri = ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)),
            ),
            size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)),
        )
    }

    private data class MediaStoreItem(
        val uri: Uri,
        val size: Long,
    )

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        applicationContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                    notificationChannelId,
                    "Downloads",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Latte image downloads"
                },
            )
    }

    private fun notificationBuilder(title: String, text: String): Notification.Builder {
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(applicationContext, notificationChannelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setGroup(notificationGroup)
            .setOnlyAlertOnce(true)
    }

    private fun showDownloadProgress(bytesCopied: Long, totalBytes: Long) {
        val hasTotal = totalBytes > 0
        val progress = if (hasTotal) {
            (bytesCopied.toDouble() / totalBytes * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }
        val notification = notificationBuilder("Downloading image", displayName)
            .setOngoing(true)
            .setProgress(100, progress, !hasTotal)
            .build()
        notify(notification)
    }

    private fun showDownloadResult(receipt: DownloadReceipt) {
        val alreadySaved = receipt.status == "already_saved"
        val title = if (alreadySaved) "Image already saved" else "Image saved"
        val text = if (alreadySaved) {
            "Already saved: $displayName · $album"
        } else {
            "$displayName · $album"
        }
        val intent = if (receipt.contentUri.isBlank()) {
            Intent(applicationContext, MainActivity::class.java)
        } else {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(receipt.contentUri), inputData.getString(KEY_MIME_TYPE))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            resultNotificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = notificationBuilder(title, text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notify(resultNotificationId, notification)
    }

    private fun showDownloadFailure(message: String) {
        val notification = notificationBuilder(
            "Image download failed",
            "$displayName · $message",
        )
            .setAutoCancel(true)
            .build()
        notify(resultNotificationId, notification)
    }

    private fun notify(notification: Notification) {
        notify(notificationId, notification)
    }

    private fun notify(id: Int, notification: Notification) {
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(id, notification)
    }

    private data class DownloadReceipt(
        val status: String,
        val contentUri: String,
    )

    companion object {
        const val album = "Pictures/Latte"
        private const val notificationChannelId = "latte_downloads"
        private const val notificationGroup = "latte_downloads"
        private const val RESULT_NOTIFICATION_MASK = 0x40000000
        private const val KEY_SOURCE_URL = "source_url"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_MIME_TYPE = "mime_type"
        private const val MAX_RETRY_COUNT = 2

        fun uniqueWorkName(displayName: String): String = "latte-download-$displayName"

        fun inputData(
            sourceUrl: String,
            displayName: String,
            mimeType: String,
        ): Data = workDataOf(
            KEY_SOURCE_URL to sourceUrl,
            KEY_DISPLAY_NAME to displayName,
            KEY_MIME_TYPE to mimeType,
        )
    }
}
