package com.azusachino.latte.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import com.azusachino.latte.ui.theme.glassBackdrop
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The post details sheet, drawn in the screen's own window rather than a dialog
 * window, so the app-wide toast stack stays above it. Its height is capped below
 * the detail top bar; taller content scrolls inside. Closes on back, a scrim tap,
 * or a downward drag on the handle or on content already scrolled to its top.
 */
@Composable
internal fun BoxScope.DetailInspectSheet(
    hazeState: HazeState,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // 0 = fully open, 1 = fully hidden below the screen edge.
    val hidden = remember { Animatable(1f) }
    var sheetHeight by remember { mutableIntStateOf(0) }
    var closing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hidden.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
    }

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            hidden.animateTo(1f, tween(durationMillis = 200))
            onDismiss()
        }
    }

    fun dragBy(deltaPx: Float) {
        if (sheetHeight <= 0 || closing) return
        scope.launch { hidden.snapTo((hidden.value + deltaPx / sheetHeight).coerceIn(0f, 1f)) }
    }

    fun settle(velocityY: Float) {
        if (closing) return
        if (hidden.value > DISMISS_FRACTION || velocityY > DISMISS_VELOCITY) {
            close()
        } else {
            scope.launch { hidden.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
        }
    }

    // Content scrolling owns the gesture until it is at its top; only then does a
    // downward pull move the sheet, and an upward push first reopens a pulled sheet.
    val nestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0 && hidden.value > 0f) {
                    dragBy(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && source == NestedScrollSource.UserInput) {
                    dragBy(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (hidden.value > 0f) {
                    settle(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    BackHandler(onBack = ::close)

    Box(
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer { alpha = 1f - hidden.value }
            .background(Color.Black.copy(alpha = 0.4f))
            .pointerInput(Unit) { detectTapGestures { close() } },
    )

    // Caps the sheet below the detail top bar.
    Box(
        modifier = Modifier
            .matchParentSize()
            .statusBarsPadding()
            .padding(top = TOP_BAR_CLEARANCE),
    ) {
        val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { sheetHeight = it.height }
                .offset { IntOffset(0, (hidden.value * sheetHeight).roundToInt()) }
                // Frosted over the image like the rest of the detail chrome, tinted to the theme for legible text.
                .glassBackdrop(hazeState, backgroundColor = Color.Black, shape = shape)
                // Taps on the sheet itself must not fall through to the scrim.
                .pointerInput(Unit) { detectTapGestures { } }
                .nestedScroll(nestedScroll),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta -> dragBy(delta) },
                        onDragStopped = { velocity -> settle(velocity) },
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                )
            }
            content()
        }
    }
}

/** Space kept clear above the sheet for the detail top bar. */
private val TOP_BAR_CLEARANCE = 72.dp

/** A drag past this hidden fraction closes the sheet on release. */
private const val DISMISS_FRACTION = 0.25f

/** A downward fling faster than this (px/s) closes the sheet. */
private const val DISMISS_VELOCITY = 1500f
