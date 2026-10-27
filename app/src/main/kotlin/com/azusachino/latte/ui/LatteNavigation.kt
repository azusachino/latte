package com.azusachino.latte.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntOffset
import com.azusachino.latte.data.model.Post

sealed interface Screen {
    data object Explore : Screen
    data class AuthorWorks(val authorId: Long? = null, val authorName: String = "") : Screen
    data class TagSearch(val query: String) : Screen
    data class Detail(
        val posts: List<Post>,
        val initialIndex: Int,
        val initialPageIndex: Int = 0,
        val parentSearchTags: String? = null,
        val parentAuthorId: Long? = null,
        val parentAuthorName: String? = null,
    ) : Screen
    data object Settings : Screen
    data object FavoriteTags : Screen
    data object AccountManager : Screen
}

internal fun detailPostsFor(screen: Screen.Detail, activePosts: List<Post>): List<Post> {
    if (activePosts.isEmpty()) return screen.posts
    val currentWork = screen.posts.getOrNull(screen.initialIndex)?.workIdentity
    if (currentWork != null && activePosts.getOrNull(screen.initialIndex)?.workIdentity == currentWork) {
        return activePosts
    }
    if (screen.posts.isNotEmpty() && activePosts.firstOrNull()?.workIdentity == screen.posts.firstOrNull()?.workIdentity) {
        if (currentWork == null || activePosts.any { it.workIdentity == currentWork }) {
            return activePosts
        }
    }
    return screen.posts
}

internal data class ScreenStack(
    val screens: List<Screen> = listOf(Screen.Explore),
) {
    val current: Screen
        get() = screens.last()

    val size: Int
        get() = screens.size

    fun push(screen: Screen): ScreenStack = copy(screens = screens + screen)

    fun pop(): ScreenStack = if (screens.size > 1) {
        copy(screens = screens.dropLast(1))
    } else {
        this
    }

    fun popAndRestoreDetailFeed(onReturnToDetail: (Screen.Detail) -> Unit = {}): ScreenStack {
        val target = screens.getOrNull(screens.lastIndex - 1) as? Screen.Detail
        val fromSearchOrAuthor = current is Screen.TagSearch || current is Screen.AuthorWorks
        if (fromSearchOrAuthor && target != null) {
            onReturnToDetail(target)
        }
        return pop()
    }

    fun replaceTop(screen: Screen): ScreenStack = if (screens.isNotEmpty()) {
        copy(screens = screens.dropLast(1) + screen)
    } else {
        copy(screens = listOf(screen))
    }

    val currentStateKey: String
        get() = "${screens.lastIndex}:${current.stateKey}"
}

internal val Screen.stateKey: String
    get() = when (this) {
        is Screen.Explore -> "explore"
        is Screen.AuthorWorks -> "author-works:${authorId ?: authorName}"
        is Screen.TagSearch -> "tag-search:${query}"
        is Screen.Detail -> "detail:${posts.getOrNull(initialIndex)?.workIdentity ?: initialIndex}:$initialPageIndex:${parentSearchTags.orEmpty()}:${parentAuthorId ?: ""}:${parentAuthorName.orEmpty()}"
        is Screen.Settings -> "settings"
        is Screen.FavoriteTags -> "favorite-tags"
        is Screen.AccountManager -> "account-manager"
    }

// A quick, slightly overshooting settle (inspired by transitions.dev's toggle
// curve) rather than Compose's flatter default spring for slideIn/Out.
internal val screenSlideSpec = spring<IntOffset>(
    dampingRatio = 0.8f,
    stiffness = Spring.StiffnessMediumLow,
)
