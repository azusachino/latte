package com.azusachino.latte.data.model

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
)

data class Post(
    val id: Long,
    val siteId: String = "yande.re",
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
) {
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 1f

    val bestVariant: MediaVariant
        get() = variants.firstOrNull {
            siteId == "pixiv" && (
                it.id == "pixiv-cat" ||
                    it.url.startsWith("https://i.pixiv.cat/") ||
                    it.url.startsWith("https://pixiv.cat/")
                )
        }
            ?: variants.firstOrNull { it.id == "jpeg" }
            ?: variants.firstOrNull { it.id == "sample" }
            ?: variants.firstOrNull { it.id == "original" }
            ?: variants.first { it.id == "preview" }

    val workIdentity: ArtworkIdentity
        get() = ArtworkIdentity(siteId, id)
}

fun YandePostDto.toDomain(): Post {
    val preview = previewUrl ?: sampleUrl ?: fileUrl.orEmpty()
    val sample = sampleUrl ?: fileUrl.orEmpty()
    val original = fileUrl ?: sampleUrl.orEmpty()
    val variants = mutableListOf<MediaVariant>()

    if (!previewUrl.isNullOrBlank()) {
        variants.add(
            MediaVariant(
                id = "preview",
                url = previewUrl,
                width = previewWidth ?: 150,
                height = previewHeight ?: 150,
            )
        )
    }
    if (!sampleUrl.isNullOrBlank()) {
        variants.add(
            MediaVariant(
                id = "sample",
                url = sampleUrl,
                width = sampleWidth ?: width,
                height = sampleHeight ?: height,
                fileSize = sampleFileSize,
                extension = fileExt,
            )
        )
    }
    if (!jpegUrl.isNullOrBlank()) {
        variants.add(
            MediaVariant(
                id = "jpeg",
                url = jpegUrl,
                width = jpegWidth ?: width,
                height = jpegHeight ?: height,
                fileSize = jpegFileSize,
                extension = "jpg",
            )
        )
    }
    if (!fileUrl.isNullOrBlank()) {
        variants.add(
            MediaVariant(
                id = "original",
                url = fileUrl,
                width = width,
                height = height,
                fileSize = fileSize,
                extension = fileExt,
            )
        )
    }

    return Post(
        id = id,
        siteId = "yande.re",
        rating = PostRating.fromCode(rating),
        tags = tags.split(" ").filter { it.isNotBlank() },
        score = score,
        author = author,
        source = source,
        createdAt = createdAt,
        width = width,
        height = height,
        previewUrl = preview,
        sampleUrl = sample,
        jpegUrl = jpegUrl,
        originalUrl = original,
        variants = variants,
    )
}
