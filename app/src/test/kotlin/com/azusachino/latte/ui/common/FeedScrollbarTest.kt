package com.azusachino.latte.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedScrollbarTest {

    // 100 items in 2 columns of 500px rows: 25,000px of content in a 2,000px window.
    private fun extentAt(index: Int, offset: Int = 0) = scrollExtent(
        firstVisibleIndex = index,
        firstVisibleOffset = offset,
        totalCount = 100,
        lanes = 2,
        averageItemHeight = 500f,
        viewportHeight = 2000,
    )

    @Test
    fun spansTopToBottom() {
        assertEquals(0f, extentAt(0).fraction, 0f)
        assertEquals(23000f, extentAt(0).scrollable, 0f)
        assertEquals(1f, extentAt(92).fraction, 0f)
    }

    @Test
    fun movesWithinARowInsteadOfSteppingPerTile() {
        // Partway through the first row the thumb has moved, though the first index has not.
        assertTrue(extentAt(0, offset = 250).fraction > 0f)
        assertTrue(extentAt(0, offset = 250).fraction < extentAt(2).fraction)
    }

    @Test
    fun contentThatFitsHasNoTravel() {
        val fits = scrollExtent(0, 0, totalCount = 4, lanes = 2, averageItemHeight = 400f, viewportHeight = 2000)
        assertEquals(0f, fits.scrollable, 0f)
        assertEquals(0f, fits.fraction, 0f)
        assertEquals(0f, scrollExtent(0, 0, 0, 2, 0f, 2000).fraction, 0f)
    }
}
