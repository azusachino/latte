package com.azusachino.latte.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.azusachino.latte.data.download.DownloadManager
import com.azusachino.latte.data.download.DownloadResult
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.model.PostRating
import com.azusachino.latte.plugin.PluginCapability
import com.azusachino.latte.plugin.SitePlugin
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
    sitePlugin: SitePlugin? = null,
    onRequireLogin: (SitePlugin) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (posts.size - 1).coerceAtLeast(0)),
        pageCount = { posts.size },
    )
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showControls by remember { mutableStateOf(true) }
    var showInspectSheet by remember { mutableStateOf(false) }
    var localScores by remember { mutableStateOf(mapOf<Long, Int>()) }

    val currentPost = posts.getOrNull(pagerState.currentPage)

    // The site never tells the app "you already scored this post" up front --
    // recover a favorite (score 3) set in a prior session or on the web.
    LaunchedEffect(currentPost?.id, sitePlugin?.isLoggedIn) {
        val post = currentPost
        if (post != null && sitePlugin != null && sitePlugin.isLoggedIn && sitePlugin.getScore(post.id) == null) {
            sitePlugin.refreshScore(post.id)?.let { refreshed ->
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
            val post = posts[page]
            ZoomableBox(
                modifier = Modifier.fillMaxSize(),
                onTap = { showControls = !showControls },
            ) {
                SubcomposeAsyncImage(
                    model = post.sampleUrl,
                    contentDescription = null,
                    loading = {
                        // Instant display of cached preview bitmap from memory cache
                        AsyncImage(
                            model = post.previewUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
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
                        text = if (currentPost != null) "#${currentPost.id}" else "",
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
                                Uri.parse("https://yande.re/post/show/${currentPost.id}"),
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
                                putExtra(Intent.EXTRA_TEXT, "https://yande.re/post/show/${currentPost.id}")
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
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
                            text = if (currentPost != null) "${currentPost.width}×${currentPost.height}" else "Details",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                        )
                    }

                    // Right actions: Favorite + Save
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (sitePlugin != null && sitePlugin.capabilities.contains(PluginCapability.FAVORITES)) {
                            val isPluginLoggedIn by sitePlugin.isLoggedInFlow.collectAsState(initial = sitePlugin.isLoggedIn)
                            val currentScore = localScores[currentPost?.id] ?: sitePlugin.getScore(currentPost?.id ?: 0L) ?: 0
                            val isFavorited = currentScore == 3

                            FilledTonalIconButton(
                                onClick = {
                                    if (!isPluginLoggedIn) {
                                        onRequireLogin(sitePlugin)
                                    } else if (currentPost != null) {
                                        scope.launch {
                                            val targetScore = if (isFavorited) 0 else 3
                                            val result = sitePlugin.setScore(currentPost.id, targetScore)
                                            if (result.isSuccess) {
                                                localScores = localScores + (currentPost.id to targetScore)
                                                if (targetScore == 3) {
                                                    ToastManager.showSuccess("Added to favorites")
                                                } else {
                                                    ToastManager.showInfo("Removed from favorites")
                                                }
                                            } else {
                                                ToastManager.showInfo("Failed to update favorite: ${result.exceptionOrNull()?.message}")
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
                                if (currentPost != null) {
                                    scope.launch {
                                        when (val result = downloadManager.enqueueDownload(currentPost)) {
                                            is DownloadResult.AlreadySaved -> {
                                                ToastManager.showWarning("Already saved: ${result.displayName}")
                                            }
                                            is DownloadResult.AlreadyRunning -> {
                                                ToastManager.showWarning("Download already running")
                                            }
                                            is DownloadResult.Started -> {
                                                ToastManager.showSuccess("Download started: ${result.displayName}")
                                            }
                                            is DownloadResult.Failed -> {
                                                ToastManager.showInfo("Failed: ${result.message}")
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
                        post = currentPost,
                        onAuthorClick = { author ->
                            showInspectSheet = false
                            onTagClick("user:$author")
                        },
                        onSourceClick = { url ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        },
                    )

                    // Personal Rating (0-3 stars)
                    if (sitePlugin != null && sitePlugin.capabilities.contains(PluginCapability.SCORING)) {
                        val isPluginLoggedIn by sitePlugin.isLoggedInFlow.collectAsState(initial = sitePlugin.isLoggedIn)
                        val currentScore = localScores[currentPost.id] ?: sitePlugin.getScore(currentPost.id) ?: 0

                        Spacer(modifier = Modifier.height(16.dp))
                        PersonalRatingSection(
                            score = currentScore,
                            isLoggedIn = isPluginLoggedIn,
                            onRate = { newScore ->
                                scope.launch {
                                    val result = sitePlugin.setScore(currentPost.id, newScore)
                                    if (result.isSuccess) {
                                        localScores = localScores + (currentPost.id to newScore)
                                        when (newScore) {
                                            0 -> ToastManager.showInfo("Rating removed")
                                            3 -> ToastManager.showSuccess("Rated 3 stars (Favorited)")
                                            else -> ToastManager.showSuccess("Rated $newScore star${if (newScore > 1) "s" else ""}")
                                        }
                                    } else {
                                        ToastManager.showInfo("Failed to submit rating: ${result.exceptionOrNull()?.message}")
                                    }
                                }
                            },
                            onLoginRequest = {
                                onRequireLogin(sitePlugin)
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

            // Score
            MetadataTableRow(
                label = "Score",
                value = "${post.score}",
            )

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
