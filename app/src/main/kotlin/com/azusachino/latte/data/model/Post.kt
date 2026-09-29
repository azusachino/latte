package com.azusachino.latte.data.model

import com.azusachino.latte.plugin.PlatformId
import java.time.LocalDate

enum class PostRating {
    SAFE, QUESTIONABLE, EXPLICIT, UNKNOWN;

    companion object {
        fun fromCode(code: String): PostRating = when (code.lowercase()) {
            "s" -> SAFE
            "q" -> QUESTIONABLE
            "e" -> EXPLICIT
            else -> UNKNOWN
        }
    }
}

enum class PopularPeriod {
    DAY, WEEK, MONTH, YEAR
}

data class MediaVariant(
    val id: String,
    val url: String,
    val width: Int,
    val height: Int,
    val fileSize: Long? = null,
    val extension: String? = null,
    val downloadPriority: Int = 0,
    val fallbackOnPreviewFailure: Boolean = false,
)

data class Post(
    val id: Long,
    val platform: PlatformId,
    val rating: PostRating,
    val tags: List<String>,
    val score: Int,
    val author: String?,
    val source: String?,
    val createdAt: Long?,
    val width: Int,
    val height: Int,
    val previewUrl: String,
    val sampleUrl: String,
    val jpegUrl: String?,
    val originalUrl: String,
    val variants: List<MediaVariant>,
    val title: String? = null,
    val canonicalUrl: String? = null,
    val bookmarkCount: Int? = null,
    val isBookmarked: Boolean = false,
    val pageIndex: Int = 0,
    val pageCount: Int = 1,
    val pages: List<ArtworkPage> = emptyList(),
    val authorId: Long? = null,
) {
    val siteId: String
        get() = platform.externalId

    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 1f

    val bestVariant: MediaVariant
        get() = variants.maxByOrNull(MediaVariant::downloadPriority)
            ?: error("Post $id has no media variants")

    val imageSources: List<String>
        get() = buildList {
            add(previewUrl)
            addAll(variants.filter(MediaVariant::fallbackOnPreviewFailure).map(MediaVariant::url))
        }.filter(String::isNotBlank).distinct()

    val workIdentity: ArtworkIdentity
        get() = ArtworkIdentity(siteId, id)
}

fun Post.forPage(index: Int): Post {
    if (pages.isEmpty()) return this
    val page = pages.getOrNull(index.coerceIn(pages.indices)) ?: return this
    return copy(
        previewUrl = page.previewUrl,
        sampleUrl = page.mediaRef.url,
        originalUrl = page.originalUrl ?: page.mediaRef.url,
        variants = listOfNotNull(
            MediaVariant(
                id = "preview",
                url = page.previewUrl,
                width = page.width,
                height = page.height,
                downloadPriority = 0,
            ),
            page.mediaRef,
            page.proxyUrl?.let {
                MediaVariant(
                    id = "path-proxy",
                    url = it,
                    width = page.width,
                    height = page.height,
                    extension = page.mediaRef.extension,
                    downloadPriority = 2,
                    fallbackOnPreviewFailure = true,
                )
            },
            page.fallbackUrl?.let {
                MediaVariant(
                    id = "mirror-fallback",
                    url = it,
                    width = page.width,
                    height = page.height,
                    extension = page.mediaRef.extension,
                    downloadPriority = 5,
                    fallbackOnPreviewFailure = true,
                )
            },
            MediaVariant(
                id = "original",
                url = page.originalUrl ?: page.mediaRef.url,
                width = page.width,
                height = page.height,
                extension = page.mediaRef.extension,
                downloadPriority = 3,
            ),
        ),
        width = page.width,
        height = page.height,
        pageIndex = page.pageIndex,
        authorId = authorId,
    )
}

fun Post.allPages(): List<Post> {
    if (pages.isEmpty() || pageCount <= 1) return listOf(this)
    return pages.indices.map { forPage(it) }
}
