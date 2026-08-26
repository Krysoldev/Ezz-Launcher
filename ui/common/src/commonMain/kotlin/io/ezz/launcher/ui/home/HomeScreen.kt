package io.ezz.launcher.ui.home

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.account.AccountType
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.model.runtime.ProcessState
import io.ezz.launcher.ui.components.EzzBadge
import io.ezz.launcher.ui.components.EzzBadgeVariant
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
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
    val selectedInstance by viewModel.selectedInstance.collectAsState()
    val instances by viewModel.instanceRepository.instances.collectAsState()
    val installedMods by viewModel.installedMods.collectAsState()
    val selectedAccount by viewModel.accountRepository.selectedAccount.collectAsState()
    val processState by viewModel.processState.collectAsState()
    val announcements by viewModel.announcements.collectAsState()
    val updateCheckResult by viewModel.updateCheckResult.collectAsState()
    val isMaintenanceMode by viewModel.isMaintenanceMode.collectAsState()
    val maintenanceMessage by viewModel.maintenanceMessage.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF050505))
            .verticalScroll(rememberScrollState())
            .padding(22.dp)
    ) {
        // 1. Maintenance Notification (if active)
        if (isMaintenanceMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1F1414))
                    .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MAINTENANCE ACTIVE: $maintenanceMessage",
                        color = Color(0xFFFCA5A5),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // 2. Launcher Update Available Notification
        if (updateCheckResult?.hasUpdate == true && updateCheckResult?.latestRelease != null) {
            val latest = updateCheckResult!!.latestRelease!!
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF141414))
                    .border(1.dp, Color(0xFF383838), RoundedCornerShape(8.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "New Release Available: v${latest.version}",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (!latest.releaseNotes.isNullOrBlank()) {
                                Text(
                                    text = latest.releaseNotes!!,
                                    color = Color(0xFF888888),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    if (!latest.downloadUrl.isNullOrBlank()) {
                        EzzButton(
                            text = "Download",
                            onClick = { viewModel.platformBridge.openUrl(latest.downloadUrl!!) },
                            variant = EzzButtonVariant.PRIMARY,
                            size = EzzButtonSize.SMALL
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // 3. Top Hero Section & High-Contrast Launch Button
        HeroBannerSection(
            instance = selectedInstance,
            accountName = selectedAccount?.username ?: "Offline Player",
            accountType = selectedAccount?.type ?: AccountType.OFFLINE,
            processState = processState,
            modCount = installedMods.size,
            onLaunch = { viewModel.launchInstance(selectedInstance) },
            onConfigure = {
                selectedInstance?.let { viewModel.showEditInstanceDialog.value = it }
            },
            onOpenFolder = {
                selectedInstance?.let { viewModel.openInstanceFolder(it.id) }
            },
            onCreateInstance = { viewModel.showCreateInstanceDialog.value = true }
        )

        Spacer(modifier = Modifier.height(22.dp))

        // 4. Main Body: My Instances (68% width) + Quick Info & Announcements (32% width)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Left Column: My Instances
            Column(modifier = Modifier.weight(0.68f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "My Instances",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        EzzBadge(
                            text = "${instances.size}",
                            variant = EzzBadgeVariant.NEUTRAL
                        )
                    }

                    EzzButton(
                        text = "New Instance",
                        icon = Icons.Default.Add,
                        onClick = { viewModel.showCreateInstanceDialog.value = true },
                        variant = EzzButtonVariant.SECONDARY,
                        size = EzzButtonSize.SMALL
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (instances.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF101010))
                            .border(1.dp, Color(0xFF242424), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        EzzEmptyState(
                            title = "No Minecraft Instances Yet",
                            description = "Create your first instance to start playing Vanilla, Fabric, or OptiFine.",
                            actionLabel = "Create Instance",
                            onAction = { viewModel.showCreateInstanceDialog.value = true }
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        instances.forEach { inst ->
                            val isSelected = inst.id == selectedInstance?.id
                            InstanceCompactCard(
                                instance = inst,
                                isSelected = isSelected,
                                isRunning = processState is ProcessState.Running && isSelected,
                                onSelect = { viewModel.selectInstance(inst) },
                                onPlay = {
                                    viewModel.selectInstance(inst)
                                    viewModel.launchInstance(inst)
                                },
                                onEdit = { viewModel.showEditInstanceDialog.value = inst },
                                onOpenFolder = { viewModel.openInstanceFolder(inst.id) }
                            )
                        }
                    }
                }
            }

            // Right Column: Quick Stats & Announcements Feed
            Column(
                modifier = Modifier.weight(0.32f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Quick Info Panel
                QuickInfoPanel(
                    instance = selectedInstance,
                    accountName = selectedAccount?.username ?: "Offline Player",
                    onViewSettings = { viewModel.navigateTo(NavigationScreen.SETTINGS) }
                )

                // Announcements Feed
                AnnouncementsPanel(
                    announcements = announcements,
                    onOpenAnnouncements = { viewModel.platformBridge.openUrl("https://github.com/Krysoldev/Ezz-Launcher") }
                )
            }
        }
    }
}

@Composable
private fun HeroBannerSection(
    instance: Instance?,
    accountName: String,
    accountType: AccountType,
    processState: ProcessState,
    modCount: Int,
    onLaunch: () -> Unit,
    onConfigure: () -> Unit,
    onOpenFolder: () -> Unit,
    onCreateInstance: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF161616),
                        Color(0xFF0F0F0F),
                        Color(0xFF0A0A0A)
                    )
                )
            )
            .border(1.dp, Color(0xFF282828), RoundedCornerShape(8.dp))
            .padding(26.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Hero Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "READY TO PLAY • $accountName",
                        color = Color(0xFFA0A0A0),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = instance?.name ?: "No Instance Selected",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (instance != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EzzLoaderBadge(loaderType = instance.loaderType)
                        EzzBadge(
                            text = "Minecraft ${instance.minecraftVersion}",
                            variant = EzzBadgeVariant.NEUTRAL
                        )
                        EzzBadge(
                            text = "${instance.maxMemoryMb / 1024} GB RAM",
                            variant = EzzBadgeVariant.NEUTRAL
                        )
                        if (instance.loaderType == LoaderType.FABRIC && modCount > 0) {
                            EzzBadge(
                                text = "$modCount Mods",
                                variant = EzzBadgeVariant.PRIMARY
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Create a Minecraft Java Edition instance to launch",
                        color = Color(0xFF777777),
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(20.dp))

            // Right Hero Actions: Prominent High-Contrast White PLAY Button
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                if (instance != null) {
                    val isRunning = processState is ProcessState.Running
                    val isPreparing = processState is ProcessState.Preparing
                    val isFailed = processState is ProcessState.Failed

                    val interactionSource = remember { MutableInteractionSource() }
                    val isHovered by interactionSource.collectIsHoveredAsState()
                    val isPressed by interactionSource.collectIsPressedAsState()

                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.97f else if (isHovered && !isRunning && !isPreparing) 1.02f else 1.0f,
                        animationSpec = tween(120)
                    )

                    Box(
                        modifier = Modifier
                            .scale(scale)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    isRunning -> Color(0xFF1E1E1E)
                                    isPreparing -> Color(0xFF282828)
                                    isHovered -> Color(0xFFE5E5E5)
                                    else -> Color(0xFFFFFFFF)
                                }
                            )
                            .border(
                                1.dp,
                                if (isRunning) Color(0xFF383838) else Color.Transparent,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                enabled = !isRunning && !isPreparing,
                                onClick = onLaunch
                            )
                            .padding(horizontal = 32.dp, vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isPreparing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "PREPARING...",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp
                                )
                            } else if (isRunning) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "PLAYING NOW",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color(0xFF050505),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "PLAY",
                                    color = Color(0xFF050505),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EzzButton(
                            text = "Configure",
                            icon = Icons.Default.Tune,
                            onClick = onConfigure,
                            variant = EzzButtonVariant.SECONDARY,
                            size = EzzButtonSize.SMALL
                        )
                        EzzButton(
                            text = "Folder",
                            icon = Icons.Default.FolderOpen,
                            onClick = onOpenFolder,
                            variant = EzzButtonVariant.SECONDARY,
                            size = EzzButtonSize.SMALL
                        )
                    }
                } else {
                    EzzButton(
                        text = "Create Instance",
                        icon = Icons.Default.Add,
                        onClick = onCreateInstance,
                        variant = EzzButtonVariant.PRIMARY,
                        size = EzzButtonSize.LARGE
                    )
                }
            }
        }
    }
}

@Composable
private fun InstanceCompactCard(
    instance: Instance,
    isSelected: Boolean,
    isRunning: Boolean,
    onSelect: () -> Unit,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onOpenFolder: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.01f else 1.0f,
        animationSpec = tween(120)
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) Color(0xFF181818) else Color(0xFF101010))
            .border(
                1.dp,
                if (isSelected) Color.White else if (isHovered) Color(0xFF383838) else Color(0xFF202020),
                RoundedCornerShape(6.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon & Details
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) Color(0xFF222222) else Color(0xFF161616)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else Color(0xFF888888),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = instance.name,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Minecraft ${instance.minecraftVersion}",
                            color = Color(0xFFA0A0A0),
                            fontSize = 11.sp
                        )
                        Text(text = "•", color = Color(0xFF555555), fontSize = 11.sp)
                        Text(
                            text = instance.loaderType.name,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(text = "•", color = Color(0xFF555555), fontSize = 11.sp)
                        Text(
                            text = "${instance.maxMemoryMb / 1024} GB",
                            color = Color(0xFF777777),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EzzIconButton(
                    icon = Icons.Default.FolderOpen,
                    onClick = onOpenFolder,
                    size = EzzButtonSize.SMALL,
                    variant = EzzButtonVariant.GHOST
                )
                EzzIconButton(
                    icon = Icons.Default.Tune,
                    onClick = onEdit,
                    size = EzzButtonSize.SMALL,
                    variant = EzzButtonVariant.GHOST
                )
                EzzButton(
                    text = if (isRunning) "Running" else "Play",
                    icon = Icons.Default.PlayArrow,
                    onClick = onPlay,
                    variant = if (isSelected) EzzButtonVariant.PRIMARY else EzzButtonVariant.SECONDARY,
                    size = EzzButtonSize.SMALL,
                    enabled = !isRunning
                )
            }
        }
    }
}

@Composable
private fun QuickInfoPanel(
    instance: Instance?,
    accountName: String,
    onViewSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF101010))
            .border(1.dp, Color(0xFF242424), RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SYSTEM & GAME SPECS",
                    color = Color(0xFF888888),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = Color(0xFF666666),
                    modifier = Modifier
                        .size(15.dp)
                        .clickable { onViewSettings() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SpecRow(label = "Active Player", value = accountName)
            SpecRow(label = "Java Runtime", value = instance?.javaPath?.substringAfterLast("\\") ?: "System Auto")
            SpecRow(label = "RAM Allocation", value = "${(instance?.maxMemoryMb ?: 4096) / 1024} GB")
            SpecRow(label = "Resolution", value = "${instance?.windowWidth ?: 1280}x${instance?.windowHeight ?: 720}")
            SpecRow(label = "Platform", value = "Windows x64")
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color(0xFF888888), fontSize = 12.sp)
        Text(text = value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AnnouncementsPanel(
    announcements: List<io.ezz.launcher.core.storage.supabase.SupabaseAnnouncementDto>,
    onOpenAnnouncements: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF101010))
            .border(1.dp, Color(0xFF242424), RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NETWORK BROADCASTS",
                    color = Color(0xFF888888),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "GitHub",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onOpenAnnouncements() }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (announcements.isEmpty()) {
                Text(
                    text = "All Ezz authentication and cloud services are fully operational.",
                    color = Color(0xFF777777),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            } else {
                announcements.take(3).forEach { ann ->
                    Column(modifier = Modifier.padding(vertical = 5.dp)) {
                        Text(
                            text = ann.title,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = ann.message,
                            color = Color(0xFFA0A0A0),
                            fontSize = 11.sp,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}
