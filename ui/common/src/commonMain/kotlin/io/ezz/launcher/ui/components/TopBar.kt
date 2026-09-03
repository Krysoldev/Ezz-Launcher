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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.account.AccountType
import io.ezz.launcher.ui.viewmodel.AppViewModel
import io.ezz.launcher.ui.viewmodel.NavigationScreen

@Composable
fun TopBar(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val accounts by viewModel.accountRepository.accounts.collectAsState()
    val selectedAccount by viewModel.accountRepository.selectedAccount.collectAsState()

    var isAccountSwitcherOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(Color(0xFF07080A))
            .border(androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1A1D26)))
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Screen Section Title / Breadcrumbs
        Row(verticalAlignment = Alignment.CenterVertically) {
            val title = when (currentScreen) {
                NavigationScreen.HOME -> "Home"
                NavigationScreen.INSTANCES -> "Instances"
                NavigationScreen.VAULT -> "Vault & Skins"
                NavigationScreen.ACCOUNTS -> "Accounts"
                NavigationScreen.MODS -> "Mods"
                NavigationScreen.RESOURCE_PACKS -> "Resource Packs"
                NavigationScreen.SHADERS -> "Shader Packs"
                NavigationScreen.WORLDS -> "Worlds"
                NavigationScreen.SCREENSHOTS -> "Screenshots"
                NavigationScreen.SETTINGS -> "Settings"
                NavigationScreen.SERVERS -> "Servers"
                NavigationScreen.PROFILES -> "Profiles"
                NavigationScreen.CONSOLE -> "Console"
                NavigationScreen.INSTANCE_MANAGER -> "Instance Manager"
            }

            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp
            )
        }

        // Right: Console Shortcut & Interactive Account Area
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Console Action Button
            TopBarIconAction(
                icon = Icons.Default.Terminal,
                contentDescription = "Console Logs",
                isActive = currentScreen == NavigationScreen.CONSOLE,
                onClick = { viewModel.navigateTo(NavigationScreen.CONSOLE) }
            )

            // Live Account Header Widget & Switcher Dropdown
            Box {
                val account = selectedAccount
                val accountInteraction = remember { MutableInteractionSource() }
                val isAccountHovered by accountInteraction.collectIsHoveredAsState()

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isAccountHovered || isAccountSwitcherOpen) Color(0xFF141720) else Color(0xFF0E1015))
                        .border(1.dp, if (isAccountHovered || isAccountSwitcherOpen) Color(0xFF2E3648) else Color(0xFF1A1D26), RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = accountInteraction,
                            indication = null,
                            onClick = { isAccountSwitcherOpen = !isAccountSwitcherOpen }
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MinecraftSkinHead(
                        account = account,
                        skinManager = viewModel.skinService,
                        size = 26.dp
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = account?.username ?: "Offline Player",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                        }
                        Text(
                            text = when (account?.type) {
                                AccountType.MICROSOFT -> "Microsoft Account"
                                AccountType.OFFLINE -> "Offline Account"
                                null -> "Select Account"
                            },
                            color = Color(0xFF64748B),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Clean Account Switcher Dropdown Popover
                DropdownMenu(
                    expanded = isAccountSwitcherOpen,
                    onDismissRequest = { isAccountSwitcherOpen = false },
                    modifier = Modifier
                        .widthIn(min = 230.dp, max = 280.dp)
                        .background(Color(0xFF101318))
                        .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(8.dp))
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "PLAYER ACCOUNTS",
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )

                    accounts.forEach { acc ->
                        val isSelected = acc.id == selectedAccount?.id
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        MinecraftSkinHead(
                                            account = acc,
                                            skinManager = viewModel.skinService,
                                            size = 22.dp
                                        )
                                        Column {
                                            Text(
                                                text = acc.username,
                                                color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                            Text(
                                                text = if (acc.type == AccountType.MICROSOFT) "Microsoft" else "Offline",
                                                color = Color(0xFF64748B),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                viewModel.selectAccount(acc)
                                isAccountSwitcherOpen = false
                            }
                        )
                    }

                    HorizontalDivider(
                        color = Color(0xFF1A1D26),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                Text("Add Account", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        },
                        onClick = {
                            isAccountSwitcherOpen = false
                            viewModel.showAddOfflineAccountDialog.value = true
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.ManageAccounts, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                                Text("Manage Accounts", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }
                        },
                        onClick = {
                            isAccountSwitcherOpen = false
                            viewModel.navigateTo(NavigationScreen.ACCOUNTS)
                        }
                    )
                }
            }
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

    val bgColor by animateColorAsState(
        targetValue = when {
            isActive -> Color(0xFF1A1E29)
            isHovered -> Color(0xFF141720)
            else -> Color(0xFF0E1015)
        },
        animationSpec = tween(120)
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isActive -> Color.White
            isHovered -> Color(0xFF2E3648)
            else -> Color(0xFF1A1D26)
        },
        animationSpec = tween(120)
    )

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(7.dp))
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
            tint = if (isActive || isHovered) Color.White else Color(0xFF94A3B8),
            modifier = Modifier.size(15.dp)
        )
    }
}
