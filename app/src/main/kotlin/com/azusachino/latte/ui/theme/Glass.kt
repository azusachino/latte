package com.azusachino.latte.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Design tokens and styling parameters for Apple-style Liquid Glass UI components.
 */
object GlassDefaults {
    val RimStrokeWidth: Dp = 1.dp
    val CardCornerRadius: Dp = 14.dp
    val PillCornerRadius: Dp = 24.dp

    // Translucent dark mode surfaces
    val DarkSurface: Color = Color(0xCC14171A)
    val DarkSurfaceElevated: Color = Color(0xD91E2228)

    // Translucent light mode surfaces
    val LightSurface: Color = Color(0xD9FFFFFF)
    val LightSurfaceElevated: Color = Color(0xE6FFFFFF)

    // Specular rim gradient brushes
    val DarkRimGradient: Brush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.22f),
            Color.White.copy(alpha = 0.05f),
        )
    )

    val LightRimGradient: Brush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.80f),
            Color.Black.copy(alpha = 0.06f),
        )
    )

    const val PressScale: Float = 0.97f

    val PressSpringSpec = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessMediumLow,
    )
}

/**
 * Applies a liquid glass surface styling: translucent surface tinting, clipping, and a
 * specular gradient rim border. Backdrop blur is not applied: Compose `Modifier.blur` blurs
 * the element's own content, not what is behind it.
 *
 * [dark] overrides the luminance-based theme detection when non-null.
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(GlassDefaults.PillCornerRadius),
    isElevated: Boolean = false,
    rimStroke: Dp = GlassDefaults.RimStrokeWidth,
    dark: Boolean? = null,
): Modifier {
    val isDark = dark ?: (MaterialTheme.colorScheme.surface.luminance() < 0.5f)
    val surfaceColor = when {
        isDark && isElevated -> GlassDefaults.DarkSurfaceElevated
        isDark -> GlassDefaults.DarkSurface
        isElevated -> GlassDefaults.LightSurfaceElevated
        else -> GlassDefaults.LightSurface
    }
    val rimBrush = if (isDark) GlassDefaults.DarkRimGradient else GlassDefaults.LightRimGradient

    return this
        .clip(shape)
        .background(surfaceColor, shape)
        .then(
            if (rimStroke > 0.dp) {
                Modifier.border(rimStroke, rimBrush, shape)
            } else {
                Modifier
            }
        )
}

/**
 * Pill chip state: a selected chip is a solid `primaryContainer` fill with a 1dp primary outline,
 * so it reads in both themes; an unselected chip stays a plain glass surface.
 */
@Composable
fun Modifier.glassPillSelection(
    selected: Boolean,
    shape: Shape = RoundedCornerShape(GlassDefaults.PillCornerRadius),
): Modifier {
    if (!selected) return glassSurface(shape = shape)
    return this
        .clip(shape)
        .background(MaterialTheme.colorScheme.primaryContainer, shape)
        .border(1.dp, MaterialTheme.colorScheme.primary, shape)
}

/**
 * Micro-interaction modifier that applies tactile spring scale-on-press feedback.
 */
@Composable
fun Modifier.pressScale(
    targetScale: Float = GlassDefaults.PressScale,
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return this

    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1f,
        animationSpec = GlassDefaults.PressSpringSpec,
        label = "glass_press_scale",
    )

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(enabled, targetScale) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                try {
                    // Quick taps and flings lift or cancel within the delay and never scale.
                    val settled = withTimeoutOrNull(100L) {
                        waitForUpOrCancellation()
                        true
                    }
                    if (settled == null) {
                        isPressed = true
                        waitForUpOrCancellation()
                    }
                } finally {
                    isPressed = false
                }
            }
        }
}

@Preview(name = "Glass Surface - Dark", showBackground = true, backgroundColor = 0xFF121418)
@Composable
private fun GlassSurfaceDarkPreview() {
    MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
        Box(
            modifier = Modifier
                .padding(24.dp)
                .glassSurface(
                    shape = RoundedCornerShape(GlassDefaults.PillCornerRadius),
                )
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text("Glass Pill (Dark)", color = Color.White)
        }
    }
}

@Preview(name = "Glass Surface - Light", showBackground = true, backgroundColor = 0xFFF0F0F0)
@Composable
private fun GlassSurfaceLightPreview() {
    MaterialTheme(colorScheme = androidx.compose.material3.lightColorScheme()) {
        Box(
            modifier = Modifier
                .padding(24.dp)
                .glassSurface(
                    shape = RoundedCornerShape(GlassDefaults.CardCornerRadius),
                    isElevated = true,
                )
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text("Glass Card (Light)", color = Color.Black)
        }
    }
}
