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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import io.ezz.launcher.ui.theme.EzzColors
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
            .background(EzzColors.Background)
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
                    text = "Accounts",
                    color = EzzColors.TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Manage your Offline and Microsoft accounts",
                    color = EzzColors.TextSecondary,
                    fontSize = 14.sp
                )
            }

            Row {
                Button(
                    onClick = { viewModel.showAddOfflineAccountDialog.value = true },
                    colors = ButtonDefaults.buttonColors(containerColor = EzzColors.SurfaceVariant),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = EzzColors.Primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Add Offline Account", color = EzzColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = { viewModel.startMicrosoftLogin() },
                    colors = ButtonDefaults.buttonColors(containerColor = EzzColors.Primary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = Color(0xFF0B0F19))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Add Microsoft Account", color = Color(0xFF0B0F19), fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Accounts List
        if (accounts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(EzzColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No Accounts Added",
                        color = EzzColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Add an Offline or Microsoft account to launch the game",
                        color = EzzColors.TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(accounts, key = { it.id }) { account ->
                    val isSelected = account.id == selectedAccount?.id
                    AccountItemCard(
                        account = account,
                        isSelected = isSelected,
                        onSelect = { viewModel.selectAccount(account) },
                        onDelete = { viewModel.removeAccount(account.id) }
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
    val borderColor = if (isSelected) EzzColors.Primary else EzzColors.Border

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EzzColors.Surface)
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onSelect)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar Placeholder
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (account.type == AccountType.MICROSOFT) EzzColors.PrimaryGlow else EzzColors.SurfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = account.username.take(1).uppercase(),
                        color = if (account.type == AccountType.MICROSOFT) EzzColors.Primary else EzzColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = account.username,
                            color = EzzColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (account.type == AccountType.MICROSOFT) Color(0xFF0284C7) else EzzColors.SurfaceLight
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (account.type == AccountType.MICROSOFT) "Microsoft" else "Offline",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "UUID: ${account.uuid}",
                        color = EzzColors.TextMuted,
                        fontSize = 12.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelected) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = EzzColors.Accent, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Active", color = EzzColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onSelect,
                        colors = ButtonDefaults.buttonColors(containerColor = EzzColors.SurfaceVariant),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(text = "Set Active", color = EzzColors.TextPrimary, fontSize = 12.sp)
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove Account", tint = EzzColors.Danger)
                }
            }
        }
    }
}
