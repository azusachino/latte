package com.azusachino.latte.plugin.pixiv

import com.azusachino.latte.data.network.PixivApi
import com.azusachino.latte.data.network.PixivBookmarkResult
import com.azusachino.latte.data.network.PixivOAuthClient
import com.azusachino.latte.data.network.PixivAuthorizationRequest
import com.azusachino.latte.data.network.PixivSession
import com.azusachino.latte.data.network.PixivSupportResult
import com.azusachino.latte.data.network.PixivUserDetailResult
import com.azusachino.latte.plugin.PluginFeedKind
import com.azusachino.latte.plugin.PluginFeedSource
import com.azusachino.latte.plugin.PluginFeedTab
import com.azusachino.latte.plugin.PluginSearchSupport
import com.azusachino.latte.plugin.AuthType
import com.azusachino.latte.plugin.PlatformId
import com.azusachino.latte.plugin.SitePlugin
import com.azusachino.latte.plugin.storage.PluginStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class PixivPlugin(
    private val storage: PluginStorage,
    httpClient: OkHttpClient,
    private val apiBaseUrl: String = PlatformId.PIXIV.apiUrl,
    private val oauthClient: PixivOAuthClient? = null,
) : SitePlugin {
    override val platform: PlatformId = PlatformId.PIXIV
    override val name: String = "Pixiv"
    override val iconRes: Int? = null
    override val authType: AuthType = AuthType.OAUTH2
    override val feedSource: PluginFeedSource by lazy { PixivFeedSource(api) }

    override val feedTabs: List<PluginFeedTab> = listOf(
        PluginFeedTab("Following", PluginFeedKind.FOLLOWED, requiresAuthentication = true),
        PluginFeedTab("Popular", PluginFeedKind.POPULAR),
        PluginFeedTab("Favorites", PluginFeedKind.FAVORITES, requiresAuthentication = true),
    )
    private var session: PixivSession? = loadSession()
    private val browserLoginMutex = Mutex()
    private val _isLoggedIn = MutableStateFlow(session?.accessToken?.isNotBlank() == true)
    override val isLoggedIn: Boolean get() = _isLoggedIn.value
    override val isLoggedInFlow: Flow<Boolean> = _isLoggedIn.asStateFlow()
    val api: PixivApi = PixivApi(
        httpClient = httpClient,
        baseUrl = apiBaseUrl,
        sessionProvider = { session },
        sessionRefresher = { refreshSession() },
        sessionInvalidator = { invalidateSession() },
    )

    override fun getDisplayUsername(): String? = session?.username

    override fun beginBrowserLogin(): Result<String> {
        val request = oauthClient?.authorizationRequest()
        if (request == null) {
            return Result.failure(IllegalStateException("Pixiv browser login is not configured"))
        }
        storage.save(id, KEY_PENDING_CODE_VERIFIER, request.codeVerifier)
        return Result.success(request.url)
    }

    override suspend fun completeBrowserLogin(code: String): Result<Unit> = browserLoginMutex.withLock {
        val verifier = storage.get(id, KEY_PENDING_CODE_VERIFIER)
            ?: return@withLock Result.failure(IllegalStateException("Pixiv browser login has expired"))
        storage.remove(id, KEY_PENDING_CODE_VERIFIER)

        val nextSession = oauthClient?.exchangeCode(code, verifier)
            ?: return@withLock Result.failure(IllegalStateException("Pixiv browser sign-in failed"))
        persistSession(nextSession)
        Result.success(Unit)
    }

    override suspend fun login(credentials: Map<String, String>): Result<Unit> = withContext(Dispatchers.IO) {
        val accessToken = credentials[KEY_ACCESS_TOKEN]?.trim()
        if (accessToken.isNullOrBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Pixiv access token required"))
        }
        val userId = credentials[KEY_USER_ID]?.trim()?.toLongOrNull()
        val nextSession = PixivSession(
            accessToken = accessToken,
            refreshToken = credentials[KEY_REFRESH_TOKEN]?.trim()?.takeIf(String::isNotBlank),
            userId = userId,
            username = credentials[KEY_USERNAME]?.trim()?.takeIf(String::isNotBlank),
        )
        persistSession(nextSession)
        Result.success(Unit)
    }

    override fun logout() {
        invalidateSession()
    }

    override suspend fun setBookmark(postId: Long, bookmarked: Boolean): Result<Unit> =
        when (val result = api.bookmark(postId, bookmarked)) {
            PixivBookmarkResult.Success -> Result.success(Unit)
            PixivBookmarkResult.AuthRequired -> Result.failure(IllegalStateException("Pixiv sign-in required"))
            is PixivBookmarkResult.RateLimited -> Result.failure(IllegalStateException("Pixiv is rate limiting requests"))
            is PixivBookmarkResult.UpstreamDrift -> Result.failure(IllegalStateException("Pixiv bookmark response changed"))
            is PixivBookmarkResult.TransportFailure -> Result.failure(IllegalStateException(result.message))
        }

    override suspend fun searchSupport(query: String): PluginSearchSupport? {
        val trimmed = query.trim()
        val suggestions = if (trimmed.isBlank()) {
            emptyList()
        } else {
            when (val result = api.autocomplete(trimmed)) {
                is PixivSupportResult.Success -> result.values
                else -> return null
            }
        }
        val trending = if (trimmed.isBlank()) {
            when (val result = api.trendingTags()) {
                is PixivSupportResult.Success -> result.values
                else -> emptyList()
            }
        } else {
            emptyList()
        }
        return PluginSearchSupport(suggestions = suggestions, trending = trending)
    }

    override suspend fun setAuthorFollowed(userId: Long, followed: Boolean): Result<Unit> =
        when (val result = api.followAuthor(userId, followed)) {
            PixivBookmarkResult.Success -> Result.success(Unit)
            PixivBookmarkResult.AuthRequired -> Result.failure(IllegalStateException("Pixiv sign-in required"))
            is PixivBookmarkResult.RateLimited -> Result.failure(IllegalStateException("Pixiv is rate limiting requests"))
            is PixivBookmarkResult.UpstreamDrift -> Result.failure(IllegalStateException("Pixiv follow response changed"))
            is PixivBookmarkResult.TransportFailure -> Result.failure(IllegalStateException(result.message))
        }

    override suspend fun isAuthorFollowed(userId: Long): Boolean? =
        when (val result = api.userDetail(userId)) {
            is PixivUserDetailResult.Success -> result.isFollowed
            PixivUserDetailResult.AuthRequired -> null
            is PixivUserDetailResult.RateLimited -> null
            is PixivUserDetailResult.UpstreamDrift -> null
            is PixivUserDetailResult.TransportFailure -> null
        }

    private suspend fun refreshSession(): PixivSession? {
        val current = session ?: return null
        val refreshToken = current.refreshToken ?: return null
        val refreshed = oauthClient?.refresh(refreshToken) ?: return null
        val nextSession = refreshed.copy(
            userId = refreshed.userId ?: current.userId,
            username = refreshed.username ?: current.username,
        )
        persistSession(nextSession)
        return nextSession
    }

    private fun persistSession(nextSession: PixivSession) {
        storage.save(id, KEY_ACCESS_TOKEN, nextSession.accessToken)
        nextSession.refreshToken?.let { storage.save(id, KEY_REFRESH_TOKEN, it) }
            ?: storage.remove(id, KEY_REFRESH_TOKEN)
        nextSession.userId?.let { storage.save(id, KEY_USER_ID, it.toString()) }
            ?: storage.remove(id, KEY_USER_ID)
        nextSession.username?.let { storage.save(id, KEY_USERNAME, it) }
            ?: storage.remove(id, KEY_USERNAME)
        storage.remove(id, KEY_PENDING_CODE_VERIFIER)
        session = nextSession
        _isLoggedIn.value = true
    }

    private fun invalidateSession() {
        storage.clearPlugin(id)
        session = null
        _isLoggedIn.value = false
    }

    private fun loadSession(): PixivSession? {
        val accessToken = storage.get(id, KEY_ACCESS_TOKEN)?.takeIf(String::isNotBlank) ?: return null
        return PixivSession(
            accessToken = accessToken,
            refreshToken = storage.get(id, KEY_REFRESH_TOKEN),
            userId = storage.get(id, KEY_USER_ID)?.toLongOrNull(),
            username = storage.get(id, KEY_USERNAME),
        )
    }

    private companion object {
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_USERNAME = "username"
        const val KEY_PENDING_CODE_VERIFIER = "pending_code_verifier"
    }
}
