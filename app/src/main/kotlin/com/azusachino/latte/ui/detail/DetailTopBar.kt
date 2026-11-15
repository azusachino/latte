package com.azusachino.latte.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.settings.UiStyle
import com.azusachino.latte.ui.common.LatteIcons
import com.azusachino.latte.ui.common.latteMediaControl
import com.azusachino.latte.ui.theme.LocalUiStyle
import com.azusachino.latte.ui.theme.glassBackdrop
import com.azusachino.latte.ui.theme.pressScale
import dev.chrisbanes.haze.HazeState

@Composable
fun DetailTopBar(
    currentPost: Post?,
    postUrl: String?,
    pageIndicatorText: String,
    hazeState: HazeState,
    onBack: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isMaterial = LocalUiStyle.current == UiStyle.MATERIAL

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isMaterial) {
                    Modifier.background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.7f), Color.Black.copy(alpha = 0.25f), Color.Transparent),
                        ),
                    )
                } else {
                    Modifier
                },
            )
            .pointerInput(Unit) {
                detectTapGestures { }
            }
            .statusBarsPadding()
            .padding(horizontal = if (isMaterial) 8.dp else 16.dp, vertical = if (isMaterial) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Left pill: Circular back button
        Box(
            modifier = Modifier
                .size(44.dp)
                .latteMediaControl(shape = CircleShape, hazeState = hazeState),
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

        // Center capsule: Title & page indicator
        val centerTitle = remember(currentPost?.id, currentPost?.title, currentPost?.platform) {
            when {
                currentPost == null -> ""
                !currentPost.title.isNullOrBlank() -> currentPost.title.orEmpty()
                else -> "#${currentPost.id} · ${currentPost.platform.displayName}"
            }
        }

        Box(
            modifier = Modifier
                .height(44.dp)
                .weight(1f)
                .padding(horizontal = 8.dp)
                .latteMediaControl(shape = RoundedCornerShape(12.dp), hazeState = hazeState)
                .clickable(enabled = currentPost != null, onClick = onDetails)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = centerTitle,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (pageIndicatorText.isNotBlank()) {
                    Text(
                        text = pageIndicatorText,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }
        }

        // Right pill: Action buttons for Browser and Share
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!postUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .latteMediaControl(shape = CircleShape, hazeState = hazeState),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(postUrl),
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

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .latteMediaControl(shape = CircleShape, hazeState = hazeState),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    postUrl,
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
