package com.azusachino.latte.data.update

import android.content.Context
import com.azusachino.latte.data.network.OkHttpProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class UpdateDownloader(
    private val context: Context,
    private val client: OkHttpClient = OkHttpProvider.client,
) {
    fun download(updateInfo: UpdateInfo): Flow<DownloadProgress> = flow {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        // Keep only the newest installer: previous update APKs are dead weight once
        // PackageInstaller has copied them, so prune them before downloading a new one.
        updatesDir.listFiles()?.forEach { it.delete() }
        val targetFile = File(updatesDir, updateInfo.fileName)
        val tempFile = File(updatesDir, "${updateInfo.fileName}.tmp")

        try {
            val request = Request.Builder()
                .url(updateInfo.downloadUrl)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                emit(DownloadProgress.Failed(IllegalStateException("HTTP ${response.code} downloading update")))
                return@flow
            }

            val body = response.body ?: run {
                emit(DownloadProgress.Failed(IllegalStateException("Empty response body")))
                return@flow
            }

            val totalBytes = if (updateInfo.fileSize > 0) updateInfo.fileSize else body.contentLength()
            var bytesDownloaded = 0L

            body.byteStream().use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var lastEmittedBytes = 0L

                    emit(DownloadProgress.Downloading(0L, totalBytes))

                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                        bytesDownloaded += read

                        if (bytesDownloaded - lastEmittedBytes >= 65536 || bytesDownloaded == totalBytes) {
                            emit(DownloadProgress.Downloading(bytesDownloaded, totalBytes))
                            lastEmittedBytes = bytesDownloaded
                        }
                    }
                    outputStream.flush()
                }
            }

            if (tempFile.exists()) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
            }

            emit(DownloadProgress.Completed(targetFile))
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            emit(DownloadProgress.Failed(e))
        }
    }.flowOn(Dispatchers.IO)
}
