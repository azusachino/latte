package com.azusachino.latte.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.settings.LattePreferences
import com.azusachino.latte.plugin.PlatformId

/**
 * Posts the owner has opened, most recent first, across every platform. The
 * chips narrow the list to one platform. Tapping a row opens Detail over the
 * filtered list, so it can be swiped through like a feed.
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
    var platformFilter by rememberSaveable { mutableStateOf<PlatformId?>(null) }
    val visible = remember(history, platformFilter) {
        history.filter { platformFilter == null || it.platform == platformFilter }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Viewing History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { preferences.clearViewHistory() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear viewing history",
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (history.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    FilterChip(
                        selected = platformFilter == null,
                        onClick = { platformFilter = null },
                        label = { Text("All") },
                    )
                    PlatformId.entries.forEach { platform ->
                        Box(modifier = Modifier.padding(start = 8.dp)) {
                            FilterChip(
                                selected = platformFilter == platform,
                                onClick = { platformFilter = platform },
                                label = { Text(platform.displayName) },
                            )
                        }
                    }
                }
            }

            when {
                history.isEmpty() -> EmptyHistory("Posts you open will appear here.")
                visible.isEmpty() -> EmptyHistory("No ${platformFilter?.displayName} posts in your history yet.")
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(visible, key = { _, post -> "${post.siteId}:${post.id}" }) { index, post ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = post.title ?: "#${post.id}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Text(post.platform.displayName)
                            },
                            leadingContent = {
                                AsyncImage(
                                    model = post.previewUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(56.dp),
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPostClick(visible, index) },
                        )
                    }
                }
            }
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
        )
    }
}
