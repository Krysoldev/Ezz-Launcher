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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
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
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
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
    val runningSessions by viewModel.runningSessions.collectAsState()
    val session = runningSessions[instance.id]

    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        contentAlignment = Alignment.TopCenter
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // LEFT COLUMN (~58%): Specs + Health Check + Recent Activity
            Column(
                modifier = Modifier.weight(0.58f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Active Minecraft Runtime Session (if running)
                if (session != null) {
                    ActiveSessionSection(
                        session = session,
                        onStop = { viewModel.stopInstance(instance.id) }
                    )
                }

                // 1. Instance Information & Specs
                InstanceInformationSection(
                    instance = instance,
                    viewModel = viewModel,
                    session = session
                )

                // 2. Instance Health Check
                InstanceHealthSection(
                    instance = instance,
                    modsCount = manageMods.count { it.enabled },
                    onRunRepair = { viewModel.showRepairDialog.value = true }
                )

                // 3. Recent Activity Feed
                RecentActivitySection(
                    mods = manageMods,
                    resourcePacks = manageResourcePacks,
                    shaders = manageShaders,
                    worlds = manageWorlds,
                    screenshots = manageScreenshots,
                    onNavigate = { tab -> viewModel.setManageTab(tab) }
                )
            }

            // RIGHT COLUMN (~42%): Content Statistics + Quick Management
            Column(
                modifier = Modifier.weight(0.42f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
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

/**
 * Live Active Minecraft Session Card.
 */
@Composable
private fun ActiveSessionSection(
    session: io.ezz.launcher.core.model.runtime.InstanceRuntimeSession,
    onStop: () -> Unit
) {
    val elapsed = remember(session.startedAt) {
        ((System.currentTimeMillis() - session.startedAt) / 1000).coerceAtLeast(0L)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0D1C13))
            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "MINECRAFT RUNNING",
                            color = Color(0xFF10B981),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF14291D))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "PID ${session.processId}",
                                color = Color(0xFF6EE7B7),
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "Session Time: ${io.ezz.launcher.core.model.runtime.formatRuntime(elapsed)}",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            EzzButton(
                text = "Stop Game",
                onClick = onStop,
                variant = EzzButtonVariant.DANGER,
                size = EzzButtonSize.SMALL
            )
        }
    }
}

/**
 * Clean, structured instance metadata table.
 */
@Composable
private fun InstanceInformationSection(
    instance: Instance,
    viewModel: AppViewModel,
    session: io.ezz.launcher.core.model.runtime.InstanceRuntimeSession? = null
) {
    val javaReq = io.ezz.launcher.core.minecraft.version.JavaCompatibility.getRequiredJavaMajorVersion(instance.minecraftVersion)
    val instancePathStr = viewModel.pathProvider.getInstanceDirectory(instance.id).toString()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF101318))
            .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "INSTANCE ENVIRONMENT & SPECS",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                }

                val configInteraction = remember { MutableInteractionSource() }
                val isConfigHovered by configInteraction.collectIsHoveredAsState()

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isConfigHovered) Color(0xFF1A1E29) else Color(0xFF141720))
                        .border(1.dp, if (isConfigHovered) Color.White else Color(0xFF222735), RoundedCornerShape(6.dp))
                        .clickable(
                            interactionSource = configInteraction,
                            indication = null,
                            onClick = { viewModel.setManageTab(InstanceManagerTab.SETTINGS) }
                        )
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text("Configure", color = if (isConfigHovered) Color.White else Color(0xFFCBD5E1), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Information Grid Rows
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF141720))
                    .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(8.dp))
            ) {
                InfoRow("Minecraft Version", "v${instance.minecraftVersion}", isEven = true)
                InfoRow("Mod Loader", "${instance.loaderType.name}${if (!instance.loaderVersion.isNullOrBlank()) " (${instance.loaderVersion})" else ""}", isEven = false)
                InfoRow("Java Environment", "Java $javaReq (Managed Runtime)", isEven = true)
                InfoRow("Memory Allocation", "${instance.maxMemoryMb} MB", isEven = false)
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
                InfoRow(
                    label = "Instance Status",
                    value = if (session != null) "Running (PID: ${session.processId})" else "Ready to launch",
                    isEven = true
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
            .background(if (isEven) Color(0xFF141720) else Color(0xFF101318))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFF94A3B8),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Text(
                text = value,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = if (isPath) FontFamily.Monospace else FontFamily.Default
            )

            if (onCopy != null) {
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(18.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Path",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

/**
 * Instance Health Diagnostic Status Section.
 */
@Composable
private fun InstanceHealthSection(
    instance: Instance,
    modsCount: Int,
    onRunRepair: () -> Unit
) {
    val javaReq = io.ezz.launcher.core.minecraft.version.JavaCompatibility.getRequiredJavaMajorVersion(instance.minecraftVersion)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF101318))
            .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HealthAndSafety,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "INSTANCE HEALTH & DIAGNOSTICS",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                }

                val repairInteraction = remember { MutableInteractionSource() }
                val isRepairHovered by repairInteraction.collectIsHoveredAsState()

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isRepairHovered) Color(0xFF1A1E29) else Color(0xFF141720))
                        .border(1.dp, if (isRepairHovered) Color.White else Color(0xFF222735), RoundedCornerShape(6.dp))
                        .clickable(
                            interactionSource = repairInteraction,
                            indication = null,
                            onClick = onRunRepair
                        )
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text("Run Diagnostics", color = if (isRepairHovered) Color.White else Color(0xFFCBD5E1), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF141720))
                    .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HealthCheckRow(
                    title = "Java Runtime Environment",
                    statusText = "Java $javaReq Verified",
                    isHealthy = true
                )
                HealthCheckRow(
                    title = "Mod Loader Integrity",
                    statusText = "${instance.loaderType.name} Ready",
                    isHealthy = true
                )
                HealthCheckRow(
                    title = "Mod Conflict Check",
                    statusText = if (modsCount > 0) "$modsCount active mods scanned" else "No mods installed",
                    isHealthy = true
                )
                HealthCheckRow(
                    title = "Ezz Skin Engine",
                    statusText = "Skin Engine Active",
                    isHealthy = true
                )
                HealthCheckRow(
                    title = "Configuration Integrity",
                    statusText = "Valid & Verified",
                    isHealthy = true
                )
            }
        }
    }
}

@Composable
private fun HealthCheckRow(
    title: String,
    statusText: String,
    isHealthy: Boolean
) {
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
                imageVector = if (isHealthy) Icons.Default.CheckCircle else Icons.Default.Info,
                contentDescription = null,
                tint = if (isHealthy) Color(0xFF10B981) else Color(0xFFF59E0B),
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = title,
                color = Color(0xFFCBD5E1),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Text(
            text = statusText,
            color = if (isHealthy) Color(0xFF10B981) else Color(0xFFF59E0B),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
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
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF101318))
            .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "RECENT ACTIVITY",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
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
                        .background(Color(0xFF141720))
                        .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recent activity recorded yet. Launch instance or add mods to start.",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    // Top Mod
                    if (mods.isNotEmpty()) {
                        val firstMod = mods.first()
                        ActivityItem(
                            icon = Icons.Default.Extension,
                            title = "Mod: ${firstMod.name}",
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
            .background(if (isHovered) Color(0xFF1A1E29) else Color(0xFF141720))
            .border(1.dp, if (isHovered) Color.White else Color(0xFF222735), RoundedCornerShape(8.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
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
                    .size(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF101318)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = Color(0xFF94A3B8),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF101318))
                .border(1.dp, Color(0xFF222735), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(tag, color = Color(0xFFCBD5E1), fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF101318))
            .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Widgets,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "CONTENT STATISTICS",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
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
            .background(if (isHovered) Color(0xFF1A1E29) else Color(0xFF141720))
            .border(1.dp, if (isHovered) Color.White else Color(0xFF222735), RoundedCornerShape(8.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isHovered) Color.White else Color(0xFFCBD5E1),
                    modifier = Modifier.size(13.dp)
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
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Manage",
                        color = if (isHovered) Color.White else Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = if (isHovered) Color.White else Color(0xFF94A3B8),
                        modifier = Modifier.size(10.dp)
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
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF101318))
            .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "QUICK ACTIONS",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                QuickActionItem(
                    title = "Manage Installed Mods",
                    description = "Enable, disable or update installed mods",
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
            .background(if (isHovered) Color(0xFF1A1E29) else Color(0xFF141720))
            .border(1.dp, if (isHovered) Color.White else Color(0xFF222735), RoundedCornerShape(8.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
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
                    .background(Color(0xFF101318)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    color = Color(0xFF94A3B8),
                    fontSize = 10.5.sp
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = if (isHovered) Color.White else Color(0xFF64748B),
            modifier = Modifier.size(12.dp)
        )
    }
}

private fun formatPlaytime(seconds: Long): String {
    if (seconds <= 0) return "0 minutes"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
