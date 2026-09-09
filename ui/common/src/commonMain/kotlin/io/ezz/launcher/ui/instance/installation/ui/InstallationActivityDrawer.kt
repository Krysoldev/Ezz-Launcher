package io.ezz.launcher.ui.instance.installation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.ModrinthAsyncImage
import io.ezz.launcher.ui.instance.installation.model.ContentInstallationItem
import io.ezz.launcher.ui.instance.installation.model.InstallationQueueState
import io.ezz.launcher.ui.instance.installation.model.InstallationStage

@Composable
fun InstallationActivityDrawer(
    isOpen: Boolean,
    onClose: () -> Unit,
    manager: io.ezz.launcher.ui.instance.installation.service.ContentInstallationManager,
    imageLoader: io.ezz.launcher.ui.image.ModrinthImageLoader? = null,
    modifier: Modifier = Modifier
) {
    val queueState by manager.queueState.collectAsState()
    InstallationActivityDrawer(
        queueState = queueState.copy(isDrawerOpen = isOpen),
        imageLoader = imageLoader,
        onClose = onClose,
        onSelectItem = { manager.setFocusedItem(it) },
        onCancelItem = { manager.cancelInstallation(it) },
        onClearCompleted = { manager.clearCompleted() },
        modifier = modifier
    )
}

@Composable
fun InstallationActivityDrawer(
    queueState: InstallationQueueState,
    imageLoader: io.ezz.launcher.ui.image.ModrinthImageLoader? = null,
    onClose: () -> Unit,
    onSelectItem: (ContentInstallationItem) -> Unit,
    onCancelItem: (String) -> Unit,
    onClearCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = queueState.isDrawerOpen,
        enter = fadeIn() + slideInHorizontally { it },
        exit = fadeOut() + slideOutHorizontally { it },
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x77000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                ),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(380.dp)
                    .background(Color(0xFF0C0E14))
                    .border(1.dp, Color(0xFF1B1F2C))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Consume click
                    )
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(20.dp))
                            Text(
                                text = "Installation Activity",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (queueState.totalActiveCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF8B5CF6))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${queueState.totalActiveCount}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF141722))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }

                    // Content List
                    if (queueState.activeItems.isEmpty() && queueState.completedHistory.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = Color(0xFF334155), modifier = Modifier.size(36.dp))
                                Text("No Active Downloads", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Installed mods, resource packs, and shaders will appear here.", color = Color(0xFF64748B), fontSize = 12.sp)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (queueState.activeItems.isNotEmpty()) {
                                item {
                                    Text("ACTIVE OPERATIONS", color = Color(0xFF64748B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                items(queueState.activeItems, key = { it.id }) { item ->
                                    ActivityCard(
                                        item = item,
                                        imageLoader = imageLoader,
                                        onClick = { onSelectItem(item) },
                                        onCancel = { onCancelItem(item.id) }
                                    )
                                }
                            }

                            if (queueState.completedHistory.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("RECENT ACTIVITY", color = Color(0xFF64748B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "Clear",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp,
                                            modifier = Modifier.clickable(onClick = onClearCompleted)
                                        )
                                    }
                                }
                                items(queueState.completedHistory, key = { it.id }) { item ->
                                    ActivityCard(
                                        item = item,
                                        imageLoader = imageLoader,
                                        onClick = { onSelectItem(item) },
                                        onCancel = null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityCard(
    item: ContentInstallationItem,
    imageLoader: io.ezz.launcher.ui.image.ModrinthImageLoader? = null,
    onClick: () -> Unit,
    onCancel: (() -> Unit)?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF10131A))
            .border(1.dp, Color(0xFF1E2436), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    ModrinthAsyncImage(
                        url = item.iconUrl,
                        imageLoader = imageLoader,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        placeholderIcon = Icons.Default.Extension,
                        contentScale = ContentScale.Crop
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${item.instanceName} • ${item.stage.label}",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }

                if (onCancel != null && item.isRunning) {
                    IconButton(onClick = onCancel, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                    }
                } else if (item.stage == InstallationStage.COMPLETED) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                } else if (item.stage == InstallationStage.FAILED) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                }
            }

            if (item.isRunning) {
                LinearProgressIndicator(
                    progress = { if (item.progress < 0f) 0.5f else item.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFF8B5CF6),
                    trackColor = Color(0xFF141722)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${item.progressPercent}% • ${item.downloadedFormatted} / ${item.totalFormatted}",
                        color = Color(0xFF64748B),
                        fontSize = 10.5.sp
                    )
                    if (item.speedText.isNotBlank()) {
                        Text(text = item.speedText, color = Color(0xFF10B981), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
