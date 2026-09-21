package com.azusachino.latte.data.network

import com.azusachino.latte.data.model.Post

enum class PixivFeedKind {
    POPULAR,
    FOLLOWED_UPDATES,
    FAVORITES,
    SEARCH,
}

data class PixivFeedRequest(
    val kind: PixivFeedKind,
    val query: String? = null,
    val cursor: String? = null,
    val refresh: Boolean = false,
)

data class PixivFeedPage(
    val items: List<Post>,
    val nextCursor: String? = null,
)

sealed interface PixivFeedResult {
    data class Success(val page: PixivFeedPage) : PixivFeedResult

    data object AuthRequired : PixivFeedResult

    data class RateLimited(val retryAfterSeconds: Long?) : PixivFeedResult

    data class UpstreamDrift(val operation: String) : PixivFeedResult

    data class TransportFailure(val message: String) : PixivFeedResult

    data object Empty : PixivFeedResult
}

data class PixivSearchSupport(
    val autocomplete: List<String> = emptyList(),
    val trendingTags: List<String> = emptyList(),
)

sealed interface PixivBookmarkResult {
    data object Success : PixivBookmarkResult

    data object AuthRequired : PixivBookmarkResult

    data class RateLimited(val retryAfterSeconds: Long?) : PixivBookmarkResult

    data class UpstreamDrift(val operation: String) : PixivBookmarkResult

    data class TransportFailure(val message: String) : PixivBookmarkResult
}

sealed interface PixivSupportResult {
    data class Success(val values: List<String>) : PixivSupportResult

    data object AuthRequired : PixivSupportResult

    data class RateLimited(val retryAfterSeconds: Long?) : PixivSupportResult

    data class UpstreamDrift(val operation: String) : PixivSupportResult

    data class TransportFailure(val message: String) : PixivSupportResult
}

sealed interface PixivDetailResult {
    data class Success(val post: Post) : PixivDetailResult

    data object AuthRequired : PixivDetailResult

    data class RateLimited(val retryAfterSeconds: Long?) : PixivDetailResult

    data class UpstreamDrift(val operation: String) : PixivDetailResult

    data class TransportFailure(val message: String) : PixivDetailResult
}
