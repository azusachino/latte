package com.azusachino.latte.ui.explore

import com.azusachino.latte.data.model.FavoriteTag
import com.azusachino.latte.data.model.PoolSummary
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.model.PostRating
import com.azusachino.latte.plugin.PlatformCapability
import com.azusachino.latte.plugin.PlatformId
import com.azusachino.latte.plugin.PluginFeedKind
import com.azusachino.latte.plugin.PluginFeedPeriod
import com.azusachino.latte.plugin.SitePlugin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.time.LocalDate

internal data class ExploreGridKey(
    val platform: PlatformId,
    val feed: String,
)

internal data class GridPosition(
    val index: Int = 0,
    val scrollOffset: Int = 0,
    val anchorPostId: Long? = null,
)

data class FeedState(
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: FeedError? = null,
    val authRequired: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val nextCursor: String? = null,
    val autoLoadStreak: Int = 0,
    /** Informational (non-error) message, rendered in normal body styling. */
    val notice: String? = null,
)

data class PoolListState(
    val pools: List<PoolSummary> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: FeedError? = null,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val query: String = "",
)

// Tab indices: 0 = Popular, 1 = Newest, 2 = Favorites, 3 = Pools
//
// Feed states live in exactly ONE store: [feeds], keyed by platform and feed
// kind. There are deliberately no flat per-feed fields — dual stores diverged
// on reset paths and served stale content (see #33/#35 history). All writes
// go through [withFeed]; all reads through [feed].
data class ExploreUiState(
    val platform: PlatformId = PlatformId.YANDE,
    val feeds: Map<PlatformId, Map<PluginFeedKind, FeedState>> = emptyMap(),
    val poolsFeed: PoolListState = PoolListState(),
    val poolCovers: Map<Long, String> = emptyMap(),
    val searchTags: String = "",
    val pixivSearchTags: String = "",
    val pixivAuthorId: Long? = null,
    val pixivAuthorName: String? = null,
    val pixivSearchSuggestions: List<String> = emptyList(),
    val pixivTrendingTags: List<String> = emptyList(),
    val yandeSearchSuggestions: List<String> = emptyList(),
    val favoriteTags: List<FavoriteTag> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val pixivAuthorFollowed: Boolean? = null,
    val isTogglingPixivFollow: Boolean = false,
    val activePoolName: String? = null,
    val popularPeriod: PluginFeedPeriod = PluginFeedPeriod.DAY,
    val popularDate: LocalDate = LocalDate.now(),
    val selectedTab: Int = 0,
) {
    fun feed(targetPlatform: PlatformId, kind: PluginFeedKind): FeedState =
        feeds[targetPlatform]?.get(kind) ?: FeedState()

    fun feed(kind: PluginFeedKind): FeedState =
        feed(platform, kind)

    fun withFeed(
        targetPlatform: PlatformId,
        kind: PluginFeedKind,
        transform: (FeedState) -> FeedState,
    ): ExploreUiState {
        val updatedFeed = transform(feed(targetPlatform, kind))
        val currentPlatformFeeds = feeds[targetPlatform].orEmpty() + (kind to updatedFeed)
        return copy(feeds = feeds + (targetPlatform to currentPlatformFeeds))
    }

    fun withFeed(
        kind: PluginFeedKind,
        transform: (FeedState) -> FeedState,
    ): ExploreUiState = withFeed(platform, kind, transform)

    val activeSearchTags: String
        get() = if (supportsUserFeeds) {
            pixivAuthorName ?: pixivSearchTags
        } else {
            searchTags
        }

    val supportsUserFeeds: Boolean get() = PlatformCapability.USER_FEED in platform.capabilities

    fun feedState(kind: PluginFeedKind): FeedState = when (kind) {
        PluginFeedKind.POPULAR,
        PluginFeedKind.FOLLOWED,
        PluginFeedKind.FAVORITES,
        PluginFeedKind.SEARCH,
        PluginFeedKind.AUTHOR_WORKS,
        -> feed(PlatformId.PIXIV, kind)
        PluginFeedKind.NEWEST, PluginFeedKind.POOLS -> FeedState()
    }

    val isPopular: Boolean get() = selectedTab == 0 && activeSearchTags.isBlank()
    val isSearch: Boolean get() = activeSearchTags.isNotBlank()

    val posts: List<Post>
        get() = when {
            isSearch -> when {
                !supportsUserFeeds -> feed(PluginFeedKind.SEARCH).posts
                pixivAuthorId != null -> feed(PlatformId.PIXIV, PluginFeedKind.AUTHOR_WORKS).posts
                else -> feed(PlatformId.PIXIV, PluginFeedKind.SEARCH).posts
            }
            supportsUserFeeds && selectedTab == 0 -> feedState(PluginFeedKind.FOLLOWED).posts
            supportsUserFeeds && selectedTab == 1 -> feedState(PluginFeedKind.POPULAR).posts
            supportsUserFeeds && selectedTab == 2 -> feedState(PluginFeedKind.FAVORITES).posts
            selectedTab == 0 -> feed(PluginFeedKind.POPULAR).posts
            selectedTab == 1 -> feed(PluginFeedKind.NEWEST).posts
            selectedTab == 2 -> feed(PluginFeedKind.FAVORITES).posts
            else -> emptyList()
        }

    val isLoading: Boolean
        get() = when {
            isSearch -> when {
                !supportsUserFeeds -> feed(PluginFeedKind.SEARCH).isLoading
                pixivAuthorId != null -> feed(PlatformId.PIXIV, PluginFeedKind.AUTHOR_WORKS).isLoading
                else -> feed(PlatformId.PIXIV, PluginFeedKind.SEARCH).isLoading
            }
            supportsUserFeeds && selectedTab == 0 -> feedState(PluginFeedKind.FOLLOWED).isLoading
            supportsUserFeeds && selectedTab == 1 -> feedState(PluginFeedKind.POPULAR).isLoading
            supportsUserFeeds && selectedTab == 2 -> feedState(PluginFeedKind.FAVORITES).isLoading
            selectedTab == 0 -> feed(PluginFeedKind.POPULAR).isLoading
            selectedTab == 1 -> feed(PluginFeedKind.NEWEST).isLoading
            selectedTab == 2 -> feed(PluginFeedKind.FAVORITES).isLoading
            else -> false
        }

    val isLoadingMore: Boolean
        get() = when {
            isSearch -> when {
                !supportsUserFeeds -> feed(PluginFeedKind.SEARCH).isLoadingMore
                pixivAuthorId != null -> feed(PlatformId.PIXIV, PluginFeedKind.AUTHOR_WORKS).isLoadingMore
                else -> feed(PlatformId.PIXIV, PluginFeedKind.SEARCH).isLoadingMore
            }
            supportsUserFeeds && selectedTab == 0 -> feedState(PluginFeedKind.FOLLOWED).isLoadingMore
            supportsUserFeeds && selectedTab == 1 -> feedState(PluginFeedKind.POPULAR).isLoadingMore
            supportsUserFeeds && selectedTab == 2 -> feedState(PluginFeedKind.FAVORITES).isLoadingMore
            selectedTab == 0 -> feed(PluginFeedKind.POPULAR).isLoadingMore
            selectedTab == 1 -> feed(PluginFeedKind.NEWEST).isLoadingMore
            selectedTab == 2 -> feed(PluginFeedKind.FAVORITES).isLoadingMore
            else -> false
        }

    val isRefreshing: Boolean
        get() = when {
            isSearch -> when {
                !supportsUserFeeds -> feed(PluginFeedKind.SEARCH).isRefreshing
                pixivAuthorId != null -> feed(PlatformId.PIXIV, PluginFeedKind.AUTHOR_WORKS).isRefreshing
                else -> feed(PlatformId.PIXIV, PluginFeedKind.SEARCH).isRefreshing
            }
            supportsUserFeeds && selectedTab == 0 -> feedState(PluginFeedKind.FOLLOWED).isRefreshing
            supportsUserFeeds && selectedTab == 1 -> feedState(PluginFeedKind.POPULAR).isRefreshing
            supportsUserFeeds && selectedTab == 2 -> feedState(PluginFeedKind.FAVORITES).isRefreshing
            selectedTab == 0 -> feed(PluginFeedKind.POPULAR).isRefreshing
            selectedTab == 1 -> feed(PluginFeedKind.NEWEST).isRefreshing
            selectedTab == 2 -> feed(PluginFeedKind.FAVORITES).isRefreshing
            else -> false
        }

    val error: FeedError?
        get() = when {
            isSearch -> when {
                !supportsUserFeeds -> feed(PluginFeedKind.SEARCH).error
                pixivAuthorId != null -> feed(PlatformId.PIXIV, PluginFeedKind.AUTHOR_WORKS).error
                else -> feed(PlatformId.PIXIV, PluginFeedKind.SEARCH).error
            }
            supportsUserFeeds && selectedTab == 0 -> feedState(PluginFeedKind.FOLLOWED).error
            supportsUserFeeds && selectedTab == 1 -> feedState(PluginFeedKind.POPULAR).error
            supportsUserFeeds && selectedTab == 2 -> feedState(PluginFeedKind.FAVORITES).error
            selectedTab == 0 -> feed(PluginFeedKind.POPULAR).error
            selectedTab == 1 -> feed(PluginFeedKind.NEWEST).error
            selectedTab == 2 -> feed(PluginFeedKind.FAVORITES).error
            else -> null
        }
}

internal fun ExploreUiState.forPlatform(platform: PlatformId): ExploreUiState = copy(
    platform = platform,
    selectedTab = 0,
    feeds = emptyMap(),
    searchTags = "",
    pixivSearchTags = "",
    pixivAuthorId = null,
    pixivAuthorName = null,
    pixivAuthorFollowed = null,
    activePoolName = null,
    poolsFeed = PoolListState(),
    poolCovers = emptyMap(),
    yandeSearchSuggestions = emptyList(),
    pixivSearchSuggestions = emptyList(),
)

internal fun ExploreUiState.pixivFeedToReloadAfterAuthentication(): PluginFeedKind? = when {
    !supportsUserFeeds -> null
    isSearch && pixivAuthorId != null -> PluginFeedKind.AUTHOR_WORKS
    isSearch -> PluginFeedKind.SEARCH
    else -> pixivKindForTab(selectedTab)
}

internal fun ExploreUiState.clearStalePixivAuthErrors(): ExploreUiState {
    var cleared = this
    for (kind in listOf(PluginFeedKind.FOLLOWED, PluginFeedKind.FAVORITES, PluginFeedKind.SEARCH, PluginFeedKind.AUTHOR_WORKS)) {
        if (feed(PlatformId.PIXIV, kind).authRequired) {
            cleared = cleared.withFeed(PlatformId.PIXIV, kind) { FeedState() }
        }
    }
    return cleared
}

internal fun pixivKindForTab(tabIndex: Int): PluginFeedKind? = when (tabIndex) {
    0 -> PluginFeedKind.FOLLOWED
    1 -> PluginFeedKind.POPULAR
    2 -> PluginFeedKind.FAVORITES
    else -> null
}

internal fun followProviderFor(plugins: Map<PlatformId, SitePlugin>): SitePlugin? =
    plugins[PlatformId.PIXIV]?.takeIf { PlatformCapability.FOLLOW_AUTHORS in it.capabilities }

internal fun pixivFeed(state: ExploreUiState, kind: PluginFeedKind): FeedState =
    state.feed(PlatformId.PIXIV, kind)

internal fun filterPosts(posts: List<Post>, safeMode: Boolean): List<Post> =
    if (safeMode) posts.filter { it.rating == PostRating.SAFE } else posts

internal suspend fun <T> applyIfActive(
    request: suspend () -> T,
    apply: (T) -> Unit,
) {
    val result = request()
    if (currentCoroutineContext().isActive) apply(result)
}
