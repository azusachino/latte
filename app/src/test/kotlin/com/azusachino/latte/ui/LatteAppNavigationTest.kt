package com.azusachino.latte.ui

import com.azusachino.latte.data.model.MediaVariant
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.model.PostRating
import com.azusachino.latte.plugin.PlatformId
import com.azusachino.latte.ui.explore.dispatchExploreBack
import com.azusachino.latte.ui.explore.shouldHandleSearchBack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatteAppNavigationTest {
    @Test
    fun returningFromTagSearchToDetailClearsTheTagSearchState() {
        var cleared = false
        val detail = Screen.Detail(posts = listOf(samplePost(1)), initialIndex = 0)
        val tagSearch = Screen.TagSearch(query = "tag")

        val restored = ScreenStack()
            .push(detail)
            .push(tagSearch)
            .popAndRestoreDetailFeed { cleared = true }

        assertTrue(cleared)
        assertEquals(detail, restored.current)
    }

    @Test
    fun returningFromTagSearchToItsExploreParentPreservesSearchState() {
        var cleared = false

        ScreenStack()
            .push(Screen.TagSearch(query = "tag"))
            .popAndRestoreDetailFeed { cleared = true }

        assertFalse(cleared)
    }

    @Test
    fun tagSearchBackAndForwardKeepsTheOriginalDetailFeedAndPage() {
        val originalPosts = listOf(samplePost(1), samplePost(2), samplePost(3))
        val firstTagResults = listOf(samplePost(90), samplePost(91))
        val nextTagResults = listOf(samplePost(80), samplePost(81))
        val detail = Screen.Detail(posts = originalPosts, initialIndex = 1)
        var navigation = ScreenStack().push(detail)
            .replaceTop(detail.copy(initialIndex = 1))
            .push(Screen.TagSearch(query = "first-tag"))

        assertEquals(Screen.TagSearch(query = "first-tag"), navigation.current)
        navigation = navigation.pop()
        val restored = navigation.current as Screen.Detail
        val restoredPosts = detailPostsFor(restored, firstTagResults)

        assertEquals(originalPosts, restoredPosts)
        assertEquals(2L, restoredPosts[restored.initialIndex].id)

        // Move forward in the restored detail feed, open another tag, then return again.
        navigation = navigation
            .replaceTop(restored.copy(initialIndex = 2))
            .push(Screen.TagSearch(query = "next-tag"))
            .pop()
        val nextDetail = navigation.current as Screen.Detail
        val nextPosts = detailPostsFor(nextDetail, nextTagResults)

        assertEquals(originalPosts, nextPosts)
        assertEquals(3L, nextPosts[nextDetail.initialIndex].id)
    }

    @Test
    fun detailFeedStillAcceptsMorePostsWhenItsCurrentWorkMatches() {
        val originalPosts = listOf(samplePost(1), samplePost(2))
        val extendedFeed = originalPosts + samplePost(3)
        val detail = Screen.Detail(posts = originalPosts, initialIndex = 1)

        assertEquals(extendedFeed, detailPostsFor(detail, extendedFeed))
    }

    private fun samplePost(id: Long) = Post(
        id = id,
        platform = PlatformId.YANDE,
        rating = PostRating.SAFE,
        tags = emptyList(),
        score = 0,
        author = null,
        source = null,
        createdAt = null,
        width = 1,
        height = 1,
        previewUrl = "preview-$id",
        sampleUrl = "sample-$id",
        jpegUrl = null,
        originalUrl = "original-$id",
        variants = listOf(MediaVariant("preview", "preview-$id", 1, 1)),
    )

    @Test
    fun authorWorksArrowPopsNavigationInsteadOfClearingSearch() {
        var popped = false
        var cleared = false

        dispatchExploreBack(onNestedBack = { popped = true }) { cleared = true }

        assertTrue(popped)
        assertFalse(cleared)
    }

    @Test
    fun authorWorksSearchDoesNotConsumeAppBack() {
        assertTrue(shouldHandleSearchBack(isRootScreen = true, isSearch = true))
        assertFalse(shouldHandleSearchBack(isRootScreen = false, isSearch = true))
    }

    @Test
    fun authorWorksBackReturnsToTheDetailScreen() {
        val detail = Screen.Detail(posts = emptyList(), initialIndex = 2)
        var navigation = ScreenStack()

        val authorWorks = Screen.AuthorWorks(authorId = 42L, authorName = "Artist")
        navigation = navigation.push(detail)
        navigation = navigation.push(authorWorks)

        assertEquals(authorWorks, navigation.current)
        navigation = navigation.pop()
        assertEquals(detail, navigation.current)
        navigation = navigation.pop()
        assertEquals(Screen.Explore, navigation.current)
    }

    @Test
    fun authorWorksBackRestoresExactDetailWorkAndMultiPageIndex() {
        val initialDetail = Screen.Detail(posts = emptyList(), initialIndex = 0, initialPageIndex = 0)
        var navigation = ScreenStack().push(initialDetail)

        // User swiped to post 3, advanced to page 2 (index 2 of multi-page), and opened author works
        val authorWorks = Screen.AuthorWorks(authorId = 42L, authorName = "Artist")
        navigation = navigation
            .replaceTop(initialDetail.copy(initialIndex = 3, initialPageIndex = 2))
            .push(authorWorks)

        assertEquals(authorWorks, navigation.current)
        assertEquals(3, navigation.size)

        navigation = navigation.pop()
        val restored = navigation.current as Screen.Detail
        assertEquals(3, restored.initialIndex)
        assertEquals(2, restored.initialPageIndex)

        navigation = navigation.pop()
        assertEquals(Screen.Explore, navigation.current)
    }

    @Test
    fun tagSearchBackReturnsToTheDetailScreen() {
        val detail = Screen.Detail(posts = emptyList(), initialIndex = 0)
        var navigation = ScreenStack()
            .push(detail)
            .push(Screen.TagSearch(query = "random-tag"))

        assertEquals(Screen.TagSearch(query = "random-tag"), navigation.current)
        navigation = navigation.pop()

        assertEquals(detail, navigation.current)
    }

    @Test
    fun tagSearchBackRestoresExactDetailWorkAndMultiPageIndex() {
        val initialDetail = Screen.Detail(posts = emptyList(), initialIndex = 1, initialPageIndex = 0)
        var navigation = ScreenStack().push(initialDetail)

        // User navigated to page 4 within post 1, then opened tag search
        navigation = navigation
            .replaceTop(initialDetail.copy(initialIndex = 1, initialPageIndex = 4))
            .push(Screen.TagSearch(query = "citlali"))

        assertEquals(Screen.TagSearch(query = "citlali"), navigation.current)
        navigation = navigation.pop()

        val restored = navigation.current as Screen.Detail
        assertEquals(1, restored.initialIndex)
        assertEquals(4, restored.initialPageIndex)
    }

    @Test
    fun deepNavigationStackPreservesFullHistoryThroughNestedDetails() {
        val detail1 = Screen.Detail(posts = emptyList(), initialIndex = 1)
        val authorWorks = Screen.AuthorWorks(authorId = 123L, authorName = "torino")
        val detail2 = Screen.Detail(posts = emptyList(), initialIndex = 2)
        val tagSearch = Screen.TagSearch(query = "citlali")
        val detail3 = Screen.Detail(posts = emptyList(), initialIndex = 3)

        var navigation = ScreenStack()
            .push(detail1)
            .push(authorWorks)
            .push(detail2)
            .push(tagSearch)
            .push(detail3)

        assertEquals(6, navigation.size)
        assertEquals(detail3, navigation.current)

        navigation = navigation.pop()
        assertEquals(5, navigation.size)
        assertEquals(tagSearch, navigation.current)

        navigation = navigation.pop()
        assertEquals(4, navigation.size)
        assertEquals(detail2, navigation.current)

        navigation = navigation.pop()
        assertEquals(3, navigation.size)
        assertEquals(authorWorks, navigation.current)

        navigation = navigation.pop()
        assertEquals(2, navigation.size)
        assertEquals(detail1, navigation.current)

        navigation = navigation.pop()
        assertEquals(1, navigation.size)
        assertEquals(Screen.Explore, navigation.current)
    }
}
