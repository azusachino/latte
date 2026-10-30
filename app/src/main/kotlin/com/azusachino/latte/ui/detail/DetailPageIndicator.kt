package com.azusachino.latte.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azusachino.latte.ui.theme.GlassDefaults
import com.azusachino.latte.ui.theme.glassBackdrop
import dev.chrisbanes.haze.HazeState

internal fun detailPageIndicatorText(
    currentPage: Int,
    totalPosts: Int,
    isMultiPagePost: Boolean = false,
    pixivPageIndex: Int = 0,
    postPageCount: Int = 1,
): String {
    if (totalPosts <= 0) return ""
    val feedIndex = (currentPage + 1).coerceIn(1, totalPosts)
    val feedPart = "$feedIndex / $totalPosts"
    return if (isMultiPagePost && postPageCount > 1) {
        val pageIndex = (pixivPageIndex + 1).coerceIn(1, postPageCount)
        "$feedPart · p$pageIndex/$postPageCount"
    } else {
        feedPart
    }
}

@Composable
fun DetailTopCapsule(
    title: String,
    pageIndicator: String,
    hazeState: HazeState,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (title.isBlank() && pageIndicator.isBlank()) return

    Box(
        modifier = modifier
            .glassBackdrop(
                hazeState = hazeState,
                backgroundColor = Color.Black,
                shape = RoundedCornerShape(GlassDefaults.PillCornerRadius),
                dark = true,
            )
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Show details", onClick = onClick)
            .sizeIn(minHeight = 48.dp)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (pageIndicator.isNotBlank()) {
                Text(
                    text = pageIndicator,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun TransientDetailPageIndicator(
    text: String,
    visible: Boolean,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible && text.isNotBlank(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .glassBackdrop(
                    hazeState = hazeState,
                    backgroundColor = Color.Black,
                    shape = RoundedCornerShape(GlassDefaults.PillCornerRadius),
                    dark = true,
                )
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
            )
        }
    }
}
