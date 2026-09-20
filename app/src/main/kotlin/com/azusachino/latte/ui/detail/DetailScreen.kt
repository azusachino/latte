package com.azusachino.latte.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.azusachino.latte.data.download.DownloadManager
import com.azusachino.latte.data.download.DownloadResult
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.model.PostRating
import com.azusachino.latte.ui.common.ToastManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    posts: List<Post>,
    initialIndex: Int,
    downloadManager: DownloadManager,
    onBack: () -> Unit,
    onTagClick: (String) -> Unit = {},
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

    val currentPost = posts.getOrNull(pagerState.currentPage)

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

        // Inspect ModalBottomSheet
        if (showInspectSheet && currentPost != null) {
            ModalBottomSheet(
                onDismissRequest = { showInspectSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 36.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = "Post #${currentPost.id}",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Meta Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("Resolution", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                            Text("${currentPost.width} × ${currentPost.height}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Column {
                            Text("Score", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                            Text("${currentPost.score}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Column {
                            Text("Rating", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                            val ratingText = when (currentPost.rating) {
                                PostRating.SAFE -> "Safe"
                                PostRating.QUESTIONABLE -> "Questionable"
                                PostRating.EXPLICIT -> "Explicit"
                                PostRating.UNKNOWN -> "Unknown"
                            }
                            Text(ratingText, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Tags
                    Text("Tags", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        currentPost.tags.forEach { tag ->
                            SuggestionChip(
                                onClick = {
                                    showInspectSheet = false
                                    onTagClick(tag)
                                },
                                label = { Text(tag) },
                            )
                        }
                    }
                }
            }
        }
    }
}
