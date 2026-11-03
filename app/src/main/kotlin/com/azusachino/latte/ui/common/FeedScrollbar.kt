package com.azusachino.latte.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridLayoutInfo
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Estimated scroll extent of a staggered grid in px: [scrolled] so far and the
 * [scrollable] total. Built from the visible rows' average height, so the thumb
 * moves continuously instead of stepping a whole tile at a time.
 */
internal data class ScrollExtent(val scrolled: Float, val scrollable: Float) {
    val fraction: Float get() = if (scrollable <= 0f) 0f else (scrolled / scrollable).coerceIn(0f, 1f)
}

/**
 * [firstVisibleIndex]/[firstVisibleOffset] locate the top; [totalCount] items in
 * [lanes] columns of about [averageItemHeight] px each, in a [viewportHeight] px window.
 */
internal fun scrollExtent(
    firstVisibleIndex: Int,
    firstVisibleOffset: Int,
    totalCount: Int,
    lanes: Int,
    averageItemHeight: Float,
    viewportHeight: Int,
): ScrollExtent {
    if (totalCount <= 0 || averageItemHeight <= 0f) return ScrollExtent(0f, 0f)
    val laneCount = lanes.coerceAtLeast(1)
    val rows = (totalCount + laneCount - 1) / laneCount
    val content = rows * averageItemHeight
    val scrolled = (firstVisibleIndex / laneCount) * averageItemHeight + firstVisibleOffset
    return ScrollExtent(scrolled, (content - viewportHeight).coerceAtLeast(0f))
}

private fun LazyStaggeredGridLayoutInfo.extent(firstIndex: Int, firstOffset: Int): ScrollExtent {
    val visible = visibleItemsInfo
    val lanes = visible.maxOfOrNull { it.lane }?.plus(1) ?: 1
    val average = if (visible.isEmpty()) 0f else visible.sumOf { it.size.height }.toFloat() / visible.size
    return scrollExtent(
        firstVisibleIndex = firstIndex,
        firstVisibleOffset = firstOffset,
        totalCount = totalItemsCount,
        lanes = lanes,
        averageItemHeight = average,
        viewportHeight = viewportEndOffset - viewportStartOffset,
    )
}

/**
 * Fast-scroll thumb for a staggered feed grid. It appears while the grid
 * scrolls and can then be dragged; hidden, it takes no touches, so taps on the
 * grid's right edge are never stolen. Dragging scrolls the grid continuously
 * (no jumps), which keeps the grid's column layout stable.
 */
@Composable
internal fun FeedScrollbar(
    state: LazyStaggeredGridState,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val extent by remember {
        derivedStateOf { state.layoutInfo.extent(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) }
    }
    val scrollable by remember { derivedStateOf { extent.scrollable > 0f } }
    val active = dragging || state.isScrollInProgress
    val alpha by animateFloatAsState(
        targetValue = if (active && scrollable) 1f else 0f,
        animationSpec = if (active) tween(150) else tween(durationMillis = 400, delayMillis = 2000),
        label = "scrollbarAlpha",
    )
    if (alpha == 0f) return

    BoxWithConstraints(modifier = modifier.fillMaxHeight().width(THUMB_TOUCH_WIDTH)) {
        val thumbHeightPx = with(LocalDensity.current) { THUMB_HEIGHT.toPx() }
        val travelPx = (constraints.maxHeight - thumbHeightPx).coerceAtLeast(1f)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                // Read in the layout phase: scrolling moves the thumb without recomposing.
                .offset {
                    val shown = if (dragging) dragFraction else extent.fraction
                    IntOffset(0, (travelPx * shown).roundToInt())
                }
                .size(THUMB_TOUCH_WIDTH, THUMB_HEIGHT)
                .alpha(alpha)
                .semantics { contentDescription = "Scrollbar" }
                .pointerInput(travelPx) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragFraction = extent.fraction
                            dragging = true
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val before = dragFraction
                            dragFraction = (dragFraction + dragAmount / travelPx).coerceIn(0f, 1f)
                            // Content px per thumb px; a plain scroll, measured item by item.
                            val contentDelta = (dragFraction - before) * extent.scrollable
                            state.dispatchRawDelta(contentDelta)
                        },
                    )
                },
        ) {
            // Light pill with a dark rim: it reads over any artwork, in either theme.
            val thumbShape = RoundedCornerShape(50)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 3.dp)
                    .shadow(if (dragging) 6.dp else 3.dp, thumbShape)
                    .size(width = if (dragging) 12.dp else 8.dp, height = THUMB_HEIGHT)
                    .clip(thumbShape)
                    .background(Color.White.copy(alpha = if (dragging) 0.95f else 0.85f))
                    .border(1.dp, Color.Black.copy(alpha = 0.22f), thumbShape),
            )
        }
    }
}

private val THUMB_HEIGHT = 56.dp
private val THUMB_TOUCH_WIDTH = 28.dp
