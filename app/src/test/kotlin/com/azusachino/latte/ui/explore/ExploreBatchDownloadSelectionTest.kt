package com.azusachino.latte.ui.explore

import com.azusachino.latte.data.model.ArtworkPage
import com.azusachino.latte.data.model.MediaVariant
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.model.PostRating
import com.azusachino.latte.data.model.forPage
import com.azusachino.latte.plugin.PlatformId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreBatchDownloadSelectionTest {

    private fun createPost(id: Long, platform: PlatformId = PlatformId.PIXIV, pageCount: Int = 1): Post {
        val pages = if (pageCount > 1) {
            (0 until pageCount).map { pageIdx ->
                ArtworkPage(
                    pageIndex = pageIdx,
                    previewUrl = "https://pixiv.cat/$id-${pageIdx + 1}.jpg",
                    width = 1200,
                    height = 1600,
                    mediaRef = MediaVariant(
                        id = "sample_$pageIdx",
                        url = "https://pixiv.cat/$id-${pageIdx + 1}.jpg",
                        width = 1200,
                        height = 1600,
                    ),
                    originalUrl = "https://pixiv.cat/$id-${pageIdx + 1}.jpg",
                )
            }
        } else emptyList()

        return Post(
            id = id,
            platform = platform,
            rating = PostRating.SAFE,
            tags = listOf("tag"),
            score = 10,
            author = "author",
            source = null,
            createdAt = null,
            width = 1200,
            height = 1600,
            previewUrl = "https://pixiv.cat/$id.jpg",
            sampleUrl = "https://pixiv.cat/$id.jpg",
            jpegUrl = null,
            originalUrl = "https://pixiv.cat/$id.jpg",
            variants = listOf(
                MediaVariant("preview", "https://pixiv.cat/$id.jpg", 1200, 1600, downloadPriority = 0),
            ),
            pageCount = pageCount,
            pages = pages,
        )
    }

    @Test
    fun selectionDownloadsOnlyFirstPageForMultiPageArtworks() {
        val multiPagePost = createPost(id = 1001, pageCount = 5)
        val singlePagePost = createPost(id = 1002, pageCount = 1)
        val booruPost = createPost(id = 2001, platform = PlatformId.YANDE, pageCount = 1)

        val feedPosts = listOf(multiPagePost, singlePagePost, booruPost)
        val selectedIds = setOf(1001L, 1002L, 2001L)

        val postsToDownload = feedPosts
            .filter { it.id in selectedIds }
            .map { it.forPage(0) }

        assertEquals(3, postsToDownload.size)

        // Pixiv multi-page work selected at Explore page only targets p0
        val downloadedMultiPage = postsToDownload.first { it.id == 1001L }
        assertEquals(0, downloadedMultiPage.pageIndex)
        assertEquals("https://pixiv.cat/1001-1.jpg", downloadedMultiPage.originalUrl)

        // Pixiv single-page work
        val downloadedSingle = postsToDownload.first { it.id == 1002L }
        assertEquals(0, downloadedSingle.pageIndex)
        assertEquals("https://pixiv.cat/1002.jpg", downloadedSingle.originalUrl)

        // Booru work
        val downloadedBooru = postsToDownload.first { it.id == 2001L }
        assertEquals(0, downloadedBooru.pageIndex)
        assertEquals(PlatformId.YANDE, downloadedBooru.platform)
    }

    @Test
    fun selectionRespectsMaximumLimitOfTen() {
        val maxSelectionSize = 10
        val allPosts = (1L..15L).map { createPost(it) }

        var selectedIds = emptySet<Long>()
        val onToggleSelect: (Long) -> Unit = { id ->
            selectedIds = if (selectedIds.contains(id)) {
                selectedIds - id
            } else {
                if (selectedIds.size >= maxSelectionSize) {
                    selectedIds
                } else {
                    selectedIds + id
                }
            }
        }

        // Select first 10 items
        for (post in allPosts.take(10)) {
            onToggleSelect(post.id)
        }
        assertEquals(10, selectedIds.size)

        // Attempting to select 11th and 12th items is rejected
        onToggleSelect(allPosts[10].id)
        onToggleSelect(allPosts[11].id)
        assertEquals(10, selectedIds.size)
        assertTrue(allPosts[10].id !in selectedIds)
        assertTrue(allPosts[11].id !in selectedIds)

        // Unselecting an item frees up a slot
        onToggleSelect(allPosts[0].id)
        assertEquals(9, selectedIds.size)
        assertTrue(allPosts[0].id !in selectedIds)

        onToggleSelect(allPosts[10].id)
        assertEquals(10, selectedIds.size)
        assertTrue(allPosts[10].id in selectedIds)
    }
}
