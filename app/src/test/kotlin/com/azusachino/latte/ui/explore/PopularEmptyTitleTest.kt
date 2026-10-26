package com.azusachino.latte.ui.explore

import com.azusachino.latte.plugin.PluginFeedPeriod
import org.junit.Assert.assertEquals
import org.junit.Test

class PopularEmptyTitleTest {

    @Test
    fun eachPeriodNamesItsWindow() {
        assertEquals("No posts for this day", popularEmptyTitle(PluginFeedPeriod.DAY))
        assertEquals("No posts for this week", popularEmptyTitle(PluginFeedPeriod.WEEK))
        assertEquals("No posts for this month", popularEmptyTitle(PluginFeedPeriod.MONTH))
        assertEquals("No posts for this year", popularEmptyTitle(PluginFeedPeriod.YEAR))
    }
}
