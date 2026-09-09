package io.ezz.launcher.ui.instance.manager

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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.ui.components.InstanceArtworkIcon
import io.ezz.launcher.ui.instance.dialogs.InstanceQuickActionMenu
import io.ezz.launcher.ui.instance.model.displayName

@Composable
fun InstanceCardCompact(
    instance: Instance,
    isRunning: Boolean,
    isLaunching: Boolean,
    onCardClick: () -> Unit,
    onPlayClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onOpenFolder: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onRename: () -> Unit,
    onRepair: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    var isMenuOpen by remember { mutableStateOf(false) }

    val bg = if (isHovered) Color(0xFF141722) else Color(0xFF0E1118)
    val border = when {
        isRunning -> Color(0xFF10B981)
        isHovered -> Color(0xFF2E364F)
        else -> Color(0xFF1B1F2C)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onCardClick
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Icon + Info
            Row(
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InstanceArtworkIcon(
                    instance = instance,
                    size = 42.dp
                )

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = instance.name,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isRunning) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                        }
                    }

                    Text(
                        text = "MC ${instance.minecraftVersion} • ${instance.loaderType.displayName}",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Right: Actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Favorite
                val starInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
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
                        tint = if (instance.isFavorite) Color(0xFFEF4444) else Color(0xFF475569),
                        modifier = Modifier.size(15.dp)
                    )
                }

                // Play / Stop
                val playInteraction = remember { MutableInteractionSource() }
                val isPlayHovered by playInteraction.collectIsHoveredAsState()
                val playBg = when {
                    isRunning -> if (isPlayHovered) Color(0xFFDC2626) else Color(0x26EF4444)
                    else -> if (isPlayHovered) Color(0xFF7C3AED) else Color(0xFF8B5CF6)
                }

                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(playBg)
                        .clickable(
                            interactionSource = playInteraction,
                            indication = null,
                            onClick = onPlayClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLaunching) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (isRunning && !isPlayHovered) Color(0xFFEF4444) else Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                // 3-Dots
                Box {
                    val menuInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
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
                        onLaunchOrStop = onPlayClick,
                        onOpenWorkspace = onCardClick,
                        onOpenFolder = onOpenFolder,
                        onDuplicate = onDuplicate,
                        onExport = onExport,
                        onRename = onRename,
                        onRepair = onRepair,
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}
