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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.viewmodel.AppViewModel
import okio.Path.Companion.toPath
import java.io.File

private data class StandardFolderItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val file: File
)

@Composable
fun FilesTab(
    instance: Instance,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val mcDir = viewModel.pathProvider.getInstanceDirectory(instance.id).resolve(".minecraft").toFile()
    var searchQuery by remember { mutableStateOf("") }

    val standardFolders = listOf(
        StandardFolderItem("mods", "Mods Directory", "Local Fabric / Forge / NeoForge mods (.jar)", Icons.Default.Extension, File(mcDir, "mods")),
        StandardFolderItem("resourcepacks", "Resource Packs", "Custom textures, models, and audio (.zip)", Icons.Default.Palette, File(mcDir, "resourcepacks")),
        StandardFolderItem("shaderpacks", "Shader Packs", "Iris / OptiFine graphical shaders (.zip)", Icons.Default.Layers, File(mcDir, "shaderpacks")),
        StandardFolderItem("saves", "World Saves", "Local singleplayer Minecraft worlds", Icons.Default.Public, File(mcDir, "saves")),
        StandardFolderItem("screenshots", "Screenshots", "In-game captured screenshots", Icons.Default.Image, File(mcDir, "screenshots")),
        StandardFolderItem("logs", "Logs & Crash Reports", "Runtime game output and crash stacktraces", Icons.Default.Terminal, File(mcDir, "logs")),
        StandardFolderItem("config", "Configuration Files", "Mod configurations and properties", Icons.Default.Settings, File(mcDir, "config")),
        StandardFolderItem(".minecraft", "Root Game Directory", "Isolated game instance root folder", Icons.Default.Folder, mcDir)
    )

    val filteredFolders = standardFolders.filter { folder ->
        searchQuery.isBlank() ||
                folder.title.contains(searchQuery, ignoreCase = true) ||
                folder.description.contains(searchQuery, ignoreCase = true) ||
                folder.file.name.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Search & Top Actions Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter instance folders...", color = Color(0xFF94A3B8), fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
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

            EzzButton(
                text = "Open Instance Root",
                onClick = {
                    viewModel.openInstanceFolder(instance.id)
                },
                icon = Icons.Default.FolderOpen,
                variant = EzzButtonVariant.SECONDARY,
                size = EzzButtonSize.SMALL
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredFolders, key = { it.id }) { folder ->
                val targetDir = folder.file
                val exists = targetDir.exists()
                val fileCount = if (exists) targetDir.listFiles()?.size ?: 0 else 0

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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF141720))
                                    .border(1.dp, Color(0xFF222735), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(folder.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = folder.title,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (exists) Color(0xFF141720) else Color(0xFFEF4444).copy(alpha = 0.15f))
                                            .border(1.dp, if (exists) Color(0xFF222735) else Color(0xFFEF4444).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (exists) "$fileCount items" else "Not Created",
                                            color = if (exists) Color(0xFF94A3B8) else Color(0xFFEF4444),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                Text(
                                    text = folder.description,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = targetDir.absolutePath,
                                    color = Color(0xFF64748B),
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconButton(
                                onClick = { viewModel.platformBridge.copyToClipboard(targetDir.absolutePath) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Path", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }

                            EzzButton(
                                text = "Open Folder",
                                onClick = {
                                    if (!targetDir.exists()) targetDir.mkdirs()
                                    viewModel.platformBridge.openFolder(targetDir.absolutePath.toPath())
                                },
                                icon = Icons.Default.FolderOpen,
                                variant = EzzButtonVariant.SECONDARY,
                                size = EzzButtonSize.SMALL
                            )
                        }
                    }
                }
            }
        }
    }
}
