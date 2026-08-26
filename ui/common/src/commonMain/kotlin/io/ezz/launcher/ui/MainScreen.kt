package io.ezz.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import io.ezz.launcher.ui.components.MobileBottomNavBar
import io.ezz.launcher.ui.components.MobileTopBar
import io.ezz.launcher.ui.components.Sidebar
import io.ezz.launcher.ui.console.ConsoleScreen
import io.ezz.launcher.ui.dialogs.AddOfflineAccountDialog
import io.ezz.launcher.ui.dialogs.CreateInstanceDialog
import io.ezz.launcher.ui.dialogs.EditInstanceDialog
import io.ezz.launcher.ui.dialogs.MicrosoftLoginDialog
import io.ezz.launcher.ui.home.HomeScreen
import io.ezz.launcher.ui.instances.InstancesScreen
import io.ezz.launcher.ui.settings.SettingsScreen
import io.ezz.launcher.ui.theme.EzzColors
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

    val showCreateInstance by viewModel.showCreateInstanceDialog.collectAsState()
    val showEditInstance by viewModel.showEditInstanceDialog.collectAsState()
    val showAddOfflineAccount by viewModel.showAddOfflineAccountDialog.collectAsState()
    val showMicrosoftLogin by viewModel.showMicrosoftLoginDialog.collectAsState()

    EzzTheme {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .background(EzzColors.Background)
        ) {
            val isCompact = maxWidth < 720.dp

            if (isCompact) {
                // Mobile Layout: Top Bar + Content + Bottom Nav Bar
                Column(modifier = Modifier.fillMaxSize()) {
                    MobileTopBar(
                        accountName = selectedAccount?.username,
                        accountType = selectedAccount?.type?.name?.lowercase()?.replaceFirstChar { it.uppercase() },
                        onAccountClick = { viewModel.navigateTo(NavigationScreen.ACCOUNTS) }
                    )

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (currentScreen) {
                            NavigationScreen.HOME -> HomeScreen(viewModel = viewModel)
                            NavigationScreen.INSTANCES -> InstancesScreen(viewModel = viewModel)
                            NavigationScreen.ACCOUNTS -> AccountsScreen(viewModel = viewModel)
                            NavigationScreen.SETTINGS -> SettingsScreen(viewModel = viewModel)
                            NavigationScreen.CONSOLE -> ConsoleScreen(viewModel = viewModel)
                        }
                    }

                    MobileBottomNavBar(
                        currentScreen = currentScreen,
                        onNavigate = { viewModel.navigateTo(it) }
                    )
                }
            } else {
                // Desktop Layout: Sidebar + Content
                Row(modifier = Modifier.fillMaxSize()) {
                    Sidebar(
                        currentScreen = currentScreen,
                        onNavigate = { viewModel.navigateTo(it) },
                        accountName = selectedAccount?.username,
                        accountType = selectedAccount?.type?.name?.lowercase()?.replaceFirstChar { it.uppercase() }
                    )

                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        when (currentScreen) {
                            NavigationScreen.HOME -> HomeScreen(viewModel = viewModel)
                            NavigationScreen.INSTANCES -> InstancesScreen(viewModel = viewModel)
                            NavigationScreen.ACCOUNTS -> AccountsScreen(viewModel = viewModel)
                            NavigationScreen.SETTINGS -> SettingsScreen(viewModel = viewModel)
                            NavigationScreen.CONSOLE -> ConsoleScreen(viewModel = viewModel)
                        }
                    }
                }
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

            if (showAddOfflineAccount) {
                AddOfflineAccountDialog(
                    viewModel = viewModel,
                    onDismiss = { viewModel.showAddOfflineAccountDialog.value = false }
                )
            }

            if (showMicrosoftLogin) {
                MicrosoftLoginDialog(
                    viewModel = viewModel,
                    onDismiss = { viewModel.showMicrosoftLoginDialog.value = false }
                )
            }

            // Global Error Banner
            val errorMessage by viewModel.errorMessage.collectAsState()
            if (errorMessage != null) {
                Dialog(onDismissRequest = { viewModel.clearError() }) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 450.dp)
                            .fillMaxWidth(0.9f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(EzzColors.Surface)
                            .border(1.dp, EzzColors.Secondary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = EzzColors.Secondary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Launcher Notice",
                                    color = EzzColors.TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = errorMessage ?: "",
                                color = EzzColors.TextSecondary,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Button(
                                onClick = { viewModel.clearError() },
                                colors = ButtonDefaults.buttonColors(containerColor = EzzColors.Primary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Dismiss", color = EzzColors.Background, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
