package com.azusachino.latte.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixivArtworkTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Test
    fun mapsMultiPageIllustrationWithoutInventingYandeScore() {
        val dto = json.decodeFromString<PixivIllustResponse>(
            """
            {
              "illust": {
                "id": 75034219,
                "title": "A study",
                "image_urls": {
                  "medium": "https://i.pximg.net/c/540x540_70/img-master/p0.jpg",
                  "large": "https://i.pximg.net/c/600x1200_90/img-master/p0.jpg"
                },
                "user": {"id": 42, "name": "Artist", "account": "artist"},
                "tags": [{"name": "original"}, {"name": "blue_hair"}],
                "create_date": "2026-09-21T10:20:30+09:00",
                "page_count": 2,
                "width": 1200,
                "height": 1600,
                "x_restrict": 0,
                "total_bookmarks": 321,
                "is_bookmarked": true,
                "meta_pages": [
                  {"image_urls": {"original": "https://i.pximg.net/original/p0.png"}},
                  {"image_urls": {"original": "https://i.pximg.net/original/p1.png"}}
                ]
              }
            }
            """.trimIndent(),
        )

        val artwork = dto.illust!!.toPost()

        assertEquals("pixiv", artwork.siteId)
        assertEquals(75034219L, artwork.id)
        assertEquals("A study", artwork.title)
        assertEquals("Artist", artwork.author)
        assertEquals(42L, artwork.authorId)
        assertEquals(listOf("original", "blue_hair"), artwork.tags)
        assertEquals(0, artwork.score)
        assertEquals(321, artwork.bookmarkCount)
        assertTrue(artwork.isBookmarked)
        assertEquals(2, artwork.pages.size)
        assertEquals(0, artwork.pages[0].pageIndex)
        assertEquals(1, artwork.pages[1].pageIndex)
        assertEquals("https://i.pximg.net/original/p1.png", artwork.pages[1].originalUrl)
        assertEquals("https://i.pixiv.re/original/p1.png", artwork.pages[1].mediaRef.url)
        assertEquals("https://pixiv.cat/75034219-1.png", artwork.pages[1].fallbackUrl)
        assertEquals(
            listOf("https://i.pixiv.re/original/p1.png", "https://pixiv.cat/75034219-1.png"),
            artwork.pages[1].imageSources,
        )
        assertEquals(
            listOf("https://i.pixiv.re/original/p0.png", "https://pixiv.cat/75034219.png"),
            artwork.imageSources,
        )
        val secondPage = artwork.forPage(1)
        assertEquals("https://pixiv.cat/75034219-1.png", secondPage.sampleUrl)
        assertEquals(1, secondPage.pageIndex)
        assertEquals(2, secondPage.pageCount)
        assertEquals("pixiv-cat", secondPage.bestVariant.id)
        assertEquals("pixiv", artwork.workIdentity.sourceId)
        assertEquals("pixiv:75034219:1", artwork.pages[1].identity)
    }

    @Test
    fun keepsLargeImageAsThePrimaryProxyWhenOriginalAndMediumAreMissing() {
        val artwork = PixivIllustDto(
            id = 88000001,
            imageUrls = PixivImageUrls(
                large = "https://i.pximg.net/c/600x1200_90/img-master/p0.jpg",
            ),
        ).toPost()

        assertEquals(
            "https://i.pixiv.re/c/600x1200_90/img-master/p0.jpg",
            artwork.previewUrl,
        )
        assertEquals(
            listOf(
                "https://i.pixiv.re/c/600x1200_90/img-master/p0.jpg",
                "https://pixiv.cat/88000001.jpg",
            ),
            artwork.imageSources,
        )
    }
}
