package com.azusachino.latte.ui.explore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.azusachino.latte.data.model.PoolSummary
import com.azusachino.latte.data.model.PopularPeriod
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.network.YandeApi
import com.azusachino.latte.data.settings.LattePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class FeedState(
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val page: Int = 1,
    val hasMore: Boolean = true,
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
    val popularFeed: FeedState = FeedState(),
    val newestFeed: FeedState = FeedState(),
    val favoritesFeed: FeedState = FeedState(),
    val searchFeed: FeedState = FeedState(),
    val poolsFeed: PoolListState = PoolListState(),
    val poolCovers: Map<Long, String> = emptyMap(),
    val searchTags: String = "",
    val activePoolName: String? = null,
    val popularPeriod: PopularPeriod = PopularPeriod.DAY,
    val popularDate: LocalDate = LocalDate.now(),
    val selectedTab: Int = 0,
) {
    val isPopular: Boolean get() = selectedTab == 0 && searchTags.isBlank()
    val isSearch: Boolean get() = searchTags.isNotBlank()

    val posts: List<Post>
        get() = when {
            isSearch -> searchFeed.posts
            selectedTab == 0 -> popularFeed.posts
            selectedTab == 1 -> newestFeed.posts
            selectedTab == 2 -> favoritesFeed.posts
            else -> emptyList()
        }

    val isLoading: Boolean
        get() = when {
            isSearch -> searchFeed.isLoading
            selectedTab == 0 -> popularFeed.isLoading
            selectedTab == 1 -> newestFeed.isLoading
            selectedTab == 2 -> favoritesFeed.isLoading
            else -> false
        }

    val isLoadingMore: Boolean
        get() = when {
            isSearch -> searchFeed.isLoadingMore
            selectedTab == 0 -> popularFeed.isLoadingMore
            selectedTab == 1 -> newestFeed.isLoadingMore
            selectedTab == 2 -> favoritesFeed.isLoadingMore
            else -> false
        }

    val isRefreshing: Boolean
        get() = when {
            isSearch -> searchFeed.isRefreshing
            selectedTab == 0 -> popularFeed.isRefreshing
            selectedTab == 1 -> newestFeed.isRefreshing
            selectedTab == 2 -> favoritesFeed.isRefreshing
            else -> false
        }

    val error: String?
        get() = when {
            isSearch -> searchFeed.error
            selectedTab == 0 -> popularFeed.error
            selectedTab == 1 -> newestFeed.error
            selectedTab == 2 -> favoritesFeed.error
            else -> null
        }
}

class ExploreViewModel(application: Application) : AndroidViewModel(application) {
    private val api = YandeApi()
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
                    search(_uiState.value.searchTags)
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

    fun clearSearch() {
        _uiState.update { it.copy(searchTags = "", searchFeed = FeedState(), activePoolName = null) }
    }

    fun refreshSearch() {
        val state = _uiState.value
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
        when {
            state.isSearch -> loadMoreSearch()
            state.selectedTab == 0 -> loadMorePopular()
            else -> loadMoreNewest()
        }
    }

    fun refresh() {
        val state = _uiState.value
        when {
            state.isSearch -> search(state.searchTags)
            state.selectedTab == 0 -> refreshPopular()
            else -> refreshNewest()
        }
    }

    fun loadInitial() {
        val state = _uiState.value
        when {
            state.isSearch -> search(state.searchTags)
            state.selectedTab == 0 -> loadPopularInitial()
            else -> loadNewestInitial()
        }
    }
}
