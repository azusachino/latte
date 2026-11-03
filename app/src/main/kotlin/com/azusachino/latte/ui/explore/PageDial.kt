package com.azusachino.latte.ui.explore

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.azusachino.latte.ui.common.DialogBackdropBlur
import com.azusachino.latte.ui.common.glassDialogColor
import com.azusachino.latte.ui.theme.glassPillSelection
import com.azusachino.latte.ui.theme.glassSurface
import com.azusachino.latte.ui.theme.pressScale
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** A key on the page dial's keypad. */
internal sealed interface DialKey {
    data class Digit(val value: Int) : DialKey
    data object Backspace : DialKey
}

/** Typed page text after [key]: no leading zero, at most [maxDigits] digits. */
internal fun dialInput(text: String, key: DialKey, maxDigits: Int = MAX_DIAL_DIGITS): String = when (key) {
    DialKey.Backspace -> text.dropLast(1)
    is DialKey.Digit -> when {
        text.isEmpty() && key.value == 0 -> text
        text.length >= maxDigits -> text
        else -> text + key.value
    }
}

/** Quick-jump targets from [current]: first page and ±1/±10 steps that stay within 1..[maxPage]. */
internal fun quickJumps(current: Int, maxPage: Int): List<Pair<String, Int>> = buildList {
    if (current > 1) add("First" to 1)
    listOf(-10, -1, 1, 10).forEach { step ->
        val target = current + step
        if (target in 1..maxPage) add((if (step > 0) "+$step" else "$step") to target)
    }
    if (maxPage < Int.MAX_VALUE && current < maxPage) add("Last" to maxPage)
}

/**
 * Page dial: a glass panel over the blurred feed with a rolling page readout,
 * quick jumps, a scrubber when the last page is known, and its own keypad.
 */
@Composable
internal fun PageDialDialog(
    currentPage: Int,
    maxPage: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    // Typing replaces the readout; untouched it shows the current page, dimmed.
    val target = text.toIntOrNull()
    val valid = target != null && target in 1..maxPage && target != currentPage
    val outOfRange = target != null && target !in 1..maxPage
    val shake = remember { Animatable(0f) }

    fun press(key: DialKey) {
        haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        val next = dialInput(text, key)
        text = next
        val value = next.toIntOrNull()
        if (value != null && value > maxPage) {
            scope.launch {
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                shake.snapTo(0f)
                shake.animateTo(0f, spring(dampingRatio = 0.25f, stiffness = 1200f), initialVelocity = 2400f)
            }
        }
    }

    fun go() {
        val page = target ?: return
        if (!valid) return
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        onConfirm(page)
    }

    Dialog(onDismissRequest = onDismiss) {
        DialogBackdropBlur()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(glassDialogColor())
                .padding(horizontal = 20.dp, vertical = 22.dp),
        ) {
            Text(
                text = "Jump to page",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))

            val readoutColor by animateColorAsState(
                targetValue = when {
                    outOfRange -> MaterialTheme.colorScheme.error
                    target == null -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    else -> MaterialTheme.colorScheme.onSurface
                },
                label = "dialReadout",
            )
            RollingNumber(
                text = text.ifEmpty { currentPage.toString() },
                color = readoutColor,
                modifier = Modifier
                    .graphicsLayer { translationX = shake.value }
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = if (target == null) "Page $currentPage" else "Page $target"
                    },
            )
            Text(
                text = when {
                    outOfRange -> "Pages go up to $maxPage"
                    maxPage < Int.MAX_VALUE -> "Now on $currentPage · 1 to $maxPage"
                    else -> "Now on $currentPage"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (outOfRange) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(14.dp))
            QuickJumpRow(
                jumps = quickJumps(currentPage, maxPage),
                selected = target,
                onPick = { page ->
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    text = page.toString()
                },
            )

            if (maxPage in 2..MAX_SCRUB_PAGES) {
                Spacer(Modifier.height(6.dp))
                var lastTick by remember { mutableStateOf(target ?: currentPage) }
                Slider(
                    value = (target ?: currentPage).coerceIn(1, maxPage).toFloat(),
                    onValueChange = { value ->
                        val page = value.roundToInt()
                        if (page != lastTick) {
                            lastTick = page
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        }
                        text = page.toString()
                    },
                    valueRange = 1f..maxPage.toFloat(),
                    colors = SliderDefaults.colors(
                        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    ),
                )
            }

            Spacer(Modifier.height(10.dp))
            DialKeypad(
                canGo = valid,
                onKey = ::press,
                onGo = ::go,
            )
        }
    }
}

/** Digits that roll vertically as they change: up when the number grows, down when it shrinks. */
@Composable
private fun RollingNumber(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier.height(76.dp), verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { index, char ->
            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { h -> if (up) h else -h } + fadeIn()) togetherWith
                        (slideOutVertically(tween(160)) { h -> if (up) -h else h } + fadeOut(tween(120)))
                },
                label = "dialDigit$index",
            ) { digit ->
                Text(
                    text = digit.toString(),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun QuickJumpRow(
    jumps: List<Pair<String, Int>>,
    selected: Int?,
    onPick: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth(),
    ) {
        jumps.forEach { (label, page) ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .pressScale()
                    .glassPillSelection(selected = selected == page)
                    .clickable(onClickLabel = "Page $page") { onPick(page) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun DialKeypad(
    canGo: Boolean,
    onKey: (DialKey) -> Unit,
    onGo: () -> Unit,
) {
    val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { digit ->
                    DialKeyButton(label = digit.toString(), onClick = { onKey(DialKey.Digit(digit)) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DialKeyButton(
                onClick = { onKey(DialKey.Backspace) },
                contentDescription = "Delete",
            ) {
                Text("\u232B", fontSize = 22.sp)
            }
            DialKeyButton(label = "0", onClick = { onKey(DialKey.Digit(0)) })
            DialKeyButton(
                onClick = onGo,
                enabled = canGo,
                primary = true,
                contentDescription = "Go",
            ) {
                Text("Go", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun DialKeyButton(
    onClick: () -> Unit,
    label: String? = null,
    enabled: Boolean = true,
    primary: Boolean = false,
    contentDescription: String? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    val container = when {
        primary && enabled -> MaterialTheme.colorScheme.primary
        primary -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        else -> Color.Transparent
    }
    val contentColor = when {
        primary && enabled -> MaterialTheme.colorScheme.onPrimary
        primary -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(76.dp)
            .aspectRatio(1.45f)
            .pressScale(enabled = enabled)
            .then(if (primary) Modifier.clip(shape).background(container, shape) else Modifier.glassSurface(shape = shape))
            .clickable(enabled = enabled) { onClick() }
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides contentColor,
        ) {
            if (content != null) {
                content()
            } else if (label != null) {
                Text(label, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = contentColor)
            }
        }
    }
}

private const val MAX_DIAL_DIGITS = 6

/** Above this many pages a slider step is too fine to land on a page by hand. */
private const val MAX_SCRUB_PAGES = 500
