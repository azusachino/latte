package com.azusachino.latte.ui.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import com.azusachino.latte.ui.common.isReducedMotionEnabled
import kotlinx.coroutines.launch

/**
 * Pinch-zoomable box. [onTap] receives the tap's horizontal position as a 0..1 fraction of the
 * box width and whether the content is currently zoomed (scale > 1.05). Scale and offset reset
 * whenever [resetKey] changes. [onZoomChanged] reports whether the content is currently zoomed.
 */
@Composable
fun ZoomableBox(
    modifier: Modifier = Modifier,
    minScale: Float = 1f,
    maxScale: Float = 4f,
    resetKey: Any? = null,
    onZoomChanged: (Boolean) -> Unit = {},
    onTap: (xFraction: Float, zoomed: Boolean) -> Unit = { _, _ -> },
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scaleAnim = remember(resetKey) { Animatable(1f) }
    val offsetAnim = remember(resetKey) { Animatable(Offset.Zero, Offset.VectorConverter) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnZoomChanged by rememberUpdatedState(onZoomChanged)
    val scope = rememberCoroutineScope()

    val isZoomed = scaleAnim.value > 1.05f
    LaunchedEffect(isZoomed) {
        currentOnZoomChanged(isZoomed)
    }

    val transformableState = rememberTransformableState { _, zoomChange, panChange, _ ->
        val currentScale = scaleAnim.value
        val newScale = (currentScale * zoomChange).coerceIn(minScale, maxScale)
        scope.launch {
            scaleAnim.snapTo(newScale)
            if (newScale > 1.02f) {
                val maxOffsetX = (boxSize.width * (newScale - 1f)) / 2f
                val maxOffsetY = (boxSize.height * (newScale - 1f)) / 2f
                offsetAnim.snapTo(
                    Offset(
                        (offsetAnim.value.x + panChange.x).coerceIn(-maxOffsetX, maxOffsetX),
                        (offsetAnim.value.y + panChange.y).coerceIn(-maxOffsetY, maxOffsetY),
                    ),
                )
            } else {
                offsetAnim.snapTo(Offset.Zero)
            }
        }
    }

    val zoomAnimSpec = spring<Float>(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMediumLow,
    )
    val offsetAnimSpec = spring<Offset>(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMediumLow,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(resetKey) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        currentOnTap(
                            (tapOffset.x / size.width).coerceIn(0f, 1f),
                            scaleAnim.value > 1.05f,
                        )
                    },
                    onDoubleTap = { tapOffset ->
                        val reducedMotion = context.isReducedMotionEnabled()
                        scope.launch {
                            if (scaleAnim.value > 1.05f) {
                                if (reducedMotion) {
                                    scaleAnim.snapTo(1f)
                                    offsetAnim.snapTo(Offset.Zero)
                                } else {
                                    launch { scaleAnim.animateTo(1f, zoomAnimSpec) }
                                    launch { offsetAnim.animateTo(Offset.Zero, offsetAnimSpec) }
                                }
                            } else {
                                val targetScale = 2.5f
                                val centerX = size.width / 2f
                                val centerY = size.height / 2f
                                val targetOffset = Offset(
                                    (centerX - tapOffset.x) * (targetScale - 1f),
                                    (centerY - tapOffset.y) * (targetScale - 1f),
                                )
                                val maxOffsetX = (boxSize.width * (targetScale - 1f)) / 2f
                                val maxOffsetY = (boxSize.height * (targetScale - 1f)) / 2f
                                val clampedOffset = Offset(
                                    targetOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                                    targetOffset.y.coerceIn(-maxOffsetY, maxOffsetY),
                                )
                                if (reducedMotion) {
                                    scaleAnim.snapTo(targetScale)
                                    offsetAnim.snapTo(clampedOffset)
                                } else {
                                    launch { scaleAnim.animateTo(targetScale, zoomAnimSpec) }
                                    launch { offsetAnim.animateTo(clampedOffset, offsetAnimSpec) }
                                }
                            }
                        }
                    },
                )
            }
            .transformable(
                state = transformableState,
                canPan = { scaleAnim.value > 1.05f },
            )
            .graphicsLayer {
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
                translationX = offsetAnim.value.x
                translationY = offsetAnim.value.y
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
