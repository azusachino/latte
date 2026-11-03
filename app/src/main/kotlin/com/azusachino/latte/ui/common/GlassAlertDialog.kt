package com.azusachino.latte.ui.common

import android.view.View
import android.view.WindowManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.azusachino.latte.ui.theme.GlassDefaults

/**
 * [AlertDialog] as glass. A dialog is its own window, so in-app Haze blur cannot
 * reach behind it; this uses the platform's cross-window blur-behind instead and
 * a translucent container. Where the device disables cross-window blur, the
 * window just dims as before.
 */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            DialogBackdropBlur()
            confirmButton()
        },
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        containerColor = if (isDark) GlassDefaults.DarkDialog else GlassDefaults.LightDialog,
    )
}

/** Blurs the app behind the enclosing dialog window. Call from inside dialog content. */
@Composable
private fun DialogBackdropBlur() {
    val view = LocalView.current
    val radiusPx = with(LocalDensity.current) { BLUR_RADIUS.roundToPx() }
    SideEffect {
        val window = view.dialogWindowProvider()?.window ?: return@SideEffect
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.attributes = window.attributes.also {
            it.blurBehindRadius = radiusPx
            it.dimAmount = DIM_AMOUNT
        }
    }
}

private fun View.dialogWindowProvider(): DialogWindowProvider? =
    generateSequence<Any>(this) { (it as? View)?.parent }
        .take(4)
        .firstNotNullOfOrNull { it as? DialogWindowProvider }

private val BLUR_RADIUS = 24.dp
private const val DIM_AMOUNT = 0.25f
