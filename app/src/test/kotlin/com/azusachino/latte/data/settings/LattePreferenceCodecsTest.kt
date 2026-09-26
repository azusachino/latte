package com.azusachino.latte.data.settings

import com.azusachino.latte.data.model.FavoriteTag
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
}
