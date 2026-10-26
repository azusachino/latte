package com.azusachino.latte.data.download

import com.azusachino.latte.data.model.MediaVariant
import com.azusachino.latte.data.model.PixivIllustDto
import com.azusachino.latte.data.model.PixivImageUrls
import com.azusachino.latte.data.model.PixivMetaPageDto
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.model.PostRating
import com.azusachino.latte.data.model.toPost
import com.azusachino.latte.plugin.PlatformId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadFallbackTest {
    private val pixivHost = "i.pximg.net"

    @Test
    fun pixivCandidatesStartWithOriginalThenPathProxyThenPixivCat() {
        val post = pixivPost()

        assertEquals(
            listOf(
                "https://i.pximg.net/original/75034219_p0.png",
                "https://i.pixiv.re/original/75034219_p0.png",
                "https://pixiv.cat/75034219.png",
            ),
            DownloadFallback.candidateUrls(post),
        )
    }

    @Test
    fun pixivCandidateOrderIgnoresVariantListPosition() {
        val post = pixivPost()

        assertEquals(
            DownloadFallback.candidateUrls(post),
            DownloadFallback.candidateUrls(post.copy(variants = post.variants.reversed())),
        )
    }

    @Test
    fun pixivPreviewThumbnailIsNeverADownloadCandidate() {
        val candidates = DownloadFallback.candidateUrls(pixivPost())

        assertFalse(candidates.any { it.contains("540x540_70") })
    }

    @Test
    fun moebooruKeepsSingleBestVariantAsItsOnlyCandidate() {
        val post = yandePost()

        assertEquals(listOf("https://files.yande.re/image/abc/original.png"), DownloadFallback.candidateUrls(post))
    }

    @Test
    fun moebooruHonoursPreferredVariant() {
        val post = yandePost()
        val sample = post.variants.first { it.id == "sample" }

        assertEquals(listOf(sample.url), DownloadFallback.candidateUrls(post, sample))
    }

    @Test
    fun serverErrorsFallBackWhenAnotherSourceRemains() {
        assertTrue(DownloadFallback.shouldFallback(500, null, pixivHost, hasNextSource = true))
        assertTrue(DownloadFallback.shouldFallback(503, null, pixivHost, hasNextSource = true))
    }

    @Test
    fun clientRejectionsThatMayBeSourceSpecificFallBack() {
        listOf(403, 404, 408, 429).forEach { code ->
            assertTrue("HTTP $code should fall back", DownloadFallback.shouldFallback(code, null, pixivHost, true))
        }
    }

    @Test
    fun transportErrorsAndTimeoutsFallBack() {
        assertTrue(DownloadFallback.shouldFallback(null, null, pixivHost, hasNextSource = true))
    }

    @Test
    fun nonImageContentOnPixivHostFallsBack() {
        assertTrue(DownloadFallback.shouldFallback(200, "text/html; charset=utf-8", pixivHost, true))
        assertTrue(DownloadFallback.shouldFallback(200, null, "pixiv.cat", true))
    }

    @Test
    fun imageContentOnPixivHostIsAccepted() {
        assertFalse(DownloadFallback.shouldFallback(200, "image/jpeg", pixivHost, hasNextSource = true))
        assertFalse(DownloadFallback.shouldFallback(206, "IMAGE/PNG; charset=binary", "i.pixiv.re", true))
    }

    @Test
    fun nonImageContentOnOtherHostsIsNotAFallbackTrigger() {
        assertFalse(DownloadFallback.shouldFallback(200, "application/octet-stream", "files.yande.re", true))
    }

    @Test
    fun unlistedStatusCodesDoNotFallBack() {
        assertFalse(DownloadFallback.shouldFallback(401, null, pixivHost, hasNextSource = true))
        assertFalse(DownloadFallback.shouldFallback(400, null, pixivHost, hasNextSource = true))
    }

    @Test
    fun lastSourceFailureDoesNotFallBack() {
        assertFalse(DownloadFallback.shouldFallback(500, null, "pixiv.cat", hasNextSource = false))
        assertFalse(DownloadFallback.shouldFallback(null, null, "pixiv.cat", hasNextSource = false))
        assertFalse(DownloadFallback.shouldFallback(200, "text/html", "pixiv.cat", hasNextSource = false))
    }

    private fun pixivPost(): Post = PixivIllustDto(
        id = 75034219,
        imageUrls = PixivImageUrls(
            medium = "https://i.pximg.net/c/540x540_70/img-master/img/75034219_p0_master1200.jpg",
        ),
        metaPages = listOf(
            PixivMetaPageDto(
                imageUrls = PixivImageUrls(original = "https://i.pximg.net/original/75034219_p0.png"),
            ),
        ),
        width = 1000,
        height = 1400,
    ).toPost()

    private fun yandePost(): Post {
        val original = MediaVariant(
            id = "original",
            url = "https://files.yande.re/image/abc/original.png",
            width = 1000,
            height = 1400,
            extension = "png",
            downloadPriority = 4,
        )
        val sample = original.copy(
            id = "sample",
            url = "https://assets.yande.re/sample/abc.jpg",
            extension = "jpg",
            downloadPriority = 2,
        )
        val preview = original.copy(
            id = "preview",
            url = "https://assets.yande.re/preview/abc.jpg",
            downloadPriority = 0,
        )
        return Post(
            id = 1,
            platform = PlatformId.YANDE,
            rating = PostRating.SAFE,
            tags = emptyList(),
            score = 0,
            author = null,
            source = null,
            createdAt = null,
            width = 1000,
            height = 1400,
            previewUrl = preview.url,
            sampleUrl = sample.url,
            jpegUrl = null,
            originalUrl = original.url,
            variants = listOf(preview, sample, original),
        )
    }
}
