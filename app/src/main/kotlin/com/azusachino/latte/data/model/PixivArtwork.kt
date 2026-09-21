package com.azusachino.latte.data.model

import com.azusachino.latte.plugin.PlatformId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

data class ArtworkIdentity(
    val sourceId: String,
    val workId: Long,
)

data class ArtworkPage(
    val pageIndex: Int,
    val width: Int,
    val height: Int,
    val originalUrl: String?,
    val previewUrl: String,
    val mediaRef: MediaVariant,
    val proxyUrl: String? = null,
    val fallbackUrl: String? = null,
) {
    val identity: String
        get() = mediaRef.id

    val imageSources: List<String>
        get() = listOfNotNull(previewUrl, proxyUrl, fallbackUrl).distinct()
}

@Serializable
data class PixivIllustResponse(
    val illust: PixivIllustDto? = null,
)

@Serializable
data class PixivIllustListResponse(
    val illusts: List<PixivIllustDto> = emptyList(),
    @SerialName("next_url") val nextUrl: String? = null,
)

@Serializable
data class PixivIllustDto(
    val id: Long,
    val title: String = "",
    @SerialName("image_urls") val imageUrls: PixivImageUrls = PixivImageUrls(),
    val user: PixivUserDto? = null,
    val tags: List<PixivTagDto> = emptyList(),
    @SerialName("create_date") val createDate: String? = null,
    @SerialName("page_count") val pageCount: Int = 1,
    val width: Int = 0,
    val height: Int = 0,
    @SerialName("x_restrict") val xRestrict: Int = 0,
    @SerialName("total_bookmarks") val totalBookmarks: Int = 0,
    @SerialName("is_bookmarked") val isBookmarked: Boolean = false,
    @SerialName("meta_pages") val metaPages: List<PixivMetaPageDto> = emptyList(),
)

@Serializable
data class PixivImageUrls(
    @SerialName("square_medium") val squareMedium: String? = null,
    val medium: String? = null,
    val large: String? = null,
    val original: String? = null,
)

@Serializable
data class PixivMetaPageDto(
    @SerialName("image_urls") val imageUrls: PixivImageUrls = PixivImageUrls(),
)

@Serializable
data class PixivUserDto(
    val id: Long = 0,
    val name: String = "",
    val account: String = "",
)

@Serializable
data class PixivTagDto(
    val name: String = "",
)

fun PixivIllustDto.toPost(): Post {
    val pages = if (metaPages.isEmpty()) {
        listOf(imageUrls)
    } else {
        metaPages.map { it.imageUrls }
    }.mapIndexed { index, urls ->
        val original = urls.original ?: urls.large ?: urls.medium
        val directPreviewUrl = listOfNotNull(urls.medium, urls.large, urls.original)
            .firstOrNull { it.isNotBlank() }
        val extension = original
            ?.substringBefore('?')
            ?.substringAfterLast('.', "jpg")
            ?.lowercase()
            ?.takeIf { it in setOf("jpg", "jpeg", "png", "gif", "webp") }
            ?: "jpg"
        val pathProxyUrl = listOfNotNull(original, urls.large, urls.medium)
            .mapNotNull(::pixivImageProxyUrl)
            .distinct()
            .firstOrNull()
        val idProxyUrl = pixivIdProxyUrl(id, index, extension)
        ArtworkPage(
            pageIndex = index,
            width = width,
            height = height,
            originalUrl = original,
            previewUrl = directPreviewUrl
                ?: pathProxyUrl
                ?: idProxyUrl,
            mediaRef = MediaVariant(
                id = "pixiv:$id:$index",
                url = original ?: pathProxyUrl ?: idProxyUrl,
                width = width,
                height = height,
                extension = extension,
            ),
            proxyUrl = pathProxyUrl,
            fallbackUrl = idProxyUrl.takeUnless { it == directPreviewUrl },
        )
    }
    val firstPage = pages.first()
    val createdAt = createDate?.let { runCatching { Instant.parse(it).epochSecond }.getOrNull() }
    return Post(
        id = id,
        platform = PlatformId.PIXIV,
        rating = when (xRestrict) {
            1 -> PostRating.QUESTIONABLE
            2 -> PostRating.EXPLICIT
            else -> PostRating.SAFE
        },
        tags = tags.mapNotNull { it.name.takeIf(String::isNotBlank) },
        score = 0,
        author = user?.name?.takeIf(String::isNotBlank),
        source = canonicalUrl(id),
        createdAt = createdAt,
        width = firstPage.width,
        height = firstPage.height,
        previewUrl = firstPage.previewUrl,
        sampleUrl = firstPage.mediaRef.url,
        jpegUrl = null,
        originalUrl = firstPage.originalUrl ?: firstPage.mediaRef.url,
        variants = listOfNotNull(
            MediaVariant(
                id = "preview",
                url = firstPage.previewUrl,
                width = firstPage.width,
                height = firstPage.height,
            ),
            firstPage.mediaRef,
            firstPage.proxyUrl?.let {
                MediaVariant(
                    id = "pixiv-re",
                    url = it,
                    width = firstPage.width,
                    height = firstPage.height,
                    extension = firstPage.mediaRef.extension,
                )
            },
            firstPage.fallbackUrl?.let {
                MediaVariant(
                    id = "pixiv-cat",
                    url = it,
                    width = firstPage.width,
                    height = firstPage.height,
                    extension = firstPage.mediaRef.extension,
                )
            },
            MediaVariant(
                id = "original",
                url = firstPage.originalUrl ?: firstPage.mediaRef.url,
                width = firstPage.width,
                height = firstPage.height,
                extension = firstPage.mediaRef.extension,
            ),
        ),
        title = title.takeIf(String::isNotBlank),
        canonicalUrl = canonicalUrl(id),
        authorId = user?.id?.takeIf { it > 0 },
        bookmarkCount = totalBookmarks,
        isBookmarked = isBookmarked,
        pageCount = pages.size,
        pages = pages,
    )
}

private fun canonicalUrl(id: Long): String = "https://www.pixiv.net/artworks/$id"

private fun pixivImageProxyUrl(url: String?): String? {
    val value = url?.takeIf(String::isNotBlank) ?: return null
    return when {
        value.startsWith("https://i.pximg.net/") -> {
            "https://i.pixiv.re/${value.removePrefix("https://i.pximg.net/")}"
        }
        value.startsWith("https://i.pixiv.re/") -> value
        else -> null
    }
}

private fun pixivIdProxyUrl(id: Long, pageIndex: Int, extension: String): String {
    val pageSuffix = if (pageIndex == 0) "" else "-$pageIndex"
    return "https://pixiv.cat/$id$pageSuffix.$extension"
}
