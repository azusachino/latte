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
data class ExploreUiState(
    val platform: PlatformId = PlatformId.YANDE,
    val feeds: Map<PlatformId, Map<PluginFeedKind, FeedState>> = emptyMap(),
    val popularFeed: FeedState = FeedState(),
    val newestFeed: FeedState = FeedState(),
    val favoritesFeed: FeedState = FeedState(),
    val searchFeed: FeedState = FeedState(),
    val pixivPopularFeed: FeedState = FeedState(),
    val pixivFollowedFeed: FeedState = FeedState(),
    val pixivFavoritesFeed: FeedState = FeedState(),
    val pixivSearchFeed: FeedState = FeedState(),
    val pixivUserWorksFeed: FeedState = FeedState(),
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
    fun feed(targetPlatform: PlatformId, kind: PluginFeedKind): FeedState {
        feeds[targetPlatform]?.get(kind)?.let { return it }
        return when (targetPlatform) {
            PlatformId.PIXIV -> when (kind) {
                PluginFeedKind.POPULAR -> pixivPopularFeed
                PluginFeedKind.FOLLOWED -> pixivFollowedFeed
                PluginFeedKind.FAVORITES -> pixivFavoritesFeed
                PluginFeedKind.SEARCH -> pixivSearchFeed
                PluginFeedKind.AUTHOR_WORKS -> pixivUserWorksFeed
                else -> FeedState()
            }
            else -> when (kind) {
                PluginFeedKind.POPULAR -> popularFeed
                PluginFeedKind.NEWEST -> newestFeed
                PluginFeedKind.FAVORITES -> favoritesFeed
                PluginFeedKind.SEARCH -> searchFeed
                else -> FeedState()
            }
        }
    }

    fun feed(kind: PluginFeedKind): FeedState =
        feed(platform, kind)

    fun withFeed(
        targetPlatform: PlatformId,
        kind: PluginFeedKind,
        transform: (FeedState) -> FeedState,
    ): ExploreUiState {
        val currentFeed = feed(targetPlatform, kind)
        val updatedFeed = transform(currentFeed)
        val currentPlatformFeeds = feeds[targetPlatform].orEmpty() + (kind to updatedFeed)
        val newFeeds = feeds + (targetPlatform to currentPlatformFeeds)

        return when (targetPlatform) {
            PlatformId.PIXIV -> when (kind) {
                PluginFeedKind.POPULAR -> copy(feeds = newFeeds, pixivPopularFeed = updatedFeed)
                PluginFeedKind.FOLLOWED -> copy(feeds = newFeeds, pixivFollowedFeed = updatedFeed)
                PluginFeedKind.FAVORITES -> copy(feeds = newFeeds, pixivFavoritesFeed = updatedFeed)
                PluginFeedKind.SEARCH -> copy(feeds = newFeeds, pixivSearchFeed = updatedFeed)
                PluginFeedKind.AUTHOR_WORKS -> copy(feeds = newFeeds, pixivUserWorksFeed = updatedFeed)
                else -> copy(feeds = newFeeds)
            }
            else -> when (kind) {
                PluginFeedKind.POPULAR -> copy(feeds = newFeeds, popularFeed = updatedFeed)
                PluginFeedKind.NEWEST -> copy(feeds = newFeeds, newestFeed = updatedFeed)
                PluginFeedKind.FAVORITES -> copy(feeds = newFeeds, favoritesFeed = updatedFeed)
                PluginFeedKind.SEARCH -> copy(feeds = newFeeds, searchFeed = updatedFeed)
                else -> copy(feeds = newFeeds)
            }
        }
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
        PluginFeedKind.POPULAR -> pixivPopularFeed
        PluginFeedKind.FOLLOWED -> pixivFollowedFeed
        PluginFeedKind.FAVORITES -> pixivFavoritesFeed
        PluginFeedKind.SEARCH -> pixivSearchFeed
        PluginFeedKind.AUTHOR_WORKS -> pixivUserWorksFeed
        PluginFeedKind.NEWEST, PluginFeedKind.POOLS -> FeedState()
    }

    val isPopular: Boolean get() = selectedTab == 0 && activeSearchTags.isBlank()
    val isSearch: Boolean get() = activeSearchTags.isNotBlank()

    val posts: List<Post>
        get() = when {
            isSearch -> when {
                !supportsUserFeeds -> searchFeed.posts
                pixivAuthorId != null -> pixivUserWorksFeed.posts
                else -> pixivSearchFeed.posts
            }
            supportsUserFeeds && selectedTab == 0 -> feedState(PluginFeedKind.FOLLOWED).posts
            supportsUserFeeds && selectedTab == 1 -> feedState(PluginFeedKind.POPULAR).posts
            supportsUserFeeds && selectedTab == 2 -> feedState(PluginFeedKind.FAVORITES).posts
            selectedTab == 0 -> popularFeed.posts
            selectedTab == 1 -> newestFeed.posts
            selectedTab == 2 -> favoritesFeed.posts
            else -> emptyList()
        }

    val isLoading: Boolean
        get() = when {
            isSearch -> when {
                !supportsUserFeeds -> searchFeed.isLoading
                pixivAuthorId != null -> pixivUserWorksFeed.isLoading
                else -> pixivSearchFeed.isLoading
            }
            supportsUserFeeds && selectedTab == 0 -> feedState(PluginFeedKind.FOLLOWED).isLoading
            supportsUserFeeds && selectedTab == 1 -> feedState(PluginFeedKind.POPULAR).isLoading
            supportsUserFeeds && selectedTab == 2 -> feedState(PluginFeedKind.FAVORITES).isLoading
            selectedTab == 0 -> popularFeed.isLoading
            selectedTab == 1 -> newestFeed.isLoading
            selectedTab == 2 -> favoritesFeed.isLoading
            else -> false
        }

    val isLoadingMore: Boolean
        get() = when {
            isSearch -> when {
                !supportsUserFeeds -> searchFeed.isLoadingMore
                pixivAuthorId != null -> pixivUserWorksFeed.isLoadingMore
                else -> pixivSearchFeed.isLoadingMore
            }
            supportsUserFeeds && selectedTab == 0 -> feedState(PluginFeedKind.FOLLOWED).isLoadingMore
            supportsUserFeeds && selectedTab == 1 -> feedState(PluginFeedKind.POPULAR).isLoadingMore
            supportsUserFeeds && selectedTab == 2 -> feedState(PluginFeedKind.FAVORITES).isLoadingMore
            selectedTab == 0 -> popularFeed.isLoadingMore
            selectedTab == 1 -> newestFeed.isLoadingMore
            selectedTab == 2 -> favoritesFeed.isLoadingMore
            else -> false
        }

    val isRefreshing: Boolean
        get() = when {
            isSearch -> when {
                !supportsUserFeeds -> searchFeed.isRefreshing
                pixivAuthorId != null -> pixivUserWorksFeed.isRefreshing
                else -> pixivSearchFeed.isRefreshing
            }
            supportsUserFeeds && selectedTab == 0 -> feedState(PluginFeedKind.FOLLOWED).isRefreshing
            supportsUserFeeds && selectedTab == 1 -> feedState(PluginFeedKind.POPULAR).isRefreshing
            supportsUserFeeds && selectedTab == 2 -> feedState(PluginFeedKind.FAVORITES).isRefreshing
            selectedTab == 0 -> popularFeed.isRefreshing
            selectedTab == 1 -> newestFeed.isRefreshing
            selectedTab == 2 -> favoritesFeed.isRefreshing
            else -> false
        }

    val error: FeedError?
        get() = when {
            isSearch -> when {
                !supportsUserFeeds -> searchFeed.error
                pixivAuthorId != null -> pixivUserWorksFeed.error
                else -> pixivSearchFeed.error
            }
            supportsUserFeeds && selectedTab == 0 -> feedState(PluginFeedKind.FOLLOWED).error
            supportsUserFeeds && selectedTab == 1 -> feedState(PluginFeedKind.POPULAR).error
            supportsUserFeeds && selectedTab == 2 -> feedState(PluginFeedKind.FAVORITES).error
            selectedTab == 0 -> popularFeed.error
            selectedTab == 1 -> newestFeed.error
            selectedTab == 2 -> favoritesFeed.error
            else -> null
        }
}

internal fun ExploreUiState.forPlatform(platform: PlatformId): ExploreUiState = copy(
    platform = platform,
    selectedTab = 0,
    searchTags = "",
    pixivSearchTags = "",
    pixivAuthorId = null,
    pixivAuthorName = null,
    pixivAuthorFollowed = null,
    activePoolName = null,
    popularFeed = FeedState(),
    newestFeed = FeedState(),
    favoritesFeed = FeedState(),
    searchFeed = FeedState(),
    pixivPopularFeed = FeedState(),
    pixivFollowedFeed = FeedState(),
    pixivFavoritesFeed = FeedState(),
    pixivSearchFeed = FeedState(),
    pixivUserWorksFeed = FeedState(),
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

internal fun ExploreUiState.clearStalePixivAuthErrors(): ExploreUiState = copy(
    pixivFollowedFeed = if (pixivFollowedFeed.authRequired) FeedState() else pixivFollowedFeed,
    pixivFavoritesFeed = if (pixivFavoritesFeed.authRequired) FeedState() else pixivFavoritesFeed,
    pixivSearchFeed = if (pixivSearchFeed.authRequired) FeedState() else pixivSearchFeed,
    pixivUserWorksFeed = if (pixivUserWorksFeed.authRequired) FeedState() else pixivUserWorksFeed,
)

internal fun pixivKindForTab(tabIndex: Int): PluginFeedKind? = when (tabIndex) {
    0 -> PluginFeedKind.FOLLOWED
    1 -> PluginFeedKind.POPULAR
    2 -> PluginFeedKind.FAVORITES
    else -> null
}

internal fun followProviderFor(plugins: Map<PlatformId, SitePlugin>): SitePlugin? =
    plugins[PlatformId.PIXIV]?.takeIf { PlatformCapability.FOLLOW_AUTHORS in it.capabilities }

internal fun pixivFeed(state: ExploreUiState, kind: PluginFeedKind): FeedState =
    when (kind) {
        PluginFeedKind.POPULAR -> state.pixivPopularFeed
        PluginFeedKind.FOLLOWED -> state.pixivFollowedFeed
        PluginFeedKind.FAVORITES -> state.pixivFavoritesFeed
        PluginFeedKind.SEARCH -> state.pixivSearchFeed
        PluginFeedKind.AUTHOR_WORKS -> state.pixivUserWorksFeed
        PluginFeedKind.NEWEST, PluginFeedKind.POOLS -> FeedState()
    }

internal fun filterPosts(posts: List<Post>, safeMode: Boolean): List<Post> =
    if (safeMode) posts.filter { it.rating == PostRating.SAFE } else posts

internal suspend fun <T> applyIfActive(
    request: suspend () -> T,
    apply: (T) -> Unit,
) {
    val result = request()
    if (currentCoroutineContext().isActive) apply(result)
}

