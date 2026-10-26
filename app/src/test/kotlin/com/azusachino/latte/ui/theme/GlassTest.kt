package com.azusachino.latte.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassTest {

    @Test
    fun glassDefaultsHaveExpectedDimensions() {
        assertEquals(24.dp, GlassDefaults.BlurRadius)
        assertEquals(1.dp, GlassDefaults.RimStrokeWidth)
        assertEquals(14.dp, GlassDefaults.CardCornerRadius)
        assertEquals(24.dp, GlassDefaults.PillCornerRadius)
        assertEquals(0.97f, GlassDefaults.PressScale)
    }

    @Test
    fun glassDefaultsHaveTranslucentSurfaces() {
        // Dark surface is translucent (alpha = 0xCC / 0xFF = 0.8)
        assertEquals(Color(0xCC14171A), GlassDefaults.DarkSurface)
        assertTrue(GlassDefaults.DarkSurface.alpha < 1.0f)

        // Light surface is translucent (alpha = 0xD9 / 0xFF = 0.85)
        assertEquals(Color(0xD9FFFFFF), GlassDefaults.LightSurface)
        assertTrue(GlassDefaults.LightSurface.alpha < 1.0f)

        // Elevated surfaces are defined
        assertNotNull(GlassDefaults.DarkSurfaceElevated)
        assertNotNull(GlassDefaults.LightSurfaceElevated)
    }

    @Test
    fun specularGradientsAndSpringSpecAreDefined() {
        assertNotNull(GlassDefaults.DarkRimGradient)
        assertNotNull(GlassDefaults.LightRimGradient)
        assertNotNull(GlassDefaults.PressSpringSpec)
    }
}
