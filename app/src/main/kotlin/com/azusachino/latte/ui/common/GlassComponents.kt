package com.azusachino.latte.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azusachino.latte.data.settings.UiStyle
import com.azusachino.latte.ui.theme.GlassDefaults
import com.azusachino.latte.ui.theme.LocalUiStyle
import com.azusachino.latte.ui.theme.glassPillSelection
import com.azusachino.latte.ui.theme.glassSurface
import com.azusachino.latte.ui.theme.pressScale

/** Top bar: flat edge-to-edge in Material 3 mode, floating rounded glass in Glass mode. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val isMaterial = LocalUiStyle.current == UiStyle.MATERIAL
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isMaterial) {
                    Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .drawBehind {
                            drawLine(
                                color = outlineVariant,
                                start = Offset(0f, size.height),
                                end = Offset(size.width, size.height),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }
                } else {
                    Modifier.glassSurface(
                        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                        isElevated = true,
                    )
                }
            ),
    ) {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = if (!isMaterial) Modifier.pressScale() else Modifier) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface,
            ),
        )
    }
}

/** A selectable glass pill. Selected pills take the primary container colour. */
@Composable
fun GlassPillChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .pressScale()
            .glassPillSelection(selected = selected)
            .clip(RoundedCornerShape(GlassDefaults.PillCornerRadius))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/** A rounded card for list rows: surfaceContainer in M3, elevated glassSurface in Glass. */
@Composable
fun Modifier.glassCard(): Modifier {
    val isMaterial = LocalUiStyle.current == UiStyle.MATERIAL
    val cardShape = RoundedCornerShape(if (isMaterial) 12.dp else GlassDefaults.CardCornerRadius)
    return if (isMaterial) {
        this
            .clip(cardShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                cardShape,
            )
    } else {
        this
            .pressScale()
            .glassSurface(shape = cardShape)
    }
}
