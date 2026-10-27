package com.azusachino.latte.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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

/** Most toasts visible at once; the oldest is pushed out when a newer one arrives. */
internal const val MAX_VISIBLE_TOASTS = 3

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
 * Visible notification stack, newest first, capped at [MAX_VISIBLE_TOASTS].
 * Plain state with no timers or Compose; [ToastManager] owns the per-toast timers.
 */
internal class ToastQueue {
    private val _visible = MutableStateFlow<List<ToastMessage>>(emptyList())
    val visible: StateFlow<List<ToastMessage>> = _visible.asStateFlow()

    private var lastId = 0L

    /**
     * Shows a message at the front of the stack. A duplicate of a visible toast is not added again:
     * the existing toast moves to the front and is returned so its timer restarts.
     * The oldest toast is dropped when the stack is full.
     */
    fun enqueue(
        message: String,
        type: ToastType,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ): ToastMessage {
        val current = _visible.value
        val duplicate = current.firstOrNull { it.message == message && it.type == type }
        if (duplicate != null) {
            _visible.value = listOf(duplicate) + current.filterNot { it.id == duplicate.id }
            return duplicate
        }

        val toast = ToastMessage(
            id = ++lastId,
            message = message,
            type = type,
            actionLabel = actionLabel,
            onAction = onAction,
        )
        _visible.value = (listOf(toast) + current).take(MAX_VISIBLE_TOASTS)
        return toast
    }

    /** Removes [id] from the stack. Returns false when it is not visible, so a stale timer or tap is a no-op. */
    fun dismiss(id: Long): Boolean {
        val current = _visible.value
        val remaining = current.filterNot { it.id == id }
        if (remaining.size == current.size) return false
        _visible.value = remaining
        return true
    }

    /** Runs the action of [id] if it is visible, then removes only that toast. */
    fun invokeAction(id: Long): Boolean {
        val toast = _visible.value.firstOrNull { it.id == id } ?: return false
        toast.onAction?.invoke()
        return dismiss(id)
    }
}

object ToastManager {
    private val queue = ToastQueue()
    val visible: StateFlow<List<ToastMessage>> = queue.visible

    private val scope = CoroutineScope(Dispatchers.Main)

    // One timer per visible toast, keyed by toast id.
    private val timers = mutableMapOf<Long, Job>()

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
        startTimer(queue.enqueue(message, type, actionLabel, onAction))
        pruneTimers()
    }

    fun dismiss(id: Long) {
        queue.dismiss(id)
        pruneTimers()
    }

    fun invokeAction(id: Long) {
        queue.invokeAction(id)
        pruneTimers()
    }

    fun showWarning(message: String) = show(message, ToastType.WARNING)
    fun showSuccess(message: String) = show(message, ToastType.SUCCESS)
    fun showInfo(message: String) = show(message, ToastType.INFO)
    fun showError(message: String) = show(message, ToastType.ERROR)

    private fun startTimer(toast: ToastMessage) {
        timers.remove(toast.id)?.cancel()
        timers[toast.id] = scope.launch {
            delay(toast.durationMs)
            timers.remove(toast.id)
            dismiss(toast.id)
        }
    }

    /** Cancels the timers of toasts that left the stack, whether dismissed, actioned or pushed out. */
    private fun pruneTimers() {
        val live = queue.visible.value.map { it.id }
        timers.keys.filterNot { it in live }.forEach { timers.remove(it)?.cancel() }
    }
}


// Dynamic Island look: a near-black blurred pill, white text in both themes.
private val IslandTint = Color(0xCC101215)

// Stack geometry: pills form a vertical list, newest at the top, with this gap between them.
private val StackGapDp = 6.dp

// Each step back in the stack scales the pill down and fades it a little more.
private const val StackScaleStepPerDepth = 0.04f
private const val StackAlphaStepPerDepth = 0.1f

// How far a pill travels when it enters from above or leaves upward.
private val StackSlideDp = 40.dp

@Composable
fun ToastHost(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val visible by ToastManager.visible.collectAsStateWithLifecycle()
    // Dismissed toasts stay in the stack, flagged as leaving, until their exit animation ends.
    val stack = remember { mutableStateListOf<ToastMessage>() }
    val leavingIds = remember { mutableStateSetOf<Long>() }
    // Measured height of each pill in px, so each pill can sit below the ones in front of it.
    val heights = remember { mutableStateMapOf<Long, Int>() }
    LaunchedEffect(visible) {
        val liveIds = visible.map { it.id }.toSet()
        val previous = stack.toList()
        leavingIds.addAll(previous.filter { it.id !in liveIds }.map { it.id })
        // A leaving pill keeps its slot until it has faded, so the pills behind it do not jump.
        val merged = visible.toMutableList()
        previous.forEachIndexed { index, toast ->
            if (toast.id !in liveIds) merged.add(index.coerceAtMost(merged.size), toast)
        }
        stack.clear()
        stack.addAll(merged)
    }

    val targetTopInset by ToastManager.topInset.collectAsStateWithLifecycle()
    val topInset by animateDpAsState(targetValue = targetTopInset, label = "toastTopInset")

    val density = LocalDensity.current
    val gapPx = with(density) { StackGapDp.toPx() }
    // Top edge of each pill: the height of the pills in front, each as scaled when drawn, plus the gap.
    val tops = mutableListOf<Float>()
    var nextTop = 0f
    stack.forEachIndexed { index, toast ->
        tops += nextTop
        nextTop += (heights[toast.id] ?: 0) * stackScale(index.toFloat()) + gapPx
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            // The stack is taller than the pill, so the host must cover it or the lower pills are not hit.
            .fillMaxHeight()
            .statusBarsPadding()
            .padding(top = 8.dp + topInset, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        stack.forEachIndexed { index, toast ->
            key(toast.id) {
                ToastStackItem(
                    toast = toast,
                    depth = index,
                    top = tops[index],
                    leaving = toast.id in leavingIds,
                    hazeState = hazeState,
                    onMeasured = { heights[toast.id] = it },
                    onExited = {
                        stack.removeAll { it.id == toast.id }
                        leavingIds.remove(toast.id)
                        heights.remove(toast.id)
                    },
                )
            }
        }
    }
}

/** Scale of the pill at [depth]: the newest is full size, each step back is a little smaller. */
private fun stackScale(depth: Float): Float = (1f - StackScaleStepPerDepth * depth).coerceAtLeast(0.5f)

/**
 * One pill in the vertical stack. [depth] 0 is the newest pill, at the top; larger depths sit lower,
 * smaller and fainter. Entering slides it down from above, leaving fades it out upward and then
 * calls [onExited] so the host can drop it.
 */
@Composable
private fun BoxScope.ToastStackItem(
    toast: ToastMessage,
    depth: Int,
    top: Float,
    leaving: Boolean,
    hazeState: HazeState,
    onMeasured: (Int) -> Unit,
    onExited: () -> Unit,
) {
    // 0 while hidden, 1 when fully shown.
    val presence = remember { Animatable(0f) }
    LaunchedEffect(leaving) {
        if (leaving) {
            presence.animateTo(0f, tween(durationMillis = 180))
            onExited()
        } else {
            presence.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    // Animated so the rest of the stack glides up or down as pills are added and removed.
    val animatedDepth by animateFloatAsState(
        targetValue = depth.toFloat(),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "toastStackDepth",
    )
    val animatedTop by animateFloatAsState(
        targetValue = top,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "toastStackTop",
    )
    val density = LocalDensity.current
    val slidePx = with(density) { StackSlideDp.toPx() }

    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .onSizeChanged { onMeasured(it.height) }
            .graphicsLayer {
                transformOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 0f)
                val scale = stackScale(animatedDepth)
                scaleX = scale
                scaleY = scale
                alpha = presence.value * (1f - StackAlphaStepPerDepth * animatedDepth).coerceIn(0f, 1f)
                translationY = animatedTop + (presence.value - 1f) * slidePx
            },
    ) {
        ToastBulletin(
            toast = toast,
            hazeState = hazeState,
            onDismiss = { ToastManager.dismiss(toast.id) },
            onAction = { ToastManager.invokeAction(toast.id) },
        )
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
        ToastContent(toast = toast, onAction = onAction)
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
