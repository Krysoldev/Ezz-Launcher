package io.ezz.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
fun MobileTopBar(
    accountName: String?,
    accountType: String?,
    onAccountClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(EzzColors.Surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Mascot Logo & Title
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                    text = "🦁",
                    fontSize = 20.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "EZZ LAUNCHER",
                    color = EzzColors.TextPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Cross-Platform Minecraft",
                    color = EzzColors.Primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Account Chip
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(EzzColors.SurfaceVariant)
                .border(1.dp, EzzColors.Border, RoundedCornerShape(20.dp))
                .clickable { onAccountClick() }
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (accountName != null) EzzColors.Accent else EzzColors.Border),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = accountName ?: "No Account",
                    color = EzzColors.TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun MobileBottomNavBar(
    currentScreen: NavigationScreen,
    onNavigate: (NavigationScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        containerColor = EzzColors.Surface,
        tonalElevation = 8.dp
    ) {
        val items = listOf(
            Triple(NavigationScreen.HOME, "Home", Icons.Default.Home),
            Triple(NavigationScreen.INSTANCES, "Instances", Icons.Default.Apps),
            Triple(NavigationScreen.ACCOUNTS, "Accounts", Icons.Default.AccountCircle),
            Triple(NavigationScreen.SETTINGS, "Settings", Icons.Default.Settings),
            Triple(NavigationScreen.CONSOLE, "Console", Icons.Default.Terminal)
        )

        for ((screen, label, icon) in items) {
            val selected = currentScreen == screen
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(screen) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = EzzColors.Primary,
                    selectedTextColor = EzzColors.Primary,
                    unselectedIconColor = EzzColors.TextMuted,
                    unselectedTextColor = EzzColors.TextMuted,
                    indicatorColor = EzzColors.SurfaceVariant
                )
            )
        }
    }
}
