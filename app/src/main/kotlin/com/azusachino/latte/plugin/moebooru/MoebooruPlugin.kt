package com.azusachino.latte.plugin.moebooru

import com.azusachino.latte.data.network.SessionCookieStore
import com.azusachino.latte.data.network.moebooru.MoebooruApi
import com.azusachino.latte.plugin.AuthType
import com.azusachino.latte.plugin.PlatformId
import com.azusachino.latte.plugin.PluginFeedKind
import com.azusachino.latte.plugin.PluginFeedSource
import com.azusachino.latte.plugin.PluginFeedTab
import com.azusachino.latte.plugin.PluginPoolSource
import com.azusachino.latte.plugin.PluginSearchSupport
import com.azusachino.latte.plugin.SitePlugin
import com.azusachino.latte.plugin.storage.PluginStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

open class MoebooruPlugin(
    private val storage: PluginStorage,
    private val httpClient: OkHttpClient,
    private val baseUrl: String,
    override val platform: PlatformId,
    override val name: String,
    private val cookieJar: SessionCookieStore? = null,
) : SitePlugin {
    private val moebooruFeedSource: MoebooruFeedSource by lazy {
        MoebooruFeedSource(MoebooruApi(baseUrl, platform), ::getDisplayUsername)
    }
    override val feedSource: PluginFeedSource get() = moebooruFeedSource
    override val poolSource: PluginPoolSource get() = moebooruFeedSource

    override suspend fun searchSupport(query: String): PluginSearchSupport =
        PluginSearchSupport(
            suggestions = MoebooruApi(baseUrl, platform).getTagSuggestions(query),
        )
    override val feedTabs: List<PluginFeedTab> = listOf(
        PluginFeedTab("Popular", PluginFeedKind.POPULAR),
        PluginFeedTab("Newest", PluginFeedKind.NEWEST),
        PluginFeedTab("Favorites", PluginFeedKind.FAVORITES, requiresAuthentication = true),
        PluginFeedTab("Pools", PluginFeedKind.POOLS),
    )
    override val iconRes: Int? = null
    override val authType: AuthType = AuthType.CREDENTIALS
    private var cachedUsername: String? = storage.get(id, KEY_USERNAME)
    private val _isLoggedIn = MutableStateFlow(cachedUsername != null)
    override val isLoggedIn: Boolean get() = _isLoggedIn.value
    override val isLoggedInFlow: Flow<Boolean> = _isLoggedIn.asStateFlow()

    override fun getDisplayUsername(): String? = cachedUsername

    private var cachedCsrfToken: String? = null

    override suspend fun login(credentials: Map<String, String>): Result<Unit> = withContext(Dispatchers.IO) {
        val username = credentials["username"]?.trim()
            ?: return@withContext Result.failure(IllegalArgumentException("Username required"))
        val password = credentials["password"]
            ?: return@withContext Result.failure(IllegalArgumentException("Password required"))

        try {
            // 1. Fetch CSRF token from login page
            val csrf = fetchCsrfToken()
                ?: return@withContext Result.failure(IOException("Failed to retrieve CSRF token from $name"))

            val passHash = MoebooruPasswordHasher.hash(password)

            // 2. Submit login form
            val formBody = FormBody.Builder()
                .add("user[name]", username)
                .add("user[password]", password)
                .add("authenticity_token", csrf)
                .add("commit", "Login")
                .build()

            val request = Request.Builder()
                .url("$baseUrl/user/authenticate")
                .post(formBody)
                .header("X-CSRF-Token", csrf)
                .header("Referer", "$baseUrl/user/login")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 302 && response.code != 303) {
                    return@withContext Result.failure(IOException("Login failed with HTTP ${response.code}"))
                }
            }

            // A public user lookup cannot prove that these credentials are valid.
            // Only accept the authenticated session cookie set by the login response.
            val userId = cookieJar?.getCookieValue(baseUrl.toHttpUrl().host, "user_id")
            val hasSession = userId?.toLongOrNull()?.let { it > 0 } == true
            if (!hasSession) {
                return@withContext Result.failure(IllegalArgumentException("Invalid username or password"))
            }

            // 4. Persist
            storage.save(id, KEY_USERNAME, username)
            storage.save(id, KEY_PASS_HASH, passHash)
            cachedUsername = username
            _isLoggedIn.value = true

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private val userScores = mutableMapOf<Long, Int>()

    override fun getScore(postId: Long): Int? = userScores[postId]

    // yande.re's vote API has no "what's my existing score" lookup, only
    // POST /post/vote.json (which errors on a re-vote) -- but the favorite
    // (score 3) tier is separately exposed via the users who favorited a
    // post, so that's the one tier we can recover after login/restart.
    override suspend fun refreshScore(postId: Long): Int? = withContext(Dispatchers.IO) {
        val username = cachedUsername ?: return@withContext null
        try {
            val request = Request.Builder()
                .url("$baseUrl/favorite/list_users.json?id=$postId")
                .header("Accept", "application/json")
                .build()

            val body = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string().orEmpty()
            }
            val favoritedUsers = FAVORITED_USERS_REGEX.find(body)
                ?.groupValues?.get(1)
                ?.split(",")
                ?: emptyList()

            if (favoritedUsers.any { it.equals(username, ignoreCase = true) }) {
                userScores[postId] = 3
                3
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun logout() {
        storage.clearPlugin(id)
        cachedUsername = null
        cachedCsrfToken = null
        userScores.clear()
        cookieJar?.clear(baseUrl.toHttpUrl().host)
        _isLoggedIn.value = false
    }

    override suspend fun setScore(postId: Long, score: Int): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isLoggedIn) {
            return@withContext Result.failure(IllegalStateException("Must be logged in to rate posts"))
        }

        try {
            val csrf = cachedCsrfToken ?: fetchCsrfToken()
                ?: return@withContext Result.failure(IOException("Failed to retrieve CSRF token"))

            val formBody = FormBody.Builder()
                .add("id", postId.toString())
                .add("score", score.toString())
                .build()

            val request = Request.Builder()
                .url("$baseUrl/post/vote.json")
                .post(formBody)
                .header("X-CSRF-Token", csrf)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "$baseUrl/post/show/$postId")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    if (score == 0) {
                        userScores.remove(postId)
                    } else {
                        userScores[postId] = score
                    }
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("Vote failed with HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchCsrfToken(): String? {
        val request = Request.Builder()
            .url("$baseUrl/user/login")
            .header("Accept", "text/html")
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val html = response.body?.string() ?: return null
                extractCsrfToken(html).also { cachedCsrfToken = it }
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val KEY_USERNAME = "username"
        private const val KEY_PASS_HASH = "pass_hash"

        private val CSRF_REGEX_1 = Regex("""<meta[^>]*name=["']csrf-token["'][^>]*content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val CSRF_REGEX_2 = Regex("""<meta[^>]*content=["']([^"']+)["'][^>]*name=["']csrf-token["']""", RegexOption.IGNORE_CASE)
        private val FAVORITED_USERS_REGEX = Regex(""""favorited_users"\s*:\s*"([^"]*)"""")

        fun extractCsrfToken(html: String): String? {
            return CSRF_REGEX_1.find(html)?.groupValues?.get(1)
                ?: CSRF_REGEX_2.find(html)?.groupValues?.get(1)
        }
    }
}
