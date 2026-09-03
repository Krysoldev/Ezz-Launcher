package io.ezz.launcher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.minecraft.skin.MinecraftSkinManager
import io.ezz.launcher.core.model.account.Account
import io.ezz.launcher.core.model.account.AccountType
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
    account: Account? = null,
    accounts: List<Account> = emptyList(),
    onSelectAccount: ((Account) -> Unit)? = null,
    skinManager: MinecraftSkinManager? = null,
    modifier: Modifier = Modifier
) {
    var showAccountSwitcher by remember { mutableStateOf(false) }

    val navItems = listOf(
        NavItem(NavigationScreen.HOME, "Home", Icons.Default.Home),
        NavItem(NavigationScreen.INSTANCES, "Instances", Icons.Default.GridView),
        NavItem(NavigationScreen.VAULT, "Vault", Icons.Default.Person),
        NavItem(NavigationScreen.ACCOUNTS, "Accounts", Icons.Default.AccountCircle),
        NavItem(NavigationScreen.CONSOLE, "Console", Icons.Default.Terminal),
        NavItem(NavigationScreen.SETTINGS, "Settings", Icons.Default.Settings)
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(220.dp)
            .background(Color(0xFF0C0E12))
            .border(androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1A1D26)))
            .padding(vertical = 16.dp, horizontal = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // Ezz Launcher Logo & Branding Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Image(
                    painter = painterResource("logo.png"),
                    contentDescription = "Ezz Launcher Logo",
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "EZZ LAUNCHER",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.5.sp,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "Minecraft Java Edition",
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Navigation Item List
            navItems.forEach { item ->
                val isSelected = currentScreen == item.screen
                val interactionSource = remember { MutableInteractionSource() }
                val isHovered by interactionSource.collectIsHoveredAsState()

                val backgroundColor by animateColorAsState(
                    targetValue = when {
                        isSelected -> Color(0xFF1A1E29)
                        isHovered -> Color(0xFF12151D)
                        else -> Color.Transparent
                    },
                    animationSpec = tween(durationMillis = 150)
                )

                val contentColor by animateColorAsState(
                    targetValue = when {
                        isSelected -> Color.White
                        isHovered -> Color(0xFFE2E8F0)
                        else -> Color(0xFF94A3B8)
                    },
                    animationSpec = tween(durationMillis = 150)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.5.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(backgroundColor)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onNavigate(item.screen) }
                        )
                        .padding(horizontal = 10.dp, vertical = 8.5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(16.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Spacer(modifier = Modifier.width(3.dp))
                    }

                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = contentColor,
                        modifier = Modifier.size(17.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = item.title,
                        color = contentColor,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )

                    if (item.badge != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1E2330))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = item.badge,
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 15. SIDEBAR ACCOUNT CARD & ACCOUNT SWITCHER
        // ==========================================
        if (account != null) {
            val otherAccounts = accounts.filter { it.id != account.id }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Animated Account Switcher Popup
                AnimatedVisibility(
                    visible = showAccountSwitcher,
                    enter = fadeIn(tween(140)) + expandVertically(tween(140)),
                    exit = fadeOut(tween(100)) + shrinkVertically(tween(100))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF10131B))
                            .border(1.dp, Color(0xFF222736), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Section: ACTIVE
                            Text(
                                text = "ACTIVE",
                                color = Color(0xFF64748B),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (skinManager != null) {
                                        MinecraftSkinHead(
                                            account = account,
                                            skinManager = skinManager,
                                            size = 26.dp
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = account.username,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = if (account.type == AccountType.MICROSOFT) "Microsoft Account" else "Offline Account",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 9.5.sp
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Active",
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            // Section: OTHER ACCOUNTS
                            if (otherAccounts.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(Color(0xFF1A1F2C))
                                )

                                Text(
                                    text = "OTHER ACCOUNTS",
                                    color = Color(0xFF64748B),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp
                                )

                                otherAccounts.forEach { otherAcc ->
                                    val otherInteraction = remember { MutableInteractionSource() }
                                    val isOtherHovered by otherInteraction.collectIsHoveredAsState()

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isOtherHovered) Color(0xFF181C26) else Color.Transparent)
                                            .clickable(
                                                interactionSource = otherInteraction,
                                                indication = null,
                                                onClick = {
                                                    onSelectAccount?.invoke(otherAcc)
                                                    showAccountSwitcher = false
                                                }
                                            )
                                            .padding(horizontal = 4.dp, vertical = 4.dp)
                                    ) {
                                        if (skinManager != null) {
                                            MinecraftSkinHead(
                                                account = otherAcc,
                                                skinManager = skinManager,
                                                size = 24.dp
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = otherAcc.username,
                                                color = if (isOtherHovered) Color.White else Color(0xFFCBD5E1),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = if (otherAcc.type == AccountType.MICROSOFT) "Microsoft" else "Offline",
                                                color = Color(0xFF64748B),
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // Divider & Manage Accounts
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0xFF1A1F2C))
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        showAccountSwitcher = false
                                        onNavigate(NavigationScreen.ACCOUNTS)
                                    }
                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ManageAccounts,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Manage Accounts",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Active Account Card (Bottom Left)
                val accountInteraction = remember { MutableInteractionSource() }
                val isAccountHovered by accountInteraction.collectIsHoveredAsState()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isAccountHovered || showAccountSwitcher) Color(0xFF151923) else Color(0xFF10131A))
                        .border(
                            1.dp,
                            if (isAccountHovered || showAccountSwitcher) Color(0xFF2D3548) else Color(0xFF1A1F2A),
                            RoundedCornerShape(9.dp)
                        )
                        .clickable(
                            interactionSource = accountInteraction,
                            indication = null,
                            onClick = { showAccountSwitcher = !showAccountSwitcher }
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (skinManager != null) {
                            MinecraftSkinHead(
                                account = account,
                                skinManager = skinManager,
                                size = 32.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = account.username,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = if (account.type == AccountType.MICROSOFT) "Microsoft Account" else "Offline Account",
                                color = Color(0xFF8B949E),
                                fontSize = 9.5.sp
                            )
                        }

                        // Green Active Status Dot
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                    }
                }
            }
        }
    }
}
