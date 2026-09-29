package com.azusachino.latte.ui.explore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.azusachino.latte.data.model.PoolSummary
import com.azusachino.latte.plugin.PluginFeedPeriod
import com.azusachino.latte.data.model.FavoriteTag
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.model.PostRating
import com.azusachino.latte.plugin.moebooru.MoebooruTags
import com.azusachino.latte.plugin.PluginFeedKind
import com.azusachino.latte.plugin.PluginFeedRequest
import com.azusachino.latte.plugin.PluginFeedResult
import com.azusachino.latte.plugin.PluginFeedSource
import com.azusachino.latte.plugin.SitePlugin
import com.azusachino.latte.ui.common.ToastManager
import com.azusachino.latte.data.settings.LattePreferences
import com.azusachino.latte.plugin.PlatformCapability
import com.azusachino.latte.plugin.PlatformId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    val error: String? = null,
    val authRequired: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val nextCursor: String? = null,
)

data class PoolListState(
    val pools: List<PoolSummary> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val query: String = "",
)

// Tab indices: 0 = Popular, 1 = Newest, 2 = Favorites, 3 = Pools
data class ExploreUiState(
    val platform: PlatformId = PlatformId.YANDE,
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

    val error: String?
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

class ExploreViewModel(application: Application) : AndroidViewModel(application) {
    private val plugins = mutableMapOf<PlatformId, SitePlugin>()
    private val feedSources = mutableMapOf<PlatformId, PluginFeedSource>()
    private val poolSources = mutableMapOf<PlatformId, com.azusachino.latte.plugin.PluginPoolSource>()
    private var pixivFollowProvider: SitePlugin? = null
    private var pixivLoadJob: Job? = null
    private var moebooruJob = SupervisorJob(viewModelScope.coroutineContext[Job])
    private val gridPositions = mutableMapOf<ExploreGridKey, GridPosition>()
    private val yandeSearchCache = mutableMapOf<Pair<PlatformId, String>, FeedState>()
    private val yandeSuggestionCache = mutableMapOf<Pair<PlatformId, String>, List<String>>()
    private val pixivSearchCache = mutableMapOf<String, FeedState>()
    private val pixivUserWorksCache = mutableMapOf<Long, FeedState>()
    val preferences = LattePreferences(application)

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    val columnCount: StateFlow<Int> = preferences.columnCount

    internal fun gridPosition(key: ExploreGridKey): GridPosition = gridPositions[key] ?: GridPosition()

    internal fun saveGridPosition(key: ExploreGridKey, position: GridPosition) {
        gridPositions[key] = position
    }

    internal fun cachedYandeSearch(tags: String): FeedState? =
        yandeSearchCache[_uiState.value.platform to tags]
    internal fun cachedPixivSearch(query: String): FeedState? = pixivSearchCache[query]
    internal fun cachedPixivUserWorks(userId: Long): FeedState? = pixivUserWorksCache[userId]

    private fun launchMoebooru(block: suspend CoroutineScope.() -> Unit) {
        viewModelScope.launch(moebooruJob, block = block)
    }

    private fun cancelMoebooruLoads() {
        moebooruJob.cancel()
        moebooruJob = SupervisorJob(viewModelScope.coroutineContext[Job])
    }

    init {
        viewModelScope.launch {
            combine(
                preferences.favoriteTags,
                preferences.recentSearches,
            ) { favorites, recents ->
                _uiState.update {
                    it.copy(favoriteTags = favorites, recentSearches = recents)
                }
            }.collect { }
        }
        viewModelScope.launch {
            preferences.safeMode.drop(1).collect {
                onContentFiltersChanged()
            }
        }
    }

    private fun onContentFiltersChanged() {
        cancelMoebooruLoads()
        pixivLoadJob?.cancel()
        yandeSearchCache.clear()
        pixivSearchCache.clear()
        pixivUserWorksCache.clear()
        _uiState.update {
            it.copy(
                popularFeed = FeedState(),
                newestFeed = FeedState(),
                favoritesFeed = FeedState(),
                searchFeed = FeedState(),
                pixivPopularFeed = FeedState(),
                pixivFollowedFeed = FeedState(),
                pixivFavoritesFeed = FeedState(),
                pixivSearchFeed = FeedState(),
                pixivUserWorksFeed = FeedState(),
            )
        }
        val state = _uiState.value
        if (state.supportsUserFeeds) {
            when {
                state.pixivAuthorId != null -> loadPixivInitial(PluginFeedKind.AUTHOR_WORKS)
                state.pixivSearchTags.isNotBlank() -> loadPixivInitial(PluginFeedKind.SEARCH, state.pixivSearchTags)
                else -> pixivKindForTab(state.selectedTab)?.let(::loadPixivInitial)
            }
        } else {
            loadPopularInitial()
            loadNewestInitial()
            if (state.isSearch) search(state.activeSearchTags)
        }
    }

    fun cycleColumns(): Int {
        return preferences.cycleColumnCount()
    }

    fun toggleFavoriteTag(query: String, platform: PlatformId = _uiState.value.platform) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        preferences.setTagFavorite(trimmed, platform, !preferences.isFavoriteTag(trimmed, platform))
    }

    fun setColumnCount(count: Int) {
        preferences.setColumnCount(count)
    }

    private fun applyLocalFilters(posts: List<Post>): List<Post> =
        filterPosts(posts, preferences.safeMode.value)

    private suspend fun loadPools(query: String?, page: Int): List<PoolSummary> =
        poolSources[_uiState.value.platform]?.load(query, page)?.getOrElse { throw it }
            ?: error("Active platform pool source is not configured")

    private suspend fun loadPosts(
        kind: PluginFeedKind,
        page: Int = 1,
        query: String? = null,
        period: PluginFeedPeriod = _uiState.value.popularPeriod,
        date: LocalDate = _uiState.value.popularDate,
    ): List<Post> {
        val result = activeFeedSource()?.load(PluginFeedRequest(
            kind = kind,
            page = page,
            query = query,
            period = period,
            date = date,
            safeMode = preferences.safeMode.value,
        )) ?: PluginFeedResult.TransportFailure("Active platform feed is not configured")
        return when (result) {
            is PluginFeedResult.Success -> applyLocalFilters(result.page.items)
            is PluginFeedResult.AuthRequired -> error("Authentication required")
            is PluginFeedResult.TransportFailure -> error(result.message)
            is PluginFeedResult.RateLimited -> error("Rate limited")
            is PluginFeedResult.UpstreamDrift -> error(result.operation)
            PluginFeedResult.Empty -> emptyList()
        }
    }

    fun configurePlugin(plugin: SitePlugin) {
        plugins[plugin.platform] = plugin
        plugin.feedSource?.let { feedSources[plugin.platform] = it }
        plugin.poolSource?.let { poolSources[plugin.platform] = it }
        if (plugin.platform == _uiState.value.platform &&
            PlatformCapability.USER_FEED !in plugin.capabilities &&
            _uiState.value.popularFeed.posts.isEmpty()
        ) {
            loadPopularInitial()
            loadNewestInitial()
        }
        if (PlatformCapability.USER_FEED in plugin.capabilities) {
            if (pixivFollowProvider === plugin) return
            pixivFollowProvider = plugin
            if (_uiState.value.supportsUserFeeds && _uiState.value.pixivFollowedFeed.posts.isEmpty()) {
                loadPixivInitial(PluginFeedKind.FOLLOWED)
            }
        }
    }

    fun configurePixiv(plugin: SitePlugin) = configurePlugin(plugin)

    private fun activePlugin(): SitePlugin? = plugins[_uiState.value.platform]
    private fun activeFeedSource(): PluginFeedSource? = feedSources[_uiState.value.platform]

    fun retryPixivAfterAuthentication() {
        val state = _uiState.value
        if (!state.supportsUserFeeds) return
        _uiState.update { it.clearStalePixivAuthErrors() }
        val activeKind = _uiState.value.pixivFeedToReloadAfterAuthentication() ?: return
        val activeFeed = pixivFeed(_uiState.value, activeKind)
        if (activeFeed.posts.isEmpty() || activeFeed.authRequired) {
            loadPixivInitial(activeKind)
        }
    }

    fun onPixivLoggedOut() {
        _uiState.update { current ->
            current.copy(
                pixivFollowedFeed = FeedState(),
                pixivFavoritesFeed = FeedState(),
                pixivAuthorFollowed = null,
            )
        }
        pixivAuthorFollowStates.clear()
    }

    fun selectPlatform(platform: PlatformId) {
        val previous = _uiState.value.platform
        if (previous == platform) return
        pixivLoadJob?.cancel()
        cancelMoebooruLoads()
        pixivSearchCache.clear()
        pixivUserWorksCache.clear()
        pixivAuthorFollowStates.clear()
        _uiState.update { it.forPlatform(platform) }
        if (PlatformCapability.USER_FEED in platform.capabilities && _uiState.value.pixivFollowedFeed.posts.isEmpty()) {
            loadPixivInitial(PluginFeedKind.FOLLOWED)
        } else if (PlatformCapability.USER_FEED !in platform.capabilities) {
            // A Moebooru switch changes the site under the shared tabs; the
            // cached feeds belong to the previous site, so reload them.
            loadPopularInitial()
            loadNewestInitial()
        }
    }

    fun selectTab(tabIndex: Int) {
        val state = _uiState.value
        _uiState.update { it.copy(selectedTab = tabIndex) }
        if (state.activeSearchTags.isNotBlank()) return

        if (state.supportsUserFeeds) {
            pixivKindForTab(tabIndex)?.let(::ensurePixivFeedLoaded)
            return
        }

        val tab = activePlugin()?.feedTabs?.getOrNull(tabIndex) ?: return
        when (tab.kind) {
            PluginFeedKind.FAVORITES -> {
                val plugin = activePlugin() ?: return
                if (!plugin.isLoggedIn) return
                loadFavoritesInitial(plugin.getDisplayUsername().orEmpty())
            }
            else -> {}
        }
    }

    fun selectPopularTab() = selectTab(0)
    fun selectNewestTab() = selectTab(1)

    fun selectPopularPeriod(period: PluginFeedPeriod) {
        _uiState.update { it.copy(popularPeriod = period) }
        loadPopularInitial()
    }

    fun setPopularDate(date: LocalDate) {
        val today = LocalDate.now()
        val clampedDate = if (date.isAfter(today)) today else date
        _uiState.update { it.copy(popularDate = clampedDate) }
        loadPopularInitial()
    }

    fun shiftPopularDate(amount: Int) {
        val state = _uiState.value
        val nextDate = when (state.popularPeriod) {
            PluginFeedPeriod.DAY -> state.popularDate.plusDays(amount.toLong())
            PluginFeedPeriod.WEEK -> state.popularDate.plusWeeks(amount.toLong())
            PluginFeedPeriod.MONTH -> state.popularDate.plusMonths(amount.toLong())
            PluginFeedPeriod.YEAR -> state.popularDate.plusYears(amount.toLong())
        }
        val today = LocalDate.now()
        val clampedDate = if (nextDate.isAfter(today)) today else nextDate
        _uiState.update { it.copy(popularDate = clampedDate) }
        loadPopularInitial()
    }

    fun loadPopularInitial() {
        launchMoebooru {
            _uiState.update { it.copy(popularFeed = it.popularFeed.copy(isLoading = true, error = null, page = 1)) }
            try {
                val state = _uiState.value
                val result = activeFeedSource()?.load(PluginFeedRequest(
                    kind = PluginFeedKind.POPULAR,
                    page = 1,
                    period = state.popularPeriod,
                    date = state.popularDate,
                    refresh = true,
                    safeMode = preferences.safeMode.value,
                )) ?: PluginFeedResult.TransportFailure("Active platform feed is not configured")
                val posts = when (result) {
                    is PluginFeedResult.Success -> applyLocalFilters(result.page.items)
                    else -> error(result.toString())
                }
                _uiState.update {
                    val feed = it.popularFeed.copy(posts = posts, isLoading = false, hasMore = posts.isNotEmpty())
                    it.copy(popularFeed = feed)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(
                        popularFeed = it.popularFeed.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to load popular posts",
                        )
                    )
                }
            }
        }
    }

    fun loadNewestInitial() {
        launchMoebooru {
            _uiState.update { it.copy(newestFeed = it.newestFeed.copy(isLoading = true, error = null, page = 1)) }
            try {
                val result = activeFeedSource()?.load(
                    PluginFeedRequest(
                        kind = PluginFeedKind.NEWEST,
                        page = 1,
                        refresh = true,
                        safeMode = preferences.safeMode.value,
                    ),
                ) ?: PluginFeedResult.TransportFailure("Active platform feed is not configured")
                val posts = when (result) {
                    is PluginFeedResult.Success -> applyLocalFilters(result.page.items)
                    else -> error(result.toString())
                }
                _uiState.update {
                    val feed = it.newestFeed.copy(posts = posts, isLoading = false, hasMore = posts.isNotEmpty())
                    it.copy(newestFeed = feed)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(
                        newestFeed = it.newestFeed.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to load newest posts",
                        )
                    )
                }
            }
        }
    }

    fun loadMorePopular() {
        val feed = _uiState.value.popularFeed
        if (feed.isLoading || feed.isLoadingMore || !feed.hasMore) return

        _uiState.update { it.copy(popularFeed = it.popularFeed.copy(isLoadingMore = true)) }
        launchMoebooru {
            val nextPage = feed.page + 1
            try {
                val state = _uiState.value
                val newPosts = loadPosts(PluginFeedKind.POPULAR, page = nextPage, period = state.popularPeriod, date = state.popularDate)
                _uiState.update {
                    it.copy(
                        popularFeed = it.popularFeed.copy(
                            posts = it.popularFeed.posts + newPosts,
                            page = nextPage,
                            hasMore = newPosts.isNotEmpty(),
                            isLoadingMore = false,
                        )
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(popularFeed = it.popularFeed.copy(isLoadingMore = false)) }
            }
        }
    }

    fun loadMoreNewest() {
        val feed = _uiState.value.newestFeed
        if (feed.isLoading || feed.isLoadingMore || !feed.hasMore) return

        _uiState.update { it.copy(newestFeed = it.newestFeed.copy(isLoadingMore = true)) }
        launchMoebooru {
            val nextPage = feed.page + 1
            try {
                val newPosts = loadPosts(PluginFeedKind.NEWEST, page = nextPage)
                _uiState.update {
                    it.copy(
                        newestFeed = it.newestFeed.copy(
                            posts = it.newestFeed.posts + newPosts,
                            page = nextPage,
                            hasMore = newPosts.isNotEmpty(),
                            isLoadingMore = false,
                        )
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(newestFeed = it.newestFeed.copy(isLoadingMore = false)) }
            }
        }
    }

    fun loadMoreSearch() {
        val state = _uiState.value
        if (state.supportsUserFeeds) {
            if (state.pixivAuthorId != null) {
                loadMorePixiv(PluginFeedKind.AUTHOR_WORKS)
            } else {
                loadMorePixiv(PluginFeedKind.SEARCH, state.pixivSearchTags)
            }
            return
        }
        val feed = state.searchFeed
        if (feed.isLoading || feed.isLoadingMore || !feed.hasMore || state.searchTags.isBlank()) return

        _uiState.update { it.copy(searchFeed = it.searchFeed.copy(isLoadingMore = true)) }
        launchMoebooru {
            val nextPage = feed.page + 1
            val searchKey = state.searchTags
            try {
                val newPosts = loadPosts(PluginFeedKind.SEARCH, page = nextPage, query = searchKey)
                _uiState.update {
                    val updatedFeed = it.searchFeed.copy(
                        posts = it.searchFeed.posts + newPosts,
                        page = nextPage,
                        hasMore = newPosts.isNotEmpty(),
                        isLoadingMore = false,
                    )
                    if (it.searchTags == searchKey) {
                        yandeSearchCache[it.platform to searchKey] = updatedFeed
                    }
                    it.copy(searchFeed = updatedFeed)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(searchFeed = it.searchFeed.copy(isLoadingMore = false)) }
            }
        }
    }

    fun refreshPopular() {
        launchMoebooru {
            _uiState.update { it.copy(popularFeed = it.popularFeed.copy(isRefreshing = true, error = null)) }
            try {
                val state = _uiState.value
                val posts = loadPosts(PluginFeedKind.POPULAR, period = state.popularPeriod, date = state.popularDate)
                _uiState.update {
                    it.copy(
                        popularFeed = it.popularFeed.copy(
                            posts = posts,
                            isRefreshing = false,
                            page = 1,
                            hasMore = posts.isNotEmpty(),
                        )
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(popularFeed = it.popularFeed.copy(isRefreshing = false, error = e.message ?: "Failed to refresh"))
                }
            }
        }
    }

    fun refreshNewest() {
        launchMoebooru {
            _uiState.update { it.copy(newestFeed = it.newestFeed.copy(isRefreshing = true, error = null)) }
            try {
                val posts = loadPosts(PluginFeedKind.NEWEST)
                _uiState.update {
                    it.copy(
                        newestFeed = it.newestFeed.copy(
                            posts = posts,
                            isRefreshing = false,
                            page = 1,
                            hasMore = posts.isNotEmpty(),
                        )
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(newestFeed = it.newestFeed.copy(isRefreshing = false, error = e.message ?: "Failed to refresh"))
                }
            }
        }
    }

    fun search(tags: String, poolName: String? = null) {
        val trimmed = tags.trim()
        if (trimmed.isBlank()) {
            clearSearch()
            return
        }
        if (_uiState.value.supportsUserFeeds) {
            searchPixiv(trimmed)
            return
        }
        val cacheKey = _uiState.value.platform to trimmed
        val cached = yandeSearchCache[cacheKey]
        if (cached != null && cached.posts.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    searchTags = trimmed,
                    activePoolName = poolName,
                    searchFeed = cached,
                )
            }
            return
        }
        _uiState.update { it.copy(searchTags = trimmed, activePoolName = poolName) }
        launchMoebooru {
            _uiState.update { it.copy(searchFeed = it.searchFeed.copy(isLoading = true, error = null, page = 1)) }
            try {
                val posts = loadPosts(PluginFeedKind.SEARCH, query = trimmed)
                if (poolName == null) {
                    // Pool opens are internal `pool:<id>` queries, not user
                    // searches; keep them out of the recent-search chips.
                    preferences.recordRecentSearch(trimmed)
                }
                _uiState.update {
                    val updatedFeed = it.searchFeed.copy(
                        posts = posts,
                        isLoading = false,
                        hasMore = posts.isNotEmpty(),
                    )
                    if (it.searchTags == trimmed) {
                        yandeSearchCache[cacheKey] = updatedFeed
                    }
                    it.copy(searchFeed = updatedFeed)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(searchFeed = it.searchFeed.copy(isLoading = false, error = e.message ?: "Failed to load search results"))
                }
            }
        }
    }

    fun searchPixiv(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            clearSearch()
            return
        }
        val cached = pixivSearchCache[trimmed]
        if (cached != null && cached.posts.isNotEmpty()) {
            pixivLoadJob?.cancel()
            _uiState.update {
                it.copy(
                    pixivSearchTags = trimmed,
                    pixivAuthorId = null,
                    pixivAuthorName = null,
                    pixivSearchFeed = cached,
                    pixivUserWorksFeed = FeedState(),
                    activePoolName = null,
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                pixivSearchTags = trimmed,
                pixivAuthorId = null,
                pixivAuthorName = null,
                pixivUserWorksFeed = FeedState(),
                activePoolName = null,
            )
        }
        loadPixivInitial(PluginFeedKind.SEARCH, trimmed)
    }

    fun loadPixivUserWorks(userId: Long, authorName: String) {
        if (userId <= 0 || authorName.isBlank()) return
        val cached = pixivUserWorksCache[userId]
        if (cached != null && cached.posts.isNotEmpty()) {
            pixivLoadJob?.cancel()
            _uiState.update {
                it.copy(
                    pixivSearchTags = "",
                    pixivAuthorId = userId,
                    pixivAuthorName = authorName,
                    pixivSearchFeed = FeedState(),
                    pixivUserWorksFeed = cached,
                    activePoolName = null,
                    pixivAuthorFollowed = pixivAuthorFollowStates[userId],
                )
            }
            if (pixivAuthorFollowStates[userId] == null) loadPixivFollowState(userId)
            return
        }
        _uiState.update {
            it.copy(
                pixivSearchTags = "",
                pixivAuthorId = userId,
                pixivAuthorName = authorName,
                pixivSearchFeed = FeedState(),
                activePoolName = null,
                pixivAuthorFollowed = pixivAuthorFollowStates[userId],
            )
        }
        loadPixivFollowState(userId)
        loadPixivInitial(PluginFeedKind.AUTHOR_WORKS)
    }

    private val pixivAuthorFollowStates = mutableMapOf<Long, Boolean>()

    fun loadPixivFollowState(userId: Long) {
        val plugin = pixivFollowProvider ?: return
        viewModelScope.launch {
            when (val followed = plugin.isAuthorFollowed(userId)) {
                null -> if (_uiState.value.pixivAuthorId == userId) {
                    // Unknown state: keep the toggle hidden rather than guessing.
                    _uiState.update { it.copy(pixivAuthorFollowed = null) }
                }
                else -> {
                    pixivAuthorFollowStates[userId] = followed
                    if (_uiState.value.pixivAuthorId == userId) {
                        _uiState.update { it.copy(pixivAuthorFollowed = followed) }
                    }
                }
            }
        }
    }

    fun togglePixivFollow() {
        val state = _uiState.value
        val plugin = pixivFollowProvider ?: return
        val userId = state.pixivAuthorId ?: return
        val currentlyFollowed = state.pixivAuthorFollowed ?: return
        if (state.isTogglingPixivFollow) return

        _uiState.update { it.copy(isTogglingPixivFollow = true) }
        viewModelScope.launch {
            plugin.setAuthorFollowed(userId, followed = !currentlyFollowed)
                .onSuccess {
                    pixivAuthorFollowStates[userId] = !currentlyFollowed
                    _uiState.update {
                        it.copy(
                            pixivAuthorFollowed = !currentlyFollowed,
                            isTogglingPixivFollow = false,
                            pixivFollowedFeed = FeedState(),
                        )
                    }
                    if (state.selectedTab == 0) {
                        loadPixivInitial(PluginFeedKind.FOLLOWED)
                    }
                }
                .onFailure { cause ->
                    _uiState.update { it.copy(isTogglingPixivFollow = false) }
                    ToastManager.showWarning(cause.message ?: "Could not update follow state")
                }
        }
    }

    fun loadYandeSearchSupport(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _uiState.update { it.copy(yandeSearchSuggestions = emptyList()) }
            return
        }
        val cacheKey = _uiState.value.platform to trimmed
        yandeSuggestionCache[cacheKey]?.let {
            _uiState.update { state -> state.copy(yandeSearchSuggestions = it) }
            return
        }
        launchMoebooru {
            runCatching { activePlugin()?.searchSupport(trimmed)?.suggestions.orEmpty() }
                .onSuccess { suggestions ->
                    yandeSuggestionCache[cacheKey] = suggestions
                    _uiState.update { it.copy(yandeSearchSuggestions = suggestions) }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _uiState.update { it.copy(yandeSearchSuggestions = emptyList()) }
                }
        }
    }

    fun loadPixivSearchSupport(query: String) {
        val plugin = pixivFollowProvider ?: return
        viewModelScope.launch {
            val support = plugin.searchSupport(query) ?: return@launch
            if (query.isNotBlank()) {
                _uiState.update { it.copy(pixivSearchSuggestions = support.suggestions) }
            } else {
                _uiState.update { it.copy(pixivSearchSuggestions = emptyList()) }
            }
            if (_uiState.value.pixivTrendingTags.isEmpty() && support.trending.isNotEmpty()) {
                _uiState.update { it.copy(pixivTrendingTags = support.trending) }
            }
        }
    }

    fun loadPixivInitial(kind: PluginFeedKind = selectedPixivKind(), query: String? = null) {
        val client = activeFeedSource() ?: return updatePixivError(kind, "Active platform feed is not configured")
        val activeQuery = query ?: _uiState.value.pixivSearchTags.takeIf { kind == PluginFeedKind.SEARCH }
        val activeUserId = _uiState.value.pixivAuthorId.takeIf { kind == PluginFeedKind.AUTHOR_WORKS }
        pixivLoadJob?.cancel()
        updatePixivFeed(kind) { it.copy(isLoading = true, isRefreshing = false, error = null, authRequired = false, page = 1, nextCursor = null) }
        pixivLoadJob = viewModelScope.launch {
            applyIfActive(
                request = {
                    client.load(
                        PluginFeedRequest(kind = kind, query = activeQuery, authorId = activeUserId, refresh = true),
                    )
                },
                apply = { result -> applyPixivResult(kind, result, isAppend = false) },
            )
        }
    }

    fun loadMorePixiv(kind: PluginFeedKind = selectedPixivKind(), query: String? = null) {
        val state = _uiState.value
        val feed = pixivFeed(state, kind)
        val cursor = feed.nextCursor ?: return
        if (feed.isLoading || feed.isLoadingMore) return
        val activeQuery = query ?: state.pixivSearchTags.takeIf { kind == PluginFeedKind.SEARCH }
        val activeUserId = state.pixivAuthorId.takeIf { kind == PluginFeedKind.AUTHOR_WORKS }
        pixivLoadJob?.cancel()
        updatePixivFeed(kind) { it.copy(isLoadingMore = true) }
        pixivLoadJob = viewModelScope.launch {
            applyIfActive(
                request = {
                    activeFeedSource()?.load(
                        PluginFeedRequest(kind = kind, query = activeQuery, authorId = activeUserId, cursor = cursor),
                    ) ?: PluginFeedResult.TransportFailure("Pixiv is not configured")
                },
                apply = { result -> applyPixivResult(kind, result, isAppend = true) },
            )
        }
    }

    fun refreshPixiv(kind: PluginFeedKind = selectedPixivKind(), query: String? = null) {
        val activeQuery = query ?: _uiState.value.pixivSearchTags.takeIf { kind == PluginFeedKind.SEARCH }
        val activeUserId = _uiState.value.pixivAuthorId.takeIf { kind == PluginFeedKind.AUTHOR_WORKS }
        pixivLoadJob?.cancel()
        updatePixivFeed(kind) { it.copy(isRefreshing = true, error = null, authRequired = false, nextCursor = null) }
        pixivLoadJob = viewModelScope.launch {
            applyIfActive(
                request = {
                    activeFeedSource()?.load(
                        PluginFeedRequest(kind = kind, query = activeQuery, authorId = activeUserId, refresh = true),
                    ) ?: PluginFeedResult.TransportFailure("Pixiv is not configured")
                },
                apply = { result -> applyPixivResult(kind, result, isAppend = false) },
            )
        }
    }

    private fun applyPixivResult(kind: PluginFeedKind, result: PluginFeedResult, isAppend: Boolean) {
        when (result) {
            is PluginFeedResult.Success -> {
                val visibleItems = filterPosts(result.page.items, preferences.safeMode.value)
                val hasMore = result.page.nextCursor != null
                val shouldContinue = visibleItems.isEmpty() && hasMore
                updatePixivFeed(kind) {
                    it.copy(
                        posts = if (isAppend) it.posts + visibleItems else visibleItems,
                        isLoading = false,
                        isLoadingMore = false,
                        isRefreshing = false,
                        error = null,
                        authRequired = false,
                        page = if (isAppend) it.page + 1 else 1,
                        hasMore = hasMore,
                        nextCursor = result.page.nextCursor,
                    )
                }
                if (shouldContinue) {
                    loadMorePixiv(kind)
                }
            }
            PluginFeedResult.Empty -> updatePixivFeed(kind) {
                it.copy(
                    posts = if (isAppend) it.posts else emptyList(),
                    isLoading = false,
                    isLoadingMore = false,
                    isRefreshing = false,
                    error = null,
                    authRequired = false,
                    hasMore = false,
                    nextCursor = null,
                )
            }
            PluginFeedResult.AuthRequired -> updatePixivError(kind, "Sign in to Pixiv to continue", authRequired = true)
            is PluginFeedResult.RateLimited -> updatePixivError(
                kind,
                result.retryAfterSeconds?.let { "Pixiv is rate limiting requests; retry in ${it}s" }
                    ?: "Pixiv is rate limiting requests",
            )
            is PluginFeedResult.UpstreamDrift -> updatePixivError(kind, "Pixiv changed its ${result.operation} response")
            is PluginFeedResult.TransportFailure -> updatePixivError(kind, result.message)
        }
    }

    private fun updatePixivError(kind: PluginFeedKind, message: String, authRequired: Boolean = false) {
        android.util.Log.w(
            "LatteExplore",
            "pixiv feed $kind failed: $message (authRequired=$authRequired)",
        )
        updatePixivFeed(kind) {
            it.copy(
                isLoading = false,
                isLoadingMore = false,
                isRefreshing = false,
                error = message,
                authRequired = authRequired,
            )
        }
    }

    private fun updatePixivFeed(kind: PluginFeedKind, transform: (FeedState) -> FeedState) {
        _uiState.update { state ->
            val updated = transform(pixivFeed(state, kind))
            when (kind) {
                PluginFeedKind.POPULAR -> state.copy(pixivPopularFeed = updated)
                PluginFeedKind.FOLLOWED -> state.copy(pixivFollowedFeed = updated)
                PluginFeedKind.FAVORITES -> state.copy(pixivFavoritesFeed = updated)
                PluginFeedKind.SEARCH -> {
                    if (state.pixivSearchTags.isNotBlank()) {
                        pixivSearchCache[state.pixivSearchTags] = updated
                    }
                    state.copy(pixivSearchFeed = updated)
                }
                PluginFeedKind.AUTHOR_WORKS -> {
                    state.pixivAuthorId?.let { authorId ->
                        pixivUserWorksCache[authorId] = updated
                    }
                    state.copy(pixivUserWorksFeed = updated)
                }
                PluginFeedKind.NEWEST, PluginFeedKind.POOLS -> state
            }
        }
    }


    private fun selectedPixivKind(): PluginFeedKind = when (_uiState.value.selectedTab) {
        0 -> PluginFeedKind.FOLLOWED
        2 -> PluginFeedKind.FAVORITES
        else -> when {
            _uiState.value.pixivAuthorId != null -> PluginFeedKind.AUTHOR_WORKS
            _uiState.value.isSearch -> PluginFeedKind.SEARCH
            else -> PluginFeedKind.POPULAR
        }
    }

    private fun ensurePixivFeedLoaded(kind: PluginFeedKind) {
        val feed = pixivFeed(_uiState.value, kind)
        val isLoggedIn = activePlugin()?.isLoggedIn == true
        val hasBlockingError = feed.error != null && !(feed.authRequired && isLoggedIn)
        if (
            feed.posts.isNotEmpty() ||
            feed.isLoading ||
            feed.isRefreshing ||
            hasBlockingError ||
            !feed.hasMore
        ) return
        loadPixivInitial(kind)
    }

    fun clearSearch() {
        pixivLoadJob?.cancel()
        _uiState.update {
            if (it.supportsUserFeeds) {
                it.copy(
                    pixivSearchTags = "",
                    pixivAuthorId = null,
                    pixivAuthorName = null,
                    pixivSearchFeed = FeedState(),
                    pixivUserWorksFeed = FeedState(),
                    activePoolName = null,
                )
            } else {
                it.copy(searchTags = "", searchFeed = FeedState(), activePoolName = null)
            }
        }
    }

    fun refreshSearch() {
        val state = _uiState.value
        if (state.supportsUserFeeds) {
            when {
                state.pixivAuthorId != null -> refreshPixiv(PluginFeedKind.AUTHOR_WORKS)
                state.pixivSearchTags.isNotBlank() -> refreshPixiv(PluginFeedKind.SEARCH, state.pixivSearchTags)
            }
            return
        }
        if (state.searchTags.isBlank()) return

        launchMoebooru {
            _uiState.update { it.copy(searchFeed = it.searchFeed.copy(isRefreshing = true, error = null)) }
            try {
                val posts = loadPosts(PluginFeedKind.SEARCH, query = state.searchTags)
                _uiState.update {
                    it.copy(
                        searchFeed = it.searchFeed.copy(
                            posts = posts,
                            isRefreshing = false,
                            page = 1,
                            hasMore = posts.isNotEmpty(),
                        )
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(searchFeed = it.searchFeed.copy(isRefreshing = false, error = e.message ?: "Failed to refresh"))
                }
            }
        }
    }

    fun retrySearch() {
        val state = _uiState.value
        if (!state.isSearch) return
        if (state.supportsUserFeeds) {
            if (state.pixivAuthorId != null) {
                loadPixivInitial(PluginFeedKind.AUTHOR_WORKS)
            } else {
                loadPixivInitial(PluginFeedKind.SEARCH, state.pixivSearchTags)
            }
        } else {
            search(state.searchTags, state.activePoolName)
        }
    }

    fun openPool(pool: PoolSummary) {
        search(tags = MoebooruTags.pool(pool.id), poolName = "#${pool.id} · ${pool.displayName}")
    }

    fun loadFavoritesInitial(username: String) {
        launchMoebooru {
            _uiState.update { it.copy(favoritesFeed = it.favoritesFeed.copy(isLoading = true, error = null, page = 1)) }
            try {
                val posts = loadPosts(PluginFeedKind.FAVORITES)
                _uiState.update {
                    it.copy(
                        favoritesFeed = it.favoritesFeed.copy(
                            posts = posts,
                            isLoading = false,
                            hasMore = posts.isNotEmpty(),
                        )
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(
                        favoritesFeed = it.favoritesFeed.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to load favorites",
                        )
                    )
                }
            }
        }
    }

    fun loadMoreFavorites(username: String) {
        val feed = _uiState.value.favoritesFeed
        if (feed.isLoading || feed.isLoadingMore || !feed.hasMore) return

        _uiState.update { it.copy(favoritesFeed = it.favoritesFeed.copy(isLoadingMore = true)) }
        launchMoebooru {
            val nextPage = feed.page + 1
            try {
                val newPosts = loadPosts(PluginFeedKind.FAVORITES, page = nextPage)
                _uiState.update {
                    it.copy(
                        favoritesFeed = it.favoritesFeed.copy(
                            posts = it.favoritesFeed.posts + newPosts,
                            page = nextPage,
                            hasMore = newPosts.isNotEmpty(),
                            isLoadingMore = false,
                        )
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(favoritesFeed = it.favoritesFeed.copy(isLoadingMore = false)) }
            }
        }
    }

    fun refreshFavorites(username: String) {
        launchMoebooru {
            _uiState.update { it.copy(favoritesFeed = it.favoritesFeed.copy(isRefreshing = true, error = null)) }
            try {
                val posts = loadPosts(PluginFeedKind.FAVORITES)
                _uiState.update {
                    it.copy(
                        favoritesFeed = it.favoritesFeed.copy(
                            posts = posts,
                            isRefreshing = false,
                            page = 1,
                            hasMore = posts.isNotEmpty(),
                        )
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(favoritesFeed = it.favoritesFeed.copy(isRefreshing = false, error = e.message ?: "Failed to refresh"))
                }
            }
        }
    }

    fun loadPoolsInitial(query: String = "") {
        launchMoebooru {
            _uiState.update { it.copy(poolsFeed = it.poolsFeed.copy(isLoading = true, error = null, page = 1, query = query)) }
            try {
                val pools = loadPools(query.ifBlank { null }, 1)
                _uiState.update {
                    it.copy(
                        poolsFeed = it.poolsFeed.copy(
                            pools = pools,
                            isLoading = false,
                            hasMore = pools.isNotEmpty(),
                        )
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(
                        poolsFeed = it.poolsFeed.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to load pools",
                        )
                    )
                }
            }
        }
    }

    fun loadMorePools() {
        val feed = _uiState.value.poolsFeed
        if (feed.isLoading || feed.isLoadingMore || !feed.hasMore) return

        _uiState.update { it.copy(poolsFeed = it.poolsFeed.copy(isLoadingMore = true)) }
        launchMoebooru {
            val nextPage = feed.page + 1
            try {
                val newPools = loadPools(feed.query.ifBlank { null }, nextPage)
                _uiState.update {
                    it.copy(
                        poolsFeed = it.poolsFeed.copy(
                            pools = it.poolsFeed.pools + newPools,
                            page = nextPage,
                            hasMore = newPools.isNotEmpty(),
                            isLoadingMore = false,
                        )
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(poolsFeed = it.poolsFeed.copy(isLoadingMore = false)) }
            }
        }
    }

    fun refreshPools() {
        val query = _uiState.value.poolsFeed.query
        launchMoebooru {
            _uiState.update { it.copy(poolsFeed = it.poolsFeed.copy(isRefreshing = true, error = null)) }
            try {
                val pools = loadPools(query.ifBlank { null }, 1)
                _uiState.update {
                    it.copy(
                        poolsFeed = it.poolsFeed.copy(
                            pools = pools,
                            isRefreshing = false,
                            page = 1,
                            hasMore = pools.isNotEmpty(),
                        )
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(poolsFeed = it.poolsFeed.copy(isRefreshing = false, error = e.message ?: "Failed to refresh"))
                }
            }
        }
    }

    // Pool list rows show a cover thumbnail (the pool's first post) and the
    // pool's own id -- pool.json has no cover field, so this is a lazy
    // one-shot fetch per pool id, cached so scrolling a row back into view
    // doesn't refetch it.
    fun loadPoolCover(poolId: Long) {
        if (_uiState.value.poolCovers.containsKey(poolId)) return
        launchMoebooru {
            try {
                val posts = loadPosts(PluginFeedKind.SEARCH, query = MoebooruTags.pool(poolId))
                val coverUrl = posts.firstOrNull()?.previewUrl ?: return@launchMoebooru
                _uiState.update { it.copy(poolCovers = it.poolCovers + (poolId to coverUrl)) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Best-effort: the row just shows no thumbnail.
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.supportsUserFeeds) {
            loadMorePixiv()
            return
        }
        when {
            state.isSearch -> loadMoreSearch()
            state.selectedTab == 0 -> loadMorePopular()
            else -> loadMoreNewest()
        }
    }

    fun refresh() {
        val state = _uiState.value
        if (state.supportsUserFeeds) {
            refreshPixiv()
            return
        }
        when {
            state.isSearch -> search(state.searchTags)
            state.selectedTab == 0 -> refreshPopular()
            else -> refreshNewest()
        }
    }

    fun loadInitial() {
        val state = _uiState.value
        if (state.supportsUserFeeds) {
            loadPixivInitial()
            return
        }
        when {
            state.isSearch -> search(state.searchTags)
            state.selectedTab == 0 -> loadPopularInitial()
            else -> loadNewestInitial()
        }
    }
}
