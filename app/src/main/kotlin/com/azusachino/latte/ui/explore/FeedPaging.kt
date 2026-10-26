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
