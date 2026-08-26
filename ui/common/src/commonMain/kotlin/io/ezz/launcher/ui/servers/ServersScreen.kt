package io.ezz.launcher.ui.servers

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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.ezz.launcher.core.model.instance.ServerEntry
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.EzzEmptyState
import io.ezz.launcher.ui.components.EzzIconButton
import io.ezz.launcher.ui.components.EzzTabs
import io.ezz.launcher.ui.components.EzzTextField
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.AppViewModel

@Composable
fun ServersScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val colors = EzzTheme.colors
    val savedServers by viewModel.savedServers.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Minecraft Servers",
                    color = colors.textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Save and quick-launch into your favorite multiplayer worlds",
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
            }

            EzzButton(
                text = "Add Server",
                icon = Icons.Default.Add,
                onClick = { showAddDialog = true },
                variant = EzzButtonVariant.PRIMARY,
                size = EzzButtonSize.MEDIUM
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tabs
        EzzTabs(
            tabs = listOf("All Servers (${savedServers.size})", "Saved", "Featured"),
            selectedIndex = selectedTab,
            onTabSelected = { selectedTab = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (savedServers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                EzzEmptyState(
                    title = "No Minecraft Servers Added",
                    description = "Add your favorite SMP, PvP, or minigame server to quickly connect from Ezz Launcher.",
                    actionLabel = "Add Server",
                    onAction = { showAddDialog = true }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(savedServers) { server ->
                    ServerCard(
                        server = server,
                        onConnect = { viewModel.launchInstance() },
                        onDelete = { viewModel.removeServer(server.id) },
                        onCopy = { viewModel.platformBridge.copyToClipboard(server.address) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddServerDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, address ->
                viewModel.addServer(name, address)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ServerCard(
    server: ServerEntry,
    onConnect: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit
) {
    val colors = EzzTheme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.cardBackground)
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = "Server",
                        tint = colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = server.name,
                            color = colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(colors.accent)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = server.address,
                        color = colors.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    val motd = server.motd
                    if (!motd.isNullOrBlank()) {
                        Text(
                            text = motd,
                            color = colors.textMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EzzIconButton(
                    icon = Icons.Default.ContentCopy,
                    onClick = onCopy,
                    size = EzzButtonSize.SMALL,
                    variant = EzzButtonVariant.OUTLINE
                )
                EzzIconButton(
                    icon = Icons.Default.Delete,
                    onClick = onDelete,
                    size = EzzButtonSize.SMALL,
                    variant = EzzButtonVariant.DANGER
                )
                EzzButton(
                    text = "Launch & Join",
                    icon = Icons.Default.PlayArrow,
                    onClick = onConnect,
                    variant = EzzButtonVariant.PRIMARY,
                    size = EzzButtonSize.SMALL
                )
            }
        }
    }
}

@Composable
private fun AddServerDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, address: String) -> Unit
) {
    val colors = EzzTheme.colors
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, colors.borderLight, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Add Minecraft Server",
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Enter server name and IP address (e.g. mc.hypixel.net)",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                EzzTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Server Name",
                    placeholder = "My SMP Server"
                )

                Spacer(modifier = Modifier.height(12.dp))

                EzzTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = "Server Address / IP",
                    placeholder = "play.example.com"
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    EzzButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        variant = EzzButtonVariant.GHOST,
                        size = EzzButtonSize.MEDIUM
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    EzzButton(
                        text = "Save Server",
                        onClick = { if (address.isNotBlank()) onAdd(name, address) },
                        variant = EzzButtonVariant.PRIMARY,
                        size = EzzButtonSize.MEDIUM,
                        enabled = address.isNotBlank()
                    )
                }
            }
        }
    }
}
