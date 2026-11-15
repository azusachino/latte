package com.azusachino.latte.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.azusachino.latte.data.settings.ThemeMode
import com.azusachino.latte.data.settings.UiStyle

enum class LattePalette {
    YANDE,
    KONACHAN,
    PIXIV,
    EMERALD,
}

private val LightColorScheme = lightColorScheme(
    primary = LattePrimaryLight,
    onPrimary = LatteOnPrimaryLight,
    primaryContainer = LattePrimaryContainerLight,
    onPrimaryContainer = LatteOnPrimaryContainerLight,
    secondary = LatteSecondaryLight,
    onSecondary = LatteOnSecondaryLight,
    secondaryContainer = LatteSecondaryContainerLight,
    onSecondaryContainer = LatteOnSecondaryContainerLight,
    tertiary = LatteTertiaryLight,
    onTertiary = LatteOnTertiaryLight,
    tertiaryContainer = LatteTertiaryContainerLight,
    onTertiaryContainer = LatteOnTertiaryContainerLight,
    background = LatteBackgroundLight,
    onBackground = LatteOnBackgroundLight,
    surface = LatteSurfaceLight,
    onSurface = LatteOnSurfaceLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = LattePrimaryDark,
    onPrimary = LatteOnPrimaryDark,
    primaryContainer = LattePrimaryContainerDark,
    onPrimaryContainer = LatteOnPrimaryContainerDark,
    secondary = LatteSecondaryDark,
    onSecondary = LatteOnSecondaryDark,
    secondaryContainer = LatteSecondaryContainerDark,
    onSecondaryContainer = LatteOnSecondaryContainerDark,
    tertiary = LatteTertiaryDark,
    onTertiary = LatteOnTertiaryDark,
    tertiaryContainer = LatteTertiaryContainerDark,
    onTertiaryContainer = LatteOnTertiaryContainerDark,
    background = LatteBackgroundDark,
    onBackground = LatteOnBackgroundDark,
    surface = LatteSurfaceDark,
    onSurface = LatteOnSurfaceDark,
)

@Composable
fun LatteTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    palette: LattePalette = LattePalette.YANDE,
    uiStyle: UiStyle = UiStyle.GLASS,
    oledDarkTheme: Boolean = false,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // Keyed on darkTheme, not run every recomposition, so a screen that overrides the
    // bar icons (see DetailScreen) is not reset while it is shown.
    val view = LocalView.current
    DisposableEffect(view, darkTheme) {
        val controller = WindowCompat.getInsetsController((view.context as Activity).window, view)
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
        onDispose {}
    }

    val context = LocalContext.current
    val baseColorScheme = when {
        dynamicColor -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val colorScheme = when (palette) {
        LattePalette.YANDE -> baseColorScheme.withYandePalette(darkTheme)
        LattePalette.KONACHAN -> baseColorScheme.withKonachanPalette(darkTheme)
        LattePalette.PIXIV -> baseColorScheme.withPixivPalette(darkTheme)
        LattePalette.EMERALD -> baseColorScheme.withEmeraldPalette(darkTheme)
    }

    val styledColorScheme = if (darkTheme && oledDarkTheme) {
        val (cBase, cHigh, cHighest) = when (palette) {
            LattePalette.YANDE -> Triple(
                androidx.compose.ui.graphics.Color(0xFF0F151B),
                androidx.compose.ui.graphics.Color(0xFF151C24),
                androidx.compose.ui.graphics.Color(0xFF1E2630),
            )
            LattePalette.PIXIV -> Triple(
                androidx.compose.ui.graphics.Color(0xFF11151E),
                androidx.compose.ui.graphics.Color(0xFF171C27),
                androidx.compose.ui.graphics.Color(0xFF202735),
            )
            LattePalette.KONACHAN -> Triple(
                androidx.compose.ui.graphics.Color(0xFF15120B),
                androidx.compose.ui.graphics.Color(0xFF1D1912),
                androidx.compose.ui.graphics.Color(0xFF27221A),
            )
            LattePalette.EMERALD -> Triple(
                androidx.compose.ui.graphics.Color(0xFF0E1511),
                androidx.compose.ui.graphics.Color(0xFF141E18),
                androidx.compose.ui.graphics.Color(0xFF1C2821),
            )
        }
        colorScheme.copy(
            surface = androidx.compose.ui.graphics.Color.Black,
            background = androidx.compose.ui.graphics.Color.Black,
            surfaceContainerLowest = androidx.compose.ui.graphics.Color.Black,
            surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFF080B0E),
            surfaceContainer = cBase,
            surfaceContainerHigh = cHigh,
            surfaceContainerHighest = cHighest,
        )
    } else {
        colorScheme
    }

    CompositionLocalProvider(
        LocalUiStyle provides uiStyle,
    ) {
        MaterialTheme(
            colorScheme = styledColorScheme,
            content = content,
        )
    }
}

private fun androidx.compose.material3.ColorScheme.withYandePalette(darkTheme: Boolean) = copy(
    primary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF96CEF4) else androidx.compose.ui.graphics.Color(0xFF3F6F8F),
    onPrimary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF00344C) else androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    primaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF1F4D68) else androidx.compose.ui.graphics.Color(0xFFC7E7FF),
    onPrimaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFC7E7FF) else androidx.compose.ui.graphics.Color(0xFF001E2F),
    secondary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFB7CAD8) else androidx.compose.ui.graphics.Color(0xFF51616F),
    onSecondary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF22333F) else androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    secondaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF384A57) else androidx.compose.ui.graphics.Color(0xFFD4E5F4),
    onSecondaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFD4E5F4) else androidx.compose.ui.graphics.Color(0xFF0D1D29),
    tertiary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFD7BDE3) else androidx.compose.ui.graphics.Color(0xFF6A5677),
    onTertiary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF3A2148) else androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    tertiaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF523A5F) else androidx.compose.ui.graphics.Color(0xFFF2DAFF),
    onTertiaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFF2DAFF) else androidx.compose.ui.graphics.Color(0xFF251431),
    background = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF101417) else androidx.compose.ui.graphics.Color(0xFFF8FAFC),
    onBackground = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFE1E4E8) else androidx.compose.ui.graphics.Color(0xFF191C1E),
    surface = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF101417) else androidx.compose.ui.graphics.Color(0xFFF8FAFC),
    onSurface = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFE1E4E8) else androidx.compose.ui.graphics.Color(0xFF191C1E),
    surfaceVariant = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF42474D) else androidx.compose.ui.graphics.Color(0xFFDEE3E9),
    onSurfaceVariant = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFC2C7CD) else androidx.compose.ui.graphics.Color(0xFF42474D),
    outline = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF8C9197) else androidx.compose.ui.graphics.Color(0xFF72787E),
    outlineVariant = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF42474D) else androidx.compose.ui.graphics.Color(0xFFC2C7CD),
    surfaceContainerLowest = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF0B0F12) else androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    surfaceContainerLow = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF181C20) else androidx.compose.ui.graphics.Color(0xFFF2F4F7),
    surfaceContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF1C2024) else androidx.compose.ui.graphics.Color(0xFFECEEF1),
    surfaceContainerHigh = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF272A2E) else androidx.compose.ui.graphics.Color(0xFFE6E8EB),
    surfaceContainerHighest = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF313539) else androidx.compose.ui.graphics.Color(0xFFE0E3E6),
)

private fun androidx.compose.material3.ColorScheme.withPixivPalette(darkTheme: Boolean) = copy(
    primary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF8BCBFF) else androidx.compose.ui.graphics.Color(0xFF006EA8),
    onPrimary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF003452) else androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    primaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF00527C) else androidx.compose.ui.graphics.Color(0xFFCDE5FF),
    onPrimaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFCDE5FF) else androidx.compose.ui.graphics.Color(0xFF001D35),
    secondary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFFFB0C8) else androidx.compose.ui.graphics.Color(0xFFB43E6D),
    onSecondary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF5E0A2C) else androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    secondaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF7F294B) else androidx.compose.ui.graphics.Color(0xFFFFD9E4),
    onSecondaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFFFD9E4) else androidx.compose.ui.graphics.Color(0xFF3F001E),
    tertiary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFC5C9FF) else androidx.compose.ui.graphics.Color(0xFF4E5F92),
    onTertiary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF282E62) else androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    tertiaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF3A4679) else androidx.compose.ui.graphics.Color(0xFFE0E5FF),
    onTertiaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFE0E5FF) else androidx.compose.ui.graphics.Color(0xFF0A153D),
    background = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF0E141A) else androidx.compose.ui.graphics.Color(0xFFF7FAFF),
    onBackground = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFE1E6EF) else androidx.compose.ui.graphics.Color(0xFF181C20),
    surface = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF0E141A) else androidx.compose.ui.graphics.Color(0xFFF7FAFF),
    onSurface = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFE1E6EF) else androidx.compose.ui.graphics.Color(0xFF181C20),
    surfaceVariant = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF41474F) else androidx.compose.ui.graphics.Color(0xFFDDE3EC),
    onSurfaceVariant = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFC1C7D0) else androidx.compose.ui.graphics.Color(0xFF41474F),
    outline = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF8B919A) else androidx.compose.ui.graphics.Color(0xFF717880),
    outlineVariant = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF41474F) else androidx.compose.ui.graphics.Color(0xFFC1C7D0),
    surfaceContainerLowest = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF090F14) else androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    surfaceContainerLow = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF161C23) else androidx.compose.ui.graphics.Color(0xFFF0F4FA),
    surfaceContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF1B2027) else androidx.compose.ui.graphics.Color(0xFFEAEEF4),
    surfaceContainerHigh = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF252B32) else androidx.compose.ui.graphics.Color(0xFFE4E8EE),
    surfaceContainerHighest = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF30363D) else androidx.compose.ui.graphics.Color(0xFFDFE3E9),
)

private fun androidx.compose.material3.ColorScheme.withKonachanPalette(darkTheme: Boolean) = copy(
    primary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFE5C286) else androidx.compose.ui.graphics.Color(0xFF7A5A1E),
    onPrimary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF412D00) else androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    primaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF5E431A) else androidx.compose.ui.graphics.Color(0xFFFFDFA5),
    onPrimaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFFFDFA5) else androidx.compose.ui.graphics.Color(0xFF261A00),
    secondary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFD8C4A4) else androidx.compose.ui.graphics.Color(0xFF6C5C42),
    onSecondary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF392E1A) else androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    secondaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF514429) else androidx.compose.ui.graphics.Color(0xFFF5E0BE),
    onSecondaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFF5E0BE) else androidx.compose.ui.graphics.Color(0xFF1A1207),
    tertiary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFB9CEA8) else androidx.compose.ui.graphics.Color(0xFF4F6540),
    onTertiary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF2A361D) else androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    tertiaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF404D33) else androidx.compose.ui.graphics.Color(0xFFD1E8BC),
    onTertiaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFD1E8BC) else androidx.compose.ui.graphics.Color(0xFF0F1906),
    background = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF15120D) else androidx.compose.ui.graphics.Color(0xFFFCF9F4),
    onBackground = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFE6E1D8) else androidx.compose.ui.graphics.Color(0xFF1C1B16),
    surface = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF15120D) else androidx.compose.ui.graphics.Color(0xFFFCF9F4),
    onSurface = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFE6E1D8) else androidx.compose.ui.graphics.Color(0xFF1C1B16),
    surfaceVariant = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF4A463D) else androidx.compose.ui.graphics.Color(0xFFE9E2D4),
    onSurfaceVariant = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFCCC5B4) else androidx.compose.ui.graphics.Color(0xFF4A463D),
    outline = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF958F80) else androidx.compose.ui.graphics.Color(0xFF7C766B),
    outlineVariant = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF4A463D) else androidx.compose.ui.graphics.Color(0xFFCCC5B4),
    surfaceContainerLowest = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF100D09) else androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    surfaceContainerLow = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF1D1B15) else androidx.compose.ui.graphics.Color(0xFFF6F3ED),
    surfaceContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF221F19) else androidx.compose.ui.graphics.Color(0xFFF1EDE6),
    surfaceContainerHigh = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF2C2923) else androidx.compose.ui.graphics.Color(0xFFEBE7E1),
    surfaceContainerHighest = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF38342D) else androidx.compose.ui.graphics.Color(0xFFE5E2DC),
)

private fun androidx.compose.material3.ColorScheme.withEmeraldPalette(darkTheme: Boolean) = copy(
    primary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF6CDBAC) else androidx.compose.ui.graphics.Color(0xFF006C4C),
    onPrimary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF003825) else androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    primaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF005138) else androidx.compose.ui.graphics.Color(0xFF89F8C7),
    onPrimaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF89F8C7) else androidx.compose.ui.graphics.Color(0xFF002114),
    secondary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFB4CCB9) else androidx.compose.ui.graphics.Color(0xFF4D6353),
    onSecondary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF203527) else androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    secondaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF364B3D) else androidx.compose.ui.graphics.Color(0xFFD0E8D5),
    onSecondaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFD0E8D5) else androidx.compose.ui.graphics.Color(0xFF0B1F13),
    tertiary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFA5CDDE) else androidx.compose.ui.graphics.Color(0xFF3D6473),
    onTertiary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF073543) else androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    tertiaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF244C5B) else androidx.compose.ui.graphics.Color(0xFFC1E9FB),
    onTertiaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFC1E9FB) else androidx.compose.ui.graphics.Color(0xFF001F29),
    background = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF0D1511) else androidx.compose.ui.graphics.Color(0xFFF5FBF5),
    onBackground = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFE0E4DF) else androidx.compose.ui.graphics.Color(0xFF171D19),
    surface = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF0D1511) else androidx.compose.ui.graphics.Color(0xFFF5FBF5),
    onSurface = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFE0E4DF) else androidx.compose.ui.graphics.Color(0xFF171D19),
    surfaceVariant = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF404943) else androidx.compose.ui.graphics.Color(0xFFDCE5DC),
    onSurfaceVariant = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFC0C9C1) else androidx.compose.ui.graphics.Color(0xFF404943),
    outline = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF8A938C) else androidx.compose.ui.graphics.Color(0xFF707973),
    outlineVariant = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF404943) else androidx.compose.ui.graphics.Color(0xFFBFC9C0),
    surfaceContainerLowest = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF08100C) else androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    surfaceContainerLow = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF151D19) else androidx.compose.ui.graphics.Color(0xFFEFF5EF),
    surfaceContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF19211D) else androidx.compose.ui.graphics.Color(0xFFE8EFE8),
    surfaceContainerHigh = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF232C27) else androidx.compose.ui.graphics.Color(0xFFE2EAE3),
    surfaceContainerHighest = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF2E3731) else androidx.compose.ui.graphics.Color(0xFFDCE4DD),
)
