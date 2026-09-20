package com.azusachino.latte

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val channelName = "com.azusachino.latte/download"
    private val album = "Pictures/Latte"
    private val notificationChannelId = "latte_downloads"
    private val notificationGroup = "latte_downloads"
    private val notificationPermissionRequestCode = 4001

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        createDownloadNotificationChannel()
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
            .setMethodCallHandler { call, result ->
                if (call.method != "saveImage") {
                    result.notImplemented()
                    return@setMethodCallHandler
                }

                val sourceUrl = call.argument<String>("sourceUrl")
                val displayName = call.argument<String>("displayName")
                val mimeType = call.argument<String>("mimeType")
                if (sourceUrl == null || displayName == null || mimeType == null) {
                    result.error("invalid_request", "Download arguments are incomplete", null)
                    return@setMethodCallHandler
                }

                requestNotificationPermission()
                val notificationId = displayName.hashCode() and Int.MAX_VALUE
                showDownloadStarted(notificationId, displayName)
                Thread {
                    try {
                        val receipt = saveImage(sourceUrl, displayName, mimeType, notificationId)
                        showDownloadResult(notificationId, receipt, mimeType)
                        runOnUiThread { result.success(receipt) }
                    } catch (error: Exception) {
                        showDownloadFailure(
                            notificationId,
                            displayName,
                            error.message ?: "Image save failed",
                        )
                        runOnUiThread {
                            result.error("save_failed", error.message ?: "Image save failed", null)
                        }
                    }
                }.start()
            }
    }

    private fun createDownloadNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                notificationChannelId,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Latte image downloads"
            },
        )
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                notificationPermissionRequestCode,
            )
        }
    }

    private fun notificationBuilder(
        notificationId: Int,
        title: String,
        text: String,
        contentUri: String? = null,
        mimeType: String? = null,
    ): Notification.Builder {
        val contentIntent = if (!contentUri.isNullOrBlank() && !mimeType.isNullOrBlank()) {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(contentUri), mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(this, MainActivity::class.java)
        }
        return Notification.Builder(this, notificationChannelId)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    notificationId,
                    contentIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setGroup(notificationGroup)
            .setOnlyAlertOnce(true)
    }

    private fun showDownloadStarted(notificationId: Int, displayName: String) {
        val notification = notificationBuilder(notificationId, "Downloading image", displayName)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
        getSystemService(NotificationManager::class.java).notify(notificationId, notification)
    }

    private fun showDownloadProgress(
        notificationId: Int,
        displayName: String,
        bytesCopied: Long,
        totalBytes: Long,
    ) {
        val hasTotal = totalBytes > 0
        val progress = if (hasTotal) {
            (bytesCopied.toDouble() / totalBytes * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }
        val notification = notificationBuilder(notificationId, "Downloading image", displayName)
            .setOngoing(true)
            .setProgress(100, progress, !hasTotal)
            .build()
        getSystemService(NotificationManager::class.java).notify(notificationId, notification)
    }

    private fun showDownloadResult(
        notificationId: Int,
        receipt: Map<String, String>,
        mimeType: String,
    ) {
        val alreadySaved = receipt["status"] == "already_saved"
        val displayName = receipt["displayName"] ?: "Latte image"
        val savedAlbum = receipt["album"] ?: album
        val title = if (alreadySaved) "Image already saved" else "Image saved"
        val text = if (alreadySaved) {
            "Already saved: $displayName · $savedAlbum"
        } else {
            "$displayName · $savedAlbum"
        }
        val notification = notificationBuilder(
            notificationId,
            title,
            text,
            receipt["contentUri"],
            mimeType,
        )
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(notificationId, notification)
    }

    private fun showDownloadFailure(notificationId: Int, displayName: String, message: String) {
        val notification = notificationBuilder(
            notificationId,
            "Image download failed",
            "$displayName · $message",
        )
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(notificationId, notification)
    }

    private fun saveImage(
        sourceUrl: String,
        displayName: String,
        mimeType: String,
        notificationId: Int,
    ): Map<String, String> {
        val resolver = contentResolver
        val existing = resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND " +
                "${MediaStore.Images.Media.RELATIVE_PATH} = ? AND " +
                "${MediaStore.Images.Media.IS_PENDING} = 0",
            arrayOf(displayName, "$album/"),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)),
                ).toString()
            } else {
                null
            }
        }
        if (existing != null) {
            return mapOf(
                "status" to "already_saved",
                "contentUri" to existing,
                "album" to album,
                "displayName" to displayName,
            )
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "$album/")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Latte could not create a MediaStore item")
        try {
            val connection = java.net.URL(sourceUrl).openConnection().apply {
                connectTimeout = 8_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", "Latte/1.0")
            }
            connection.getInputStream().use { input ->
                resolver.openOutputStream(uri)?.use { output ->
                    val buffer = ByteArray(16 * 1024)
                    val totalBytes = connection.contentLengthLong
                    var bytesCopied = 0L
                    var lastUpdateNanos = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        bytesCopied += count
                        val now = System.nanoTime()
                        if (
                            bytesCopied == totalBytes ||
                            now - lastUpdateNanos >= 250_000_000L
                        ) {
                            showDownloadProgress(
                                notificationId,
                                displayName,
                                bytesCopied,
                                totalBytes,
                            )
                            lastUpdateNanos = now
                        }
                    }
                } ?: error("Latte could not open the MediaStore item")
            }
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null,
            )
            return mapOf(
                "status" to "completed",
                "contentUri" to uri.toString(),
                "album" to album,
                "displayName" to displayName,
            )
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
}
