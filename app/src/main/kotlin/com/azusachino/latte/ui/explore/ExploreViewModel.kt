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
internal class TtlFeedCache<K : Any>(maxSize: Int, private val ttlMs: Long) {
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
    internal val plugins = mutableMapOf<PlatformId, SitePlugin>()
    internal val feedSources = mutableMapOf<PlatformId, PluginFeedSource>()
    internal val poolSources = mutableMapOf<PlatformId, com.azusachino.latte.plugin.PluginPoolSource>()
    // Per-(Platform, FeedKind) jobs: a platform switch or an adjacent-tab load
    // never cancels a different tab's in-flight request.
    internal val feedJobs = mutableMapOf<Pair<PlatformId, PluginFeedKind>, Job>()

    internal fun cancelFeedJob(platform: PlatformId, kind: PluginFeedKind) {
        feedJobs.remove(platform to kind)?.cancel()
    }

    internal fun cancelFeedJobs(platform: PlatformId) {
        val keys = feedJobs.keys.filter { it.first == platform }
        keys.forEach { feedJobs.remove(it)?.cancel() }
    }

    internal fun cancelAllFeedJobs() {
        feedJobs.values.forEach { it.cancel() }
        feedJobs.clear()
    }

    internal fun cancelPixivJob(kind: PluginFeedKind) = cancelFeedJob(PlatformId.PIXIV, kind)
    internal fun cancelAllPixivJobs() = cancelFeedJobs(PlatformId.PIXIV)
    internal var moebooruJob = SupervisorJob(viewModelScope.coroutineContext[Job])
    internal val gridPositions = mutableMapOf<ExploreGridKey, GridPosition>()
    internal val yandeSearchCache = TtlFeedCache<Pair<PlatformId, String>>(maxSize = 30, ttlMs = SEARCH_CACHE_TTL_MS)
    internal val yandeSuggestionCache = BoundedFifoCache<Pair<PlatformId, String>, List<String>>(maxSize = 50)
    internal val pixivSearchCache = TtlFeedCache<String>(maxSize = 30, ttlMs = SEARCH_CACHE_TTL_MS)
    internal val pixivUserWorksCache = TtlFeedCache<Long>(maxSize = 30, ttlMs = SEARCH_CACHE_TTL_MS)
    private val sessionResetObserved = mutableMapOf<PlatformId, SitePlugin>()
    val preferences = LattePreferences(application)

    internal val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    val columnCount: StateFlow<Int> = preferences.columnCount

    internal fun gridPosition(key: ExploreGridKey): GridPosition = gridPositions[key] ?: GridPosition()

    internal fun saveGridPosition(key: ExploreGridKey, position: GridPosition) {
        gridPositions[key] = position
    }

    fun clearSearchCaches() {
        yandeSearchCache.clear()
        yandeSuggestionCache.clear()
        pixivSearchCache.clear()
        pixivUserWorksCache.clear()
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

    fun configurePlugin(plugin: SitePlugin) {
        plugins[plugin.platform] = plugin
        plugin.feedSource?.let { feedSources[plugin.platform] = it }
        plugin.poolSource?.let { poolSources[plugin.platform] = it }
        if (plugin.platform == _uiState.value.platform &&
            PlatformCapability.USER_FEED !in plugin.capabilities &&
            _uiState.value.feed(PluginFeedKind.POPULAR).posts.isEmpty()
        ) {
            loadPopularInitial()
            loadNewestInitial()
        }
        if (PlatformCapability.USER_FEED in plugin.capabilities) {
            observeSessionReset(plugin)
            if (_uiState.value.supportsUserFeeds && _uiState.value.feed(PlatformId.PIXIV, PluginFeedKind.FOLLOWED).posts.isEmpty()) {
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
                listOf(
                    PluginFeedKind.FOLLOWED,
                    PluginFeedKind.FAVORITES,
                    PluginFeedKind.SEARCH,
                    PluginFeedKind.AUTHOR_WORKS,
                ).fold(state) { s, kind -> s.withFeed(PlatformId.PIXIV, kind) { FeedState() } }
            } else {
                state.copy(
                    feeds = state.feeds - platform,
                    poolsFeed = PoolListState(query = state.poolsFeed.query),
                )
            }
        }
        if (_uiState.value.platform == platform) {
            loadPopularInitial()
        }
    }

    fun configurePixiv(plugin: SitePlugin) = configurePlugin(plugin)

    internal fun activePlugin(): SitePlugin? = plugins[_uiState.value.platform]
    internal fun activeFeedSource(): PluginFeedSource? = feedSources[_uiState.value.platform]

    fun selectPlatform(platform: PlatformId) {
        val previous = _uiState.value.platform
        if (previous == platform) return
        cancelAllPixivJobs()
        cancelMoebooruLoads()
        pixivSearchCache.clear()
        pixivUserWorksCache.clear()
        pixivAuthorFollowStates.clear()
        _uiState.update { it.forPlatform(platform) }
        if (PlatformCapability.USER_FEED in platform.capabilities && _uiState.value.feed(PlatformId.PIXIV, PluginFeedKind.FOLLOWED).posts.isEmpty()) {
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
                loadFavoritesInitial()
            }
            else -> {}
        }
    }

    fun selectPopularTab() = selectTab(0)
    fun selectNewestTab() = selectTab(1)

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
                2 -> loadMoreFavorites()
            }
        }
    }

    internal val pixivAuthorFollowStates = mutableMapOf<Long, Boolean>()

    // doesn't refetch it.




    internal companion object {
        /** Re-searches older than this fetch fresh results instead of the cache. */
        const val SEARCH_CACHE_TTL_MS = 60_000L
    }
}
