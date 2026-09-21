package com.azusachino.latte.data.download

import com.azusachino.latte.data.model.MediaVariant
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.model.PostRating
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixivDownloadIdentityTest {
    @Test
    fun multiPagePixivDownloadsKeepPageAndVariantIdentity() {
        val pixivCat = MediaVariant(
            id = "pixiv:75034219:1",
            url = "https://pixiv.cat/75034219-1.jpg",
            width = 1000,
            height = 1400,
            extension = "jpg",
        )
        val original = pixivCat.copy(id = "original", url = "https://i.pximg.net/original/75034219-1.jpg")
        val page = Post(
            id = 75034219,
            siteId = "pixiv",
            rating = PostRating.SAFE,
            tags = listOf("original"),
            score = 0,
            author = "Artist",
            source = "https://www.pixiv.net/artworks/75034219",
            createdAt = null,
            width = 1000,
            height = 1400,
            previewUrl = pixivCat.url,
            sampleUrl = pixivCat.url,
            jpegUrl = null,
            originalUrl = original.url,
            variants = listOf(pixivCat, original),
            title = "A work",
            pageIndex = 1,
            pageCount = 2,
        )

        val pixivCatName = DownloadIdentity.displayName(page, pixivCat)
        val originalName = DownloadIdentity.displayName(page, original)

        assertNotEquals(pixivCatName, originalName)
        assertTrue(pixivCatName.contains("p2"))
        assertTrue(pixivCatName.contains("pixiv-75034219-1"))
        assertTrue(pixivCatName.endsWith("A work.jpg"))
    }
}
