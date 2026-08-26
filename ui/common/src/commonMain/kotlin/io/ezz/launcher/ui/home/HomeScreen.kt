package io.ezz.launcher.ui.home

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.ui.components.EzzBadge
import io.ezz.launcher.ui.components.EzzBadgeVariant
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.EzzCard
import io.ezz.launcher.ui.components.EzzEmptyState
import io.ezz.launcher.ui.components.EzzIconButton
import io.ezz.launcher.ui.components.EzzLoaderBadge
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.AppViewModel
import io.ezz.launcher.ui.viewmodel.NavigationScreen

@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val colors = EzzTheme.colors
    val selectedInstance by viewModel.selectedInstance.collectAsState()
    val instances by viewModel.instanceRepository.instances.collectAsState()
    val installedMods by viewModel.installedMods.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(32.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Welcome to Ezz Launcher",
                    color = colors.textPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (selectedInstance != null) "Ready to play Minecraft Java Edition" else "Create an instance or select one below",
                    color = colors.textSecondary,
                    fontSize = 14.sp
                )
            }

            EzzButton(
                text = "New Instance",
                onClick = { viewModel.showCreateInstanceDialog.value = true },
                variant = EzzButtonVariant.SECONDARY,
                size = EzzButtonSize.MEDIUM,
                icon = Icons.Default.Add
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 1. Maintenance Mode Alert
        val isMaintenanceMode by viewModel.isMaintenanceMode.collectAsState()
        val maintenanceMessage by viewModel.maintenanceMessage.collectAsState()
        if (isMaintenanceMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.danger.copy(alpha = 0.15f))
                    .border(1.dp, colors.danger, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "⚠️ MAINTENANCE MODE: $maintenanceMessage",
                    color = colors.danger,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 2. Launcher Update Alert
        val updateCheckResult by viewModel.updateCheckResult.collectAsState()
        if (updateCheckResult?.hasUpdate == true && updateCheckResult?.latestRelease != null) {
            val latest = updateCheckResult!!.latestRelease!!
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.primary.copy(alpha = 0.12f))
                    .border(1.dp, colors.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (updateCheckResult!!.isRequired) "🚨 Mandatory Update Required (v${latest.version})" else "🚀 New Launcher Update: v${latest.version}",
                            color = colors.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val notes = latest.releaseNotes
                        if (!notes.isNullOrBlank()) {
                            Text(
                                text = notes,
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }

                    val dlUrl = latest.downloadUrl
                    if (!dlUrl.isNullOrBlank()) {
                        EzzButton(
                            text = "Download",
                            onClick = { viewModel.platformBridge.openUrl(dlUrl) },
                            variant = EzzButtonVariant.PRIMARY,
                            size = EzzButtonSize.SMALL
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 3. Announcements Broadcast Feed
        val announcements by viewModel.announcements.collectAsState()
        if (announcements.isNotEmpty()) {
            val top = announcements.first()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceVariant)
                    .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "📢 ${top.title}: ${top.message}",
                        color = colors.textPrimary,
                        fontSize = 13.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 4. Hero Instance Showcase Card
        if (selectedInstance != null) {
            val inst = selectedInstance!!
            EzzCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = colors.surfaceVariant,
                borderColor = colors.primary.copy(alpha = 0.4f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                EzzLoaderBadge(loaderType = inst.loaderType)
                                Spacer(modifier = Modifier.width(8.dp))
                                EzzBadge(
                                    text = "v${inst.minecraftVersion}",
                                    variant = EzzBadgeVariant.NEUTRAL
                                )
                                if (inst.loaderType == LoaderType.FABRIC) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    EzzBadge(
                                        text = "${installedMods.size} Mods Installed",
                                        variant = EzzBadgeVariant.INFO
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = inst.name,
                                color = colors.textPrimary,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        // Open Folder Shortcut
                        EzzIconButton(
                            icon = Icons.Default.FolderOpen,
                            onClick = { viewModel.openInstanceFolder(inst.id) },
                            contentDescription = "Open Directory",
                            tint = colors.textSecondary,
                            backgroundColor = colors.surface
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Instance Specs Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        StatItem(
                            icon = Icons.Default.Memory,
                            label = "RAM Allocated",
                            value = "${inst.maxMemoryMb / 1024} GB"
                        )
                        StatItem(
                            icon = Icons.Default.Speed,
                            label = "Playtime",
                            value = formatPlaytime(inst.totalPlayTimeSeconds)
                        )
                        if (inst.loaderType == LoaderType.FABRIC) {
                            StatItem(
                                icon = Icons.Default.Extension,
                                label = "Active Mods",
                                value = "${installedMods.count { it.enabled }} enabled"
                            )
                        }
                    }
                }
            }
        } else {
            EzzEmptyState(
                title = "No Instance Selected",
                description = "Create or choose a Minecraft instance from your library to start playing.",
                actionButtonText = "Create Instance",
                onActionClick = { viewModel.showCreateInstanceDialog.value = true }
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 5. Quick Switcher: All Instances
        if (instances.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Instances (${instances.size})",
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "View All",
                    color = colors.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { viewModel.navigateTo(NavigationScreen.INSTANCES) }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(instances, key = { it.id }) { inst ->
                    val isSelected = inst.id == selectedInstance?.id
                    InstanceQuickCard(
                        instance = inst,
                        isSelected = isSelected,
                        onClick = { viewModel.selectInstance(inst) }
                    )
                }
            }
        }
    }
}

@Composable
private fun InstanceQuickCard(
    instance: Instance,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = EzzTheme.colors

    EzzCard(
        modifier = Modifier.width(220.dp),
        borderColor = if (isSelected) colors.primary else colors.border,
        backgroundColor = if (isSelected) colors.surfaceVariant else colors.cardBackground,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EzzLoaderBadge(loaderType = instance.loaderType)
                Text(
                    text = "v${instance.minecraftVersion}",
                    color = colors.textMuted,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = instance.name,
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    label: String,
    value: String
) {
    val colors = EzzTheme.colors

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = label, color = colors.textSecondary, fontSize = 11.sp)
            Text(text = value, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatPlaytime(seconds: Long): String {
    if (seconds <= 0) return "Never played"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
