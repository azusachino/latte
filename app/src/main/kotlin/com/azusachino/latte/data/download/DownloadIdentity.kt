package com.azusachino.latte.data.download

import com.azusachino.latte.data.model.MediaVariant
import com.azusachino.latte.data.model.Post

object DownloadIdentity {
    fun displayName(post: Post, variant: MediaVariant = post.bestVariant): String {
        val extension = variant.extension
            ?: if (variant.url.endsWith(".png", ignoreCase = true)) "png" else "jpg"
        val sourceLabel = if (post.siteId == "yande.re") "yande.re" else post.siteId
        val pageSuffix = if (post.pageCount > 1) " p${post.pageIndex + 1}" else ""
        val variantSuffix = if (post.siteId == "pixiv") {
            " ${variant.id.toSafeFilePart()}"
        } else {
            ""
        }
        val safeTitle = (post.title ?: post.tags.take(4).joinToString(" "))
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return if (safeTitle.isNotBlank()) {
            "$sourceLabel ${post.id}$pageSuffix$variantSuffix $safeTitle.$extension"
        } else {
            "$sourceLabel ${post.id}$pageSuffix$variantSuffix.$extension"
        }
    }

    private fun String.toSafeFilePart(): String =
        replace(Regex("[^A-Za-z0-9._-]"), "-")
}
