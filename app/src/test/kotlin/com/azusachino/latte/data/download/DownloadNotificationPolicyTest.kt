package com.azusachino.latte.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadNotificationPolicyTest {
    private val group = "latte_downloads"
    private val channel = "latte_downloads"

    private fun item(
        id: Int,
        postTime: Long,
        isOngoing: Boolean = false,
        groupName: String? = group,
        channelName: String? = channel,
    ) = DownloadNotificationPolicy.NotificationItem(
        id = id,
        postTime = postTime,
        isOngoing = isOngoing,
        group = groupName,
        channelId = channelName,
    )

    @Test
    fun doesNotPruneWhenUnderCapacity() {
        val items = (1..5).map { item(id = it, postTime = it.toLong() * 1000) }
        val toPrune = DownloadNotificationPolicy.findNotificationsToPrune(
            activeItems = items,
            targetGroup = group,
            targetChannel = channel,
            maxKeep = 8,
        )
        assertTrue(toPrune.isEmpty())
    }

    @Test
    fun doesNotPruneWhenAtThresholdMinusOne() {
        val items = (1..7).map { item(id = it, postTime = it.toLong() * 1000) }
        val toPrune = DownloadNotificationPolicy.findNotificationsToPrune(
            activeItems = items,
            targetGroup = group,
            targetChannel = channel,
            maxKeep = 8,
        )
        assertTrue(toPrune.isEmpty())
    }

    @Test
    fun prunesOldestSingleItemWhenAtCapacity() {
        val items = (1..8).map { item(id = it, postTime = it.toLong() * 1000) }
        val toPrune = DownloadNotificationPolicy.findNotificationsToPrune(
            activeItems = items,
            targetGroup = group,
            targetChannel = channel,
            maxKeep = 8,
        )
        assertEquals(1, toPrune.size)
        assertEquals(1, toPrune.first().id)
    }

    @Test
    fun prunesExcessToRestoreHeadroomWhen49NotificationsAccumulated() {
        // User scenario: 49 completed notifications stacked in tray.
        val items = (1..49).map { item(id = it, postTime = it.toLong() * 1000) }
        val toPrune = DownloadNotificationPolicy.findNotificationsToPrune(
            activeItems = items,
            targetGroup = group,
            targetChannel = channel,
            maxKeep = 8,
        )
        // 49 - (8 - 1) = 42 excess items must be pruned, leaving 7 slots so the 50th can enter as 8th.
        assertEquals(42, toPrune.size)
        assertEquals(1, toPrune.first().id)
        assertEquals(42, toPrune.last().id)
    }

    @Test
    fun ignoresOngoingNotificationsAndSummary() {
        val ongoingItem = item(id = 99, postTime = 100, isOngoing = true)
        val summaryItem = item(id = DownloadNotificationPolicy.SUMMARY_NOTIFICATION_ID, postTime = 200)
        val otherChannelItem = item(id = 101, postTime = 300, groupName = "other", channelName = "other")
        val completedItems = (1..8).map { item(id = it, postTime = (it + 500).toLong()) }

        val active = listOf(ongoingItem, summaryItem, otherChannelItem) + completedItems
        val toPrune = DownloadNotificationPolicy.findNotificationsToPrune(
            activeItems = active,
            targetGroup = group,
            targetChannel = channel,
            maxKeep = 8,
        )
        assertEquals(1, toPrune.size)
        assertEquals(1, toPrune.first().id)
    }

    @Test
    fun countsGroupChildrenExcludingSummary() {
        val items = listOf(
            item(id = 1, postTime = 100),
            item(id = 2, postTime = 200, isOngoing = true),
            item(id = DownloadNotificationPolicy.SUMMARY_NOTIFICATION_ID, postTime = 300),
            item(id = 999, postTime = 400, groupName = "other", channelName = "other"),
        )
        val count = DownloadNotificationPolicy.countGroupChildren(
            activeItems = items,
            targetGroup = group,
            targetChannel = channel,
        )
        assertEquals(2, count)
    }

    @Test
    fun formatsSummaryTextProperly() {
        assertEquals("1 download", DownloadNotificationPolicy.summaryText(1))
        assertEquals("5 downloads", DownloadNotificationPolicy.summaryText(5))
        assertEquals("0 downloads", DownloadNotificationPolicy.summaryText(0))
    }
}
