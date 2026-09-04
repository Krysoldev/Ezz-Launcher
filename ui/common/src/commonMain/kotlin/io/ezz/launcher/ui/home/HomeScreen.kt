package io.ezz.launcher.ui.home

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import io.ezz.launcher.core.model.runtime.ProcessState
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.HeroRuntimeActionDisplay
import io.ezz.launcher.ui.components.InstanceArtworkIcon
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

            // Update Notification (if available)
            if (updateCheckResult?.hasUpdate == true && updateCheckResult?.latestRelease != null) {
                val latest = updateCheckResult!!.latestRelease!!
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF101318))
                        .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "New Launcher Release: v${latest.version}",
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
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
                startedAt = selectedStartedAt,
                onSelectInstance = { viewModel.selectInstance(it) },
                onLaunch = { viewModel.launchInstance(selectedInstance) },
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

            // 3. Latest News & Announcements (if any)
            if (announcements.isNotEmpty()) {
                AnnouncementsSection(
                    announcements = announcements,
                    onOpenUrl = { url -> viewModel.platformBridge.openUrl(url) }
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
    startedAt: Long?,
    onSelectInstance: (Instance) -> Unit,
    onLaunch: () -> Unit,
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
            .background(Color(0xFF101318))
            .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(12.dp))
            .padding(horizontal = 22.dp, vertical = 20.dp)
    ) {
        if (instance != null) {
            val javaReq = io.ezz.launcher.core.minecraft.version.JavaCompatibility.getRequiredJavaMajorVersion(instance.minecraftVersion)
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Top Row: Title + Dropdown Switcher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = "CURRENT INSTANCE",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = instance.name,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
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
                                        onSelectInstance(inst)
                                        isDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Middle Row: Metadata Badges + Status Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TargetPillBadge(text = "Minecraft ${instance.minecraftVersion}")
                    TargetPillBadge(text = instance.loaderType.name)
                    TargetPillBadge(text = "Java $javaReq")
                    TargetPillBadge(text = "${(instance.maxMemoryMb / 1024).coerceAtLeast(1)} GB RAM")

                    Spacer(modifier = Modifier.width(4.dp))

                    val isRunning = processState is ProcessState.Running || startedAt != null
                    if (isRunning && startedAt != null) {
                        RuntimeDisplay(
                            startedAt = startedAt,
                            showPrefix = true,
                            prefixText = "RUNNING",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            dotColor = Color(0xFF10B981)
                        )
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

                // Bottom Row: High-Contrast Solid White Play Button + Secondary Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isRunning = processState is ProcessState.Running || startedAt != null
                    val isPreparing = processState is ProcessState.Preparing

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
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isRunning -> Color(0xFF10131A)
                                    isPreparing -> Color(0xFF261838)
                                    isHovered -> Color(0xFF7C3AED)
                                    else -> Color(0xFF8B5CF6)
                                }
                            )
                            .border(
                                1.dp,
                                when {
                                    isRunning -> Color(0xFF10B981)
                                    isPreparing -> Color(0xFF6D28D9)
                                    isHovered -> Color(0xFF9333EA)
                                    else -> Color(0xFF8B5CF6)
                                },
                                RoundedCornerShape(8.dp)
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                enabled = !isRunning && !isPreparing,
                                onClick = onLaunch
                            )
                            .padding(horizontal = 42.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPreparing) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "PREPARING...",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else if (isRunning && startedAt != null) {
                            HeroRuntimeActionDisplay(startedAt = startedAt)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "PLAY MINECRAFT",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp
                                )
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
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
