package com.azusachino.latte.data.update

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
    suspend fun checkForUpdate(currentVersion: String = BuildConfig.VERSION_NAME): UpdateCheckResult =
        withContext(Dispatchers.IO) {
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
                    UpdateCheckResult.Available(
                        UpdateInfo(
                            versionName = candidateVersion,
                            tagName = release.tagName,
                            releaseTitle = release.name ?: release.tagName,
                            releaseNotes = release.body.orEmpty(),
                            downloadUrl = apkAsset.browserDownloadUrl,
                            fileName = apkAsset.name,
                            fileSize = apkAsset.size,
                            publishedAt = release.publishedAt.orEmpty(),
                        )
                    )
                } else {
                    UpdateCheckResult.UpToDate
                }
            } catch (e: Exception) {
                UpdateCheckResult.Error(e.message ?: "Failed to check for updates")
            }
        }
}
