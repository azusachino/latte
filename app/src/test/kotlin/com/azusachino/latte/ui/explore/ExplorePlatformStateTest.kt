package com.azusachino.latte.ui.explore

import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.model.PostRating
import com.azusachino.latte.plugin.PluginFeedKind
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
            PluginFeedKind.POPULAR,
            ExploreUiState(platform = PlatformId.PIXIV, selectedTab = 0)
                .pixivFeedToReloadAfterAuthentication(),
        )
        assertEquals(
            PluginFeedKind.SEARCH,
            ExploreUiState(platform = PlatformId.PIXIV, pixivSearchTags = "blue hair")
                .pixivFeedToReloadAfterAuthentication(),
        )
        assertEquals(
            PluginFeedKind.FOLLOWED,
            ExploreUiState(platform = PlatformId.PIXIV, selectedTab = 1)
                .pixivFeedToReloadAfterAuthentication(),
        )
        assertEquals(
            PluginFeedKind.FAVORITES,
            ExploreUiState(platform = PlatformId.PIXIV, selectedTab = 2)
                .pixivFeedToReloadAfterAuthentication(),
        )
        assertEquals(
            PluginFeedKind.AUTHOR_WORKS,
            ExploreUiState(
                platform = PlatformId.PIXIV,
                pixivAuthorId = 99,
                pixivAuthorName = "Artist",
            ).pixivFeedToReloadAfterAuthentication(),
        )
    }

    @Test
    fun pixivTabsMapToIndependentFeedKinds() {
        assertEquals(PluginFeedKind.POPULAR, pixivKindForTab(0))
        assertEquals(PluginFeedKind.FOLLOWED, pixivKindForTab(1))
        assertEquals(PluginFeedKind.FAVORITES, pixivKindForTab(2))
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

    @Test
    fun blacklistComposesWithSafeModeInTheSharedFilterSeam() {
        val safe = Post(
            id = 1,
            platform = PlatformId.YANDE,
            rating = PostRating.SAFE,
            tags = listOf("landscape"),
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
        val blacklistedSafe = safe.copy(id = 2, tags = listOf("comic"))
        val explicit = safe.copy(id = 3, rating = PostRating.EXPLICIT)
        val blacklist = setOf("comic")
        val all = listOf(safe, blacklistedSafe, explicit)

        // Blacklist alone hides matching tags on any rating.
        assertEquals(listOf(safe, explicit), filterPosts(all, safeMode = false, blacklist = blacklist))
        // Safe Mode alone hides non-safe ratings.
        assertEquals(listOf(safe, blacklistedSafe), filterPosts(all, safeMode = true, blacklist = emptySet()))
        // Composition drops by either rule, not precedence.
        assertEquals(listOf(safe), filterPosts(all, safeMode = true, blacklist = blacklist))
        // Empty filter config short-circuits without copying semantics changes.
        assertEquals(all, filterPosts(all, safeMode = false, blacklist = emptySet()))
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

    @Test
    fun exploreUiStatePreservesCachedFeedsAcrossSearchTransitions() {
        val post1 = Post(
            id = 101,
            platform = PlatformId.PIXIV,
            rating = PostRating.SAFE,
            tags = listOf("tag1"),
            score = 0,
            author = "Artist A",
            source = null,
            createdAt = null,
            width = 100,
            height = 100,
            previewUrl = "p1",
            sampleUrl = "s1",
            jpegUrl = null,
            originalUrl = "o1",
            variants = emptyList(),
        )
        val post2 = post1.copy(id = 102, author = "Artist B", tags = listOf("tag2"))

        // Simulating cache restoration in state:
        // Transitioning from AuthorWorks (id=1) to Search ("tag2") and back
        val authorFeed = FeedState(posts = listOf(post1), page = 1, hasMore = false)
        val searchFeed = FeedState(posts = listOf(post2), page = 1, hasMore = false)

        val authorState = ExploreUiState(
            platform = PlatformId.PIXIV,
            pixivAuthorId = 1L,
            pixivAuthorName = "Artist A",
            pixivUserWorksFeed = authorFeed,
        )
        assertEquals(listOf(post1), authorState.posts)
        assertEquals("Artist A", authorState.activeSearchTags)

        val searchState = ExploreUiState(
            platform = PlatformId.PIXIV,
            pixivSearchTags = "tag2",
            pixivSearchFeed = searchFeed,
        )
        assertEquals(listOf(post2), searchState.posts)
        assertEquals("tag2", searchState.activeSearchTags)

        // Restoring author state preserves original author works posts
        val restoredAuthorState = authorState.copy()
        assertEquals(listOf(post1), restoredAuthorState.posts)
        assertEquals(1L, restoredAuthorState.pixivAuthorId)
    }

    @Test
    fun safeModeFilteringAllItemsRetainsEmptyVisibleListForCursorContinuation() {
        val explicit1 = Post(
            id = 1,
            platform = PlatformId.PIXIV,
            rating = PostRating.EXPLICIT,
            tags = emptyList(),
            score = 0,
            author = "Artist",
            source = null,
            createdAt = null,
            width = 1,
            height = 1,
            previewUrl = "exp1",
            sampleUrl = "exp1",
            jpegUrl = null,
            originalUrl = "exp1",
            variants = emptyList(),
        )
        val explicit2 = explicit1.copy(id = 2)
        val filtered = filterPixivPosts(listOf(explicit1, explicit2), safeMode = true)
        assertTrue(filtered.isEmpty())
    }
}
