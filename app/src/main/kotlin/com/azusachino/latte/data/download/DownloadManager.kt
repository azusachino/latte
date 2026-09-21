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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

sealed class DownloadResult {
    data class Started(val displayName: String) : DownloadResult()
    data class AlreadySaved(val displayName: String) : DownloadResult()
    data class AlreadyRunning(val displayName: String) : DownloadResult()
    data class Failed(val message: String) : DownloadResult()
}

class DownloadManager(private val context: Context) {
    private val workManager = WorkManager.getInstance(context)

    suspend fun enqueueDownload(post: Post, preferredVariant: MediaVariant? = null): DownloadResult =
        withContext(Dispatchers.IO) {
            val variant = preferredVariant ?: post.bestVariant
            val ext = variant.extension ?: if (variant.url.endsWith(".png", ignoreCase = true)) "png" else "jpg"
            val sourceLabel = if (post.siteId == "yande.re") "yande.re" else post.siteId
            val pageSuffix = if (post.pageCount > 1) " p${post.pageIndex + 1}" else ""
            val safeTags = (post.title ?: post.tags.take(4).joinToString(" "))
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val displayName = if (safeTags.isNotBlank()) {
                "$sourceLabel ${post.id}$pageSuffix $safeTags.$ext"
            } else {
                "$sourceLabel ${post.id}$pageSuffix.$ext"
            }
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
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(
                    DownloadWorker.inputData(
                        sourceUrl = variant.url,
                        displayName = displayName,
                        mimeType = mimeType,
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
