package com.azusachino.latte.ui.history

import com.azusachino.latte.ui.common.GlassAlertDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.settings.LattePreferences
import com.azusachino.latte.plugin.PlatformId
import com.azusachino.latte.ui.common.BackToTopButton
import com.azusachino.latte.ui.common.GlassPillChip
import com.azusachino.latte.ui.common.GlassTopBar
import com.azusachino.latte.ui.common.ToastManager
import com.azusachino.latte.ui.common.ToastType
import com.azusachino.latte.ui.common.glassCard
import com.azusachino.latte.ui.common.SlideAwayHeader
import com.azusachino.latte.ui.common.rememberHeaderVisibleState
import com.azusachino.latte.ui.explore.PostGridItem
import com.azusachino.latte.ui.explore.shouldShowBackToTop
import com.azusachino.latte.ui.theme.pressScale
import kotlinx.coroutines.launch

/**
 * Posts the owner has opened, most recent first, across every platform, laid
 * out in the same grid as Explore. Each tile carries its platform name at the
 * bottom. The glass pills narrow the grid to one platform, and tapping a tile
 * opens Detail over the filtered grid so it can be swiped through.
 * Long-pressing a tile removes that entry (with an undo toast), and the
 * top-bar trash clears the whole history — or just the filtered platform,
 * after asking for confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewHistoryScreen(
    preferences: LattePreferences,
    onBack: () -> Unit,
    onPostClick: (posts: List<Post>, index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val history by preferences.viewHistory.collectAsStateWithLifecycle()
    val columnCount by preferences.columnCount.collectAsStateWithLifecycle()
    var platformFilter by rememberSaveable { mutableStateOf<PlatformId?>(null) }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    val visible = remember(history, platformFilter) {
        history.filter { platformFilter == null || it.platform == platformFilter }
    }
    val gridState = rememberLazyStaggeredGridState()
    val headerVisible = rememberHeaderVisibleState(
        firstVisibleItemIndex = { gridState.firstVisibleItemIndex },
        trackingKey = gridState,
    )
    val density = LocalDensity.current
    var headerHeightPx by remember { mutableIntStateOf(0) }
    // Keeps the first tiles below the header; constant while the header
    // slides, so the grid never re-measures mid-scroll. Scrolled tiles render
    // behind the translucent header, matching Explore.
    val headerHeight = with(density) {
        headerHeightPx.toDp().takeIf { it > 0.dp } ?: 112.dp
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                history.isEmpty() -> EmptyHistory("Posts you open will appear here.")
                visible.isEmpty() -> EmptyHistory("No ${platformFilter?.displayName} posts in your history yet.")
                else -> HistoryGrid(
                    gridState = gridState,
                    posts = visible,
                    columnCount = columnCount,
                    onPostClick = onPostClick,
                    topPadding = headerHeight,
                    onRemove = { post ->
                        preferences.removeViewedPosts(listOf(post))
                        ToastManager.show("Removed from history", ToastType.INFO, "Undo") {
                            preferences.recordView(post)
                        }
                    },
                )
            }

            SlideAwayHeader(
                hidden = !headerVisible,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.onSizeChanged { headerHeightPx = it.height },
                ) {
                    GlassTopBar(
                        title = "Viewing History",
                        onBack = onBack,
                        actions = {
                            if (history.isNotEmpty()) {
                                IconButton(
                                    onClick = { showClearDialog = true },
                                    modifier = Modifier.pressScale(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Clear viewing history",
                                    )
                                }
                            }
                        },
                    )
                    if (history.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            GlassPillChip(
                                selected = platformFilter == null,
                                onClick = { platformFilter = null },
                                label = "All",
                            )
                            PlatformId.entries.forEach { platform ->
                                GlassPillChip(
                                    selected = platformFilter == platform,
                                    onClick = { platformFilter = platform },
                                    label = platform.displayName,
                                )
                            }
                        }
                    }
                }
            }
        }

            val filter = platformFilter
            if (showClearDialog && history.isNotEmpty()) {
                GlassAlertDialog(
                    onDismissRequest = { showClearDialog = false },
                    title = { Text("Clear viewing history?") },
                    text = {
                        Text(
                            if (filter == null) {
                                "This removes all ${visible.size} saved posts. This cannot be undone."
                            } else {
                                "This removes ${visible.size} ${filter.displayName} posts. This cannot be undone."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showClearDialog = false
                            if (filter == null) {
                                preferences.clearViewHistory()
                            } else {
                                preferences.removeViewedPosts(visible)
                            }
                        }) {
                            Text("Clear")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearDialog = false }) {
                            Text("Cancel")
                        }
                    },
                )
            }
        }
}

/**
 * The filtered history grid with Explore's back-to-top affordance, so long
 * history grids behave like every other feed.
 */
@Composable
private fun HistoryGrid(
    gridState: LazyStaggeredGridState,
    posts: List<Post>,
    columnCount: Int,
    onPostClick: (posts: List<Post>, index: Int) -> Unit,
    onRemove: (Post) -> Unit,
    topPadding: Dp = 0.dp,
) {
    val coroutineScope = rememberCoroutineScope()
    val showBackToTop by remember {
        derivedStateOf { shouldShowBackToTop(gridState.firstVisibleItemIndex) }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalStaggeredGrid(
            state = gridState,
            columns = StaggeredGridCells.Fixed(columnCount),
            contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = topPadding, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalItemSpacing = 4.dp,
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(posts, key = { _, post -> "${post.siteId}:${post.id}" }) { index, post ->
                PostGridItem(
                    post = post,
                    onClick = { onPostClick(posts, index) },
                    onLongClick = { onRemove(post) },
                    bottomLabel = post.platform.displayName,
                )
            }
        }

        AnimatedVisibility(
            visible = showBackToTop,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            BackToTopButton(
                onClick = { coroutineScope.launch { gridState.animateScrollToItem(0) } },
            )
        }
    }
}

@Composable
private fun EmptyHistory(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .glassCard()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        )
    }
}
