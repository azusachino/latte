package com.azusachino.latte.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azusachino.latte.ui.theme.GlassDefaults
import com.azusachino.latte.ui.theme.glassSurface
import com.azusachino.latte.ui.theme.pressScale
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
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
    modifier: Modifier = Modifier,
) {
    val detailPosts = posts
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (detailPosts.size - 1).coerceAtLeast(0)),
        pageCount = { detailPosts.size },
    )

    LaunchedEffect(pagerState.currentPage, detailPosts.size) {
        if (detailPosts.isNotEmpty() && pagerState.currentPage >= detailPosts.size - 5) {
            onLoadMore()
        }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showControls by remember { mutableStateOf(true) }
    var showInspectSheet by remember { mutableStateOf(false) }
    var localScores by remember { mutableStateOf(mapOf<Long, Int>()) }
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
    // recover a favorite (score 3) set in a prior session or on the web.
    LaunchedEffect(currentPost?.id, currentPlugin?.isLoggedIn) {
        val post = currentPost
        if (post != null && PlatformCapability.USER_FEED !in post.platform.capabilities && currentPlugin != null && currentPlugin.isLoggedIn && currentPlugin.getScore(post.id) == null) {
            currentPlugin.refreshScore(post.id)?.let { refreshed ->
                localScores = localScores + (post.id to refreshed)
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
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val parentPost = detailPosts[page]
            val post = remember(parentPost, page, pagerState.currentPage, pixivPageIndex) {
                if (page == pagerState.currentPage) {
                    parentPost.forPage(pixivPageIndex)
                } else {
                    parentPost.forPage(0)
                }
            }
            DetailImageView(
                targetPost = post,
                onTap = { xFraction, zoomed ->
                    // Edge taps page through multi-page works; everything else toggles controls.
                    val edgeLeft = xFraction < 0.25f
                    val edgeRight = xFraction > 0.75f
                    val canPage = isMultiPagePost && !zoomed && (edgeLeft || edgeRight)
                    val target = if (edgeLeft) pixivPageIndex - 1 else pixivPageIndex + 1
                    if (canPage && target in 0 until (currentPost?.pageCount ?: 0)) {
                        pixivPageIndex = target
                    } else {
                        showControls = !showControls
                    }
                },
            )
        }

        // Top Controls Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Left pill: Circular frosted glass back button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .glassSurface(shape = CircleShape, dark = true)
                        .pressScale(),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                }

                // Center capsule: Floating frosted glass pill
                val centerTitle = remember(currentPost?.id, currentPost?.title, currentPost?.platform) {
                    when {
                        currentPost == null -> ""
                        !currentPost.title.isNullOrBlank() -> currentPost.title.orEmpty()
                        else -> "#${currentPost.id} · ${currentPost.platform.displayName}"
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (centerTitle.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .glassSurface(shape = RoundedCornerShape(GlassDefaults.PillCornerRadius), dark = true)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = centerTitle,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // Right pill: Circular frosted glass action buttons for Browser and Share
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (currentPost != null) {
                        // Open in browser
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .glassSurface(shape = CircleShape, dark = true)
                                .pressScale(),
                            contentAlignment = Alignment.Center,
                        ) {
                            IconButton(
                                onClick = {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(
                                            currentPost.canonicalUrl
                                                ?: currentPost.source
                                                ?: "https://yande.re/post/show/${currentPost.id}",
                                        ),
                                    )
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Icon(
                                    imageVector = LatteIcons.OpenInBrowser,
                                    contentDescription = "Open in browser",
                                    tint = Color.White,
                                )
                            }
                        }

                        // Share
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .glassSurface(shape = CircleShape, dark = true)
                                .pressScale(),
                            contentAlignment = Alignment.Center,
                        ) {
                            IconButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            currentPost.canonicalUrl
                                                ?: currentPost.source
                                                ?: "https://yande.re/post/show/${currentPost.id}",
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share post"))
                                },
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = Color.White,
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.size(44.dp))
                    }
                }
            }
        }

        // Bottom Controls Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                inlineActionError?.let { message ->
                    Box(
                        modifier = Modifier
                            .padding(bottom = 10.dp)
                            .glassSurface(shape = RoundedCornerShape(12.dp), dark = true)
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
                            .glassSurface(shape = RoundedCornerShape(GlassDefaults.PillCornerRadius), dark = true)
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassSurface(shape = RoundedCornerShape(28.dp), dark = true)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Details button: elegant pill with icon & resolution
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color.White.copy(alpha = 0.12f))
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
                            val isPluginLoggedIn by currentPlugin.isLoggedInFlow.collectAsState(initial = currentPlugin.isLoggedIn)
                            val post = displayPost
                            val currentScore = localScores[post.id] ?: currentPlugin.getScore(post.id) ?: 0
                            val hasUserFeedCapability = PlatformCapability.USER_FEED in post.platform.capabilities
                            val isFavorited = if (hasUserFeedCapability) {
                                localBookmarks[post.workIdentity.toString()] ?: post.isBookmarked
                            } else {
                                currentScore == 3
                            }

                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isFavorited) Color(0xFFE53935).copy(alpha = 0.25f)
                                        else Color.White.copy(alpha = 0.12f),
                                    )
                                    .pressScale(),
                                contentAlignment = Alignment.Center,
                            ) {
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
                        Button(
                            onClick = {
                                if (displayPost != null) {
                                    scope.launch {
                                        when (val result = downloadManager.enqueueDownload(displayPost)) {
                                            is DownloadResult.AlreadySaved -> {
                                                ToastManager.showWarning("Already saved: ${result.displayName}")
                                            }
                                            is DownloadResult.AlreadyRunning -> {
                                                ToastManager.showWarning("Download already running")
                                            }
                                            is DownloadResult.Started -> {
                                                inlineActionError = null
                                                ToastManager.showSuccess("Download started: ${result.displayName}")
                                            }
                                            is DownloadResult.Failed -> {
                                                inlineActionError = "Failed to save image: ${result.message}"
                                            }
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .height(44.dp)
                                .pressScale(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        ) {
                            Icon(
                                imageVector = LatteIcons.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Save",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            )
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
                dragHandle = null,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassSurface(
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
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
                            val isPluginLoggedIn by currentPlugin.isLoggedInFlow.collectAsState(initial = currentPlugin.isLoggedIn)
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
                                            onToggleFavoriteTag?.invoke(tag, currentPost.platform)
                                            if (wasFavorite) {
                                                ToastManager.show(
                                                    message = "Removed favorite tag \"$tag\"",
                                                    type = ToastType.INFO,
                                                    actionLabel = "Undo",
                                                ) {
                                                    onToggleFavoriteTag?.invoke(tag, currentPost.platform)
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

@Composable
private fun DetailImageView(
    targetPost: Post,
    onTap: (xFraction: Float, zoomed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    ZoomableBox(
        modifier = modifier.fillMaxSize(),
        resetKey = Triple(targetPost.siteId, targetPost.id, targetPost.pageIndex),
        onTap = onTap,
    ) {
        val imageSources = remember(targetPost.siteId, targetPost.id, targetPost.pageIndex) {
            buildList {
                add(targetPost.sampleUrl)
                add(targetPost.previewUrl)
                addAll(targetPost.imageSources)
            }.filter(String::isNotBlank).distinct()
        }
        if (imageSources.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Image unavailable", color = Color.White)
            }
        } else {
            var imageSourceIndex by rememberSaveable(
                targetPost.siteId,
                targetPost.id,
                targetPost.pageIndex,
            ) { mutableStateOf(0) }
            var imageRetryCount by rememberSaveable(
                targetPost.siteId,
                targetPost.id,
                targetPost.pageIndex,
            ) { mutableStateOf(0) }
            val safeImageSourceIndex = imageSourceIndex.coerceIn(imageSources.indices)
            val currentImageUrl = imageSources[safeImageSourceIndex]
            val hasPreview = targetPost.previewUrl.isNotBlank() && targetPost.previewUrl != currentImageUrl

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                // If a low-res preview is available, display it immediately so the screen never blanks black
                if (hasPreview) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(targetPost.previewUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                val imageRequest = remember(currentImageUrl, imageRetryCount) {
                    ImageRequest.Builder(context)
                        .data(currentImageUrl)
                        .crossfade(200)
                        .build()
                }

                SubcomposeAsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    loading = {
                        // Subtle, small loading indicator (24dp, 2dp stroke, semi-transparent)
                        // Never an unsized huge spinner covering the screen
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = Color.White.copy(alpha = 0.5f),
                            )
                        }
                    },
                    error = {
                        if (safeImageSourceIndex < imageSources.lastIndex) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White.copy(alpha = 0.5f),
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Image unavailable", color = Color.White.copy(alpha = 0.8f))
                                    TextButton(
                                        onClick = {
                                            imageSourceIndex = 0
                                            imageRetryCount++
                                        },
                                    ) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                    },
                    onError = {
                        if (safeImageSourceIndex < imageSources.lastIndex) {
                            imageSourceIndex = safeImageSourceIndex + 1
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun MetadataTable(
    post: Post,
    onAuthorClick: (String) -> Unit,
    onSourceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(
                shape = RoundedCornerShape(GlassDefaults.CardCornerRadius),
                isElevated = true,
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
                    // Author
            if (!post.title.isNullOrBlank()) {
                MetadataTableRow(
                    label = "Title",
                    value = post.title,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            }

            if (!post.author.isNullOrBlank()) {
                MetadataTableRow(
                    label = "Author",
                    value = post.author,
                    isClickable = true,
                    onClick = { onAuthorClick(post.author) },
                    valueColor = MaterialTheme.colorScheme.primary,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            }

            // Dimensions
            val ratio = calculateAspectRatio(post.width, post.height)
            val ratioSuffix = if (ratio.isNotBlank()) " ($ratio)" else ""
            MetadataTableRow(
                label = "Dimensions",
                value = "${post.width} × ${post.height}$ratioSuffix",
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            // Rating
            val (ratingLabel, ratingColor) = when (post.rating) {
                PostRating.SAFE -> "Safe" to Color(0xFF4CAF50)
                PostRating.QUESTIONABLE -> "Questionable" to Color(0xFFFFB300)
                PostRating.EXPLICIT -> "Explicit" to Color(0xFFE53935)
                PostRating.UNKNOWN -> "Unknown" to MaterialTheme.colorScheme.onSurfaceVariant
            }
            MetadataTableRow(
                label = "Rating",
                value = ratingLabel,
                valueColor = ratingColor,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            if (PlatformCapability.USER_FEED in post.platform.capabilities) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                MetadataTableRow(label = "Pages", value = "${post.pageIndex + 1} / ${post.pageCount}")
                post.bookmarkCount?.let { count ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    MetadataTableRow(label = "Bookmarks", value = count.toString())
                }
            } else {
                // Score
                MetadataTableRow(
                    label = "Score",
                    value = "${post.score}",
                )
            }

            // Created Date
            if (post.createdAt != null && post.createdAt > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                val formattedDate = Instant.ofEpochSecond(post.createdAt)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                MetadataTableRow(
                    label = "Date",
                    value = formattedDate,
                )
            }

            // File Size
            val bestVariant = post.bestVariant
            if (bestVariant.fileSize != null && bestVariant.fileSize > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                val ext = bestVariant.extension?.uppercase() ?: "IMAGE"
                MetadataTableRow(
                    label = "File",
                    value = "$ext • ${formatFileSize(bestVariant.fileSize)}",
                )
            }

            // Source
            if (!post.source.isNullOrBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                val host = runCatching { Uri.parse(post.source).host?.removePrefix("www.") }.getOrNull()
                val displaySource = if (!host.isNullOrBlank()) host else "View Source"
                MetadataTableRow(
                    label = "Source",
                    value = displaySource,
                    isClickable = true,
                    onClick = { onSourceClick(post.source) },
                    valueColor = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun MetadataTableRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isClickable: Boolean = false,
    onClick: () -> Unit = {},
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isClickable) Modifier.pressScale().clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(108.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private val TagPalette = listOf(
    Color(0xFFE57373), // Red / Coral
    Color(0xFFF06292), // Pink
    Color(0xFFBA68C8), // Purple
    Color(0xFF9575CD), // Deep Purple
    Color(0xFF7986CB), // Indigo
    Color(0xFF64B5F6), // Blue
    Color(0xFF4FC3F7), // Light Blue
    Color(0xFF4DD0E1), // Cyan
    Color(0xFF4DB6AC), // Teal
    Color(0xFF81C784), // Green
    Color(0xFFAED581), // Light Green
    Color(0xFFFFD54F), // Amber
    Color(0xFFFFB74D), // Orange
    Color(0xFFFF8A65), // Deep Orange
    Color(0xFFA1887F), // Brown
    Color(0xFF90A4AE), // Blue Grey
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagChip(
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val baseColor = TagPalette[abs(tag.hashCode()) % TagPalette.size]
    val containerColor = if (isFavorite) {
        baseColor.copy(alpha = 0.28f)
    } else {
        baseColor.copy(alpha = 0.14f)
    }
    val rimColor = if (isFavorite) {
        baseColor.copy(alpha = 0.90f)
    } else {
        baseColor.copy(alpha = 0.45f)
    }
    val shape = RoundedCornerShape(GlassDefaults.PillCornerRadius)

    Box(
        modifier = modifier
            .pressScale()
            .clip(shape)
            .background(containerColor, shape)
            .border(GlassDefaults.RimStrokeWidth, rimColor, shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(baseColor),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = tag,
                color = baseColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            if (isFavorite) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Favorited tag",
                    tint = baseColor,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

private fun calculateAspectRatio(width: Int, height: Int): String {
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

private fun formatFileSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return ""
    val units = arrayOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }
    return String.format(java.util.Locale.US, "%.1f %s", size, units[unitIndex])
}

@Composable
private fun PersonalRatingSection(
    score: Int,
    isLoggedIn: Boolean,
    onRate: (Int) -> Unit,
    onLoginRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(
                shape = RoundedCornerShape(GlassDefaults.CardCornerRadius),
                isElevated = true,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Personal Rating",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = when (score) {
                        1 -> "★ Good (1 star)"
                        2 -> "★★ Great (2 stars)"
                        3 -> "★★★ Favorite (3 stars)"
                        else -> if (isLoggedIn) "Tap stars to rate" else "Sign in to rate"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (score > 0) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurface,
                )
            }

            // Interactive 3-star row
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..3).forEach { starIndex ->
                    IconButton(
                        onClick = {
                            if (!isLoggedIn) {
                                onLoginRequest()
                            } else {
                                val targetScore = if (score == starIndex) 0 else starIndex
                                onRate(targetScore)
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .pressScale(),
                    ) {
                        Icon(
                            imageVector = if (starIndex <= score) Icons.Default.Star else LatteIcons.StarBorder,
                            contentDescription = "Rate $starIndex star",
                            tint = if (starIndex <= score) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
        }
    }
}
