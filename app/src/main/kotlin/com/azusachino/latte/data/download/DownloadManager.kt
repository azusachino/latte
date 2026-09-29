package com.azusachino.latte.data.download

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.azusachino.latte.DownloadWorker
import com.azusachino.latte.data.model.MediaVariant
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.plugin.PlatformId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

sealed class DownloadResult {
    data class Started(val displayName: String) : DownloadResult()
    data class AlreadySaved(val displayName: String) : DownloadResult()
    data class AlreadyRunning(val displayName: String) : DownloadResult()
    data class Failed(val message: String) : DownloadResult()
}

data class BatchDownloadResult(
    val total: Int,
    val started: Int,
    val alreadySaved: Int,
    val alreadyRunning: Int,
    val failed: Int,
)

class DownloadManager(private val context: Context) {
    private val workManager = WorkManager.getInstance(context)

    suspend fun enqueueBatchDownload(posts: List<Post>): BatchDownloadResult =
        withContext(Dispatchers.IO) {
            var started = 0
            var alreadySaved = 0
            var alreadyRunning = 0
            var failed = 0
            for (post in posts) {
                when (enqueueDownload(post)) {
                    is DownloadResult.Started -> started++
                    is DownloadResult.AlreadySaved -> alreadySaved++
                    is DownloadResult.AlreadyRunning -> alreadyRunning++
                    is DownloadResult.Failed -> failed++
                }
            }
            BatchDownloadResult(
                total = posts.size,
                started = started,
                alreadySaved = alreadySaved,
                alreadyRunning = alreadyRunning,
                failed = failed,
            )
        }

    suspend fun enqueueDownload(post: Post, preferredVariant: MediaVariant? = null): DownloadResult =
        withContext(Dispatchers.IO) {
            val variant = preferredVariant ?: post.bestVariant
            val displayName = DownloadIdentity.displayName(post)
            val ext = DownloadIdentity.fileExtension(post)
            val mimeType = if (ext == "png") "image/png" else "image/jpeg"

            // 1. Check if already saved in MediaStore
            if (isAlreadySaved(displayName)) {
                return@withContext DownloadResult.AlreadySaved(displayName)
            }

            // 2. Check if already actively downloading in WorkManager
            val uniqueName = DownloadWorker.uniqueWorkName(displayName)
            val workInfos = runCatching {
                workManager.getWorkInfosForUniqueWork(uniqueName).get()
            }.getOrDefault(emptyList())

            if (workInfos.any { !it.state.isFinished }) {
                return@withContext DownloadResult.AlreadyRunning(displayName)
            }

            // 3. Enqueue download
            val headers = if (post.platform == PlatformId.PIXIV && (variant.url.contains("pximg.net") || post.originalUrl.contains("pximg.net"))) {
                mapOf("Referer" to "https://app-api.pixiv.net/")
            } else {
                emptyMap()
            }
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(
                    DownloadWorker.inputData(
                        sourceUrl = variant.url,
                        displayName = displayName,
                        mimeType = mimeType,
                        headers = headers,
                    )
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.SECONDS,
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            workManager.enqueueUniqueWork(
                uniqueName,
                ExistingWorkPolicy.KEEP,
                request,
            )

            DownloadResult.Started(displayName)
        }

    private fun isAlreadySaved(displayName: String): Boolean {
        val resolver: ContentResolver = context.contentResolver
        val album = DownloadWorker.album
        return resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND " +
                "${MediaStore.Images.Media.RELATIVE_PATH} = ? AND " +
                "${MediaStore.Images.Media.IS_PENDING} = ?",
            arrayOf(displayName, "$album/", "0"),
            null,
        )?.use { cursor ->
            cursor.moveToFirst()
        } ?: false
    }
}
