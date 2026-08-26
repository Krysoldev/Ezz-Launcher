package io.ezz.launcher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
    val currentScreen by viewModel.currentScreen.collectAsState()
    val isConnected by viewModel.isSupabaseConnected.collectAsState()
    val selectedAccount by viewModel.accountRepository.selectedAccount.collectAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(Color(0xFF0A0A0A))
            .border(androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF242424)))
            .padding(horizontal = 18.dp),
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
            EzzLogo(size = 30.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "EZZ",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "LAUNCHER",
                        color = Color(0xFFD4D4D4),
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "Minecraft Java Edition",
                    color = Color(0xFF777777),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Center: Primary Navigation Tabs (High contrast minimal)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF101010))
                .border(1.dp, Color(0xFF202020), RoundedCornerShape(8.dp))
                .padding(3.dp)
        ) {
            TopNavTab(
                label = "HOME",
                icon = Icons.Default.Home,
                isSelected = currentScreen == NavigationScreen.HOME,
                onClick = { viewModel.navigateTo(NavigationScreen.HOME) }
            )
            TopNavTab(
                label = "INSTANCES",
                icon = Icons.Default.Apps,
                isSelected = currentScreen == NavigationScreen.INSTANCES,
                onClick = { viewModel.navigateTo(NavigationScreen.INSTANCES) }
            )
            TopNavTab(
                label = "ACCOUNTS",
                icon = Icons.Default.AccountCircle,
                isSelected = currentScreen == NavigationScreen.ACCOUNTS,
                onClick = { viewModel.navigateTo(NavigationScreen.ACCOUNTS) }
            )
            TopNavTab(
                label = "MODS",
                icon = Icons.Default.Extension,
                isSelected = currentScreen == NavigationScreen.MODS,
                onClick = { viewModel.navigateTo(NavigationScreen.MODS) }
            )
            TopNavTab(
                label = "SERVERS",
                icon = Icons.Default.Dns,
                isSelected = currentScreen == NavigationScreen.SERVERS,
                onClick = { viewModel.navigateTo(NavigationScreen.SERVERS) }
            )
            TopNavTab(
                label = "SETTINGS",
                icon = Icons.Default.Settings,
                isSelected = currentScreen == NavigationScreen.SETTINGS,
                onClick = { viewModel.navigateTo(NavigationScreen.SETTINGS) }
            )
        }

        // Right: Search, Console shortcut, Cloud status & Account area
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Search Quick Trigger
            TopBarIconAction(
                icon = Icons.Default.Search,
                contentDescription = "Search",
                onClick = onSearchClick
            )

            // Console Shortcut
            TopBarIconAction(
                icon = Icons.Default.Terminal,
                contentDescription = "Console Logs",
                isActive = currentScreen == NavigationScreen.CONSOLE,
                onClick = { viewModel.navigateTo(NavigationScreen.CONSOLE) }
            )

            // Cloud Status Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF141414))
                    .border(1.dp, Color(0xFF242424), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                when (isConnected) {
                                    true -> Color(0xFF10B981)
                                    false -> Color(0xFFEF4444)
                                    null -> Color(0xFFF59E0B)
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (isConnected) {
                            true -> "Cloud"
                            false -> "Offline"
                            null -> "Syncing"
                        },
                        color = Color(0xFFA0A0A0),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Selected Account Pill
            val account = selectedAccount
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF151515))
                    .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(6.dp))
                    .clickable { viewModel.navigateTo(NavigationScreen.ACCOUNTS) }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF202020)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Avatar",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(7.dp))
                Column {
                    Text(
                        text = account?.username ?: "Offline Player",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = if (account?.type == AccountType.MICROSOFT) "Microsoft" else "Offline Account",
                        color = Color(0xFF777777),
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
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected -> Color(0xFF222222)
            isHovered -> Color(0xFF181818)
            else -> Color.Transparent
        },
        animationSpec = tween(120)
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isSelected -> Color.White
            isHovered -> Color(0xFFE5E5E5)
            else -> Color(0xFF888888)
        },
        animationSpec = tween(120)
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 11.dp, vertical = 6.dp),
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
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun TopBarIconAction(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isActive) Color(0xFF242424) else if (isHovered) Color(0xFF1E1E1E) else Color(0xFF141414))
            .border(1.dp, if (isActive) Color.White else if (isHovered) Color(0xFF383838) else Color(0xFF242424), RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive || isHovered) Color.White else Color(0xFF888888),
            modifier = Modifier.size(15.dp)
        )
    }
}
