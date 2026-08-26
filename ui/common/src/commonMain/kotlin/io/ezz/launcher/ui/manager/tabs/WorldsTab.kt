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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LocalWorld
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.AppViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WorldsTab(
    instance: Instance,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val colors = EzzTheme.colors
    val worlds by viewModel.manageWorlds.collectAsState()

    var worldToRename by remember { mutableStateOf<LocalWorld?>(null) }
    var worldToDuplicate by remember { mutableStateOf<LocalWorld?>(null) }
    var worldToDelete by remember { mutableStateOf<LocalWorld?>(null) }

    var renameText by remember { mutableStateOf("") }
    var duplicateText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "WORLDS & SAVES",
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "${worlds.size} world(s) found in saves directory",
                    color = colors.textMuted,
                    fontSize = 13.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { viewModel.refreshManageData() },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Refresh")
                }

                Button(
                    onClick = {
                        val path = viewModel.pathProvider.getInstanceDirectory(instance.id).resolve(".minecraft").resolve("saves")
                        viewModel.platformBridge.openFolder(path)
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceLight, contentColor = colors.textPrimary)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Saves Folder")
                }
            }
        }

        if (worlds.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Public, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(40.dp))
                    Text(text = "No worlds found", color = colors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Launch the game and create a singleplayer world or place save folders into saves/", color = colors.textMuted, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(worlds, key = { it.folderName }) { world ->
                    WorldCard(
                        world = world,
                        onPlay = { viewModel.launchInstance(instance) },
                        onBackup = { viewModel.backupWorld(world.folderName) },
                        onHistory = { viewModel.openWorldBackups(world) },
                        onDuplicate = {
                            worldToDuplicate = world
                            duplicateText = "${world.name}_Copy"
                        },
                        onRename = {
                            worldToRename = world
                            renameText = world.name
                        },
                        onDelete = { worldToDelete = world }
                    )
                }
            }
        }
    }

    // Rename Dialog
    if (worldToRename != null) {
        AlertDialog(
            onDismissRequest = { worldToRename = null },
            title = { Text("Rename World", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                TextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    placeholder = { Text("Enter new world name") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val w = worldToRename
                        if (w != null && renameText.isNotBlank()) {
                            viewModel.renameWorld(w.folderName, renameText.trim())
                            worldToRename = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Text("Rename", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { worldToRename = null }) {
                    Text("Cancel")
                }
            },
            containerColor = colors.surface
        )
    }

    // Duplicate Dialog
    if (worldToDuplicate != null) {
        AlertDialog(
            onDismissRequest = { worldToDuplicate = null },
            title = { Text("Duplicate World", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                TextField(
                    value = duplicateText,
                    onValueChange = { duplicateText = it },
                    placeholder = { Text("Enter copy name") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val w = worldToDuplicate
                        if (w != null && duplicateText.isNotBlank()) {
                            viewModel.duplicateWorld(w.folderName, duplicateText.trim())
                            worldToDuplicate = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Text("Duplicate", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { worldToDuplicate = null }) {
                    Text("Cancel")
                }
            },
            containerColor = colors.surface
        )
    }

    // Delete Confirmation Dialog
    if (worldToDelete != null) {
        AlertDialog(
            onDismissRequest = { worldToDelete = null },
            title = { Text("Delete World", color = colors.danger, fontWeight = FontWeight.Black) },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete world '${worldToDelete?.name}'? This cannot be undone.",
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        worldToDelete?.let { viewModel.deleteWorld(it.folderName) }
                        worldToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.danger, contentColor = Color.White)
                ) {
                    Text("Delete Permanently", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { worldToDelete = null }) {
                    Text("Cancel")
                }
            },
            containerColor = colors.surface
        )
    }
}

@Composable
private fun WorldCard(
    world: LocalWorld,
    onPlay: () -> Unit,
    onBackup: () -> Unit,
    onHistory: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = EzzTheme.colors
    val dateStr = remember(world.lastPlayed) {
        if (world.lastPlayed > 0) {
            SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(world.lastPlayed))
        } else {
            "Unknown"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.cardBackground)
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Public, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(26.dp))
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = world.name,
                            color = colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.surfaceLight)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(text = world.gameType, color = colors.textSecondary, fontSize = 11.sp)
                        }
                    }

                    Text(
                        text = "Last Played: $dateStr • Size: ${formatBytes(world.sizeBytes)}",
                        color = colors.textMuted,
                        fontSize = 12.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onPlay,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                IconButton(onClick = onBackup) {
                    Icon(Icons.Default.Archive, contentDescription = "Create Backup", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onHistory) {
                    Icon(Icons.Default.History, contentDescription = "Backups History", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDuplicate) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate World", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onRename) {
                    Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Rename World", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete World", tint = colors.danger.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb > 1024) String.format("%.2f GB", mb / 1024) else String.format("%.1f MB", mb)
}
