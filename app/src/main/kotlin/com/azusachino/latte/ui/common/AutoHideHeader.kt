package com.azusachino.latte.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.azusachino.latte.data.settings.UiStyle
import com.azusachino.latte.ui.theme.LocalUiStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.azusachino.latte.ui.theme.glassSurface
import com.azusachino.latte.ui.theme.pressScale
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Reports whether a scroll-linked hideable header should be shown: hidden
 * once the observed list has scrolled down past [hideAfterItems] distinct
 * first-visible indices, shown again on any upward scroll (or when the list
 * returns near the top). Pass [forceVisible] to pin the header regardless of
 * scroll (e.g. selection mode).
 *
 * [trackingKey] resets the direction tracking — pass the active list state so
 * switching tabs, feeds, or search results restarts from that list's current
 * position instead of comparing against the previous one.
 */
@Composable
fun rememberHeaderVisibleState(
    firstVisibleItemIndex: () -> Int,
    trackingKey: Any? = null,
    hideAfterItems: Int = 4,
    forceVisible: Boolean = false,
): Boolean {
    var visible by rememberSaveable { mutableStateOf(true) }
    val lastSeenIndex = remember { mutableIntStateOf(0) }
    val currentIndexProducer by rememberUpdatedState(firstVisibleItemIndex)

    LaunchedEffect(trackingKey) {
        lastSeenIndex.intValue = currentIndexProducer()
        snapshotFlow { currentIndexProducer() }
            .distinctUntilChanged()
            .collect { index ->
                val previous = lastSeenIndex.intValue
                lastSeenIndex.intValue = index
                if (index > previous && index >= hideAfterItems) {
                    visible = false
                } else if (index < previous || index < hideAfterItems) {
                    visible = true
                }
            }
    }

    return forceVisible || visible
}

/**
 * Slides its content up off-screen when [hidden] flips true and back when it
 * flips false, using a draw-time transform: the content's layout height never
 * changes, so scrolling lists and haze headers are not re-measured mid-gesture
 * (which is what makes height-animating hide/show feel janky).
 */
@Composable
fun SlideAwayHeader(
    hidden: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val slideFraction = remember { Animatable(if (hidden) 1f else 0f) }
    var heightPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(hidden) {
        slideFraction.animateTo(if (hidden) 1f else 0f, tween(durationMillis = 200))
    }

    Box(
        modifier = modifier
            .graphicsLayer { translationY = -heightPx * slideFraction.value }
            .onSizeChanged { heightPx = it.height },
    ) {
        content()
    }
}

/**
 * Back-to-top affordance:
 * Harmonized floating circular pill across Material 3 and Glass with the palette-tinted arrow icon.
 */
@Composable
fun BackToTopButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val isMaterial = LocalUiStyle.current == UiStyle.MATERIAL
    Box(
        modifier = modifier
            .size(44.dp)
            .then(
                if (isMaterial) {
                    Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape)
                        .clickable(onClick = onClick)
                } else {
                    Modifier
                        .pressScale()
                        .glassSurface(shape = CircleShape, isElevated = true)
                        .clickable(onClick = onClick)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = "Back to top",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
    }
}
