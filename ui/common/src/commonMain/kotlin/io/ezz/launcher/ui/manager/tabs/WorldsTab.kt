package io.ezz.launcher.ui.manager.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.ModrinthAsyncImage
import io.ezz.launcher.ui.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WorldsTab(
    instance: Instance,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val worlds by viewModel.manageWorlds.collectAsState()

    var worldSearch by remember(instance.id) { mutableStateOf("") }
    var worldToRename by remember { mutableStateOf<LocalWorld?>(null) }
    var worldToDuplicate by remember { mutableStateOf<LocalWorld?>(null) }
    var worldToDelete by remember { mutableStateOf<LocalWorld?>(null) }

    var renameText by remember { mutableStateOf("") }
    var duplicateText by remember { mutableStateOf("") }

    val filteredWorlds = worlds.filter { world ->
        worldSearch.isBlank() || world.name.contains(worldSearch, ignoreCase = true) || world.folderName.contains(worldSearch, ignoreCase = true)
    }

    Column(
        modifier = modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Action Bar & Search
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = worldSearch,
                onValueChange = { worldSearch = it },
                placeholder = { Text("Search worlds...", color = Color(0xFF94A3B8), fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp)) },
                trailingIcon = {
                    if (worldSearch.isNotBlank()) {
                        IconButton(onClick = { worldSearch = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF101318))
                    .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(8.dp)),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF101318),
                    unfocusedContainerColor = Color(0xFF101318),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.width(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                EzzButton(
                    text = "Import World",
                    onClick = { viewModel.importLocalWorld(instance) },
                    icon = Icons.Default.Download,
                    variant = EzzButtonVariant.PRIMARY,
                    size = EzzButtonSize.SMALL
                )

                EzzButton(
                    text = "Refresh",
                    onClick = { viewModel.refreshManageData() },
                    icon = Icons.Default.Refresh,
                    variant = EzzButtonVariant.SECONDARY,
                    size = EzzButtonSize.SMALL
                )

                EzzButton(
                    text = "Open Saves Folder",
                    onClick = { viewModel.openSavesFolder(instance.id) },
                    icon = Icons.Default.FolderOpen,
                    variant = EzzButtonVariant.SECONDARY,
                    size = EzzButtonSize.SMALL
                )
            }
        }

        if (filteredWorlds.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF101318))
                    .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color(0xFF141720))
                            .border(1.dp, Color(0xFF222735), androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Text(
                        text = if (worlds.isEmpty()) "No Singleplayer Worlds Found" else "No Matching Worlds Found",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = if (worlds.isEmpty())
                            "Import a world archive (.zip) or launch Minecraft to create a singleplayer world."
                        else
                            "Try searching with a different name.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.5.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (worlds.isEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EzzButton(
                                text = "Import World",
                                onClick = { viewModel.importLocalWorld(instance) },
                                icon = Icons.Default.Download,
                                variant = EzzButtonVariant.PRIMARY,
                                size = EzzButtonSize.MEDIUM
                            )
                            EzzButton(
                                text = "Open Saves Folder",
                                onClick = { viewModel.openSavesFolder(instance.id) },
                                icon = Icons.Default.FolderOpen,
                                variant = EzzButtonVariant.SECONDARY,
                                size = EzzButtonSize.MEDIUM
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredWorlds, key = { it.folderName }) { world ->
                    WorldCard(
                        world = world,
                        viewModel = viewModel,
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
            title = { Text("Rename World", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                TextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    placeholder = { Text("Enter new world name", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF141720),
                        unfocusedContainerColor = Color(0xFF141720),
                        focusedIndicatorColor = Color.White,
                        unfocusedIndicatorColor = Color(0xFF222735),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                )
            },
            confirmButton = {
                EzzButton(
                    text = "Rename",
                    onClick = {
                        val w = worldToRename
                        if (w != null && renameText.isNotBlank()) {
                            viewModel.renameWorld(w.folderName, renameText.trim())
                            worldToRename = null
                        }
                    },
                    variant = EzzButtonVariant.PRIMARY,
                    size = EzzButtonSize.SMALL
                )
            },
            dismissButton = {
                EzzButton(
                    text = "Cancel",
                    onClick = { worldToRename = null },
                    variant = EzzButtonVariant.SECONDARY,
                    size = EzzButtonSize.SMALL
                )
            },
            containerColor = Color(0xFF101318),
            shape = RoundedCornerShape(12.dp)
        )
    }

    // Duplicate Dialog
    if (worldToDuplicate != null) {
        AlertDialog(
            onDismissRequest = { worldToDuplicate = null },
            title = { Text("Duplicate World", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                TextField(
                    value = duplicateText,
                    onValueChange = { duplicateText = it },
                    placeholder = { Text("Enter copy name", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF141720),
                        unfocusedContainerColor = Color(0xFF141720),
                        focusedIndicatorColor = Color.White,
                        unfocusedIndicatorColor = Color(0xFF222735),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                )
            },
            confirmButton = {
                EzzButton(
                    text = "Duplicate",
                    onClick = {
                        val w = worldToDuplicate
                        if (w != null && duplicateText.isNotBlank()) {
                            viewModel.duplicateWorld(w.folderName, duplicateText.trim())
                            worldToDuplicate = null
                        }
                    },
                    variant = EzzButtonVariant.PRIMARY,
                    size = EzzButtonSize.SMALL
                )
            },
            dismissButton = {
                EzzButton(
                    text = "Cancel",
                    onClick = { worldToDuplicate = null },
                    variant = EzzButtonVariant.SECONDARY,
                    size = EzzButtonSize.SMALL
                )
            },
            containerColor = Color(0xFF101318),
            shape = RoundedCornerShape(12.dp)
        )
    }

    // Delete Confirmation Dialog
    if (worldToDelete != null) {
        AlertDialog(
            onDismissRequest = { worldToDelete = null },
            title = { Text("Delete World", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete world '${worldToDelete?.name}'? This cannot be undone.",
                    color = Color(0xFFCBD5E1)
                )
            },
            confirmButton = {
                EzzButton(
                    text = "Delete Permanently",
                    onClick = {
                        worldToDelete?.let { viewModel.deleteWorld(it.folderName) }
                        worldToDelete = null
                    },
                    variant = EzzButtonVariant.DANGER,
                    size = EzzButtonSize.SMALL
                )
            },
            dismissButton = {
                EzzButton(
                    text = "Cancel",
                    onClick = { worldToDelete = null },
                    variant = EzzButtonVariant.SECONDARY,
                    size = EzzButtonSize.SMALL
                )
            },
            containerColor = Color(0xFF101318),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun WorldCard(
    world: LocalWorld,
    viewModel: AppViewModel,
    onPlay: () -> Unit,
    onBackup: () -> Unit,
    onHistory: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(world.lastPlayed) {
        if (world.lastPlayed > 0) {
            SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()).format(Date(world.lastPlayed))
        } else {
            "Unknown"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF101318))
            .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(10.dp))
            .padding(14.dp)
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
                // Real World Icon or Fallback
                ModrinthAsyncImage(
                    url = world.iconPath,
                    imageLoader = viewModel.imageLoader,
                    placeholderIcon = Icons.Default.Public,
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(8.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = world.name,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF141720))
                                .border(1.dp, Color(0xFF222735), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(text = world.gameType, color = Color(0xFFCBD5E1), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Text(
                        text = "Last Played: $dateStr • Size: ${formatBytes(world.sizeBytes)}",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EzzButton(
                    text = "Play",
                    onClick = onPlay,
                    icon = Icons.Default.PlayArrow,
                    variant = EzzButtonVariant.PRIMARY,
                    size = EzzButtonSize.SMALL
                )

                IconButton(onClick = onBackup, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Archive, contentDescription = "Create Backup", tint = Color(0xFFCBD5E1), modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onHistory, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.History, contentDescription = "Backups History", tint = Color(0xFFCBD5E1), modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDuplicate, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate World", tint = Color(0xFFCBD5E1), modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Rename World", tint = Color(0xFFCBD5E1), modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete World", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
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
