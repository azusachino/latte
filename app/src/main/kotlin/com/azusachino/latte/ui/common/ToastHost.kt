package com.azusachino.latte.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ToastType {
    INFO, SUCCESS, WARNING, ERROR
}

data class ToastMessage(
    val id: Long = System.nanoTime(),
    val message: String,
    val type: ToastType = ToastType.INFO,
)

object ToastManager {
    private val _messages = MutableStateFlow<List<ToastMessage>>(emptyList())
    val messages: StateFlow<List<ToastMessage>> = _messages.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)

    fun show(message: String, type: ToastType = ToastType.INFO, durationMs: Long = 2800L) {
        val toast = ToastMessage(message = message, type = type)
        _messages.value = _messages.value + toast

        scope.launch {
            delay(durationMs)
            _messages.value = _messages.value.filterNot { it.id == toast.id }
        }
    }

    fun showWarning(message: String) = show(message, ToastType.WARNING)
    fun showSuccess(message: String) = show(message, ToastType.SUCCESS)
    fun showInfo(message: String) = show(message, ToastType.INFO)
}

@Composable
fun ToastHost(modifier: Modifier = Modifier) {
    val messages by ToastManager.messages.collectAsState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 96.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            messages.takeLast(4).forEach { toast ->
                ToastItem(toast = toast)
            }
        }
    }
}

@Composable
private fun ToastItem(toast: ToastMessage) {
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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
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
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
        }
    }
}
