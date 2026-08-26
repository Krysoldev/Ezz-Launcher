package io.ezz.launcher.ui.accounts

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.account.Account
import io.ezz.launcher.core.model.account.AccountType
import io.ezz.launcher.ui.components.EzzBadge
import io.ezz.launcher.ui.components.EzzBadgeVariant
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.EzzEmptyState
import io.ezz.launcher.ui.components.EzzIconButton
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.AppViewModel

@Composable
fun AccountsScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val accounts by viewModel.accountRepository.accounts.collectAsState()
    val selectedAccount by viewModel.accountRepository.selectedAccount.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF050505))
            .padding(26.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "ACCOUNTS",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    EzzBadge(
                        text = "${accounts.size}",
                        variant = EzzBadgeVariant.NEUTRAL
                    )
                }
                Text(
                    text = "Manage Microsoft OAuth credentials and offline local player profiles",
                    color = Color(0xFF888888),
                    fontSize = 12.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EzzButton(
                    text = "Add Offline Account",
                    onClick = { viewModel.showAddOfflineAccountDialog.value = true },
                    variant = EzzButtonVariant.SECONDARY,
                    size = EzzButtonSize.MEDIUM,
                    icon = Icons.Default.Person
                )

                EzzButton(
                    text = "Microsoft Login",
                    onClick = { viewModel.startMicrosoftLogin() },
                    variant = EzzButtonVariant.PRIMARY,
                    size = EzzButtonSize.MEDIUM,
                    icon = Icons.Default.Security
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (accounts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F0F0F))
                    .border(1.dp, Color(0xFF202020), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                EzzEmptyState(
                    title = "No Accounts Added Yet",
                    description = "Add an offline player profile or sign in with Microsoft to launch Minecraft.",
                    actionLabel = "Add Offline Account",
                    onAction = { viewModel.showAddOfflineAccountDialog.value = true }
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(accounts, key = { it.id }) { account ->
                    val isSelected = account.id == selectedAccount?.id
                    AccountItemCard(
                        account = account,
                        isSelected = isSelected,
                        skinManager = viewModel.skinService,
                        onSelect = { viewModel.selectAccount(account) },
                        onDelete = { viewModel.deleteAccount(account.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountItemCard(
    account: Account,
    isSelected: Boolean,
    skinManager: io.ezz.launcher.core.minecraft.skin.MinecraftSkinManager,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.01f else 1.0f,
        animationSpec = tween(120)
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) Color(0xFF181818) else Color(0xFF101010))
            .border(
                1.dp,
                if (isSelected) Color.White else if (isHovered) Color(0xFF383838) else Color(0xFF222222),
                RoundedCornerShape(6.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect
            )
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Minecraft Skin Avatar Head
                io.ezz.launcher.ui.components.MinecraftSkinHead(
                    account = account,
                    skinManager = skinManager,
                    size = 42.dp
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = account.username,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        EzzBadge(
                            text = if (account.type == AccountType.MICROSOFT) "MICROSOFT" else "OFFLINE",
                            variant = if (account.type == AccountType.MICROSOFT) EzzBadgeVariant.PRIMARY else EzzBadgeVariant.NEUTRAL
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            EzzBadge(
                                text = "ACTIVE",
                                variant = EzzBadgeVariant.SUCCESS
                            )
                        }
                    }

                    Text(
                        text = "UUID: ${account.uuid.take(18)}...",
                        color = Color(0xFF777777),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isSelected) {
                    EzzButton(
                        text = "Select",
                        onClick = onSelect,
                        variant = EzzButtonVariant.SECONDARY,
                        size = EzzButtonSize.SMALL
                    )
                }

                EzzIconButton(
                    icon = Icons.Default.Delete,
                    onClick = onDelete,
                    contentDescription = "Delete Account",
                    size = EzzButtonSize.SMALL,
                    variant = EzzButtonVariant.DANGER
                )
            }
        }
    }
}
