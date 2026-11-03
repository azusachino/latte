package com.azusachino.latte.ui.explore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileEntranceTest {

    @Test
    fun everyTileIsHiddenAtTheStartAndSettledAtTheEnd() {
        for (index in listOf(0, 5, 12, 80)) {
            assertEquals(0f, tileEntrance(0f, index), 0f)
            assertEquals(1f, tileEntrance(1f, index), 0f)
        }
    }

    @Test
    fun earlierTilesLeadAndLateTilesShareTheLastStart() {
        assertTrue(tileEntrance(0.3f, 0) > tileEntrance(0.3f, 5))
        assertEquals(tileEntrance(0.6f, 12), tileEntrance(0.6f, 90), 0f)
    }
}
