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
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
