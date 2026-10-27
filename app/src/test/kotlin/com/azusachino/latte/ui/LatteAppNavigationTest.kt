package com.azusachino.latte.ui

import com.azusachino.latte.data.model.MediaVariant
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.model.PostRating
import com.azusachino.latte.plugin.PlatformId
import com.azusachino.latte.ui.detail.detailPageIndicatorText
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
    fun returningFromAuthorWorksToDetailClearsTheAuthorSearchState() {
        var cleared = false
        val detail = Screen.Detail(posts = listOf(samplePost(1)), initialIndex = 0)
        val authorWorks = Screen.AuthorWorks(authorId = 42L, authorName = "Artist")

        val restored = ScreenStack()
            .push(detail)
            .push(authorWorks)
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
    fun returningFromAuthorWorksToItsExploreParentPreservesSearchState() {
        var cleared = false

        ScreenStack()
            .push(Screen.AuthorWorks(authorId = 42L, authorName = "Artist"))
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
    fun authorWorksBackAndForwardKeepsTheOriginalDetailFeedAndPage() {
        val originalPosts = listOf(samplePost(1), samplePost(2), samplePost(3))
        val authorResults = listOf(samplePost(90), samplePost(91))
        val detail = Screen.Detail(posts = originalPosts, initialIndex = 1)
        var navigation = ScreenStack().push(detail)
            .replaceTop(detail.copy(initialIndex = 1))
            .push(Screen.AuthorWorks(authorId = 42L, authorName = "Artist"))

        assertEquals(Screen.AuthorWorks(authorId = 42L, authorName = "Artist"), navigation.current)
        navigation = navigation.pop()
        val restored = navigation.current as Screen.Detail
        val restoredPosts = detailPostsFor(restored, authorResults)

        assertEquals(originalPosts, restoredPosts)
        assertEquals(2L, restoredPosts[restored.initialIndex].id)
    }

    @Test
    fun detailFeedStillAcceptsMorePostsWhenItsCurrentWorkMatches() {
        val originalPosts = listOf(samplePost(1), samplePost(2))
        val extendedFeed = originalPosts + samplePost(3)
        val detail = Screen.Detail(posts = originalPosts, initialIndex = 1)

        assertEquals(extendedFeed, detailPostsFor(detail, extendedFeed))
    }

    @Test
    fun detailFeedAcceptsExtendedFeedWhenScrolledPastInitialSlice() {
        val initialSlice = (1L..30L).map { samplePost(it) }
        val extendedFeed = (1L..60L).map { samplePost(it) }
        val detail = Screen.Detail(posts = initialSlice, initialIndex = 45)

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

    @Test
    fun detailPageIndicatorFormatsFeedAndMultiPageCorrectly() {
        // Standard feed position
        assertEquals("1 / 40", detailPageIndicatorText(currentPage = 0, totalPosts = 40))
        assertEquals("20 / 40", detailPageIndicatorText(currentPage = 19, totalPosts = 40))
        assertEquals("40 / 40", detailPageIndicatorText(currentPage = 39, totalPosts = 40))

        // Empty feed returns blank
        assertEquals("", detailPageIndicatorText(currentPage = 0, totalPosts = 0))

        // Multi-page post
        assertEquals(
            "5 / 25 · p1/8",
            detailPageIndicatorText(
                currentPage = 4,
                totalPosts = 25,
                isMultiPagePost = true,
                pixivPageIndex = 0,
                postPageCount = 8,
            ),
        )
        assertEquals(
            "5 / 25 · p3/8",
            detailPageIndicatorText(
                currentPage = 4,
                totalPosts = 25,
                isMultiPagePost = true,
                pixivPageIndex = 2,
                postPageCount = 8,
            ),
        )

        // Non multi-page with pageCount > 1 does not append page index
        assertEquals(
            "5 / 25",
            detailPageIndicatorText(
                currentPage = 4,
                totalPosts = 25,
                isMultiPagePost = false,
                pixivPageIndex = 2,
                postPageCount = 8,
            ),
        )

        // Boundary clamping
        assertEquals("1 / 10", detailPageIndicatorText(currentPage = -5, totalPosts = 10))
        assertEquals("10 / 10", detailPageIndicatorText(currentPage = 99, totalPosts = 10))
    }

    @Test
    fun returningFromTagSearchToDetailPreservesParentSearchContext() {
        var restoredDetail: Screen.Detail? = null
        val detailWithParentSearch = Screen.Detail(
            posts = listOf(samplePost(1)),
            initialIndex = 0,
            parentSearchTags = "genshin",
        )
        val tagSearch = Screen.TagSearch(query = "citlali")

        val restored = ScreenStack()
            .push(detailWithParentSearch)
            .push(tagSearch)
            .popAndRestoreDetailFeed { target -> restoredDetail = target }

        assertEquals(detailWithParentSearch, restored.current)
        assertEquals("genshin", restoredDetail?.parentSearchTags)
    }

    @Test
    fun returningFromAuthorWorksToDetailPreservesParentAuthorContext() {
        var restoredDetail: Screen.Detail? = null
        val detailWithParentAuthor = Screen.Detail(
            posts = listOf(samplePost(1)),
            initialIndex = 0,
            parentAuthorId = 12345L,
            parentAuthorName = "torino",
        )
        val authorWorks = Screen.AuthorWorks(authorId = 99999L, authorName = "other_artist")

        val restored = ScreenStack()
            .push(detailWithParentAuthor)
            .push(authorWorks)
            .popAndRestoreDetailFeed { target -> restoredDetail = target }

        assertEquals(detailWithParentAuthor, restored.current)
        assertEquals(12345L, restoredDetail?.parentAuthorId)
        assertEquals("torino", restoredDetail?.parentAuthorName)
    }

    @Test
    fun screenDetailStateKeyDistinguishesDifferentParentContexts() {
        val post = samplePost(1)
        val plainDetail = Screen.Detail(posts = listOf(post), initialIndex = 0)
        val searchDetail = Screen.Detail(posts = listOf(post), initialIndex = 0, parentSearchTags = "tagA")
        val authorDetail = Screen.Detail(posts = listOf(post), initialIndex = 0, parentAuthorId = 777L)
        val authorNameDetail = Screen.Detail(posts = listOf(post), initialIndex = 0, parentAuthorName = "authorX")

        val key1 = plainDetail.stateKey
        val key2 = searchDetail.stateKey
        val key3 = authorDetail.stateKey
        val key4 = authorNameDetail.stateKey

        assertTrue(key1 != key2)
        assertTrue(key1 != key3)
        assertTrue(key1 != key4)
        assertTrue(key2 != key3)
        assertTrue(key3 != key4)
    }

    @Test
    fun stackEntryStateKeysAreUniqueWhenSameScreenPushedMultipleTimes() {
        val tagSearch = Screen.TagSearch(query = "citlali")
        val detail = Screen.Detail(posts = listOf(samplePost(1)), initialIndex = 0)

        val stack1 = ScreenStack().push(tagSearch)
        val key1 = stack1.currentStateKey

        val stack2 = stack1.push(detail).push(tagSearch)
        val key2 = stack2.currentStateKey

        assertEquals("1:tag-search:citlali", key1)
        assertEquals("3:tag-search:citlali", key2)
        assertTrue(key1 != key2)
    }

    @Test
    fun predictiveBackScaleAndCornerRadiusInterpolateProperly() {
        fun scaleFor(progress: Float) = 1f - (progress * 0.08f)
        fun cornerRadiusFor(progress: Float) = progress * 24f

        // Initial state at gesture start
        assertEquals(1.0f, scaleFor(0f), 0.001f)
        assertEquals(0f, cornerRadiusFor(0f), 0.001f)

        // Mid-gesture peek
        assertEquals(0.96f, scaleFor(0.5f), 0.001f)
        assertEquals(12f, cornerRadiusFor(0.5f), 0.001f)

        // Fully dragged to trigger threshold
        assertEquals(0.92f, scaleFor(1.0f), 0.001f)
        assertEquals(24f, cornerRadiusFor(1.0f), 0.001f)
    }
}
