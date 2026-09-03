package io.ezz.launcher.ui.manager.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import okio.Path.Companion.toPath
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.InstanceLogEntry
import io.ezz.launcher.core.model.instance.LogLine
import io.ezz.launcher.core.model.instance.LogSeverityLevel
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.viewmodel.AppViewModel
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

@Composable
fun LogsTab(
    instance: Instance,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val logs by viewModel.manageLogs.collectAsState()
    val selectedLog by viewModel.selectedLogFile.collectAsState()
    val logResult by viewModel.manageLogResult.collectAsState()
    val isLogLoading by viewModel.isLogLoading.collectAsState()
    val logLoadError by viewModel.logLoadError.collectAsState()
    val runningSessions by viewModel.runningSessions.collectAsState()

    val isRunning = runningSessions.containsKey(instance.id)
    val listState = rememberLazyListState()

    var searchQuery by remember { mutableStateOf("") }
    var levelFilter by remember { mutableStateOf("ALL") }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var showCopiedToast by remember { mutableStateOf(false) }
    var autoScrollToBottom by remember { mutableStateOf(false) }

    // Lifecycle: Cleanly manage live log watching on mount & unmount
    DisposableEffect(instance.id, isRunning) {
        if (isRunning) {
            viewModel.startLiveLogWatching(instance.id)
        }
        onDispose {
            viewModel.stopLiveLogWatching()
        }
    }

    // Auto-select latest log if not set
    LaunchedEffect(instance.id, logs) {
        if (selectedLog == null && logs.isNotEmpty()) {
            val latest = logs.firstOrNull { it.fileName == "latest.log" } ?: logs.first()
            viewModel.loadLogContent(latest)
        }
    }

    // Filter lines in memory with zero-cost virtualization
    val currentLines = logResult?.lines ?: emptyList()
    val filteredLines = remember(currentLines, searchQuery, levelFilter) {
        if (currentLines.isEmpty()) {
            emptyList()
        } else {
            currentLines.filter { line ->
                val matchesSearch = searchQuery.isBlank() || line.text.contains(searchQuery, ignoreCase = true)
                val matchesLevel = when (levelFilter) {
                    "ERROR" -> line.level == LogSeverityLevel.ERROR
                    "WARN" -> line.level == LogSeverityLevel.WARN
                    "INFO" -> line.level == LogSeverityLevel.INFO
                    "DEBUG" -> line.level == LogSeverityLevel.DEBUG
                    else -> true
                }
                matchesSearch && matchesLevel
            }
        }
    }

    // Auto-scroll to bottom when new live lines arrive if enabled
    LaunchedEffect(filteredLines.size, autoScrollToBottom) {
        if (autoScrollToBottom && filteredLines.isNotEmpty()) {
            listState.scrollToItem(filteredLines.lastIndex)
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Toolbar: File selector + Search + Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Log File Selector Dropdown
                Box {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF101318))
                            .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(8.dp))
                            .clickable { isDropdownExpanded = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                if (selectedLog?.isCrashReport == true) Icons.Default.Warning else Icons.Default.Description,
                                contentDescription = null,
                                tint = if (selectedLog?.isCrashReport == true) Color(0xFFEF4444) else Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = selectedLog?.fileName ?: if (logs.isEmpty()) "No Logs" else "Select Log File",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (logs.isNotEmpty()) {
                        DropdownMenu(
                            expanded = isDropdownExpanded,
                            onDismissRequest = { isDropdownExpanded = false },
                            modifier = Modifier
                                .background(Color(0xFF101318))
                                .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(8.dp))
                        ) {
                            logs.forEach { logEntry ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (logEntry.isCrashReport) {
                                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                                            }
                                            Text(
                                                text = logEntry.fileName,
                                                color = if (logEntry.filePath == selectedLog?.filePath) Color.White else Color(0xFF94A3B8),
                                                fontWeight = if (logEntry.filePath == selectedLog?.filePath) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 12.sp
                                            )
                                        }
                                    },
                                    onClick = {
                                        isDropdownExpanded = false
                                        viewModel.loadLogContent(logEntry)
                                    }
                                )
                            }
                        }
                    }
                }

                // Live Indicator Badge if running
                if (isRunning) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Text("LIVE LOG", color = Color(0xFF10B981), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Search Input Field
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search log lines...", color = Color(0xFF94A3B8), fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .width(220.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF101318))
                        .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(8.dp)),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF101318),
                        unfocusedContainerColor = Color(0xFF101318),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true
                )
            }

            // Right Action Controls
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                // Auto-Scroll Toggle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (autoScrollToBottom) Color(0xFF1A1E29) else Color(0xFF101318))
                        .border(1.dp, if (autoScrollToBottom) Color.White else Color(0xFF1A1D26), RoundedCornerShape(6.dp))
                        .clickable { autoScrollToBottom = !autoScrollToBottom }
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = if (autoScrollToBottom) Color.White else Color(0xFF94A3B8),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Auto-Scroll",
                            color = if (autoScrollToBottom) Color.White else Color(0xFF94A3B8),
                            fontSize = 11.5.sp,
                            fontWeight = if (autoScrollToBottom) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }

                // Refresh Button
                EzzButton(
                    text = "Refresh",
                    onClick = {
                        val curr = selectedLog ?: logs.firstOrNull { it.fileName == "latest.log" }
                        viewModel.loadLogContent(curr)
                    },
                    icon = Icons.Default.Refresh,
                    variant = EzzButtonVariant.SECONDARY,
                    size = EzzButtonSize.SMALL
                )

                // Copy Full Log Button
                EzzButton(
                    text = if (showCopiedToast) "Copied!" else "Copy Log",
                    onClick = {
                        val textToCopy = logResult?.lines?.joinToString("\n") { it.text } ?: ""
                        if (textToCopy.isNotBlank()) {
                            val sel = StringSelection(textToCopy)
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
                            showCopiedToast = true
                        }
                    },
                    icon = if (showCopiedToast) Icons.Default.Check else Icons.Default.ContentCopy,
                    variant = EzzButtonVariant.SECONDARY,
                    size = EzzButtonSize.SMALL
                )

                // Open Logs Folder Button
                EzzButton(
                    text = "Open Folder",
                    onClick = {
                        val mcDir = viewModel.pathProvider.getInstanceDirectory(instance.id).resolve(".minecraft").toFile()
                        val logsDir = File(mcDir, "logs")
                        if (!logsDir.exists()) logsDir.mkdirs()
                        viewModel.platformBridge.openFolder(logsDir.absolutePath.toPath())
                    },
                    icon = Icons.Default.FolderOpen,
                    variant = EzzButtonVariant.SECONDARY,
                    size = EzzButtonSize.SMALL
                )
            }
        }

        // Sub-Bar: Severity Filters + Truncation Info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Severity Level Filter Chips
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF101318))
                    .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(8.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val totalCount = logResult?.lines?.size ?: 0
                val errCount = logResult?.errorCount ?: 0
                val warnCount = logResult?.warnCount ?: 0
                val infoCount = logResult?.infoCount ?: 0

                FilterChip("All ($totalCount)", levelFilter == "ALL") { levelFilter = "ALL" }
                FilterChip("INFO ($infoCount)", levelFilter == "INFO", Color(0xFFE2E8F0)) { levelFilter = "INFO" }
                FilterChip("WARN ($warnCount)", levelFilter == "WARN", Color(0xFFFBBF24)) { levelFilter = "WARN" }
                FilterChip("ERROR ($errCount)", levelFilter == "ERROR", Color(0xFFF87171)) { levelFilter = "ERROR" }
            }

            // Truncation notice or line count
            if (logResult?.isTruncated == true) {
                Text(
                    text = "Showing latest 5,000 lines (log truncated for performance)",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            } else if (logResult != null && logResult!!.lines.isNotEmpty()) {
                Text(
                    text = "${filteredLines.size} of ${logResult!!.lines.size} lines",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp
                )
            }
        }

        // Main Content Area (State Machine: Empty, Loading, Error, Content)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF07080A))
                .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(10.dp))
        ) {
            when {
                // Loading State
                isLogLoading && logResult == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                            Text("Reading log file...", color = Color(0xFF94A3B8), fontSize = 13.sp)
                        }
                    }
                }

                // Error State
                logLoadError != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(36.dp))
                            Text("LOG COULD NOT BE LOADED", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(logLoadError ?: "Unknown error", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            EzzButton(
                                text = "Retry",
                                onClick = { viewModel.loadLogContent(selectedLog) },
                                variant = EzzButtonVariant.SECONDARY,
                                size = EzzButtonSize.SMALL
                            )
                        }
                    }
                }

                // Empty State (No log files generated yet or empty)
                logs.isEmpty() || logResult == null || logResult!!.lines.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF101318))
                                    .border(1.dp, Color(0xFF1A1D26), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Terminal, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(28.dp))
                            }
                            Text(
                                text = "NO LOG FILE AVAILABLE",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Minecraft has not generated a runtime log for this instance yet.\nLaunch the game to generate live logs and crash diagnostics.",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                EzzButton(
                                    text = "Refresh",
                                    onClick = { viewModel.refreshManageData() },
                                    icon = Icons.Default.Refresh,
                                    variant = EzzButtonVariant.SECONDARY,
                                    size = EzzButtonSize.MEDIUM
                                )

                                EzzButton(
                                    text = "Launch Minecraft",
                                    onClick = { viewModel.launchInstance(instance) },
                                    icon = Icons.Default.PlayArrow,
                                    variant = EzzButtonVariant.PRIMARY,
                                    size = EzzButtonSize.MEDIUM
                                )
                            }
                        }
                    }
                }

                // Filtered to 0 lines
                filteredLines.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("No log lines match your filter.", color = Color(0xFF94A3B8), fontSize = 13.sp)
                            EzzButton(
                                text = "Clear Filter",
                                onClick = {
                                    searchQuery = ""
                                    levelFilter = "ALL"
                                },
                                variant = EzzButtonVariant.SECONDARY,
                                size = EzzButtonSize.SMALL
                            )
                        }
                    }
                }

                // Fully Virtualized Log Lines List
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filteredLines, key = { it.lineNumber }) { logLine ->
                            LogLineRow(logLine)
                        }
                    }
                }
            }
        }
    }

    // Auto-dismiss copied toast
    LaunchedEffect(showCopiedToast) {
        if (showCopiedToast) {
            kotlinx.coroutines.delay(2000L)
            showCopiedToast = false
        }
    }
}

@Composable
private fun LogLineRow(line: LogLine) {
    val textColor = when (line.level) {
        LogSeverityLevel.ERROR -> Color(0xFFF87171)
        LogSeverityLevel.WARN -> Color(0xFFFBBF24)
        LogSeverityLevel.DEBUG -> Color(0xFF64748B)
        LogSeverityLevel.INFO -> Color(0xFFE2E8F0)
        LogSeverityLevel.UNKNOWN -> Color(0xFFCBD5E1)
    }

    val isBold = line.level == LogSeverityLevel.ERROR

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Line number
        Text(
            text = line.lineNumber.toString().padStart(4, ' '),
            color = Color(0xFF475569),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(42.dp)
        )

        // Line content with horizontal scroll if single line is wide
        Box(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                text = line.text,
                color = textColor,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    isSelected: Boolean,
    accentColor: Color = Color.White,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) Color(0xFF1A1E29) else Color.Transparent)
            .border(1.dp, if (isSelected) Color.White else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) accentColor else Color(0xFF94A3B8),
            fontSize = 11.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
