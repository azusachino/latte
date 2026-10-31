package com.azusachino.latte.ui.detail

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import com.azusachino.latte.ui.common.LatteIcons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.azusachino.latte.ui.theme.GlassDefaults
import com.azusachino.latte.ui.theme.glassBackdrop
import com.azusachino.latte.ui.theme.glassSurface
import com.azusachino.latte.ui.theme.pressScale
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.azusachino.latte.data.download.DownloadIdentity
import com.azusachino.latte.data.download.DownloadManager
import com.azusachino.latte.data.download.DownloadResult
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.model.FavoriteTag
import com.azusachino.latte.data.model.PostRating
import com.azusachino.latte.data.model.forPage
import com.azusachino.latte.plugin.PlatformCapability
import com.azusachino.latte.plugin.PlatformId
import com.azusachino.latte.plugin.SitePlugin
import com.azusachino.latte.plugin.SitePluginManager
import com.azusachino.latte.ui.common.ToastManager
import com.azusachino.latte.ui.common.ToastType
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    posts: List<Post>,
    initialIndex: Int,
    initialPageIndex: Int = 0,
    downloadManager: DownloadManager,
    onBack: () -> Unit,
    onLoadMore: () -> Unit = {},
    onTagClick: (tag: String, postIndex: Int, pageIndex: Int) -> Unit = { _, _, _ -> },
    onAuthorClick: (post: Post, postIndex: Int, pageIndex: Int) -> Unit = { _, _, _ -> },
    pluginManager: SitePluginManager,
    onRequireLogin: (SitePlugin) -> Unit = {},
    favoriteTags: List<FavoriteTag> = emptyList(),
    onToggleFavoriteTag: ((tag: String, platform: PlatformId) -> Unit)? = null,
    onPostViewed: (Post) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val detailPosts = posts
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (detailPosts.size - 1).coerceAtLeast(0)),
        pageCount = { detailPosts.size },
    )

    LaunchedEffect(pagerState.currentPage, detailPosts.size) {
        if (shouldLoadMoreDetail(pagerState.currentPage, detailPosts.size)) {
            onLoadMore()
        }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // The viewer is always black, so bar icons stay light whatever the app theme is.
    val view = LocalView.current
    DisposableEffect(view) {
        val controller = WindowCompat.getInsetsController((view.context as Activity).window, view)
        val wasLightStatusBars = controller.isAppearanceLightStatusBars
        val wasLightNavigationBars = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = wasLightStatusBars
            controller.isAppearanceLightNavigationBars = wasLightNavigationBars
        }
    }

    val hazeState = rememberHazeState()
    var showControls by remember { mutableStateOf(true) }
    // Pushes the toast pill below the top controls row (10dp + 44dp + 8dp) while it is shown.
    val toastInsetOwner = remember { Any() }
    DisposableEffect(showControls) {
        ToastManager.setTopInset(toastInsetOwner, if (showControls) 62.dp else 0.dp)
        onDispose { ToastManager.clearTopInset(toastInsetOwner) }
    }
    var showInspectSheet by remember { mutableStateOf(false) }
    var localScores by remember { mutableStateOf(mapOf<Long, Int>()) }
    // Server-confirmed favorite state per post (Moebooru probe). Kept apart
    // from localScores: the probe's "not favorited" must not blank out the
    // 1/2 star rating the user actually voted.
    var favoriteOverrides by remember { mutableStateOf(mapOf<Long, Boolean>()) }
    var localBookmarks by remember { mutableStateOf(mapOf<String, Boolean>()) }
    var inlineActionError by remember { mutableStateOf<String?>(null) }
    val heartScale = remember { Animatable(1f) }
    val currentPost = detailPosts.getOrNull(pagerState.currentPage)
    var pixivPageIndex by rememberSaveable(currentPost?.siteId, currentPost?.id) {
        mutableStateOf(if (pagerState.currentPage == initialIndex) initialPageIndex.coerceAtLeast(0) else 0)
    }
    val displayPost = currentPost?.forPage(pixivPageIndex)
    val currentPlugin = currentPost?.let { pluginManager.get(it.platform) }
    val isMultiPagePost = currentPost != null &&
        PlatformCapability.USER_FEED in currentPost.platform.capabilities &&
        currentPost.pageCount > 1

    val pageIndicatorText = remember(
        pagerState.currentPage,
        detailPosts.size,
        isMultiPagePost,
        pixivPageIndex,
        currentPost?.pageCount,
    ) {
        detailPageIndicatorText(
            currentPage = pagerState.currentPage,
            totalPosts = detailPosts.size,
            isMultiPagePost = isMultiPagePost,
            pixivPageIndex = pixivPageIndex,
            postPageCount = currentPost?.pageCount ?: 1,
        )
    }

    var showTransientPageIndicator by remember { mutableStateOf(false) }
    var isCurrentPageZoomed by remember(pagerState.currentPage, pixivPageIndex) { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage, pixivPageIndex) {
        if (!showControls) {
            showTransientPageIndicator = true
            delay(1500)
            showTransientPageIndicator = false
        }
    }

    LaunchedEffect(showControls) {
        if (showControls) {
            showTransientPageIndicator = false
        }
    }

    LaunchedEffect(currentPost?.siteId, currentPost?.id) {
        currentPost?.let(onPostViewed)
    }

    LaunchedEffect(currentPost?.siteId, currentPost?.id) {
        inlineActionError = null
    }

    LaunchedEffect(pixivPageIndex, currentPost?.siteId, currentPost?.id) {
        if (currentPost != null && isMultiPagePost && pixivPageIndex + 1 < currentPost.pageCount) {
            context.imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(currentPost.forPage(pixivPageIndex + 1).sampleUrl)
                    .build(),
            )
        }
    }

    // The site never tells the app "you already scored this post" up front --
    // recover the favorite state on every open so web-side changes (added or
    // removed favorites) are not masked by this session's cached score.
    LaunchedEffect(currentPost?.id, currentPlugin?.isLoggedIn) {
        val post = currentPost
        if (post != null && PlatformCapability.USER_FEED !in post.platform.capabilities && currentPlugin != null && currentPlugin.isLoggedIn) {
            currentPlugin.refreshScore(post.id)?.let { refreshed ->
                favoriteOverrides = favoriteOverrides + (post.id to (refreshed == 3))
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Horizontal Pager for browsing posts
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isCurrentPageZoomed,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
        ) { page ->
            val parentPost = detailPosts[page]
            val post = remember(parentPost, page, pagerState.currentPage, pixivPageIndex) {
                if (page == pagerState.currentPage) {
                    parentPost.forPage(pixivPageIndex)
                } else {
                    parentPost.forPage(0)
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                // Behind the zoomable image: gives the glass chrome something to blur.
                AmbientBackdrop(previewUrl = post.previewUrl)
                DetailImageView(
                    targetPost = post,
                    onZoomChanged = { zoomed ->
                        if (page == pagerState.currentPage) {
                            isCurrentPageZoomed = zoomed
                        }
                    },
                    onTap = { xFraction, zoomed ->
                        when (detailTapAction(xFraction, zoomed, isMultiPagePost, pixivPageIndex, currentPost?.pageCount ?: 0, showControls)) {
                            DetailTapAction.PreviousPage -> pixivPageIndex -= 1
                            DetailTapAction.NextPage -> pixivPageIndex += 1
                            DetailTapAction.ToggleControls -> showControls = !showControls
                        }
                    },
                )
            }
        }

        // Top Controls Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                initialOffsetY = { -it },
            ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            exit = slideOutVertically(
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                targetOffsetY = { -it },
            ) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            DetailTopBar(
                currentPost = currentPost,
                postUrl = currentPost?.let { currentPlugin?.postUrl(it) ?: it.canonicalUrl ?: it.source },
                pageIndicatorText = pageIndicatorText,
                hazeState = hazeState,
                onBack = onBack,
                onDetails = { showInspectSheet = true },
            )
        }

        // Transient Page Indicator when controls are hidden
        TransientDetailPageIndicator(
            text = pageIndicatorText,
            visible = !showControls && showTransientPageIndicator,
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // Bottom Controls Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                initialOffsetY = { it },
            ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            exit = slideOutVertically(
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                targetOffsetY = { it },
            ) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures { }
                    }
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                inlineActionError?.let { message ->
                    Box(
                        modifier = Modifier
                            .padding(bottom = 10.dp)
                            .glassBackdrop(hazeState, backgroundColor = Color.Black, shape = RoundedCornerShape(12.dp), dark = true)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }

                if (currentPost?.platform?.let { PlatformCapability.USER_FEED in it.capabilities } == true && currentPost.pageCount > 1) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .glassBackdrop(hazeState, backgroundColor = Color.Black, shape = RoundedCornerShape(GlassDefaults.PillCornerRadius), dark = true)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            IconButton(
                                onClick = {
                                    if (pixivPageIndex > 0) {
                                        pixivPageIndex--
                                    }
                                },
                                enabled = pixivPageIndex > 0,
                                modifier = Modifier
                                    .size(36.dp)
                                    .pressScale(enabled = pixivPageIndex > 0),
                            ) {
                                Icon(
                                    imageVector = LatteIcons.ChevronLeft,
                                    contentDescription = "Previous illustration page",
                                    tint = if (pixivPageIndex > 0) Color.White else Color.White.copy(alpha = 0.35f),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Text(
                                text = "Page ${pixivPageIndex + 1} of ${currentPost.pageCount}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                            IconButton(
                                onClick = {
                                    if (pixivPageIndex < currentPost.pageCount - 1) {
                                        pixivPageIndex++
                                    }
                                },
                                enabled = pixivPageIndex < currentPost.pageCount - 1,
                                modifier = Modifier
                                    .size(36.dp)
                                    .pressScale(enabled = pixivPageIndex < currentPost.pageCount - 1),
                            ) {
                                Icon(
                                    imageVector = LatteIcons.ChevronRight,
                                    contentDescription = "Next illustration page",
                                    tint = if (pixivPageIndex < currentPost.pageCount - 1) Color.White else Color.White.copy(alpha = 0.35f),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }

                if (currentPost != null && isMultiPagePost) {
                    val thumbnailState = rememberLazyListState()
                    LaunchedEffect(pixivPageIndex) {
                        thumbnailState.animateScrollToItem(pixivPageIndex)
                    }
                    LazyRow(
                        state = thumbnailState,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    ) {
                        items(count = currentPost.pageCount) { index ->
                            val selected = index == pixivPageIndex
                            AsyncImage(
                                model = currentPost.forPage(index).previewUrl,
                                contentDescription = "Page ${index + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = 2.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    .clickable { pixivPageIndex = index },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Details pill: same glass as the top pills, icon & resolution
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .glassBackdrop(hazeState, backgroundColor = Color.Black, shape = RoundedCornerShape(22.dp), dark = true)
                            .clickable { showInspectSheet = true }
                            .pressScale()
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Details",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (displayPost != null) "${displayPost.width}×${displayPost.height}" else "Details",
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = Color.White,
                            )
                        }
                    }

                    // Right actions: Favorite + Save
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (
                            currentPlugin != null &&
                            currentPlugin.capabilities.contains(PlatformCapability.FAVORITES) &&
                            displayPost != null
                        ) {
                            val isPluginLoggedIn by currentPlugin.isLoggedInFlow.collectAsStateWithLifecycle(initialValue = currentPlugin.isLoggedIn)
                            val post = displayPost
                            val currentScore = localScores[post.id] ?: currentPlugin.getScore(post.id) ?: 0
                            val hasUserFeedCapability = PlatformCapability.USER_FEED in post.platform.capabilities
                            val isFavorited = if (hasUserFeedCapability) {
                                localBookmarks[post.workIdentity.toString()] ?: post.isBookmarked
                            } else {
                                favoriteOverrides[post.id] ?: (currentScore == 3)
                            }

                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .glassBackdrop(hazeState, backgroundColor = Color.Black, shape = CircleShape, dark = true)
                                    .pressScale(),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isFavorited) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(Color(0xFFE53935).copy(alpha = 0.25f), CircleShape),
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (!isPluginLoggedIn) {
                                            onRequireLogin(currentPlugin)
                                        } else {
                                            scope.launch {
                                                launch {
                                                    heartScale.animateTo(
                                                        targetValue = 1.35f,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessMediumLow,
                                                        ),
                                                    )
                                                    heartScale.animateTo(
                                                        targetValue = 1f,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessMedium,
                                                        ),
                                                    )
                                                }

                                                val targetScore = if (isFavorited) 0 else 3
                                                val targetBookmarked = !isFavorited
                                                val result = if (hasUserFeedCapability) {
                                                    currentPlugin.setBookmark(post.id, targetBookmarked)
                                                } else {
                                                    currentPlugin.setScore(post.id, targetScore)
                                                }
                                                if (result.isSuccess) {
                                                    inlineActionError = null
                                                    if (hasUserFeedCapability) {
                                                        localBookmarks = localBookmarks + (post.workIdentity.toString() to targetBookmarked)
                                                    } else {
                                                        localScores = localScores + (post.id to targetScore)
                                                        favoriteOverrides = favoriteOverrides + (post.id to targetBookmarked)
                                                    }
                                                } else {
                                                    inlineActionError = "Failed to update favorite: ${result.exceptionOrNull()?.message.orEmpty()}"
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    Icon(
                                        imageVector = if (isFavorited) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = if (isFavorited) "Remove from favorites" else "Add to favorites",
                                        tint = if (isFavorited) Color(0xFFE53935) else Color.White,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .graphicsLayer {
                                                scaleX = heartScale.value
                                                scaleY = heartScale.value
                                            },
                                    )
                                }
                            }
                        }

                        // Download button: matching 44dp pill
                        Box(
                            modifier = Modifier
                                .height(44.dp)
                                .glassBackdrop(
                                    hazeState,
                                    backgroundColor = Color.Black,
                                    shape = RoundedCornerShape(22.dp),
                                    dark = true,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                )
                                .pressScale()
                                .clickable {
                                    if (displayPost != null) {
                                        scope.launch {
                                            when (val result = downloadManager.enqueueDownload(displayPost)) {
                                                is DownloadResult.AlreadySaved -> {
                                                    ToastManager.showWarning("Already saved ${DownloadIdentity.toastLabel(displayPost)}")
                                                }
                                                is DownloadResult.AlreadyRunning -> {
                                                    ToastManager.showWarning("Already downloading ${DownloadIdentity.toastLabel(displayPost)}")
                                                }
                                                is DownloadResult.Started -> {
                                                    inlineActionError = null
                                                    ToastManager.showSuccess("Saving ${DownloadIdentity.toastLabel(displayPost)}")
                                                }
                                                is DownloadResult.Failed -> {
                                                    android.util.Log.w("LatteDetail", "save failed for ${displayPost.id}: ${result.message}")
                                                    ToastManager.showError("Couldn't save ${DownloadIdentity.toastLabel(displayPost)}")
                                                }
                                            }
                                        }
                                    }
                                }
                                .padding(horizontal = 22.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = LatteIcons.Download,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Save",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Inspect ModalBottomSheet
        if (showInspectSheet && currentPost != null) {
            ModalBottomSheet(
                onDismissRequest = { showInspectSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                dragHandle = null,
            ) {
                // The sheet is its own window and sets its own bar appearance; keep the status bar icons light.
                val sheetView = LocalView.current
                val sheetWindow = (sheetView.parent as? DialogWindowProvider)?.window
                SideEffect {
                    sheetWindow?.let {
                        WindowCompat.getInsetsController(it, sheetView).isAppearanceLightStatusBars = false
                    }
                }
                val sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, sheetShape)
                        .glassSurface(
                            shape = sheetShape,
                            isElevated = true,
                        ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(top = 12.dp, bottom = 36.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(bottom = 16.dp)
                                .size(width = 36.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                        )

                        Text(
                            text = "Post #${currentPost.id}",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Metadata Table
                        Text(
                            text = "Information",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        MetadataTable(
                            post = displayPost ?: currentPost,
                            onAuthorClick = { author ->
                                showInspectSheet = false
                                onAuthorClick(displayPost ?: currentPost, pagerState.currentPage, pixivPageIndex)
                            },
                            onSourceClick = { url ->
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            },
                        )

                        inlineActionError?.let { message ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        // Personal Rating (0-3 stars)
                        if (currentPlugin != null && currentPlugin.capabilities.contains(PlatformCapability.SCORING)) {
                            val isPluginLoggedIn by currentPlugin.isLoggedInFlow.collectAsStateWithLifecycle(initialValue = currentPlugin.isLoggedIn)
                            val currentScore = localScores[currentPost.id] ?: currentPlugin.getScore(currentPost.id) ?: 0

                            Spacer(modifier = Modifier.height(16.dp))
                            PersonalRatingSection(
                                score = currentScore,
                                isLoggedIn = isPluginLoggedIn,
                                onRate = { newScore ->
                                    scope.launch {
                                        val result = currentPlugin.setScore(currentPost.id, newScore)
                                        if (result.isSuccess) {
                                            inlineActionError = null
                                            localScores = localScores + (currentPost.id to newScore)
                                        } else {
                                            inlineActionError = "Failed to submit rating: ${result.exceptionOrNull()?.message.orEmpty()}"
                                        }
                                    }
                                },
                                onLoginRequest = {
                                    onRequireLogin(currentPlugin)
                                },
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Tags with Colorful Palette
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Tags",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Text(
                                text = "${currentPost.tags.size} tags",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            currentPost.tags.forEach { tag ->
                                val canFavorite = onToggleFavoriteTag != null
                                TagChip(
                                    tag = tag,
                                    onClick = {
                                        showInspectSheet = false
                                        onTagClick(tag, pagerState.currentPage, pixivPageIndex)
                                    },
                                    isFavorite = canFavorite &&
                                        favoriteTags.any { it.tag == tag && it.platform == currentPost.platform },
                                    onLongClick = if (canFavorite) {
                                        {
                                            val wasFavorite = favoriteTags.any {
                                                it.tag == tag && it.platform == currentPost.platform
                                            }
                                            onToggleFavoriteTag(tag, currentPost.platform)
                                            if (wasFavorite) {
                                                ToastManager.show(
                                                    message = "Removed favorite tag \"$tag\"",
                                                    type = ToastType.INFO,
                                                    actionLabel = "Undo",
                                                ) {
                                                    onToggleFavoriteTag(tag, currentPost.platform)
                                                }
                                            }
                                        }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Blurred, darkened copy of the post's preview, filling the page behind the fitted image. It sits
 * outside [ZoomableBox] so zoom and pan never move it, and it has no pointer handlers so taps and
 * pinches fall through to the image.
 */

/** The Detail list asks for more posts once the viewer is within this many posts of the end. */
private const val DETAIL_LOAD_MORE_THRESHOLD = 5

internal fun shouldLoadMoreDetail(currentPage: Int, size: Int): Boolean =
    size > 0 && currentPage >= size - DETAIL_LOAD_MORE_THRESHOLD

internal sealed interface DetailTapAction {
    data object PreviousPage : DetailTapAction
    data object NextPage : DetailTapAction
    data object ToggleControls : DetailTapAction
}

/**
 * Edge taps (outer 25% on each side) page through a multi-page work when controls are hidden and not zoomed.
 * When controls are shown, middle taps, zoomed taps, single-page works and edges past the first or last page toggle controls.
 */

internal fun detailTapAction(
    xFraction: Float,
    zoomed: Boolean,
    isMultiPage: Boolean,
    pageIndex: Int,
    pageCount: Int,
    controlsVisible: Boolean = false,
): DetailTapAction {
    if (controlsVisible) return DetailTapAction.ToggleControls
    val edgeLeft = xFraction < 0.25f
    val edgeRight = xFraction > 0.75f
    if (!isMultiPage || zoomed || !(edgeLeft || edgeRight)) return DetailTapAction.ToggleControls
    val target = if (edgeLeft) pageIndex - 1 else pageIndex + 1
    return when {
        target !in 0 until pageCount -> DetailTapAction.ToggleControls
        edgeLeft -> DetailTapAction.PreviousPage
        else -> DetailTapAction.NextPage
    }
}

internal fun calculateAspectRatio(width: Int, height: Int): String {
    if (width <= 0 || height <= 0) return ""
    val gcd = gcd(width, height)
    val rw = width / gcd
    val rh = height / gcd
    return if (rw <= 21 && rh <= 21) {
        "$rw:$rh"
    } else {
        String.format(java.util.Locale.US, "%.2f:1", width.toFloat() / height.toFloat())
    }
}

private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

