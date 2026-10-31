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

internal fun ExploreViewModel.retryPixivAfterAuthentication() {
    val state = _uiState.value
    if (!state.supportsUserFeeds) return
    _uiState.update { it.clearStalePixivAuthErrors() }
    val activeKind = _uiState.value.pixivFeedToReloadAfterAuthentication() ?: return
    val activeFeed = pixivFeed(_uiState.value, activeKind)
    if (activeFeed.posts.isEmpty() || activeFeed.authRequired) {
        loadPixivInitial(activeKind)
    }
}

internal fun ExploreViewModel.onPixivLoggedOut() {
    _uiState.update { current ->
        listOf(PluginFeedKind.FOLLOWED, PluginFeedKind.FAVORITES)
            .fold(current) { state, kind -> state.withFeed(PlatformId.PIXIV, kind) { FeedState() } }
            .copy(pixivAuthorFollowed = null)
    }
    pixivAuthorFollowStates.clear()
}

internal fun ExploreViewModel.searchPixiv(query: String) {
    val trimmed = query.trim()
    if (trimmed.isBlank()) {
        clearSearch()
        return
    }
    val cached = pixivSearchCache[trimmed]
    if (cached != null && cached.posts.isNotEmpty()) {
        cancelAllPixivJobs()
        _uiState.update {
            it.copy(
                pixivSearchTags = trimmed,
                pixivAuthorId = null,
                pixivAuthorName = null,
                activePoolName = null,
            )
                .withFeed(PlatformId.PIXIV, PluginFeedKind.SEARCH) { cached }
                .withFeed(PlatformId.PIXIV, PluginFeedKind.AUTHOR_WORKS) { FeedState() }
        }
        return
    }
    _uiState.update {
        it.copy(
            pixivSearchTags = trimmed,
            pixivAuthorId = null,
            pixivAuthorName = null,
            activePoolName = null,
        )
            .withFeed(PlatformId.PIXIV, PluginFeedKind.AUTHOR_WORKS) { FeedState() }
    }
    loadPixivInitial(PluginFeedKind.SEARCH, trimmed)
}

internal fun ExploreViewModel.loadPixivUserWorks(userId: Long, authorName: String) {
    if (userId <= 0 || authorName.isBlank()) return
    val cached = pixivUserWorksCache[userId]
    if (cached != null && cached.posts.isNotEmpty()) {
        cancelAllPixivJobs()
        _uiState.update {
            it.copy(
                pixivSearchTags = "",
                pixivAuthorId = userId,
                pixivAuthorName = authorName,
                activePoolName = null,
                pixivAuthorFollowed = pixivAuthorFollowStates[userId],
            )
                .withFeed(PlatformId.PIXIV, PluginFeedKind.SEARCH) { FeedState() }
                .withFeed(PlatformId.PIXIV, PluginFeedKind.AUTHOR_WORKS) { cached }
        }
        // Re-check on every open so web-side unfollows are not masked by
        // this session's cached state; the cache above only pre-renders.
        loadPixivFollowState(userId)
        return
    }
    _uiState.update {
        it.copy(
            pixivSearchTags = "",
            pixivAuthorId = userId,
            pixivAuthorName = authorName,
            activePoolName = null,
            pixivAuthorFollowed = pixivAuthorFollowStates[userId],
        )
    }
    loadPixivFollowState(userId)
    loadPixivInitial(PluginFeedKind.AUTHOR_WORKS)
}

internal fun ExploreViewModel.loadPixivFollowState(userId: Long) {
    val plugin = followProviderFor(plugins) ?: return
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

internal fun ExploreViewModel.togglePixivFollow() {
    val state = _uiState.value
    val plugin = followProviderFor(plugins) ?: return
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
                    ).withFeed(PlatformId.PIXIV, PluginFeedKind.FOLLOWED) { FeedState() }
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

internal fun ExploreViewModel.loadPixivInitial(kind: PluginFeedKind = selectedPixivKind(), query: String? = null) {
    val client = activeFeedSource()
        ?: return updatePixivError(kind, feedError(IllegalStateException("Active platform feed is not configured")))
    val activeQuery = query ?: _uiState.value.pixivSearchTags.takeIf { kind == PluginFeedKind.SEARCH }
    val activeUserId = _uiState.value.pixivAuthorId.takeIf { kind == PluginFeedKind.AUTHOR_WORKS }
    cancelPixivJob(kind)
    updatePixivFeed(kind) { it.copy(isLoading = true, isRefreshing = false, error = null, notice = null, authRequired = false, page = 1, nextCursor = null, autoLoadStreak = 0) }
    feedJobs[PlatformId.PIXIV to kind] = viewModelScope.launch {
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

internal fun ExploreViewModel.loadMorePixiv(kind: PluginFeedKind = selectedPixivKind(), query: String? = null) {
    val state = _uiState.value
    val feed = pixivFeed(state, kind)
    val cursor = feed.nextCursor ?: return
    if (feed.isLoading || feed.isLoadingMore) return
    val activeQuery = query ?: state.pixivSearchTags.takeIf { kind == PluginFeedKind.SEARCH }
    val activeUserId = state.pixivAuthorId.takeIf { kind == PluginFeedKind.AUTHOR_WORKS }
    cancelPixivJob(kind)
    updatePixivFeed(kind) { it.copy(isLoadingMore = true) }
    feedJobs[PlatformId.PIXIV to kind] = viewModelScope.launch {
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

internal fun ExploreViewModel.refreshPixiv(kind: PluginFeedKind = selectedPixivKind(), query: String? = null) {
    val activeQuery = query ?: _uiState.value.pixivSearchTags.takeIf { kind == PluginFeedKind.SEARCH }
    val activeUserId = _uiState.value.pixivAuthorId.takeIf { kind == PluginFeedKind.AUTHOR_WORKS }
    cancelPixivJob(kind)
    updatePixivFeed(kind) { it.copy(isRefreshing = true, error = null, notice = null, authRequired = false, nextCursor = null, autoLoadStreak = 0) }
    feedJobs[PlatformId.PIXIV to kind] = viewModelScope.launch {
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

internal fun ExploreViewModel.applyPixivResult(kind: PluginFeedKind, result: PluginFeedResult, isAppend: Boolean) {
    when (result) {
        is PluginFeedResult.Success -> {
            val visibleItems = filterPosts(result.page.items, preferences.safeMode.value)
            val hasMore = result.page.nextCursor != null
            val currentFeed = pixivFeed(_uiState.value, kind)
            val base = if (isAppend) currentFeed.posts else emptyList()
            val merged = base.appendDistinct(visibleItems)
            val addedCount = merged.size - base.size
            val streak = nextEmptyAppendStreak(isAppend, currentFeed.autoLoadStreak, addedCount)
            // Safe Mode can filter an entire page (e.g. an R-18-heavy Following feed), and
            // overlapping cursor pages can repeat one. Auto-fetch the next page a bounded number
            // of times, then stop instead of spinning forever.
            val shouldContinue = shouldAutoLoadNextPage(addedCount, hasMore, streak)
            updatePixivFeed(kind) {
                it.copy(
                    posts = merged,
                    isLoading = false,
                    isLoadingMore = false,
                    isRefreshing = false,
                    error = null,
                    authRequired = false,
                    page = if (isAppend) it.page + 1 else 1,
                    hasMore = hasMore,
                    nextCursor = result.page.nextCursor,
                    autoLoadStreak = streak,
                )
            }
            if (shouldContinue) {
                loadMorePixiv(kind)
            } else if (addedCount == 0 && hasMore) {
                updatePixivFeed(kind) {
                    it.copy(
                        hasMore = false,
                        notice = "No results after $streak pages; Safe Mode may be hiding content",
                    )
                }
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
        PluginFeedResult.AuthRequired -> updatePixivError(
            kind,
            FeedError("Sign in to Pixiv to continue"),
            authRequired = true,
        )
        is PluginFeedResult.RateLimited,
        is PluginFeedResult.UpstreamDrift,
        is PluginFeedResult.TransportFailure -> updatePixivError(kind, feedError(FeedLoadFailure(result)))
    }
}

internal fun ExploreViewModel.updatePixivError(kind: PluginFeedKind, error: FeedError, authRequired: Boolean = false) {
    updatePixivFeed(kind) {
        it.copy(
            isLoading = false,
            isLoadingMore = false,
            isRefreshing = false,
            error = error,
            authRequired = authRequired,
        )
    }
}

internal fun ExploreViewModel.updatePixivFeed(kind: PluginFeedKind, transform: (FeedState) -> FeedState) {
    _uiState.update { state ->
        val nextState = state.withFeed(PlatformId.PIXIV, kind, transform)
        val updated = nextState.feed(PlatformId.PIXIV, kind)
        if (kind == PluginFeedKind.SEARCH && state.pixivSearchTags.isNotBlank()) {
            pixivSearchCache[state.pixivSearchTags] = updated
        } else if (kind == PluginFeedKind.AUTHOR_WORKS && state.pixivAuthorId != null) {
            pixivUserWorksCache[state.pixivAuthorId] = updated
        }
        nextState
    }
}

internal fun ExploreViewModel.selectedPixivKind(): PluginFeedKind = when (_uiState.value.selectedTab) {
    0 -> PluginFeedKind.FOLLOWED
    2 -> PluginFeedKind.FAVORITES
    else -> when {
        _uiState.value.pixivAuthorId != null -> PluginFeedKind.AUTHOR_WORKS
        _uiState.value.isSearch -> PluginFeedKind.SEARCH
        else -> PluginFeedKind.POPULAR
    }
}

internal fun ExploreViewModel.ensurePixivFeedLoaded(kind: PluginFeedKind) {
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

