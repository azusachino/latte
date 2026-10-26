package com.azusachino.latte.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azusachino.latte.ui.theme.glassSurface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ToastType {
    INFO, SUCCESS, WARNING, ERROR
}

data class ToastMessage(
    val id: Long,
    val message: String,
    val type: ToastType = ToastType.INFO,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
) {
    val durationMs: Long
        get() {
            val base = when (type) {
                ToastType.INFO, ToastType.SUCCESS -> 2_500L
                ToastType.WARNING -> 4_000L
                ToastType.ERROR -> 5_000L
            }
            return if (actionLabel != null) maxOf(base, 5_000L) else base
        }
}

/**
 * Single-bulletin queue: one visible toast at a time, the rest wait in order.
 * Plain state with no timers or Compose; [ToastManager] owns the timers.
 */
internal class ToastQueue {
    private val _current = MutableStateFlow<ToastMessage?>(null)
    val current: StateFlow<ToastMessage?> = _current.asStateFlow()

    private val pending = ArrayDeque<ToastMessage>()
    private var lastId = 0L

    /**
     * Shows or queues a message. Returns the visible toast whose timer should (re)start,
     * or null when the message was queued or dropped as a duplicate of a queued one.
     */
    fun enqueue(
        message: String,
        type: ToastType,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ): ToastMessage? {
        val visible = _current.value
        if (visible != null && visible.message == message && visible.type == type) return visible
        if (pending.any { it.message == message && it.type == type }) return null

        val toast = ToastMessage(
            id = ++lastId,
            message = message,
            type = type,
            actionLabel = actionLabel,
            onAction = onAction,
        )
        if (visible != null) {
            pending.addLast(toast)
            return null
        }
        _current.value = toast
        return toast
    }

    /** Dismisses [id] if it is the visible toast. Returns true when the visible toast changed. */
    fun dismiss(id: Long): Boolean {
        if (_current.value?.id != id) return false
        _current.value = pending.removeFirstOrNull()
        return true
    }

    /** Runs the visible toast's action, then dismisses it. Returns true when the visible toast changed. */
    fun invokeAction(id: Long): Boolean {
        val toast = _current.value?.takeIf { it.id == id } ?: return false
        toast.onAction?.invoke()
        return dismiss(id)
    }
}

object ToastManager {
    private val queue = ToastQueue()
    val current: StateFlow<ToastMessage?> = queue.current

    private val scope = CoroutineScope(Dispatchers.Main)
    private var timer: Job? = null

    fun show(
        message: String,
        type: ToastType = ToastType.INFO,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ) {
        queue.enqueue(message, type, actionLabel, onAction)?.let(::restartTimer)
    }

    fun dismiss(id: Long) {
        if (queue.dismiss(id)) restartTimer(queue.current.value)
    }

    fun invokeAction(id: Long) {
        if (queue.invokeAction(id)) restartTimer(queue.current.value)
    }

    fun showWarning(message: String) = show(message, ToastType.WARNING)
    fun showSuccess(message: String) = show(message, ToastType.SUCCESS)
    fun showInfo(message: String) = show(message, ToastType.INFO)
    fun showError(message: String) = show(message, ToastType.ERROR)

    private fun restartTimer(toast: ToastMessage?) {
        timer?.cancel()
        timer = toast?.let {
            scope.launch {
                delay(it.durationMs)
                dismiss(it.id)
            }
        }
    }
}

@Composable
fun ToastHost(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 144.dp,
) {
    val current by ToastManager.current.collectAsState()
    // Keeps the last toast composed so its exit animation still has content to draw.
    var lastToast by remember { mutableStateOf<ToastMessage?>(null) }
    current?.let { lastToast = it }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = bottomPadding),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = current != null,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
        ) {
            lastToast?.let { toast ->
                ToastBulletin(
                    toast = toast,
                    onDismiss = { ToastManager.dismiss(toast.id) },
                    onAction = { ToastManager.invokeAction(toast.id) },
                )
            }
        }
    }
}

@Composable
private fun ToastBulletin(
    toast: ToastMessage,
    onDismiss: () -> Unit,
    onAction: () -> Unit,
) {
    val (icon, iconColor) = when (toast.type) {
        ToastType.SUCCESS -> Pair(
            Icons.Default.CheckCircle,
            Color(0xFF4CAF50),
        )
        ToastType.WARNING -> Pair(
            Icons.Default.Warning,
            Color(0xFFFFA000),
        )
        ToastType.ERROR -> Pair(
            Icons.Default.Warning,
            MaterialTheme.colorScheme.error,
        )
        ToastType.INFO -> Pair(
            Icons.Default.Info,
            MaterialTheme.colorScheme.primary,
        )
    }

    // Horizontal swipe offset; resets to 0 when the drag ends short of the threshold.
    var dragX by remember(toast.id) { mutableFloatStateOf(0f) }
    val animatedDragX by animateFloatAsState(targetValue = dragX, label = "toastDrag")
    val dismissThresholdPx = with(LocalDensity.current) { 96.dp.toPx() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(animatedDragX.roundToInt(), 0) }
            .glassSurface(shape = RoundedCornerShape(24.dp), isElevated = true)
            .pointerInput(toast.id) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragX += dragAmount
                    },
                    onDragEnd = {
                        if (abs(dragX) > dismissThresholdPx) {
                            onDismiss()
                        } else {
                            dragX = 0f
                        }
                    },
                    onDragCancel = { dragX = 0f },
                )
            }
            .clickable(onClick = onDismiss)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = toast.message,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        toast.actionLabel?.let { label ->
            TextButton(onClick = onAction) {
                Text(text = label, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
