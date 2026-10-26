package com.azusachino.latte.ui.explore

import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.model.PostRating
import com.azusachino.latte.plugin.PlatformId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedPagingTest {

    private fun post(id: Long, pageIndex: Int = 0, platform: PlatformId = PlatformId.PIXIV) = Post(
        id = id,
        platform = platform,
        rating = PostRating.SAFE,
        tags = emptyList(),
        score = 0,
        author = null,
        source = null,
        createdAt = null,
        width = 1000,
        height = 1400,
        previewUrl = "https://example.test/preview.jpg",
        sampleUrl = "https://example.test/sample.jpg",
        jpegUrl = null,
        originalUrl = "https://example.test/original.jpg",
        variants = emptyList(),
        pageIndex = pageIndex,
        pageCount = 3,
    )

    private fun ids(posts: List<Post>) = posts.map { "${it.platform.externalId}:${it.id}:${it.pageIndex}" }

    @Test
    fun appendDistinctKeepsExistingOrderAndAppendsNewPosts() {
        val merged = listOf(post(1), post(2)).appendDistinct(listOf(post(3), post(4)))

        assertEquals(listOf("pixiv:1:0", "pixiv:2:0", "pixiv:3:0", "pixiv:4:0"), ids(merged))
    }

    @Test
    fun appendDistinctDropsPostsAlreadyPresent() {
        val merged = listOf(post(1), post(2)).appendDistinct(listOf(post(2), post(3)))

        assertEquals(listOf("pixiv:1:0", "pixiv:2:0", "pixiv:3:0"), ids(merged))
    }

    @Test
    fun appendDistinctDropsDuplicatesInsideIncoming() {
        val merged = listOf(post(1)).appendDistinct(listOf(post(2), post(2), post(3), post(3)))

        assertEquals(listOf("pixiv:1:0", "pixiv:2:0", "pixiv:3:0"), ids(merged))
    }

    @Test
    fun appendDistinctCountsPixivPagesOfOneWorkAsOneWork() {
        val existing = listOf(post(10, pageIndex = 0))
        val incoming = listOf(post(10, pageIndex = 1), post(10, pageIndex = 2), post(11, pageIndex = 0))

        val merged = existing.appendDistinct(incoming)

        assertEquals(listOf("pixiv:10:0", "pixiv:11:0"), ids(merged))
    }

    @Test
    fun appendDistinctKeepsSameIdFromDifferentPlatforms() {
        val merged = listOf(post(7, platform = PlatformId.PIXIV))
            .appendDistinct(listOf(post(7, platform = PlatformId.YANDE)))

        assertEquals(listOf("pixiv:7:0", "yande.re:7:0"), ids(merged))
    }

    @Test
    fun appendDistinctHandlesEmptyInputs() {
        assertEquals(emptyList<Post>(), emptyList<Post>().appendDistinct(emptyList()))
        assertEquals(ids(listOf(post(1))), ids(emptyList<Post>().appendDistinct(listOf(post(1)))))
        assertEquals(ids(listOf(post(1))), ids(listOf(post(1)).appendDistinct(emptyList())))
    }

    @Test
    fun streakCountsOnlyAppendsThatAddNothing() {
        assertEquals(1, nextEmptyAppendStreak(isAppend = true, previousStreak = 0, addedCount = 0))
        assertEquals(3, nextEmptyAppendStreak(isAppend = true, previousStreak = 2, addedCount = 0))
    }

    @Test
    fun streakResetsWhenAnAppendAddsPosts() {
        assertEquals(0, nextEmptyAppendStreak(isAppend = true, previousStreak = 2, addedCount = 4))
    }

    @Test
    fun streakIsZeroForInitialLoad() {
        assertEquals(0, nextEmptyAppendStreak(isAppend = false, previousStreak = 2, addedCount = 0))
    }

    @Test
    fun emptyAppendAutoLoadsWhileUnderCapAndMoreExists() {
        assertTrue(shouldAutoLoadNextPage(addedCount = 0, hasMore = true, streak = 0))
        assertTrue(shouldAutoLoadNextPage(addedCount = 0, hasMore = true, streak = MAX_EMPTY_APPEND_STREAK - 1))
    }

    @Test
    fun emptyAppendStopsAtCapOrWhenSourceIsExhausted() {
        assertFalse(shouldAutoLoadNextPage(addedCount = 0, hasMore = true, streak = MAX_EMPTY_APPEND_STREAK))
        assertFalse(shouldAutoLoadNextPage(addedCount = 0, hasMore = false, streak = 0))
    }

    @Test
    fun pageThatAddsPostsNeverAutoLoads() {
        assertFalse(shouldAutoLoadNextPage(addedCount = 1, hasMore = true, streak = 0))
    }

    @Test
    fun longFeedDoesNotExhaustAutoLoadsAfterOrdinaryPages() {
        // Five ordinary appends, then one fully-duplicated page: the streak starts at 0 again,
        // so the duplicate page still triggers the next fetch instead of ending pagination.
        var streak = 0
        repeat(5) { streak = nextEmptyAppendStreak(isAppend = true, previousStreak = streak, addedCount = 20) }
        streak = nextEmptyAppendStreak(isAppend = true, previousStreak = streak, addedCount = 0)

        assertEquals(1, streak)
        assertTrue(shouldAutoLoadNextPage(addedCount = 0, hasMore = true, streak = streak))
    }
}
