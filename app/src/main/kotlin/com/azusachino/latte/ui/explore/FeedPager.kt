package com.azusachino.latte.ui.explore


import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.azusachino.latte.ui.theme.pressScale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azusachino.latte.ui.common.LatteIcons

/**
 * Page-mode controls for one feed. [maxDirectPage] is the highest page the
 * source opens by number; null means it pages by cursor only, so there is no
 * "Go to page" and Prev/Next walk visited pages.
 */
internal class FeedPager(
    val maxDirectPage: Int?,
    val onGoToPage: (Int) -> Unit,
)

@Composable
internal fun FeedPagerBar(
    pager: FeedPager,
    feed: FeedState,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var showJump by remember { mutableStateOf(false) }
    val idle = !feed.isLoading && !feed.isRefreshing
    val maxPage = pager.maxDirectPage
    fun turn(page: Int) {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        pager.onGoToPage(page)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.45f)),
    ) {
        IconButton(
            onClick = { turn(feed.page - 1) },
            enabled = idle && feed.page > 1,
            modifier = Modifier.size(40.dp).pressScale(),
        ) {
            Icon(LatteIcons.ChevronLeft, contentDescription = "Previous page", tint = Color.White)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .semantics { contentDescription = "Page ${feed.page}" }
                .clip(RoundedCornerShape(999.dp))
                .then(
                    if (maxPage != null && idle) {
                        Modifier.clickable(onClickLabel = "Go to page") { showJump = true }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            // The number rolls the way the page turned.
            AnimatedContent(
                targetState = feed.page,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInVertically { h -> if (forward) h else -h } + fadeIn()) togetherWith
                        (slideOutVertically { h -> if (forward) -h else h } + fadeOut())
                },
                label = "pagerNumber",
            ) { page ->
                Text(
                    text = "$page",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                )
            }
        }
        IconButton(
            onClick = { turn(feed.page + 1) },
            enabled = idle && feed.hasMore,
            modifier = Modifier.size(40.dp).pressScale(),
        ) {
            Icon(LatteIcons.ChevronRight, contentDescription = "Next page", tint = Color.White)
        }
    }

    if (showJump && maxPage != null) {
        PageDialDialog(
            currentPage = feed.page,
            maxPage = maxPage,
            onDismiss = { showJump = false },
            onConfirm = { page ->
                showJump = false
                pager.onGoToPage(page)
            },
        )
    }
}
