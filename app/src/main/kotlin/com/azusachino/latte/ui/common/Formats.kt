package com.azusachino.latte.ui.common

import java.util.Locale

/** Human-readable file size ("20.0 MB"); empty for null/non-positive input. */
internal fun formatFileSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return ""
    val units = arrayOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", size, units[unitIndex])
}
