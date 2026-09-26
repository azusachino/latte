package com.azusachino.latte.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.azusachino.latte.data.settings.ThemeMode

enum class LattePalette {
    YANDE,
    KONACHAN,
    PIXIV,
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
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val baseColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val colorScheme = when (palette) {
        LattePalette.YANDE -> baseColorScheme.withYandePalette(darkTheme)
        LattePalette.KONACHAN -> baseColorScheme.withKonachanPalette(darkTheme)
        LattePalette.PIXIV -> baseColorScheme.withPixivPalette(darkTheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

private fun androidx.compose.material3.ColorScheme.withYandePalette(darkTheme: Boolean) = copy(
    primary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF96CEF4) else androidx.compose.ui.graphics.Color(0xFF3F6F8F),
    onPrimary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF00344C) else androidx.compose.ui.graphics.Color.White,
    primaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF1F4D68) else androidx.compose.ui.graphics.Color(0xFFC7E7FF),
    onPrimaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFC7E7FF) else androidx.compose.ui.graphics.Color(0xFF001E2F),
    secondary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFB7CAD8) else androidx.compose.ui.graphics.Color(0xFF51616F),
    onSecondary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF22333F) else androidx.compose.ui.graphics.Color.White,
    secondaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF384A57) else androidx.compose.ui.graphics.Color(0xFFD4E5F4),
    onSecondaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFD4E5F4) else androidx.compose.ui.graphics.Color(0xFF0D1D29),
    tertiary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFD7BDE3) else androidx.compose.ui.graphics.Color(0xFF6A5677),
    onTertiary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF3A2148) else androidx.compose.ui.graphics.Color.White,
    tertiaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF523A5F) else androidx.compose.ui.graphics.Color(0xFFF2DAFF),
    onTertiaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFF2DAFF) else androidx.compose.ui.graphics.Color(0xFF251431),
    background = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF101417) else androidx.compose.ui.graphics.Color(0xFFF8FAFC),
    onBackground = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFE1E4E8) else androidx.compose.ui.graphics.Color(0xFF191C1E),
    surface = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF101417) else androidx.compose.ui.graphics.Color(0xFFF8FAFC),
    onSurface = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFE1E4E8) else androidx.compose.ui.graphics.Color(0xFF191C1E),
    surfaceVariant = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF42474D) else androidx.compose.ui.graphics.Color(0xFFDEE3E9),
    onSurfaceVariant = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFC2C7CD) else androidx.compose.ui.graphics.Color(0xFF42474D),
)

private fun androidx.compose.material3.ColorScheme.withPixivPalette(darkTheme: Boolean) = copy(
    primary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF8BCBFF) else androidx.compose.ui.graphics.Color(0xFF006EA8),
    onPrimary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF003452) else androidx.compose.ui.graphics.Color.White,
    primaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF00527C) else androidx.compose.ui.graphics.Color(0xFFCDE5FF),
    onPrimaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFCDE5FF) else androidx.compose.ui.graphics.Color(0xFF001D35),
    secondary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFFFB0C8) else androidx.compose.ui.graphics.Color(0xFFB43E6D),
    onSecondary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF5E0A2C) else androidx.compose.ui.graphics.Color.White,
    secondaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF7F294B) else androidx.compose.ui.graphics.Color(0xFFFFD9E4),
    onSecondaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFFFD9E4) else androidx.compose.ui.graphics.Color(0xFF3F001E),
    tertiary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFC5C9FF) else androidx.compose.ui.graphics.Color(0xFF4E5F92),
    onTertiary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF282E62) else androidx.compose.ui.graphics.Color.White,
    tertiaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF3A4679) else androidx.compose.ui.graphics.Color(0xFFE0E5FF),
    onTertiaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFE0E5FF) else androidx.compose.ui.graphics.Color(0xFF0A153D),
    background = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF0E141A) else androidx.compose.ui.graphics.Color(0xFFF7FAFF),
    onBackground = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFE1E6EF) else androidx.compose.ui.graphics.Color(0xFF181C20),
    surface = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF0E141A) else androidx.compose.ui.graphics.Color(0xFFF7FAFF),
    onSurface = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFE1E6EF) else androidx.compose.ui.graphics.Color(0xFF181C20),
    surfaceVariant = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF41474F) else androidx.compose.ui.graphics.Color(0xFFDDE3EC),
    onSurfaceVariant = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFC1C7D0) else androidx.compose.ui.graphics.Color(0xFF41474F),
)

private fun androidx.compose.material3.ColorScheme.withKonachanPalette(darkTheme: Boolean) = copy(
    primary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFE5C286) else androidx.compose.ui.graphics.Color(0xFF7A5A1E),
    onPrimary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF412D00) else androidx.compose.ui.graphics.Color.White,
    primaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF5E431A) else androidx.compose.ui.graphics.Color(0xFFFFDFA5),
    onPrimaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFFFDFA5) else androidx.compose.ui.graphics.Color(0xFF261A00),
    secondary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFD8C4A4) else androidx.compose.ui.graphics.Color(0xFF6C5C42),
    onSecondary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF392E1A) else androidx.compose.ui.graphics.Color.White,
    secondaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF514429) else androidx.compose.ui.graphics.Color(0xFFF5E0BE),
    onSecondaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFF5E0BE) else androidx.compose.ui.graphics.Color(0xFF1A1207),
    tertiary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFB9CEA8) else androidx.compose.ui.graphics.Color(0xFF4F6540),
    onTertiary = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF2A361D) else androidx.compose.ui.graphics.Color.White,
    tertiaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF404D33) else androidx.compose.ui.graphics.Color(0xFFD1E8BC),
    onTertiaryContainer = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFD1E8BC) else androidx.compose.ui.graphics.Color(0xFF0F1906),
    background = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF15120D) else androidx.compose.ui.graphics.Color(0xFFFCF9F4),
    onBackground = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFE6E1D8) else androidx.compose.ui.graphics.Color(0xFF1C1B16),
    surface = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF15120D) else androidx.compose.ui.graphics.Color(0xFFFCF9F4),
    onSurface = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFE6E1D8) else androidx.compose.ui.graphics.Color(0xFF1C1B16),
    surfaceVariant = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF4A463D) else androidx.compose.ui.graphics.Color(0xFFE9E2D4),
    onSurfaceVariant = if (darkTheme) androidx.compose.ui.graphics.Color(0xFFCCC5B4) else androidx.compose.ui.graphics.Color(0xFF4A463D),
)
