package io.ezz.launcher.ui.instances

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.ui.components.CompactRuntimeBadge
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.EzzEmptyState
import io.ezz.launcher.ui.components.InstanceArtworkIcon
import io.ezz.launcher.ui.components.RuntimeDisplay
import io.ezz.launcher.ui.viewmodel.AppViewModel
import io.ezz.launcher.ui.viewmodel.NavigationScreen

@Composable
fun InstancesScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val instances by viewModel.instanceRepository.instances.collectAsState()
    val selectedInstance by viewModel.selectedInstance.collectAsState()
    val runningSessions by viewModel.runningSessions.collectAsState()

    var instanceToDelete by remember { mutableStateOf<Instance?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07080A)),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1200.dp)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF101318))
                    .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "INSTANCES",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF141720))
                                    .border(1.dp, Color(0xFF222735), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${instances.size}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Isolated Minecraft environments, custom profiles, and mod configurations",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp
                        )
                    }

                    // Header Action Buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EzzButton(
                            text = "Create Instance",
                            onClick = { viewModel.showCreateInstanceDialog.value = true },
                            icon = Icons.Default.Add,
                            variant = EzzButtonVariant.PRIMARY,
                            size = EzzButtonSize.MEDIUM
                        )

                        EzzButton(
                            text = "Import",
                            onClick = { viewModel.openImportModpack() },
                            icon = Icons.Default.FileDownload,
                            variant = EzzButtonVariant.SECONDARY,
                            size = EzzButtonSize.MEDIUM
                        )

                        EzzButton(
                            text = "Browse",
                            onClick = { viewModel.showModpackBrowserDialog.value = true },
                            icon = Icons.Default.GridView,
                            variant = EzzButtonVariant.SECONDARY,
                            size = EzzButtonSize.MEDIUM
                        )
                    }
                }
            }

            // 2. Instance Grid / Empty State
            if (instances.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF10131A))
                        .border(1.dp, Color(0xFF1B1F2C), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF161A24))
                                .border(1.dp, Color(0xFF1B1F2C), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GridView,
                                contentDescription = null,
                                tint = Color(0xFFA78BFA),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "No Instances Yet",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Create an instance from scratch or import a Modrinth modpack (.mrpack).",
                                color = Color(0xFF64748B),
                                fontSize = 12.sp
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            EzzButton(
                                text = "Create Instance",
                                onClick = { viewModel.showCreateInstanceDialog.value = true },
                                icon = Icons.Default.Add,
                                variant = EzzButtonVariant.PRIMARY,
                                size = EzzButtonSize.MEDIUM
                            )
                            EzzButton(
                                text = "Import .mrpack",
                                onClick = { viewModel.openImportModpack() },
                                icon = Icons.Default.FileDownload,
                                variant = EzzButtonVariant.SECONDARY,
                                size = EzzButtonSize.MEDIUM
                            )
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 340.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(instances, key = { it.id }) { instance ->
                        val instStartedAt = runningSessions[instance.id]?.startedAt

                        InstanceGridCard(
                            instance = instance,
                            isSelected = instance.id == selectedInstance?.id,
                            startedAt = instStartedAt,
                            onSelect = { viewModel.selectInstance(instance) },
                            onPlay = {
                                viewModel.selectInstance(instance)
                                viewModel.launchInstance(instance)
                                viewModel.navigateTo(NavigationScreen.HOME)
                            },
                            onManage = {
                                viewModel.openInstanceManager(instance)
                            },
                            onEdit = { viewModel.showEditInstanceDialog.value = instance },
                            onDuplicate = { viewModel.duplicateInstance(instance.id, "${instance.name} (Copy)") },
                            onExport = { viewModel.openExportModpack(instance) },
                            onOpenFolder = { viewModel.openInstanceFolder(instance.id) },
                            onDelete = { instanceToDelete = instance }
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (instanceToDelete != null) {
        val target = instanceToDelete!!
        Dialog(
            onDismissRequest = { instanceToDelete = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .width(420.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF101318))
                    .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(10.dp))
                    .padding(22.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Delete Instance",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Are you sure you want to delete \"${target.name}\"? All worlds, mods, and instance settings will be permanently removed.",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EzzButton(
                            text = "Cancel",
                            onClick = { instanceToDelete = null },
                            variant = EzzButtonVariant.GHOST,
                            size = EzzButtonSize.SMALL
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        EzzButton(
                            text = "Delete",
                            onClick = {
                                val id = target.id
                                instanceToDelete = null
                                viewModel.deleteInstance(id)
                            },
                            variant = EzzButtonVariant.DANGER,
                            size = EzzButtonSize.SMALL
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstanceGridCard(
    instance: Instance,
    isSelected: Boolean,
    startedAt: Long? = null,
    onSelect: () -> Unit,
    onPlay: () -> Unit,
    onManage: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onOpenFolder: () -> Unit,
    onDelete: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.006f else 1.0f,
        animationSpec = tween(120)
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Color(0xFF131122) else if (isHovered) Color(0xFF161A24) else Color(0xFF10131A))
            .border(
                1.dp,
                if (isSelected) Color(0xFF8B5CF6).copy(alpha = 0.85f) else if (isHovered) Color(0xFF2D3448) else Color(0xFF1B1F2C),
                RoundedCornerShape(10.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect
            )
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header Row: Instance Icon + Title + Status + Active Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                InstanceArtworkIcon(
                    instance = instance,
                    size = 44.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = instance.name,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0x268B5CF6))
                                    .border(1.dp, Color(0xFF6D28D9), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "ACTIVE TARGET",
                                    color = Color(0xFFDDD6FE),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.4.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    if (startedAt != null) {
                        RuntimeDisplay(
                            startedAt = startedAt,
                            showPrefix = true,
                            prefixText = "RUNNING",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            dotColor = Color(0xFF10B981)
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Ready to Play",
                                color = Color(0xFF10B981),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Middle: Badges Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GridPillBadge(text = "MC ${instance.minecraftVersion}")
                GridPillBadge(text = instance.loaderType.name)
                GridPillBadge(text = "${instance.maxMemoryMb} MB RAM")
            }

            // Bottom Action Toolbar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Launch + Manage
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (startedAt != null) {
                        CompactRuntimeBadge(
                            startedAt = startedAt,
                            onClick = onPlay
                        )
                    } else {
                        EzzButton(
                            text = "Play",
                            onClick = onPlay,
                            icon = Icons.Default.PlayArrow,
                            variant = if (isSelected) EzzButtonVariant.PRIMARY else EzzButtonVariant.SECONDARY,
                            size = EzzButtonSize.SMALL
                        )
                    }

                    EzzButton(
                        text = "Manage",
                        onClick = onManage,
                        variant = EzzButtonVariant.SECONDARY,
                        size = EzzButtonSize.SMALL
                    )
                }

                // Right Action Icons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CardIconButton(
                        icon = Icons.Default.FolderOpen,
                        tooltip = "Open Directory",
                        onClick = onOpenFolder
                    )
                    CardIconButton(
                        icon = Icons.Default.Edit,
                        tooltip = "Edit Config",
                        onClick = onEdit
                    )
                    CardIconButton(
                        icon = Icons.Default.ContentCopy,
                        tooltip = "Duplicate",
                        onClick = onDuplicate
                    )
                    CardIconButton(
                        icon = Icons.Default.FileUpload,
                        tooltip = "Export (.zip)",
                        onClick = onExport
                    )
                    CardIconButton(
                        icon = Icons.Default.Delete,
                        tooltip = "Delete",
                        isDanger = true,
                        onClick = onDelete
                    )
                }
            }
        }
    }
}

@Composable
private fun GridPillBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF141720))
            .border(1.dp, Color(0xFF222735), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = Color(0xFFCBD5E1),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun CardIconButton(
    icon: ImageVector,
    tooltip: String,
    isDanger: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isHovered) {
                    if (isDanger) Color(0xFF3B1216) else Color(0xFF181C28)
                } else Color.Transparent
            )
            .border(
                1.dp,
                if (isHovered) {
                    if (isDanger) Color(0xFFEF4444).copy(alpha = 0.5f) else Color(0xFF323A4E)
                } else Color.Transparent,
                RoundedCornerShape(6.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = tooltip,
            tint = if (isHovered) {
                if (isDanger) Color(0xFFEF4444) else Color.White
            } else {
                if (isDanger) Color(0xFF94A3B8) else Color(0xFF64748B)
            },
            modifier = Modifier.size(15.dp)
        )
    }
}
