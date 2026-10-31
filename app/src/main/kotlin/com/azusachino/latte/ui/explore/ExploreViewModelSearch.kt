package com.azusachino.latte.ui.explore

import android.app.Application
import android.os.SystemClock
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
import com.azusachino.latte.data.cache.BoundedFifoCache
import com.azusachino.latte.data.settings.LattePreferences
import com.azusachino.latte.plugin.PlatformCapability
import com.azusachino.latte.plugin.PlatformId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal fun ExploreViewModel.cachedYandeSearch(tags: String): FeedState? =
    yandeSearchCache[_uiState.value.platform to tags]

internal fun ExploreViewModel.cachedPixivSearch(query: String): FeedState? = pixivSearchCache[query]
internal fun ExploreViewModel.cachedPixivUserWorks(userId: Long): FeedState? = pixivUserWorksCache[userId]

internal fun ExploreViewModel.loadMoreSearch() {
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
                    posts = it.searchFeed.posts.appendDistinct(newPosts),
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

internal fun ExploreViewModel.search(tags: String, poolName: String? = null) {
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
                it.copy(searchFeed = it.searchFeed.copy(isLoading = false, error = feedError(e)))
            }
        }
    }
}

internal fun ExploreViewModel.loadYandeSearchSupport(query: String) {
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

internal fun ExploreViewModel.loadPixivSearchSupport(query: String) {
    val plugin = followProviderFor(plugins) ?: return
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

internal fun ExploreViewModel.clearSearch() {
    cancelAllPixivJobs()
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

internal fun ExploreViewModel.refreshSearch() {
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
                val updatedFeed = it.searchFeed.copy(
                    posts = posts,
                    isRefreshing = false,
                    page = 1,
                    hasMore = posts.isNotEmpty(),
                )
                // Write the fresh result back, or the next re-search of the
                // same tag would resurrect the stale cached list.
                if (it.searchTags == state.searchTags) {
                    yandeSearchCache[it.platform to state.searchTags] = updatedFeed
                }
                it.copy(searchFeed = updatedFeed)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.update {
                it.copy(searchFeed = it.searchFeed.copy(isRefreshing = false, error = feedError(e)))
            }
        }
    }
}

internal fun ExploreViewModel.retrySearch() {
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

