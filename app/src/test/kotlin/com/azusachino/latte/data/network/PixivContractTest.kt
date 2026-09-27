package com.azusachino.latte.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PixivContractTest {

    @Test
    fun feedResultsKeepAuthAndEmptyDistinct() {
        val empty = PixivFeedResult.Empty
        val auth = PixivFeedResult.AuthRequired

        assertNotNull(empty)
        assertNotNull(auth)
    }

    @Test
    fun pixivCatBuildsStablePageUrlsAndClassifiesImageResponses() {
        val resolver = PixivCatResolver()

        assertEquals("https://pixiv.cat/75034219.jpg", resolver.url(75034219, 0, "jpg"))
        assertEquals("https://pixiv.cat/75034219-1.png", resolver.url(75034219, 0, "png", pageCount = 2))
        assertEquals("https://pixiv.cat/75034219-2.png", resolver.url(75034219, 1, "png", pageCount = 2))

        val response = resolver.classifyResponse(
            code = 200,
            contentType = "image/png",
            contentDisposition = "attachment; filename=75034219-1.png",
        )
        assertEquals(
            PixivCatResponse.Image("image/png", "75034219-1.png"),
            response,
        )
        assertEquals(
            PixivCatResponse.NonImage(200, "text/html"),
            resolver.classifyResponse(200, "text/html", null),
        )
    }
}
