package com.azusachino.latte.ui.explore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azusachino.latte.data.model.FavoriteTag
import com.azusachino.latte.plugin.PlatformId

/**
 * Lists the owner's saved tags across both platforms. Tapping a row switches
 * to its platform and opens that tag's search feed, so favorited tags work
 * as an update checklist; the trailing action removes the tag.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteTagsScreen(
    viewModel: ExploreViewModel,
    onBack: () -> Unit,
    onOpenTag: (FavoriteTag) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val favorites = uiState.favoriteTags.sortedWith(
        compareBy({ it.platform }, { it.tag }),
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Favorite Tags") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (favorites.isEmpty()) {
            Text(
                text = "No favorite tags yet. Long-press a tag on a post's detail page " +
                    "to save it, or use the star in the search bar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                items(favorites, key = { "${it.platform}:${it.tag}" }) { favorite ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = favorite.tag,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            Text(platformLabel(favorite.platform))
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingContent = {
                            IconButton(
                                onClick = { viewModel.toggleFavoriteTag(favorite.tag, favorite.platform) },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove ${favorite.tag} from favorites",
                                )
                            }
                        },
                        modifier = Modifier.clickable { onOpenTag(favorite) },
                    )
                }
            }
        }
    }
}

private fun platformLabel(platform: PlatformId): String = when (platform) {
    PlatformId.YANDE -> "yande.re"
    PlatformId.PIXIV -> "Pixiv"
    PlatformId.KONACHAN -> "Konachan"
}
