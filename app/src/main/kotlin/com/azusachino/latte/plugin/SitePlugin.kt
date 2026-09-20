package com.azusachino.latte.plugin

import kotlinx.coroutines.flow.Flow
import okhttp3.Request

enum class AuthType {
    CREDENTIALS,
    OAUTH2,
    API_KEY
}

enum class PluginCapability {
    SCORING,
    FAVORITES,
    REFERER_INJECT,
    USER_FEED
}

interface SitePlugin {
    val id: String
    val name: String
    val iconRes: Int?
    val authType: AuthType
    val capabilities: Set<PluginCapability>

    val isLoggedIn: Boolean
    val isLoggedInFlow: Flow<Boolean>
    fun getDisplayUsername(): String?

    suspend fun login(credentials: Map<String, String>): Result<Unit>
    fun logout()

    suspend fun setScore(postId: Long, score: Int): Result<Unit> =
        Result.failure(UnsupportedOperationException("Scoring not supported by $name"))

    fun getScore(postId: Long): Int? = null

    // Best-effort lookup for a score set outside this app process (a prior
    // session, or the site's own web UI) -- null if the plugin has no way
    // to check, or the post genuinely has no recorded score.
    suspend fun refreshScore(postId: Long): Int? = null

    fun applyHeaders(builder: Request.Builder, url: String) {}
}
