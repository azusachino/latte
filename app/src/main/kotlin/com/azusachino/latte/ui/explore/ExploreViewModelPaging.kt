package com.azusachino.latte.ui.explore

import androidx.lifecycle.viewModelScope
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.plugin.PlatformId
import com.azusachino.latte.plugin.PluginFeedKind
import com.azusachino.latte.plugin.PluginFeedRequest
import com.azusachino.latte.plugin.PluginFeedResult
import com.azusachino.latte.ui.common.ToastManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Page mode: a feed shows one numbered page and every page change is a single
 * request that replaces it, so reaching an old page never walks the pages
 * before it. Popular keeps its period/date navigation and stays infinite.
 */
internal fun isPageable(kind: PluginFeedKind): Boolean =
    kind != PluginFeedKind.POPULAR && kind != PluginFeedKind.POOLS

internal fun ExploreViewModel.isPaged(kind: PluginFeedKind): Boolean =
    preferences.pagedBrowsing.value && isPageable(kind)

/** Page controls for [kind] while page mode is on, or null when it scrolls infinitely. */
internal fun ExploreViewModel.feedPager(kind: PluginFeedKind, pagedBrowsing: Boolean): FeedPager? {
    if (!pagedBrowsing || !isPageable(kind)) return null
    return FeedPager(
        maxDirectPage = activeFeedSource()?.maxDirectPage(kind),
        onGoToPage = { page -> goToPage(kind, page) },
    )
}

internal fun ExploreViewModel.goToPage(kind: PluginFeedKind, page: Int) {
    if (_uiState.value.supportsUserFeeds) loadPixivPage(kind, page) else loadMoebooruPage(kind, page)
}

private fun emptyPageNotice(page: Int): String? =
    if (page > 1) "Nothing on page $page" else null

private fun ExploreViewModel.loadPixivPage(kind: PluginFeedKind, page: Int) {
    val state = _uiState.value
    val client = activeFeedSource() ?: return
    val request = pageRequestFor(pixivFeed(state, kind), page, client.maxDirectPage(kind)) ?: return
    val activeQuery = state.pixivSearchTags.takeIf { kind == PluginFeedKind.SEARCH }
    val activeUserId = state.pixivAuthorId.takeIf { kind == PluginFeedKind.AUTHOR_WORKS }
    cancelPixivJob(kind)
    updatePixivFeed(kind) {
        it.copy(isLoading = true, isLoadingMore = false, isRefreshing = false, error = null, authRequired = false)
    }
    feedJobs[PlatformId.PIXIV to kind] = viewModelScope.launch {
        applyIfActive(
            request = {
                client.load(
                    PluginFeedRequest(
                        kind = kind,
                        query = activeQuery,
                        authorId = activeUserId,
                        page = request.page,
                        cursor = request.cursor,
                        refresh = request.page == 1,
                    ),
                )
            },
            apply = { result -> applyPixivPage(kind, request, result) },
        )
    }
}

private fun ExploreViewModel.applyPixivPage(kind: PluginFeedKind, request: PageRequest, result: PluginFeedResult) {
    when (result) {
        is PluginFeedResult.Success -> {
            val posts = emptyList<Post>().appendDistinct(filterPosts(result.page.items, preferences.safeMode.value))
            val next = result.page.nextCursor
            updatePixivFeed(kind) {
                it.copy(
                    posts = posts,
                    isLoading = false,
                    page = request.page,
                    hasMore = next != null,
                    nextCursor = next,
                    autoLoadStreak = 0,
                    pageCursors = it.pageCursors.recordPage(request, next),
                    notice = if (posts.isEmpty()) "Safe Mode hid every post on page ${request.page}" else null,
                )
            }
        }
        PluginFeedResult.Empty -> updatePixivFeed(kind) {
            it.copy(
                posts = emptyList(),
                isLoading = false,
                page = request.page,
                hasMore = false,
                nextCursor = null,
                notice = emptyPageNotice(request.page),
            )
        }
        else -> {
            val shown = pixivFeed(_uiState.value, kind).posts.isNotEmpty()
            if (shown && result !is PluginFeedResult.AuthRequired) {
                // Keep the current page on screen; the failed page change is a toast.
                updatePixivFeed(kind) { it.copy(isLoading = false) }
                ToastManager.showWarning(feedError(FeedLoadFailure(result)).toastText())
            } else {
                applyPixivResult(kind, result, isAppend = false)
            }
        }
    }
}

private fun ExploreViewModel.loadMoebooruPage(kind: PluginFeedKind, page: Int) {
    val state = _uiState.value
    val platform = state.platform
    val maxDirectPage = activeFeedSource()?.maxDirectPage(kind)
    val request = pageRequestFor(state.feed(kind), page, maxDirectPage) ?: return
    val query = state.searchTags.takeIf { kind == PluginFeedKind.SEARCH }
    if (kind == PluginFeedKind.SEARCH && query.isNullOrBlank()) return
    cancelFeedJob(platform, kind)
    _uiState.update {
        it.withFeed(kind) { f -> f.copy(isLoading = true, isLoadingMore = false, isRefreshing = false, error = null) }
    }
    feedJobs[platform to kind] = launchMoebooru {
        try {
            applyIfActive(
                request = { loadPosts(kind, page = request.page, query = query) },
                apply = { posts ->
                    _uiState.update {
                        val updated = it.feed(kind).copy(
                            posts = emptyList<Post>().appendDistinct(posts),
                            isLoading = false,
                            page = request.page,
                            hasMore = posts.isNotEmpty(),
                            notice = if (posts.isEmpty()) emptyPageNotice(request.page) else null,
                        )
                        if (kind == PluginFeedKind.SEARCH && query != null && it.searchTags == query) {
                            yandeSearchCache[it.platform to query] = updated
                        }
                        it.withFeed(kind) { updated }
                    }
                },
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val error = feedError(e)
            val shown = _uiState.value.feed(kind).posts.isNotEmpty()
            _uiState.update {
                it.withFeed(kind) { f -> f.copy(isLoading = false, error = if (shown) null else error) }
            }
            if (shown) ToastManager.showWarning(error.toastText())
        }
    }
}

private fun FeedError.toastText(): String = listOfNotNull(title, hint).joinToString(" ")
