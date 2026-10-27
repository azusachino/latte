package com.azusachino.latte.data.download

object DownloadNotificationPolicy {
    /**
     * Android's NotificationManagerService enforces a strict cap of 50 active notifications
     * per package. SystemUI also caps expanded child notifications in a group to 8.
     * We keep at most 8 completed download notifications in the notification shade,
     * ensuring plenty of headroom for ongoing progress notifications and the group summary.
     */
    const val MAX_COMPLETED_NOTIFICATIONS = 8

    /**
     * Stable notification ID for the group summary notification.
     */
    const val SUMMARY_NOTIFICATION_ID = 0x4C415454 // "LATT" (1279341652)

    data class NotificationItem(
        val id: Int,
        val tag: String? = null,
        val postTime: Long = 0L,
        val isOngoing: Boolean = false,
        val group: String? = null,
        val channelId: String? = null,
    )

    /**
     * Determines which older completed notifications should be cancelled to ensure
     * that total completed notifications after posting a new one do not exceed [maxKeep].
     *
     * @param activeItems all currently active notifications for the app
     * @param targetGroup the group identifier to filter (e.g. "latte_downloads")
     * @param targetChannel the channel ID to filter (e.g. "latte_downloads")
     * @param maxKeep the maximum number of completed notifications to keep (default 8)
     * @return the list of NotificationItems that should be cancelled
     */
    fun findNotificationsToPrune(
        activeItems: List<NotificationItem>,
        targetGroup: String,
        targetChannel: String,
        maxKeep: Int = MAX_COMPLETED_NOTIFICATIONS,
    ): List<NotificationItem> {
        val completed = activeItems.filter { item ->
            item.id != SUMMARY_NOTIFICATION_ID &&
                !item.isOngoing &&
                (item.group == targetGroup || item.channelId == targetChannel)
        }.sortedBy { it.postTime }

        // We want at most (maxKeep - 1) before adding a new notification, so total doesn't exceed maxKeep.
        val excess = completed.size - (maxKeep - 1)
        if (excess <= 0) return emptyList()

        return completed.take(excess)
    }

    /**
     * Calculates the summary count of child notifications in the group.
     */
    fun countGroupChildren(
        activeItems: List<NotificationItem>,
        targetGroup: String,
        targetChannel: String,
    ): Int {
        return activeItems.count { item ->
            item.id != SUMMARY_NOTIFICATION_ID &&
                (item.group == targetGroup || item.channelId == targetChannel)
        }
    }

    /**
     * Produces human-readable summary text for the group summary notification.
     */
    fun summaryText(childCount: Int): String {
        return if (childCount == 1) "1 download" else "$childCount downloads"
    }
}
