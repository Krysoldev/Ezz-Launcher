package io.ezz.launcher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.ui.theme.EzzTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ToastType {
    SUCCESS,
    INFO,
    WARNING,
    ERROR
}

data class ToastMessage(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val description: String? = null,
    val type: ToastType = ToastType.INFO,
    val durationMs: Long = 4000
)

object ToastManager {
    private val _toasts = MutableStateFlow<List<ToastMessage>>(emptyList())
    val toasts: StateFlow<List<ToastMessage>> = _toasts.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)

    fun show(
        title: String,
        description: String? = null,
        type: ToastType = ToastType.INFO,
        durationMs: Long = 4000
    ) {
        val toast = ToastMessage(title = title, description = description, type = type, durationMs = durationMs)
        _toasts.value = _toasts.value + toast

        scope.launch {
            delay(durationMs)
            dismiss(toast.id)
        }
    }

    fun dismiss(id: Long) {
        _toasts.value = _toasts.value.filter { it.id != id }
    }
}

@Composable
fun EzzToastHost(
    modifier: Modifier = Modifier
) {
    val toasts by ToastManager.toasts.collectAsState()
    val colors = EzzTheme.colors

    Column(
        modifier = modifier
            .padding(24.dp)
            .width(360.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.End
    ) {
        toasts.forEach { toast ->
            val (icon, tintColor, borderColor) = when (toast.type) {
                ToastType.SUCCESS -> Triple(Icons.Default.CheckCircle, colors.accent, colors.accent.copy(alpha = 0.5f))
                ToastType.INFO -> Triple(Icons.Default.Info, colors.primary, colors.primary.copy(alpha = 0.5f))
                ToastType.WARNING -> Triple(Icons.Default.Warning, colors.warning, colors.warning.copy(alpha = 0.5f))
                ToastType.ERROR -> Triple(Icons.Default.Error, colors.danger, colors.danger.copy(alpha = 0.5f))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceVariant)
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tintColor,
                        modifier = Modifier.size(22.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = toast.title,
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (toast.description != null) {
                            Text(
                                text = toast.description,
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = colors.textMuted,
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { ToastManager.dismiss(toast.id) }
                    )
                }
            }
        }
    }
}
