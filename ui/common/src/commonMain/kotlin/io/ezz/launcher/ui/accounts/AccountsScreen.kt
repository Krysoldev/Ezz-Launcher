package io.ezz.launcher.ui.accounts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.minecraft.skin.MinecraftSkinManager
import io.ezz.launcher.core.model.account.Account
import io.ezz.launcher.core.model.account.AccountType
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.MicrosoftSignInButton
import io.ezz.launcher.ui.components.MinecraftSkinHead
import io.ezz.launcher.ui.dialogs.RemoveAccountConfirmationDialog
import io.ezz.launcher.ui.viewmodel.AppViewModel

@Composable
fun AccountsScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val accounts by viewModel.accountRepository.accounts.collectAsState()
    val selectedAccount by viewModel.accountRepository.selectedAccount.collectAsState()

    // 1. Active account first, 2. Other Microsoft accounts, 3. Offline accounts
    val sortedAccounts = remember(accounts, selectedAccount) {
        accounts.sortedWith(
            compareByDescending<Account> { it.id == selectedAccount?.id }
                .thenBy { if (it.type == AccountType.MICROSOFT) 0 else 1 }
                .thenBy { it.username.lowercase() }
        )
    }

    var accountPendingDelete by remember { mutableStateOf<Account?>(null) }

    // Deletion confirmation dialog
    accountPendingDelete?.let { accountToDelete ->
        RemoveAccountConfirmationDialog(
            username = accountToDelete.username,
            onDismiss = { accountPendingDelete = null },
            onConfirm = {
                viewModel.deleteAccount(accountToDelete.id)
                accountPendingDelete = null
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07080A))
            .padding(horizontal = 28.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ==========================================
        // 1. ACCOUNTS PAGE HEADER
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "ACCOUNTS",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Manage your Minecraft identities and profiles",
                    color = Color(0xFF8B949E),
                    fontSize = 12.5.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Secondary Action: Add Offline Account
                EzzButton(
                    text = "Add Offline Account",
                    onClick = { viewModel.showAddOfflineAccountDialog.value = true },
                    icon = Icons.Default.Add,
                    variant = EzzButtonVariant.SECONDARY,
                    size = EzzButtonSize.MEDIUM
                )

                // Primary Action: Sign in with Microsoft
                MicrosoftSignInButton(
                    onClick = { viewModel.openMicrosoftLoginModal() },
                    size = EzzButtonSize.MEDIUM
                )
            }
        }

        // ==========================================
        // 2. UNIFIED SECTION HEADER: YOUR ACCOUNTS
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "YOUR ACCOUNTS",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )

            if (accounts.isNotEmpty()) {
                Text(
                    text = if (accounts.size == 1) "1 account" else "${accounts.size} accounts",
                    color = Color(0xFF8B949E),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // ==========================================
        // 3. UNIFIED ACCOUNT LIST OR EMPTY STATE
        // ==========================================
        if (accounts.isEmpty()) {
            EmptyAccountsCard(
                onSignIn = { viewModel.openMicrosoftLoginModal() },
                onAddOffline = { viewModel.showAddOfflineAccountDialog.value = true }
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(sortedAccounts, key = { it.id }) { account ->
                    val isActive = account.id == selectedAccount?.id
                    UnifiedAccountCard(
                        account = account,
                        isActive = isActive,
                        skinManager = viewModel.skinService,
                        onSelect = { viewModel.selectAccount(account) },
                        onDeleteClick = { accountPendingDelete = account }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * Unified Account Card for both Microsoft and Offline accounts:
 * ┌──────────────────────────────────────────────────────────────┐
 * │ [Skin]  KrysolDev                         ● ACTIVE           │
 * │         Microsoft Account · Minecraft Java Edition           │
 * │                                              [Select]  [⋮]   │
 * └──────────────────────────────────────────────────────────────┘
 */
@Composable
private fun UnifiedAccountCard(
    account: Account,
    isActive: Boolean,
    skinManager: MinecraftSkinManager,
    onSelect: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isMicrosoft = account.type == AccountType.MICROSOFT
    var menuExpanded by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.996f else if (isHovered) 1.002f else 1.0f,
        animationSpec = tween(100)
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) Color(0xFF131122) else if (isHovered) Color(0xFF161A24) else Color(0xFF10131A))
            .border(
                1.dp,
                if (isActive) Color(0xFF8B5CF6).copy(alpha = 0.75f) else if (isHovered) Color(0xFF2D3448) else Color(0xFF1B1F2C),
                RoundedCornerShape(10.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT & CENTER: Avatar + Username + Account Type Metadata
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                MinecraftSkinHead(
                    account = account,
                    skinManager = skinManager,
                    size = 44.dp
                )

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = account.username,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isMicrosoft) "Microsoft Account" else "Offline Account",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.5.sp
                        )
                        Text(
                            text = "·",
                            color = Color(0xFF475569),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isMicrosoft) "Minecraft Java Edition" else "Local Profile",
                            color = if (isMicrosoft) Color(0xFFA78BFA) else Color(0xFF64748B),
                            fontSize = 11.5.sp,
                            fontWeight = if (isMicrosoft) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            // RIGHT: Active Status if applicable, [Select] button, and Three-Dot Menu [⋮]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Active Status Indicator
                if (isActive) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Text(
                            text = "ACTIVE",
                            color = Color(0xFF34D399),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp
                        )
                    }
                } else {
                    EzzButton(
                        text = "Select",
                        onClick = onSelect,
                        variant = EzzButtonVariant.SECONDARY,
                        size = EzzButtonSize.SMALL
                    )
                }

                // Three-Dot Menu Button [⋮]
                Box {
                    val menuInteraction = remember { MutableInteractionSource() }
                    val isMenuHovered by menuInteraction.collectIsHoveredAsState()

                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isMenuHovered || menuExpanded) Color(0xFF1A1F2C) else Color.Transparent)
                            .clickable(
                                interactionSource = menuInteraction,
                                indication = null,
                                onClick = { menuExpanded = true }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = if (isMenuHovered || menuExpanded) Color.White else Color(0xFF8B949E),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier
                            .background(Color(0xFF10131A))
                            .border(1.dp, Color(0xFF1B1F2C), RoundedCornerShape(8.dp))
                    ) {
                        if (!isActive) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Select Account",
                                        color = Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onSelect()
                                },
                                colors = MenuDefaults.itemColors()
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Set as Active",
                                        color = Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onSelect()
                                },
                                colors = MenuDefaults.itemColors()
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0xFF1E2433))
                            )
                        }

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Remove Account",
                                    color = Color(0xFFF87171),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDeleteClick()
                            },
                            colors = MenuDefaults.itemColors()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single Unified Empty State Card when zero accounts exist in Ezz Launcher.
 */
@Composable
private fun EmptyAccountsCard(
    onSignIn: () -> Unit,
    onAddOffline: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0C0E12))
            .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(12.dp))
            .padding(horizontal = 24.dp, vertical = 36.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF141720))
                    .border(1.dp, Color(0xFF222735), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(26.dp)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "No accounts yet",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Add a Microsoft account or create an offline profile to get started.",
                    color = Color(0xFF64748B),
                    fontSize = 12.5.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MicrosoftSignInButton(
                    onClick = onSignIn,
                    size = EzzButtonSize.MEDIUM
                )

                EzzButton(
                    text = "Add Offline Account",
                    onClick = onAddOffline,
                    variant = EzzButtonVariant.SECONDARY,
                    size = EzzButtonSize.MEDIUM
                )
            }
        }
    }
}
