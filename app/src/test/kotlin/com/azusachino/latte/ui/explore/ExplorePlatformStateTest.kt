package com.azusachino.latte.ui.explore

import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.model.PostRating
import com.azusachino.latte.data.network.PixivFeedKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorePlatformStateTest {
    @Test
    fun pixivStateUsesItsOwnFeedAndSearchContext() {
        val post = Post(
            id = 75034219,
            siteId = "pixiv",
            rating = PostRating.SAFE,
            tags = listOf("original"),
            score = 0,
            author = "Artist",
            source = "https://www.pixiv.net/artworks/75034219",
            createdAt = null,
            width = 1000,
            height = 1400,
            previewUrl = "https://pixiv.cat/75034219.jpg",
            sampleUrl = "https://pixiv.cat/75034219.jpg",
            jpegUrl = null,
            originalUrl = "https://i.pximg.net/original/75034219.jpg",
            variants = emptyList(),
        )
        val state = ExploreUiState(
            platform = ExplorePlatform.PIXIV,
            pixivPopularFeed = FeedState(posts = listOf(post)),
            pixivSearchTags = "blue hair",
            pixivSearchFeed = FeedState(posts = listOf(post.copy(id = 75034220))),
        )

        assertTrue(state.isPixiv)
        assertEquals("blue hair", state.activeSearchTags)
        assertEquals(75034220L, state.posts.single().id)
        assertEquals(75034219L, state.copy(pixivSearchTags = "").posts.single().id)
    }

    @Test
    fun pixivAuthenticationReloadsPopularAndSearchButNotPersonalTabs() {
        assertEquals(
            PixivFeedKind.POPULAR,
            ExploreUiState(platform = ExplorePlatform.PIXIV, selectedTab = 0)
                .pixivFeedToReloadAfterAuthentication(),
        )
        assertEquals(
            PixivFeedKind.SEARCH,
            ExploreUiState(platform = ExplorePlatform.PIXIV, pixivSearchTags = "blue hair")
                .pixivFeedToReloadAfterAuthentication(),
        )
        assertEquals(
            null,
            ExploreUiState(platform = ExplorePlatform.PIXIV, selectedTab = 1)
                .pixivFeedToReloadAfterAuthentication(),
        )
    }
}
