package com.azusachino.latte.ui.explore

import com.azusachino.latte.ui.common.GlassAlertDialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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

/** Parses a typed page number, accepting only 1..[maxPage]. */
internal fun parsePageInput(text: String, maxPage: Int): Int? =
    text.trim().toIntOrNull()?.takeIf { it in 1..maxPage }

@Composable
internal fun FeedPagerBar(
    pager: FeedPager,
    feed: FeedState,
    modifier: Modifier = Modifier,
) {
    var showJump by remember { mutableStateOf(false) }
    val idle = !feed.isLoading && !feed.isRefreshing
    val maxPage = pager.maxDirectPage
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.45f)),
    ) {
        IconButton(
            onClick = { pager.onGoToPage(feed.page - 1) },
            enabled = idle && feed.page > 1,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(LatteIcons.ChevronLeft, contentDescription = "Previous page", tint = Color.White)
        }
        Text(
            text = "${feed.page}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = Color.White,
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
        )
        IconButton(
            onClick = { pager.onGoToPage(feed.page + 1) },
            enabled = idle && feed.hasMore,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(LatteIcons.ChevronRight, contentDescription = "Next page", tint = Color.White)
        }
    }

    if (showJump && maxPage != null) {
        GoToPageDialog(
            currentPage = feed.page,
            maxPage = maxPage,
            onDismiss = { showJump = false },
            onConfirm = { page ->
                showJump = false
                if (page != feed.page) pager.onGoToPage(page)
            },
        )
    }
}

@Composable
private fun GoToPageDialog(
    currentPage: Int,
    maxPage: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(currentPage.toString()) }
    val page = parsePageInput(text, maxPage)
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to page") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(MAX_PAGE_DIGITS) },
                singleLine = true,
                isError = text.isNotEmpty() && page == null,
                supportingText = if (maxPage < Int.MAX_VALUE) {
                    { Text("1 to $maxPage") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { page?.let(onConfirm) }),
            )
        },
        confirmButton = {
            TextButton(onClick = { page?.let(onConfirm) }, enabled = page != null) { Text("Go") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private const val MAX_PAGE_DIGITS = 6
