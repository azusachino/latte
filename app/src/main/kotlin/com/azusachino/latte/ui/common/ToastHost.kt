package com.azusachino.latte.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.azusachino.latte.ui.theme.glassBackdrop
import dev.chrisbanes.haze.HazeState
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

    private val _topInset = MutableStateFlow(0.dp)

    /** Extra space the pill clears below the status bar, set by screens with floating top controls. */
    val topInset: StateFlow<Dp> = _topInset.asStateFlow()
    private var topInsetOwner: Any? = null

    fun setTopInset(owner: Any, inset: Dp) {
        topInsetOwner = owner
        _topInset.value = inset
    }

    /** Resets the inset only if [owner] set it, so a screen leaving composition cannot clear a newer one. */
    fun clearTopInset(owner: Any) {
        if (topInsetOwner !== owner) return
        topInsetOwner = null
        _topInset.value = 0.dp
    }

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


// Dynamic Island look: a near-black blurred pill, white text in both themes.
private val IslandTint = Color(0xCC101215)

@Composable
fun ToastHost(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val current by ToastManager.current.collectAsState()
    // Keeps the last toast composed so its exit animation still has content to draw.
    var lastToast by remember { mutableStateOf<ToastMessage?>(null) }
    current?.let { lastToast = it }
    val targetTopInset by ToastManager.topInset.collectAsState()
    val topInset by animateDpAsState(targetValue = targetTopInset, label = "toastTopInset")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 8.dp + topInset, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = current != null,
            enter = slideInVertically(
                animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
                initialOffsetY = { -it * 2 },
            ) + fadeIn() + scaleIn(
                animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
                initialScale = 0.9f,
            ),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it * 2 }),
        ) {
            lastToast?.let { toast ->
                ToastBulletin(
                    toast = toast,
                    hazeState = hazeState,
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
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onAction: () -> Unit,
) {
    // Drag offsets; reset to 0 when the drag ends short of the dismiss threshold.
    var dragX by remember(toast.id) { mutableFloatStateOf(0f) }
    var dragY by remember(toast.id) { mutableFloatStateOf(0f) }
    val animatedDragX by animateFloatAsState(targetValue = dragX, label = "toastDragX")
    val animatedDragY by animateFloatAsState(targetValue = dragY, label = "toastDragY")
    val density = LocalDensity.current
    val dismissUpPx = with(density) { 40.dp.toPx() }
    val dismissSidePx = with(density) { 96.dp.toPx() }

    Box(
        modifier = Modifier
            .widthIn(min = 160.dp)
            .offset { IntOffset(animatedDragX.roundToInt(), animatedDragY.roundToInt()) }
            .glassBackdrop(
                hazeState = hazeState,
                backgroundColor = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(percent = 50),
                dark = true,
                tint = IslandTint,
            )
            .pointerInput(toast.id) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragX += dragAmount.x
                        dragY += dragAmount.y
                    },
                    onDragEnd = {
                        if (dragY < -dismissUpPx || abs(dragX) > dismissSidePx) {
                            onDismiss()
                        } else {
                            dragX = 0f
                            dragY = 0f
                        }
                    },
                    onDragCancel = {
                        dragX = 0f
                        dragY = 0f
                    },
                )
            }
            .clickable(onClick = onDismiss)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        // Cross-fades the content when a queued toast replaces this one.
        AnimatedContent(
            targetState = toast,
            contentKey = { it.id },
            label = "toastContent",
        ) { visible ->
            ToastContent(toast = visible, onAction = onAction)
        }
    }
}

@Composable
private fun ToastContent(
    toast: ToastMessage,
    onAction: () -> Unit,
) {
    val (icon, circleColor, glyphColor) = when (toast.type) {
        ToastType.SUCCESS -> Triple(Icons.Default.CheckCircle, Color(0xFF4CAF50), Color.White)
        ToastType.WARNING -> Triple(Icons.Default.Warning, Color(0xFFFFA000), Color(0xFF101215))
        ToastType.ERROR -> Triple(Icons.Default.Warning, MaterialTheme.colorScheme.error, Color.White)
        ToastType.INFO -> Triple(Icons.Default.Info, MaterialTheme.colorScheme.primary, Color.White)
    }

    // The success check springs in on enter; warning and error pulse once.
    val iconScale = remember(toast.id) { Animatable(if (toast.type == ToastType.SUCCESS) 0f else 1f) }
    LaunchedEffect(toast.id) {
        when (toast.type) {
            ToastType.SUCCESS -> iconScale.animateTo(
                1f,
                spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
            )
            ToastType.WARNING, ToastType.ERROR -> {
                iconScale.animateTo(1.2f, tween(durationMillis = 120))
                iconScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
            }
            ToastType.INFO -> Unit
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer {
                    scaleX = iconScale.value
                    scaleY = iconScale.value
                    alpha = iconScale.value.coerceIn(0f, 1f)
                }
                .background(circleColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = glyphColor,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = toast.message,
            modifier = Modifier.weight(1f, fill = false),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        toast.actionLabel?.let { label ->
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(percent = 50),
                    )
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
