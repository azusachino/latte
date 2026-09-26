package com.azusachino.latte.data.network.moebooru

import com.azusachino.latte.data.model.MediaVariant
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.model.PostRating
import com.azusachino.latte.plugin.PlatformId

fun MoebooruPostDto.toDomain(platform: PlatformId): Post {
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
                downloadPriority = 0,
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
                downloadPriority = 2,
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
                downloadPriority = 4,
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
                downloadPriority = 3,
            )
        )
    }

    return Post(
        id = id,
        platform = platform,
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
        authorId = null,
    )
}
