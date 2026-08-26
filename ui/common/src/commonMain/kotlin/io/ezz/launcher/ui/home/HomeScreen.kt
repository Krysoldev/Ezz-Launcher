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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
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
    val selectedAccount by viewModel.accountRepository.selectedAccount.collectAsState()
    val processState by viewModel.processState.collectAsState()
    val announcements by viewModel.announcements.collectAsState()
    val updateCheckResult by viewModel.updateCheckResult.collectAsState()
    val isMaintenanceMode by viewModel.isMaintenanceMode.collectAsState()
    val maintenanceMessage by viewModel.maintenanceMessage.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        // 1. Maintenance Alert (if active)
        if (isMaintenanceMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.danger.copy(alpha = 0.15f))
                    .border(1.dp, colors.danger, RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "⚠️ MAINTENANCE ACTIVE: $maintenanceMessage",
                    color = colors.danger,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 2. Launcher Update Alert (if available)
        if (updateCheckResult?.hasUpdate == true && updateCheckResult?.latestRelease != null) {
            val latest = updateCheckResult!!.latestRelease!!
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.primary.copy(alpha = 0.12f))
                    .border(1.dp, colors.primary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "🚀 New Update Available: v${latest.version}",
                            color = colors.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (!latest.releaseNotes.isNullOrBlank()) {
                            Text(
                                text = latest.releaseNotes!!,
                                color = colors.textSecondary,
                                fontSize = 11.sp
                            )
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
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 3. Main Hero Banner & Launch Control Section
        HeroBannerSection(
            instance = selectedInstance,
            accountName = selectedAccount?.username ?: "Player",
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

        Spacer(modifier = Modifier.height(24.dp))

        // 4. Main Body: Instances Grid on Left + Quick Info / News on Right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Left Column: My Instances (68% width)
            Column(modifier = Modifier.weight(0.68f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "My Instances",
                            color = colors.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        EzzBadge(
                            text = "${instances.size}",
                            variant = EzzBadgeVariant.NEUTRAL
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EzzButton(
                            text = "Create Instance",
                            icon = Icons.Default.Add,
                            onClick = { viewModel.showCreateInstanceDialog.value = true },
                            variant = EzzButtonVariant.PRIMARY,
                            size = EzzButtonSize.SMALL
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (instances.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.cardBackground)
                            .border(1.dp, colors.border, RoundedCornerShape(10.dp)),
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
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

            // Right Column: Quick Info & Latest Announcements (32% width)
            Column(
                modifier = Modifier.weight(0.32f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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
    val colors = EzzTheme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF14171A),
                        Color(0xFF0D0F11),
                        Color(0xFF070809)
                    )
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        colors.primary.copy(alpha = 0.6f),
                        colors.border
                    )
                ),
                RoundedCornerShape(12.dp)
            )
            .padding(28.dp)
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
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(colors.accent)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "READY FOR BATTLE • $accountName",
                        color = colors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = instance?.name ?: "No Instance Selected",
                    color = colors.textPrimary,
                    fontSize = 28.sp,
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
                                variant = EzzBadgeVariant.INFO
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Create a Minecraft Java Edition instance to launch",
                        color = colors.textSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(24.dp))

            // Right Hero Actions & Main Play Button
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                if (instance != null) {
                    // Massive Red Play Button
                    val isRunning = processState is ProcessState.Running
                    val isPreparing = processState is ProcessState.Preparing

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    isRunning -> colors.accent
                                    isPreparing -> colors.warning
                                    else -> colors.primary
                                }
                            )
                            .clickable(
                                enabled = !isRunning && !isPreparing,
                                onClick = onLaunch
                            )
                            .padding(horizontal = 36.dp, vertical = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isPreparing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "PREPARING...",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                            } else if (isRunning) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "PLAYING NOW",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "PLAY MINECRAFT",
                                    color = Color.White,
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
                            text = "Settings",
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
    val colors = EzzTheme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) colors.cardBackground else colors.surface)
            .border(
                1.dp,
                if (isSelected) colors.primary else colors.border,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onSelect)
            .padding(14.dp)
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
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) colors.primary.copy(alpha = 0.15f) else colors.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = if (isSelected) colors.primary else colors.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = instance.name,
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(colors.primary)
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
                            color = colors.textSecondary,
                            fontSize = 11.sp
                        )
                        Text(text = "•", color = colors.textMuted, fontSize = 11.sp)
                        Text(
                            text = instance.loaderType.name,
                            color = colors.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(text = "•", color = colors.textMuted, fontSize = 11.sp)
                        Text(
                            text = "${instance.maxMemoryMb / 1024} GB RAM",
                            color = colors.textMuted,
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
    val colors = EzzTheme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.cardBackground)
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "QUICK SPECS",
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onViewSettings() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SpecRow(label = "Active Account", value = accountName)
            SpecRow(label = "Java Runtime", value = instance?.javaPath?.substringAfterLast("\\") ?: "System Auto")
            SpecRow(label = "RAM Allocation", value = "${(instance?.maxMemoryMb ?: 4096) / 1024} GB")
            SpecRow(label = "Resolution", value = "${instance?.windowWidth ?: 1280}x${instance?.windowHeight ?: 720}")
            SpecRow(label = "Launcher Version", value = "v1.0.0 (Latest)")
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    val colors = EzzTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = colors.textSecondary, fontSize = 12.sp)
        Text(text = value, color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AnnouncementsPanel(
    announcements: List<io.ezz.launcher.core.storage.supabase.SupabaseAnnouncementDto>,
    onOpenAnnouncements: () -> Unit
) {
    val colors = EzzTheme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.cardBackground)
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ANNOUNCEMENTS",
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "GitHub",
                    color = colors.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onOpenAnnouncements() }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (announcements.isEmpty()) {
                Text(
                    text = "No active broadcasts. All Ezz services and authentication servers are operating normally.",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            } else {
                announcements.take(3).forEach { ann ->
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(
                            text = ann.title,
                            color = colors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = ann.message,
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}
