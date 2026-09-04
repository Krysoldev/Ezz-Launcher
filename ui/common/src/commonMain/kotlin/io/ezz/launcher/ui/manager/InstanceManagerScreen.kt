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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import io.ezz.launcher.ui.components.CompactRuntimeBadge
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
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

    // Modals
    val manageScreenshots by viewModel.manageScreenshots.collectAsState()
    val viewerScreenshot by viewModel.selectedScreenshotForViewer.collectAsState()
    val showRepair by viewModel.showRepairDialog.collectAsState()
    val duplicateInstance by viewModel.showDuplicateInstanceDialog.collectAsState()
    val exportInstance by viewModel.showExportInstanceDialog.collectAsState()
    val worldBackupTarget by viewModel.showWorldBackupRestoreDialog.collectAsState()

    if (selectedInstance == null) {
        Box(
            modifier = modifier.fillMaxSize().background(Color(0xFF07080A)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("No Instance Selected", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                EzzButton(
                    text = "Go to Instances",
                    onClick = { viewModel.navigateTo(NavigationScreen.INSTANCES) },
                    variant = EzzButtonVariant.PRIMARY,
                    size = EzzButtonSize.MEDIUM
                )
            }
        }
        return
    }

    val instance = selectedInstance!!
    val isRunning = runningSessions.containsKey(instance.id)
    val session = runningSessions[instance.id]
    val javaReq = io.ezz.launcher.core.minecraft.version.JavaCompatibility.getRequiredJavaMajorVersion(instance.minecraftVersion)

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF07080A))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1200.dp)
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Header Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF10131A))
                    .border(1.dp, Color(0xFF1B1F2C), RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Back button + Artwork + Name & Badges
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Back Button
                        val backInteraction = remember { MutableInteractionSource() }
                        val isBackHovered by backInteraction.collectIsHoveredAsState()

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isBackHovered) Color(0xFF181C28) else Color(0xFF141720))
                                .border(1.dp, if (isBackHovered) Color(0xFF323A4E) else Color(0xFF222735), RoundedCornerShape(8.dp))
                                .clickable(
                                    interactionSource = backInteraction,
                                    indication = null,
                                    onClick = { viewModel.navigateTo(NavigationScreen.HOME) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = if (isBackHovered) Color.White else Color(0xFFCBD5E1),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Instance Artwork Icon
                        InstanceArtworkIcon(
                            instance = instance,
                            size = 50.dp
                        )

                        // Title & Badges
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = instance.name,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                if (isRunning && session != null) {
                                    RuntimeDisplay(
                                        startedAt = session.startedAt,
                                        showPrefix = true,
                                        prefixText = "RUNNING",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        dotColor = Color(0xFF10B981)
                                    )
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF10B981))
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "Ready to Play",
                                            color = Color(0xFF10B981),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Badges Row
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ManagerHeaderBadge("MC ${instance.minecraftVersion}")
                                ManagerHeaderBadge(instance.loaderType.name)
                                ManagerHeaderBadge("Java $javaReq")
                                ManagerHeaderBadge("${instance.maxMemoryMb} MB RAM")
                            }
                        }
                    }

                    // Right: Last Played + Open Folder + More Menu + Primary Play CTA
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Last played info
                        val lastPlayed = instance.lastPlayedAt
                        val lastPlayedText = if (lastPlayed != null && lastPlayed > 0) {
                            "Last played: ${SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(lastPlayed))}"
                        } else {
                            "Not played yet"
                        }
                        Text(lastPlayedText, color = Color(0xFF64748B), fontSize = 11.5.sp, fontWeight = FontWeight.Medium)

                        // Open Folder Button
                        EzzButton(
                            text = "Folder",
                            onClick = { viewModel.openInstanceFolder(instance.id) },
                            icon = Icons.Default.FolderOpen,
                            variant = EzzButtonVariant.SECONDARY,
                            size = EzzButtonSize.MEDIUM
                        )

                        // More Options Dropdown
                        var showMoreMenu by remember { mutableStateOf(false) }
                        val moreInteraction = remember { MutableInteractionSource() }
                        val isMoreHovered by moreInteraction.collectIsHoveredAsState()

                        Box {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isMoreHovered) Color(0xFF181C28) else Color(0xFF141720))
                                    .border(1.dp, if (isMoreHovered) Color(0xFF323A4E) else Color(0xFF222735), RoundedCornerShape(8.dp))
                                    .clickable(
                                        interactionSource = moreInteraction,
                                        indication = null,
                                        onClick = { showMoreMenu = true }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More Actions",
                                    tint = if (isMoreHovered) Color.White else Color(0xFFCBD5E1),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                                modifier = Modifier
                                    .background(Color(0xFF141720))
                                    .border(1.dp, Color(0xFF222735), RoundedCornerShape(8.dp))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Duplicate Instance", color = Color.White, fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp)) },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.showDuplicateInstanceDialog.value = instance
                                    },
                                    colors = MenuDefaults.itemColors(textColor = Color.White)
                                )
                                DropdownMenuItem(
                                    text = { Text("Export Instance (.zip)", color = Color.White, fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp)) },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.showExportInstanceDialog.value = instance
                                    },
                                    colors = MenuDefaults.itemColors(textColor = Color.White)
                                )
                                DropdownMenuItem(
                                    text = { Text("Repair & Verify Assets", color = Color.White, fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.Build, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp)) },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.showRepairDialog.value = true
                                    },
                                    colors = MenuDefaults.itemColors(textColor = Color.White)
                                )
                            }
                        }

                        // Primary Play / Stop Button
                        if (isRunning) {
                            EzzButton(
                                text = "Stop",
                                onClick = { viewModel.stopInstance(instance.id) },
                                icon = Icons.Default.Stop,
                                variant = EzzButtonVariant.DANGER,
                                size = EzzButtonSize.MEDIUM
                            )
                        } else {
                            EzzButton(
                                text = "PLAY",
                                onClick = { viewModel.launchInstance(instance) },
                                icon = Icons.Default.PlayArrow,
                                variant = EzzButtonVariant.PRIMARY,
                                size = EzzButtonSize.MEDIUM
                            )
                        }
                    }
                }
            }

            // 2. Tab Navigation Strip Card (Pure Clean Navigation - No Numeric Badges)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF10131A))
                    .border(1.dp, Color(0xFF1B1F2C), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactPillTab(
                        tab = InstanceManagerTab.OVERVIEW,
                        icon = Icons.Default.Apps,
                        selected = activeTab == InstanceManagerTab.OVERVIEW
                    ) { viewModel.setManageTab(InstanceManagerTab.OVERVIEW) }

                    CompactPillTab(
                        tab = InstanceManagerTab.MODS,
                        icon = Icons.Default.Extension,
                        selected = activeTab == InstanceManagerTab.MODS
                    ) { viewModel.setManageTab(InstanceManagerTab.MODS) }

                    CompactPillTab(
                        tab = InstanceManagerTab.RESOURCE_PACKS,
                        icon = Icons.Default.Palette,
                        selected = activeTab == InstanceManagerTab.RESOURCE_PACKS
                    ) { viewModel.setManageTab(InstanceManagerTab.RESOURCE_PACKS) }

                    CompactPillTab(
                        tab = InstanceManagerTab.SHADERS,
                        icon = Icons.Default.Layers,
                        selected = activeTab == InstanceManagerTab.SHADERS
                    ) { viewModel.setManageTab(InstanceManagerTab.SHADERS) }

                    CompactPillTab(
                        tab = InstanceManagerTab.WORLDS,
                        icon = Icons.Default.Public,
                        selected = activeTab == InstanceManagerTab.WORLDS
                    ) { viewModel.setManageTab(InstanceManagerTab.WORLDS) }

                    CompactPillTab(
                        tab = InstanceManagerTab.SCREENSHOTS,
                        icon = Icons.Default.Image,
                        selected = activeTab == InstanceManagerTab.SCREENSHOTS
                    ) { viewModel.setManageTab(InstanceManagerTab.SCREENSHOTS) }

                    CompactPillTab(
                        tab = InstanceManagerTab.SETTINGS,
                        icon = Icons.Default.Settings,
                        selected = activeTab == InstanceManagerTab.SETTINGS
                    ) { viewModel.setManageTab(InstanceManagerTab.SETTINGS) }

                    CompactPillTab(
                        tab = InstanceManagerTab.FILES,
                        icon = Icons.Default.Folder,
                        selected = activeTab == InstanceManagerTab.FILES
                    ) { viewModel.setManageTab(InstanceManagerTab.FILES) }

                    CompactPillTab(
                        tab = InstanceManagerTab.LOGS,
                        icon = Icons.Default.Terminal,
                        selected = activeTab == InstanceManagerTab.LOGS
                    ) { viewModel.setManageTab(InstanceManagerTab.LOGS) }
                }
            }

            // 3. Tab Content Area
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
 * Pill badge for instance manager header.
 */
@Composable
private fun ManagerHeaderBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF161A24))
            .border(1.dp, Color(0xFF1B1F2C), RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = text.replace(" ", "\u00A0"),
            color = Color(0xFFCBD5E1),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Clean Horizontal Tab with active underline accent indicator (No numeric badges, no oversized pills).
 */
@Composable
private fun CompactPillTab(
    tab: InstanceManagerTab,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val contentColor by androidx.compose.animation.animateColorAsState(
        targetValue = when {
            selected -> Color.White
            isHovered -> Color(0xFFF1F5F9)
            else -> Color(0xFF94A3B8)
        },
        animationSpec = tween(150)
    )

    val underlineColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) Color(0xFF8B5CF6) else Color.Transparent,
        animationSpec = tween(180)
    )

    val itemBg by androidx.compose.animation.animateColorAsState(
        targetValue = when {
            selected -> Color(0x1A8B5CF6)
            isHovered -> Color(0xFF161A24)
            else -> Color.Transparent
        },
        animationSpec = tween(150)
    )

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(itemBg)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = tab.title,
                tint = if (selected) Color(0xFFA78BFA) else if (isHovered) Color.White else Color(0xFF64748B),
                modifier = Modifier.size(14.dp)
            )

            Text(
                text = tab.title,
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }

        // Active Underline Indicator
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(underlineColor)
        )
    }
}
