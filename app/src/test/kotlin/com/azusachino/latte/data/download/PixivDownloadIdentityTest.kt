package com.azusachino.latte.data.download

import com.azusachino.latte.data.model.MediaVariant
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.model.PostRating
import com.azusachino.latte.plugin.PlatformId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixivDownloadIdentityTest {
    @Test
    fun multiPagePixivFilenameIsStableAcrossProxyAndOriginalVariants() {
        val proxy = MediaVariant(
            id = "mirror-fallback",
            url = "https://pixiv.cat/75034219-1.png",
            width = 1000,
            height = 1400,
            extension = "png",
        )
        val original = proxy.copy(
            id = "original",
            url = "https://i.pximg.net/original/75034219-1.jpg",
            extension = "jpg",
        )
        val page = Post(
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
            previewUrl = proxy.url,
            sampleUrl = proxy.url,
            jpegUrl = null,
            originalUrl = original.url,
            variants = listOf(proxy, original),
            title = "A work",
            pageIndex = 1,
            pageCount = 2,
        )

        val proxyName = DownloadIdentity.displayName(page.copy(variants = listOf(proxy)))
        val originalName = DownloadIdentity.displayName(page.copy(variants = listOf(original)))

        assertEquals(proxyName, originalName)
        assertTrue(proxyName.contains("p2"))
        assertTrue(proxyName.endsWith("A work.jpg"))
        assertNotEquals(proxyName, DownloadIdentity.displayName(page.copy(pageIndex = 0)))
    }
}
