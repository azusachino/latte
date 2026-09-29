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
    fun platformSwitchDropsThePreviousSitesFeedAndSearchState() {
        val previous = ExploreUiState(
            platform = PlatformId.YANDE,
            selectedTab = 2,
            searchTags = "landscape",
            popularFeed = FeedState(isLoading = true),
            searchFeed = FeedState(error = "old site"),
            favoritesFeed = FeedState(error = "old account"),
            poolsFeed = PoolListState(query = "old pool"),
            poolCovers = mapOf(7L to "old cover"),
            pixivFollowedFeed = FeedState(isLoadingMore = true),
        )

        val next = previous.forPlatform(PlatformId.KONACHAN)

        assertEquals(PlatformId.KONACHAN, next.platform)
        assertEquals(0, next.selectedTab)
        assertEquals("", next.searchTags)
        assertEquals(FeedState(), next.popularFeed)
        assertEquals(FeedState(), next.searchFeed)
        assertEquals(FeedState(), next.favoritesFeed)
        assertEquals(PoolListState(), next.poolsFeed)
        assertTrue(next.poolCovers.isEmpty())
        assertEquals(FeedState(), next.pixivFollowedFeed)
    }

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
            pixivFollowedFeed = FeedState(posts = listOf(post.copy(id = 75034218))),
            pixivSearchTags = "blue hair",
            pixivSearchFeed = FeedState(posts = listOf(post.copy(id = 75034220))),
        )

        assertTrue(state.supportsUserFeeds)
        assertEquals("blue hair", state.activeSearchTags)
        assertEquals(75034220L, state.posts.single().id)
        // Tab 0 is Following on Pixiv; Popular is tab 1.
        assertEquals(75034218L, state.copy(pixivSearchTags = "").posts.single().id)
        assertEquals(75034219L, state.copy(pixivSearchTags = "", selectedTab = 1).posts.single().id)
    }

    @Test
    fun pixivAuthenticationReloadsTheSelectedFeed() {
        assertEquals(
            PluginFeedKind.FOLLOWED,
            ExploreUiState(platform = PlatformId.PIXIV, selectedTab = 0)
                .pixivFeedToReloadAfterAuthentication(),
        )
        assertEquals(
            PluginFeedKind.SEARCH,
            ExploreUiState(platform = PlatformId.PIXIV, pixivSearchTags = "blue hair")
                .pixivFeedToReloadAfterAuthentication(),
        )
        assertEquals(
            PluginFeedKind.POPULAR,
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
        assertEquals(PluginFeedKind.FOLLOWED, pixivKindForTab(0))
        assertEquals(PluginFeedKind.POPULAR, pixivKindForTab(1))
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

        assertEquals(listOf(safe), filterPosts(listOf(safe, explicit), safeMode = true))
        assertEquals(listOf(safe, explicit), filterPosts(listOf(safe, explicit), safeMode = false))
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
        val filtered = filterPosts(listOf(explicit1, explicit2), safeMode = true)
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun clearStalePixivAuthErrorsClearsOnlyAuthRequiredFeeds() {
        val post = Post(
            id = 1,
            platform = PlatformId.PIXIV,
            rating = PostRating.SAFE,
            tags = emptyList(),
            score = 0,
            author = "Artist",
            source = null,
            createdAt = null,
            width = 100,
            height = 100,
            previewUrl = "p",
            sampleUrl = "s",
            jpegUrl = null,
            originalUrl = "o",
            variants = emptyList(),
        )
        val state = ExploreUiState(
            platform = PlatformId.PIXIV,
            pixivFollowedFeed = FeedState(error = "Sign in to Pixiv to continue", authRequired = true),
            pixivPopularFeed = FeedState(posts = listOf(post)),
            pixivFavoritesFeed = FeedState(error = "Sign in to Pixiv to continue", authRequired = true),
            pixivSearchFeed = FeedState(error = "Network timeout", authRequired = false),
            pixivUserWorksFeed = FeedState(error = "Sign in to Pixiv to continue", authRequired = true),
        )

        val cleared = state.clearStalePixivAuthErrors()

        // Auth-required feeds are reset so they can reload cleanly
        assertEquals(FeedState(), cleared.pixivFollowedFeed)
        assertEquals(FeedState(), cleared.pixivFavoritesFeed)
        assertEquals(FeedState(), cleared.pixivUserWorksFeed)
        // Non-auth error and successful feeds are preserved
        assertEquals(listOf(post), cleared.pixivPopularFeed.posts)
        assertEquals("Network timeout", cleared.pixivSearchFeed.error)
        assertEquals(false, cleared.pixivSearchFeed.authRequired)
    }

    @Test
    fun pixivAuthRecoveryEnablesFollowingTabReloadAfterLoginFromOtherTabs() {
        // Issue #9 reproduction:
        // 1. Unauthenticated user opens Pixiv (Following feed fails with authRequired = true)
        // 2. User navigates to Popular (loads successfully) and Favorites (tab 2)
        // 3. User logs in while on Favorites tab (selectedTab = 2)
        val stateAfterBrowsing = ExploreUiState(
            platform = PlatformId.PIXIV,
            selectedTab = 2,
            pixivFollowedFeed = FeedState(
                posts = emptyList(),
                error = "Sign in to Pixiv to continue",
                authRequired = true,
            ),
            pixivPopularFeed = FeedState(
                posts = listOf(
                    Post(
                        id = 10,
                        platform = PlatformId.PIXIV,
                        rating = PostRating.SAFE,
                        tags = emptyList(),
                        score = 0,
                        author = "Artist",
                        source = null,
                        createdAt = null,
                        width = 1,
                        height = 1,
                        previewUrl = "p",
                        sampleUrl = "s",
                        jpegUrl = null,
                        originalUrl = "o",
                        variants = emptyList(),
                    )
                ),
            ),
            pixivFavoritesFeed = FeedState(),
        )

        // On authentication, clearing stale errors must unblock Following tab
        val cleared = stateAfterBrowsing.clearStalePixivAuthErrors()
        assertEquals(FeedState(), cleared.pixivFollowedFeed)
        assertEquals(null, cleared.pixivFollowedFeed.error)
        assertEquals(false, cleared.pixivFollowedFeed.authRequired)

        // When switching back to Following (tab 0), kind is FOLLOWED and it has no blocking error
        val followingKind = pixivKindForTab(0)
        assertEquals(PluginFeedKind.FOLLOWED, followingKind)
        val feed = cleared.pixivFollowedFeed
        assertTrue(feed.posts.isEmpty() && feed.error == null && !feed.isLoading)
    }

    @Test
    fun pixivFeedRetrievesCorrectFeedPerKind() {
        val post = Post(
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
            previewUrl = "p",
            sampleUrl = "s",
            jpegUrl = null,
            originalUrl = "o",
            variants = emptyList(),
        )
        val state = ExploreUiState(
            platform = PlatformId.PIXIV,
            pixivFollowedFeed = FeedState(posts = listOf(post.copy(id = 1))),
            pixivPopularFeed = FeedState(posts = listOf(post.copy(id = 2))),
            pixivFavoritesFeed = FeedState(posts = listOf(post.copy(id = 3))),
        )
        assertEquals(1L, pixivFeed(state, PluginFeedKind.FOLLOWED).posts.single().id)
        assertEquals(2L, pixivFeed(state, PluginFeedKind.POPULAR).posts.single().id)
        assertEquals(3L, pixivFeed(state, PluginFeedKind.FAVORITES).posts.single().id)
    }
}
