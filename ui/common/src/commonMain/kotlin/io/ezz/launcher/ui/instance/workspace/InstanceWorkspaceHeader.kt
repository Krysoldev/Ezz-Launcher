package io.ezz.launcher.ui.instance.workspace

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.EzzLoaderBadge
import io.ezz.launcher.ui.components.InstanceArtworkIcon
import io.ezz.launcher.ui.instance.dialogs.InstanceQuickActionMenu

@Composable
fun InstanceWorkspaceHeader(
    instance: Instance,
    isRunning: Boolean,
    isLaunching: Boolean,
    runningTimeFormatted: String?,
    onBackToFleet: () -> Unit,
    onLaunchOrStop: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onOpenFolder: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onRepair: () -> Unit,
    onDelete: () -> Unit,
    onEditLogo: (() -> Unit)? = null,
    activeDownloadsCount: Int = 0,
    onOpenActivityDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF131722),
                        Color(0xFF080A0F)
                    )
                )
            )
            .border(1.dp, Color(0xFF1B1F2C), RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // TOP BAR: Navigation Back & Quick Tools
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back Button
            val backInteraction = remember { MutableInteractionSource() }
            val isBackHovered by backInteraction.collectIsHoveredAsState()

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isBackHovered) Color(0xFF1B1F2C) else Color(0xFF10131A))
                    .border(1.dp, Color(0xFF262C3F), RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = backInteraction,
                        indication = null,
                        onClick = onBackToFleet
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "All Instances",
                    color = Color(0xFFE2E8F0),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Right Quick Actions: Activity Drawer, Folder, Context Menu
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (activeDownloadsCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF8B5CF6).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .clickable { onOpenActivityDrawer() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF8B5CF6),
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "$activeDownloadsCount downloading",
                                color = Color(0xFFC4B5FD),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                EzzButton(
                    text = "Open Folder",
                    icon = Icons.Default.FolderOpen,
                    onClick = onOpenFolder,
                    variant = EzzButtonVariant.SECONDARY,
                    size = EzzButtonSize.SMALL
                )

                // 3-Dot Options Dropdown
                Box {
                    val menuInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF10131A))
                            .border(1.dp, Color(0xFF1B1F2C), RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = menuInteraction,
                                indication = null,
                                onClick = { isMenuOpen = true }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    InstanceQuickActionMenu(
                        expanded = isMenuOpen,
                        onDismissRequest = { isMenuOpen = false },
                        instance = instance,
                        isRunning = isRunning,
                        onLaunchOrStop = onLaunchOrStop,
                        onOpenWorkspace = {},
                        onOpenFolder = onOpenFolder,
                        onDuplicate = onDuplicate,
                        onExport = onExport,
                        onRename = onRename,
                        onRepair = onRepair,
                        onDelete = onDelete,
                        onEditLogo = onEditLogo
                    )
                }
            }
        }

        // IDENTITY ROW: Artwork, Name, Version Badge, Favorite Star, and Primary Launch Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Icon + Titles & Badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InstanceArtworkIcon(
                    instance = instance,
                    size = 64.dp,
                    isEditable = onEditLogo != null,
                    onEditClick = onEditLogo
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = instance.name,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )

                        // Rename button
                        val renameInteraction = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF141824))
                                .clickable(
                                    interactionSource = renameInteraction,
                                    indication = null,
                                    onClick = onRename
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DriveFileRenameOutline,
                                contentDescription = "Rename",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(13.dp)
                            )
                        }

                        // Favorite star
                        val starInteraction = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF141824))
                                .clickable(
                                    interactionSource = starInteraction,
                                    indication = null,
                                    onClick = onFavoriteToggle
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (instance.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (instance.isFavorite) Color(0xFFEF4444) else Color(0xFF64748B),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // Metadata row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EzzLoaderBadge(loaderType = instance.loaderType)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF141722))
                                .border(1.dp, Color(0xFF262C3F), RoundedCornerShape(4.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = instance.minecraftVersion,
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Status Badge
                        if (isRunning) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x2210B981))
                                    .border(1.dp, Color(0x4410B981), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                                Text(
                                    text = if (runningTimeFormatted != null) "RUNNING • $runningTimeFormatted" else "RUNNING",
                                    color = Color(0xFF10B981),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        } else if (isLaunching) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x228B5CF6))
                                    .border(1.dp, Color(0x448B5CF6), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF8B5CF6),
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp
                                )
                                Text(
                                    text = "LAUNCHING...",
                                    color = Color(0xFF8B5CF6),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // Right: Primary Play / Stop Action
            EzzButton(
                text = if (isRunning) "Stop Process" else if (isLaunching) "Launching..." else "Launch Game",
                icon = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                onClick = onLaunchOrStop,
                variant = if (isRunning) EzzButtonVariant.DANGER else EzzButtonVariant.PRIMARY,
                size = EzzButtonSize.LARGE,
                isLoading = isLaunching
            )
        }
    }
}
