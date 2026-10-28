package com.azusachino.latte.ui.explore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azusachino.latte.data.model.FavoriteTag
import com.azusachino.latte.plugin.PlatformId
import com.azusachino.latte.ui.common.GlassTopBar
import com.azusachino.latte.ui.common.glassCard
import com.azusachino.latte.ui.theme.GlassDefaults
import com.azusachino.latte.ui.theme.glassPillSelection
import com.azusachino.latte.ui.theme.pressScale

/**
 * Lists the owner's saved tags, grouped by platform, as glass pills. Tapping a
 * pill switches to its platform and opens that tag's search feed, so favorited
 * tags work as an update checklist; the pill's close button removes the tag.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteTagsScreen(
    viewModel: ExploreViewModel,
    onBack: () -> Unit,
    onOpenTag: (FavoriteTag) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val byPlatform = uiState.favoriteTags
        .sortedWith(compareBy({ it.platform }, { it.tag }))
        .groupBy { it.platform }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { GlassTopBar(title = "Favorite Tags", onBack = onBack) },
    ) { innerPadding ->
        if (byPlatform.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No favorite tags yet. Long-press a tag on a post's detail page " +
                        "to save it, or use the star in the search bar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .glassCard()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                byPlatform.forEach { (platform, tags) ->
                    item(key = "header:$platform") {
                        Text(
                            text = platformLabel(platform),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                        )
                    }
                    item(key = "tags:$platform") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            tags.forEach { favorite ->
                                TagPill(
                                    favorite = favorite,
                                    onOpen = { onOpenTag(favorite) },
                                    onRemove = { viewModel.toggleFavoriteTag(favorite.tag, favorite.platform) },
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
private fun TagPill(
    favorite: FavoriteTag,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .pressScale()
            .glassPillSelection(selected = true)
            .clip(RoundedCornerShape(GlassDefaults.PillCornerRadius))
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = favorite.tag,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp, end = 2.dp),
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove ${favorite.tag} from favorites",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private fun platformLabel(platform: PlatformId): String = when (platform) {
    PlatformId.YANDE -> "yande.re"
    PlatformId.PIXIV -> "Pixiv"
    PlatformId.KONACHAN -> "Konachan"
}
