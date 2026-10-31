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
import java.time.LocalDate

/**
 * Feed cache that stops serving entries once they age past [ttlMs], so a
 * re-search re-fetches instead of freezing old results. Refresh paths must
 * write their fresh result back here or the next read reverts it.
 */
private class TtlFeedCache<K : Any>(maxSize: Int, private val ttlMs: Long) {
    private val entries = BoundedFifoCache<K, Pair<FeedState, Long>>(maxSize)

    operator fun get(key: K): FeedState? {
        val (feed, cachedAt) = entries[key] ?: return null
        return feed.takeIf { SystemClock.elapsedRealtime() - cachedAt < ttlMs }
    }

    operator fun set(key: K, feed: FeedState) {
        entries[key] = feed to SystemClock.elapsedRealtime()
    }

    fun clear() = entries.clear()
}

class ExploreViewModel(application: Application) : AndroidViewModel(application) {
    private val plugins = mutableMapOf<PlatformId, SitePlugin>()
    private val feedSources = mutableMapOf<PlatformId, PluginFeedSource>()
    private val poolSources = mutableMapOf<PlatformId, com.azusachino.latte.plugin.PluginPoolSource>()
    // Per-(Platform, FeedKind) jobs: a platform switch or an adjacent-tab load
    // never cancels a different tab's in-flight request.
    private val feedJobs = mutableMapOf<Pair<PlatformId, PluginFeedKind>, Job>()

    private fun cancelFeedJob(platform: PlatformId, kind: PluginFeedKind) {
        feedJobs.remove(platform to kind)?.cancel()
    }

    private fun cancelFeedJobs(platform: PlatformId) {
        val keys = feedJobs.keys.filter { it.first == platform }
        keys.forEach { feedJobs.remove(it)?.cancel() }
    }

    private fun cancelAllFeedJobs() {
        feedJobs.values.forEach { it.cancel() }
        feedJobs.clear()
    }

    private fun cancelPixivJob(kind: PluginFeedKind) = cancelFeedJob(PlatformId.PIXIV, kind)
    private fun cancelAllPixivJobs() = cancelFeedJobs(PlatformId.PIXIV)
    private var moebooruJob = SupervisorJob(viewModelScope.coroutineContext[Job])
    private val gridPositions = mutableMapOf<ExploreGridKey, GridPosition>()
    private val yandeSearchCache = TtlFeedCache<Pair<PlatformId, String>>(maxSize = 30, ttlMs = SEARCH_CACHE_TTL_MS)
    private val yandeSuggestionCache = BoundedFifoCache<Pair<PlatformId, String>, List<String>>(maxSize = 50)
    private val pixivSearchCache = TtlFeedCache<String>(maxSize = 30, ttlMs = SEARCH_CACHE_TTL_MS)
    private val pixivUserWorksCache = TtlFeedCache<Long>(maxSize = 30, ttlMs = SEARCH_CACHE_TTL_MS)
    private val sessionResetObserved = mutableMapOf<PlatformId, SitePlugin>()
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

    fun clearSearchCaches() {
        yandeSearchCache.clear()
        yandeSuggestionCache.clear()
        pixivSearchCache.clear()
        pixivUserWorksCache.clear()
    }

    private fun launchMoebooru(block: suspend CoroutineScope.() -> Unit): Job =
        viewModelScope.launch(moebooruJob, block = block)

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
        cancelAllPixivJobs()
        yandeSearchCache.clear()
        pixivSearchCache.clear()
        pixivUserWorksCache.clear()
        _uiState.update {
            // Both slot stores reset together (see forPlatform).
            it.copy(
                feeds = emptyMap(),
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
        return result.feedPostsOrThrow(::applyLocalFilters)
    }

    /** Logs the raw cause; only the mapped message reaches the UI. */
    private fun feedError(cause: Throwable): FeedError {
        android.util.Log.w("LatteExplore", "feed load failed: ${cause.message}", cause)
        return cause.toFeedError(_uiState.value.platform.displayName)
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
            observeSessionReset(plugin)
            if (_uiState.value.supportsUserFeeds && _uiState.value.pixivFollowedFeed.posts.isEmpty()) {
                loadPixivInitial(PluginFeedKind.FOLLOWED)
            }
        }
    }

    /**
     * When any plugin transitions to signed-out, drop everything its session
     * produced: in-flight loads, per-platform caches, and rendered feed state.
     * Transport-agnostic by contract -- no plugin family is special-cased here.
     */
    private fun observeSessionReset(plugin: SitePlugin) {
        if (sessionResetObserved.put(plugin.platform, plugin) != null) return
        viewModelScope.launch {
            plugin.isLoggedInFlow.drop(1).filter { !it }.collect {
                onPluginSignedOut(plugin.platform)
            }
        }
    }

    private fun onPluginSignedOut(platform: PlatformId) {
        cancelAllPixivJobs()
        cancelMoebooruLoads()
        pixivSearchCache.clear()
        pixivUserWorksCache.clear()
        yandeSearchCache.clear()
        _uiState.update { state ->
            // Both slot stores reset together (see forPlatform).
            if (platform == PlatformId.PIXIV) {
                state.copy(
                    feeds = state.feeds - PlatformId.PIXIV,
                    pixivFollowedFeed = FeedState(),
                    pixivFavoritesFeed = FeedState(),
                    pixivSearchFeed = FeedState(),
                    pixivUserWorksFeed = FeedState(),
                )
            } else {
                state.copy(
                    feeds = state.feeds - platform,
                    popularFeed = FeedState(),
                    newestFeed = FeedState(),
                    favoritesFeed = FeedState(),
                    searchFeed = FeedState(),
                    poolsFeed = PoolListState(query = state.poolsFeed.query),
                )
            }
        }
        if (_uiState.value.platform == platform) {
            loadPopularInitial()
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
        cancelAllPixivJobs()
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
                val posts = result.feedPostsOrThrow(::applyLocalFilters)
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
                            error = feedError(e),
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
                val posts = result.feedPostsOrThrow(::applyLocalFilters)
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
                            error = feedError(e),
                        )
                    )
                }
            }
        }
    }

    fun loadMoreActiveFeed() {
        val state = _uiState.value
        if (state.isSearch) {
            loadMoreSearch()
            return
        }
        if (state.supportsUserFeeds) {
            val kind = pixivKindForTab(state.selectedTab) ?: PluginFeedKind.POPULAR
            loadMorePixiv(kind)
        } else {
            when (state.selectedTab) {
                0 -> loadMorePopular()
                1 -> loadMoreNewest()
                2 -> {
                    activePlugin()?.getDisplayUsername()?.let(::loadMoreFavorites)
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
                            posts = it.popularFeed.posts.appendDistinct(newPosts),
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
                            posts = it.newestFeed.posts.appendDistinct(newPosts),
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
                    it.copy(popularFeed = it.popularFeed.copy(isRefreshing = false, error = feedError(e)))
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
                    it.copy(newestFeed = it.newestFeed.copy(isRefreshing = false, error = feedError(e)))
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
                    it.copy(searchFeed = it.searchFeed.copy(isLoading = false, error = feedError(e)))
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
            cancelAllPixivJobs()
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
            cancelAllPixivJobs()
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

    fun togglePixivFollow() {
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

    fun loadPixivInitial(kind: PluginFeedKind = selectedPixivKind(), query: String? = null) {
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

    fun loadMorePixiv(kind: PluginFeedKind = selectedPixivKind(), query: String? = null) {
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

    fun refreshPixiv(kind: PluginFeedKind = selectedPixivKind(), query: String? = null) {
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

    private fun applyPixivResult(kind: PluginFeedKind, result: PluginFeedResult, isAppend: Boolean) {
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

    private fun updatePixivError(kind: PluginFeedKind, error: FeedError, authRequired: Boolean = false) {
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

    private fun updatePixivFeed(kind: PluginFeedKind, transform: (FeedState) -> FeedState) {
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
        // Favorites refetches on every landing on its tab; serialize the loads
        // like the Pixiv feeds so a stale page cannot land after a fresh one.
        val platform = _uiState.value.platform
        cancelFeedJob(platform, PluginFeedKind.FAVORITES)
        _uiState.update {
            it.copy(
                favoritesFeed = it.favoritesFeed.copy(
                    isLoading = true,
                    isLoadingMore = false,
                    isRefreshing = false,
                    error = null,
                    page = 1,
                )
            )
        }
        feedJobs[platform to PluginFeedKind.FAVORITES] = launchMoebooru {
            try {
                applyIfActive(
                    request = { loadPosts(PluginFeedKind.FAVORITES) },
                    apply = { posts ->
                        _uiState.update {
                            it.copy(
                                favoritesFeed = it.favoritesFeed.copy(
                                    posts = posts,
                                    isLoading = false,
                                    hasMore = posts.isNotEmpty(),
                                )
                            )
                        }
                    },
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(
                        favoritesFeed = it.favoritesFeed.copy(
                            isLoading = false,
                            error = feedError(e),
                        )
                    )
                }
            }
        }
    }

    fun loadMoreFavorites(username: String) {
        val state = _uiState.value
        val feed = state.favoritesFeed
        if (feed.isLoading || feed.isLoadingMore || !feed.hasMore) return

        val platform = state.platform
        cancelFeedJob(platform, PluginFeedKind.FAVORITES)
        _uiState.update { it.copy(favoritesFeed = it.favoritesFeed.copy(isLoadingMore = true)) }
        val nextPage = feed.page + 1
        feedJobs[platform to PluginFeedKind.FAVORITES] = launchMoebooru {
            try {
                applyIfActive(
                    request = { loadPosts(PluginFeedKind.FAVORITES, page = nextPage) },
                    apply = { newPosts ->
                        _uiState.update {
                            it.copy(
                                favoritesFeed = it.favoritesFeed.copy(
                                    posts = it.favoritesFeed.posts.appendDistinct(newPosts),
                                    page = nextPage,
                                    hasMore = newPosts.isNotEmpty(),
                                    isLoadingMore = false,
                                )
                            )
                        }
                    },
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(favoritesFeed = it.favoritesFeed.copy(isLoadingMore = false)) }
            }
        }
    }

    fun refreshFavorites(username: String) {
        val platform = _uiState.value.platform
        cancelFeedJob(platform, PluginFeedKind.FAVORITES)
        _uiState.update {
            it.copy(
                favoritesFeed = it.favoritesFeed.copy(
                    isRefreshing = true,
                    isLoading = false,
                    isLoadingMore = false,
                    error = null,
                )
            )
        }
        feedJobs[platform to PluginFeedKind.FAVORITES] = launchMoebooru {
            try {
                applyIfActive(
                    request = { loadPosts(PluginFeedKind.FAVORITES) },
                    apply = { posts ->
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
                    },
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(favoritesFeed = it.favoritesFeed.copy(isRefreshing = false, error = feedError(e)))
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
                            error = feedError(e),
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
                    it.copy(poolsFeed = it.poolsFeed.copy(isRefreshing = false, error = feedError(e)))
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
            state.selectedTab == 2 -> activePlugin()?.getDisplayUsername()?.let(::loadMoreFavorites)
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
            state.selectedTab == 2 -> activePlugin()?.getDisplayUsername()?.let(::refreshFavorites)
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
            state.selectedTab == 2 -> activePlugin()?.getDisplayUsername()?.let(::loadFavoritesInitial)
            else -> loadNewestInitial()
        }
    }

    private companion object {
        /** Re-searches older than this fetch fresh results instead of the cache. */
        const val SEARCH_CACHE_TTL_MS = 60_000L
    }
}
