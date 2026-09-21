package com.azusachino.latte.ui.explore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.azusachino.latte.data.model.PoolSummary
import com.azusachino.latte.data.model.PopularPeriod
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.network.YandeApi
import com.azusachino.latte.data.network.PixivApi
import com.azusachino.latte.data.network.PixivFeedKind
import com.azusachino.latte.data.network.PixivFeedRequest
import com.azusachino.latte.data.network.PixivFeedResult
import com.azusachino.latte.data.network.PixivSupportResult
import com.azusachino.latte.data.settings.LattePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class ExplorePlatform {
    YANDE,
    PIXIV,
}

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
    val platform: ExplorePlatform = ExplorePlatform.YANDE,
    val popularFeed: FeedState = FeedState(),
    val newestFeed: FeedState = FeedState(),
    val favoritesFeed: FeedState = FeedState(),
    val searchFeed: FeedState = FeedState(),
    val pixivPopularFeed: FeedState = FeedState(),
    val pixivFollowedFeed: FeedState = FeedState(),
    val pixivFavoritesFeed: FeedState = FeedState(),
    val pixivSearchFeed: FeedState = FeedState(),
    val poolsFeed: PoolListState = PoolListState(),
    val poolCovers: Map<Long, String> = emptyMap(),
    val searchTags: String = "",
    val pixivSearchTags: String = "",
    val pixivSearchSuggestions: List<String> = emptyList(),
    val pixivTrendingTags: List<String> = emptyList(),
    val activePoolName: String? = null,
    val popularPeriod: PopularPeriod = PopularPeriod.DAY,
    val popularDate: LocalDate = LocalDate.now(),
    val selectedTab: Int = 0,
) {
    val activeSearchTags: String
        get() = if (platform == ExplorePlatform.PIXIV) pixivSearchTags else searchTags

    val isPixiv: Boolean get() = platform == ExplorePlatform.PIXIV
    val isPopular: Boolean get() = selectedTab == 0 && activeSearchTags.isBlank()
    val isSearch: Boolean get() = activeSearchTags.isNotBlank()

    val posts: List<Post>
        get() = when {
            isSearch -> if (isPixiv) pixivSearchFeed.posts else searchFeed.posts
            isPixiv && selectedTab == 0 -> pixivPopularFeed.posts
            isPixiv && selectedTab == 1 -> pixivFollowedFeed.posts
            isPixiv && selectedTab == 2 -> pixivFavoritesFeed.posts
            selectedTab == 0 -> popularFeed.posts
            selectedTab == 1 -> newestFeed.posts
            selectedTab == 2 -> favoritesFeed.posts
            else -> emptyList()
        }

    val isLoading: Boolean
        get() = when {
            isSearch -> if (isPixiv) pixivSearchFeed.isLoading else searchFeed.isLoading
            isPixiv && selectedTab == 0 -> pixivPopularFeed.isLoading
            isPixiv && selectedTab == 1 -> pixivFollowedFeed.isLoading
            isPixiv && selectedTab == 2 -> pixivFavoritesFeed.isLoading
            selectedTab == 0 -> popularFeed.isLoading
            selectedTab == 1 -> newestFeed.isLoading
            selectedTab == 2 -> favoritesFeed.isLoading
            else -> false
        }

    val isLoadingMore: Boolean
        get() = when {
            isSearch -> if (isPixiv) pixivSearchFeed.isLoadingMore else searchFeed.isLoadingMore
            isPixiv && selectedTab == 0 -> pixivPopularFeed.isLoadingMore
            isPixiv && selectedTab == 1 -> pixivFollowedFeed.isLoadingMore
            isPixiv && selectedTab == 2 -> pixivFavoritesFeed.isLoadingMore
            selectedTab == 0 -> popularFeed.isLoadingMore
            selectedTab == 1 -> newestFeed.isLoadingMore
            selectedTab == 2 -> favoritesFeed.isLoadingMore
            else -> false
        }

    val isRefreshing: Boolean
        get() = when {
            isSearch -> if (isPixiv) pixivSearchFeed.isRefreshing else searchFeed.isRefreshing
            isPixiv && selectedTab == 0 -> pixivPopularFeed.isRefreshing
            isPixiv && selectedTab == 1 -> pixivFollowedFeed.isRefreshing
            isPixiv && selectedTab == 2 -> pixivFavoritesFeed.isRefreshing
            selectedTab == 0 -> popularFeed.isRefreshing
            selectedTab == 1 -> newestFeed.isRefreshing
            selectedTab == 2 -> favoritesFeed.isRefreshing
            else -> false
        }

    val error: String?
        get() = when {
            isSearch -> if (isPixiv) pixivSearchFeed.error else searchFeed.error
            isPixiv && selectedTab == 0 -> pixivPopularFeed.error
            isPixiv && selectedTab == 1 -> pixivFollowedFeed.error
            isPixiv && selectedTab == 2 -> pixivFavoritesFeed.error
            selectedTab == 0 -> popularFeed.error
            selectedTab == 1 -> newestFeed.error
            selectedTab == 2 -> favoritesFeed.error
            else -> null
        }
}

internal fun ExploreUiState.pixivFeedToReloadAfterAuthentication(): PixivFeedKind? = when {
    !isPixiv -> null
    isSearch -> PixivFeedKind.SEARCH
    selectedTab == 0 -> PixivFeedKind.POPULAR
    else -> null
}

class ExploreViewModel(application: Application) : AndroidViewModel(application) {
    private val api = YandeApi()
    private var pixivApi: PixivApi? = null
    private var pixivLoadJob: Job? = null
    val preferences = LattePreferences(application)

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    val columnCount: StateFlow<Int> = preferences.columnCount

    init {
        loadPopularInitial()
        loadNewestInitial()
        viewModelScope.launch {
            preferences.safeMode.drop(1).collect {
                loadPopularInitial()
                loadNewestInitial()
                if (_uiState.value.isSearch) {
                    search(_uiState.value.activeSearchTags)
                }
            }
        }
    }

    private fun applySafeMode(tags: String?): String? {
        return YandeApi.safeModeTags(tags, preferences.safeMode.value)
    }

    fun cycleColumns(): Int {
        return preferences.cycleColumnCount()
    }

    fun setColumnCount(count: Int) {
        preferences.setColumnCount(count)
    }

    fun configurePixiv(api: PixivApi) {
        if (pixivApi === api) return
        pixivApi = api
        if (_uiState.value.platform == ExplorePlatform.PIXIV && _uiState.value.pixivPopularFeed.posts.isEmpty()) {
            loadPixivInitial(PixivFeedKind.POPULAR)
        }
    }

    fun retryPixivAfterAuthentication() {
        val state = _uiState.value
        val kind = state.pixivFeedToReloadAfterAuthentication() ?: return
        if (!pixivFeed(state, kind).authRequired) return
        loadPixivInitial(kind)
    }

    fun selectPlatform(platform: ExplorePlatform) {
        val previous = _uiState.value.platform
        if (previous == platform) return
        pixivLoadJob?.cancel()
        _uiState.update {
            it.copy(
                platform = platform,
                selectedTab = if (platform == ExplorePlatform.PIXIV) it.selectedTab.coerceAtMost(2) else it.selectedTab,
            )
        }
        if (platform == ExplorePlatform.PIXIV && _uiState.value.pixivPopularFeed.posts.isEmpty()) {
            loadPixivInitial(PixivFeedKind.POPULAR)
        }
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex) }
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
                val posts = api.getPopular(state.popularPeriod, state.popularDate, page = 1, safeMode = preferences.safeMode.value)
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
                val posts = api.getPosts(page = 1, tags = applySafeMode(null))
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
                val newPosts = api.getPopular(state.popularPeriod, state.popularDate, page = nextPage, safeMode = preferences.safeMode.value)
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
                val newPosts = api.getPosts(page = nextPage, tags = applySafeMode(null))
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
            loadMorePixiv(PixivFeedKind.SEARCH, state.pixivSearchTags)
            return
        }
        val feed = state.searchFeed
        if (feed.isLoading || feed.isLoadingMore || !feed.hasMore || state.searchTags.isBlank()) return

        _uiState.update { it.copy(searchFeed = it.searchFeed.copy(isLoadingMore = true)) }
        viewModelScope.launch {
            val nextPage = feed.page + 1
            try {
                val newPosts = api.getPosts(page = nextPage, tags = applySafeMode(state.searchTags))
                _uiState.update {
                    it.copy(
                        searchFeed = it.searchFeed.copy(
                            posts = it.searchFeed.posts + newPosts,
                            page = nextPage,
                            hasMore = newPosts.isNotEmpty(),
                            isLoadingMore = false,
                        )
                    )
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
                val posts = api.getPopular(state.popularPeriod, state.popularDate, page = 1, safeMode = preferences.safeMode.value)
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
                val posts = api.getPosts(page = 1, tags = applySafeMode(null))
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
        _uiState.update { it.copy(searchTags = trimmed, activePoolName = poolName) }
        viewModelScope.launch {
            _uiState.update { it.copy(searchFeed = it.searchFeed.copy(isLoading = true, error = null, page = 1)) }
            try {
                val posts = api.getPosts(page = 1, tags = applySafeMode(trimmed))
                _uiState.update {
                    it.copy(
                        searchFeed = it.searchFeed.copy(
                            posts = posts,
                            isLoading = false,
                            hasMore = posts.isNotEmpty(),
                        )
                    )
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
        _uiState.update { it.copy(pixivSearchTags = trimmed, activePoolName = null) }
        loadPixivInitial(PixivFeedKind.SEARCH, trimmed)
    }

    fun loadPixivSearchSupport(query: String) {
        val client = pixivApi ?: return
        viewModelScope.launch {
            if (query.isNotBlank()) {
                when (val result = client.autocomplete(query)) {
                    is PixivSupportResult.Success -> _uiState.update { it.copy(pixivSearchSuggestions = result.values) }
                    else -> Unit
                }
            } else {
                _uiState.update { it.copy(pixivSearchSuggestions = emptyList()) }
            }
            if (_uiState.value.pixivTrendingTags.isEmpty()) {
                when (val result = client.trendingTags()) {
                    is PixivSupportResult.Success -> _uiState.update { it.copy(pixivTrendingTags = result.values) }
                    else -> Unit
                }
            }
        }
    }

    fun loadPixivInitial(kind: PixivFeedKind = selectedPixivKind(), query: String? = null) {
        val client = pixivApi ?: return updatePixivError(kind, "Pixiv is not configured")
        val activeQuery = query ?: _uiState.value.pixivSearchTags.takeIf { kind == PixivFeedKind.SEARCH }
        pixivLoadJob?.cancel()
        updatePixivFeed(kind) { it.copy(isLoading = true, isRefreshing = false, error = null, authRequired = false, page = 1, nextCursor = null) }
        pixivLoadJob = viewModelScope.launch {
            val result = client.load(
                PixivFeedRequest(kind = kind, query = activeQuery, refresh = true),
            )
            if (!isActive) return@launch
            applyPixivResult(kind, result, isAppend = false)
        }
    }

    fun loadMorePixiv(kind: PixivFeedKind = selectedPixivKind(), query: String? = null) {
        val state = _uiState.value
        val feed = pixivFeed(state, kind)
        val cursor = feed.nextCursor ?: return
        if (feed.isLoading || feed.isLoadingMore) return
        val activeQuery = query ?: state.pixivSearchTags.takeIf { kind == PixivFeedKind.SEARCH }
        pixivLoadJob?.cancel()
        updatePixivFeed(kind) { it.copy(isLoadingMore = true) }
        pixivLoadJob = viewModelScope.launch {
            val result = pixivApi?.load(PixivFeedRequest(kind = kind, query = activeQuery, cursor = cursor))
                ?: PixivFeedResult.TransportFailure("Pixiv is not configured")
            if (!isActive) return@launch
            applyPixivResult(kind, result, isAppend = true)
        }
    }

    fun refreshPixiv(kind: PixivFeedKind = selectedPixivKind(), query: String? = null) {
        val activeQuery = query ?: _uiState.value.pixivSearchTags.takeIf { kind == PixivFeedKind.SEARCH }
        pixivLoadJob?.cancel()
        updatePixivFeed(kind) { it.copy(isRefreshing = true, error = null, authRequired = false, nextCursor = null) }
        pixivLoadJob = viewModelScope.launch {
            val result = pixivApi?.load(
                PixivFeedRequest(kind = kind, query = activeQuery, refresh = true),
            ) ?: PixivFeedResult.TransportFailure("Pixiv is not configured")
            if (!isActive) return@launch
            applyPixivResult(kind, result, isAppend = false)
        }
    }

    private fun applyPixivResult(kind: PixivFeedKind, result: PixivFeedResult, isAppend: Boolean) {
        when (result) {
            is PixivFeedResult.Success -> updatePixivFeed(kind) {
                it.copy(
                    posts = if (isAppend) it.posts + result.page.items else result.page.items,
                    isLoading = false,
                    isLoadingMore = false,
                    isRefreshing = false,
                    error = null,
                    authRequired = false,
                    page = if (isAppend) it.page + 1 else 1,
                    hasMore = result.page.nextCursor != null,
                    nextCursor = result.page.nextCursor,
                )
            }
            PixivFeedResult.Empty -> updatePixivFeed(kind) {
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
            PixivFeedResult.AuthRequired -> updatePixivError(kind, "Sign in to Pixiv to continue", authRequired = true)
            is PixivFeedResult.RateLimited -> updatePixivError(
                kind,
                result.retryAfterSeconds?.let { "Pixiv is rate limiting requests; retry in ${it}s" }
                    ?: "Pixiv is rate limiting requests",
            )
            is PixivFeedResult.UpstreamDrift -> updatePixivError(kind, "Pixiv changed its ${result.operation} response")
            is PixivFeedResult.TransportFailure -> updatePixivError(kind, result.message)
        }
    }

    private fun updatePixivError(kind: PixivFeedKind, message: String, authRequired: Boolean = false) {
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

    private fun updatePixivFeed(kind: PixivFeedKind, transform: (FeedState) -> FeedState) {
        _uiState.update { state ->
            val updated = transform(pixivFeed(state, kind))
            when (kind) {
                PixivFeedKind.POPULAR -> state.copy(pixivPopularFeed = updated)
                PixivFeedKind.FOLLOWED_UPDATES -> state.copy(pixivFollowedFeed = updated)
                PixivFeedKind.FAVORITES -> state.copy(pixivFavoritesFeed = updated)
                PixivFeedKind.SEARCH -> state.copy(pixivSearchFeed = updated)
            }
        }
    }

    private fun pixivFeed(state: ExploreUiState, kind: PixivFeedKind): FeedState = when (kind) {
        PixivFeedKind.POPULAR -> state.pixivPopularFeed
        PixivFeedKind.FOLLOWED_UPDATES -> state.pixivFollowedFeed
        PixivFeedKind.FAVORITES -> state.pixivFavoritesFeed
        PixivFeedKind.SEARCH -> state.pixivSearchFeed
    }

    private fun selectedPixivKind(): PixivFeedKind = when (_uiState.value.selectedTab) {
        1 -> PixivFeedKind.FOLLOWED_UPDATES
        2 -> PixivFeedKind.FAVORITES
        else -> if (_uiState.value.isSearch) PixivFeedKind.SEARCH else PixivFeedKind.POPULAR
    }

    fun clearSearch() {
        pixivLoadJob?.cancel()
        _uiState.update {
            if (it.isPixiv) {
                it.copy(pixivSearchTags = "", pixivSearchFeed = FeedState(), activePoolName = null)
            } else {
                it.copy(searchTags = "", searchFeed = FeedState(), activePoolName = null)
            }
        }
    }

    fun refreshSearch() {
        val state = _uiState.value
        if (state.isPixiv) {
            if (state.pixivSearchTags.isNotBlank()) refreshPixiv(PixivFeedKind.SEARCH, state.pixivSearchTags)
            return
        }
        if (state.searchTags.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(searchFeed = it.searchFeed.copy(isRefreshing = true, error = null)) }
            try {
                val posts = api.getPosts(page = 1, tags = applySafeMode(state.searchTags))
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

    fun openPool(pool: PoolSummary) {
        search(tags = YandeApi.poolTags(pool.id), poolName = "#${pool.id} · ${pool.displayName}")
    }

    fun loadFavoritesInitial(username: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(favoritesFeed = it.favoritesFeed.copy(isLoading = true, error = null, page = 1)) }
            try {
                val posts = api.getPosts(page = 1, tags = applySafeMode(YandeApi.favoriteTags(username)))
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
                val newPosts = api.getPosts(page = nextPage, tags = applySafeMode(YandeApi.favoriteTags(username)))
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
                val posts = api.getPosts(page = 1, tags = applySafeMode(YandeApi.favoriteTags(username)))
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
                val pools = api.getPools(query = query.ifBlank { null }, page = 1)
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
                val newPools = api.getPools(query = feed.query.ifBlank { null }, page = nextPage)
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
                val pools = api.getPools(query = query.ifBlank { null }, page = 1)
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
                val posts = api.getPosts(page = 1, limit = 1, tags = YandeApi.poolTags(poolId))
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
