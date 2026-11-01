package com.azusachino.latte.data.download

import android.content.Context
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
            DownloadWorker.pruneStaleNotifications(context)
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
            DownloadWorker.pruneStaleNotifications(context)
            val candidates = DownloadFallback.candidateUrls(post, preferredVariant)
            val displayName = DownloadIdentity.fileName(post)
            val albumPath = DownloadIdentity.albumPath(post.platform)
            val ext = DownloadIdentity.fileExtension(post)
            val mimeType = if (ext == "png") "image/png" else "image/jpeg"

            // 1. Dedup against the saved-library keyset: keys are parsed from
            // the stored file names themselves, so the set is always
            // rebuildable and O(1) to query. Reload from Settings if files
            // were deleted outside the app.
            DownloadLibrary.ensureLoaded(context)
            if (DownloadLibrary.isSaved(post)) {
                return@withContext DownloadResult.AlreadySaved(displayName)
            }

            // 2. Check if already actively downloading in WorkManager
            val uniqueName = DownloadWorker.uniqueWorkName(displayName)
            val workInfos = runCatching {
                workManager.getWorkInfosForUniqueWork(uniqueName).get()
            }.getOrDefault(emptyList())

            if (workInfos.any {
                it.state == WorkInfo.State.RUNNING ||
                    it.state == WorkInfo.State.ENQUEUED ||
                    it.state == WorkInfo.State.BLOCKED
            }) {
                return@withContext DownloadResult.AlreadyRunning(displayName)
            }

            // 3. Enqueue download
            val headers = if (post.platform == PlatformId.PIXIV && (candidates.any { it.contains("pximg.net") } || post.originalUrl.contains("pximg.net"))) {
                mapOf("Referer" to "https://app-api.pixiv.net/")
            } else {
                emptyMap()
            }
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(
                    DownloadWorker.inputData(
                        sourceUrl = candidates.first(),
                        displayName = displayName,
                        albumPath = albumPath,
                        mimeType = mimeType,
                        headers = headers,
                        fallbackUrls = candidates.drop(1),
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
                ExistingWorkPolicy.REPLACE,
                request,
            )

            DownloadResult.Started(displayName)
        }
}
