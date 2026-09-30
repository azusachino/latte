package com.azusachino.latte.data.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GitHubAssetDto> = emptyList(),
)

@Serializable
data class GitHubAssetDto(
    val name: String,
    val size: Long = 0L,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    @SerialName("content_type") val contentType: String? = null,
)

@Serializable
data class ReleaseMetadata(
    @SerialName("min_sdk") val minSdk: Int = 29,
    @SerialName("min_supported_version") val minSupportedVersion: String? = null,
)

data class UpdateInfo(
    val versionName: String,
    val tagName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val fileName: String,
    val fileSize: Long,
    val publishedAt: String,
    val minSdk: Int = 29,
    val minSupportedVersion: String? = null,
    val isMandatory: Boolean = false,
    val isDeviceSupported: Boolean = true,
)

sealed interface UpdateCheckResult {
    data class Available(val updateInfo: UpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

sealed interface DownloadProgress {
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : DownloadProgress {
        val fraction: Float?
            get() = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else null
    }
    data class Completed(val file: File) : DownloadProgress
    data class Failed(val error: Throwable) : DownloadProgress
}
