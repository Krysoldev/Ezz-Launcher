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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import io.ezz.launcher.core.model.instance.InstanceManagerTab
import io.ezz.launcher.core.model.runtime.ProcessState
import io.ezz.launcher.ui.components.RuntimeDisplay
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.AppViewModel

@Composable
fun OverviewTab(
    instance: Instance,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val colors = EzzTheme.colors
    val stats by viewModel.manageStatistics.collectAsState()
    val runningSessions by viewModel.runningSessions.collectAsState()
    val isRunning = runningSessions.containsKey(instance.id)
    val session = runningSessions[instance.id]

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Quick Actions & Launch Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.cardBackground)
                .border(1.dp, if (isRunning) Color(0xFF10B981).copy(alpha = 0.5f) else colors.border, RoundedCornerShape(12.dp))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = instance.name,
                            color = colors.textPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )
                        if (isRunning && session != null) {
                            RuntimeDisplay(
                                startedAt = session.startedAt,
                                showPrefix = true,
                                prefixText = "RUNNING",
                                fontSize = 13.sp,
                                dotSize = 8.dp
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.surfaceLight)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "READY",
                                    color = colors.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        text = "Minecraft ${instance.minecraftVersion} • ${instance.loaderType.name} • Java Memory: ${instance.maxMemoryMb} MB",
                        color = colors.textMuted,
                        fontSize = 13.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isRunning) {
                        Button(
                            onClick = { viewModel.stopInstance(instance.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.danger),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop Process", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.launchInstance(instance) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play Instance", fontWeight = FontWeight.Black)
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.openInstanceFolder(instance.id) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Open Folder", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Folder")
                    }
                }
            }
        }

        // Content Breakdown Counters
        Text(
            text = "INSTANCE CONTENT & STATISTICS",
            color = colors.textMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Mods",
                count = "${stats?.modsCount ?: 0}",
                icon = Icons.Default.Extension,
                onClick = { viewModel.setManageTab(InstanceManagerTab.MODS) },
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Resource Packs",
                count = "${stats?.resourcePacksCount ?: 0}",
                icon = Icons.Default.Palette,
                onClick = { viewModel.setManageTab(InstanceManagerTab.RESOURCE_PACKS) },
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Shaders",
                count = "${stats?.shadersCount ?: 0}",
                icon = Icons.Default.Layers,
                onClick = { viewModel.setManageTab(InstanceManagerTab.SHADERS) },
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Worlds",
                count = "${stats?.worldsCount ?: 0}",
                icon = Icons.Default.Public,
                onClick = { viewModel.setManageTab(InstanceManagerTab.WORLDS) },
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Screenshots",
                count = "${stats?.screenshotsCount ?: 0}",
                icon = Icons.Default.Image,
                onClick = { viewModel.setManageTab(InstanceManagerTab.SCREENSHOTS) },
                modifier = Modifier.weight(1f)
            )
        }

        // System Configuration Overview Card
        Text(
            text = "CONFIGURATION & STORAGE",
            color = colors.textMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ConfigRow("Minecraft Version", instance.minecraftVersion)
                ConfigRow("Mod Loader", "${instance.loaderType.name} ${instance.loaderVersion ?: ""}")
                ConfigRow("Allocated Memory", "${instance.minMemoryMb} MB (Min) / ${instance.maxMemoryMb} MB (Max)")
                ConfigRow("Window Resolution", "${instance.windowWidth} x ${instance.windowHeight}")
                ConfigRow("Total Disk Space", formatBytes(stats?.totalSizeBytes ?: 0L))
            }
        }

        // Management Utility Actions
        Text(
            text = "INSTANCE ACTIONS & REPAIR",
            color = colors.textMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionButton(
                label = "Repair Instance",
                desc = "Validate game files & assets",
                icon = Icons.Default.Build,
                onClick = { viewModel.runInstanceRepair() },
                modifier = Modifier.weight(1f)
            )
            ActionButton(
                label = "Duplicate Instance",
                desc = "Clone with mods & settings",
                icon = Icons.Default.ContentCopy,
                onClick = { viewModel.showDuplicateInstanceDialog.value = instance },
                modifier = Modifier.weight(1f)
            )
            ActionButton(
                label = "Export Instance",
                desc = "Package into ZIP archive",
                icon = Icons.Default.Upload,
                onClick = { viewModel.showExportInstanceDialog.value = instance },
                modifier = Modifier.weight(1f)
            )
            ActionButton(
                label = "Delete Instance",
                desc = "Permanently remove instance",
                icon = Icons.Default.Delete,
                isDanger = true,
                onClick = { viewModel.deleteInstance(instance.id) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    count: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = EzzTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.cardBackground)
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = title, tint = colors.textMuted, modifier = Modifier.size(20.dp))
                Text(
                    text = count,
                    color = colors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Text(
                text = title,
                color = colors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ConfigRow(label: String, value: String) {
    val colors = EzzTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = colors.textSecondary, fontSize = 13.sp)
        Text(
            text = value,
            color = colors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    desc: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isDanger: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = EzzTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.cardBackground)
            .border(1.dp, if (isDanger) colors.danger.copy(alpha = 0.4f) else colors.border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (isDanger) colors.danger else colors.textPrimary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                color = if (isDanger) colors.danger else colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = desc,
                color = colors.textMuted,
                fontSize = 11.sp
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb > 1024) {
        String.format("%.2f GB", mb / 1024)
    } else {
        String.format("%.1f MB", mb)
    }
}
