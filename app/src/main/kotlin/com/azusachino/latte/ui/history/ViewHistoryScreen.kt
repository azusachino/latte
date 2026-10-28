package com.azusachino.latte.ui.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.data.settings.LattePreferences
import com.azusachino.latte.plugin.PlatformId
import com.azusachino.latte.ui.common.GlassPillChip
import com.azusachino.latte.ui.common.GlassTopBar
import com.azusachino.latte.ui.common.glassCard
import com.azusachino.latte.ui.explore.PostGridItem
import com.azusachino.latte.ui.theme.pressScale

/**
 * Posts the owner has opened, most recent first, across every platform, laid
 * out in the same grid as Explore. Each tile carries its platform name at the
 * bottom. The glass pills narrow the grid to one platform, and tapping a tile
 * opens Detail over the filtered grid so it can be swiped through.
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
    val columnCount by preferences.columnCount.collectAsStateWithLifecycle()
    var platformFilter by rememberSaveable { mutableStateOf<PlatformId?>(null) }
    val visible = remember(history, platformFilter) {
        history.filter { platformFilter == null || it.platform == platformFilter }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            GlassTopBar(
                title = "Viewing History",
                onBack = onBack,
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(
                            onClick = { preferences.clearViewHistory() },
                            modifier = Modifier.pressScale(),
                        ) {
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
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GlassPillChip(
                        selected = platformFilter == null,
                        onClick = { platformFilter = null },
                        label = "All",
                    )
                    PlatformId.entries.forEach { platform ->
                        GlassPillChip(
                            selected = platformFilter == platform,
                            onClick = { platformFilter = platform },
                            label = platform.displayName,
                        )
                    }
                }
            }

            when {
                history.isEmpty() -> EmptyHistory("Posts you open will appear here.")
                visible.isEmpty() -> EmptyHistory("No ${platformFilter?.displayName} posts in your history yet.")
                else -> LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(columnCount),
                    contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalItemSpacing = 4.dp,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(visible, key = { _, post -> "${post.siteId}:${post.id}" }) { index, post ->
                        PostGridItem(
                            post = post,
                            onClick = { onPostClick(visible, index) },
                            bottomLabel = post.platform.displayName,
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
            modifier = Modifier
                .glassCard()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        )
    }
}
