package com.azusachino.latte.ui.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import com.azusachino.latte.ui.common.LatteIcons
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.ui.graphics.painter.ColorPainter
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import com.azusachino.latte.data.download.DownloadIdentity
import com.azusachino.latte.data.download.DownloadManager
import com.azusachino.latte.data.model.PoolSummary
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.model.forPage
import com.azusachino.latte.plugin.PluginFeedKind
import com.azusachino.latte.plugin.PluginFeedPeriod
import com.azusachino.latte.plugin.PlatformCapability
import com.azusachino.latte.plugin.PlatformId
import com.azusachino.latte.plugin.SitePlugin
import com.azusachino.latte.ui.common.ToastManager
import com.azusachino.latte.ui.common.ToastType
import com.azusachino.latte.ui.theme.GlassDefaults
import com.azusachino.latte.ui.theme.glassBackdrop
import com.azusachino.latte.ui.theme.glassPillSelection
import com.azusachino.latte.ui.theme.glassSurface
import com.azusachino.latte.ui.theme.pressScale
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedGrid(
    feed: FeedState,
    viewModel: ExploreViewModel,
    gridKey: ExploreGridKey,
    gridState: LazyStaggeredGridState,
    columnCount: Int,
    onPostClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onRequireLogin: (() -> Unit)? = null,
    selectedPostIds: Set<Long> = emptySet(),
    onToggleSelect: ((Long) -> Unit)? = null,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
    header: (@Composable () -> Unit)? = null,
    emptyTitle: String = "No posts yet",
    emptyHint: String = "Pull to refresh or try again later.",
    modifier: Modifier = Modifier,
) {
    // The header is grid item 0 while shown, so post indices are offset by one.
    val headerCount = if (header != null) 1 else 0
    val latestHeaderCount by rememberUpdatedState(headerCount)

    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = gridState.layoutInfo.totalItemsCount
            val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: 0
            totalItems > 0 && lastVisibleIndex >= totalItems - 10
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }

    LaunchedEffect(feed.posts.size, feed.hasMore, feed.nextCursor, feed.isLoading, feed.isLoadingMore) {
        if (
            feed.posts.isEmpty() &&
            feed.hasMore &&
            feed.nextCursor != null &&
            feed.error == null &&
            feed.notice == null &&
            !feed.isLoading &&
            !feed.isLoadingMore
        ) {
            onLoadMore()
        }
    }

    fun currentGridPosition(): GridPosition {
        val postIndex = gridState.firstVisibleItemIndex - latestHeaderCount
        return GridPosition(
            index = postIndex.coerceAtLeast(0),
            scrollOffset = gridState.firstVisibleItemScrollOffset,
            anchorPostId = feed.posts.getOrNull(postIndex)?.id,
        )
    }

    val pullToRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = feed.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
        state = pullToRefreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = feed.isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = topPadding),
            )
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (feed.posts.isEmpty()) {
                // The header and state copy sit below the bar, so Popular period
                // controls stay usable on an empty, offline or signed-out day.
                Column(modifier = Modifier.fillMaxSize().padding(top = topPadding)) {
                    header?.invoke()
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            feed.authRequired -> {
                                // Signed-out state, NOT a failure: render with the same neutral
                                // styling as PersonalFeedContent so every tab looks identical.
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.FavoriteBorder,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = feed.error?.title ?: "Sign in to continue",
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        if (onRequireLogin != null) {
                                            Button(onClick = onRequireLogin, modifier = Modifier.pressScale()) {
                                                Text("Sign in")
                                            }
                                        } else {
                                            IconButton(onClick = onRetry, modifier = Modifier.pressScale()) {
                                                Icon(Icons.Default.Refresh, contentDescription = "Retry")
                                            }
                                        }
                                    }
                                }
                            }
                            feed.notice != null -> {
                                // Informational (non-error) state, e.g. Safe Mode filtered everything.
                                // Neutral styling on purpose: it is not a failure.
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = feed.notice,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                            }
                            feed.error != null -> {
                                FeedErrorState(
                                    error = feed.error,
                                    onRetry = onRetry,
                                    topPadding = 0.dp,
                                )
                            }
                            !feed.isLoading && !feed.isLoadingMore -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = emptyTitle,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center,
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = emptyHint,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        FilledTonalButton(onClick = onRetry, modifier = Modifier.pressScale()) {
                                            Text("Refresh")
                                        }
                                    }
                                }
                            }
                            else -> Box(modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            } else {
                LaunchedEffect(gridKey) {
                    val position = viewModel.gridPosition(gridKey)
                    val restoredIndex = position.anchorPostId
                        ?.let { anchorId -> feed.posts.indexOfFirst { it.id == anchorId } }
                        ?.takeIf { it >= 0 }
                        ?: position.index
                    // The staggered grid cannot honor scrollToItem until its first
                    // layout has measured the current children.
                    withFrameNanos { }
                    gridState.scrollToItem(
                        restoredIndex.coerceIn(0, feed.posts.lastIndex) + headerCount,
                        position.scrollOffset,
                    )
                    snapshotFlow { currentGridPosition() }
                        .distinctUntilChanged()
                        .collect { viewModel.saveGridPosition(gridKey, it) }
                }
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(columnCount),
                    state = gridState,
                    contentPadding = PaddingValues(
                        start = 4.dp,
                        end = 4.dp,
                        top = topPadding + 4.dp,
                        bottom = 4.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalItemSpacing = 4.dp,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (header != null) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            header()
                        }
                    }

                    itemsIndexed(
                        items = feed.posts,
                        key = { _, post -> post.id },
                    ) { index, post ->
                        val isSelectionMode = selectedPostIds.isNotEmpty()
                        val isSelected = post.id in selectedPostIds
                        PostGridItem(
                            post = post,
                            modifier = Modifier.animateItem(),
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onClick = {
                                if (isSelectionMode) {
                                    onToggleSelect?.invoke(post.id)
                                } else {
                                    viewModel.saveGridPosition(gridKey, currentGridPosition())
                                    onPostClick(index)
                                }
                            },
                            onLongClick = {
                                onToggleSelect?.invoke(post.id)
                            },
                        )
                    }

                    if (feed.isLoadingMore) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }

            if ((feed.isLoading || (feed.isLoadingMore && feed.posts.isEmpty())) && !feed.isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = topPadding)
                        .height(3.dp)
                        .align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                )
            }

            val coroutineScope = rememberCoroutineScope()
            val showBackToTop by remember {
                derivedStateOf { shouldShowBackToTop(gridState.firstVisibleItemIndex) }
            }
            AnimatedVisibility(
                visible = showBackToTop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                SmallFloatingActionButton(
                    onClick = { coroutineScope.launch { gridState.animateScrollToItem(0) } },
                    modifier = Modifier.pressScale(),
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Back to top")
                }
            }

            // Transparent feed-page cursor: which fetched page the viewport is
            // in. Per-page count is the loaded average, exact while pages are
            // full. Hidden on an empty feed; bottom-start keeps it clear of the
            // back-to-top FAB.
            val feedPageCursor by remember(feed.posts.size, feed.page) {
                derivedStateOf {
                    val perPage = (feed.posts.size / feed.page.coerceAtLeast(1)).coerceAtLeast(1)
                    gridState.firstVisibleItemIndex / perPage + 1
                }
            }
            AnimatedVisibility(
                visible = feed.posts.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 16.dp),
            ) {
                Text(
                    text = "Page $feedPageCursor",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.Black.copy(alpha = 0.32f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

// Shows the button once the feed is well past the first screenful of posts.
internal const val BACK_TO_TOP_THRESHOLD_ITEMS = 12

internal fun shouldShowBackToTop(firstVisibleIndex: Int): Boolean =
    firstVisibleIndex >= BACK_TO_TOP_THRESHOLD_ITEMS

@Composable
internal fun PersonalFeedContent(
    viewModel: ExploreViewModel,
    plugin: SitePlugin?,
    feed: FeedState,
    label: String,
    emptyTitle: String,
    emptyHint: String,
    gridKey: ExploreGridKey,
    gridState: LazyStaggeredGridState,
    columnCount: Int,
    onPostClick: (Int) -> Unit,
    onRequireLogin: (SitePlugin) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    selectedPostIds: Set<Long> = emptySet(),
    onToggleSelect: ((Long) -> Unit)? = null,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    if (plugin == null) return

    val isLoggedIn by plugin.isLoggedInFlow.collectAsStateWithLifecycle(initialValue = plugin.isLoggedIn)
    val username = plugin.getDisplayUsername()
    val needsUsername = PlatformCapability.USER_FEED !in plugin.capabilities

    if (!isLoggedIn || (needsUsername && username.isNullOrBlank())) {
        Box(
            modifier = modifier.fillMaxSize().padding(top = topPadding, start = 24.dp, end = 24.dp, bottom = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { onRequireLogin(plugin) }, modifier = Modifier.pressScale()) {
                    Text("Sign in")
                }
            }
        }
        return
    }

    LaunchedEffect(isLoggedIn, username, feed.posts.isEmpty(), feed.hasMore, feed.isLoading, feed.error, feed.authRequired) {
        val shouldRetryStaleAuth = feed.authRequired && isLoggedIn
        if (
            isLoggedIn &&
            (!needsUsername || !username.isNullOrBlank()) &&
            feed.posts.isEmpty() &&
            feed.hasMore &&
            !feed.isLoading &&
            !feed.isRefreshing &&
            (feed.error == null || shouldRetryStaleAuth)
        ) {
            onRetry()
        }
    }

    FeedGrid(
        feed = feed,
        viewModel = viewModel,
        gridKey = gridKey,
        gridState = gridState,
        columnCount = columnCount,
        topPadding = topPadding,
        onPostClick = onPostClick,
        onLoadMore = onLoadMore,
        onRetry = onRetry,
        onRefresh = onRefresh,
        emptyTitle = emptyTitle,
        emptyHint = emptyHint,
        onRequireLogin = { onRequireLogin(plugin) },
        selectedPostIds = selectedPostIds,
        onToggleSelect = onToggleSelect,
        modifier = modifier,
    )
}

@Composable
internal fun FeedErrorState(
    error: FeedError,
    onRetry: () -> Unit,
    topPadding: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topPadding, start = 24.dp, end = 24.dp, bottom = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = error.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            error.hint?.let { hint ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(onClick = onRetry, modifier = Modifier.pressScale()) {
                Text("Try again")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PostGridItem(
    post: Post,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    bottomLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    val imageSources = remember(post.siteId, post.id, post.pageIndex) { post.imageSources }
    var imageSourceIndex by rememberSaveable(post.siteId, post.id, post.pageIndex) { mutableStateOf(0) }
    var imageRetryCount by rememberSaveable(post.siteId, post.id, post.pageIndex) { mutableStateOf(0) }
    var imageFailed by rememberSaveable(post.siteId, post.id, post.pageIndex) { mutableStateOf(false) }
    val safeImageSourceIndex = imageSourceIndex.coerceIn(0, (imageSources.size - 1).coerceAtLeast(0))
    val imageUrl = imageSources.getOrNull(safeImageSourceIndex)
    val context = LocalContext.current
    val imageRequest = if (imageUrl == null) {
        null
    } else {
        remember(imageUrl, imageRetryCount) {
            ImageRequest.Builder(context)
                .data(imageUrl)
                .build()
        }
    }
    val imageModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(post.aspectRatio.coerceIn(0.4f, 2.5f))

    val cardShape = RoundedCornerShape(GlassDefaults.CardCornerRadius)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val rimBrush = if (isDark) GlassDefaults.DarkRimGradient else GlassDefaults.LightRimGradient
    val borderStroke = if (isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(GlassDefaults.RimStrokeWidth, rimBrush)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(GlassDefaults.PressScale)
            .clip(cardShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = cardShape,
        border = borderStroke,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box(modifier = imageModifier) {
            if (imageRequest == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Image unavailable",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // AsyncImage, not SubcomposeAsyncImage: per-item subcomposition
                // is a known flicker source in lazy grids, and the flat
                // placeholder avoids spinner churn while flinging.
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    onSuccess = { imageFailed = false },
                    onError = {
                        if (safeImageSourceIndex < imageSources.lastIndex) imageSourceIndex = safeImageSourceIndex + 1
                        else imageFailed = true
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (imageFailed) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Image unavailable",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = {
                                    imageFailed = false
                                    imageSourceIndex = 0
                                    imageRetryCount++
                                },
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }

            if (isSelectionMode) {
                val badgeScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.05f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = 0.7f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "selection_badge_scale",
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(26.dp)
                        .graphicsLayer {
                            scaleX = badgeScale
                            scaleY = badgeScale
                        }
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.35f),
                        )
                        .border(
                            1.5.dp,
                            if (isSelected) Color.White.copy(alpha = 0.9f) else Color.White,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isSelected,
                        enter = fadeIn() + scaleIn(spring(dampingRatio = 0.7f)),
                        exit = fadeOut() + scaleOut(spring(dampingRatio = 0.7f)),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            if (bottomLabel != null) {
                Text(
                    text = bottomLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .glassPillSelection(selected = false, shape = RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

