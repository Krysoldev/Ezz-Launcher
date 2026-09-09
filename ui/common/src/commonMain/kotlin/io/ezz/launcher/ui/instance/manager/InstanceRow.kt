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
import io.ezz.launcher.ui.components.EzzLoaderBadge
import io.ezz.launcher.ui.components.InstanceArtworkIcon
import io.ezz.launcher.ui.instance.dialogs.InstanceQuickActionMenu
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InstanceRow(
    instance: Instance,
    isRunning: Boolean,
    isLaunching: Boolean,
    onRowClick: () -> Unit,
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

    val bg = when {
        isRunning -> Color(0x1210B981)
        isHovered -> Color(0xFF131620)
        else -> Color(0xFF0A0C11)
    }

    val borderColor = when {
        isRunning -> Color(0x4410B981)
        isHovered -> Color(0xFF262C3F)
        else -> Color(0xFF141722)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onRowClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Column 1: Icon & Name
            Row(
                modifier = Modifier.weight(2.5f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InstanceArtworkIcon(
                    instance = instance,
                    size = 36.dp
                )

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = instance.name,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
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
                        text = "Minecraft ${instance.minecraftVersion}",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Column 2: Loader Badge
            Box(
                modifier = Modifier.weight(1.5f),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EzzLoaderBadge(loaderType = instance.loaderType)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF141722))
                            .border(1.dp, Color(0xFF262C3F), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = instance.minecraftVersion,
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Column 3: Playtime
            Text(
                text = formatPlaytime(instance.totalPlayTimeSeconds),
                color = Color(0xFFCBD5E1),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1.2f)
            )

            // Column 4: Last Played
            Text(
                text = formatLastPlayed(instance.lastPlayedAt),
                color = Color(0xFF64748B),
                fontSize = 12.sp,
                modifier = Modifier.weight(1.2f)
            )

            // Column 5: Actions
            Row(
                modifier = Modifier.weight(1.6f),
                horizontalArrangement = Arrangement.End,
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

                Spacer(modifier = Modifier.width(6.dp))

                // Play / Stop Button
                val playInteraction = remember { MutableInteractionSource() }
                val isPlayHovered by playInteraction.collectIsHoveredAsState()
                val playBg = when {
                    isRunning -> if (isPlayHovered) Color(0xFFDC2626) else Color(0x26EF4444)
                    else -> if (isPlayHovered) Color(0xFF7C3AED) else Color(0xFF8B5CF6)
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(playBg)
                        .clickable(
                            interactionSource = playInteraction,
                            indication = null,
                            onClick = onPlayClick
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isLaunching) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(10.dp),
                            strokeWidth = 1.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (isRunning && !isPlayHovered) Color(0xFFEF4444) else Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = if (isRunning) "Stop" else if (isLaunching) "..." else "Play",
                        color = if (isRunning && !isPlayHovered) Color(0xFFEF4444) else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 3-Dots Menu
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
                        onOpenWorkspace = onRowClick,
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

private fun formatPlaytime(seconds: Long): String {
    if (seconds <= 0) return "—"
    val hours = seconds / 3600
    val remainingMins = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${remainingMins}m" else "${remainingMins}m"
}

private fun formatLastPlayed(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0) return "Never"
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val oneDay = 24 * 60 * 60 * 1000L
    return when {
        diff < oneDay -> "Today"
        diff < 2 * oneDay -> "Yesterday"
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}
