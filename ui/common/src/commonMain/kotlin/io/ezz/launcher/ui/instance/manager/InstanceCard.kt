package io.ezz.launcher.ui.instance.manager

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
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
fun InstanceCard(
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
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var isMenuOpen by remember { mutableStateOf(false) }

    val artworkScale by animateFloatAsState(
        targetValue = if (isHovered) 1.035f else 1.0f,
        animationSpec = tween(140)
    )
    val favoriteScale by animateFloatAsState(
        targetValue = if (instance.isFavorite) 1.2f else 1.0f,
        animationSpec = tween(150)
    )

    val elevationColor = if (isHovered) Color(0xFF141824) else Color(0xFF0E1118)
    val borderColor = when {
        isRunning -> Color(0xFF10B981)
        isHovered -> Color(0xFF8B5CF6).copy(alpha = 0.55f)
        else -> Color(0xFF1B1F2C)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(elevationColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onCardClick
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // TOP BANNER / ARTWORK HEADER
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF161A28),
                                Color(0xFF0E1118)
                            )
                        )
                    )
            ) {
                // Artwork Icon (top left) with micro-hover scale
                Box(
                    modifier = Modifier
                        .padding(start = 14.dp, top = 14.dp)
                        .scale(artworkScale)
                ) {
                    InstanceArtworkIcon(
                        instance = instance,
                        size = 52.dp
                    )
                }

                // Top right actions: Favorite button & Status Badge
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Running / Launching indicator badge
                    if (isRunning) {
                        RunningStatusBadge()
                    } else if (isLaunching) {
                        LaunchingStatusBadge()
                    }

                    // Favorite Star Toggle
                    val starInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .scale(favoriteScale)
                            .clip(CircleShape)
                            .background(Color(0x6607080A))
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
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // MIDDLE BODY: Name, Loader/Version badges, and Telemetry
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Name
                Text(
                    text = instance.name,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Version & Loader Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Telemetry: Playtime & Last Played
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = formatPlaytime(instance.totalPlayTimeSeconds),
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Text(
                        text = formatLastPlayed(instance.lastPlayedAt),
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
            }

            // BOTTOM BAR: Action buttons (Launch/Stop & 3-Dot Menu)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = Color(0xFF161A24),
                        shape = RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
                    )
                    .background(Color(0xFF090B10))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Launch / Stop CTA Button
                val playInteraction = remember { MutableInteractionSource() }
                val isPlayHovered by playInteraction.collectIsHoveredAsState()

                val playBtnScale by animateFloatAsState(
                    targetValue = if (isPlayHovered && !isLaunching) 1.03f else 1.0f,
                    animationSpec = tween(120)
                )

                val playBg = when {
                    isRunning -> if (isPlayHovered) Color(0xFFDC2626) else Color(0x26EF4444)
                    isLaunching -> Color(0xFF6B21A8)
                    else -> if (isPlayHovered) Color(0xFF7C3AED) else Color(0xFF8B5CF6)
                }
                val playText = when {
                    isRunning -> "Stop"
                    isLaunching -> "Launching..."
                    else -> "Launch"
                }

                Row(
                    modifier = Modifier
                        .scale(playBtnScale)
                        .clip(RoundedCornerShape(8.dp))
                        .background(playBg)
                        .clickable(
                            interactionSource = playInteraction,
                            indication = null,
                            enabled = !isLaunching,
                            onClick = onPlayClick
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
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
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = playText,
                        color = if (isRunning && !isPlayHovered) Color(0xFFEF4444) else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // 3-Dot Menu Button
                Box {
                    val menuInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF10131A))
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

@Composable
private fun RunningStatusBadge() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x2210B981))
            .border(1.dp, Color(0x4410B981), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(Color(0xFF10B981))
        )
        Text(
            text = "RUNNING",
            color = Color(0xFF10B981),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun LaunchingStatusBadge() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x228B5CF6))
            .border(1.dp, Color(0x448B5CF6), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            color = Color(0xFF8B5CF6),
            modifier = Modifier.size(8.dp),
            strokeWidth = 1.5.dp
        )
        Text(
            text = "STARTING",
            color = Color(0xFF8B5CF6),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

private fun formatPlaytime(seconds: Long): String {
    if (seconds <= 0) return "Never played"
    val minutes = seconds / 60
    val hours = minutes / 60
    val remainingMins = minutes % 60
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
