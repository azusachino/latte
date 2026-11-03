package com.azusachino.latte.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedScrollbarTest {

    @Test
    fun fractionSpansTopToBottom() {
        assertEquals(0f, scrollbarFraction(firstVisibleIndex = 0, visibleCount = 10, totalCount = 110), 0f)
        assertEquals(0.5f, scrollbarFraction(firstVisibleIndex = 50, visibleCount = 10, totalCount = 110), 0f)
        assertEquals(1f, scrollbarFraction(firstVisibleIndex = 100, visibleCount = 10, totalCount = 110), 0f)
    }

    @Test
    fun listThatFitsOnScreenHasNoTravel() {
        assertEquals(0f, scrollbarFraction(firstVisibleIndex = 0, visibleCount = 8, totalCount = 8), 0f)
        assertEquals(0f, scrollbarFraction(firstVisibleIndex = 0, visibleCount = 0, totalCount = 0), 0f)
    }

    @Test
    fun dragTargetMapsToFirstAndLastItems() {
        assertEquals(0, scrollbarTargetIndex(0f, totalCount = 101))
        assertEquals(50, scrollbarTargetIndex(0.5f, totalCount = 101))
        assertEquals(100, scrollbarTargetIndex(1.5f, totalCount = 101))
        assertEquals(0, scrollbarTargetIndex(0.7f, totalCount = 0))
    }
}
