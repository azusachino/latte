package com.azusachino.latte.ui.common

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
