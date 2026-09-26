package com.azusachino.latte.plugin

import com.azusachino.latte.data.model.Post

/**
 * Platform-neutral feed contract. Top-tier components (ViewModel, UI) speak
 * only these types; each plugin maps them onto its own transport. A plugin
 * that cannot serve feeds leaves [SitePlugin.feedSource] null.
 */
enum class PluginFeedKind {
    POPULAR,
    FOLLOWED,
    FAVORITES,
    SEARCH,
    AUTHOR_WORKS,
}

data class PluginFeedRequest(
    val kind: PluginFeedKind,
    val query: String? = null,
    val authorId: Long? = null,
    val cursor: String? = null,
    val refresh: Boolean = false,
)

data class PluginFeedPage(
    val items: List<Post>,
    val nextCursor: String? = null,
)

sealed interface PluginFeedResult {
    data class Success(val page: PluginFeedPage) : PluginFeedResult

    data object AuthRequired : PluginFeedResult

    data class RateLimited(val retryAfterSeconds: Long?) : PluginFeedResult

    data class UpstreamDrift(val operation: String) : PluginFeedResult

    data class TransportFailure(val message: String) : PluginFeedResult

    data object Empty : PluginFeedResult
}

interface PluginFeedSource {
    suspend fun load(request: PluginFeedRequest): PluginFeedResult
}

/** Platform-neutral search support: autocomplete plus trending seeds. */
data class PluginSearchSupport(
    val suggestions: List<String> = emptyList(),
    val trending: List<String> = emptyList(),
)
