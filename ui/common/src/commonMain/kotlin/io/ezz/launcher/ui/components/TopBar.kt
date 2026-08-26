package io.ezz.launcher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.account.AccountType
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.AppViewModel
import io.ezz.launcher.ui.viewmodel.NavigationScreen

@Composable
fun TopBar(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = { viewModel.showSearchDialog.value = true }
) {
    val colors = EzzTheme.colors
    val currentScreen by viewModel.currentScreen.collectAsState()
    val isConnected by viewModel.isSupabaseConnected.collectAsState()
    val selectedAccount by viewModel.accountRepository.selectedAccount.collectAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(colors.surface)
            .border(androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.6f)))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Branding Logo & Name
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { viewModel.navigateTo(NavigationScreen.HOME) }
        ) {
            Image(
                painter = painterResource("logo.png"),
                contentDescription = "Ezz Launcher Mascot",
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "EZZ",
                        color = colors.primary,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "LAUNCHER",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "Minecraft Java Edition",
                    color = colors.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Center: Primary Navigation Tabs
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TopNavTab(
                label = "Home",
                icon = Icons.Default.Home,
                isSelected = currentScreen == NavigationScreen.HOME,
                onClick = { viewModel.navigateTo(NavigationScreen.HOME) }
            )
            TopNavTab(
                label = "Instances",
                icon = Icons.Default.Apps,
                isSelected = currentScreen == NavigationScreen.INSTANCES,
                onClick = { viewModel.navigateTo(NavigationScreen.INSTANCES) }
            )
            TopNavTab(
                label = "Accounts",
                icon = Icons.Default.AccountCircle,
                isSelected = currentScreen == NavigationScreen.ACCOUNTS,
                onClick = { viewModel.navigateTo(NavigationScreen.ACCOUNTS) }
            )
            TopNavTab(
                label = "Mods",
                icon = Icons.Default.Extension,
                isSelected = currentScreen == NavigationScreen.MODS,
                onClick = { viewModel.navigateTo(NavigationScreen.MODS) }
            )
            TopNavTab(
                label = "Servers",
                icon = Icons.Default.Dns,
                isSelected = currentScreen == NavigationScreen.SERVERS,
                onClick = { viewModel.navigateTo(NavigationScreen.SERVERS) }
            )
            TopNavTab(
                label = "Settings",
                icon = Icons.Default.Settings,
                isSelected = currentScreen == NavigationScreen.SETTINGS,
                onClick = { viewModel.navigateTo(NavigationScreen.SETTINGS) }
            )
        }

        // Right: Account pill, Search, Console shortcut & Cloud status
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Search Quick Trigger
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.inputBackground)
                    .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                    .clickable { onSearchClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Console Shortcut
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (currentScreen == NavigationScreen.CONSOLE) colors.primary.copy(alpha = 0.15f) else colors.inputBackground)
                    .border(1.dp, if (currentScreen == NavigationScreen.CONSOLE) colors.primary else colors.border, RoundedCornerShape(8.dp))
                    .clickable { viewModel.navigateTo(NavigationScreen.CONSOLE) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = "Console Logs",
                    tint = if (currentScreen == NavigationScreen.CONSOLE) colors.primary else colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Cloud Status Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.inputBackground)
                    .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                when (isConnected) {
                                    true -> colors.accent
                                    false -> colors.danger
                                    null -> colors.warning
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (isConnected) {
                            true -> "Cloud Sync"
                            false -> "Offline"
                            null -> "Connecting"
                        },
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Selected Account Pill
            val account = selectedAccount
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.cardBackground)
                    .border(1.dp, colors.borderLight, RoundedCornerShape(8.dp))
                    .clickable { viewModel.navigateTo(NavigationScreen.ACCOUNTS) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.surfaceLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Avatar",
                        tint = if (account?.type == AccountType.MICROSOFT) colors.primary else colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = account?.username ?: "No Account",
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (account?.type == AccountType.MICROSOFT) "Microsoft" else "Offline",
                        color = if (account?.type == AccountType.MICROSOFT) colors.primary else colors.textMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun TopNavTab(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = EzzTheme.colors
    val bgColor by animateColorAsState(
        if (isSelected) colors.primary.copy(alpha = 0.12f) else Color.Transparent
    )
    val borderColor by animateColorAsState(
        if (isSelected) colors.primary.copy(alpha = 0.7f) else Color.Transparent
    )
    val contentColor by animateColorAsState(
        if (isSelected) colors.primary else colors.textSecondary
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = if (isSelected) colors.textPrimary else colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
