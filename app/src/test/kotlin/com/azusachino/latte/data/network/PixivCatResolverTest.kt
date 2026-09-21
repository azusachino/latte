package com.azusachino.latte.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PixivCatResolverTest {
    private val resolver = PixivCatResolver("https://pixiv.cat")

    @Test
    fun pageZeroAndLaterPagesHaveStableProxyNames() {
        assertEquals("https://pixiv.cat/75034219.jpg", resolver.url(75034219, 0, ".jpg"))
        assertEquals("https://pixiv.cat/75034219-2.png", resolver.url(75034219, 2, "png"))
    }

    @Test
    fun originalPixivImagePathsUseTheReverseProxyHost() {
        assertEquals(
            "https://i.pixiv.re/img-original/img/2018/04/24/01/51/35/68377968_p0.png",
            resolver.proxyUrl("https://i.pximg.net/img-original/img/2018/04/24/01/51/35/68377968_p0.png"),
        )
    }

    @Test
    fun invalidIdsAndExtensionsDoNotCreateUntrustedUrls() {
        assertThrows(IllegalArgumentException::class.java) { PixivCatResolver("http://pixiv.cat") }
        assertThrows(IllegalArgumentException::class.java) { PixivCatResolver("https://example.com") }
        assertThrows(IllegalArgumentException::class.java) {
            resolver.proxyUrl("http://i.pximg.net/img-original/image.jpg")
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolver.proxyUrl("https://example.com/image.jpg")
        }
        assertThrows(IllegalArgumentException::class.java) { resolver.url(0, 0, "jpg") }
        assertThrows(IllegalArgumentException::class.java) { resolver.url(75034219, -1, "jpg") }
        assertThrows(IllegalArgumentException::class.java) { resolver.url(75034219, 0, "html") }
    }

    @Test
    fun responseHeadersDetermineImageFilenameAndRetryPolicy() {
        assertEquals(
            PixivCatResponse.Image("image/jpeg", "75034219-1.jpg"),
            resolver.classifyResponse(200, "image/jpeg; charset=binary", "attachment; filename=75034219-1.jpg"),
        )
        assertEquals(
            PixivCatResponse.Image("image/png", "page 1.png"),
            resolver.classifyResponse(200, "image/png", "attachment; filename*=UTF-8''page%201.png"),
        )
        assertEquals(
            PixivCatResponse.NonImage(200, "text/html"),
            resolver.classifyResponse(200, "text/html", null),
        )
        val retry = resolver.classifyResponse(503, "text/html", null)
        assertTrue(retry is PixivCatResponse.RetryableFailure)
    }
}
