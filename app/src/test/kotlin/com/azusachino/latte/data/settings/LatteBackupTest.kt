package com.azusachino.latte.data.settings

import com.azusachino.latte.data.model.FavoriteTag
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.model.PostRating
import com.azusachino.latte.plugin.PlatformId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatteBackupTest {

    private fun post(id: Long, title: String? = null) = Post(
        id = id,
        platform = PlatformId.YANDE,
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
        title = title,
    )

    @Test
    fun backupRoundTripsEverySetting() {
        val backup = LatteBackup(
            columnCount = 3,
            themeMode = ThemeMode.DARK,
            safeMode = false,
            favoriteTags = listOf(FavoriteTag("landscape", PlatformId.YANDE)),
            recentSearches = listOf("sky", "forest"),
            proxyHost = "127.0.0.1",
            proxyPort = 8080,
            viewHistory = listOf(post(1, "first"), post(2)),
        )
        val decoded = LatteBackupCodec.decode(LatteBackupCodec.encode(backup)).getOrThrow()
        assertEquals(backup, decoded)
    }

    @Test
    fun encodedBackupAlwaysStatesItsVersionAndEveryField() {
        val text = LatteBackupCodec.encode(LatteBackup())
        assertTrue(text.contains("\"version\": 1"))
        assertTrue(text.contains("\"viewHistory\": []"))
        assertTrue(text.contains("\"proxyHost\": null"))
    }

    @Test
    fun unknownFieldsFromNewerBuildsAreIgnored() {
        val text = """{"version":1,"columnCount":4,"someFutureField":true}"""
        val decoded = LatteBackupCodec.decode(text).getOrThrow()
        assertEquals(4, decoded.columnCount)
    }

    @Test
    fun newerFormatVersionIsRejected() {
        assertTrue(LatteBackupCodec.decode("""{"version":2}""").isFailure)
    }

    @Test
    fun malformedFileIsRejected() {
        assertTrue(LatteBackupCodec.decode("not a backup").isFailure)
    }

    @Test
    fun backupOmitsCredentialFields() {
        val text = LatteBackupCodec.encode(LatteBackup())
        assertFalse(text.contains("password", ignoreCase = true))
        assertFalse(text.contains("token", ignoreCase = true))
    }

    @Test
    fun viewHistoryRoundTripsThroughJson() {
        val history = listOf(post(7, "seven"), post(8))
        val encoded = LattePreferenceCodecs.encodeViewHistory(history)
        assertEquals(history, LattePreferenceCodecs.decodeViewHistory(encoded))
    }

    @Test
    fun recordingAViewPutsItFirstAndDropsItsEarlierEntry() {
        val existing = listOf(post(1), post(2), post(3))
        val updated = LattePreferenceCodecs.recordView(existing, post(2, "reopened"))
        assertEquals(listOf(2L, 1L, 3L), updated.map { it.id })
        assertEquals("reopened", updated.first().title)
    }

    @Test
    fun viewHistoryIsCappedAtTheLimit() {
        val existing = (1L..5L).map { post(it) }
        val updated = LattePreferenceCodecs.recordView(existing, post(9), limit = 3)
        assertEquals(listOf(9L, 1L, 2L), updated.map { it.id })
    }
}
