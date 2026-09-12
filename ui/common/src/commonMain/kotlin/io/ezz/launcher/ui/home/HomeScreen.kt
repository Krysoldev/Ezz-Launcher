package io.ezz.launcher.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import io.ezz.launcher.ui.components.EzzBadge
import io.ezz.launcher.ui.components.EzzBadgeVariant
import io.ezz.launcher.ui.components.EzzCard
import io.ezz.launcher.ui.components.EzzCardVariant
import io.ezz.launcher.ui.components.EzzModal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.account.Account
import io.ezz.launcher.core.model.account.AccountType
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.model.runtime.LaunchProgressState
import io.ezz.launcher.core.model.runtime.ProcessState
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.HeroRuntimeActionDisplay
import io.ezz.launcher.ui.components.InstanceArtworkIcon
import io.ezz.launcher.ui.components.LaunchProgressTrack
import io.ezz.launcher.ui.components.MinecraftSkinHead
import io.ezz.launcher.ui.components.RuntimeDisplay
import io.ezz.launcher.ui.viewmodel.AppViewModel
import io.ezz.launcher.ui.viewmodel.NavigationScreen

@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val selectedInstance by viewModel.selectedInstance.collectAsState()
    val instances by viewModel.instanceRepository.instances.collectAsState()
    val selectedAccount by viewModel.accountRepository.selectedAccount.collectAsState()
    val processState by viewModel.processState.collectAsState()
    val updateCheckResult by viewModel.updateCheckResult.collectAsState()
    val isMaintenanceMode by viewModel.isMaintenanceMode.collectAsState()
    val maintenanceMessage by viewModel.maintenanceMessage.collectAsState()
    val runningSessions by viewModel.runningSessions.collectAsState()
    val announcements by viewModel.announcements.collectAsState()

    val selectedStartedAt = selectedInstance?.let { runningSessions[it.id]?.startedAt }
    val launchProgress by viewModel.launchProgressState.collectAsState()

    val updateDownloadProgress by viewModel.updateDownloadProgress.collectAsState()
    val updateDownloadStatus by viewModel.updateDownloadStatus.collectAsState()
    val isApplyingUpdate by viewModel.isApplyingUpdate.collectAsState()

    var showUpdateDetailsModal by remember { mutableStateOf(false) }
    var isUpdateDismissedForSession by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07080A)),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 1200.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Maintenance Notification (if active)
            if (isMaintenanceMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1C1012))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
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
            }

            // User Update Notification Card (if update available)
            if (updateCheckResult?.hasUpdate == true && updateCheckResult?.latestRelease != null && !isUpdateDismissedForSession) {
                val latest = updateCheckResult!!.latestRelease!!
                EzzCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = EzzCardVariant.OUTLINED,
                    borderColor = Color(0xFF2E3D52),
                    backgroundColor = Color(0xFF0C1017)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF38BDF8))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Ezz Launcher v${latest.version} Available",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                EzzBadge(
                                    text = "v${viewModel.currentLauncherVersion} → v${latest.version}",
                                    variant = EzzBadgeVariant.PRIMARY
                                )
                                if (latest.isRequired) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    EzzBadge(text = "REQUIRED", variant = EzzBadgeVariant.WARNING)
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                EzzButton(
                                    text = "View Changes",
                                    variant = EzzButtonVariant.GHOST,
                                    size = EzzButtonSize.SMALL,
                                    onClick = { showUpdateDetailsModal = true }
                                )

                                EzzButton(
                                    text = if (isApplyingUpdate) "Updating..." else "Update Now",
                                    icon = Icons.Default.Download,
                                    variant = EzzButtonVariant.PRIMARY,
                                    size = EzzButtonSize.SMALL,
                                    isLoading = isApplyingUpdate,
                                    onClick = { viewModel.downloadAndApplyUpdate(latest) }
                                )

                                if (!latest.isRequired) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(16.dp).clickable { isUpdateDismissedForSession = true }
                                    )
                                }
                            }
                        }

                        if (isApplyingUpdate || updateDownloadStatus != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = updateDownloadStatus ?: "Preparing update installer...",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.5.sp
                                )
                                if (updateDownloadProgress != null) {
                                    LinearProgressIndicator(
                                        progress = { updateDownloadProgress!! },
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                        color = Color(0xFF38BDF8),
                                        trackColor = Color(0xFF1E2638)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 1. User Welcome Header Card
            UserWelcomeCard(
                account = selectedAccount,
                skinManager = viewModel.skinService,
                onOpenAccounts = { viewModel.navigateTo(NavigationScreen.ACCOUNTS) }
            )

            // 2. Active Launch Target Hero Card
            ActiveLaunchTargetCard(
                instance = selectedInstance,
                instances = instances,
                processState = processState,
                launchProgress = launchProgress,
                startedAt = selectedStartedAt,
                onSelectInstance = { viewModel.selectInstance(it) },
                onLaunch = {
                    io.ezz.launcher.ui.audio.EzzAudioService.playLaunch()
                    viewModel.launchInstance(selectedInstance)
                },
                onCancelLaunch = { viewModel.cancelLaunch() },
                onManage = {
                    selectedInstance?.let { viewModel.openInstanceManager(it) }
                        ?: run { viewModel.navigateTo(NavigationScreen.INSTANCES) }
                },
                onConfigure = {
                    selectedInstance?.let { viewModel.showEditInstanceDialog.value = it }
                },
                onOpenFolder = {
                    selectedInstance?.let { viewModel.openInstanceFolder(it.id) }
                },
                onCreateInstance = { viewModel.showCreateInstanceDialog.value = true }
            )

            // 3. Quick Action Navigation Tiles
            HomeQuickActionsRow(
                instanceCount = instances.size,
                activeAccountName = selectedAccount?.username ?: "Guest Player",
                onNavigate = { screen ->
                    io.ezz.launcher.ui.audio.EzzAudioService.playSelect()
                    viewModel.navigateTo(screen)
                }
            )

            // 4. Latest News & Announcements (if any)
            if (announcements.isNotEmpty()) {
                AnnouncementsSection(
                    announcements = announcements,
                    onOpenUrl = { url -> viewModel.platformBridge.openUrl(url) }
                )
            }
        }

        // Modal: Update Details & What's New
        if (showUpdateDetailsModal && updateCheckResult?.latestRelease != null) {
            val latest = updateCheckResult!!.latestRelease!!
            EzzModal(
                onDismiss = { showUpdateDetailsModal = false },
                title = "EZZ LAUNCHER UPDATE — v${latest.version}"
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = latest.title ?: "Ezz Launcher ${latest.version}",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Release Date: ${latest.publishedAt?.take(10) ?: "Latest"} • Windows Setup",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.5.sp
                            )
                        }

                        EzzBadge(
                            text = "Current: v${viewModel.currentLauncherVersion}",
                            variant = EzzBadgeVariant.NEUTRAL
                        )
                    }

                    // What's New / Release Notes
                    EzzCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = EzzCardVariant.SURFACE,
                        borderColor = Color(0xFF1E2638)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "WHAT'S NEW",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = latest.releaseNotes ?: "Bug fixes and performance improvements.",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Safety / In-place update notice
                    EzzCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = EzzCardVariant.OUTLINED,
                        borderColor = Color(0xFF1B382B),
                        backgroundColor = Color(0xFF0D1C16)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Your Microsoft accounts, offline profiles, instances, mods, worlds, and settings will be completely preserved. The installer will update Ezz Launcher in-place.",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    if (isApplyingUpdate || updateDownloadStatus != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = updateDownloadStatus ?: "Preparing update...",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.5.sp
                            )
                            if (updateDownloadProgress != null) {
                                LinearProgressIndicator(
                                    progress = { updateDownloadProgress!! },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = Color(0xFF38BDF8),
                                    trackColor = Color(0xFF1E2638)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EzzButton(
                            text = "Later",
                            variant = EzzButtonVariant.GHOST,
                            enabled = !isApplyingUpdate,
                            onClick = { showUpdateDetailsModal = false }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        EzzButton(
                            text = if (isApplyingUpdate) "Updating..." else "Update & Restart",
                            icon = Icons.Default.Download,
                            variant = EzzButtonVariant.PRIMARY,
                            isLoading = isApplyingUpdate,
                            onClick = { viewModel.downloadAndApplyUpdate(latest) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 3. Home Quick Actions Row
 */
@Composable
private fun HomeQuickActionsRow(
    instanceCount: Int,
    activeAccountName: String,
    onNavigate: (NavigationScreen) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HomeQuickActionCard(
            title = "Instances",
            subtitle = if (instanceCount == 1) "1 active installation" else "$instanceCount installations",
            icon = Icons.Default.GridView,
            modifier = Modifier.weight(1f),
            onClick = { onNavigate(NavigationScreen.INSTANCES) }
        )
        HomeQuickActionCard(
            title = "Vault Studio",
            subtitle = "3D preview & skin collection",
            icon = Icons.Default.Person,
            modifier = Modifier.weight(1f),
            onClick = { onNavigate(NavigationScreen.VAULT) }
        )
        HomeQuickActionCard(
            title = "Accounts",
            subtitle = activeAccountName,
            icon = Icons.Default.Person,
            modifier = Modifier.weight(1f),
            onClick = { onNavigate(NavigationScreen.ACCOUNTS) }
        )
    }
}

@Composable
private fun HomeQuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else if (isHovered) 1.015f else 1.0f,
        animationSpec = tween(120)
    )

    androidx.compose.runtime.LaunchedEffect(isHovered) {
        if (isHovered) {
            io.ezz.launcher.ui.audio.EzzAudioService.playHover()
        }
    }

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isHovered) Color(0xFF141824) else Color(0xFF10131A))
            .border(
                1.dp,
                if (isHovered) Color(0xFF8B5CF6).copy(alpha = 0.65f) else Color(0xFF1B1F2C),
                RoundedCornerShape(10.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isHovered) Color(0x338B5CF6) else Color(0x1F8B5CF6)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isHovered) Color.White else Color(0xFFA78BFA),
                    modifier = Modifier.size(17.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 1. User Welcome Header Card
 */
@Composable
private fun UserWelcomeCard(
    account: Account?,
    skinManager: io.ezz.launcher.core.minecraft.skin.MinecraftSkinManager,
    onOpenAccounts: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF10131A))
            .border(1.dp, Color(0xFF1B1F2C), RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Player Head + Welcome text + Account Type & UUID
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                MinecraftSkinHead(
                    account = account,
                    skinManager = skinManager,
                    size = 46.dp
                )

                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = "WELCOME BACK",
                        color = Color(0xFFA78BFA),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = account?.username ?: "Guest Player",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isMicrosoft = account?.type == AccountType.MICROSOFT
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isMicrosoft) Color(0x268B5CF6) else Color(0xFF161A24))
                                .border(1.dp, if (isMicrosoft) Color(0xFF6D28D9) else Color(0xFF1B1F2C), RoundedCornerShape(4.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isMicrosoft) "Microsoft Account" else "Offline Account",
                                color = if (isMicrosoft) Color(0xFFDDD6FE) else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val uuidText = account?.uuid?.let { "UUID: ${it.take(18)}..." } ?: "UUID: offline-local-session"
                        Text(
                            text = uuidText,
                            color = Color(0xFF64748B),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Right: Accounts Shortcut Button
            EzzButton(
                text = "Accounts",
                onClick = onOpenAccounts,
                variant = EzzButtonVariant.SECONDARY,
                size = EzzButtonSize.SMALL,
                icon = Icons.Default.Person
            )
        }
    }
}

/**
 * 2. Active Launch Target Hero Card
 */
@Composable
private fun ActiveLaunchTargetCard(
    instance: Instance?,
    instances: List<Instance>,
    processState: ProcessState,
    launchProgress: LaunchProgressState?,
    startedAt: Long?,
    onSelectInstance: (Instance) -> Unit,
    onLaunch: () -> Unit,
    onCancelLaunch: () -> Unit,
    onManage: () -> Unit,
    onConfigure: () -> Unit,
    onOpenFolder: () -> Unit,
    onCreateInstance: () -> Unit
) {
    var isDropdownOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF10131A))
            .border(1.dp, Color(0xFF1B1F2C), RoundedCornerShape(12.dp))
            .padding(horizontal = 22.dp, vertical = 20.dp)
    ) {
        if (instance != null) {
            val javaReq = io.ezz.launcher.core.minecraft.version.JavaCompatibility.getRequiredJavaMajorVersion(instance.minecraftVersion)
            val isRunning = processState is ProcessState.Running || startedAt != null
            val isPreparing = processState is ProcessState.Preparing || (launchProgress != null && launchProgress.percentage < 100)
            val isLaunching = launchProgress != null || isPreparing

            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                // Top Row: Large Instance Artwork + Title & Metadata + Switcher Dropdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        // Large Artwork Icon with hover micro-zoom
                        val artInteraction = remember { MutableInteractionSource() }
                        val isArtHovered by artInteraction.collectIsHoveredAsState()
                        val artScale by animateFloatAsState(
                            targetValue = if (isArtHovered) 1.025f else 1.0f,
                            animationSpec = tween(160)
                        )

                        Box(
                            modifier = Modifier
                                .scale(artScale)
                                .clickable(
                                    interactionSource = artInteraction,
                                    indication = null,
                                    onClick = onManage
                                )
                        ) {
                            InstanceArtworkIcon(
                                instance = instance,
                                size = 74.dp
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "ACTIVE INSTANCE",
                                color = Color(0xFFA78BFA),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = instance.name,
                                color = Color.White,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Black
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Minecraft ${instance.minecraftVersion} • ${instance.loaderType.name}",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                val lastPlayed = instance.lastPlayedAt
                                val lastPlayedStr = if (lastPlayed != null && lastPlayed > 0) {
                                    val diffMs = System.currentTimeMillis() - lastPlayed
                                    val hours = diffMs / (1000 * 60 * 60)
                                    if (hours < 1) "Played recently" else if (hours < 24) "Last played ${hours}h ago" else "Last played ${hours / 24}d ago"
                                } else {
                                    "Never played yet"
                                }

                                Text(
                                    text = "•  $lastPlayedStr",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Instance Switcher Dropdown
                    Box {
                        val interactionSource = remember { MutableInteractionSource() }
                        val isHovered by interactionSource.collectIsHoveredAsState()

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isHovered) Color(0xFF181C28) else Color(0xFF141720))
                                .border(1.dp, if (isHovered) Color(0xFF323A4E) else Color(0xFF222735), RoundedCornerShape(8.dp))
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = { isDropdownOpen = true }
                                )
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${instance.name} (${instance.minecraftVersion})",
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Switch",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isDropdownOpen,
                            onDismissRequest = { isDropdownOpen = false },
                            modifier = Modifier
                                .background(Color(0xFF141720))
                                .border(1.dp, Color(0xFF222735), RoundedCornerShape(6.dp))
                        ) {
                            instances.forEach { inst ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = inst.name,
                                                color = if (inst.id == instance.id) Color.White else Color(0xFFCBD5E1),
                                                fontSize = 13.sp,
                                                fontWeight = if (inst.id == instance.id) FontWeight.Bold else FontWeight.Normal
                                            )
                                            Text(
                                                text = "(${inst.minecraftVersion} · ${inst.loaderType.name})",
                                                color = Color(0xFF64748B),
                                                fontSize = 11.5.sp
                                            )
                                        }
                                    },
                                    onClick = {
                                        io.ezz.launcher.ui.audio.EzzAudioService.playSelect()
                                        onSelectInstance(inst)
                                        isDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Middle Row: Technical Spec Badges + Live Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TargetPillBadge(text = "Java $javaReq")
                        TargetPillBadge(text = "${(instance.maxMemoryMb / 1024).coerceAtLeast(1)} GB RAM")
                        TargetPillBadge(text = instance.loaderType.name)
                    }

                    if (isRunning && startedAt != null) {
                        RuntimeDisplay(
                            startedAt = startedAt,
                            showPrefix = true,
                            prefixText = "MINECRAFT RUNNING",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            dotColor = Color(0xFF10B981)
                        )
                    } else if (launchProgress != null) {
                        val isErr = launchProgress.error != null
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!isErr) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = Color(0xFFA78BFA),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = launchProgress.stage.uppercase(),
                                color = if (isErr) Color(0xFFEF4444) else Color(0xFFA78BFA),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (isPreparing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = Color(0xFFA78BFA),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "LAUNCHING...",
                                color = Color(0xFFA78BFA),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Ready to Play",
                                color = Color(0xFF10B981),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Minecraft Steve Runner Real-Time Launch Progress Track
                AnimatedVisibility(
                    visible = launchProgress != null && (launchProgress.instanceId.isBlank() || launchProgress.instanceId == instance.id),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    launchProgress?.let { progress ->
                        LaunchProgressTrack(
                            progressState = progress,
                            onCancel = onCancelLaunch,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                    }
                }

                // Bottom Row: The Next-Gen Tactile Play Button + Secondary Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val interactionSource = remember { MutableInteractionSource() }
                    val isHovered by interactionSource.collectIsHoveredAsState()
                    val isPressed by interactionSource.collectIsPressedAsState()

                    androidx.compose.runtime.LaunchedEffect(isHovered) {
                        if (isHovered && !isRunning && !isLaunching) {
                            io.ezz.launcher.ui.audio.EzzAudioService.playHover()
                        }
                    }

                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.97f else if (isHovered && !isRunning && !isLaunching) 1.025f else 1.0f,
                        animationSpec = tween(120)
                    )

                    Box(
                        modifier = Modifier
                            .scale(scale)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                when {
                                    isRunning -> Color(0xFF131122)
                                    isLaunching -> Color(0xFF261838)
                                    isHovered -> Color(0xFF7C3AED)
                                    else -> Color(0xFF8B5CF6)
                                }
                            )
                            .border(
                                1.dp,
                                when {
                                    isRunning -> Color(0xFF10B981)
                                    isLaunching -> Color(0xFF6D28D9)
                                    isHovered -> Color(0xFFA78BFA)
                                    else -> Color(0xFF8B5CF6)
                                },
                                RoundedCornerShape(9.dp)
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                enabled = !isRunning && !isLaunching,
                                onClick = onLaunch
                            )
                            .padding(horizontal = 46.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            launchProgress != null -> {
                                val prepText = if (launchProgress.percentage >= 100 || launchProgress.stage.contains("START", ignoreCase = true)) {
                                    "STARTING MINECRAFT..."
                                } else {
                                    "LAUNCHING..."
                                }
                                Text(
                                    text = prepText,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp
                                )
                            }
                            isPreparing -> {
                                val prepStage = (processState as? ProcessState.Preparing)?.stage
                                val prepText = if (prepStage?.contains("starting", ignoreCase = true) == true) {
                                    "STARTING MINECRAFT..."
                                } else {
                                    "LAUNCHING..."
                                }
                                Text(
                                    text = prepText,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp
                                )
                            }
                            isRunning && startedAt != null -> {
                                HeroRuntimeActionDisplay(startedAt = startedAt)
                            }
                            else -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "PLAY",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.8.sp
                                    )
                                }
                            }
                        }
                    }

                    // Secondary Actions: Manage, Configure, Open Folder
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EzzButton(
                            text = "Manage",
                            onClick = onManage,
                            variant = EzzButtonVariant.SECONDARY,
                            size = EzzButtonSize.MEDIUM,
                            icon = Icons.Default.GridView
                        )
                        EzzButton(
                            text = "Configure",
                            onClick = onConfigure,
                            variant = EzzButtonVariant.SECONDARY,
                            size = EzzButtonSize.MEDIUM,
                            icon = Icons.Default.Edit
                        )
                        EzzButton(
                            text = "Open Folder",
                            onClick = onOpenFolder,
                            variant = EzzButtonVariant.SECONDARY,
                            size = EzzButtonSize.MEDIUM,
                            icon = Icons.Default.FolderOpen
                        )
                    }
                }
            }
        } else {
            // Empty State
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "No Minecraft instance selected",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Create or select an instance to configure your launch target.",
                    color = Color(0xFF64748B),
                    fontSize = 13.sp
                )
                EzzButton(
                    text = "Create Instance",
                    onClick = onCreateInstance,
                    icon = Icons.Default.Add,
                    variant = EzzButtonVariant.PRIMARY,
                    size = EzzButtonSize.MEDIUM
                )
            }
        }
    }
}

@Composable
private fun TargetPillBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF141720))
            .border(1.dp, Color(0xFF222735), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = Color(0xFFCBD5E1),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * 3. Announcements Section
 */
@Composable
private fun AnnouncementsSection(
    announcements: List<io.ezz.launcher.core.storage.supabase.SupabaseAnnouncementDto>,
    onOpenUrl: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "LATEST ANNOUNCEMENTS",
            color = Color(0xFF64748B),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )

        announcements.take(2).forEach { item ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF101318))
                    .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(8.dp))
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
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = item.title,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (item.message.isNotBlank()) {
                                Text(
                                    text = item.message,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
