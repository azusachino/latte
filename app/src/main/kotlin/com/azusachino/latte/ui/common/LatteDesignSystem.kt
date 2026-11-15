package com.azusachino.latte.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azusachino.latte.data.settings.UiStyle
import com.azusachino.latte.ui.explore.GlassSegmentedTabs
import com.azusachino.latte.ui.theme.LocalUiStyle
import com.azusachino.latte.ui.theme.glassBackdrop
import com.azusachino.latte.ui.theme.glassSurface
import com.azusachino.latte.ui.theme.pressScale
import dev.chrisbanes.haze.HazeState

/**
 * The two design systems Latte ships, selected by [LocalUiStyle]:
 *
 * - [UiStyle.MATERIAL]: authentic Material 3 (Material You) — flat `PrimaryTabRow`, ripple filter
 *   chips, `surfaceContainer` cards with 12.dp corners.
 * - [UiStyle.GLASS]: Liquid Glass — sliding frosted pills, specular rims, spring press scale.
 *
 * Screens should compose through these components rather than branching on the style themselves.
 */
object LatteDesign {
    val MaterialCardCornerRadius = 12.dp
    val GlassCardCornerRadius = 16.dp
    val FilterChipCornerRadius = 8.dp
}

private val isMaterialStyle: Boolean
    @Composable get() = LocalUiStyle.current == UiStyle.MATERIAL

/**
 * Tab row for a fixed set of sibling views (Explore: Posts, Pools, Popular).
 *
 * Material 3: [PrimaryTabRow] — transparent container, `primary` content colour and the official
 * pill underline indicator. Glass: [GlassSegmentedTabs] with its sliding frosted accent pill.
 *
 * [position] is a fractional tab position (pager page + offset) the Glass indicator follows live;
 * Material 3 ignores it because [PrimaryTabRow] slides its own indicator.
 */
@Composable
fun LatteTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    position: Float? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    if (!isMaterialStyle) {
        GlassSegmentedTabs(
            tabs = tabs,
            selectedIndex = selectedIndex,
            onTabSelected = onTabSelected,
            modifier = modifier,
            position = position,
            accent = accent,
        )
        return
    }

    PrimaryTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onTabSelected(index) },
                text = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Single-choice filter chip.
 *
 * Material 3: 8.dp rounded chip with a ripple, `secondaryContainer` fill when selected, a
 * [Icons.Default.Check] mark and a hairline border. Glass: the 22.dp [GlassPillChip] capsule with
 * its specular rim and spring press scale, so [leadingIcon] is Material-only.
 */
@Composable
fun LatteFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    if (!isMaterialStyle) {
        GlassPillChip(
            selected = selected,
            onClick = onClick,
            label = label,
            modifier = modifier,
        )
        return
    }

    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(LatteDesign.FilterChipCornerRadius)
    val contentColor = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant
    val showIcon = selected || leadingIcon != null

    Row(
        modifier = modifier
            .clip(shape)
            .background(
                color = if (selected) colors.secondaryContainer else Color.Transparent,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    colors.secondary.copy(alpha = 0.35f)
                } else {
                    colors.outlineVariant
                },
                shape = shape,
            )
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Checkbox,
            )
            .defaultMinSize(minHeight = 32.dp)
            .padding(
                start = if (showIcon) 12.dp else 16.dp,
                end = 16.dp,
                top = 6.dp,
                bottom = 6.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
        } else if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
        )
    }
}

/**
 * Grouped container for settings and detail rows.
 *
 * Material 3: [Card] on `surfaceContainer` with 12.dp corners and a 1.dp `outlineVariant` hairline.
 * Glass: elevated [glassSurface] column with 16.dp corners.
 */
@Composable
fun LatteGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!isMaterialStyle) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .glassSurface(
                    shape = RoundedCornerShape(LatteDesign.GlassCardCornerRadius),
                    isElevated = true,
                ),
            content = content,
        )
        return
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LatteDesign.MaterialCardCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(content = content)
    }
}

/**
 * Section label above a [LatteGroupCard].
 *
 * Material 3: sentence case `titleSmall` semi-bold in `primary`. Glass: uppercase tracked
 * `labelMedium`.
 */
@Composable
fun LatteSectionHeader(title: String, modifier: Modifier = Modifier) {
    if (!isMaterialStyle) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
        )
        return
    }

    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

/**
 * Action pill button for quick actions (e.g. Wallpaper, Copy Link) in sheets and headers.
 *
 * Material 3: subtle pill with surfaceContainerHigh container, outlineVariant border, and ripple.
 * Glass: elevated glass capsule with press spring scale and specular rim.
 */
@Composable
fun LatteActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    val isMaterial = LocalUiStyle.current == UiStyle.MATERIAL
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .then(
                if (isMaterial) {
                    Modifier
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), shape)
                        .clickable(onClick = onClick)
                } else {
                    Modifier
                        .pressScale()
                        .glassSurface(shape = shape, isElevated = true)
                        .clickable(onClick = onClick)
                },
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Screen top bar. Material 3: flat edge-to-edge `surfaceContainer` with a hairline divider and
 * loose icon buttons. Glass: rounded-bottom elevated glass with a specular rim.
 *
 * [GlassTopBar] already implements both treatments; this alias gives screens one design-system
 * vocabulary.
 */
@Composable
fun LatteTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    GlassTopBar(title = title, onBack = onBack, actions = actions)
}

/**
 * Surface styling for floating media viewer controls (Back, Title, Actions, Details, Favorite, Save).
 *
 * In Material 3: elevated surface container matching the chosen palette with outlineVariant border.
 * In Glass: theme-adaptive frosted backdrop blur with specular gradient rim and press spring scale.
 */
@Composable
fun Modifier.latteMediaControl(
    shape: Shape,
    hazeState: HazeState? = null,
): Modifier {
    val isMaterial = LocalUiStyle.current == UiStyle.MATERIAL
    return if (isMaterial) {
        this
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f), shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), shape)
    } else {
        if (hazeState != null) {
            this
                .glassBackdrop(
                    hazeState,
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    shape = shape,
                )
                .pressScale()
        } else {
            this
                .glassSurface(shape = shape, isElevated = true)
                .pressScale()
        }
    }
}
