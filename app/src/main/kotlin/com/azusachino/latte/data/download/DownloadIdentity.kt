package com.azusachino.latte.data.download

import com.azusachino.latte.DownloadWorker
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.plugin.PlatformId

/**
 * Stable, title-free naming and identity for saved downloads.
 *
 * Files are saved as `<platform>_<id>[_pN].<ext>` inside a per-platform
 * subfolder of the album (`Pictures/Latte/yande.re/`, ...). The name is pure
 * identity — no title or tags, whose values can vary between sessions — so
 * dedup queries on the canonical name (plus a tolerant match against the
 * legacy title-bearing names) can never misidentify a post.
 *
 * Legacy files from pre-0.3.9 builds were saved flat into the album as
 * `<platform> <id>[ pN] <title>.<ext>`; [legacyNamePrefix] lets dedup match
 * those too, so previously saved posts do not re-download once.
 */
object DownloadIdentity {
    /** Page suffix of the canonical file name, "" for single-page works. */
    fun pageSuffix(post: Post): String =
        if (post.pageCount > 1) "_p${post.pageIndex + 1}" else ""

    /** Canonical file name: `<platform>_<id>[_pN].<ext>`, e.g. `yande.re_1271440.jpg`. */
    fun fileName(post: Post): String =
        "${post.platform.externalId}_${post.id}${pageSuffix(post)}.${fileExtension(post)}"

    /**
     * Prefix of the legacy (pre-0.3.9) file name for the same post, e.g.
     * `yande.re 1271440` — used as a tolerant LIKE pattern against files
     * saved by older builds.
     */
    fun legacyNamePrefix(post: Post): String {
        val pageSuffix = if (post.pageCount > 1) " p${post.pageIndex + 1}" else ""
        return "${post.platform.externalId} ${post.id}$pageSuffix"
    }

    /** Album subfolder holding this platform's downloads: `Pictures/Latte/<platform>`. */
    fun albumPath(platform: PlatformId): String = "${DownloadWorker.album}/${platform.externalId}"

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
