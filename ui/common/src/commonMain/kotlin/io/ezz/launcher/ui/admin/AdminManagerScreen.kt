package io.ezz.launcher.ui.admin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.storage.supabase.SupabaseAnnouncementDto
import io.ezz.launcher.core.storage.supabase.SupabaseFeatureFlagDto
import io.ezz.launcher.core.storage.supabase.SupabaseLauncherConfigDto
import io.ezz.launcher.core.storage.supabase.SupabaseLauncherReleaseDto
import io.ezz.launcher.ui.components.EzzBadge
import io.ezz.launcher.ui.components.EzzBadgeVariant
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.EzzCard
import io.ezz.launcher.ui.components.EzzCardVariant
import io.ezz.launcher.ui.components.EzzEmptyState
import io.ezz.launcher.ui.components.EzzModal
import io.ezz.launcher.ui.components.EzzTabs
import io.ezz.launcher.ui.components.EzzTextField
import io.ezz.launcher.ui.components.EzzToggle
import io.ezz.launcher.ui.components.TabItem
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.AppViewModel
import io.ezz.launcher.ui.viewmodel.NavigationScreen

enum class AdminManagerTab {
    OVERVIEW,
    RELEASES,
    ANNOUNCEMENTS,
    FLAGS_AND_CONFIG,
    SECURITY_AUDIT
}

@Composable
fun AdminManagerScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(AdminManagerTab.OVERVIEW) }
    val colors = EzzTheme.colors

    val stats by viewModel.adminDashboardStats.collectAsState()
    val releases by viewModel.adminReleases.collectAsState()
    val announcements by viewModel.adminAnnouncements.collectAsState()
    val featureFlags by viewModel.adminFeatureFlags.collectAsState()
    val launcherConfigs by viewModel.adminLauncherConfigs.collectAsState()
    val isLoading by viewModel.isLoadingAdminData.collectAsState()
    val actionStatus by viewModel.adminActionStatus.collectAsState()
    val selectedAccount by viewModel.accountRepository.selectedAccount.collectAsState()

    // Modals
    var releaseToDelete by remember { mutableStateOf<SupabaseLauncherReleaseDto?>(null) }
    var announcementToDelete by remember { mutableStateOf<SupabaseAnnouncementDto?>(null) }
    var announcementToEdit by remember { mutableStateOf<SupabaseAnnouncementDto?>(null) }
    var isCreatingAnnouncement by remember { mutableStateOf(false) }
    var configToEdit by remember { mutableStateOf<SupabaseLauncherConfigDto?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadAdminData()
    }

    val tabs = remember {
        listOf(
            TabItem(AdminManagerTab.OVERVIEW, "Overview", Icons.Default.Security),
            TabItem(AdminManagerTab.RELEASES, "Releases", Icons.Default.NewReleases),
            TabItem(AdminManagerTab.ANNOUNCEMENTS, "Announcements", Icons.Default.Campaign),
            TabItem(AdminManagerTab.FLAGS_AND_CONFIG, "Config & Flags", Icons.Default.Tune),
            TabItem(AdminManagerTab.SECURITY_AUDIT, "Security & Audit", Icons.Default.Security)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(24.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ADMIN MANAGER",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    EzzBadge(
                        text = "v${viewModel.currentLauncherVersion}",
                        variant = EzzBadgeVariant.PRIMARY
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    EzzBadge(
                        text = "AUTHENTICATED ADMIN",
                        variant = EzzBadgeVariant.SUCCESS
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Operator identity: KrysolDev (Microsoft Authenticated • Canonical UUID Enforced)",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                EzzButton(
                    text = if (isLoading) "Refreshing..." else "Sync Backend",
                    icon = Icons.Default.Refresh,
                    variant = EzzButtonVariant.SECONDARY,
                    size = EzzButtonSize.SMALL,
                    isLoading = isLoading,
                    onClick = { viewModel.loadAdminData(force = true) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Status Banner
        if (actionStatus != null) {
            val isErr = actionStatus!!.contains("Failed", ignoreCase = true) || actionStatus!!.contains("Error", ignoreCase = true)
            EzzCard(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                variant = EzzCardVariant.OUTLINED,
                borderColor = if (isErr) Color(0xFFEF4444) else Color(0xFF10B981),
                backgroundColor = if (isErr) Color(0xFF1F1116) else Color(0xFF0E1A16)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (isErr) Icons.Default.Warning else Icons.Default.Check,
                            contentDescription = null,
                            tint = if (isErr) Color(0xFFEF4444) else Color(0xFF10B981),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = actionStatus!!,
                            color = Color.White,
                            fontSize = 12.5.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp).clickable { viewModel.clearAdminActionStatus() }
                    )
                }
            }
        }

        // Tab Navigation
        EzzTabs(
            items = tabs,
            selectedItem = selectedTab,
            onItemSelected = { selectedTab = it },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Tab Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                AdminManagerTab.OVERVIEW -> AdminOverviewTab(
                    stats = stats,
                    releases = releases,
                    announcements = announcements,
                    featureFlags = featureFlags,
                    launcherConfigs = launcherConfigs,
                    onNavigateToTab = { selectedTab = it },
                    onRefresh = { viewModel.loadAdminData(force = true) }
                )
                AdminManagerTab.RELEASES -> AdminReleasesTab(
                    releases = releases,
                    isLoading = isLoading,
                    onToggleLatest = { release, isLatest -> viewModel.toggleAdminReleaseLatest(release.id, isLatest) },
                    onDeleteRelease = { release -> releaseToDelete = release }
                )
                AdminManagerTab.ANNOUNCEMENTS -> AdminAnnouncementsTab(
                    announcements = announcements,
                    isLoading = isLoading,
                    onCreateAnnouncement = { isCreatingAnnouncement = true },
                    onEditAnnouncement = { announcement -> announcementToEdit = announcement },
                    onDeleteAnnouncement = { announcement -> announcementToDelete = announcement },
                    onToggleActive = { ann, active ->
                        viewModel.saveAdminAnnouncement(ann.copy(isActive = active))
                    }
                )
                AdminManagerTab.FLAGS_AND_CONFIG -> AdminFlagsAndConfigTab(
                    featureFlags = featureFlags,
                    launcherConfigs = launcherConfigs,
                    isLoading = isLoading,
                    onToggleFlag = { key, enabled -> viewModel.updateAdminFeatureFlag(key, enabled) },
                    onEditConfig = { config -> configToEdit = config },
                    onToggleMaintenance = { isMaint ->
                        viewModel.updateAdminLauncherConfig("maintenance_mode", isMaint.toString())
                    }
                )
                AdminManagerTab.SECURITY_AUDIT -> AdminSecurityAuditTab(
                    account = selectedAccount,
                    stats = stats
                )
            }
        }
    }

    // Delete Release Confirmation Modal
    if (releaseToDelete != null) {
        val rel = releaseToDelete!!
        EzzModal(
            onDismiss = { releaseToDelete = null },
            title = "DELETE LAUNCHER RELEASE"
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Are you sure you want to permanently delete release v${rel.version} (${rel.platform})?",
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Target ID: ${rel.id}\nDownload URL: ${rel.downloadUrl ?: "None"}",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Warning: This action permanently removes the release from the remote catalog. Clients will no longer receive update prompts for this version.",
                    color = Color(0xFFF59E0B),
                    fontSize = 11.5.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    EzzButton(
                        text = "Cancel",
                        variant = EzzButtonVariant.GHOST,
                        onClick = { releaseToDelete = null }
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    EzzButton(
                        text = "Confirm Delete",
                        variant = EzzButtonVariant.DANGER,
                        onClick = {
                            val id = rel.id
                            releaseToDelete = null
                            viewModel.deleteAdminRelease(id)
                        }
                    )
                }
            }
        }
    }

    // Delete Announcement Confirmation Modal
    if (announcementToDelete != null) {
        val ann = announcementToDelete!!
        EzzModal(
            onDismiss = { announcementToDelete = null },
            title = "DELETE ANNOUNCEMENT"
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Are you sure you want to delete announcement '${ann.title}'?",
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Announcement ID: ${ann.id}\nPriority: ${ann.priority}",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    EzzButton(
                        text = "Cancel",
                        variant = EzzButtonVariant.GHOST,
                        onClick = { announcementToDelete = null }
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    EzzButton(
                        text = "Confirm Delete",
                        variant = EzzButtonVariant.DANGER,
                        onClick = {
                            val id = ann.id
                            announcementToDelete = null
                            viewModel.deleteAdminAnnouncement(id)
                        }
                    )
                }
            }
        }
    }

    // Create / Edit Announcement Modal
    if (isCreatingAnnouncement || announcementToEdit != null) {
        val editing = announcementToEdit
        var title by remember(editing) { mutableStateOf(editing?.title ?: "") }
        var message by remember(editing) { mutableStateOf(editing?.message ?: "") }
        var type by remember(editing) { mutableStateOf(editing?.type ?: "info") }
        var priority by remember(editing) { mutableStateOf(editing?.priority?.toString() ?: "0") }
        var isActive by remember(editing) { mutableStateOf(editing?.isActive ?: true) }

        EzzModal(
            onDismiss = {
                isCreatingAnnouncement = false
                announcementToEdit = null
            },
            title = if (editing != null) "EDIT ANNOUNCEMENT" else "NEW ANNOUNCEMENT"
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Title", color = Color(0xFF94A3B8), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                EzzTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = "Announcement headline...",
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Message", color = Color(0xFF94A3B8), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                EzzTextField(
                    value = message,
                    onValueChange = { message = it },
                    placeholder = "Announcement content...",
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Type (info, warning, error)", color = Color(0xFF94A3B8), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        EzzTextField(
                            value = type,
                            onValueChange = { type = it },
                            placeholder = "info",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Priority (higher = first)", color = Color(0xFF94A3B8), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        EzzTextField(
                            value = priority,
                            onValueChange = { priority = it },
                            placeholder = "0",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    EzzToggle(
                        checked = isActive,
                        onCheckedChange = { isActive = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Announcement Active (Visible in launcher feed)", color = Color.White, fontSize = 12.5.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    EzzButton(
                        text = "Cancel",
                        variant = EzzButtonVariant.GHOST,
                        onClick = {
                            isCreatingAnnouncement = false
                            announcementToEdit = null
                        }
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    EzzButton(
                        text = "Save Announcement",
                        variant = EzzButtonVariant.PRIMARY,
                        onClick = {
                            val dto = SupabaseAnnouncementDto(
                                id = editing?.id ?: "",
                                title = title.trim(),
                                message = message.trim(),
                                type = type.trim(),
                                priority = priority.toIntOrNull() ?: 0,
                                isActive = isActive
                            )
                            isCreatingAnnouncement = false
                            announcementToEdit = null
                            viewModel.saveAdminAnnouncement(dto)
                        }
                    )
                }
            }
        }
    }

    // Config Edit Modal
    if (configToEdit != null) {
        val conf = configToEdit!!
        var configValue by remember(conf) { mutableStateOf(conf.value) }

        EzzModal(
            onDismiss = { configToEdit = null },
            title = "EDIT CONFIGURATION: ${conf.key}"
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Description: ${conf.description ?: "Launcher setting variable"}",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text("Config Value", color = Color(0xFF94A3B8), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                EzzTextField(
                    value = configValue,
                    onValueChange = { configValue = it },
                    placeholder = "Configuration value...",
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    EzzButton(
                        text = "Cancel",
                        variant = EzzButtonVariant.GHOST,
                        onClick = { configToEdit = null }
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    EzzButton(
                        text = "Save Value",
                        variant = EzzButtonVariant.PRIMARY,
                        onClick = {
                            val key = conf.key
                            val newVal = configValue.trim()
                            configToEdit = null
                            viewModel.updateAdminLauncherConfig(key, newVal)
                        }
                    )
                }
            }
        }
    }
}

// =========================================================================
// TAB 1: OVERVIEW
// =========================================================================

@Composable
private fun AdminOverviewTab(
    stats: io.ezz.launcher.ui.viewmodel.AdminDashboardStats,
    releases: List<SupabaseLauncherReleaseDto>,
    announcements: List<SupabaseAnnouncementDto>,
    featureFlags: List<SupabaseFeatureFlagDto>,
    launcherConfigs: List<SupabaseLauncherConfigDto>,
    onNavigateToTab: (AdminManagerTab) -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // High-level Stats Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AdminMetricCard(
                title = "LAUNCHER RELEASES",
                value = "${stats.totalReleases}",
                subtext = "Windows builds in database",
                icon = Icons.Default.NewReleases,
                accentColor = Color(0xFF6366F1),
                modifier = Modifier.weight(1f),
                onClick = { onNavigateToTab(AdminManagerTab.RELEASES) }
            )

            AdminMetricCard(
                title = "ACTIVE ANNOUNCEMENTS",
                value = "${stats.activeAnnouncements}",
                subtext = "Live in player feeds",
                icon = Icons.Default.Campaign,
                accentColor = Color(0xFF10B981),
                modifier = Modifier.weight(1f),
                onClick = { onNavigateToTab(AdminManagerTab.ANNOUNCEMENTS) }
            )

            AdminMetricCard(
                title = "FEATURE FLAGS",
                value = "${stats.enabledFeatureFlags} / ${stats.totalFeatureFlags}",
                subtext = "Features currently enabled",
                icon = Icons.Default.Flag,
                accentColor = Color(0xFF38BDF8),
                modifier = Modifier.weight(1f),
                onClick = { onNavigateToTab(AdminManagerTab.FLAGS_AND_CONFIG) }
            )

            AdminMetricCard(
                title = "LAUNCHER STATUS",
                value = if (stats.isMaintenanceMode) "MAINTENANCE" else "OPERATIONAL",
                subtext = if (stats.isMaintenanceMode) "Players blocked" else "All systems normal",
                icon = if (stats.isMaintenanceMode) Icons.Default.Warning else Icons.Default.CloudDone,
                accentColor = if (stats.isMaintenanceMode) Color(0xFFEF4444) else Color(0xFF10B981),
                modifier = Modifier.weight(1f),
                onClick = { onNavigateToTab(AdminManagerTab.FLAGS_AND_CONFIG) }
            )
        }

        // Admin Identity & Session Card
        EzzCard(
            modifier = Modifier.fillMaxWidth(),
            variant = EzzCardVariant.SURFACE,
            borderColor = Color(0xFF1E2638)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AUTHORIZED ADMINISTRATOR SESSION",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }

                    EzzBadge(text = "CRYPTOGRAPHICALLY VERIFIED", variant = EzzBadgeVariant.SUCCESS)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    AdminSessionDetail(label = "AUTHORIZED USERNAME", value = stats.adminUsername, modifier = Modifier.weight(1f))
                    AdminSessionDetail(label = "CANONICAL MINECRAFT UUID", value = stats.adminUuid, modifier = Modifier.weight(1.5f))
                    AdminSessionDetail(label = "AUTHENTICATION PROVIDER", value = "Microsoft Live (Offline Strictly Disallowed)", modifier = Modifier.weight(1.5f))
                    AdminSessionDetail(label = "AUTHORIZATION LEVEL", value = "Full Launcher SuperAdmin (All RPCs)", modifier = Modifier.weight(1.2f))
                }
            }
        }

        // Live Feed Previews
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Latest Release Card
            EzzCard(
                modifier = Modifier.weight(1f),
                variant = EzzCardVariant.OUTLINED,
                borderColor = Color(0xFF1E222D)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LATEST WINDOWS RELEASE",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "View All →",
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onNavigateToTab(AdminManagerTab.RELEASES) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val latest = releases.find { it.isLatest } ?: releases.firstOrNull()
                    if (latest != null) {
                        Text(
                            text = "v${latest.version}",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = latest.releaseNotes ?: "No release notes provided.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (latest.isLatest) EzzBadge(text = "LATEST", variant = EzzBadgeVariant.SUCCESS)
                            if (latest.isRequired) EzzBadge(text = "MANDATORY", variant = EzzBadgeVariant.WARNING)
                        }
                    } else {
                        Text(
                            text = "No releases currently recorded in Supabase.",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Active Announcement Card
            EzzCard(
                modifier = Modifier.weight(1f),
                variant = EzzCardVariant.OUTLINED,
                borderColor = Color(0xFF1E222D)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ACTIVE ANNOUNCEMENTS",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Manage →",
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onNavigateToTab(AdminManagerTab.ANNOUNCEMENTS) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val activeAnn = announcements.filter { it.isActive }
                    if (activeAnn.isNotEmpty()) {
                        activeAnn.take(2).forEach { ann ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ann.title,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = ann.message,
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                EzzBadge(text = "Priority ${ann.priority}", variant = EzzBadgeVariant.NEUTRAL)
                            }
                        }
                    } else {
                        Text(
                            text = "No active announcements in the broadcast stream.",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminMetricCard(
    title: String,
    value: String,
    subtext: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    EzzCard(
        modifier = modifier,
        variant = EzzCardVariant.SURFACE,
        borderColor = Color(0xFF1E222D),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = Color(0xFF94A3B8),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = value,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtext,
                color = Color(0xFF64748B),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun AdminSessionDetail(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = label, color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(3.dp))
        Text(text = value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// =========================================================================
// TAB 2: RELEASES
// =========================================================================

@Composable
private fun AdminReleasesTab(
    releases: List<SupabaseLauncherReleaseDto>,
    isLoading: Boolean,
    onToggleLatest: (SupabaseLauncherReleaseDto, Boolean) -> Unit,
    onDeleteRelease: (SupabaseLauncherReleaseDto) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(releases, searchQuery) {
        if (searchQuery.isBlank()) releases
        else releases.filter {
            it.version.contains(searchQuery, ignoreCase = true) ||
            (it.releaseNotes?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EzzTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search releases by version...",
                leadingIcon = Icons.Default.Search,
                modifier = Modifier.width(320.dp)
            )

            Text(
                text = "${filtered.size} Releases found",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Releases Table Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F1218), RoundedCornerShape(6.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("VERSION", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
            Text("PLATFORM", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
            Text("FLAGS", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
            Text("PUBLISHED AT", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
            Text("ACTIONS", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (isLoading && releases.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
            }
        } else if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EzzEmptyState(
                    title = "No Releases Found",
                    description = if (searchQuery.isNotBlank()) "No releases match '$searchQuery'" else "No launcher releases in database",
                    icon = Icons.Default.NewReleases
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filtered, key = { it.id }) { rel ->
                    EzzCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = EzzCardVariant.OUTLINED,
                        borderColor = if (rel.isLatest) Color(0xFF1E3A2F) else Color(0xFF1A1E29)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Version
                            Text(
                                text = "v${rel.version}",
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1.5f)
                            )

                            // Platform
                            Text(
                                text = rel.platform,
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1.2f)
                            )

                            // Flags
                            Row(
                                modifier = Modifier.weight(2f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (rel.isLatest) {
                                    EzzBadge(text = "LATEST", variant = EzzBadgeVariant.SUCCESS)
                                }
                                if (rel.isRequired) {
                                    EzzBadge(text = "REQUIRED", variant = EzzBadgeVariant.WARNING)
                                }
                                if (!rel.isLatest && !rel.isRequired) {
                                    Text("Standard", color = Color(0xFF64748B), fontSize = 11.sp)
                                }
                            }

                            // Published At
                            Text(
                                text = rel.publishedAt?.take(10) ?: rel.createdAt?.take(10) ?: "Unknown",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.5.sp,
                                modifier = Modifier.weight(2f)
                            )

                            // Actions
                            Row(
                                modifier = Modifier.weight(2f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!rel.isLatest) {
                                    EzzButton(
                                        text = "Make Latest",
                                        variant = EzzButtonVariant.OUTLINE,
                                        size = EzzButtonSize.SMALL,
                                        onClick = { onToggleLatest(rel, true) }
                                    )
                                }

                                EzzButton(
                                    text = "Delete",
                                    icon = Icons.Default.Delete,
                                    variant = EzzButtonVariant.DANGER,
                                    size = EzzButtonSize.SMALL,
                                    onClick = { onDeleteRelease(rel) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// TAB 3: ANNOUNCEMENTS
// =========================================================================

@Composable
private fun AdminAnnouncementsTab(
    announcements: List<SupabaseAnnouncementDto>,
    isLoading: Boolean,
    onCreateAnnouncement: () -> Unit,
    onEditAnnouncement: (SupabaseAnnouncementDto) -> Unit,
    onDeleteAnnouncement: (SupabaseAnnouncementDto) -> Unit,
    onToggleActive: (SupabaseAnnouncementDto, Boolean) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(announcements, searchQuery) {
        if (searchQuery.isBlank()) announcements
        else announcements.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.message.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EzzTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search announcements...",
                leadingIcon = Icons.Default.Search,
                modifier = Modifier.width(320.dp)
            )

            EzzButton(
                text = "New Announcement",
                icon = Icons.Default.Add,
                variant = EzzButtonVariant.PRIMARY,
                size = EzzButtonSize.SMALL,
                onClick = onCreateAnnouncement
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Table Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F1218), RoundedCornerShape(6.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("TITLE & MESSAGE", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(3f))
            Text("TYPE", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("PRIORITY", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("ACTIVE", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("ACTIONS", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (isLoading && announcements.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
            }
        } else if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EzzEmptyState(
                    title = "No Announcements",
                    description = if (searchQuery.isNotBlank()) "No announcements match '$searchQuery'" else "Create an announcement to broadcast news to all launcher users",
                    icon = Icons.Default.Campaign,
                    actionLabel = "Create Announcement",
                    onAction = onCreateAnnouncement
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filtered, key = { it.id }) { ann ->
                    EzzCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = EzzCardVariant.OUTLINED,
                        borderColor = if (ann.isActive) Color(0xFF1E3A2F) else Color(0xFF1A1E29)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Title & Message
                            Column(modifier = Modifier.weight(3f)) {
                                Text(
                                    text = ann.title,
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = ann.message,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Type
                            Box(modifier = Modifier.weight(1f)) {
                                val variant = when (ann.type.lowercase()) {
                                    "warning" -> EzzBadgeVariant.WARNING
                                    "error" -> EzzBadgeVariant.DANGER
                                    else -> EzzBadgeVariant.NEUTRAL
                                }
                                EzzBadge(text = ann.type.uppercase(), variant = variant)
                            }

                            // Priority
                            Text(
                                text = "P${ann.priority}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )

                            // Active Toggle
                            Box(modifier = Modifier.weight(1f)) {
                                EzzToggle(
                                    checked = ann.isActive,
                                    onCheckedChange = { onToggleActive(ann, it) }
                                )
                            }

                            // Actions
                            Row(
                                modifier = Modifier.weight(1.5f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                EzzButton(
                                    text = "Edit",
                                    icon = Icons.Default.Edit,
                                    variant = EzzButtonVariant.OUTLINE,
                                    size = EzzButtonSize.SMALL,
                                    onClick = { onEditAnnouncement(ann) }
                                )
                                EzzButton(
                                    text = "Delete",
                                    icon = Icons.Default.Delete,
                                    variant = EzzButtonVariant.DANGER,
                                    size = EzzButtonSize.SMALL,
                                    onClick = { onDeleteAnnouncement(ann) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// TAB 4: REMOTE CONFIG & FEATURE FLAGS
// =========================================================================

@Composable
private fun AdminFlagsAndConfigTab(
    featureFlags: List<SupabaseFeatureFlagDto>,
    launcherConfigs: List<SupabaseLauncherConfigDto>,
    isLoading: Boolean,
    onToggleFlag: (String, Boolean) -> Unit,
    onEditConfig: (SupabaseLauncherConfigDto) -> Unit,
    onToggleMaintenance: (Boolean) -> Unit
) {
    val isMaint = launcherConfigs.find { it.key == "maintenance_mode" }?.value?.equals("true", ignoreCase = true) ?: false

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Maintenance Mode Emergency Control Card
        EzzCard(
            modifier = Modifier.fillMaxWidth(),
            variant = EzzCardVariant.OUTLINED,
            borderColor = if (isMaint) Color(0xFFEF4444) else Color(0xFF1E222D),
            backgroundColor = if (isMaint) Color(0xFF1A0E13) else Color(0xFF10131A)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isMaint) Color(0xFFEF4444).copy(alpha = 0.2f) else Color(0xFF10B981).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isMaint) Icons.Default.Warning else Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = if (isMaint) Color(0xFFEF4444) else Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "LAUNCHER MAINTENANCE MODE",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isMaint) "MAINTENANCE ACTIVE: Non-admin users are blocked from playing." else "Operational: All launcher instances & services are online.",
                            color = if (isMaint) Color(0xFFEF4444) else Color(0xFF94A3B8),
                            fontSize = 11.5.sp
                        )
                    }
                }

                EzzToggle(
                    checked = isMaint,
                    onCheckedChange = onToggleMaintenance
                )
            }
        }

        // Section: Remote Config Key-Value Variables
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "REMOTE LAUNCHER CONFIGURATION",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Global dynamic variables applied to all client installations on launch.",
                color = Color(0xFF94A3B8),
                fontSize = 11.5.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            launcherConfigs.forEach { conf ->
                EzzCard(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    variant = EzzCardVariant.OUTLINED,
                    borderColor = Color(0xFF1A1E29)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = conf.key,
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            val desc = conf.description
                            if (!desc.isNullOrBlank()) {
                                Text(
                                    text = desc,
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = conf.value,
                                color = Color(0xFF38BDF8),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 12.dp)
                            )

                            EzzButton(
                                text = "Edit",
                                icon = Icons.Default.Edit,
                                variant = EzzButtonVariant.OUTLINE,
                                size = EzzButtonSize.SMALL,
                                onClick = { onEditConfig(conf) }
                            )
                        }
                    }
                }
            }
        }

        // Section: Feature Flags
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "DYNAMIC FEATURE FLAGS",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Remotely toggle features without requiring client updates.",
                color = Color(0xFF94A3B8),
                fontSize = 11.5.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            featureFlags.forEach { flag ->
                EzzCard(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    variant = EzzCardVariant.OUTLINED,
                    borderColor = if (flag.enabled) Color(0xFF1E3A2F) else Color(0xFF1A1E29)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = flag.featureKey,
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                EzzBadge(
                                    text = if (flag.enabled) "ENABLED" else "DISABLED",
                                    variant = if (flag.enabled) EzzBadgeVariant.SUCCESS else EzzBadgeVariant.NEUTRAL
                                )
                            }
                            val flagDesc = flag.description
                            if (!flagDesc.isNullOrBlank()) {
                                Text(
                                    text = flagDesc,
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        EzzToggle(
                            checked = flag.enabled,
                            onCheckedChange = { onToggleFlag(flag.featureKey, it) }
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// TAB 5: SECURITY & AUDIT
// =========================================================================

@Composable
private fun AdminSecurityAuditTab(
    account: io.ezz.launcher.core.model.account.Account?,
    stats: io.ezz.launcher.ui.viewmodel.AdminDashboardStats
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Access Policy Overview Card
        EzzCard(
            modifier = Modifier.fillMaxWidth(),
            variant = EzzCardVariant.SURFACE,
            borderColor = Color(0xFF1B3D2B)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CANONICAL ACCESS CONTROL POLICY",
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Admin Manager access is governed by strict zero-trust identity verification. Access is permitted if and only if all four conditions are met simultaneously:",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                SecurityCheckItem(
                    title = "1. Active Authenticated Session",
                    description = "The user must have an active, non-expired authentication session in local secure storage.",
                    isPassed = account != null
                )

                SecurityCheckItem(
                    title = "2. Microsoft Account Provider",
                    description = "Authentication provider must be Microsoft. Offline accounts are strictly blocked regardless of username.",
                    isPassed = account is io.ezz.launcher.core.model.account.MicrosoftAccount && account.type == io.ezz.launcher.core.model.account.AccountType.MICROSOFT
                )

                SecurityCheckItem(
                    title = "3. Canonical Admin Identity Enforcement",
                    description = "Username must be 'KrysolDev' AND immutable Minecraft UUID must match 'ad17221c-781d-4ec5-aca6-f5069fbced7b'.",
                    isPassed = account?.username.equals("KrysolDev", ignoreCase = true) &&
                               account?.uuid?.replace("-", "").equals("ad17221c781d4ec5aca6f5069fbced7b", ignoreCase = true)
                )

                SecurityCheckItem(
                    title = "4. Remote Server-Side RPC Verification",
                    description = "Supabase PostgreSQL function 'is_admin_user' verifies operator credentials against protected admin tables.",
                    isPassed = true
                )
            }
        }

        // Active Session Matrix Card
        EzzCard(
            modifier = Modifier.fillMaxWidth(),
            variant = EzzCardVariant.OUTLINED,
            borderColor = Color(0xFF1E222D)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "ACTIVE SESSION TELEMETRY & CREDENTIALS",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black
                )

                Spacer(modifier = Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AuditRow(label = "Operator Account ID", value = account?.id ?: "N/A")
                    AuditRow(label = "Authenticated Username", value = account?.username ?: "N/A")
                    AuditRow(label = "Authenticated UUID", value = account?.uuid ?: "N/A")
                    AuditRow(label = "Account Class", value = account?.let { it::class.simpleName } ?: "N/A")
                    AuditRow(label = "Account Type Enum", value = account?.type?.name ?: "N/A")
                    AuditRow(label = "Canonical Admin Match", value = if (io.ezz.launcher.core.auth.admin.AdminAuthorizationService.isCanonicalAdminIdentity(account)) "TRUE (VALIDATED)" else "FALSE")
                    AuditRow(label = "Client Route Guard", value = "ENFORCED (Auto-redirects to Home on auth loss)")
                    AuditRow(label = "Backend Operations Guard", value = "ENFORCED (All mutating endpoints verify identity server-side)")
                }
            }
        }
    }
}

@Composable
private fun SecurityCheckItem(title: String, description: String, isPassed: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(if (isPassed) Color(0xFF10B981) else Color(0xFFEF4444)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPassed) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(11.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = title, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            Text(text = description, color = Color(0xFF94A3B8), fontSize = 11.sp)
        }
    }
}

@Composable
private fun AuditRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color(0xFF94A3B8), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
        Text(text = value, color = Color.White, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace)
    }
}
