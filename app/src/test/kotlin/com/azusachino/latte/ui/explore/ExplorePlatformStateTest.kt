package com.azusachino.latte.ui.explore

import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.model.PostRating
import com.azusachino.latte.data.network.PixivFeedKind
import com.azusachino.latte.plugin.PlatformId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorePlatformStateTest {
    @Test
    fun pixivStateUsesItsOwnFeedAndSearchContext() {
        val post = Post(
            id = 75034219,
            platform = PlatformId.PIXIV,
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
            platform = PlatformId.PIXIV,
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
    fun pixivAuthenticationReloadsTheSelectedFeed() {
        assertEquals(
            PixivFeedKind.POPULAR,
            ExploreUiState(platform = PlatformId.PIXIV, selectedTab = 0)
                .pixivFeedToReloadAfterAuthentication(),
        )
        assertEquals(
            PixivFeedKind.SEARCH,
            ExploreUiState(platform = PlatformId.PIXIV, pixivSearchTags = "blue hair")
                .pixivFeedToReloadAfterAuthentication(),
        )
        assertEquals(
            PixivFeedKind.FOLLOWED_UPDATES,
            ExploreUiState(platform = PlatformId.PIXIV, selectedTab = 1)
                .pixivFeedToReloadAfterAuthentication(),
        )
        assertEquals(
            PixivFeedKind.FAVORITES,
            ExploreUiState(platform = PlatformId.PIXIV, selectedTab = 2)
                .pixivFeedToReloadAfterAuthentication(),
        )
        assertEquals(
            PixivFeedKind.USER_WORKS,
            ExploreUiState(
                platform = PlatformId.PIXIV,
                pixivAuthorId = 99,
                pixivAuthorName = "Artist",
            ).pixivFeedToReloadAfterAuthentication(),
        )
    }

    @Test
    fun pixivTabsMapToIndependentFeedKinds() {
        assertEquals(PixivFeedKind.POPULAR, pixivKindForTab(0))
        assertEquals(PixivFeedKind.FOLLOWED_UPDATES, pixivKindForTab(1))
        assertEquals(PixivFeedKind.FAVORITES, pixivKindForTab(2))
        assertEquals(null, pixivKindForTab(3))
    }

    @Test
    fun pixivSafeModeKeepsOnlySafeIllustrations() {
        val safe = Post(
            id = 1,
            platform = PlatformId.PIXIV,
            rating = PostRating.SAFE,
            tags = emptyList(),
            score = 0,
            author = "Artist",
            source = null,
            createdAt = null,
            width = 1,
            height = 1,
            previewUrl = "safe",
            sampleUrl = "safe",
            jpegUrl = null,
            originalUrl = "safe",
            variants = emptyList(),
        )
        val explicit = safe.copy(id = 2, rating = PostRating.EXPLICIT)

        assertEquals(listOf(safe), filterPixivPosts(listOf(safe, explicit), safeMode = true))
        assertEquals(listOf(safe, explicit), filterPixivPosts(listOf(safe, explicit), safeMode = false))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun cancelledPixivRequestCannotApplyLateResult() = runTest {
        val response = CompletableDeferred<String>()
        var applied: String? = null
        val request = launch {
            applyIfActive(
                request = { withContext(NonCancellable) { response.await() } },
                apply = { applied = it },
            )
        }

        runCurrent()
        request.cancel()
        response.complete("stale")
        request.join()

        assertEquals(null, applied)
    }
}
