package io.ezz.launcher.ui.instance.manager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
@Composable
fun InstanceFleetHeader(
    totalInstances: Int,
    runningCount: Int,
    totalPlaytimeHours: Long,
    onCreateInstance: () -> Unit,
    onImportModpack: () -> Unit,
    activeDownloadsCount: Int = 0,
    onOpenActivityDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF07080A))
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        // TOP ROW: Title, Fleet Telemetry Stats, & Primary Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Title & Live Stats
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Instances",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "Manage your Minecraft installations and modpacks",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                }

                // Stats Pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FleetStatBadge(
                        label = "Total",
                        value = totalInstances.toString(),
                        icon = Icons.Default.Layers
                    )

                    if (runningCount > 0) {
                        FleetRunningBadge(runningCount = runningCount)
                    }

                    if (totalPlaytimeHours > 0) {
                        FleetStatBadge(
                            label = "Playtime",
                            value = "${totalPlaytimeHours}h",
                            icon = Icons.Default.Schedule
                        )
                    }
                }
            }

            // Right: Primary CTAs (Create, Import, & Activity Drawer)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (activeDownloadsCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF8B5CF6).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFF8B5CF6), RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenActivityDrawer)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(16.dp))
                            Text(
                                text = "Downloads ($activeDownloadsCount)",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                EzzButton(
                    text = "Import Modpack",
                    icon = Icons.Default.FileDownload,
                    onClick = onImportModpack,
                    variant = EzzButtonVariant.SECONDARY,
                    size = EzzButtonSize.MEDIUM
                )

                EzzButton(
                    text = "Create Instance",
                    icon = Icons.Default.Add,
                    onClick = onCreateInstance,
                    variant = EzzButtonVariant.PRIMARY,
                    size = EzzButtonSize.MEDIUM
                )
            }
        }
    }
}

@Composable
private fun FleetStatBadge(
    label: String,
    value: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF10131A))
            .border(1.dp, Color(0xFF1B1F2C), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF94A3B8),
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = "$label: $value",
            color = Color(0xFFCBD5E1),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun FleetRunningBadge(runningCount: Int) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x1A10B981))
            .border(1.dp, Color(0x3310B981), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
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
            text = "$runningCount Running",
            color = Color(0xFF10B981),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
