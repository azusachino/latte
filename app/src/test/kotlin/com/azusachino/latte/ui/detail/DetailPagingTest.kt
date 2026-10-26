package com.azusachino.latte.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailPagingTest {

    @Test
    fun loadMoreTriggersWithinFiveOfTheEnd() {
        assertTrue(shouldLoadMoreDetail(currentPage = 95, size = 100))
        assertTrue(shouldLoadMoreDetail(currentPage = 99, size = 100))
    }

    @Test
    fun loadMoreDoesNotTriggerFarFromTheEnd() {
        assertFalse(shouldLoadMoreDetail(currentPage = 0, size = 100))
        assertFalse(shouldLoadMoreDetail(currentPage = 94, size = 100))
    }

    @Test
    fun loadMoreDoesNotTriggerForEmptyList() {
        assertFalse(shouldLoadMoreDetail(currentPage = 0, size = 0))
    }

    @Test
    fun loadMoreTriggersForShortListFromFirstPage() {
        assertTrue(shouldLoadMoreDetail(currentPage = 0, size = 3))
    }

    @Test
    fun leftEdgePagesBackOnMultiPageWork() {
        assertEquals(
            DetailTapAction.PreviousPage,
            detailTapAction(xFraction = 0.1f, zoomed = false, isMultiPage = true, pageIndex = 1, pageCount = 3),
        )
    }

    @Test
    fun rightEdgePagesForwardOnMultiPageWork() {
        assertEquals(
            DetailTapAction.NextPage,
            detailTapAction(xFraction = 0.9f, zoomed = false, isMultiPage = true, pageIndex = 1, pageCount = 3),
        )
    }

    @Test
    fun middleTapTogglesControls() {
        assertEquals(
            DetailTapAction.ToggleControls,
            detailTapAction(xFraction = 0.5f, zoomed = false, isMultiPage = true, pageIndex = 1, pageCount = 3),
        )
    }

    @Test
    fun edgeTapWhileZoomedTogglesControls() {
        assertEquals(
            DetailTapAction.ToggleControls,
            detailTapAction(xFraction = 0.1f, zoomed = true, isMultiPage = true, pageIndex = 1, pageCount = 3),
        )
        assertEquals(
            DetailTapAction.ToggleControls,
            detailTapAction(xFraction = 0.9f, zoomed = true, isMultiPage = true, pageIndex = 1, pageCount = 3),
        )
    }

    @Test
    fun leftEdgeOnFirstPageTogglesControls() {
        assertEquals(
            DetailTapAction.ToggleControls,
            detailTapAction(xFraction = 0.1f, zoomed = false, isMultiPage = true, pageIndex = 0, pageCount = 3),
        )
    }

    @Test
    fun rightEdgeOnLastPageTogglesControls() {
        assertEquals(
            DetailTapAction.ToggleControls,
            detailTapAction(xFraction = 0.9f, zoomed = false, isMultiPage = true, pageIndex = 2, pageCount = 3),
        )
    }

    @Test
    fun edgeTapOnSinglePageWorkTogglesControls() {
        assertEquals(
            DetailTapAction.ToggleControls,
            detailTapAction(xFraction = 0.1f, zoomed = false, isMultiPage = false, pageIndex = 0, pageCount = 1),
        )
        assertEquals(
            DetailTapAction.ToggleControls,
            detailTapAction(xFraction = 0.9f, zoomed = false, isMultiPage = false, pageIndex = 0, pageCount = 1),
        )
    }
}
