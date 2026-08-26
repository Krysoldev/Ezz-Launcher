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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import okio.Path.Companion.toPath
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.AppViewModel
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
    val colors = EzzTheme.colors
    val mcDir = viewModel.pathProvider.getInstanceDirectory(instance.id).resolve(".minecraft").toFile()

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

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Instance File System",
                color = colors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Direct access to local instance directories. All data is isolated to this specific instance.",
                color = colors.textSecondary,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.padding(bottom = 8.dp))
        }

        items(standardFolders) { folder ->
            val targetDir = folder.file
            val exists = targetDir.exists()
            val fileCount = if (exists) targetDir.listFiles()?.size ?: 0 else 0

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                    .padding(16.dp)
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
                                .background(colors.surfaceLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(folder.icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = folder.title,
                                    color = colors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (exists) "$fileCount item(s)" else "Not Created",
                                    color = if (exists) colors.textMuted else colors.danger,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                text = folder.description,
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = targetDir.absolutePath,
                                color = colors.textMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (!targetDir.exists()) targetDir.mkdirs()
                                viewModel.platformBridge.openFolder(targetDir.absolutePath.toPath())
                            },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open in Explorer", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
