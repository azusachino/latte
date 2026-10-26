package com.azusachino.latte.data.download

import com.azusachino.latte.data.model.Post

object DownloadIdentity {
    fun displayName(post: Post): String {
        val sourceLabel = post.platform.externalId
        val pageSuffix = if (post.pageCount > 1) " p${post.pageIndex + 1}" else ""
        val safeTitle = (post.title ?: post.tags.take(4).joinToString(" "))
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val extension = fileExtension(post)
        return if (safeTitle.isNotBlank()) {
            "$sourceLabel ${post.id}$pageSuffix $safeTitle.$extension"
        } else {
            "$sourceLabel ${post.id}$pageSuffix.$extension"
        }
    }

    /** Short toast label that names the post, not the file: "#1271440" or "#75034219 p2". */
    fun toastLabel(post: Post): String {
        val pageSuffix = if (post.pageCount > 1) " p${post.pageIndex + 1}" else ""
        return "#${post.id}$pageSuffix"
    }

    /** "1 post" or "3 posts" for batch toasts. */
    fun postCountLabel(count: Int): String = if (count == 1) "1 post" else "$count posts"

    fun fileExtension(post: Post): String =
        post.originalUrl.substringBefore('?')
            .substringAfterLast('.', "jpg")
            .lowercase()
            .takeIf { it in setOf("jpg", "jpeg", "png", "gif", "webp") }
            ?: "jpg"
}
