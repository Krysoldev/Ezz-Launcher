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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.ezz.launcher.core.model.instance.ServerEntry
import io.ezz.launcher.ui.components.EzzBadge
import io.ezz.launcher.ui.components.EzzBadgeVariant
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.EzzEmptyState
import io.ezz.launcher.ui.components.EzzIconButton
import io.ezz.launcher.ui.components.EzzTextField
import io.ezz.launcher.ui.components.ToastManager
import io.ezz.launcher.ui.components.ToastType
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.AppViewModel

@Composable
fun ServersScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val savedServers by viewModel.savedServers.collectAsState()
    var selectedCategory by remember { mutableStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }

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
                        text = "SERVERS",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    EzzBadge(
                        text = "${savedServers.size}",
                        variant = EzzBadgeVariant.NEUTRAL
                    )
                }
                Text(
                    text = "Saved multiplayer server addresses, direct connection shortcuts, and ping telemetry",
                    color = Color(0xFF888888),
                    fontSize = 12.sp
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

        Spacer(modifier = Modifier.height(18.dp))

        // Categories
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF141414))
                .border(1.dp, Color(0xFF242424), RoundedCornerShape(6.dp))
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            listOf("Saved (${savedServers.size})", "Featured").forEachIndexed { index, label ->
                val isSelected = selectedCategory == index
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) Color(0xFF242424) else Color.Transparent)
                        .clickable { selectedCategory = index }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else Color(0xFF888888),
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (savedServers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F0F0F))
                    .border(1.dp, Color(0xFF202020), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                EzzEmptyState(
                    title = "No Minecraft Servers Saved",
                    description = "Add your favorite multiplayer server address to quick-launch directly into the game.",
                    actionLabel = "Add Server",
                    onAction = { showAddDialog = true }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(savedServers) { server ->
                    ServerCard(
                        server = server,
                        onConnect = { viewModel.launchInstance() },
                        onDelete = { viewModel.removeServer(server.id) },
                        onCopy = {
                            viewModel.platformBridge.copyToClipboard(server.address)
                            ToastManager.show("Address Copied", server.address, ToastType.SUCCESS)
                        }
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF101010))
            .border(1.dp, Color(0xFF222222), RoundedCornerShape(6.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF181818))
                        .border(1.dp, Color(0xFF282828), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = "Server",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = server.name,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = server.address,
                        color = Color(0xFFA0A0A0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
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
                    variant = EzzButtonVariant.GHOST
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
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF282828), RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = "Add Multiplayer Server",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.3.sp
                )
                Text(
                    text = "Enter server name and IP address (e.g. mc.hypixel.net)",
                    color = Color(0xFF888888),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                EzzTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Server Name",
                    placeholder = "e.g. Hypixel Network"
                )

                Spacer(modifier = Modifier.height(12.dp))

                EzzTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = "Server Address / Host",
                    placeholder = "mc.hypixel.net"
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
                        onClick = { if (address.isNotBlank()) onAdd(name.ifBlank { address }, address) },
                        variant = EzzButtonVariant.PRIMARY,
                        size = EzzButtonSize.MEDIUM,
                        enabled = address.isNotBlank()
                    )
                }
            }
        }
    }
}
