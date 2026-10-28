package com.azusachino.latte.ui.explore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackToTopThresholdTest {

    @Test
    fun hiddenAtTheTopOfTheFeed() {
        assertFalse(shouldShowBackToTop(0))
    }

    @Test
    fun hiddenJustBelowThreshold() {
        assertFalse(shouldShowBackToTop(BACK_TO_TOP_THRESHOLD_ITEMS - 1))
    }

    @Test
    fun shownAtAndBeyondThreshold() {
        assertTrue(shouldShowBackToTop(BACK_TO_TOP_THRESHOLD_ITEMS))
        assertTrue(shouldShowBackToTop(BACK_TO_TOP_THRESHOLD_ITEMS + 200))
    }
}
