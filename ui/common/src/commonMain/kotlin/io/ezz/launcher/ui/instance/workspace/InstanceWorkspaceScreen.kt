package io.ezz.launcher.ui.instance.workspace

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.ezz.launcher.core.model.instance.InstanceManagerTab
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.instance.dialogs.InstanceDeleteDialog
import io.ezz.launcher.ui.instance.dialogs.InstanceRenameDialog
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

@Composable
fun InstanceWorkspaceScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val selectedInstance by viewModel.selectedInstance.collectAsState()
    val activeTab by viewModel.activeManageTab.collectAsState()
    val runningSessions by viewModel.runningSessions.collectAsState()
    val launchProgress by viewModel.launchProgressState.collectAsState()
    val activeDownload by viewModel.activeDownloadState.collectAsState()
    val launchErrorData by viewModel.launchErrorDialogData.collectAsState()

    // Real-time Content Item Counts
    val manageMods by viewModel.manageMods.collectAsState()
    val manageResourcePacks by viewModel.manageResourcePacks.collectAsState()
    val manageShaders by viewModel.manageShaders.collectAsState()
    val manageWorlds by viewModel.manageWorlds.collectAsState()
    val manageScreenshots by viewModel.manageScreenshots.collectAsState()

    // Modals & Dialog States
    val viewerScreenshot by viewModel.selectedScreenshotForViewer.collectAsState()
    val showRepair by viewModel.showRepairDialog.collectAsState()
    val duplicateInstance by viewModel.showDuplicateInstanceDialog.collectAsState()
    val exportInstance by viewModel.showExportInstanceDialog.collectAsState()
    val worldBackupTarget by viewModel.showWorldBackupRestoreDialog.collectAsState()
    val fileConflict by viewModel.fileConflictState.collectAsState()

    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // EMPTY SELECTION STATE
    val currentInstance = selectedInstance
    if (currentInstance == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF07080A)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10131A))
                        .border(1.dp, Color(0xFF1B1F2C), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "No Instance Selected",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Choose an instance from your fleet to view its workspace, mods, and settings.",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )

                EzzButton(
                    text = "Return to Instances",
                    icon = Icons.Default.ArrowBack,
                    onClick = { viewModel.navigateTo(NavigationScreen.INSTANCES) },
                    variant = EzzButtonVariant.PRIMARY,
                    size = EzzButtonSize.MEDIUM
                )
            }
        }
        return
    }

    val session = runningSessions[currentInstance.id]
    val isRunning = session != null
    val isLaunching = launchProgress?.instanceId == currentInstance.id

    val runningTimeFormatted = session?.let {
        val totalSecs = (System.currentTimeMillis() - it.startedAt) / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        val hours = mins / 60
        if (hours > 0) String.format("%02d:%02d:%02d", hours, mins % 60, secs)
        else String.format("%02d:%02d", mins, secs)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07080A))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. TOP WORKSPACE HEADER
            InstanceWorkspaceHeader(
                instance = currentInstance,
                isRunning = isRunning,
                isLaunching = isLaunching,
                runningTimeFormatted = runningTimeFormatted,
                onBackToFleet = { viewModel.navigateTo(NavigationScreen.INSTANCES) },
                onLaunchOrStop = {
                    if (isRunning) {
                        viewModel.stopInstance(currentInstance.id)
                    } else {
                        viewModel.launchInstance(currentInstance)
                    }
                },
                onFavoriteToggle = {
                    viewModel.updateInstance(currentInstance.copy(isFavorite = !currentInstance.isFavorite))
                },
                onOpenFolder = {
                    viewModel.openInstanceFolder(currentInstance.id)
                },
                onRename = { showRenameDialog = true },
                onDuplicate = { viewModel.showDuplicateInstanceDialog.value = currentInstance },
                onExport = { viewModel.showExportInstanceDialog.value = currentInstance },
                onRepair = { viewModel.showRepairDialog.value = true },
                onDelete = { showDeleteDialog = true }
            )

            // 2. HORIZONTAL TAB NAVIGATION BAR
            InstanceWorkspaceTabNav(
                activeTab = activeTab,
                onTabSelect = { viewModel.setManageTab(it) },
                modsCount = manageMods.size,
                resourcePacksCount = manageResourcePacks.size,
                shadersCount = manageShaders.size,
                worldsCount = manageWorlds.size,
                screenshotsCount = manageScreenshots.size
            )

            // 3. TAB CONTENT AREA
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        val enter = fadeIn() + slideInHorizontally { width -> if (targetState.ordinal > initialState.ordinal) width / 4 else -width / 4 }
                        val exit = fadeOut() + slideOutHorizontally { width -> if (targetState.ordinal > initialState.ordinal) -width / 4 else width / 4 }
                        enter togetherWith exit
                    },
                    label = "InstanceWorkspaceTabAnimation"
                ) { targetTab ->
                    when (targetTab) {
                        InstanceManagerTab.OVERVIEW -> OverviewTab(instance = currentInstance, viewModel = viewModel)
                        InstanceManagerTab.MODS -> ModsTab(instance = currentInstance, viewModel = viewModel)
                        InstanceManagerTab.RESOURCE_PACKS -> ResourcePacksTab(instance = currentInstance, viewModel = viewModel)
                        InstanceManagerTab.SHADERS -> ShadersTab(instance = currentInstance, viewModel = viewModel)
                        InstanceManagerTab.WORLDS -> WorldsTab(instance = currentInstance, viewModel = viewModel)
                        InstanceManagerTab.SCREENSHOTS -> ScreenshotsTab(instance = currentInstance, viewModel = viewModel)
                        InstanceManagerTab.SETTINGS -> InstanceSettingsTab(instance = currentInstance, viewModel = viewModel)
                        InstanceManagerTab.FILES -> FilesTab(instance = currentInstance, viewModel = viewModel)
                        InstanceManagerTab.LOGS -> LogsTab(instance = currentInstance, viewModel = viewModel)
                    }
                }
            }

            // 4. PINNED BOTTOM DOCK
            InstanceLaunchDock(
                instance = currentInstance,
                isRunning = isRunning,
                isLaunching = isLaunching,
                runningTimeFormatted = runningTimeFormatted,
                activeDownload = activeDownload,
                launchError = launchErrorData,
                onLaunchOrStop = {
                    if (isRunning) {
                        viewModel.stopInstance(currentInstance.id)
                    } else {
                        viewModel.launchInstance(currentInstance)
                    }
                },
                onClearError = { viewModel.launchErrorDialogData.value = null }
            )
        }

        // DIALOGS & OVERLAYS

        // Rename Dialog
        if (showRenameDialog) {
            InstanceRenameDialog(
                instance = currentInstance,
                onDismiss = { showRenameDialog = false },
                onConfirmRename = { newName ->
                    viewModel.updateInstance(currentInstance.copy(name = newName))
                    showRenameDialog = false
                }
            )
        }

        // Delete Dialog
        if (showDeleteDialog) {
            InstanceDeleteDialog(
                instance = currentInstance,
                onDismiss = { showDeleteDialog = false },
                onConfirmDelete = {
                    showDeleteDialog = false
                    viewModel.deleteInstance(currentInstance.id)
                    viewModel.navigateTo(NavigationScreen.INSTANCES)
                }
            )
        }

        // Screenshot Viewer Modal
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
        val dup = duplicateInstance
        if (dup != null) {
            InstanceDuplicateDialog(
                sourceInstance = dup,
                viewModel = viewModel,
                onDismiss = { viewModel.showDuplicateInstanceDialog.value = null }
            )
        }

        // Export Dialog
        val exp = exportInstance
        if (exp != null) {
            InstanceExportDialog(
                sourceInstance = exp,
                viewModel = viewModel,
                onDismiss = { viewModel.showExportInstanceDialog.value = null }
            )
        }

        // World Backup History Dialog
        val world = worldBackupTarget
        if (world != null) {
            WorldBackupRestoreDialog(
                world = world,
                viewModel = viewModel,
                onDismiss = { viewModel.showWorldBackupRestoreDialog.value = null }
            )
        }

        // Safe File Conflict Prompt Dialog
        val conflict = fileConflict
        if (conflict != null) {
            Dialog(
                onDismissRequest = { conflict.onCancel() },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .width(440.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF10131A))
                        .border(1.dp, Color(0xFF1B1F2C), RoundedCornerShape(12.dp))
                        .padding(22.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                conflict.title,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = conflict.message,
                            color = Color(0xFFCBD5E1),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EzzButton(
                                text = "Cancel",
                                onClick = { conflict.onCancel() },
                                variant = EzzButtonVariant.GHOST,
                                size = EzzButtonSize.SMALL
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            EzzButton(
                                text = "Replace",
                                onClick = { conflict.onConfirmReplace() },
                                variant = EzzButtonVariant.DANGER,
                                size = EzzButtonSize.SMALL
                            )
                        }
                    }
                }
            }
        }
    }
}
