package com.azusachino.latte.ui.detail

import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

import androidx.compose.foundation.ExperimentalFoundationApi

/**
 * Pinch-zoomable box. [onTap] receives the tap's horizontal position as a 0..1 fraction of the
 * box width and whether the content is currently zoomed (scale > 1.05). Scale and offset reset
 * whenever [resetKey] changes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZoomableBox(
    modifier: Modifier = Modifier,
    minScale: Float = 1f,
    maxScale: Float = 4f,
    resetKey: Any? = null,
    onTap: (xFraction: Float, zoomed: Boolean) -> Unit = { _, _ -> },
    content: @Composable () -> Unit,
) {
    var scale by remember(resetKey) { mutableFloatStateOf(1f) }
    var offset by remember(resetKey) { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val currentOnTap by rememberUpdatedState(onTap)

    val transformableState = rememberTransformableState { _, zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(minScale, maxScale)
        scale = newScale
        if (newScale > 1.02f) {
            val maxOffsetX = (boxSize.width * (scale - 1f)) / 2f
            val maxOffsetY = (boxSize.height * (scale - 1f)) / 2f
            offset = Offset(
                (offset.x + panChange.x).coerceIn(-maxOffsetX, maxOffsetX),
                (offset.y + panChange.y).coerceIn(-maxOffsetY, maxOffsetY),
            )
        } else {
            scale = 1f
            offset = Offset.Zero
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(resetKey) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        currentOnTap(
                            (tapOffset.x / size.width).coerceIn(0f, 1f),
                            scale > 1.05f,
                        )
                    },
                    onDoubleTap = { tapOffset ->
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f
                            offset = Offset(
                                (centerX - tapOffset.x) * 1.5f,
                                (centerY - tapOffset.y) * 1.5f,
                            )
                        }
                    },
                )
            }
            .transformable(
                state = transformableState,
                canPan = { scale > 1.05f },
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
