package com.azusachino.latte.ui.explore

import com.azusachino.latte.plugin.PluginFeedKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageModeTest {

    @Test
    fun pageOneIsAlwaysAFreshRequest() {
        val feed = FeedState(page = 5, nextCursor = "c6", pageCursors = mapOf(2 to "c2"))
        assertEquals(PageRequest(1, null), pageRequestFor(feed, 1, maxDirectPage = null))
    }

    @Test
    fun nextPageFollowsTheLiveCursor() {
        val feed = FeedState(page = 3, nextCursor = "c4")
        assertEquals(PageRequest(4, "c4"), pageRequestFor(feed, 4, maxDirectPage = 167))
    }

    @Test
    fun visitedPageReusesItsRecordedCursor() {
        val feed = FeedState(page = 3, nextCursor = "c4", pageCursors = mapOf(2 to "c2", 3 to "c3", 4 to "c4"))
        assertEquals(PageRequest(2, "c2"), pageRequestFor(feed, 2, maxDirectPage = null))
    }

    @Test
    fun farPageJumpsByNumberWhenTheSourceAllowsIt() {
        val feed = FeedState(page = 1, nextCursor = "c2")
        assertEquals(PageRequest(40, null), pageRequestFor(feed, 40, maxDirectPage = 167))
    }

    @Test
    fun unreachablePagesAreRejected() {
        val feed = FeedState(page = 1, nextCursor = "c2")
        // Cursor-only source (Pixiv bookmarks): an unvisited page cannot be opened.
        assertNull(pageRequestFor(feed, 7, maxDirectPage = null))
        // Past the source's offset cap.
        assertNull(pageRequestFor(feed, 168, maxDirectPage = 167))
        assertNull(pageRequestFor(feed, 0, maxDirectPage = 167))
    }

    @Test
    fun recordPageKeepsOpeningAndContinuationCursors() {
        val cursors = emptyMap<Int, String>()
            .recordPage(PageRequest(2, "c2"), nextCursor = "c3")
            .recordPage(PageRequest(3, "c3"), nextCursor = null)
        assertEquals(mapOf(2 to "c2", 3 to "c3"), cursors)
    }

    @Test
    fun popularAndPoolsKeepInfiniteScroll() {
        assertFalse(isPageable(PluginFeedKind.POPULAR))
        assertFalse(isPageable(PluginFeedKind.POOLS))
        assertTrue(isPageable(PluginFeedKind.FOLLOWED))
        assertTrue(isPageable(PluginFeedKind.NEWEST))
    }

    @Test
    fun dialInputTypesDeletesAndIgnoresLeadingZero() {
        assertEquals("", dialInput("", DialKey.Digit(0)))
        assertEquals("4", dialInput("", DialKey.Digit(4)))
        assertEquals("40", dialInput("4", DialKey.Digit(0)))
        assertEquals("4", dialInput("40", DialKey.Backspace))
        assertEquals("", dialInput("", DialKey.Backspace))
        assertEquals("123", dialInput("123", DialKey.Digit(9), maxDigits = 3))
    }

    @Test
    fun quickJumpsStayInRange() {
        assertEquals(
            listOf("-1" to 1, "+1" to 3, "+10" to 12, "Last" to 167).drop(0),
            quickJumps(current = 2, maxPage = 167).filter { it.first != "First" },
        )
        assertEquals(listOf("+1" to 2, "+10" to 11), quickJumps(current = 1, maxPage = Int.MAX_VALUE))
        assertEquals(listOf("First" to 1, "-10" to 157, "-1" to 166), quickJumps(current = 167, maxPage = 167))
    }
}
