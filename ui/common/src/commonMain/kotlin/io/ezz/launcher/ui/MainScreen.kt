package io.ezz.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.ezz.launcher.ui.accounts.AccountsScreen
import io.ezz.launcher.ui.components.DownloadProgressOverlay
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.EzzToastHost
import io.ezz.launcher.ui.components.LaunchErrorDialog
import io.ezz.launcher.ui.components.Sidebar
import io.ezz.launcher.ui.console.ConsoleScreen
import io.ezz.launcher.ui.dialogs.AddOfflineAccountDialog
import io.ezz.launcher.ui.dialogs.CreateInstanceDialog
import io.ezz.launcher.ui.dialogs.EditInstanceDialog
import io.ezz.launcher.ui.dialogs.ExportModpackDialog
import io.ezz.launcher.ui.dialogs.ImportModpackDialog
import io.ezz.launcher.ui.dialogs.MicrosoftAuthModal
import io.ezz.launcher.ui.dialogs.ModrinthModpackBrowserDialog
import io.ezz.launcher.ui.dialogs.QuickSearchDialog
import io.ezz.launcher.ui.home.HomeScreen
import io.ezz.launcher.ui.instances.InstancesScreen
import io.ezz.launcher.ui.manager.InstanceManagerScreen
import io.ezz.launcher.ui.profiles.ProfilesScreen
import io.ezz.launcher.ui.servers.ServersScreen
import io.ezz.launcher.ui.settings.SettingsScreen
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.AppViewModel
import io.ezz.launcher.ui.viewmodel.NavigationScreen

@Composable
fun MainScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val activeDownload by viewModel.activeDownloadState.collectAsState()
    val launchErrorData by viewModel.launchErrorDialogData.collectAsState()

    val showCreateInstance by viewModel.showCreateInstanceDialog.collectAsState()
    val showEditInstance by viewModel.showEditInstanceDialog.collectAsState()
    val showModpackBrowser by viewModel.showModpackBrowserDialog.collectAsState()
    val showImportModpack by viewModel.showImportModpackDialog.collectAsState()
    val showExportModpack by viewModel.showExportModpackDialog.collectAsState()
    val pendingMrpackFile by viewModel.pendingMrpackFile.collectAsState()
    val showAddOfflineAccount by viewModel.showAddOfflineAccountDialog.collectAsState()
    val showMicrosoftLogin by viewModel.showMicrosoftLoginDialog.collectAsState()
    val showSearchDialog by viewModel.showSearchDialog.collectAsState()
    val accounts by viewModel.accountRepository.accounts.collectAsState()
    val selectedAccount by viewModel.accountRepository.selectedAccount.collectAsState()

    EzzTheme {
        val colors = EzzTheme.colors

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
            // Main App Shell: Left Sidebar + Screen Content Area
            Row(modifier = Modifier.fillMaxSize()) {
                Sidebar(
                    currentScreen = currentScreen,
                    onNavigate = { screen -> viewModel.navigateTo(screen) },
                    account = selectedAccount,
                    accounts = accounts,
                    onSelectAccount = { acc -> viewModel.selectAccount(acc) },
                    skinManager = viewModel.skinService
                )

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)) +
                             androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(180)) { 8 })
                                .togetherWith(androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140)))
                        },
                        label = "MainScreenTransition"
                    ) { screen ->
                        when (screen) {
                            NavigationScreen.HOME -> HomeScreen(viewModel = viewModel)
                            NavigationScreen.INSTANCES -> InstancesScreen(viewModel = viewModel)
                            NavigationScreen.VAULT -> io.ezz.launcher.ui.vault.VaultScreen(viewModel = viewModel)
                            NavigationScreen.ACCOUNTS -> AccountsScreen(viewModel = viewModel)
                            NavigationScreen.MODS -> InstancesScreen(viewModel = viewModel)
                            NavigationScreen.RESOURCE_PACKS -> io.ezz.launcher.ui.packs.ResourcePacksScreen(viewModel = viewModel)
                            NavigationScreen.SHADERS -> io.ezz.launcher.ui.shaders.ShadersScreen(viewModel = viewModel)
                            NavigationScreen.WORLDS -> io.ezz.launcher.ui.worlds.WorldsScreen(viewModel = viewModel)
                            NavigationScreen.SCREENSHOTS -> io.ezz.launcher.ui.screenshots.ScreenshotsScreen(viewModel = viewModel)
                            NavigationScreen.SETTINGS -> SettingsScreen(viewModel = viewModel)
                            NavigationScreen.SERVERS -> ServersScreen(viewModel = viewModel)
                            NavigationScreen.PROFILES -> ProfilesScreen(viewModel = viewModel)
                            NavigationScreen.CONSOLE -> ConsoleScreen(viewModel = viewModel)
                            NavigationScreen.INSTANCE_MANAGER -> InstanceManagerScreen(viewModel = viewModel)
                        }
                    }

                    // Floating Download & Installation HUD Overlay
                    DownloadProgressOverlay(
                        state = activeDownload,
                        onCancel = {
                            viewModel.activeDownloadState.value = null
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }

            // Global Notification Toast Overlay (Top-Right)
            EzzToastHost(modifier = Modifier.align(Alignment.TopEnd))

            // Quick Search Modal
            if (showSearchDialog) {
                QuickSearchDialog(
                    viewModel = viewModel,
                    onDismiss = { viewModel.showSearchDialog.value = false }
                )
            }

            // Launch Error Diagnostic Dialog (Non-crashing)
            if (launchErrorData != null) {
                LaunchErrorDialog(
                    data = launchErrorData,
                    onDismiss = { viewModel.launchErrorDialogData.value = null },
                    onViewLogs = {
                        viewModel.launchErrorDialogData.value = null
                        viewModel.navigateTo(NavigationScreen.CONSOLE)
                    },
                    onRepair = {
                        viewModel.launchErrorDialogData.value = null
                        viewModel.repairInstance()
                    },
                    onCopyDiagnostics = { diag ->
                        viewModel.platformBridge.copyToClipboard(diag)
                    }
                )
            }

            // Dialog Overlays
            if (showCreateInstance) {
                CreateInstanceDialog(
                    viewModel = viewModel,
                    onDismiss = { viewModel.showCreateInstanceDialog.value = false }
                )
            }

            if (showEditInstance != null) {
                EditInstanceDialog(
                    instance = showEditInstance!!,
                    viewModel = viewModel,
                    onDismiss = { viewModel.showEditInstanceDialog.value = null }
                )
            }

            if (showModpackBrowser) {
                ModrinthModpackBrowserDialog(
                    viewModel = viewModel,
                    onDismiss = { viewModel.showModpackBrowserDialog.value = false }
                )
            }

            if (showImportModpack) {
                ImportModpackDialog(
                    viewModel = viewModel,
                    initialFile = pendingMrpackFile,
                    onDismiss = { viewModel.closeImportModpack() }
                )
            }

            if (showExportModpack != null) {
                ExportModpackDialog(
                    instance = showExportModpack!!,
                    viewModel = viewModel,
                    onDismiss = { viewModel.showExportModpackDialog.value = null }
                )
            }

            if (showAddOfflineAccount) {
                AddOfflineAccountDialog(
                    onDismiss = { viewModel.showAddOfflineAccountDialog.value = false },
                    onConfirm = { username ->
                        viewModel.addOfflineAccount(username)
                        viewModel.showAddOfflineAccountDialog.value = false
                    }
                )
            }

            if (showMicrosoftLogin) {
                MicrosoftAuthModal(
                    viewModel = viewModel,
                    onDismiss = { viewModel.cancelMicrosoftLogin() }
                )
            }

            val activeModInstallProject by viewModel.activeModInstallProject.collectAsState()
            if (activeModInstallProject != null) {
                io.ezz.launcher.ui.dialogs.InstallModDialog(
                    project = activeModInstallProject!!,
                    viewModel = viewModel,
                    onDismiss = { viewModel.closeModInstaller() }
                )
            }

            // Error Banner Dialog
            val errorMessage by viewModel.errorMessage.collectAsState()
            if (errorMessage != null) {
                Dialog(onDismissRequest = { viewModel.clearError() }) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 440.dp)
                            .fillMaxWidth(0.9f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.danger, RoundedCornerShape(12.dp))
                            .padding(24.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = colors.danger
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Notice",
                                color = colors.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage!!,
                                color = colors.textSecondary,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            EzzButton(
                                text = "Dismiss",
                                onClick = { viewModel.clearError() },
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
