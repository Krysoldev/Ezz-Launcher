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
            .background(Color(0xFF050505))
            .padding(26.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "CONSOLE",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    EzzBadge(
                        text = "${logs.size} LINES",
                        variant = EzzBadgeVariant.NEUTRAL
                    )
                }
                Text(
                    text = "Live STDOUT and STDERR stream from Minecraft process execution",
                    color = Color(0xFF888888),
                    fontSize = 12.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                EzzButton(
                    text = "Clear",
                    onClick = { viewModel.clearLogs() },
                    variant = EzzButtonVariant.DANGER,
                    size = EzzButtonSize.MEDIUM,
                    icon = Icons.Default.Clear
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Console Terminal Box
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF080808))
                .border(1.dp, Color(0xFF202020), RoundedCornerShape(8.dp))
                .padding(14.dp)
        ) {
            if (logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No logs yet. Launch an instance to view live output stream.",
                        color = Color(0xFF555555),
                        fontSize = 12.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(logs) { log ->
                        val logColor = when {
                            log.isError -> Color(0xFFEF4444)
                            log.message.startsWith("===") -> Color.White
                            log.message.contains("WARN", ignoreCase = true) -> Color(0xFFF59E0B)
                            else -> Color(0xFFB0B0B0)
                        }
                        Text(
                            text = log.message,
                            color = logColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
