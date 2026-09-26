package com.azusachino.latte.ui.explore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.azusachino.latte.data.model.PoolSummary
import com.azusachino.latte.data.model.PopularPeriod
import com.azusachino.latte.data.model.FavoriteTag
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.model.PostRating
import com.azusachino.latte.data.network.YandeApi
import com.azusachino.latte.plugin.PluginFeedKind
import com.azusachino.latte.plugin.PluginFeedRequest
import com.azusachino.latte.plugin.PluginFeedResult
import com.azusachino.latte.plugin.PluginFeedSource
import com.azusachino.latte.plugin.SitePlugin
import com.azusachino.latte.ui.common.ToastManager
import com.azusachino.latte.data.settings.LattePreferences
import com.azusachino.latte.plugin.PlatformId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
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
    val popularPeriod: PopularPeriod = PopularPeriod.DAY,
    val popularDate: LocalDate = LocalDate.now(),
    val selectedTab: Int = 0,
) {
    val activeSearchTags: String
        get() = if (platform == PlatformId.PIXIV) {
            pixivAuthorName ?: pixivSearchTags
        } else {
            searchTags
        }

    val isPixiv: Boolean get() = platform == PlatformId.PIXIV
    val isPopular: Boolean get() = selectedTab == 0 && activeSearchTags.isBlank()
    val isSearch: Boolean get() = activeSearchTags.isNotBlank()

    val posts: List<Post>
        get() = when {
            isSearch -> when {
                !isPixiv -> searchFeed.posts
                pixivAuthorId != null -> pixivUserWorksFeed.posts
                else -> pixivSearchFeed.posts
            }
            isPixiv && selectedTab == 0 -> pixivFollowedFeed.posts
            isPixiv && selectedTab == 1 -> pixivPopularFeed.posts
            isPixiv && selectedTab == 2 -> pixivFavoritesFeed.posts
            selectedTab == 0 -> popularFeed.posts
            selectedTab == 1 -> newestFeed.posts
            selectedTab == 2 -> favoritesFeed.posts
            else -> emptyList()
        }

    val isLoading: Boolean
        get() = when {
            isSearch -> when {
                !isPixiv -> searchFeed.isLoading
                pixivAuthorId != null -> pixivUserWorksFeed.isLoading
                else -> pixivSearchFeed.isLoading
            }
            isPixiv && selectedTab == 0 -> pixivFollowedFeed.isLoading
            isPixiv && selectedTab == 1 -> pixivPopularFeed.isLoading
            isPixiv && selectedTab == 2 -> pixivFavoritesFeed.isLoading
            selectedTab == 0 -> popularFeed.isLoading
            selectedTab == 1 -> newestFeed.isLoading
            selectedTab == 2 -> favoritesFeed.isLoading
            else -> false
        }

    val isLoadingMore: Boolean
        get() = when {
            isSearch -> when {
                !isPixiv -> searchFeed.isLoadingMore
                pixivAuthorId != null -> pixivUserWorksFeed.isLoadingMore
                else -> pixivSearchFeed.isLoadingMore
            }
            isPixiv && selectedTab == 0 -> pixivFollowedFeed.isLoadingMore
            isPixiv && selectedTab == 1 -> pixivPopularFeed.isLoadingMore
            isPixiv && selectedTab == 2 -> pixivFavoritesFeed.isLoadingMore
            selectedTab == 0 -> popularFeed.isLoadingMore
            selectedTab == 1 -> newestFeed.isLoadingMore
            selectedTab == 2 -> favoritesFeed.isLoadingMore
            else -> false
        }

    val isRefreshing: Boolean
        get() = when {
            isSearch -> when {
                !isPixiv -> searchFeed.isRefreshing
                pixivAuthorId != null -> pixivUserWorksFeed.isRefreshing
                else -> pixivSearchFeed.isRefreshing
            }
            isPixiv && selectedTab == 0 -> pixivFollowedFeed.isRefreshing
            isPixiv && selectedTab == 1 -> pixivPopularFeed.isRefreshing
            isPixiv && selectedTab == 2 -> pixivFavoritesFeed.isRefreshing
            selectedTab == 0 -> popularFeed.isRefreshing
            selectedTab == 1 -> newestFeed.isRefreshing
            selectedTab == 2 -> favoritesFeed.isRefreshing
            else -> false
        }

    val error: String?
        get() = when {
            isSearch -> when {
                !isPixiv -> searchFeed.error
                pixivAuthorId != null -> pixivUserWorksFeed.error
                else -> pixivSearchFeed.error
            }
            isPixiv && selectedTab == 0 -> pixivFollowedFeed.error
            isPixiv && selectedTab == 1 -> pixivPopularFeed.error
            isPixiv && selectedTab == 2 -> pixivFavoritesFeed.error
            selectedTab == 0 -> popularFeed.error
            selectedTab == 1 -> newestFeed.error
            selectedTab == 2 -> favoritesFeed.error
            else -> null
        }
}

internal fun ExploreUiState.pixivFeedToReloadAfterAuthentication(): PluginFeedKind? = when {
    !isPixiv -> null
    isSearch && pixivAuthorId != null -> PluginFeedKind.AUTHOR_WORKS
    isSearch -> PluginFeedKind.SEARCH
    else -> pixivKindForTab(selectedTab)
}

internal fun pixivKindForTab(tabIndex: Int): PluginFeedKind? = when (tabIndex) {
    0 -> PluginFeedKind.FOLLOWED
    1 -> PluginFeedKind.POPULAR
    2 -> PluginFeedKind.FAVORITES
    else -> null
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
    private val api = YandeApi()
    private val konachanApi = YandeApi(baseUrl = PlatformId.KONACHAN.apiUrl)

    private fun moebooruApi(): YandeApi =
        if (_uiState.value.platform == PlatformId.KONACHAN) konachanApi else api
    private var feedSource: PluginFeedSource? = null
    private var pixivFollowProvider: SitePlugin? = null
    private var pixivLoadJob: Job? = null
    private val gridPositions = mutableMapOf<ExploreGridKey, GridPosition>()
    private val yandeSearchCache = mutableMapOf<String, FeedState>()
    private val yandeSuggestionCache = mutableMapOf<String, List<String>>()
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

    internal fun cachedYandeSearch(tags: String): FeedState? = yandeSearchCache[tags]
    internal fun cachedPixivSearch(query: String): FeedState? = pixivSearchCache[query]
    internal fun cachedPixivUserWorks(userId: Long): FeedState? = pixivUserWorksCache[userId]

    init {
        loadPopularInitial()
        loadNewestInitial()
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
        loadPopularInitial()
        loadNewestInitial()
        pixivLoadJob?.cancel()
        yandeSearchCache.clear()
        pixivSearchCache.clear()
        pixivUserWorksCache.clear()
        _uiState.update {
            it.copy(
                pixivPopularFeed = FeedState(),
                pixivFollowedFeed = FeedState(),
                pixivFavoritesFeed = FeedState(),
                pixivSearchFeed = FeedState(),
                pixivUserWorksFeed = FeedState(),
            )
        }
        val state = _uiState.value
        if (state.isPixiv) {
            when {
                state.pixivAuthorId != null -> loadPixivInitial(PluginFeedKind.AUTHOR_WORKS)
                state.pixivSearchTags.isNotBlank() -> loadPixivInitial(PluginFeedKind.SEARCH, state.pixivSearchTags)
                else -> pixivKindForTab(state.selectedTab)?.let(::loadPixivInitial)
            }
        } else if (state.isSearch) {
            search(state.activeSearchTags)
        }
    }

    private fun applySafeMode(tags: String?): String? {
        return YandeApi.safeModeTags(tags, preferences.safeMode.value)
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

    fun configurePixiv(plugin: SitePlugin) {
        val api = plugin.feedSource ?: return
        if (feedSource === api && pixivFollowProvider === plugin) return
        feedSource = api
        pixivFollowProvider = plugin
        if (_uiState.value.platform == PlatformId.PIXIV && _uiState.value.pixivFollowedFeed.posts.isEmpty()) {
            loadPixivInitial(PluginFeedKind.FOLLOWED)
        }
    }

    fun retryPixivAfterAuthentication() {
        val state = _uiState.value
        val kind = state.pixivFeedToReloadAfterAuthentication() ?: return
        if (!pixivFeed(state, kind).authRequired) return
        loadPixivInitial(kind)
    }

    fun selectPlatform(platform: PlatformId) {
        val previous = _uiState.value.platform
        if (previous == platform) return
        pixivLoadJob?.cancel()
        _uiState.update {
            it.copy(
                platform = platform,
                selectedTab = if (platform == PlatformId.PIXIV) 0 else it.selectedTab,
            )
        }
        if (platform == PlatformId.PIXIV && _uiState.value.pixivFollowedFeed.posts.isEmpty()) {
            loadPixivInitial(PluginFeedKind.FOLLOWED)
        } else if (platform != PlatformId.PIXIV) {
            // A Moebooru switch changes the site under the shared tabs; the
            // cached feeds belong to the previous site, so reload them.
            loadPopularInitial()
            loadNewestInitial()
        }
    }

    fun selectTab(tabIndex: Int) {
        val state = _uiState.value
        _uiState.update { it.copy(selectedTab = tabIndex) }
        if (state.isPixiv && state.activeSearchTags.isBlank()) {
            pixivKindForTab(tabIndex)?.let { kind -> ensurePixivFeedLoaded(kind) }
        }
    }

    fun selectPopularTab() = selectTab(0)
    fun selectNewestTab() = selectTab(1)

    fun selectPopularPeriod(period: PopularPeriod) {
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
            PopularPeriod.DAY -> state.popularDate.plusDays(amount.toLong())
            PopularPeriod.WEEK -> state.popularDate.plusWeeks(amount.toLong())
            PopularPeriod.MONTH -> state.popularDate.plusMonths(amount.toLong())
            PopularPeriod.YEAR -> state.popularDate.plusYears(amount.toLong())
        }
        val today = LocalDate.now()
        val clampedDate = if (nextDate.isAfter(today)) today else nextDate
        _uiState.update { it.copy(popularDate = clampedDate) }
        loadPopularInitial()
    }

    fun loadPopularInitial() {
        viewModelScope.launch {
            _uiState.update { it.copy(popularFeed = it.popularFeed.copy(isLoading = true, error = null, page = 1)) }
            try {
                val state = _uiState.value
                val posts = applyLocalFilters(moebooruApi().getPopular(state.popularPeriod, state.popularDate, page = 1, safeMode = preferences.safeMode.value))
                _uiState.update {
                    it.copy(
                        popularFeed = it.popularFeed.copy(
                            posts = posts,
                            isLoading = false,
                            hasMore = posts.isNotEmpty(),
                        )
                    )
                }
            } catch (e: Exception) {
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
        viewModelScope.launch {
            _uiState.update { it.copy(newestFeed = it.newestFeed.copy(isLoading = true, error = null, page = 1)) }
            try {
                val posts = applyLocalFilters(moebooruApi().getPosts(page = 1, tags = applySafeMode(null)))
                _uiState.update {
                    it.copy(
                        newestFeed = it.newestFeed.copy(
                            posts = posts,
                            isLoading = false,
                            hasMore = posts.isNotEmpty(),
                        )
                    )
                }
            } catch (e: Exception) {
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
        viewModelScope.launch {
            val nextPage = feed.page + 1
            try {
                val state = _uiState.value
                val newPosts = applyLocalFilters(moebooruApi().getPopular(state.popularPeriod, state.popularDate, page = nextPage, safeMode = preferences.safeMode.value))
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
                _uiState.update { it.copy(popularFeed = it.popularFeed.copy(isLoadingMore = false)) }
            }
        }
    }

    fun loadMoreNewest() {
        val feed = _uiState.value.newestFeed
        if (feed.isLoading || feed.isLoadingMore || !feed.hasMore) return

        _uiState.update { it.copy(newestFeed = it.newestFeed.copy(isLoadingMore = true)) }
        viewModelScope.launch {
            val nextPage = feed.page + 1
            try {
                val newPosts = applyLocalFilters(moebooruApi().getPosts(page = nextPage, tags = applySafeMode(null)))
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
                _uiState.update { it.copy(newestFeed = it.newestFeed.copy(isLoadingMore = false)) }
            }
        }
    }

    fun loadMoreSearch() {
        val state = _uiState.value
        if (state.isPixiv) {
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
        viewModelScope.launch {
            val nextPage = feed.page + 1
            val searchKey = state.searchTags
            try {
                val newPosts = applyLocalFilters(moebooruApi().getPosts(page = nextPage, tags = applySafeMode(searchKey)))
                _uiState.update {
                    val updatedFeed = it.searchFeed.copy(
                        posts = it.searchFeed.posts + newPosts,
                        page = nextPage,
                        hasMore = newPosts.isNotEmpty(),
                        isLoadingMore = false,
                    )
                    if (it.searchTags == searchKey) {
                        yandeSearchCache[searchKey] = updatedFeed
                    }
                    it.copy(searchFeed = updatedFeed)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(searchFeed = it.searchFeed.copy(isLoadingMore = false)) }
            }
        }
    }

    fun refreshPopular() {
        viewModelScope.launch {
            _uiState.update { it.copy(popularFeed = it.popularFeed.copy(isRefreshing = true, error = null)) }
            try {
                val state = _uiState.value
                val posts = applyLocalFilters(moebooruApi().getPopular(state.popularPeriod, state.popularDate, page = 1, safeMode = preferences.safeMode.value))
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
                _uiState.update {
                    it.copy(popularFeed = it.popularFeed.copy(isRefreshing = false, error = e.message ?: "Failed to refresh"))
                }
            }
        }
    }

    fun refreshNewest() {
        viewModelScope.launch {
            _uiState.update { it.copy(newestFeed = it.newestFeed.copy(isRefreshing = true, error = null)) }
            try {
                val posts = applyLocalFilters(moebooruApi().getPosts(page = 1, tags = applySafeMode(null)))
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
        if (_uiState.value.isPixiv) {
            searchPixiv(trimmed)
            return
        }
        val cached = yandeSearchCache[trimmed]
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
        viewModelScope.launch {
            _uiState.update { it.copy(searchFeed = it.searchFeed.copy(isLoading = true, error = null, page = 1)) }
            try {
                val posts = applyLocalFilters(moebooruApi().getPosts(page = 1, tags = applySafeMode(trimmed)))
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
                        yandeSearchCache[trimmed] = updatedFeed
                    }
                    it.copy(searchFeed = updatedFeed)
                }
            } catch (e: Exception) {
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
        yandeSuggestionCache[trimmed]?.let {
            _uiState.update { state -> state.copy(yandeSearchSuggestions = it) }
            return
        }
        viewModelScope.launch {
            runCatching { moebooruApi().getTagSuggestions(trimmed) }
                .onSuccess { suggestions ->
                    yandeSuggestionCache[trimmed] = suggestions
                    _uiState.update { it.copy(yandeSearchSuggestions = suggestions) }
                }
                .onFailure { _uiState.update { it.copy(yandeSearchSuggestions = emptyList()) } }
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
        val client = feedSource ?: return updatePixivError(kind, "Pixiv is not configured")
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
                    feedSource?.load(
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
                    feedSource?.load(
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
            }
        }
    }

    private fun pixivFeed(state: ExploreUiState, kind: PluginFeedKind): FeedState = when (kind) {
        PluginFeedKind.POPULAR -> state.pixivPopularFeed
        PluginFeedKind.FOLLOWED -> state.pixivFollowedFeed
        PluginFeedKind.FAVORITES -> state.pixivFavoritesFeed
        PluginFeedKind.SEARCH -> state.pixivSearchFeed
        PluginFeedKind.AUTHOR_WORKS -> state.pixivUserWorksFeed
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
        if (
            feed.posts.isNotEmpty() ||
            feed.isLoading ||
            feed.isRefreshing ||
            feed.error != null ||
            !feed.hasMore
        ) return
        loadPixivInitial(kind)
    }

    fun clearSearch() {
        pixivLoadJob?.cancel()
        _uiState.update {
            if (it.isPixiv) {
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
        if (state.isPixiv) {
            when {
                state.pixivAuthorId != null -> refreshPixiv(PluginFeedKind.AUTHOR_WORKS)
                state.pixivSearchTags.isNotBlank() -> refreshPixiv(PluginFeedKind.SEARCH, state.pixivSearchTags)
            }
            return
        }
        if (state.searchTags.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(searchFeed = it.searchFeed.copy(isRefreshing = true, error = null)) }
            try {
                val posts = applyLocalFilters(moebooruApi().getPosts(page = 1, tags = applySafeMode(state.searchTags)))
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
                _uiState.update {
                    it.copy(searchFeed = it.searchFeed.copy(isRefreshing = false, error = e.message ?: "Failed to refresh"))
                }
            }
        }
    }

    fun retrySearch() {
        val state = _uiState.value
        if (!state.isSearch) return
        if (state.isPixiv) {
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
        search(tags = YandeApi.poolTags(pool.id), poolName = "#${pool.id} · ${pool.displayName}")
    }

    fun loadFavoritesInitial(username: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(favoritesFeed = it.favoritesFeed.copy(isLoading = true, error = null, page = 1)) }
            try {
                val posts = applyLocalFilters(moebooruApi().getPosts(page = 1, tags = applySafeMode(YandeApi.favoriteTags(username))))
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
        viewModelScope.launch {
            val nextPage = feed.page + 1
            try {
                val newPosts = applyLocalFilters(moebooruApi().getPosts(page = nextPage, tags = applySafeMode(YandeApi.favoriteTags(username))))
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
                _uiState.update { it.copy(favoritesFeed = it.favoritesFeed.copy(isLoadingMore = false)) }
            }
        }
    }

    fun refreshFavorites(username: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(favoritesFeed = it.favoritesFeed.copy(isRefreshing = true, error = null)) }
            try {
                val posts = applyLocalFilters(moebooruApi().getPosts(page = 1, tags = applySafeMode(YandeApi.favoriteTags(username))))
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
                _uiState.update {
                    it.copy(favoritesFeed = it.favoritesFeed.copy(isRefreshing = false, error = e.message ?: "Failed to refresh"))
                }
            }
        }
    }

    fun loadPoolsInitial(query: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(poolsFeed = it.poolsFeed.copy(isLoading = true, error = null, page = 1, query = query)) }
            try {
                val pools = moebooruApi().getPools(query = query.ifBlank { null }, page = 1)
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
        viewModelScope.launch {
            val nextPage = feed.page + 1
            try {
                val newPools = moebooruApi().getPools(query = feed.query.ifBlank { null }, page = nextPage)
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
                _uiState.update { it.copy(poolsFeed = it.poolsFeed.copy(isLoadingMore = false)) }
            }
        }
    }

    fun refreshPools() {
        val query = _uiState.value.poolsFeed.query
        viewModelScope.launch {
            _uiState.update { it.copy(poolsFeed = it.poolsFeed.copy(isRefreshing = true, error = null)) }
            try {
                val pools = moebooruApi().getPools(query = query.ifBlank { null }, page = 1)
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
        viewModelScope.launch {
            try {
                val posts = moebooruApi().getPosts(page = 1, limit = 1, tags = YandeApi.poolTags(poolId))
                val coverUrl = posts.firstOrNull()?.previewUrl ?: return@launch
                _uiState.update { it.copy(poolCovers = it.poolCovers + (poolId to coverUrl)) }
            } catch (e: Exception) {
                // Best-effort: the row just shows no thumbnail.
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isPixiv) {
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
        if (state.isPixiv) {
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
        if (state.isPixiv) {
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
