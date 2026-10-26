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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import com.azusachino.latte.data.download.DownloadFallback
import com.azusachino.latte.data.network.OkHttpProvider
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

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
                sourceUrls = listOf(inputData.getString(KEY_SOURCE_URL).orEmpty()) +
                    inputData.getStringArray(KEY_FALLBACK_URLS)?.toList().orEmpty(),
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
        sourceUrls: List<String>,
        displayName: String,
        mimeType: String,
    ): DownloadReceipt {
        val candidateUrls = sourceUrls.filter(String::isNotBlank).distinct()
        require(candidateUrls.isNotEmpty()) { "Image URL is empty" }
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

        downloadToMediaStore(uri, candidateUrls)

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

    private suspend fun downloadToMediaStore(uri: Uri, candidateUrls: List<String>) {
        val resolver = applicationContext.contentResolver
        val headersJson = inputData.getString(KEY_HEADERS)
        val headers = if (!headersJson.isNullOrBlank()) {
            val json = org.json.JSONObject(headersJson)
            json.keys().asSequence().associateWith { json.getString(it) }
        } else {
            emptyMap()
        }
        // A partial file may have been written by another source, so only a single-source work resumes it.
        var offset = if (candidateUrls.size == 1) pendingSize(resolver, uri) else 0L
        for ((index, sourceUrl) in candidateUrls.withIndex()) {
            try {
                // The server rejected the resume range; restart this source from byte 0.
                if (!copyFrom(resolver, uri, sourceUrl, headers, offset)) {
                    copyFrom(resolver, uri, sourceUrl, headers, offset = 0L)
                }
                return
            } catch (error: IOException) {
                val status = error as? HttpStatusException
                val shouldFallback = DownloadFallback.shouldFallback(
                    statusCode = status?.statusCode,
                    contentType = status?.contentType,
                    host = sourceUrl.toHttpUrl().host,
                    hasNextSource = index < candidateUrls.lastIndex,
                )
                if (!shouldFallback) throw error
                offset = 0L
            }
        }
    }

    /** Returns false when the server rejects the resume range, so the caller restarts from byte 0. */
    private suspend fun copyFrom(
        resolver: android.content.ContentResolver,
        uri: Uri,
        sourceUrl: String,
        headers: Map<String, String>,
        offset: Long,
    ): Boolean {
        val url = sourceUrl.toHttpUrl()
        require(url.scheme == "https") { "Image source must use HTTPS" }
        val request = Request.Builder()
            .url(url)
            .apply {
                headers.forEach { (k, v) -> header(k, v) }
                if (offset > 0) header("Range", "bytes=$offset-")
            }
            .build()
        OkHttpProvider.client.newCall(request).execute().use { response ->
            if (response.code == 416 && offset > 0) return false
            val contentType = response.header("Content-Type")
            if (!response.isSuccessful || !DownloadFallback.acceptsImage(url.host, contentType)) {
                val message = if (response.isSuccessful) {
                    "Image server returned ${contentType ?: "a non-image response"}"
                } else {
                    "Image server returned HTTP ${response.code}"
                }
                throw HttpStatusException(response.code, contentType, message)
            }
            val body = response.body ?: throw IOException("Image server returned an empty body")
            val append = offset > 0 && response.code == 206
            val start = if (append) offset else 0L
            val totalBytes = body.contentLength().takeIf { it > 0 }?.let { it + start } ?: -1L
            val outputMode = if (append) "wa" else "w"
            body.byteStream().use { input ->
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
        }
        return true
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
            .apply {
                loadNotificationThumbnail(receipt.contentUri)?.let { thumbnail ->
                    setLargeIcon(thumbnail)
                    setStyle(Notification.BigPictureStyle().bigPicture(thumbnail))
                }
            }
            .build()
        notify(resultNotificationId, notification)
    }

    private fun loadNotificationThumbnail(contentUri: String): Bitmap? {
        if (contentUri.isBlank()) return null
        val uri = Uri.parse(contentUri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        applicationContext.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 500 ||
            bounds.outHeight / sampleSize > 500
        ) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return applicationContext.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
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
        private const val KEY_HEADERS = "headers"
        private const val KEY_FALLBACK_URLS = "fallback_urls"
        private const val MAX_RETRY_COUNT = 2

        fun uniqueWorkName(displayName: String): String = "latte-download-$displayName"

        fun inputData(
            sourceUrl: String,
            displayName: String,
            mimeType: String,
            headers: Map<String, String> = emptyMap(),
            fallbackUrls: List<String> = emptyList(),
        ): Data = workDataOf(
            KEY_SOURCE_URL to sourceUrl,
            KEY_FALLBACK_URLS to fallbackUrls.toTypedArray(),
            KEY_DISPLAY_NAME to displayName,
            KEY_MIME_TYPE to mimeType,
            KEY_HEADERS to org.json.JSONObject(headers).toString(),
        )
    }
}

private class HttpStatusException(
    val statusCode: Int,
    val contentType: String?,
    message: String,
) : IOException(message)
