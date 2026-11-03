package com.azusachino.latte.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Thumb position in 0..1 for a list showing [visibleCount] of [totalCount] items from [firstVisibleIndex]. */
internal fun scrollbarFraction(firstVisibleIndex: Int, visibleCount: Int, totalCount: Int): Float {
    val scrollable = totalCount - visibleCount
    if (scrollable <= 0) return 0f
    return (firstVisibleIndex.toFloat() / scrollable).coerceIn(0f, 1f)
}

/** Item to jump to when the thumb is dragged to [fraction] of a [totalCount]-item list. */
internal fun scrollbarTargetIndex(fraction: Float, totalCount: Int): Int =
    if (totalCount <= 0) 0 else (fraction.coerceIn(0f, 1f) * (totalCount - 1)).roundToInt()

/**
 * Fast-scroll thumb for a staggered feed grid. It appears while the grid
 * scrolls and can then be dragged to jump through the loaded items; hidden,
 * it takes no touches, so taps on the grid's right edge are never stolen.
 */
@Composable
internal fun FeedScrollbar(
    state: LazyStaggeredGridState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val total by remember { derivedStateOf { state.layoutInfo.totalItemsCount } }
    val latestTotal by rememberUpdatedState(total)
    val fraction by remember {
        derivedStateOf {
            scrollbarFraction(
                state.firstVisibleItemIndex,
                state.layoutInfo.visibleItemsInfo.size,
                state.layoutInfo.totalItemsCount,
            )
        }
    }
    val scrollable by remember {
        derivedStateOf { state.layoutInfo.totalItemsCount > state.layoutInfo.visibleItemsInfo.size }
    }
    val active = dragging || state.isScrollInProgress
    val alpha by animateFloatAsState(
        targetValue = if (active && scrollable) 1f else 0f,
        animationSpec = if (active) tween(150) else tween(durationMillis = 400, delayMillis = 1200),
        label = "scrollbarAlpha",
    )
    if (alpha == 0f) return

    BoxWithConstraints(modifier = modifier.fillMaxHeight().width(THUMB_TOUCH_WIDTH)) {
        val thumbHeightPx = with(LocalDensity.current) { THUMB_HEIGHT.toPx() }
        val travelPx = (constraints.maxHeight - thumbHeightPx).coerceAtLeast(1f)
        val shown = if (dragging) dragFraction else fraction
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, (travelPx * shown).roundToInt()) }
                .size(THUMB_TOUCH_WIDTH, THUMB_HEIGHT)
                .alpha(alpha)
                .pointerInput(travelPx) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragFraction = fraction
                            dragging = true
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            dragFraction = (dragFraction + dragAmount / travelPx).coerceIn(0f, 1f)
                            scope.launch { state.scrollToItem(scrollbarTargetIndex(dragFraction, latestTotal)) }
                        },
                    )
                },
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
                    .size(width = if (dragging) 8.dp else 6.dp, height = THUMB_HEIGHT)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (dragging) 0.75f else 0.5f)),
            )
        }
    }
}

private val THUMB_HEIGHT = 56.dp
private val THUMB_TOUCH_WIDTH = 28.dp
