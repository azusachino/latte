package com.azusachino.latte.ui.common

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Returns true if animations are disabled or reduced motion is requested
 * (e.g. system animator duration scale is set to 0).
 */
fun Context.isReducedMotionEnabled(): Boolean {
    return runCatching {
        Settings.Global.getFloat(
            contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1.0f,
        ) == 0f
    }.getOrDefault(false)
}

@Composable
fun isReducedMotion(): Boolean {
    val context = LocalContext.current
    return context.isReducedMotionEnabled()
}
