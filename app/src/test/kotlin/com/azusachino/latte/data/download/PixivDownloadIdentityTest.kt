package com.azusachino.latte.data.download

import com.azusachino.latte.data.model.ArtworkPage
import com.azusachino.latte.data.model.MediaVariant
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.model.PostRating
import com.azusachino.latte.data.model.allPages
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
            url = "https://pixiv.cat/75034219-2.png",
            width = 1000,
            height = 1400,
            extension = "png",
        )
        val original = proxy.copy(
            id = "original",
            url = "https://i.pximg.net/original/75034219_p1.jpg",
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

        val proxyName = DownloadIdentity.fileName(page.copy(variants = listOf(proxy)))
        val originalName = DownloadIdentity.fileName(page.copy(variants = listOf(original)))

        assertEquals("pixiv_75034219_p2.jpg", proxyName)
        assertEquals(proxyName, originalName)
        // Identity names carry no title or tags, so the same work always
        // produces the same file name — the property dedup depends on.
        assertEquals(proxyName, DownloadIdentity.fileName(page.copy(title = null, tags = listOf("different"))))
        assertNotEquals(proxyName, DownloadIdentity.fileName(page.copy(pageIndex = 0)))
    }

    @Test
    fun postAllPagesExpandsAllArtworkPagesWithDistinctDisplayNames() {
        val page0 = ArtworkPage(
            pageIndex = 0,
            width = 1000,
            height = 1400,
            previewUrl = "https://i.pixiv.re/c/540x540_70/img-master/img/75034219_p0_master1200.jpg",
            mediaRef = MediaVariant(id = "page-0", url = "https://i.pixiv.re/img-original/img/75034219_p0.jpg", width = 1000, height = 1400),
            originalUrl = "https://i.pximg.net/img-original/img/75034219_p0.jpg",
        )
        val page1 = ArtworkPage(
            pageIndex = 1,
            width = 1000,
            height = 1400,
            previewUrl = "https://i.pixiv.re/c/540x540_70/img-master/img/75034219_p1_master1200.jpg",
            mediaRef = MediaVariant(id = "page-1", url = "https://i.pixiv.re/img-original/img/75034219_p1.jpg", width = 1000, height = 1400),
            originalUrl = "https://i.pximg.net/img-original/img/75034219_p1.jpg",
        )
        val multiPost = Post(
            id = 75034219,
            platform = PlatformId.PIXIV,
            rating = PostRating.SAFE,
            tags = listOf("tag1"),
            score = 0,
            author = "Artist",
            source = "https://www.pixiv.net/artworks/75034219",
            createdAt = null,
            width = 1000,
            height = 1400,
            previewUrl = page0.previewUrl,
            sampleUrl = page0.mediaRef.url,
            jpegUrl = null,
            originalUrl = page0.originalUrl!!,
            variants = listOf(page0.mediaRef),
            title = "Manga Chapter",
            pageIndex = 0,
            pageCount = 2,
            pages = listOf(page0, page1),
        )

        val allPages = multiPost.allPages()
        assertEquals(2, allPages.size)
        assertEquals(0, allPages[0].pageIndex)
        assertEquals(1, allPages[1].pageIndex)

        val name0 = DownloadIdentity.fileName(allPages[0])
        val name1 = DownloadIdentity.fileName(allPages[1])

        assertEquals("pixiv_75034219_p1.jpg", name0)
        assertEquals("pixiv_75034219_p2.jpg", name1)
        assertNotEquals(name0, name1)
    }

    @Test
    fun legacyNamePrefixMatchesThePreTitleFreeLayout() {
        val post = Post(
            id = 75034219,
            platform = PlatformId.PIXIV,
            rating = PostRating.SAFE,
            tags = listOf("tag1"),
            score = 0,
            author = "Artist",
            source = null,
            createdAt = null,
            width = 1000,
            height = 1400,
            previewUrl = "https://i.pixiv.re/preview.jpg",
            sampleUrl = "https://i.pixiv.re/sample.jpg",
            jpegUrl = null,
            originalUrl = "https://i.pximg.net/img-original/img/75034219_p0.jpg",
            variants = emptyList(),
            title = "Manga Chapter",
            pageIndex = 1,
            pageCount = 2,
        )

        // Old builds saved "<platform> <id>[ pN] <title>.<ext>"; the prefix
        // must match those files so they dedup against the new names.
        assertEquals("pixiv 75034219 p2", DownloadIdentity.legacyNamePrefix(post))
        assertTrue("pixiv 75034219 p2 Manga Chapter.jpg".startsWith(DownloadIdentity.legacyNamePrefix(post)))
    }

    @Test
    fun postAllPagesReturnsSinglePostForSinglePagePost() {
        val singlePost = Post(
            id = 12345,
            platform = PlatformId.PIXIV,
            rating = PostRating.SAFE,
            tags = emptyList(),
            score = 0,
            author = null,
            source = null,
            createdAt = null,
            width = 500,
            height = 500,
            previewUrl = "p",
            sampleUrl = "s",
            jpegUrl = null,
            originalUrl = "https://i.pximg.net/original/12345.png",
            variants = emptyList(),
            pageCount = 1,
            pages = emptyList(),
        )
        val pages = singlePost.allPages()
        assertEquals(1, pages.size)
        assertEquals(singlePost, pages[0])
    }
}
