package com.azusachino.latte.data.download

import com.azusachino.latte.data.model.MediaVariant
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.model.PostRating
import com.azusachino.latte.plugin.PlatformId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadLibraryTest {
    private fun post(id: Long, platform: PlatformId = PlatformId.YANDE): Post {
        val url = "https://files.example/$id.jpg"
        return Post(
            id = id,
            platform = platform,
            rating = PostRating.SAFE,
            tags = emptyList(),
            score = 0,
            author = null,
            source = null,
            createdAt = null,
            width = 100,
            height = 100,
            previewUrl = url,
            sampleUrl = url,
            jpegUrl = null,
            originalUrl = url,
            variants = listOf(
                MediaVariant(id = "original", url = url, width = 100, height = 100, extension = "jpg"),
            ),
        )
    }

    @Test
    fun rememberedSaveIsDedupedWithoutRescan() {
        val library = DownloadLibrary
        val saved = post(4242)
        assertFalse(library.isSaved(saved))

        library.remember(DownloadIdentity.fileName(saved))

        assertTrue(library.isSaved(saved))
        assertFalse(library.isSaved(post(4243)))
        assertFalse(library.isSaved(post(4242, PlatformId.KONACHAN)))
    }
}
