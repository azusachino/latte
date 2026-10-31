package com.azusachino.latte.ui.explore

import com.azusachino.latte.plugin.PlatformId
import com.azusachino.latte.plugin.PluginFeedKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the single feed-state store: no flat mirrors, no divergence. */
class ExploreStateTest {

    @Test
    fun withFeedWritesTheSingleStoreAndFeedReadsItBack() {
        val state = ExploreUiState(platform = PlatformId.YANDE)
            .withFeed(PlatformId.YANDE, PluginFeedKind.FAVORITES) { it.copy(page = 3) }

        assertEquals(3, state.feed(PlatformId.YANDE, PluginFeedKind.FAVORITES).page)
        // no bleed into sibling kinds or platforms
        assertEquals(FeedState(), state.feed(PlatformId.YANDE, PluginFeedKind.POPULAR))
        assertEquals(FeedState(), state.feed(PlatformId.PIXIV, PluginFeedKind.FAVORITES))
    }

    @Test
    fun platformKeyedConvenienceOverloadsTargetTheRightStore() {
        val state = ExploreUiState(platform = PlatformId.PIXIV)
            .withFeed(PlatformId.PIXIV, PluginFeedKind.AUTHOR_WORKS) { it.copy(page = 9) }

        assertEquals(9, state.feed(PlatformId.PIXIV, PluginFeedKind.AUTHOR_WORKS).page)
        assertEquals(9, state.feedState(PluginFeedKind.AUTHOR_WORKS).page)
        assertEquals(9, state.feed(PluginFeedKind.AUTHOR_WORKS).page)
    }

    @Test
    fun forPlatformClearsEveryFeedSlot() {
        val populated = ExploreUiState(platform = PlatformId.YANDE)
            .withFeed(PluginFeedKind.POPULAR) { it.copy(page = 2) }
            .withFeed(PlatformId.PIXIV, PluginFeedKind.FAVORITES) { it.copy(page = 5) }

        val switched = populated.forPlatform(PlatformId.KONACHAN)

        assertEquals(FeedState(), switched.feed(PlatformId.YANDE, PluginFeedKind.POPULAR))
        assertEquals(FeedState(), switched.feed(PlatformId.PIXIV, PluginFeedKind.FAVORITES))
    }

    @Test
    fun clearStalePixivAuthErrorsClearsOnlyAuthRequiredSlots() {
        val state = ExploreUiState(platform = PlatformId.PIXIV)
            .withFeed(PlatformId.PIXIV, PluginFeedKind.FOLLOWED) { it.copy(authRequired = true) }
            .withFeed(PlatformId.PIXIV, PluginFeedKind.FAVORITES) { it.copy(page = 4) }

        val cleared = state.clearStalePixivAuthErrors()

        assertEquals(FeedState(), cleared.feed(PlatformId.PIXIV, PluginFeedKind.FOLLOWED))
        assertEquals(4, cleared.feed(PlatformId.PIXIV, PluginFeedKind.FAVORITES).page)
    }

    @Test
    fun tabGettersReadTheSingleStore() {
        val state = ExploreUiState(platform = PlatformId.YANDE, selectedTab = 2)
            .withFeed(PluginFeedKind.FAVORITES) { it.copy(isLoading = true) }

        assertTrue(state.isLoading)
        assertFalse(state.isRefreshing)
    }
}
