package io.ezz.launcher.ui.manager

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.InstanceManagerTab
import io.ezz.launcher.ui.components.InstanceArtworkIcon
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
import io.ezz.launcher.ui.viewmodel.AppViewModel
import io.ezz.launcher.ui.viewmodel.NavigationScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Compact Master Header (110–130px)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111111))
                    .border(1.dp, Color(0xFF1E1E1E))
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 1440.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Back button + 64px Instance Artwork + Name & Monochrome Badges
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Back Button
                            IconButton(
                                onClick = { viewModel.navigateTo(NavigationScreen.HOME) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1C1C1C))
                                    .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(8.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            // 64px Sharp Minecraft Artwork / Isometric Block
                            InstanceArtworkIcon(
                                instance = instance,
                                size = 64.dp
                            )

                            // Title & Badges
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = instance.name,
                                        color = Color.White,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = (-0.3).sp
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
                                                .background(Color(0xFF1C1C1C))
                                                .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF888888)))
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text("READY", color = Color(0xFFAAAAAA), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                // Monochrome badges
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MonochromeHeaderBadge("MC ${instance.minecraftVersion}")
                                    MonochromeHeaderBadge(instance.loaderType.name)
                                    MonochromeHeaderBadge("Java ${io.ezz.launcher.core.minecraft.version.JavaCompatibility.getRequiredJavaMajorVersion(instance.minecraftVersion)}")
                                    MonochromeHeaderBadge("${instance.maxMemoryMb / 1024} GB RAM")
                                }
                            }
                        }

                        // Right: Primary Play CTA + Secondary Open Folder + More Menu
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Last played info
                            Column(horizontalAlignment = Alignment.End) {
                                val lastPlayed = instance.lastPlayedAt
                                val lastPlayedText = if (lastPlayed != null && lastPlayed > 0) {
                                    "Last played: ${SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(lastPlayed))}"
                                } else {
                                    "Not played yet"
                                }
                                Text(lastPlayedText, color = Color(0xFF666666), fontSize = 11.sp)
                            }

                            // Secondary: Open Folder
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1A1A1A))
                                    .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(8.dp))
                                    .clickable { viewModel.openInstanceFolder(instance.id) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = Color(0xFFCCCCCC),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text("Open Folder", color = Color(0xFFE0E0E0), fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                                }
                            }

                            // Secondary: More Options Dropdown
                            var showMoreMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(
                                    onClick = { showMoreMenu = true },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1A1A1A))
                                        .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(8.dp))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More Actions",
                                        tint = Color(0xFFCCCCCC),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false },
                                    modifier = Modifier
                                        .background(Color(0xFF181818))
                                        .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(8.dp))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Duplicate Instance", color = Color.White, fontSize = 13.sp) },
                                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFFAAAAAA), modifier = Modifier.size(16.dp)) },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.showDuplicateInstanceDialog.value = instance
                                        },
                                        colors = MenuDefaults.itemColors(textColor = Color.White)
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Export Instance (.zip)", color = Color.White, fontSize = 13.sp) },
                                        leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null, tint = Color(0xFFAAAAAA), modifier = Modifier.size(16.dp)) },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.showExportInstanceDialog.value = instance
                                        },
                                        colors = MenuDefaults.itemColors(textColor = Color.White)
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Repair & Verify Assets", color = Color.White, fontSize = 13.sp) },
                                        leadingIcon = { Icon(Icons.Default.Build, contentDescription = null, tint = Color(0xFFAAAAAA), modifier = Modifier.size(16.dp)) },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.showRepairDialog.value = true
                                        },
                                        colors = MenuDefaults.itemColors(textColor = Color.White)
                                    )
                                }
                            }

                            // Primary: Play / Stop Button
                            val playInteraction = remember { MutableInteractionSource() }
                            val isPlayHovered by playInteraction.collectIsHoveredAsState()
                            val isPlayPressed by playInteraction.collectIsPressedAsState()

                            val playScale by animateFloatAsState(
                                targetValue = if (isPlayPressed) 0.97f else if (isPlayHovered) 1.02f else 1.0f,
                                animationSpec = tween(100)
                            )

                            if (isRunning) {
                                Button(
                                    onClick = { viewModel.stopInstance(instance.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.scale(playScale).height(40.dp)
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Stop", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.launchInstance(instance) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isPlayHovered) Color(0xFFE5E5E5) else Color.White,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.scale(playScale).height(40.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.Black)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("PLAY", fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 0.5.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 2. Compact Tab Navigation Strip (~44px, Pill style, NO full width line!)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0E0E0E))
                    .border(1.dp, Color(0xFF1E1E1E))
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 1440.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactPillTab(
                        tab = InstanceManagerTab.OVERVIEW,
                        icon = Icons.Default.Apps,
                        selected = activeTab == InstanceManagerTab.OVERVIEW,
                        count = null
                    ) { viewModel.setManageTab(InstanceManagerTab.OVERVIEW) }

                    CompactPillTab(
                        tab = InstanceManagerTab.MODS,
                        icon = Icons.Default.Extension,
                        selected = activeTab == InstanceManagerTab.MODS,
                        count = manageMods.size
                    ) { viewModel.setManageTab(InstanceManagerTab.MODS) }

                    CompactPillTab(
                        tab = InstanceManagerTab.RESOURCE_PACKS,
                        icon = Icons.Default.Palette,
                        selected = activeTab == InstanceManagerTab.RESOURCE_PACKS,
                        count = manageResourcePacks.size
                    ) { viewModel.setManageTab(InstanceManagerTab.RESOURCE_PACKS) }

                    CompactPillTab(
                        tab = InstanceManagerTab.SHADERS,
                        icon = Icons.Default.Layers,
                        selected = activeTab == InstanceManagerTab.SHADERS,
                        count = manageShaders.size
                    ) { viewModel.setManageTab(InstanceManagerTab.SHADERS) }

                    CompactPillTab(
                        tab = InstanceManagerTab.WORLDS,
                        icon = Icons.Default.Public,
                        selected = activeTab == InstanceManagerTab.WORLDS,
                        count = manageWorlds.size
                    ) { viewModel.setManageTab(InstanceManagerTab.WORLDS) }

                    CompactPillTab(
                        tab = InstanceManagerTab.SCREENSHOTS,
                        icon = Icons.Default.Image,
                        selected = activeTab == InstanceManagerTab.SCREENSHOTS,
                        count = manageScreenshots.size
                    ) { viewModel.setManageTab(InstanceManagerTab.SCREENSHOTS) }

                    CompactPillTab(
                        tab = InstanceManagerTab.SETTINGS,
                        icon = Icons.Default.Settings,
                        selected = activeTab == InstanceManagerTab.SETTINGS,
                        count = null
                    ) { viewModel.setManageTab(InstanceManagerTab.SETTINGS) }

                    CompactPillTab(
                        tab = InstanceManagerTab.FILES,
                        icon = Icons.Default.Folder,
                        selected = activeTab == InstanceManagerTab.FILES,
                        count = null
                    ) { viewModel.setManageTab(InstanceManagerTab.FILES) }

                    CompactPillTab(
                        tab = InstanceManagerTab.LOGS,
                        icon = Icons.Default.Terminal,
                        selected = activeTab == InstanceManagerTab.LOGS,
                        count = null
                    ) { viewModel.setManageTab(InstanceManagerTab.LOGS) }
                }
            }

            // 3. Dynamic Tab Content Area
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = { fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150)) },
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

/**
 * Dark monochrome badge.
 */
@Composable
private fun MonochromeHeaderBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF181818))
            .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(text = text, color = Color(0xFFCCCCCC), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Compact Pill Tab Item with hover animations and count badges.
 */
@Composable
private fun CompactPillTab(
    tab: InstanceManagerTab,
    icon: ImageVector,
    selected: Boolean,
    count: Int?,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bg = when {
        selected -> Color(0xFF222222)
        isHovered -> Color(0xFF161616)
        else -> Color.Transparent
    }

    val border = when {
        selected -> Color(0xFF383838)
        isHovered -> Color(0xFF262626)
        else -> Color.Transparent
    }

    val fg = when {
        selected -> Color.White
        isHovered -> Color(0xFFDDDDDD)
        else -> Color(0xFF888888)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = tab.title,
                tint = fg,
                modifier = Modifier.size(15.dp)
            )

            Text(
                text = tab.title,
                color = fg,
                fontSize = 12.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )

            if (count != null && count > 0) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (selected) Color(0xFF333333) else Color(0xFF1A1A1A))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = count.toString(),
                        color = if (selected) Color.White else Color(0xFF888888),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
