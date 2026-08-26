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
import io.ezz.launcher.ui.components.BottomPlayBar
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.EzzToastHost
import io.ezz.launcher.ui.components.Sidebar
import io.ezz.launcher.ui.console.ConsoleScreen
import io.ezz.launcher.ui.dialogs.AddOfflineAccountDialog
import io.ezz.launcher.ui.dialogs.CreateInstanceDialog
import io.ezz.launcher.ui.dialogs.EditInstanceDialog
import io.ezz.launcher.ui.dialogs.MicrosoftLoginDialog
import io.ezz.launcher.ui.home.HomeScreen
import io.ezz.launcher.ui.instances.InstancesScreen
import io.ezz.launcher.ui.mods.ModsScreen
import io.ezz.launcher.ui.profiles.ProfilesScreen
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
    val selectedAccount by viewModel.accountRepository.selectedAccount.collectAsState()
    val isSupabaseConnected by viewModel.isSupabaseConnected.collectAsState()

    val showCreateInstance by viewModel.showCreateInstanceDialog.collectAsState()
    val showEditInstance by viewModel.showEditInstanceDialog.collectAsState()
    val showAddOfflineAccount by viewModel.showAddOfflineAccountDialog.collectAsState()
    val showMicrosoftLogin by viewModel.showMicrosoftLoginDialog.collectAsState()

    EzzTheme {
        val colors = EzzTheme.colors

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
            // Main Shell: Sidebar + Content Column (Page View + Bottom Play Bar)
            Row(modifier = Modifier.fillMaxSize()) {
                Sidebar(
                    currentScreen = currentScreen,
                    onNavigate = { viewModel.navigateTo(it) },
                    accountName = selectedAccount?.username,
                    accountType = selectedAccount?.type?.name?.lowercase()?.replaceFirstChar { it.uppercase() },
                    isSupabaseConnected = isSupabaseConnected
                )

                Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "MainScreenTransition"
                        ) { screen ->
                            when (screen) {
                                NavigationScreen.HOME -> HomeScreen(viewModel = viewModel)
                                NavigationScreen.INSTANCES -> InstancesScreen(viewModel = viewModel)
                                NavigationScreen.MODS -> ModsScreen(viewModel = viewModel)
                                NavigationScreen.ACCOUNTS -> AccountsScreen(viewModel = viewModel)
                                NavigationScreen.PROFILES -> ProfilesScreen(viewModel = viewModel)
                                NavigationScreen.SETTINGS -> SettingsScreen(viewModel = viewModel)
                                NavigationScreen.CONSOLE -> ConsoleScreen(viewModel = viewModel)
                            }
                        }
                    }

                    // Persistent Gaming Launch Bar
                    BottomPlayBar(viewModel = viewModel)
                }
            }

            // Global Notification Toast Overlay (Top-Right)
            EzzToastHost(modifier = Modifier.align(Alignment.TopEnd))

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
                MicrosoftLoginDialog(
                    viewModel = viewModel,
                    onDismiss = { viewModel.cancelMicrosoftLogin() }
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
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.danger, RoundedCornerShape(16.dp))
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
