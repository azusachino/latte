package com.azusachino.latte.plugin.pixiv

import com.azusachino.latte.data.network.PixivApi
import com.azusachino.latte.data.network.PixivFeedKind
import com.azusachino.latte.data.network.PixivFeedRequest
import com.azusachino.latte.data.network.PixivFeedResult
import com.azusachino.latte.plugin.PluginFeedKind
import com.azusachino.latte.plugin.PluginFeedRequest
import com.azusachino.latte.plugin.PluginFeedResult
import com.azusachino.latte.plugin.PluginFeedSource

/**
 * Maps the neutral feed contract onto Pixiv's transport types. This adapter is
 * the only place the two vocabularies meet; everything above it is
 * platform-blind.
 */
internal class PixivFeedSource(private val api: PixivApi) : PluginFeedSource {

    override suspend fun load(request: PluginFeedRequest): PluginFeedResult {
        val result = api.load(request.toPixiv())
        return result.toNeutral()
    }

    private fun PluginFeedRequest.toPixiv(): PixivFeedRequest = PixivFeedRequest(
        kind = when (kind) {
            PluginFeedKind.POPULAR -> PixivFeedKind.POPULAR
            PluginFeedKind.FOLLOWED -> PixivFeedKind.FOLLOWED_UPDATES
            PluginFeedKind.FAVORITES -> PixivFeedKind.FAVORITES
            PluginFeedKind.SEARCH -> PixivFeedKind.SEARCH
            PluginFeedKind.AUTHOR_WORKS -> PixivFeedKind.USER_WORKS
        },
        query = query,
        userId = authorId,
        cursor = cursor,
        refresh = refresh,
    )

    private fun PixivFeedResult.toNeutral(): PluginFeedResult = when (this) {
        is PixivFeedResult.Success -> PluginFeedResult.Success(
            page = com.azusachino.latte.plugin.PluginFeedPage(
                items = page.items,
                nextCursor = page.nextCursor,
            ),
        )
        PixivFeedResult.AuthRequired -> PluginFeedResult.AuthRequired
        is PixivFeedResult.RateLimited -> PluginFeedResult.RateLimited(retryAfterSeconds)
        is PixivFeedResult.UpstreamDrift -> PluginFeedResult.UpstreamDrift(operation)
        is PixivFeedResult.TransportFailure -> PluginFeedResult.TransportFailure(message)
        PixivFeedResult.Empty -> PluginFeedResult.Empty
    }
}
