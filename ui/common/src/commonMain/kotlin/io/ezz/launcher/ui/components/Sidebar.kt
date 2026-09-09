package io.ezz.launcher.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.minecraft.skin.MinecraftSkinManager
import io.ezz.launcher.core.model.account.Account
import io.ezz.launcher.core.model.account.AccountType
import io.ezz.launcher.ui.audio.EzzAudioService
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

    val navItems = remember {
        listOf(
            NavItem(NavigationScreen.HOME, "Home", Icons.Default.Home),
            NavItem(NavigationScreen.INSTANCES, "Instances", Icons.Default.GridView),
            NavItem(NavigationScreen.VAULT, "Vault", Icons.Default.Person),
            NavItem(NavigationScreen.ACCOUNTS, "Accounts", Icons.Default.AccountCircle),
            NavItem(NavigationScreen.CONSOLE, "Console", Icons.Default.Terminal),
            NavItem(NavigationScreen.SETTINGS, "Settings", Icons.Default.Settings)
        )
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(220.dp)
            .background(Color(0xFF0C0E14))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 14.dp, horizontal = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                // Ezz Launcher Logo & Branding Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF222736), RoundedCornerShape(8.dp))
                    ) {
                        Image(
                            painter = painterResource("logo.png"),
                            contentDescription = "Ezz Launcher Logo",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
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

                Spacer(modifier = Modifier.height(14.dp))

                // Subtle hairline divider between brand and nav items
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .padding(horizontal = 4.dp)
                        .background(Color(0xFF181C26))
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Navigation Item List
                navItems.forEach { item ->
                    val isSelected = currentScreen == item.screen
                    val interactionSource = remember { MutableInteractionSource() }
                    val isHovered by interactionSource.collectIsHoveredAsState()

                    val backgroundColor by animateColorAsState(
                        targetValue = when {
                            isSelected -> Color(0x1F8B5CF6)
                            isHovered -> Color(0xFF161A24)
                            else -> Color.Transparent
                        },
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                    )

                    val borderColor by animateColorAsState(
                        targetValue = when {
                            isSelected -> Color(0x338B5CF6)
                            isHovered -> Color(0xFF242A3B)
                            else -> Color.Transparent
                        },
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                    )

                    val textAndIconColor by animateColorAsState(
                        targetValue = when {
                            isSelected -> Color.White
                            isHovered -> Color(0xFFF1F5F9)
                            else -> Color(0xFF94A3B8)
                        },
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                    )

                    val iconTint by animateColorAsState(
                        targetValue = when {
                            isSelected -> Color(0xFFA78BFA)
                            isHovered -> Color(0xFFF1F5F9)
                            else -> Color(0xFF64748B)
                        },
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                    )

                    val indicatorHeight by animateDpAsState(
                        targetValue = if (isSelected) 18.dp else 0.dp,
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                    )

                    val indicatorAlpha by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0f,
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .padding(vertical = 1.5.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(backgroundColor)
                            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = {
                                    EzzAudioService.playSelect()
                                    onNavigate(item.screen)
                                }
                            )
                    ) {
                        LaunchedEffect(isHovered) {
                            if (isHovered && !isSelected) {
                                EzzAudioService.playHover()
                            }
                        }

                        // Left active indicator bar positioned without shifting inner content
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .width(3.dp)
                                .height(indicatorHeight)
                                .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                                .background(Color(0xFF8B5CF6).copy(alpha = indicatorAlpha))
                        )

                        // Inner content row with stationary coordinates (zero layout shift)
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 12.dp, end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Optically centered icon container
                            Box(
                                modifier = Modifier.size(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    tint = iconTint,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Text(
                                text = item.title,
                                color = textAndIconColor,
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
            }

            // ==========================================
            // SIDEBAR ACCOUNT CARD & ACCOUNT SWITCHER
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
                        enter = fadeIn(tween(180, easing = FastOutSlowInEasing)) +
                                expandVertically(tween(180, easing = FastOutSlowInEasing)),
                        exit = fadeOut(tween(140, easing = FastOutSlowInEasing)) +
                                shrinkVertically(tween(140, easing = FastOutSlowInEasing))
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

                                        val itemBg by animateColorAsState(
                                            targetValue = if (isOtherHovered) Color(0xFF181C26) else Color.Transparent,
                                            animationSpec = tween(150, easing = FastOutSlowInEasing)
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(itemBg)
                                                .clickable(
                                                    interactionSource = otherInteraction,
                                                    indication = null,
                                                    onClick = {
                                                        onSelectAccount?.invoke(otherAcc)
                                                        showAccountSwitcher = false
                                                    }
                                                )
                                                .padding(horizontal = 6.dp, vertical = 5.dp)
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

                                val manageInteraction = remember { MutableInteractionSource() }
                                val isManageHovered by manageInteraction.collectIsHoveredAsState()
                                val manageBg by animateColorAsState(
                                    targetValue = if (isManageHovered) Color(0x1F8B5CF6) else Color.Transparent,
                                    animationSpec = tween(150, easing = FastOutSlowInEasing)
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(manageBg)
                                        .clickable(
                                            interactionSource = manageInteraction,
                                            indication = null,
                                            onClick = {
                                                showAccountSwitcher = false
                                                onNavigate(NavigationScreen.ACCOUNTS)
                                            }
                                        )
                                        .padding(horizontal = 6.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ManageAccounts,
                                        contentDescription = null,
                                        tint = Color(0xFF8B5CF6),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Manage Accounts",
                                        color = Color(0xFFA78BFA),
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

                    val accountBg by animateColorAsState(
                        targetValue = if (isAccountHovered || showAccountSwitcher) Color(0xFF161A24) else Color(0xFF10131A),
                        animationSpec = tween(180, easing = FastOutSlowInEasing)
                    )
                    val accountBorder by animateColorAsState(
                        targetValue = if (isAccountHovered || showAccountSwitcher) Color(0xFF8B5CF6).copy(alpha = 0.5f) else Color(0xFF1B1F2C),
                        animationSpec = tween(180, easing = FastOutSlowInEasing)
                    )
                    val chevronRotation by animateFloatAsState(
                        targetValue = if (showAccountSwitcher) 180f else 0f,
                        animationSpec = tween(180, easing = FastOutSlowInEasing)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(accountBg)
                            .border(1.dp, accountBorder, RoundedCornerShape(9.dp))
                            .clickable(
                                interactionSource = accountInteraction,
                                indication = null,
                                onClick = { showAccountSwitcher = !showAccountSwitcher }
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        LaunchedEffect(isAccountHovered) {
                            if (isAccountHovered && !showAccountSwitcher) {
                                EzzAudioService.playHover()
                            }
                        }

                        AnimatedContent(
                            targetState = account,
                            transitionSpec = {
                                (fadeIn(tween(160, easing = FastOutSlowInEasing)) +
                                 slideInVertically(tween(160, easing = FastOutSlowInEasing)) { 6 })
                                    .togetherWith(fadeOut(tween(120, easing = FastOutSlowInEasing)))
                            },
                            label = "SidebarAccountTransition"
                        ) { currentAcc ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (skinManager != null) {
                                    MinecraftSkinHead(
                                        account = currentAcc,
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
                                        text = currentAcc.username,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = if (currentAcc.type == AccountType.MICROSOFT) "Microsoft Account" else "Offline Account",
                                        color = Color(0xFF8B949E),
                                        fontSize = 9.5.sp
                                    )
                                }

                                // Live Active Indicator Dot + Label + Chevron
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val isMs = currentAcc.type == AccountType.MICROSOFT
                                    val statusColor = if (isMs) Color(0xFF10B981) else Color(0xFFF59E0B)
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(statusColor)
                                            .border(1.dp, statusColor.copy(alpha = 0.4f), CircleShape)
                                    )

                                    Icon(
                                        imageVector = Icons.Default.ExpandMore,
                                        contentDescription = "Toggle Account Menu",
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier
                                            .size(16.dp)
                                            .rotate(chevronRotation)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Clean vertical right-edge border separating sidebar from main screen content
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(1.dp)
                .background(Color(0xFF1B1F2C))
        )
    }
}

