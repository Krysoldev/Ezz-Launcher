package io.ezz.launcher.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import io.ezz.launcher.ui.components.EzzCard
import io.ezz.launcher.ui.components.EzzEmptyState
import io.ezz.launcher.ui.components.EzzIconButton
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.AppViewModel

@Composable
fun AccountsScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val colors = EzzTheme.colors
    val accounts by viewModel.accountRepository.accounts.collectAsState()
    val selectedAccount by viewModel.accountRepository.selectedAccount.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(32.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Account Manager",
                    color = colors.textPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Manage your Microsoft Online and Offline player profiles (${accounts.size} active)",
                    color = colors.textSecondary,
                    fontSize = 14.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                EzzButton(
                    text = "Add Offline Account",
                    onClick = { viewModel.showAddOfflineAccountDialog.value = true },
                    variant = EzzButtonVariant.SECONDARY,
                    size = EzzButtonSize.MEDIUM,
                    icon = Icons.Default.Person
                )

                Spacer(modifier = Modifier.width(12.dp))

                EzzButton(
                    text = "Microsoft Login",
                    onClick = { viewModel.startMicrosoftLogin() },
                    variant = EzzButtonVariant.PRIMARY,
                    size = EzzButtonSize.MEDIUM,
                    icon = Icons.Default.Security
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (accounts.isEmpty()) {
            EzzEmptyState(
                title = "No Accounts Configured",
                description = "Add an offline player account or sign in with Microsoft to launch Minecraft.",
                actionButtonText = "Add Offline Account",
                onActionClick = { viewModel.showAddOfflineAccountDialog.value = true }
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(accounts, key = { it.id }) { account ->
                    val isSelected = account.id == selectedAccount?.id
                    AccountItemCard(
                        account = account,
                        isSelected = isSelected,
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
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = EzzTheme.colors

    EzzCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (isSelected) colors.primary else colors.border,
        backgroundColor = if (isSelected) colors.surfaceVariant else colors.cardBackground,
        onClick = onSelect
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Avatar Head
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) colors.primaryGlow else colors.surface)
                        .border(1.dp, if (isSelected) colors.primary else colors.border, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (account.type == AccountType.MICROSOFT) Icons.Default.Security else Icons.Default.Person,
                        contentDescription = null,
                        tint = if (isSelected) colors.primary else colors.textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = account.username,
                            color = colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        EzzBadge(
                            text = if (account.type == AccountType.MICROSOFT) "MICROSOFT" else "OFFLINE",
                            variant = if (account.type == AccountType.MICROSOFT) EzzBadgeVariant.SUCCESS else EzzBadgeVariant.NEUTRAL
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            EzzBadge(
                                text = "ACTIVE",
                                variant = EzzBadgeVariant.PRIMARY
                            )
                        }
                    }

                    Text(
                        text = "UUID: ${account.uuid.take(18)}...",
                        color = colors.textMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isSelected) {
                    EzzButton(
                        text = "Select",
                        onClick = onSelect,
                        variant = EzzButtonVariant.SECONDARY,
                        size = EzzButtonSize.SMALL
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }

                EzzIconButton(
                    icon = Icons.Default.Delete,
                    onClick = onDelete,
                    contentDescription = "Delete Account",
                    tint = colors.danger
                )
            }
        }
    }
}
