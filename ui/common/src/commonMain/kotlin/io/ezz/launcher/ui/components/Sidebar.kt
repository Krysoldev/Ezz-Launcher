package io.ezz.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.ui.theme.EzzColors
import io.ezz.launcher.ui.viewmodel.NavigationScreen

@Composable
fun Sidebar(
    currentScreen: NavigationScreen,
    onNavigate: (NavigationScreen) -> Unit,
    accountName: String?,
    accountType: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(240.dp)
            .background(EzzColors.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            // App Branding
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(EzzColors.Primary, EzzColors.Secondary)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "E",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "EZZ LAUNCHER",
                        color = EzzColors.TextPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Minecraft Java Edition",
                        color = EzzColors.Primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Navigation Items
            NavItem(
                title = "Home",
                icon = Icons.Default.Home,
                isSelected = currentScreen == NavigationScreen.HOME,
                onClick = { onNavigate(NavigationScreen.HOME) }
            )
            Spacer(modifier = Modifier.height(6.dp))

            NavItem(
                title = "Instances",
                icon = Icons.Default.Apps,
                isSelected = currentScreen == NavigationScreen.INSTANCES,
                onClick = { onNavigate(NavigationScreen.INSTANCES) }
            )
            Spacer(modifier = Modifier.height(6.dp))

            NavItem(
                title = "Accounts",
                icon = Icons.Default.AccountCircle,
                isSelected = currentScreen == NavigationScreen.ACCOUNTS,
                onClick = { onNavigate(NavigationScreen.ACCOUNTS) }
            )
            Spacer(modifier = Modifier.height(6.dp))

            NavItem(
                title = "Console / Logs",
                icon = Icons.Default.Terminal,
                isSelected = currentScreen == NavigationScreen.CONSOLE,
                onClick = { onNavigate(NavigationScreen.CONSOLE) }
            )
            Spacer(modifier = Modifier.height(6.dp))

            NavItem(
                title = "Settings",
                icon = Icons.Default.Settings,
                isSelected = currentScreen == NavigationScreen.SETTINGS,
                onClick = { onNavigate(NavigationScreen.SETTINGS) }
            )
        }

        // Active Account Chip at Bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(EzzColors.SurfaceVariant)
                .clickable { onNavigate(NavigationScreen.ACCOUNTS) }
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(EzzColors.PrimaryGlow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = EzzColors.Primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = accountName ?: "No Account Selected",
                        color = EzzColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = accountType ?: "Click to add",
                        color = EzzColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) EzzColors.SurfaceVariant else Color.Transparent
    val contentColor = if (isSelected) EzzColors.Primary else EzzColors.TextSecondary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                color = if (isSelected) EzzColors.TextPrimary else EzzColors.TextSecondary,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}
