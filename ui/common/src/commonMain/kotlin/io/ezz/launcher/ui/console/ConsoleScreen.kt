package io.ezz.launcher.ui.console

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.ui.components.EzzBadge
import io.ezz.launcher.ui.components.EzzBadgeVariant
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.ToastManager
import io.ezz.launcher.ui.components.ToastType
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.AppViewModel
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun ConsoleScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val colors = EzzTheme.colors
    val logs by viewModel.logs.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new log
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(32.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Terminal, contentDescription = null, tint = colors.primary, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Game Console",
                        color = colors.textPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    EzzBadge(
                        text = "${logs.size} LINES",
                        variant = EzzBadgeVariant.NEUTRAL
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Live STDOUT and STDERR stream from Minecraft process",
                    color = colors.textSecondary,
                    fontSize = 14.sp
                )
            }

            Row {
                EzzButton(
                    text = "Copy Logs",
                    onClick = {
                        val text = logs.joinToString("\n") { it.message }
                        try {
                            val selection = StringSelection(text)
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                            ToastManager.show("Logs Copied", "${logs.size} log lines copied to clipboard", ToastType.SUCCESS)
                        } catch (e: Exception) {
                            ToastManager.show("Copy Failed", e.message, ToastType.ERROR)
                        }
                    },
                    variant = EzzButtonVariant.SECONDARY,
                    size = EzzButtonSize.MEDIUM,
                    icon = Icons.Default.ContentCopy
                )

                Spacer(modifier = Modifier.width(12.dp))

                EzzButton(
                    text = "Clear",
                    onClick = { viewModel.clearLogs() },
                    variant = EzzButtonVariant.DANGER,
                    size = EzzButtonSize.MEDIUM,
                    icon = Icons.Default.Clear
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Console Terminal Box
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF06090F))
                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            if (logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No logs yet. Launch an instance to view live output.",
                        color = colors.textMuted,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(logs) { log ->
                        val logColor = when {
                            log.isError -> colors.danger
                            log.message.startsWith("===") -> colors.primary
                            log.message.contains("WARN", ignoreCase = true) -> colors.warning
                            else -> colors.textSecondary
                        }
                        Text(
                            text = log.message,
                            color = logColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
