package com.azusachino.latte.data.network

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import okhttp3.HttpUrl.Companion.toHttpUrl

sealed interface PixivCatResponse {
    data class Image(val contentType: String, val filename: String) : PixivCatResponse

    data class NonImage(val statusCode: Int, val contentType: String?) : PixivCatResponse

    data class RetryableFailure(val statusCode: Int) : PixivCatResponse
}

class PixivCatResolver(
    private val baseUrl: String = "https://pixiv.cat",
) {
    init {
        val url = baseUrl.toHttpUrl()
        require(url.scheme == "https" && url.host == "pixiv.cat") {
            "Pixiv.Cat resolver requires the canonical HTTPS host"
        }
    }

    fun url(workId: Long, pageIndex: Int, extension: String): String {
        require(workId > 0) { "workId must be positive" }
        require(pageIndex >= 0) { "pageIndex must not be negative" }
        val normalizedExtension = extension.trim().lowercase().removePrefix(".")
        require(normalizedExtension in ALLOWED_EXTENSIONS) {
            "unsupported Pixiv media extension: $extension"
        }
        val pageSuffix = if (pageIndex == 0) "" else "-$pageIndex"
        return "${baseUrl.trimEnd('/')}/$workId$pageSuffix.$normalizedExtension"
    }

    fun classifyResponse(
        code: Int,
        contentType: String?,
        contentDisposition: String?,
    ): PixivCatResponse {
        if (code == 408 || code == 429 || code in 500..599) {
            return PixivCatResponse.RetryableFailure(code)
        }

        val normalizedContentType = contentType?.substringBefore(';')?.trim()?.lowercase()
        if (code !in 200..299 || normalizedContentType?.startsWith("image/") != true) {
            return PixivCatResponse.NonImage(code, normalizedContentType)
        }

        val filename = filenameFrom(contentDisposition) ?: "pixiv-image"
        return PixivCatResponse.Image(normalizedContentType, filename)
    }

    private fun filenameFrom(contentDisposition: String?): String? {
        if (contentDisposition.isNullOrBlank()) return null
        val match = FILENAME_PATTERN.find(contentDisposition) ?: return null
        val raw = match.groupValues[1].trim().trim('"')
        if (raw.isBlank()) return null
        return if (raw.startsWith("UTF-8''", ignoreCase = true)) {
            URLDecoder.decode(raw.substring(7), StandardCharsets.UTF_8.name())
        } else {
            raw
        }
    }

    private companion object {
        val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp")
        val FILENAME_PATTERN = Regex("(?:^|;)\\s*filename\\*?=([^;]+)", RegexOption.IGNORE_CASE)
    }
}
