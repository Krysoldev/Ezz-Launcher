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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.NavigationScreen

data class NavItem(
    val screen: NavigationScreen,
    val title: String,
    val icon: ImageVector,
    val badge: String? = null
)

@Composable
fun Sidebar(
    currentScreen: NavigationScreen,
    onNavigate: (NavigationScreen) -> Unit,
    accountName: String?,
    accountType: String?,
    isSupabaseConnected: Boolean? = true,
    modifier: Modifier = Modifier
) {
    val colors = EzzTheme.colors

    val navItems = listOf(
        NavItem(NavigationScreen.HOME, "Home", Icons.Default.Home),
        NavItem(NavigationScreen.INSTANCES, "Instances", Icons.Default.Apps),
        NavItem(NavigationScreen.MODS, "Mods", Icons.Default.Extension),
        NavItem(NavigationScreen.ACCOUNTS, "Accounts", Icons.Default.AccountCircle),
        NavItem(NavigationScreen.PROFILES, "Profiles", Icons.Default.Tune),
        NavItem(NavigationScreen.SETTINGS, "Settings", Icons.Default.Settings),
        NavItem(NavigationScreen.CONSOLE, "Console", Icons.Default.Terminal)
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(240.dp)
            .background(colors.surface)
            .border(androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.5f)))
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            // App Branding Logo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                Image(
                    painter = painterResource("logo.png"),
                    contentDescription = "Ezz Launcher Logo",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "EZZ LAUNCHER",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Minecraft Java Edition",
                        color = colors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Navigation Items
            navItems.forEach { item ->
                val isSelected = currentScreen == item.screen
                val interactionSource = remember { MutableInteractionSource() }

                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) colors.primary.copy(alpha = 0.15f) else Color.Transparent
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) colors.primary else colors.textSecondary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(backgroundColor)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onNavigate(item.screen) }
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Active Pill Bar
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(colors.primary)
                        )
                        Spacer(modifier = Modifier.width(9.dp))
                    }

                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = contentColor,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = item.title,
                        color = contentColor,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )

                    if (item.badge != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.primaryGlow)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = item.badge,
                                color = colors.primary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Bottom User Profile Card & Supabase Sync Status
        Column {
            // Supabase Cloud Status Indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isSupabaseConnected == true) Icons.Default.CloudDone else Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = if (isSupabaseConnected == true) colors.accent else colors.warning,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSupabaseConnected == true) "Supabase Cloud: Active" else "Supabase Cloud: Connecting",
                    color = if (isSupabaseConnected == true) colors.accent else colors.warning,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // User Profile Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceVariant)
                    .clickable { onNavigate(NavigationScreen.ACCOUNTS) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.primaryGlow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = accountName ?: "Player",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = accountType ?: "Offline",
                        color = colors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
