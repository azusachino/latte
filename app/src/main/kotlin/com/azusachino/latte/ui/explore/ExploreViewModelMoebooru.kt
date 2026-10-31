package com.azusachino.latte.ui.explore

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.azusachino.latte.data.model.PoolSummary
import java.time.LocalDate
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

internal fun ExploreViewModel.launchMoebooru(block: suspend CoroutineScope.() -> Unit): Job =
    viewModelScope.launch(moebooruJob, block = block)

internal fun ExploreViewModel.cancelMoebooruLoads() {
    moebooruJob.cancel()
    moebooruJob = SupervisorJob(viewModelScope.coroutineContext[Job])
}

internal fun ExploreViewModel.applyLocalFilters(posts: List<Post>): List<Post> =
    filterPosts(posts, preferences.safeMode.value)

internal suspend fun ExploreViewModel.loadPools(query: String?, page: Int): List<PoolSummary> =
    poolSources[_uiState.value.platform]?.load(query, page)?.getOrElse { throw it }
        ?: error("Active platform pool source is not configured")

internal suspend fun ExploreViewModel.loadPosts(
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

internal fun ExploreViewModel.feedError(cause: Throwable): FeedError {
    android.util.Log.w("LatteExplore", "feed load failed: ${cause.message}", cause)
    return cause.toFeedError(_uiState.value.platform.displayName)
}

internal fun ExploreViewModel.selectPopularPeriod(period: PluginFeedPeriod) {
    _uiState.update { it.copy(popularPeriod = period) }
    loadPopularInitial()
}

internal fun ExploreViewModel.setPopularDate(date: LocalDate) {
    val today = LocalDate.now()
    val clampedDate = if (date.isAfter(today)) today else date
    _uiState.update { it.copy(popularDate = clampedDate) }
    loadPopularInitial()
}

internal fun ExploreViewModel.shiftPopularDate(amount: Int) {
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

internal fun ExploreViewModel.loadPopularInitial() {
    launchMoebooru {
        _uiState.update { it.withFeed(PluginFeedKind.POPULAR) { f -> f.copy(isLoading = true, error = null, page = 1) } }
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
                val feed = it.feed(PluginFeedKind.POPULAR).copy(posts = posts, isLoading = false, hasMore = posts.isNotEmpty())
                it.withFeed(PluginFeedKind.POPULAR) { f -> feed }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.update {
                it.withFeed(PluginFeedKind.POPULAR) { f -> f.copy(
                    isLoading = false,
                    error = feedError(e),
                )}
            }
        }
    }
}

internal fun ExploreViewModel.loadNewestInitial() {
    launchMoebooru {
        _uiState.update { it.withFeed(PluginFeedKind.NEWEST) { f -> f.copy(isLoading = true, error = null, page = 1) } }
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
                val feed = it.feed(PluginFeedKind.NEWEST).copy(posts = posts, isLoading = false, hasMore = posts.isNotEmpty())
                it.withFeed(PluginFeedKind.NEWEST) { f -> feed }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.update {
                it.withFeed(PluginFeedKind.NEWEST) { f -> f.copy(
                    isLoading = false,
                    error = feedError(e),
                )}
            }
        }
    }
}

internal fun ExploreViewModel.loadMorePopular() {
    val feed = _uiState.value.feed(PluginFeedKind.POPULAR)
    if (feed.isLoading || feed.isLoadingMore || !feed.hasMore) return

    _uiState.update { it.withFeed(PluginFeedKind.POPULAR) { f -> f.copy(isLoadingMore = true) } }
    launchMoebooru {
        val nextPage = feed.page + 1
        try {
            val state = _uiState.value
            val newPosts = loadPosts(PluginFeedKind.POPULAR, page = nextPage, period = state.popularPeriod, date = state.popularDate)
            _uiState.update {
                it.withFeed(PluginFeedKind.POPULAR) { f -> f.copy(
                    posts = it.feed(PluginFeedKind.POPULAR).posts.appendDistinct(newPosts),
                    page = nextPage,
                    hasMore = newPosts.isNotEmpty(),
                    isLoadingMore = false,
                )}
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.update { it.withFeed(PluginFeedKind.POPULAR) { f -> f.copy(isLoadingMore = false) } }
        }
    }
}

internal fun ExploreViewModel.loadMoreNewest() {
    val feed = _uiState.value.feed(PluginFeedKind.NEWEST)
    if (feed.isLoading || feed.isLoadingMore || !feed.hasMore) return

    _uiState.update { it.withFeed(PluginFeedKind.NEWEST) { f -> f.copy(isLoadingMore = true) } }
    launchMoebooru {
        val nextPage = feed.page + 1
        try {
            val newPosts = loadPosts(PluginFeedKind.NEWEST, page = nextPage)
            _uiState.update {
                it.withFeed(PluginFeedKind.NEWEST) { f -> f.copy(
                    posts = it.feed(PluginFeedKind.NEWEST).posts.appendDistinct(newPosts),
                    page = nextPage,
                    hasMore = newPosts.isNotEmpty(),
                    isLoadingMore = false,
                )}
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.update { it.withFeed(PluginFeedKind.NEWEST) { f -> f.copy(isLoadingMore = false) } }
        }
    }
}

internal fun ExploreViewModel.refreshPopular() {
    launchMoebooru {
        _uiState.update { it.withFeed(PluginFeedKind.POPULAR) { f -> f.copy(isRefreshing = true, error = null) } }
        try {
            val state = _uiState.value
            val posts = loadPosts(PluginFeedKind.POPULAR, period = state.popularPeriod, date = state.popularDate)
            _uiState.update {
                it.withFeed(PluginFeedKind.POPULAR) { f -> f.copy(
                    posts = posts,
                    isRefreshing = false,
                    page = 1,
                    hasMore = posts.isNotEmpty(),
                )}
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.update {
                it.withFeed(PluginFeedKind.POPULAR) { f -> f.copy(isRefreshing = false, error = feedError(e)) }
        }
    }
}

}
internal fun ExploreViewModel.refreshNewest() {
    launchMoebooru {
        _uiState.update { it.withFeed(PluginFeedKind.NEWEST) { f -> f.copy(isRefreshing = true, error = null) } }
        try {
            val posts = loadPosts(PluginFeedKind.NEWEST)
            _uiState.update {
                it.withFeed(PluginFeedKind.NEWEST) { f -> f.copy(
                    posts = posts,
                    isRefreshing = false,
                    page = 1,
                    hasMore = posts.isNotEmpty(),
                )}
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.update {
                it.withFeed(PluginFeedKind.NEWEST) { f -> f.copy(isRefreshing = false, error = feedError(e)) }
        }
    }
}

}
internal fun ExploreViewModel.openPool(pool: PoolSummary) {
    search(tags = MoebooruTags.pool(pool.id), poolName = "#${pool.id} · ${pool.displayName}")
}

internal fun ExploreViewModel.loadFavoritesInitial() {
    // Favorites refetches on every landing on its tab; serialize the loads
    // like the Pixiv feeds so a stale page cannot land after a fresh one.
    val platform = _uiState.value.platform
    cancelFeedJob(platform, PluginFeedKind.FAVORITES)
    _uiState.update {
        it.withFeed(PluginFeedKind.FAVORITES) { f -> f.copy(
            isLoading = true,
            isLoadingMore = false,
            isRefreshing = false,
            error = null,
            page = 1,
        )}
    }
    feedJobs[platform to PluginFeedKind.FAVORITES] = launchMoebooru {
        try {
            applyIfActive(
                request = { loadPosts(PluginFeedKind.FAVORITES) },
                apply = { posts ->
                    _uiState.update {
                        it.withFeed(PluginFeedKind.FAVORITES) { f -> f.copy(
                            posts = posts,
                            isLoading = false,
                            hasMore = posts.isNotEmpty(),
                        )}
                    }
                },
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.update {
                it.withFeed(PluginFeedKind.FAVORITES) { f -> f.copy(
                    isLoading = false,
                    error = feedError(e),
                )}
            }
        }
    }
}

internal fun ExploreViewModel.loadMoreFavorites() {
    val state = _uiState.value
    val feed = state.feed(PluginFeedKind.FAVORITES)
    if (feed.isLoading || feed.isLoadingMore || !feed.hasMore) return

    val platform = state.platform
    cancelFeedJob(platform, PluginFeedKind.FAVORITES)
    _uiState.update { it.withFeed(PluginFeedKind.FAVORITES) { f -> f.copy(isLoadingMore = true) } }
    val nextPage = feed.page + 1
    feedJobs[platform to PluginFeedKind.FAVORITES] = launchMoebooru {
        try {
            applyIfActive(
                request = { loadPosts(PluginFeedKind.FAVORITES, page = nextPage) },
                apply = { newPosts ->
                    _uiState.update {
                        it.withFeed(PluginFeedKind.FAVORITES) { f -> f.copy(
                            posts = it.feed(PluginFeedKind.FAVORITES).posts.appendDistinct(newPosts),
                            page = nextPage,
                            hasMore = newPosts.isNotEmpty(),
                            isLoadingMore = false,
                        )}
                    }
                },
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.update { it.withFeed(PluginFeedKind.FAVORITES) { f -> f.copy(isLoadingMore = false) } }
        }
    }
}

internal fun ExploreViewModel.refreshFavorites() {
    val platform = _uiState.value.platform
    cancelFeedJob(platform, PluginFeedKind.FAVORITES)
    _uiState.update {
        it.withFeed(PluginFeedKind.FAVORITES) { f -> f.copy(
            isRefreshing = true,
            isLoading = false,
            isLoadingMore = false,
            error = null,
        )}
    }
    feedJobs[platform to PluginFeedKind.FAVORITES] = launchMoebooru {
        try {
            applyIfActive(
                request = { loadPosts(PluginFeedKind.FAVORITES) },
                apply = { posts ->
                    _uiState.update {
                        it.withFeed(PluginFeedKind.FAVORITES) { f -> f.copy(
                            posts = posts,
                            isRefreshing = false,
                            page = 1,
                            hasMore = posts.isNotEmpty(),
                        )}
                    }
                },
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.update {
                it.withFeed(PluginFeedKind.FAVORITES) { f -> f.copy(isRefreshing = false, error = feedError(e)) }
        }
    }
}

}
internal fun ExploreViewModel.loadPoolsInitial(query: String = "") {
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

internal fun ExploreViewModel.loadMorePools() {
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

internal fun ExploreViewModel.refreshPools() {
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

internal fun ExploreViewModel.loadPoolCover(poolId: Long) {
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

