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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
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
    modifier: Modifier = Modifier
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val selectedAccount by viewModel.accountRepository.selectedAccount.collectAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
            .background(Color(0xFF080808))
            .border(androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E1E1E)))
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Prominent High-Impact HD Ezz Brand Block
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { viewModel.navigateTo(NavigationScreen.HOME) }
        ) {
            EzzLogo(size = 46.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "EZZ",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "LAUNCHER",
                        color = Color(0xFFE0E0E0),
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        letterSpacing = 1.5.sp
                    )
                }
                Text(
                    text = "Minecraft Java Edition",
                    color = Color(0xFF888888),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Center: Primary Navigation Tabs (Floating minimal pill container)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF111111))
                .border(1.dp, Color(0xFF222222), RoundedCornerShape(10.dp))
                .padding(4.dp)
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
                label = "SETTINGS",
                icon = Icons.Default.Settings,
                isSelected = currentScreen == NavigationScreen.SETTINGS,
                onClick = { viewModel.navigateTo(NavigationScreen.SETTINGS) }
            )
        }

        // Right: Search, Console, Cloud Status & Premium Account Widget
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Console Shortcut Action
            TopBarIconAction(
                icon = Icons.Default.Terminal,
                contentDescription = "Console Logs (Ctrl+L)",
                isActive = currentScreen == NavigationScreen.CONSOLE,
                onClick = { viewModel.navigateTo(NavigationScreen.CONSOLE) }
            )



            // Integrated Header Account Widget
            val account = selectedAccount
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF141414))
                    .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(8.dp))
                    .clickable { viewModel.navigateTo(NavigationScreen.ACCOUNTS) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MinecraftSkinHead(
                    account = account,
                    skinManager = viewModel.skinService,
                    size = 32.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = account?.username ?: "No Account",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = when (account?.type) {
                            AccountType.MICROSOFT -> "Microsoft Account"
                            AccountType.OFFLINE -> "Offline Account"
                            null -> "Select an account"
                        },
                        color = Color(0xFF888888),
                        fontSize = 10.sp,
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
            isSelected -> Color(0xFF242424)
            isHovered -> Color(0xFF1A1A1A)
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
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
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
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = 12.sp,
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
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) Color(0xFF242424) else if (isHovered) Color(0xFF1C1C1C) else Color(0xFF141414))
            .border(1.dp, if (isActive) Color.White else if (isHovered) Color(0xFF383838) else Color(0xFF242424), RoundedCornerShape(8.dp))
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
            modifier = Modifier.size(17.dp)
        )
    }
}
