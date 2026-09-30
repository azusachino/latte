package com.azusachino.latte.data.update

import android.os.Build
import com.azusachino.latte.BuildConfig
import com.azusachino.latte.data.network.OkHttpProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class UpdateService(
    private val client: OkHttpClient = OkHttpProvider.client,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val repoOwner: String = "azusachino",
    private val repoName: String = "latte",
    private val baseUrl: String = "https://api.github.com",
) {
    private val metaCommentRegex = Regex("""<!--\s*latte-meta:\s*(\{.*?\})\s*-->""", RegexOption.DOT_MATCHES_ALL)

    suspend fun checkForUpdate(
        currentVersion: String = BuildConfig.VERSION_NAME,
        deviceSdk: Int = Build.VERSION.SDK_INT,
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val cleanBase = baseUrl.trimEnd('/')
            val url = "$cleanBase/repos/$repoOwner/$repoName/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Latte/$currentVersion")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    return@withContext UpdateCheckResult.UpToDate
                }
                return@withContext UpdateCheckResult.Error("GitHub API responded with HTTP ${response.code}")
            }

            val bodyStr = response.body?.string()
                ?: return@withContext UpdateCheckResult.Error("Empty response body from GitHub API")
            val release = json.decodeFromString<GitHubReleaseDto>(bodyStr)

            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: return@withContext UpdateCheckResult.Error("No APK asset found in the latest release")

            val candidateVersion = release.tagName.removePrefix("v").removePrefix("V")
            val curr = SemanticVersion.parse(currentVersion)
            val cand = SemanticVersion.parse(candidateVersion)

            val isNewer = if (curr != null && cand != null) {
                cand > curr
            } else {
                candidateVersion != currentVersion
            }

            if (isNewer) {
                val metadata = parseReleaseMetadata(release.body)
                val isDeviceSupported = if (deviceSdk > 0) deviceSdk >= metadata.minSdk else true

                val isMandatory = metadata.minSupportedVersion?.let { minVerStr ->
                    val minSupported = SemanticVersion.parse(minVerStr)
                    if (minSupported != null && curr != null) {
                        curr < minSupported
                    } else {
                        false
                    }
                } ?: false

                UpdateCheckResult.Available(
                    UpdateInfo(
                        versionName = candidateVersion,
                        tagName = release.tagName,
                        releaseTitle = release.name ?: release.tagName,
                        releaseNotes = cleanReleaseNotes(release.body),
                        downloadUrl = apkAsset.browserDownloadUrl,
                        fileName = apkAsset.name,
                        fileSize = apkAsset.size,
                        publishedAt = release.publishedAt.orEmpty(),
                        minSdk = metadata.minSdk,
                        minSupportedVersion = metadata.minSupportedVersion,
                        isMandatory = isMandatory,
                        isDeviceSupported = isDeviceSupported,
                    )
                )
            } else {
                UpdateCheckResult.UpToDate
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Failed to check for updates")
        }
    }

    internal fun parseReleaseMetadata(body: String?): ReleaseMetadata {
        if (body.isNullOrBlank()) return ReleaseMetadata()

        metaCommentRegex.find(body)?.groupValues?.get(1)?.let { jsonStr ->
            runCatching {
                return json.decodeFromString<ReleaseMetadata>(jsonStr)
            }
        }

        var minSdk = 29
        var minSupported: String? = null

        val minSdkMatch = Regex("""(?i)(?:min[-_]?sdk|requires android sdk)[:\s]+(\d+)""").find(body)
        if (minSdkMatch != null) {
            minSdk = minSdkMatch.groupValues[1].toIntOrNull() ?: 29
        }

        val minSupportedMatch = Regex("""(?i)(?:min(?:imum)?[-_]?supported(?:[-_]?version)?|requires app version)[:\s]+v?(\d+\.\d+(?:\.\d+)?)""").find(body)
        if (minSupportedMatch != null) {
            minSupported = minSupportedMatch.groupValues[1]
        }

        return ReleaseMetadata(minSdk = minSdk, minSupportedVersion = minSupported)
    }

    private fun cleanReleaseNotes(body: String?): String {
        if (body.isNullOrBlank()) return ""
        return body.replace(metaCommentRegex, "").trim()
    }
}
