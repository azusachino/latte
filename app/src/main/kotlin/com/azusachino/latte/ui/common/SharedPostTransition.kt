package com.azusachino.latte.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.plugin.PlatformId

/**
 * Grid tile ↔ image viewer morph. The app-level [SharedTransitionScope] wraps
 * screen navigation; a tile and the viewer's current image share a key, so the
 * tapped tile grows into the viewer and shrinks back into its tile on return.
 *
 * Keys carry the grid a tile lives in: Explore composes neighbouring tabs, and
 * one post can sit in two of them at once.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
class SharedPostTransition(val scope: SharedTransitionScope) {
    /** Grid the viewer was opened from; the viewer's image matches only that grid's tiles. */
    var originGrid: String? by mutableStateOf(null)
}

val LocalSharedPostTransition = staticCompositionLocalOf<SharedPostTransition?> { null }

/** The navigation transition's scope for the screen being composed. */
val LocalScreenVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

private data class SharedPostKey(val grid: String, val platform: PlatformId, val id: Long)

@OptIn(ExperimentalSharedTransitionApi::class)
private val PostBoundsTransform = BoundsTransform { _: Rect, _: Rect ->
    spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
}

/**
 * Marks this node as [post]'s image in [grid]. A no-op outside the app's
 * transition (previews, tests) or when [grid] is null.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedPostImage(grid: String?, post: Post): Modifier {
    val transition = LocalSharedPostTransition.current ?: return this
    val visibility = LocalScreenVisibilityScope.current ?: return this
    if (grid == null) return this
    return with(transition.scope) {
        this@sharedPostImage.sharedElement(
            sharedContentState = rememberSharedContentState(SharedPostKey(grid, post.platform, post.id)),
            animatedVisibilityScope = visibility,
            boundsTransform = PostBoundsTransform,
        )
    }
}
