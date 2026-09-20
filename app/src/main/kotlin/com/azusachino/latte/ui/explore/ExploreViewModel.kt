package com.azusachino.latte.ui.explore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

data class ExploreUiState(
    val popularFeed: FeedState = FeedState(),
    val newestFeed: FeedState = FeedState(),
    val searchFeed: FeedState = FeedState(),
    val searchTags: String = "",
    val popularPeriod: PopularPeriod = PopularPeriod.DAY,
    val popularDate: LocalDate = LocalDate.now(),
    val selectedTab: Int = 0, // 0 = Popular, 1 = Newest
) {
    val isPopular: Boolean get() = selectedTab == 0 && searchTags.isBlank()
    val isSearch: Boolean get() = searchTags.isNotBlank()

    val posts: List<Post>
        get() = when {
            isSearch -> searchFeed.posts
            selectedTab == 0 -> popularFeed.posts
            else -> newestFeed.posts
        }

    val isLoading: Boolean
        get() = when {
            isSearch -> searchFeed.isLoading
            selectedTab == 0 -> popularFeed.isLoading
            else -> newestFeed.isLoading
        }

    val isLoadingMore: Boolean
        get() = when {
            isSearch -> searchFeed.isLoadingMore
            selectedTab == 0 -> popularFeed.isLoadingMore
            else -> newestFeed.isLoadingMore
        }

    val isRefreshing: Boolean
        get() = when {
            isSearch -> searchFeed.isRefreshing
            selectedTab == 0 -> popularFeed.isRefreshing
            else -> newestFeed.isRefreshing
        }

    val error: String?
        get() = when {
            isSearch -> searchFeed.error
            selectedTab == 0 -> popularFeed.error
            else -> newestFeed.error
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
        if (!preferences.safeMode.value) return tags?.ifBlank { null }
        return if (tags.isNullOrBlank()) {
            "rating:safe"
        } else if (!tags.contains("rating:")) {
            "$tags rating:safe"
        } else {
            tags
        }
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

    fun search(tags: String) {
        val trimmed = tags.trim()
        if (trimmed.isBlank()) {
            clearSearch()
            return
        }
        _uiState.update { it.copy(searchTags = trimmed) }
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
        _uiState.update { it.copy(searchTags = "", searchFeed = FeedState()) }
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
