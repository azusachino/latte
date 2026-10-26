package com.azusachino.latte.data.download

import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.model.PostRating
import com.azusachino.latte.plugin.PlatformId
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadToastLabelTest {
    private fun post(pageIndex: Int = 0, pageCount: Int = 1) = Post(
        id = 1271440,
        platform = PlatformId.PIXIV,
        rating = PostRating.SAFE,
        tags = listOf("animal_ears"),
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
        title = "A long filename-ish title",
        pageIndex = pageIndex,
        pageCount = pageCount,
    )

    @Test
    fun singlePostLabelIsHashIdWithoutFilename() {
        assertEquals("#1271440", DownloadIdentity.toastLabel(post()))
    }

    @Test
    fun multiPageLabelAddsOneBasedPageIndex() {
        assertEquals("#1271440 p3", DownloadIdentity.toastLabel(post(pageIndex = 2, pageCount = 5)))
    }

    @Test
    fun singlePageWorkOmitsPageSuffixEvenWithIndexZero() {
        assertEquals("#1271440", DownloadIdentity.toastLabel(post(pageIndex = 0, pageCount = 1)))
    }

    @Test
    fun postCountLabelPluralizes() {
        assertEquals("1 post", DownloadIdentity.postCountLabel(1))
        assertEquals("3 posts", DownloadIdentity.postCountLabel(3))
        assertEquals("0 posts", DownloadIdentity.postCountLabel(0))
    }
}
