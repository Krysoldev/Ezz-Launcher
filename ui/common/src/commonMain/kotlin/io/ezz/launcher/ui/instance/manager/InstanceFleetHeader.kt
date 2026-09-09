package io.ezz.launcher.ui.instance.manager

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.EzzSearchField
import io.ezz.launcher.ui.instance.model.InstanceFilterChip
import io.ezz.launcher.ui.instance.model.InstanceSortOrder
import io.ezz.launcher.ui.instance.model.InstanceViewMode

@Composable
fun InstanceFleetHeader(
    totalInstances: Int,
    runningCount: Int,
    totalPlaytimeHours: Long,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedFilter: InstanceFilterChip,
    onFilterSelect: (InstanceFilterChip) -> Unit,
    selectedSort: InstanceSortOrder,
    onSortSelect: (InstanceSortOrder) -> Unit,
    viewMode: InstanceViewMode,
    onViewModeChange: (InstanceViewMode) -> Unit,
    onCreateInstance: () -> Unit,
    onImportModpack: () -> Unit,
    activeDownloadsCount: Int = 0,
    onOpenActivityDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var sortMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF07080A))
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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

        // BOTTOM ROW: Search Bar, Filter Chips, Sort Selector, and View Mode Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Search Field and Filter Chips
            Row(
                modifier = Modifier.weight(1f).padding(end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search Field
                EzzSearchField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = "Search instances...",
                    modifier = Modifier.width(260.dp)
                )

                // Filter Chips (Scrollable if cramped)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    InstanceFilterChip.entries.forEach { chip ->
                        val isSelected = selectedFilter == chip
                        val interaction = remember { MutableInteractionSource() }
                        val isHovered by interaction.collectIsHoveredAsState()

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        isSelected -> Color(0xFF1E2333)
                                        isHovered -> Color(0xFF141722)
                                        else -> Color(0xFF0C0E14)
                                    }
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFF8B5CF6) else Color(0xFF1B1F2C),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable(
                                    interactionSource = interaction,
                                    indication = null
                                ) { onFilterSelect(chip) }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = chip.label,
                                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Right: Sort Dropdown & View Mode Switcher
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sort Dropdown
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0C0E14))
                            .border(1.dp, Color(0xFF1B1F2C), RoundedCornerShape(8.dp))
                            .clickable { sortMenuOpen = true }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sort: ${selectedSort.label}",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false },
                        modifier = Modifier
                            .background(Color(0xFF10131A))
                            .border(1.dp, Color(0xFF1E2436), RoundedCornerShape(10.dp))
                    ) {
                        InstanceSortOrder.entries.forEach { sort ->
                            val isChosen = selectedSort == sort
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = sort.label,
                                            color = if (isChosen) Color(0xFF8B5CF6) else Color(0xFFE2E8F0),
                                            fontSize = 13.sp,
                                            fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (isChosen) {
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color(0xFF8B5CF6),
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onSortSelect(sort)
                                    sortMenuOpen = false
                                }
                            )
                        }
                    }
                }

                // View Mode Switcher: Cinematic / Compact / List
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0C0E14))
                        .border(1.dp, Color(0xFF1B1F2C), RoundedCornerShape(8.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ViewModeButton(
                        icon = Icons.Default.ViewAgenda,
                        tooltip = "Cinematic Grid",
                        isSelected = viewMode == InstanceViewMode.CINEMATIC,
                        onClick = { onViewModeChange(InstanceViewMode.CINEMATIC) }
                    )
                    ViewModeButton(
                        icon = Icons.Default.GridView,
                        tooltip = "Compact Grid",
                        isSelected = viewMode == InstanceViewMode.COMPACT,
                        onClick = { onViewModeChange(InstanceViewMode.COMPACT) }
                    )
                    ViewModeButton(
                        icon = Icons.Default.FormatListBulleted,
                        tooltip = "List View",
                        isSelected = viewMode == InstanceViewMode.LIST,
                        onClick = { onViewModeChange(InstanceViewMode.LIST) }
                    )
                }
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

@Composable
private fun ViewModeButton(
    icon: ImageVector,
    tooltip: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    isSelected -> Color(0xFF1E2333)
                    isHovered -> Color(0xFF141722)
                    else -> Color.Transparent
                }
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = tooltip,
            tint = if (isSelected) Color.White else Color(0xFF64748B),
            modifier = Modifier.size(15.dp)
        )
    }
}
