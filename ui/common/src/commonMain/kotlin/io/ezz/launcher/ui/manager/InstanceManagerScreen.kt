package io.ezz.launcher.ui.manager

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.InstanceManagerTab
import io.ezz.launcher.ui.components.RuntimeDisplay
import io.ezz.launcher.ui.manager.dialogs.InstanceDuplicateDialog
import io.ezz.launcher.ui.manager.dialogs.InstanceExportDialog
import io.ezz.launcher.ui.manager.dialogs.InstanceRepairDialog
import io.ezz.launcher.ui.manager.dialogs.ScreenshotViewerDialog
import io.ezz.launcher.ui.manager.dialogs.WorldBackupRestoreDialog
import io.ezz.launcher.ui.manager.tabs.FilesTab
import io.ezz.launcher.ui.manager.tabs.InstanceSettingsTab
import io.ezz.launcher.ui.manager.tabs.LogsTab
import io.ezz.launcher.ui.manager.tabs.ModsTab
import io.ezz.launcher.ui.manager.tabs.OverviewTab
import io.ezz.launcher.ui.manager.tabs.ResourcePacksTab
import io.ezz.launcher.ui.manager.tabs.ScreenshotsTab
import io.ezz.launcher.ui.manager.tabs.ShadersTab
import io.ezz.launcher.ui.manager.tabs.WorldsTab
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.AppViewModel
import io.ezz.launcher.ui.viewmodel.NavigationScreen

@Composable
fun InstanceManagerScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val selectedInstance by viewModel.selectedInstance.collectAsState()
    val activeTab by viewModel.activeManageTab.collectAsState()
    val runningSessions by viewModel.runningSessions.collectAsState()

    // Real-time counts for badges
    val manageMods by viewModel.manageMods.collectAsState()
    val manageResourcePacks by viewModel.manageResourcePacks.collectAsState()
    val manageShaders by viewModel.manageShaders.collectAsState()
    val manageWorlds by viewModel.manageWorlds.collectAsState()
    val manageScreenshots by viewModel.manageScreenshots.collectAsState()

    // Modals
    val viewerScreenshot by viewModel.selectedScreenshotForViewer.collectAsState()
    val showRepair by viewModel.showRepairDialog.collectAsState()
    val duplicateInstance by viewModel.showDuplicateInstanceDialog.collectAsState()
    val exportInstance by viewModel.showExportInstanceDialog.collectAsState()
    val worldBackupTarget by viewModel.showWorldBackupRestoreDialog.collectAsState()

    if (selectedInstance == null) {
        Box(
            modifier = modifier.fillMaxSize().background(Color(0xFF0D0D0D)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("No Instance Selected", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Button(onClick = { viewModel.navigateTo(NavigationScreen.INSTANCES) }) {
                    Text("Go to Instances")
                }
            }
        }
        return
    }

    val instance = selectedInstance!!
    val isRunning = runningSessions.containsKey(instance.id)
    val session = runningSessions[instance.id]

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0E0E0E))) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Master Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141414))
                    .border(1.dp, Color(0xFF222222))
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Back button + Icon + Title + Badges
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconButton(
                            onClick = { viewModel.navigateTo(NavigationScreen.HOME) },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1C1C1C))
                                .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(8.dp))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(18.dp))
                        }

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1E1E1E))
                                .border(1.dp, Color(0xFF2C2C2C), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Apps, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = instance.name,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black
                                )

                                if (isRunning && session != null) {
                                    RuntimeDisplay(
                                        startedAt = session.startedAt,
                                        showPrefix = true,
                                        prefixText = "RUNNING",
                                        fontSize = 12.sp,
                                        dotSize = 7.dp
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF222222))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("READY", color = Color(0xFFAAAAAA), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                HeaderBadge("MC ${instance.minecraftVersion}", Color(0xFF1976D2))
                                HeaderBadge(instance.loaderType.name, Color(0xFF388E3C))
                                HeaderBadge("${instance.maxMemoryMb / 1024} GB RAM", Color(0xFF7B1FA2))
                            }
                        }
                    }

                    // Right: Play / Stop Button
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isRunning) {
                            Button(
                                onClick = { viewModel.stopInstance(instance.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935), contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stop", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { viewModel.launchInstance(instance) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Play", fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }

            // Navigation Tab Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF121212))
                    .border(1.dp, Color(0xFF222222))
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TabItem(tab = InstanceManagerTab.OVERVIEW, icon = Icons.Default.Apps, selected = activeTab == InstanceManagerTab.OVERVIEW, count = null) {
                    viewModel.setManageTab(InstanceManagerTab.OVERVIEW)
                }
                TabItem(tab = InstanceManagerTab.MODS, icon = Icons.Default.Extension, selected = activeTab == InstanceManagerTab.MODS, count = manageMods.size) {
                    viewModel.setManageTab(InstanceManagerTab.MODS)
                }
                TabItem(tab = InstanceManagerTab.RESOURCE_PACKS, icon = Icons.Default.Palette, selected = activeTab == InstanceManagerTab.RESOURCE_PACKS, count = manageResourcePacks.size) {
                    viewModel.setManageTab(InstanceManagerTab.RESOURCE_PACKS)
                }
                TabItem(tab = InstanceManagerTab.SHADERS, icon = Icons.Default.Layers, selected = activeTab == InstanceManagerTab.SHADERS, count = manageShaders.size) {
                    viewModel.setManageTab(InstanceManagerTab.SHADERS)
                }
                TabItem(tab = InstanceManagerTab.WORLDS, icon = Icons.Default.Public, selected = activeTab == InstanceManagerTab.WORLDS, count = manageWorlds.size) {
                    viewModel.setManageTab(InstanceManagerTab.WORLDS)
                }
                TabItem(tab = InstanceManagerTab.SCREENSHOTS, icon = Icons.Default.Image, selected = activeTab == InstanceManagerTab.SCREENSHOTS, count = manageScreenshots.size) {
                    viewModel.setManageTab(InstanceManagerTab.SCREENSHOTS)
                }
                TabItem(tab = InstanceManagerTab.SETTINGS, icon = Icons.Default.Settings, selected = activeTab == InstanceManagerTab.SETTINGS, count = null) {
                    viewModel.setManageTab(InstanceManagerTab.SETTINGS)
                }
                TabItem(tab = InstanceManagerTab.FILES, icon = Icons.Default.Folder, selected = activeTab == InstanceManagerTab.FILES, count = null) {
                    viewModel.setManageTab(InstanceManagerTab.FILES)
                }
                TabItem(tab = InstanceManagerTab.LOGS, icon = Icons.Default.Terminal, selected = activeTab == InstanceManagerTab.LOGS, count = null) {
                    viewModel.setManageTab(InstanceManagerTab.LOGS)
                }
            }

            // Active Tab View Content
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "TabContentTransition"
                ) { currentTab ->
                    when (currentTab) {
                        InstanceManagerTab.OVERVIEW -> OverviewTab(instance = instance, viewModel = viewModel)
                        InstanceManagerTab.MODS -> ModsTab(instance = instance, viewModel = viewModel)
                        InstanceManagerTab.RESOURCE_PACKS -> ResourcePacksTab(instance = instance, viewModel = viewModel)
                        InstanceManagerTab.SHADERS -> ShadersTab(instance = instance, viewModel = viewModel)
                        InstanceManagerTab.WORLDS -> WorldsTab(instance = instance, viewModel = viewModel)
                        InstanceManagerTab.SCREENSHOTS -> ScreenshotsTab(instance = instance, viewModel = viewModel)
                        InstanceManagerTab.SETTINGS -> InstanceSettingsTab(instance = instance, viewModel = viewModel)
                        InstanceManagerTab.FILES -> FilesTab(instance = instance, viewModel = viewModel)
                        InstanceManagerTab.LOGS -> LogsTab(instance = instance, viewModel = viewModel)
                    }
                }
            }
        }

        // Fullscreen Screenshot Viewer
        val activeViewerScreenshot = viewerScreenshot
        if (activeViewerScreenshot != null) {
            ScreenshotViewerDialog(
                screenshot = activeViewerScreenshot,
                allScreenshots = manageScreenshots,
                viewModel = viewModel,
                onDismiss = { viewModel.selectedScreenshotForViewer.value = null }
            )
        }

        // Repair Diagnostics Dialog
        if (showRepair) {
            InstanceRepairDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.showRepairDialog.value = false }
            )
        }

        // Duplicate Dialog
        if (duplicateInstance != null) {
            InstanceDuplicateDialog(
                sourceInstance = duplicateInstance!!,
                viewModel = viewModel,
                onDismiss = { viewModel.showDuplicateInstanceDialog.value = null }
            )
        }

        // Export Dialog
        if (exportInstance != null) {
            InstanceExportDialog(
                sourceInstance = exportInstance!!,
                viewModel = viewModel,
                onDismiss = { viewModel.showExportInstanceDialog.value = null }
            )
        }

        // World Backup History Dialog
        if (worldBackupTarget != null) {
            WorldBackupRestoreDialog(
                world = worldBackupTarget!!,
                viewModel = viewModel,
                onDismiss = { viewModel.showWorldBackupRestoreDialog.value = null }
            )
        }
    }
}

@Composable
private fun HeaderBadge(text: String, accentColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(accentColor.copy(alpha = 0.15f))
            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = text, color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TabItem(
    tab: InstanceManagerTab,
    icon: ImageVector,
    selected: Boolean,
    count: Int?,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val contentColor = when {
        selected -> Color.White
        isHovered -> Color(0xFFCCCCCC)
        else -> Color(0xFF777777)
    }

    Box(
        modifier = Modifier
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = tab.title,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = tab.title,
                    color = contentColor,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
                if (count != null && count > 0) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (selected) Color(0xFF333333) else Color(0xFF222222))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = count.toString(),
                            color = if (selected) Color.White else Color(0xFF999999),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(if (selected) Color.White else Color.Transparent)
            )
        }
    }
}
