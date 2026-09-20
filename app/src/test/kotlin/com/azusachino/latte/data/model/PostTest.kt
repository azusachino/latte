package com.azusachino.latte.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PostTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Test
    fun testYandePostDtoDecodingAndDomainMapping() {
        val sampleJson = """
        [
          {
            "id": 123456,
            "tags": "genshin_impact furina solo blue_hair",
            "created_at": 1700000000,
            "creator_id": 42,
            "author": "artist_name",
            "change": 100,
            "source": "https://twitter.com/artist/status/123",
            "score": 85,
            "md5": "abc123def456",
            "file_size": 2500000,
            "file_ext": "png",
            "file_url": "https://files.yande.re/image/abc123def456/yande.re%20123456.png",
            "is_shown_in_index": true,
            "preview_url": "https://assets.yande.re/data/preview/abc.jpg",
            "preview_width": 150,
            "preview_height": 200,
            "actual_preview_width": 150,
            "actual_preview_height": 200,
            "sample_url": "https://files.yande.re/sample/abc123def456/yande.re%20123456%20sample.jpg",
            "sample_width": 900,
            "sample_height": 1200,
            "sample_file_size": 450000,
            "jpeg_url": "https://files.yande.re/jpeg/abc123def456/yande.re%20123456%20jpeg.jpg",
            "jpeg_width": 1500,
            "jpeg_height": 2000,
            "jpeg_file_size": 1200000,
            "rating": "s",
            "has_children": false,
            "parent_id": null,
            "status": "active",
            "width": 1500,
            "height": 2000,
            "is_held": false,
            "frames_pending_string": "",
            "frames_pending": [],
            "frames_string": "",
            "frames": []
          }
        ]
        """.trimIndent()

        val dtos = json.decodeFromString<List<YandePostDto>>(sampleJson)
        assertEquals(1, dtos.size)

        val post = dtos.first().toDomain()
        assertEquals(123456L, post.id)
        assertEquals(PostRating.SAFE, post.rating)
        assertEquals(listOf("genshin_impact", "furina", "solo", "blue_hair"), post.tags)
        assertEquals(85, post.score)
        assertEquals("artist_name", post.author)
        assertEquals("https://twitter.com/artist/status/123", post.source)
        assertEquals(1500, post.width)
        assertEquals(2000, post.height)
        assertEquals(0.75f, post.aspectRatio, 0.001f)

        assertEquals(4, post.variants.size)
        assertEquals("jpeg", post.bestVariant.id)
        assertEquals("https://files.yande.re/jpeg/abc123def456/yande.re%20123456%20jpeg.jpg", post.bestVariant.url)
    }

    @Test
    fun testRatingParsing() {
        assertEquals(PostRating.SAFE, PostRating.fromCode("s"))
        assertEquals(PostRating.SAFE, PostRating.fromCode("S"))
        assertEquals(PostRating.QUESTIONABLE, PostRating.fromCode("q"))
        assertEquals(PostRating.QUESTIONABLE, PostRating.fromCode("Q"))
        assertEquals(PostRating.EXPLICIT, PostRating.fromCode("e"))
        assertEquals(PostRating.EXPLICIT, PostRating.fromCode("E"))
        assertEquals(PostRating.UNKNOWN, PostRating.fromCode("unknown"))
    }
}
