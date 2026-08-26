package io.ezz.launcher.ui.manager.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.InstanceLogEntry
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.AppViewModel
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun LogsTab(
    instance: Instance,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val colors = EzzTheme.colors
    val logs by viewModel.manageLogs.collectAsState()
    val selectedLog by viewModel.selectedLogFile.collectAsState()
    val logContent by viewModel.manageSelectedLogContent.collectAsState()

    var filterQuery by remember { mutableStateOf("") }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var isCopiedToast by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    LaunchedEffect(logs) {
        if (selectedLog == null && logs.isNotEmpty()) {
            val latest = logs.firstOrNull { it.fileName == "latest.log" } ?: logs.first()
            viewModel.loadLogContent(latest)
        }
    }

    val displayContent = remember(logContent, filterQuery) {
        val raw = logContent ?: "No log file selected."
        if (filterQuery.isBlank()) raw
        else {
            raw.lineSequence().filter { it.contains(filterQuery, ignoreCase = true) }.joinToString("\n")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Log File Selector & Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    OutlinedButton(
                        onClick = { isDropdownExpanded = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary)
                    ) {
                        Icon(
                            if (selectedLog?.isCrashReport == true) Icons.Default.Warning else Icons.Default.Description,
                            contentDescription = null,
                            tint = if (selectedLog?.isCrashReport == true) colors.danger else colors.textPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(selectedLog?.fileName ?: "Select Log File", fontWeight = FontWeight.Bold)
                    }

                    DropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false }
                    ) {
                        logs.forEach { logEntry ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (logEntry.isCrashReport) {
                                            Icon(Icons.Default.Warning, contentDescription = null, tint = colors.danger, modifier = Modifier.size(14.dp))
                                        }
                                        Text(logEntry.fileName, fontWeight = if (logEntry == selectedLog) FontWeight.Bold else FontWeight.Normal)
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

                TextField(
                    value = filterQuery,
                    onValueChange = { filterQuery = it },
                    placeholder = { Text("Filter lines (e.g. ERROR, WARN)...", color = colors.textMuted, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.width(280.dp).height(42.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        val sel = StringSelection(logContent ?: "")
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
                        isCopiedToast = true
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Full Log")
                }

                Button(
                    onClick = {
                        val path = viewModel.pathProvider.getInstanceDirectory(instance.id).resolve(".minecraft").resolve("logs")
                        viewModel.platformBridge.openFolder(path)
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceLight, contentColor = colors.textPrimary)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Logs Folder")
                }
            }
        }

        // Log Console Box
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0C0C0C))
                .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                .padding(16.dp)
        ) {
            SelectionContainer {
                Text(
                    text = displayContent,
                    color = Color(0xFFD4D4D4),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                )
            }
        }
    }
}
