package com.azusachino.latte.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperHelperTest {

    @Test
    fun cropHintForWiderImageCropsLeftAndRight() {
        // Image is 16:9 (1920 x 1080), Screen is 20:9 (1080 x 2400)
        // Screen ratio: 2400 / 1080 = 2.2222
        // Target width in image: 1080 / 2.2222 = 486
        // Left offset: (1920 - 486) / 2 = 717
        val crop = calculateCenterCropHint(
            imageWidth = 1920,
            imageHeight = 1080,
            screenWidth = 1080,
            screenHeight = 2400,
        )

        assertEquals(0, crop.top)
        assertEquals(1080, crop.bottom)
        assertTrue(crop.left in 701..729)
        assertTrue(crop.width in 481..489)
        // Ratio of crop should match screen ratio
        val cropRatio = crop.height.toDouble() / crop.width.toDouble()
        val screenRatio = 2400.0 / 1080.0
        assertEquals(screenRatio, cropRatio, 0.01)
    }

    @Test
    fun cropHintForTallerImageCropsTopAndBottom() {
        // Image is tall (1000 x 3000), Screen is 20:9 (1080 x 2400)
        // Screen ratio: 2400 / 1080 = 2.2222
        // Target height in image: 1000 * 2.2222 = 2222
        // Top offset: (3000 - 2222) / 2 = 389
        val crop = calculateCenterCropHint(
            imageWidth = 1000,
            imageHeight = 3000,
            screenWidth = 1080,
            screenHeight = 2400,
        )

        assertEquals(0, crop.left)
        assertEquals(1000, crop.right)
        assertTrue(crop.top in 381..399)
        assertTrue(crop.height in 2216..2229)
        val cropRatio = crop.height.toDouble() / crop.width.toDouble()
        val screenRatio = 2400.0 / 1080.0
        assertEquals(screenRatio, cropRatio, 0.01)
    }

    @Test
    fun cropHintForMatchingAspectRatioKeepsFullDimensions() {
        val crop = calculateCenterCropHint(
            imageWidth = 1080,
            imageHeight = 2400,
            screenWidth = 1080,
            screenHeight = 2400,
        )

        assertEquals(WallpaperCropRect(0, 0, 1080, 2400), crop)
    }

    @Test
    fun cropHintHandlesZeroOrNegativeGracefully() {
        val crop = calculateCenterCropHint(
            imageWidth = 0,
            imageHeight = 0,
            screenWidth = 1080,
            screenHeight = 2400,
        )
        assertEquals(WallpaperCropRect(0, 0, 0, 0), crop)
    }
}
