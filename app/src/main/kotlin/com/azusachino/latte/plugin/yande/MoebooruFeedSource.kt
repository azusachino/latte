package com.azusachino.latte.plugin.yande

import com.azusachino.latte.data.model.PopularPeriod
import com.azusachino.latte.data.network.YandeApi
import com.azusachino.latte.plugin.PluginFeedKind
import com.azusachino.latte.plugin.PluginFeedPage
import com.azusachino.latte.plugin.PluginFeedPeriod
import com.azusachino.latte.plugin.PluginFeedRequest
import com.azusachino.latte.plugin.PluginFeedResult
import com.azusachino.latte.plugin.PluginFeedSource
import com.azusachino.latte.plugin.PluginPoolSource

/**
 * Neutral feed adapter for Moebooru-compatible sites.
 *
 * Site identity and HTTP details stay inside the plugin; callers only see
 * PluginFeedRequest/Result. The username provider is deliberately injected so
 * authentication state is not coupled to the feed coordinator.
 */
object MoebooruTags {
    fun pool(id: Long): String = "pool:$id"
    fun safeMode(tags: String?, enabled: Boolean): String? =
        if (enabled) listOfNotNull(tags, "rating:safe").joinToString(" ") else tags
}

class MoebooruFeedSource(
    private val api: YandeApi,
    private val username: () -> String?,
) : PluginFeedSource, PluginPoolSource {
    override suspend fun load(query: String?, page: Int): Result<List<com.azusachino.latte.data.model.PoolSummary>> =
        runCatching { api.getPools(query = query, page = page) }

    override suspend fun load(request: PluginFeedRequest): PluginFeedResult {
        return runCatching {
            val posts = when (request.kind) {
                PluginFeedKind.POPULAR -> api.getPopular(
                    period = when (request.period) {
                        PluginFeedPeriod.DAY -> PopularPeriod.DAY
                        PluginFeedPeriod.WEEK -> PopularPeriod.WEEK
                        PluginFeedPeriod.MONTH -> PopularPeriod.MONTH
                        PluginFeedPeriod.YEAR -> PopularPeriod.YEAR
                    },
                    date = request.date,
                    page = request.page,
                )
                PluginFeedKind.NEWEST -> api.getPosts(page = request.page)
                PluginFeedKind.SEARCH -> api.getPosts(page = request.page, tags = request.query)
                PluginFeedKind.FAVORITES -> {
                    val user = username() ?: return PluginFeedResult.AuthRequired
                    api.getPosts(page = request.page, tags = YandeApi.favoriteTags(user))
                }
                PluginFeedKind.FOLLOWED,
                PluginFeedKind.AUTHOR_WORKS,
                PluginFeedKind.POOLS -> return PluginFeedResult.UpstreamDrift(
                    "Unsupported Moebooru feed kind: ${request.kind}",
                )
            }
            PluginFeedResult.Success(PluginFeedPage(posts))
        }.getOrElse { error ->
            PluginFeedResult.TransportFailure(error.message ?: error::class.simpleName.orEmpty())
        }
    }
}
