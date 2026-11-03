package com.azusachino.latte.plugin.pixiv

import com.azusachino.latte.data.network.PixivApi
import com.azusachino.latte.data.network.PixivFeedKind
import com.azusachino.latte.data.network.PixivFeedRequest
import com.azusachino.latte.data.network.PixivFeedResult
import com.azusachino.latte.plugin.PluginFeedKind
import com.azusachino.latte.plugin.PluginFeedPage
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

    // Following, search and author works accept `offset`; bookmarks page by
    // max_bookmark_id and ranking is not paged by number here.
    override fun maxDirectPage(kind: PluginFeedKind): Int? = when (kind) {
        PluginFeedKind.FOLLOWED,
        PluginFeedKind.SEARCH,
        PluginFeedKind.AUTHOR_WORKS -> MAX_OFFSET / PAGE_SIZE + 1
        else -> null
    }

    private fun PluginFeedRequest.toPixiv(): PixivFeedRequest = PixivFeedRequest(
        kind = when (kind) {
            PluginFeedKind.POPULAR -> PixivFeedKind.POPULAR
            PluginFeedKind.NEWEST,
            PluginFeedKind.POOLS -> error("Unsupported Pixiv feed kind: $kind")
            PluginFeedKind.FOLLOWED -> PixivFeedKind.FOLLOWED_UPDATES
            PluginFeedKind.FAVORITES -> PixivFeedKind.FAVORITES
            PluginFeedKind.SEARCH -> PixivFeedKind.SEARCH
            PluginFeedKind.AUTHOR_WORKS -> PixivFeedKind.USER_WORKS
        },
        query = query,
        userId = authorId,
        cursor = cursor,
        offset = ((page - 1) * PAGE_SIZE).takeIf { cursor == null && page > 1 && maxDirectPage(kind) != null },
        refresh = refresh,
    )

    private fun PixivFeedResult.toNeutral(): PluginFeedResult = when (this) {
        is PixivFeedResult.Success -> PluginFeedResult.Success(
            page = PluginFeedPage(
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

    internal companion object {
        /** Items per app-API list page. */
        const val PAGE_SIZE = 30

        /** Pixiv rejects list offsets past this. */
        const val MAX_OFFSET = 5000
    }
}
