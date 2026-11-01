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
 * the same work always produces the same name and the same key. Download
 * dedup runs against the per-platform keyset parsed from these names
 * ([parseKey] is the single source of key semantics).
 */
object DownloadIdentity {
    /** Page suffix of the canonical file name, "" for single-page works. */
    fun pageSuffix(post: Post): String =
        if (post.pageCount > 1) "_p${post.pageIndex + 1}" else ""

    /** Canonical file name: `<platform>_<id>[_pN].<ext>`, e.g. `yande.re_1271440.jpg`. */
    fun fileName(post: Post): String =
        "${post.platform.externalId}_${post.id}${pageSuffix(post)}.${fileExtension(post)}"

    /** Dedup key within a platform: the id, plus the page for multi-page works. */
    fun key(post: Post): String =
        if (post.pageCount > 1) "${post.id}:p${post.pageIndex + 1}" else "${post.id}"

    private val CURRENT_NAME = Regex("^([a-z.]+)_(\\d+)(?:_p(\\d+))?\\.[a-z]+$")

    /**
     * Extracts [key] semantics from a stored file name. Returns null for
     * names this platform did not save.
     */
    fun parseKey(platform: PlatformId, displayName: String): String? {
        CURRENT_NAME.find(displayName)?.let { match ->
            val (foundPlatform, id, page) = match.destructured
            if (foundPlatform == platform.externalId) {
                return if (page.isNotEmpty()) "$id:p$page" else id
            }
        }
        return null
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
