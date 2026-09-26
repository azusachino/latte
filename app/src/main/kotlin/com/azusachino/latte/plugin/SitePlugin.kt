package com.azusachino.latte.plugin

import kotlinx.coroutines.flow.Flow
import okhttp3.Request

enum class AuthType {
    CREDENTIALS,
    OAUTH2,
    API_KEY
}

enum class AuthFlow {
    CREDENTIALS,
    BROWSER,
    TOKEN_IMPORT,
}

interface SitePlugin {
    val platform: PlatformId
    val id: String
        get() = platform.externalId
    val name: String
    val favoritesPrompt: String
        get() = "Sign in to see $name favorites"
    val iconRes: Int?
    val authType: AuthType
    val supportedAuthFlows: Set<AuthFlow>
        get() = when (authType) {
            AuthType.CREDENTIALS -> setOf(AuthFlow.CREDENTIALS)
            AuthType.OAUTH2 -> setOf(AuthFlow.BROWSER, AuthFlow.TOKEN_IMPORT)
            AuthType.API_KEY -> setOf(AuthFlow.TOKEN_IMPORT)
        }
    val capabilities: Set<PlatformCapability>
        get() = platform.capabilities

    /** Feed access for plugins that serve paged personal feeds; null otherwise. */
    val feedSource: PluginFeedSource? get() = null
    val poolSource: PluginPoolSource? get() = null

    /** Tabs exposed by this plugin; presentation does not infer them from identity. */
    val feedTabs: List<PluginFeedTab>
        get() = emptyList()

    /** Best-effort search support; null when the plugin has none. */
    suspend fun searchSupport(query: String): PluginSearchSupport? = null

    val isLoggedIn: Boolean
    val isLoggedInFlow: Flow<Boolean>
    fun getDisplayUsername(): String?

    suspend fun login(credentials: Map<String, String>): Result<Unit>
    fun logout()

    suspend fun setScore(postId: Long, score: Int): Result<Unit> =
        Result.failure(UnsupportedOperationException("Scoring not supported by $name"))

    suspend fun setBookmark(postId: Long, bookmarked: Boolean): Result<Unit> =
        Result.failure(UnsupportedOperationException("Bookmarks not supported by $name"))

    suspend fun setAuthorFollowed(userId: Long, followed: Boolean): Result<Unit> =
        Result.failure(UnsupportedOperationException("Following not supported by $name"))

    // Best-effort read of the current follow state for an author -- null if
    // the plugin cannot check it or is not authenticated.
    suspend fun isAuthorFollowed(userId: Long): Boolean? = null

    fun getScore(postId: Long): Int? = null

    // Best-effort lookup for a score set outside this app process (a prior
    // session, or the site's own web UI) -- null if the plugin has no way
    // to check, or the post genuinely has no recorded score.
    suspend fun refreshScore(postId: Long): Int? = null

    fun applyHeaders(builder: Request.Builder, url: String) {}
}
