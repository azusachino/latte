package com.azusachino.latte.data.download

import com.azusachino.latte.data.model.MediaVariant
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.plugin.PlatformId
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Download-only source order and fallback rules. Preview and display paths do not use this.
 */
object DownloadFallback {
    private const val PREVIEW_VARIANT_ID = "preview"
    private val pixivImageHosts = setOf("i.pximg.net", "i.pixiv.re", "pixiv.cat")

    /**
     * Ordered download URLs for [post]. Pixiv tries the original `i.pximg.net` first, then the
     * `i.pixiv.re` path proxy, then Pixiv.Cat last. Other platforms use one URL: the preferred
     * variant, or the post's best variant. Pixiv order is fixed by host, so [preferredVariant]
     * only applies to other platforms.
     */
    fun candidateUrls(post: Post, preferredVariant: MediaVariant? = null): List<String> {
        if (post.platform != PlatformId.PIXIV) {
            return listOf((preferredVariant ?: post.bestVariant).url)
        }
        return post.variants
            .filter { it.id != PREVIEW_VARIANT_ID }
            .map { it.url }
            .filter(String::isNotBlank)
            .distinct()
            .sortedBy(::pixivSourceRank)
            .ifEmpty { listOf(post.bestVariant.url) }
    }

    /**
     * Whether a failed attempt should move to the next source. [statusCode] is null for transport
     * errors (IO or timeout). The last source never falls back, so its failure fails or retries the work.
     */
    fun shouldFallback(
        statusCode: Int?,
        contentType: String?,
        host: String,
        hasNextSource: Boolean,
    ): Boolean {
        if (!hasNextSource) return false
        if (statusCode == null) return true
        return when (statusCode) {
            in 200..299 -> !acceptsImage(host, contentType)
            403, 404, 408, 429 -> true
            else -> statusCode in 500..599
        }
    }

    /** Pixiv hosts must answer with an image. Other hosts keep the existing unchecked behavior. */
    fun acceptsImage(host: String, contentType: String?): Boolean {
        if (host.lowercase() !in pixivImageHosts) return true
        val mediaType = contentType?.substringBefore(';')?.trim()?.lowercase()
        return mediaType?.startsWith("image/") == true
    }

    private fun pixivSourceRank(url: String): Int = when (url.toHttpUrlOrNull()?.host) {
        "i.pximg.net" -> 0
        "i.pixiv.re" -> 1
        "pixiv.cat" -> 2
        else -> 3
    }
}
