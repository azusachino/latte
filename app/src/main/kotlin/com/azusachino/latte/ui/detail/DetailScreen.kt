package com.azusachino.latte.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import com.azusachino.latte.data.download.DownloadManager
import com.azusachino.latte.data.download.DownloadResult
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.model.PostRating
import com.azusachino.latte.data.model.forPage
import com.azusachino.latte.plugin.PlatformCapability
import com.azusachino.latte.plugin.SitePlugin
import com.azusachino.latte.plugin.SitePluginManager
import com.azusachino.latte.ui.common.ToastManager
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
    downloadManager: DownloadManager,
    onBack: () -> Unit,
    onTagClick: (String) -> Unit = {},
    onAuthorClick: (Post) -> Unit = {},
    pluginManager: SitePluginManager,
    onRequireLogin: (SitePlugin) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val detailPosts = posts
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (detailPosts.size - 1).coerceAtLeast(0)),
        pageCount = { detailPosts.size },
    )
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showControls by remember { mutableStateOf(true) }
    var showInspectSheet by remember { mutableStateOf(false) }
    var localScores by remember { mutableStateOf(mapOf<Long, Int>()) }
    var localBookmarks by remember { mutableStateOf(mapOf<String, Boolean>()) }
    var inlineActionError by remember { mutableStateOf<String?>(null) }
    var pixivPageIndex by remember { mutableStateOf(0) }

    val currentPost = detailPosts.getOrNull(pagerState.currentPage)
    val displayPost = currentPost?.forPage(pixivPageIndex)
    val currentPlugin = currentPost?.let { pluginManager.get(it.siteId) }

    LaunchedEffect(currentPost?.siteId, currentPost?.id) {
        pixivPageIndex = 0
        inlineActionError = null
    }

    // The site never tells the app "you already scored this post" up front --
    // recover a favorite (score 3) set in a prior session or on the web.
    LaunchedEffect(currentPost?.id, currentPlugin?.isLoggedIn) {
        val post = currentPost
        if (post != null && post.siteId != "pixiv" && currentPlugin != null && currentPlugin.isLoggedIn && currentPlugin.getScore(post.id) == null) {
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
        // Horizontal Pager with beyondViewportPageCount = 1 for butter-smooth swiping
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val post = detailPosts[page].forPage(if (page == pagerState.currentPage) pixivPageIndex else 0)
            AnimatedContent(
                targetState = post,
                transitionSpec = {
                    if (initialState.workIdentity == targetState.workIdentity &&
                        initialState.pageIndex != targetState.pageIndex
                    ) {
                        val direction = if (targetState.pageIndex > initialState.pageIndex) 1 else -1
                        (slideInHorizontally(tween(220)) { width -> direction * width / 3 } + fadeIn(tween(220)))
                            .togetherWith(slideOutHorizontally(tween(160)) { width -> -direction * width / 3 } + fadeOut(tween(160)))
                    } else {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                label = "PixivPageTransition",
                modifier = Modifier.fillMaxSize(),
            ) { targetPost ->
                ZoomableBox(
                    modifier = Modifier.fillMaxSize(),
                    onTap = { showControls = !showControls },
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
                        val imageRequest = remember(imageSources[safeImageSourceIndex], imageRetryCount) {
                            ImageRequest.Builder(context)
                                .data(imageSources[safeImageSourceIndex])
                                .build()
                        }
                        SubcomposeAsyncImage(
                            model = imageRequest,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            loading = {
                                if (targetPost.previewUrl.isBlank() ||
                                    imageSources[safeImageSourceIndex] == targetPost.previewUrl
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(color = Color.White)
                                    }
                                } else {
                                    SubcomposeAsyncImage(
                                        model = targetPost.previewUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        loading = {
                                            CircularProgressIndicator(color = Color.White)
                                        },
                                        error = {
                                            CircularProgressIndicator(color = Color.White)
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            },
                            error = {
                                if (safeImageSourceIndex < imageSources.lastIndex) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(color = Color.White)
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Image unavailable", color = Color.White)
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

        // Top Controls Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.65f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }

                    Text(
                        text = if (currentPost != null) currentPost.title ?: "#${currentPost.id}" else "",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f),
                    )

                    if (currentPost != null) {
                        // Open in browser
                        IconButton(onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(currentPost.canonicalUrl ?: currentPost.source ?: "https://yande.re/post/show/${currentPost.id}"),
                            )
                            context.startActivity(intent)
                        }) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = "Open in browser",
                                tint = Color.White,
                            )
                        }

                        // Share
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    currentPost.canonicalUrl ?: currentPost.source ?: "https://yande.re/post/show/${currentPost.id}",
                                )
                            }
                            context.startActivity(Intent.createChooser(intent, "Share post"))
                        }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color.White,
                            )
                        }
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
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.65f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    inlineActionError?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    if (currentPost?.siteId == "pixiv" && currentPost.pageCount > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { pixivPageIndex-- },
                                enabled = pixivPageIndex > 0,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = "Previous illustration page",
                                    tint = if (pixivPageIndex > 0) Color.White else Color.White.copy(alpha = 0.35f),
                                )
                            }
                            Text(
                                text = "Page ${pixivPageIndex + 1} of ${currentPost.pageCount}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            IconButton(
                                onClick = { pixivPageIndex++ },
                                enabled = pixivPageIndex < currentPost.pageCount - 1,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Next illustration page",
                                    tint = if (pixivPageIndex < currentPost.pageCount - 1) Color.White else Color.White.copy(alpha = 0.35f),
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                    // Details button: elegant pill with icon & resolution
                    FilledTonalButton(
                        onClick = { showInspectSheet = true },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.height(48.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.White.copy(alpha = 0.18f),
                            contentColor = Color.White,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (displayPost != null) "${displayPost.width}×${displayPost.height}" else "Details",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                        )
                    }

                    // Right actions: Favorite + Save
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (
                            currentPlugin != null &&
                            currentPlugin.capabilities.contains(PlatformCapability.FAVORITES) &&
                            displayPost != null
                        ) {
                            val isPluginLoggedIn by currentPlugin.isLoggedInFlow.collectAsState(initial = currentPlugin.isLoggedIn)
                            val post = displayPost
                            val currentScore = localScores[post.id] ?: currentPlugin.getScore(post.id) ?: 0
                            val isPixivPost = post.siteId == "pixiv"
                            val isFavorited = if (isPixivPost) {
                                localBookmarks[post.workIdentity.toString()] ?: post.isBookmarked
                            } else {
                                currentScore == 3
                            }

                            FilledTonalIconButton(
                                onClick = {
                                    if (!isPluginLoggedIn) {
                                        onRequireLogin(currentPlugin)
                                    } else {
                                        scope.launch {
                                            val targetScore = if (isFavorited) 0 else 3
                                            val targetBookmarked = !isFavorited
                                            val result = if (isPixivPost) {
                                                currentPlugin.setBookmark(post.id, targetBookmarked)
                                            } else {
                                                currentPlugin.setScore(post.id, targetScore)
                                            }
                                            if (result.isSuccess) {
                                                inlineActionError = null
                                                if (isPixivPost) {
                                                    localBookmarks = localBookmarks + (post.workIdentity.toString() to targetBookmarked)
                                                } else {
                                                    localScores = localScores + (post.id to targetScore)
                                                }
                                                if (targetBookmarked) {
                                                    ToastManager.showSuccess("Added to favorites")
                                                } else {
                                                    ToastManager.showInfo("Removed from favorites")
                                                }
                                            } else {
                                                inlineActionError = "Failed to update favorite: ${result.exceptionOrNull()?.message.orEmpty()}"
                                            }
                                        }
                                    }
                                },
                                shape = CircleShape,
                                modifier = Modifier.size(48.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (isFavorited) Color(0xFFE53935).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.18f),
                                    contentColor = if (isFavorited) Color(0xFFE53935) else Color.White,
                                ),
                            ) {
                                Icon(
                                    imageVector = if (isFavorited) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = if (isFavorited) "Remove from favorites" else "Add to favorites",
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }

                        // Download button: matching 48dp pill
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
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
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
        }

        // Inspect ModalBottomSheet
        if (showInspectSheet && currentPost != null) {
            ModalBottomSheet(
                onDismissRequest = { showInspectSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 36.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
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
                            onAuthorClick(displayPost ?: currentPost)
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
                                        when (newScore) {
                                            0 -> ToastManager.showInfo("Rating removed")
                                            3 -> ToastManager.showSuccess("Rated 3 stars (Favorited)")
                                            else -> ToastManager.showSuccess("Rated $newScore star${if (newScore > 1) "s" else ""}")
                                        }
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
                            TagChip(
                                tag = tag,
                                onClick = {
                                    showInspectSheet = false
                                    onTagClick(tag)
                                },
                            )
                        }
                    }
                }
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
                    // Author
            if (!post.title.isNullOrBlank()) {
                MetadataTableRow(
                    label = "Title",
                    value = post.title,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            }

            if (!post.author.isNullOrBlank()) {
                MetadataTableRow(
                    label = "Author",
                    value = post.author,
                    isClickable = true,
                    onClick = { onAuthorClick(post.author) },
                    valueColor = MaterialTheme.colorScheme.primary,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            }

            // Dimensions
            val ratio = calculateAspectRatio(post.width, post.height)
            val ratioSuffix = if (ratio.isNotBlank()) " ($ratio)" else ""
            MetadataTableRow(
                label = "Dimensions",
                value = "${post.width} × ${post.height}$ratioSuffix",
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

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
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            if (post.siteId == "pixiv") {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                MetadataTableRow(label = "Pages", value = "${post.pageIndex + 1} / ${post.pageCount}")
                post.bookmarkCount?.let { count ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                val ext = bestVariant.extension?.uppercase() ?: "IMAGE"
                MetadataTableRow(
                    label = "File",
                    value = "$ext • ${formatFileSize(bestVariant.fileSize)}",
                )
            }

            // Source
            if (!post.source.isNullOrBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
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
            .then(if (isClickable) Modifier.clickable(onClick = onClick) else Modifier)
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

@Composable
private fun TagChip(
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val baseColor = TagPalette[abs(tag.hashCode()) % TagPalette.size]
    val containerColor = baseColor.copy(alpha = 0.15f)
    val borderColor = baseColor.copy(alpha = 0.45f)

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(baseColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = tag,
                color = baseColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
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
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = if (starIndex <= score) Icons.Default.Star else Icons.Default.StarBorder,
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
