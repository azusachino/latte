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

    val isLoggedIn: Boolean
    val isLoggedInFlow: Flow<Boolean>
    fun getDisplayUsername(): String?

    suspend fun login(credentials: Map<String, String>): Result<Unit>
    fun logout()

    suspend fun setScore(postId: Long, score: Int): Result<Unit> =
        Result.failure(UnsupportedOperationException("Scoring not supported by $name"))

    suspend fun setBookmark(postId: Long, bookmarked: Boolean): Result<Unit> =
        Result.failure(UnsupportedOperationException("Bookmarks not supported by $name"))

    fun getScore(postId: Long): Int? = null

    // Best-effort lookup for a score set outside this app process (a prior
    // session, or the site's own web UI) -- null if the plugin has no way
    // to check, or the post genuinely has no recorded score.
    suspend fun refreshScore(postId: Long): Int? = null

    fun applyHeaders(builder: Request.Builder, url: String) {}
}
