package com.azusachino.latte

import android.content.ContentUris
import android.content.ContentValues
import android.provider.MediaStore
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val channelName = "com.azusachino.latte/download"
    private val album = "Pictures/Latte"

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

                Thread {
                    try {
                        val receipt = saveImage(sourceUrl, displayName, mimeType)
                        runOnUiThread { result.success(receipt) }
                    } catch (error: Exception) {
                        runOnUiThread {
                            result.error("save_failed", error.message ?: "Image save failed", null)
                        }
                    }
                }.start()
            }
    }

    private fun saveImage(sourceUrl: String, displayName: String, mimeType: String): Map<String, String> {
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
                    input.copyTo(output)
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
