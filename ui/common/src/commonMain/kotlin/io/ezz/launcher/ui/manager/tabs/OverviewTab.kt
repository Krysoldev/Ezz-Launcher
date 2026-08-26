package io.ezz.launcher.ui.manager.tabs

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.InstanceManagerTab
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.ui.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OverviewTab(
    instance: Instance,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val manageMods by viewModel.manageMods.collectAsState()
    val manageResourcePacks by viewModel.manageResourcePacks.collectAsState()
    val manageShaders by viewModel.manageShaders.collectAsState()
    val manageWorlds by viewModel.manageWorlds.collectAsState()
    val manageScreenshots by viewModel.manageScreenshots.collectAsState()

    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        // Centered max-width content container
        Box(modifier = Modifier.fillMaxWidth().widthIn(max = 1440.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // LEFT COLUMN (~62%): Instance Details + Recent Activity
                Column(
                    modifier = Modifier.weight(0.62f),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 1. Instance Information Card
                    InstanceInformationSection(
                        instance = instance,
                        viewModel = viewModel
                    )

                    // 2. Recent Activity Card
                    RecentActivitySection(
                        mods = manageMods,
                        resourcePacks = manageResourcePacks,
                        shaders = manageShaders,
                        worlds = manageWorlds,
                        screenshots = manageScreenshots,
                        onNavigate = { tab -> viewModel.setManageTab(tab) }
                    )
                }

                // RIGHT COLUMN (~38%): Content Statistics + Quick Management
                Column(
                    modifier = Modifier.weight(0.38f),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 1. Content Statistics Grid
                    ContentStatisticsSection(
                        modsCount = manageMods.size,
                        packsCount = manageResourcePacks.size,
                        shadersCount = manageShaders.size,
                        worldsCount = manageWorlds.size,
                        screenshotsCount = manageScreenshots.size,
                        onNavigate = { tab -> viewModel.setManageTab(tab) }
                    )

                    // 2. Quick Management Actions
                    QuickActionsSection(
                        instance = instance,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

/**
 * Clean, structured instance metadata table.
 */
@Composable
private fun InstanceInformationSection(
    instance: Instance,
    viewModel: AppViewModel
) {
    val javaReq = io.ezz.launcher.core.minecraft.version.JavaCompatibility.getRequiredJavaMajorVersion(instance.minecraftVersion)
    val instancePathStr = viewModel.pathProvider.getInstanceDirectory(instance.id).toString()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF131313))
            .border(1.dp, Color(0xFF242424), RoundedCornerShape(12.dp))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFFAAAAAA),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "INSTANCE INFORMATION",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF1E1E1E))
                        .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(4.dp))
                        .clickable { viewModel.setManageTab(InstanceManagerTab.SETTINGS) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Configure Settings", color = Color(0xFFCCCCCC), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }

            // Information Grid Rows
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0E0E0E))
                    .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
            ) {
                InfoRow("Minecraft Version", "v${instance.minecraftVersion}", isEven = true)
                InfoRow("Mod Loader", "${instance.loaderType.name}${if (!instance.loaderVersion.isNullOrBlank()) " (${instance.loaderVersion})" else ""}", isEven = false)
                InfoRow("Java Environment", "Java $javaReq (Managed Runtime)", isEven = true)
                InfoRow("Memory Allocation", "${instance.maxMemoryMb} MB (${instance.maxMemoryMb / 1024} GB RAM)", isEven = false)
                InfoRow("Window Resolution", "${instance.windowWidth} × ${instance.windowHeight}", isEven = true)
                InfoRow(
                    label = "Instance Directory",
                    value = instancePathStr,
                    isEven = false,
                    isPath = true,
                    onCopy = {
                        viewModel.platformBridge.copyToClipboard(instancePathStr)
                    }
                )
                val lastPlayed = instance.lastPlayedAt
                InfoRow(
                    label = "Last Played",
                    value = if (lastPlayed != null && lastPlayed > 0) {
                        SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(lastPlayed))
                    } else {
                        "Never played yet"
                    },
                    isEven = true
                )
                InfoRow(
                    label = "Total Playtime",
                    value = formatPlaytime(instance.totalPlayTimeSeconds),
                    isEven = false
                )
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    isEven: Boolean,
    isPath: Boolean = false,
    onCopy: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isEven) Color(0xFF121212) else Color(0xFF0E0E0E))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFF888888),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Text(
                text = value,
                color = Color(0xFFEEEEEE),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = if (isPath) FontFamily.Monospace else FontFamily.Default
            )

            if (onCopy != null) {
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Path",
                        tint = Color(0xFF888888),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

/**
 * Compact Recent Activity Feed.
 */
@Composable
private fun RecentActivitySection(
    mods: List<io.ezz.launcher.core.model.instance.LocalMod>,
    resourcePacks: List<io.ezz.launcher.core.model.instance.LocalResourcePack>,
    shaders: List<io.ezz.launcher.core.model.instance.LocalShaderPack>,
    worlds: List<io.ezz.launcher.core.model.instance.LocalWorld>,
    screenshots: List<io.ezz.launcher.core.model.instance.LocalScreenshot>,
    onNavigate: (InstanceManagerTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF131313))
            .border(1.dp, Color(0xFF242424), RoundedCornerShape(12.dp))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = Color(0xFFAAAAAA),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "RECENT ACTIVITY",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
            }

            val hasAnyItems = mods.isNotEmpty() || resourcePacks.isNotEmpty() || shaders.isNotEmpty() || worlds.isNotEmpty() || screenshots.isNotEmpty()

            if (!hasAnyItems) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0E0E0E))
                        .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recent activity recorded yet. Launch instance or add mods to start.",
                        color = Color(0xFF666666),
                        fontSize = 12.sp
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Top Mod
                    if (mods.isNotEmpty()) {
                        val firstMod = mods.first()
                        ActivityItem(
                            icon = Icons.Default.Extension,
                            title = "Installed Mod: ${firstMod.name}",
                            subtitle = "v${firstMod.version} • ${if (firstMod.enabled) "Active" else "Disabled"}",
                            tag = "MODS",
                            onClick = { onNavigate(InstanceManagerTab.MODS) }
                        )
                    }

                    // Top World
                    if (worlds.isNotEmpty()) {
                        val firstWorld = worlds.first()
                        val worldLastPlayed = if (firstWorld.lastPlayed > 0) {
                            SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(firstWorld.lastPlayed))
                        } else {
                            "Never"
                        }
                        ActivityItem(
                            icon = Icons.Default.Public,
                            title = "World: ${firstWorld.name}",
                            subtitle = "${firstWorld.gameType} • Last played: $worldLastPlayed",
                            tag = "SAVED",
                            onClick = { onNavigate(InstanceManagerTab.WORLDS) }
                        )
                    }

                    // Top Resource Pack / Shader
                    if (resourcePacks.isNotEmpty()) {
                        val pack = resourcePacks.first()
                        ActivityItem(
                            icon = Icons.Default.Palette,
                            title = "Resource Pack: ${pack.name}",
                            subtitle = if (pack.enabled) "Active Pack" else "Installed (Disabled)",
                            tag = "PACK",
                            onClick = { onNavigate(InstanceManagerTab.RESOURCE_PACKS) }
                        )
                    } else if (shaders.isNotEmpty()) {
                        val shader = shaders.first()
                        ActivityItem(
                            icon = Icons.Default.Layers,
                            title = "Shader: ${shader.name}",
                            subtitle = if (shader.enabled) "Active Shaderpack" else "Installed (Disabled)",
                            tag = "SHADER",
                            onClick = { onNavigate(InstanceManagerTab.SHADERS) }
                        )
                    }

                    // Top Screenshot
                    if (screenshots.isNotEmpty()) {
                        val shot = screenshots.first()
                        ActivityItem(
                            icon = Icons.Default.Image,
                            title = "Screenshot: ${shot.fileName}",
                            subtitle = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(shot.lastModified)),
                            tag = "CAPTURE",
                            onClick = { onNavigate(InstanceManagerTab.SCREENSHOTS) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tag: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isHovered) Color(0xFF1A1A1A) else Color(0xFF0E0E0E))
            .border(1.dp, if (isHovered) Color(0xFF333333) else Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E1E1E)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color(0xFFAAAAAA), modifier = Modifier.size(14.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = Color(0xFF777777),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF1C1C1C))
                .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(tag, color = Color(0xFF888888), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Clickable Content Statistics Cards.
 */
@Composable
private fun ContentStatisticsSection(
    modsCount: Int,
    packsCount: Int,
    shadersCount: Int,
    worldsCount: Int,
    screenshotsCount: Int,
    onNavigate: (InstanceManagerTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF131313))
            .border(1.dp, Color(0xFF242424), RoundedCornerShape(12.dp))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Widgets,
                    contentDescription = null,
                    tint = Color(0xFFAAAAAA),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "CONTENT STATISTICS",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
            }

            // Stat Cards Grid (2 columns)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(
                        title = "MODS",
                        count = modsCount,
                        icon = Icons.Default.Extension,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(InstanceManagerTab.MODS) }
                    )
                    StatCard(
                        title = "RESOURCE PACKS",
                        count = packsCount,
                        icon = Icons.Default.Palette,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(InstanceManagerTab.RESOURCE_PACKS) }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(
                        title = "SHADERS",
                        count = shadersCount,
                        icon = Icons.Default.Layers,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(InstanceManagerTab.SHADERS) }
                    )
                    StatCard(
                        title = "WORLDS",
                        count = worldsCount,
                        icon = Icons.Default.Public,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(InstanceManagerTab.WORLDS) }
                    )
                }

                StatCard(
                    title = "SCREENSHOTS",
                    count = screenshotsCount,
                    icon = Icons.Default.Image,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onNavigate(InstanceManagerTab.SCREENSHOTS) }
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    count: Int,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else if (isHovered) 1.02f else 1.0f,
        animationSpec = tween(100)
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isHovered) Color(0xFF1C1C1C) else Color(0xFF0E0E0E))
            .border(1.dp, if (isHovered) Color(0xFF383838) else Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = Color(0xFF777777),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isHovered) Color.White else Color(0xFF555555),
                    modifier = Modifier.size(14.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = count.toString(),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Manage",
                        color = if (isHovered) Color.White else Color(0xFF888888),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = if (isHovered) Color.White else Color(0xFF888888),
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
    }
}

/**
 * Quick Action Shortcut Cards.
 */
@Composable
private fun QuickActionsSection(
    instance: Instance,
    viewModel: AppViewModel
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF131313))
            .border(1.dp, Color(0xFF242424), RoundedCornerShape(12.dp))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = Color(0xFFAAAAAA),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "QUICK ACTIONS",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickActionItem(
                    title = "Manage Installed Mods",
                    description = "Enable, disable or configure mods",
                    icon = Icons.Default.Extension,
                    onClick = { viewModel.setManageTab(InstanceManagerTab.MODS) }
                )

                QuickActionItem(
                    title = "Browse Addons & Shaders",
                    description = "Search Modrinth catalog for content",
                    icon = Icons.Default.Download,
                    onClick = {
                        viewModel.setManageTab(InstanceManagerTab.MODS)
                        viewModel.searchMods()
                    }
                )

                QuickActionItem(
                    title = "Open Game Directory",
                    description = "Explore saves, configs, and logs on disk",
                    icon = Icons.Default.FolderOpen,
                    onClick = { viewModel.openInstanceFolder(instance.id) }
                )

                QuickActionItem(
                    title = "Repair & Verify Files",
                    description = "Diagnose missing assets or corrupted libraries",
                    icon = Icons.Default.Build,
                    onClick = { viewModel.showRepairDialog.value = true }
                )
            }
        }
    }
}

@Composable
private fun QuickActionItem(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isHovered) Color(0xFF1A1A1A) else Color(0xFF0E0E0E))
            .border(1.dp, if (isHovered) Color(0xFF333333) else Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E1E1E)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    color = Color(0xFF777777),
                    fontSize = 11.sp
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = if (isHovered) Color.White else Color(0xFF555555),
            modifier = Modifier.size(13.dp)
        )
    }
}

private fun formatPlaytime(seconds: Long): String {
    if (seconds <= 0) return "0 minutes"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
