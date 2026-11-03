package com.azusachino.latte.ui.explore

import com.azusachino.latte.data.model.Post

/** Pixiv pages that may add nothing visible in a row before pagination stops. */
internal const val MAX_EMPTY_APPEND_STREAK = 3

/**
 * Appends [incoming] to this feed, keeping existing order. A post is skipped when its work is
 * already present or repeated earlier in [incoming]. Work identity is used, so Pixiv pages of one
 * multi-page work count once.
 */

internal fun List<Post>.appendDistinct(incoming: List<Post>): List<Post> {
    val seen = mapTo(HashSet()) { it.workIdentity }
    return this + incoming.filter { seen.add(it.workIdentity) }
}

/**
 * Consecutive appended pages that added no new works. Resets as soon as an append adds one,
 * so a long feed does not run out of auto-loads after a few ordinary pages.
 */

internal fun nextEmptyAppendStreak(isAppend: Boolean, previousStreak: Int, addedCount: Int): Int =
    if (isAppend && addedCount == 0) previousStreak + 1 else 0

/** Auto-fetch the next Pixiv page when this one added nothing new, the source has more, and the streak is under the cap. */

internal fun shouldAutoLoadNextPage(addedCount: Int, hasMore: Boolean, streak: Int): Boolean =
    addedCount == 0 && hasMore && streak < MAX_EMPTY_APPEND_STREAK

/** One page-mode fetch: [page] opened by a known continuation [cursor], or by number when null. */
internal data class PageRequest(val page: Int, val cursor: String?)

/**
 * Resolves how page mode fetches [page] of [feed] in a single request: page 1 fresh, the next
 * page by the live cursor, a visited page by its recorded cursor, else by number when the source
 * can open it directly ([maxDirectPage]). Null when the page cannot be reached in one request.
 */
internal fun pageRequestFor(feed: FeedState, page: Int, maxDirectPage: Int?): PageRequest? = when {
    page < 1 -> null
    page == 1 -> PageRequest(1, null)
    page == feed.page + 1 && feed.nextCursor != null -> PageRequest(page, feed.nextCursor)
    feed.pageCursors[page] != null -> PageRequest(page, feed.pageCursors[page])
    maxDirectPage != null && page <= maxDirectPage -> PageRequest(page, null)
    else -> null
}

/** Records the cursor that opened [request] and the one that continues past it. */
internal fun Map<Int, String>.recordPage(request: PageRequest, nextCursor: String?): Map<Int, String> {
    var cursors = this
    request.cursor?.let { cursors = cursors + (request.page to it) }
    nextCursor?.let { cursors = cursors + (request.page + 1 to it) }
    return cursors
}
