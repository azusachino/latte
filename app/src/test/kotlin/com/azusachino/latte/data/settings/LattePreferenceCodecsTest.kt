package com.azusachino.latte.data.settings

import com.azusachino.latte.data.model.FavoriteTag
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.model.PostRating
import com.azusachino.latte.plugin.PlatformId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LattePreferenceCodecsTest {

    @Test
    fun recentSearchesRoundTripThroughJson() {
        val values = listOf("genshin_impact", "hatsune_miku", "landscape")
        val encoded = LattePreferenceCodecs.encodeRecentSearches(values)
        assertEquals(values, LattePreferenceCodecs.decodeRecentSearches(encoded))
    }

    @Test
    fun malformedStoredValueDecodesToEmpty() {
        assertTrue(LattePreferenceCodecs.decodeRecentSearches("not json").isEmpty())
    }

    @Test
    fun recordingMovesExistingQueryToFrontAndDeduplicates() {
        val existing = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), LattePreferenceCodecs.recordRecentSearch(existing, "b"))
        assertEquals(listOf("d", "a", "b", "c"), LattePreferenceCodecs.recordRecentSearch(existing, "d"))
    }

    @Test
    fun recordingBindsToListLimit() {
        val existing = (1..20).map { "tag$it" }
        val updated = LattePreferenceCodecs.recordRecentSearch(existing, "new", limit = 20)
        assertEquals(20, updated.size)
        assertEquals("new", updated.first())
        assertEquals("tag19", updated.last())
    }

    @Test
    fun blankQueriesAreNotRecorded() {
        val existing = listOf("a")
        assertEquals(existing, LattePreferenceCodecs.recordRecentSearch(existing, "   "))
    }

    @Test
    fun favoriteTagsRoundTripThroughJsonWithPlatform() {
        val values = listOf(
            FavoriteTag("genshin_impact", PlatformId.YANDE),
            FavoriteTag("hatsune_miku", PlatformId.PIXIV),
        )
        val encoded = LattePreferenceCodecs.encodeFavoriteTags(values)
        assertEquals(values, LattePreferenceCodecs.decodeFavoriteTags(encoded))
    }

    @Test
    fun malformedFavoriteTagsDecodeToEmpty() {
        assertTrue(LattePreferenceCodecs.decodeFavoriteTags("not json").isEmpty())
    }

    private fun post(id: Long, platform: PlatformId = PlatformId.YANDE) = Post(
        id = id,
        platform = platform,
        rating = PostRating.SAFE,
        tags = listOf("landscape"),
        score = 3,
        author = "artist",
        source = null,
        createdAt = 1_700_000_000L,
        width = 1200,
        height = 800,
        previewUrl = "https://example.test/$id-preview.jpg",
        sampleUrl = "https://example.test/$id-sample.jpg",
        jpegUrl = null,
        originalUrl = "https://example.test/$id-original.png",
        variants = emptyList(),
    )

    @Test
    fun removingViewsDropsEveryMatchingIdentityAndKeepsOrder() {
        val first = post(1)
        val second = post(2)
        val sameIdOtherSite = post(1, platform = PlatformId.PIXIV)
        val existing = listOf(first, second, sameIdOtherSite)
        assertEquals(
            listOf(second, sameIdOtherSite),
            LattePreferenceCodecs.removeViews(existing, listOf(first)),
        )
    }

    @Test
    fun removingViewsKeepsEverythingWhenNoIdentityMatches() {
        val existing = listOf(post(1), post(2))
        assertEquals(existing, LattePreferenceCodecs.removeViews(existing, listOf(post(9))))
        assertEquals(existing, LattePreferenceCodecs.removeViews(existing, emptyList()))
    }


}
