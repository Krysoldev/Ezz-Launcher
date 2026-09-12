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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import io.ezz.launcher.core.storage.github.GitHubReleaseDto
import io.ezz.launcher.ui.viewmodel.ReleasePublishStep
import java.io.File
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
                    viewModel = viewModel,
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

// =========================================================================
// TAB 2: RELEASES (OFFICIAL RELEASE MANAGEMENT CENTER)
// =========================================================================

@Composable
private fun AdminReleasesTab(
    viewModel: AppViewModel,
    releases: List<SupabaseLauncherReleaseDto>,
    isLoading: Boolean,
    onToggleLatest: (SupabaseLauncherReleaseDto, Boolean) -> Unit,
    onDeleteRelease: (SupabaseLauncherReleaseDto) -> Unit
) {
    val gitHubReleases by viewModel.gitHubReleases.collectAsState()
    val isFetchingGitHub by viewModel.isFetchingGitHubReleases.collectAsState()
    val publishStep by viewModel.releasePublishStep.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var isCreatingRelease by remember { mutableStateOf(false) }
    var selectedGitHubReleaseForDetails by remember { mutableStateOf<GitHubReleaseDto?>(null) }
    var selectedSupabaseReleaseForDetails by remember { mutableStateOf<SupabaseLauncherReleaseDto?>(null) }

    val latestSupabase = remember(releases) { releases.find { it.isLatest } ?: releases.firstOrNull() }
    val latestGitHub = remember(gitHubReleases) { gitHubReleases.firstOrNull() }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Summary Status Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AdminMetricCard(
                title = "CURRENT APPLICATION",
                value = "v${viewModel.currentLauncherVersion}",
                subtext = "Active running binary",
                icon = Icons.Default.CheckCircle,
                accentColor = Color(0xFF10B981),
                modifier = Modifier.weight(1f),
                onClick = {}
            )

            AdminMetricCard(
                title = "SUPABASE LATEST",
                value = if (latestSupabase != null) "v${latestSupabase.version}" else "None",
                subtext = if (latestSupabase != null) "User update target" else "No release cataloged",
                icon = Icons.Default.CloudDone,
                accentColor = Color(0xFF6366F1),
                modifier = Modifier.weight(1f),
                onClick = {}
            )

            AdminMetricCard(
                title = "GITHUB RELEASES",
                value = "${gitHubReleases.size} Builds",
                subtext = "Krysoldev/Ezz-Launcher",
                icon = Icons.Default.CloudUpload,
                accentColor = Color(0xFF38BDF8),
                modifier = Modifier.weight(1f),
                onClick = {}
            )

            AdminMetricCard(
                title = "INTEGRITY & SAFETY",
                value = "SHA-256",
                subtext = "Dual binaries verified",
                icon = Icons.Default.Security,
                accentColor = Color(0xFFF59E0B),
                modifier = Modifier.weight(1f),
                onClick = {}
            )
        }

        // Action & Filter Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                EzzTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "Search releases by version or tag...",
                    leadingIcon = Icons.Default.Search,
                    modifier = Modifier.width(280.dp)
                )

                // Quick Filters
                Row(
                    modifier = Modifier
                        .background(Color(0xFF0F1218), RoundedCornerShape(6.dp))
                        .padding(2.dp)
                ) {
                    listOf("ALL" to "All", "GITHUB" to "GitHub", "SUPABASE" to "Supabase").forEach { (key, label) ->
                        val isSelected = selectedFilter == key
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) Color(0xFF1E2638) else Color.Transparent)
                                .clickable { selectedFilter = key }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                EzzButton(
                    text = if (isFetchingGitHub) "Syncing..." else "Refresh",
                    icon = Icons.Default.Refresh,
                    variant = EzzButtonVariant.SECONDARY,
                    size = EzzButtonSize.SMALL,
                    isLoading = isFetchingGitHub,
                    onClick = {
                        viewModel.loadAdminData(force = true)
                        viewModel.fetchGitHubReleases(force = true)
                    }
                )

                EzzButton(
                    text = "Create Release",
                    icon = Icons.Default.Add,
                    variant = EzzButtonVariant.PRIMARY,
                    size = EzzButtonSize.SMALL,
                    onClick = { isCreatingRelease = true }
                )
            }
        }

        // Releases Table Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F1218), RoundedCornerShape(6.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("VERSION & TITLE", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2.2f))
            Text("HOST & STATUS", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.8f))
            Text("ARTIFACTS", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
            Text("PUBLISHED AT", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
            Text("ACTIONS", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2.2f))
        }

        // List Content
        if (isLoading && releases.isEmpty() && gitHubReleases.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
            }
        } else {
            // Unify releases for display
            val displayedGitHubReleases = remember(gitHubReleases, searchQuery, selectedFilter) {
                if (selectedFilter == "SUPABASE") emptyList()
                else gitHubReleases.filter {
                    searchQuery.isBlank() ||
                    it.tagName.contains(searchQuery, ignoreCase = true) ||
                    (it.name?.contains(searchQuery, ignoreCase = true) == true)
                }
            }

            val displayedSupabaseReleases = remember(releases, searchQuery, selectedFilter) {
                if (selectedFilter == "GITHUB") emptyList()
                else releases.filter {
                    searchQuery.isBlank() ||
                    it.version.contains(searchQuery, ignoreCase = true) ||
                    (it.releaseNotes?.contains(searchQuery, ignoreCase = true) == true)
                }
            }

            val isEmpty = displayedGitHubReleases.isEmpty() && displayedSupabaseReleases.isEmpty()

            if (isEmpty) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EzzEmptyState(
                        title = "No Releases Found",
                        description = if (searchQuery.isNotBlank()) "No releases match '$searchQuery'" else "Click 'Create Release' to build and publish your first official release.",
                        icon = Icons.Default.NewReleases,
                        actionLabel = "Create Release",
                        onAction = { isCreatingRelease = true }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // GitHub Releases
                    if (selectedFilter != "SUPABASE") {
                        items(displayedGitHubReleases, key = { "gh-${it.id}" }) { gh ->
                            val cleanVer = gh.tagName.removePrefix("v").trim()
                            val matchingSupabase = releases.find { it.version.equals(cleanVer, ignoreCase = true) }
                            val hasInstaller = gh.assets.any { it.name.contains("Setup", ignoreCase = true) && it.name.endsWith(".exe", ignoreCase = true) }
                            val hasExe = gh.assets.any { it.name.endsWith(".exe", ignoreCase = true) && !it.name.contains("Setup", ignoreCase = true) }

                            EzzCard(
                                modifier = Modifier.fillMaxWidth(),
                                variant = EzzCardVariant.OUTLINED,
                                borderColor = if (matchingSupabase?.isLatest == true) Color(0xFF1E3A2F) else Color(0xFF1A1E29)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Version & Title
                                    Column(modifier = Modifier.weight(2.2f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = gh.tagName,
                                                color = Color.White,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (gh.draft) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                EzzBadge(text = "DRAFT", variant = EzzBadgeVariant.WARNING)
                                            }
                                            if (matchingSupabase?.isLatest == true) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                EzzBadge(text = "LATEST", variant = EzzBadgeVariant.SUCCESS)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = gh.name ?: "Release ${gh.tagName}",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Host & Status
                                    Column(modifier = Modifier.weight(1.8f)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            EzzBadge(text = "GITHUB", variant = EzzBadgeVariant.PRIMARY)
                                            if (matchingSupabase != null) {
                                                EzzBadge(text = "SYNCED", variant = EzzBadgeVariant.SUCCESS)
                                            } else {
                                                EzzBadge(text = "NOT SYNCED", variant = EzzBadgeVariant.NEUTRAL)
                                            }
                                        }
                                        if (matchingSupabase?.isRequired == true) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            EzzBadge(text = "MANDATORY", variant = EzzBadgeVariant.DANGER)
                                        }
                                    }

                                    // Artifacts
                                    Column(modifier = Modifier.weight(2f)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (hasInstaller) {
                                                EzzBadge(text = "Setup.exe", variant = EzzBadgeVariant.SUCCESS)
                                            }
                                            if (hasExe) {
                                                EzzBadge(text = "EzzLauncher.exe", variant = EzzBadgeVariant.NEUTRAL)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${gh.assets.size} asset(s) attached",
                                            color = Color(0xFF64748B),
                                            fontSize = 11.sp
                                        )
                                    }

                                    // Published At
                                    Text(
                                        text = gh.publishedAt?.take(10) ?: gh.createdAt?.take(10) ?: "Unknown",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.5.sp,
                                        modifier = Modifier.weight(1.5f)
                                    )

                                    // Actions
                                    Row(
                                        modifier = Modifier.weight(2.2f),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        EzzButton(
                                            text = "Details",
                                            variant = EzzButtonVariant.GHOST,
                                            size = EzzButtonSize.SMALL,
                                            onClick = { selectedGitHubReleaseForDetails = gh }
                                        )

                                        if (gh.htmlUrl != null) {
                                            EzzButton(
                                                text = "GitHub",
                                                icon = Icons.Default.OpenInNew,
                                                variant = EzzButtonVariant.OUTLINE,
                                                size = EzzButtonSize.SMALL,
                                                onClick = { viewModel.platformBridge.openUrl(gh.htmlUrl) }
                                            )
                                        }

                                        if (matchingSupabase == null) {
                                            EzzButton(
                                                text = "Sync",
                                                icon = Icons.Default.Sync,
                                                variant = EzzButtonVariant.SECONDARY,
                                                size = EzzButtonSize.SMALL,
                                                onClick = {
                                                    viewModel.syncGitHubReleaseToSupabase(
                                                        release = gh,
                                                        isLatest = true,
                                                        isRequired = false
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Supabase Only Releases (if not already listed in GitHub)
                    if (selectedFilter != "GITHUB") {
                        val supabaseOnly = displayedSupabaseReleases.filter { s ->
                            gitHubReleases.none { it.tagName.removePrefix("v").equals(s.version.removePrefix("v"), ignoreCase = true) }
                        }

                        items(supabaseOnly, key = { "sb-${it.id}" }) { rel ->
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
                                    Column(modifier = Modifier.weight(2.2f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "v${rel.version}",
                                                color = Color.White,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (rel.isLatest) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                EzzBadge(text = "LATEST", variant = EzzBadgeVariant.SUCCESS)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = rel.title ?: "Ezz Launcher ${rel.version}",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Host & Status
                                    Column(modifier = Modifier.weight(1.8f)) {
                                        EzzBadge(text = "SUPABASE ONLY", variant = EzzBadgeVariant.NEUTRAL)
                                        if (rel.isRequired) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            EzzBadge(text = "REQUIRED", variant = EzzBadgeVariant.WARNING)
                                        }
                                    }

                                    // Artifacts
                                    Column(modifier = Modifier.weight(2f)) {
                                        if (rel.installerUrl != null) {
                                            EzzBadge(text = "Setup.exe", variant = EzzBadgeVariant.SUCCESS)
                                        } else if (rel.downloadUrl != null) {
                                            EzzBadge(text = "Download URL", variant = EzzBadgeVariant.PRIMARY)
                                        } else {
                                            Text("No artifact URL", color = Color(0xFF64748B), fontSize = 11.sp)
                                        }
                                    }

                                    // Published At
                                    Text(
                                        text = rel.publishedAt?.take(10) ?: rel.createdAt?.take(10) ?: "Unknown",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.5.sp,
                                        modifier = Modifier.weight(1.5f)
                                    )

                                    // Actions
                                    Row(
                                        modifier = Modifier.weight(2.2f),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
    }

    // =========================================================================
    // MODAL: CREATE RELEASE (OFFICIAL RELEASE CENTER)
    // =========================================================================
    if (isCreatingRelease) {
        var version by remember { mutableStateOf("1.0.1") }
        var releaseTitle by remember { mutableStateOf("Ezz Launcher 1.0.1") }
        var releaseNotes by remember {
            mutableStateOf(
                """## What's New in Ezz Launcher v1.0.1
- Official Admin Release Management Center
- Strict Microsoft Administrator authentication (KrysolDev canonical verification)
- Live GitHub Release asset upload and Supabase update synchronization
- Automatic in-place updater with SHA-256 integrity verification
- Buttery-smooth sidebar interactions and UI polish
- Full user profile, account, and modpack preservation"""
            )
        }
        var installerPath by remember { mutableStateOf("release/EzzLauncher-Setup-1.0.1.exe") }
        var exePath by remember { mutableStateOf("release/EzzLauncher.exe") }
        var isLatest by remember { mutableStateOf(true) }
        var isDraft by remember { mutableStateOf(false) }
        var isRequired by remember { mutableStateOf(false) }

        val cleanVer = version.trim().removePrefix("v")
        val isSemVerValid = remember(cleanVer) {
            Regex("""^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?$""").matches(cleanVer)
        }
        val existsOnGithub = remember(cleanVer, gitHubReleases) {
            gitHubReleases.any { it.tagName.removePrefix("v").equals(cleanVer, ignoreCase = true) }
        }
        val existsOnSupabase = remember(cleanVer, releases) {
            releases.any { it.version.removePrefix("v").equals(cleanVer, ignoreCase = true) }
        }

        // Check local files
        val installerFile = remember(installerPath) { File(installerPath) }
        val exeFile = remember(exePath) { File(exePath) }
        val installerExists = remember(installerFile) { installerFile.exists() && installerFile.isFile }
        val exeExists = remember(exeFile) { exeFile.exists() && exeFile.isFile }

        var installerSha256 by remember { mutableStateOf<String?>(null) }
        var exeSha256 by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(installerFile) {
            if (installerExists) {
                installerSha256 = calculateLocalSha256(installerFile)
            } else {
                installerSha256 = null
            }
        }

        LaunchedEffect(exeFile) {
            if (exeExists) {
                exeSha256 = calculateLocalSha256(exeFile)
            } else {
                exeSha256 = null
            }
        }

        val isPublishing = publishStep !is ReleasePublishStep.Idle &&
                           publishStep !is ReleasePublishStep.Success &&
                           publishStep !is ReleasePublishStep.Failed &&
                           publishStep !is ReleasePublishStep.PartialSuccess

        EzzModal(
            onDismiss = {
                if (!isPublishing) {
                    isCreatingRelease = false
                    viewModel.resetReleasePublishState()
                }
            },
            title = "OFFICIAL RELEASE CENTER — CREATE & PUBLISH"
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Note
                Text(
                    text = "Publish a production release to official GitHub repository (Krysoldev/Ezz-Launcher) and synchronize release metadata with Supabase. All active Ezz Launcher clients will immediately receive this update.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )

                // Pipeline Progress Banner (Active when publishing)
                when (val step = publishStep) {
                    ReleasePublishStep.Idle -> {}
                    is ReleasePublishStep.Validating -> {
                        ReleasePipelineBanner(
                            title = "VALIDATING ARTIFACTS & METADATA",
                            message = step.message,
                            color = Color(0xFF6366F1),
                            isLoading = true
                        )
                    }
                    is ReleasePublishStep.Publishing -> {
                        ReleasePipelineBanner(
                            title = "CREATING GITHUB RELEASE",
                            message = step.message,
                            color = Color(0xFF8B5CF6),
                            isLoading = true
                        )
                    }
                    is ReleasePublishStep.Uploading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF161226))
                                .border(1.dp, Color(0xFF4C2889), RoundedCornerShape(6.dp))
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "UPLOADING ASSETS TO GITHUB (${step.currentFileIndex}/${step.totalFiles})",
                                        color = Color(0xFFA855F7),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${(step.progress * 100).toInt()}%",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "Uploading ${step.fileName} directly to GitHub Releases...",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.5.sp
                                )
                                LinearProgressIndicator(
                                    progress = { step.progress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = Color(0xFFA855F7),
                                    trackColor = Color(0xFF261D3B)
                                )
                            }
                        }
                    }
                    is ReleasePublishStep.SyncingSupabase -> {
                        ReleasePipelineBanner(
                            title = "SYNCHRONIZING SUPABASE CATALOG",
                            message = "Publishing release metadata and download URLs to Supabase database...",
                            color = Color(0xFF3B82F6),
                            isLoading = true
                        )
                    }
                    is ReleasePublishStep.Success -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0F2618))
                                .border(1.dp, Color(0xFF1B4D2E), RoundedCornerShape(6.dp))
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = "RELEASE PUBLISHED SUCCESSFULLY!", color = Color(0xFF10B981), fontSize = 12.5.sp, fontWeight = FontWeight.Black)
                                }
                                Text(
                                    text = "Release v${step.version} is now officially published to GitHub and synchronized with Supabase. Client launchers can now detect and download the update.",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.5.sp
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    EzzButton(
                                        text = "View on GitHub",
                                        icon = Icons.Default.OpenInNew,
                                        size = EzzButtonSize.SMALL,
                                        variant = EzzButtonVariant.SECONDARY,
                                        onClick = { viewModel.platformBridge.openUrl(step.releaseUrl) }
                                    )
                                    EzzButton(
                                        text = "Done",
                                        size = EzzButtonSize.SMALL,
                                        variant = EzzButtonVariant.PRIMARY,
                                        onClick = {
                                            viewModel.resetReleasePublishState()
                                            isCreatingRelease = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    is ReleasePublishStep.PartialSuccess -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF2B1F0E))
                                .border(1.dp, Color(0xFF593F16), RoundedCornerShape(6.dp))
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = "PARTIAL SUCCESS: GITHUB PUBLISHED / SUPABASE SYNC FAILED", color = Color(0xFFF59E0B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(text = step.message, color = Color(0xFFCBD5E1), fontSize = 11.5.sp)
                                if (step.existingRelease != null) {
                                    EzzButton(
                                        text = "Retry Supabase Sync Now",
                                        icon = Icons.Default.Sync,
                                        size = EzzButtonSize.SMALL,
                                        variant = EzzButtonVariant.PRIMARY,
                                        onClick = { viewModel.syncGitHubReleaseToSupabase(step.existingRelease, isLatest = isLatest, isRequired = isRequired) }
                                    )
                                }
                            }
                        }
                    }
                    is ReleasePublishStep.Failed -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF260F0F))
                                .border(1.dp, Color(0xFF592222), RoundedCornerShape(6.dp))
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = "RELEASE PUBLISHING FAILED", color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(text = step.error, color = Color(0xFFCBD5E1), fontSize = 11.5.sp)
                            }
                        }
                    }
                    is ReleasePublishStep.Preparing -> {
                        ReleasePipelineBanner(
                            title = "PREPARING RELEASE",
                            message = "Preparing release parameters and security tokens...",
                            color = Color(0xFF6366F1),
                            isLoading = true
                        )
                    }
                }

                // Version & Title Fields
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Version (Semantic Versioning)", color = Color(0xFF94A3B8), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        EzzTextField(
                            value = version,
                            onValueChange = {
                                version = it
                                releaseTitle = "Ezz Launcher ${it.trim().removePrefix("v")}"
                                installerPath = "release/EzzLauncher-Setup-${it.trim().removePrefix("v")}.exe"
                            },
                            placeholder = "1.0.1",
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (!isSemVerValid && version.isNotBlank()) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text("Must be semantic versioning (e.g. 1.0.1)", color = Color(0xFFEF4444), fontSize = 11.sp)
                        }
                        if (existsOnGithub || existsOnSupabase) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text("Warning: Version $version already exists on ${if (existsOnGithub) "GitHub" else ""} ${if (existsOnSupabase) "Supabase" else ""}", color = Color(0xFFF59E0B), fontSize = 11.sp)
                        }
                    }

                    Column(modifier = Modifier.weight(1.5f)) {
                        Text("Release Title", color = Color(0xFF94A3B8), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        EzzTextField(
                            value = releaseTitle,
                            onValueChange = { releaseTitle = it },
                            placeholder = "Ezz Launcher 1.0.1",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Release Notes
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Release Notes (Markdown supported • Seen by end users)", color = Color(0xFF94A3B8), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    EzzTextField(
                        value = releaseNotes,
                        onValueChange = { releaseNotes = it },
                        placeholder = "Changelog and release notes...",
                        modifier = Modifier.fillMaxWidth().height(110.dp)
                    )
                }

                // Artifact Validation Card
                EzzCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = EzzCardVariant.SURFACE,
                    borderColor = Color(0xFF1E2638)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("PRODUCTION WINDOWS ARTIFACTS", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                            Text("Required for live distribution", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }

                        // Artifact 1: Windows Setup Installer
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Primary Installer: ", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                if (installerExists) {
                                    EzzBadge(text = "FOUND • ${formatBytes(installerFile.length())}", variant = EzzBadgeVariant.SUCCESS)
                                } else {
                                    EzzBadge(text = "NOT FOUND", variant = EzzBadgeVariant.DANGER)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            EzzTextField(
                                value = installerPath,
                                onValueChange = { installerPath = it },
                                placeholder = "Path to EzzLauncher-Setup-*.exe",
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (installerSha256 != null) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text("SHA-256: $installerSha256", color = Color(0xFF64748B), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }

                        // Artifact 2: Standalone Executable
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Standalone EXE: ", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                if (exeExists) {
                                    EzzBadge(text = "FOUND • ${formatBytes(exeFile.length())}", variant = EzzBadgeVariant.SUCCESS)
                                } else {
                                    EzzBadge(text = "OPTIONAL • NOT FOUND", variant = EzzBadgeVariant.NEUTRAL)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            EzzTextField(
                                value = exePath,
                                onValueChange = { exePath = it },
                                placeholder = "Path to EzzLauncher.exe",
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (exeSha256 != null) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text("SHA-256: $exeSha256", color = Color(0xFF64748B), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }

                // Options / Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EzzToggle(checked = isLatest, onCheckedChange = { isLatest = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mark as Latest Release", color = Color.White, fontSize = 12.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EzzToggle(checked = isDraft, onCheckedChange = { isDraft = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Draft on GitHub", color = Color.White, fontSize = 12.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EzzToggle(checked = isRequired, onCheckedChange = { isRequired = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mandatory Update", color = Color.White, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EzzButton(
                        text = "Cancel",
                        variant = EzzButtonVariant.GHOST,
                        enabled = !isPublishing,
                        onClick = {
                            isCreatingRelease = false
                            viewModel.resetReleasePublishState()
                        }
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    val canPublish = isSemVerValid && installerExists && !isPublishing
                    EzzButton(
                        text = if (isPublishing) "Publishing Release..." else "Publish Release",
                        icon = Icons.Default.CloudUpload,
                        variant = EzzButtonVariant.PRIMARY,
                        enabled = canPublish,
                        isLoading = isPublishing,
                        onClick = {
                            viewModel.publishAdminRelease(
                                version = cleanVer,
                                title = releaseTitle.trim(),
                                changelog = releaseNotes.trim(),
                                installerFile = installerFile,
                                exeFile = if (exeExists) exeFile else null,
                                isDraft = isDraft,
                                isRequired = isRequired
                            )
                        }
                    )
                }
            }
        }
    }

    // =========================================================================
    // MODAL: RELEASE DETAILS
    // =========================================================================
    if (selectedGitHubReleaseForDetails != null) {
        val gh = selectedGitHubReleaseForDetails!!
        EzzModal(
            onDismiss = { selectedGitHubReleaseForDetails = null },
            title = "RELEASE DETAILS: ${gh.tagName}"
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = gh.name ?: gh.tagName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "Published at: ${gh.publishedAt ?: gh.createdAt ?: "Unknown"}", color = Color(0xFF94A3B8), fontSize = 11.5.sp)
                    }
                    if (gh.htmlUrl != null) {
                        EzzButton(
                            text = "Open on GitHub",
                            icon = Icons.Default.OpenInNew,
                            size = EzzButtonSize.SMALL,
                            variant = EzzButtonVariant.SECONDARY,
                            onClick = { viewModel.platformBridge.openUrl(gh.htmlUrl) }
                        )
                    }
                }

                // Release Notes
                EzzCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = EzzCardVariant.SURFACE,
                    borderColor = Color(0xFF1E2638)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("RELEASE NOTES", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = gh.body ?: "No release notes available.",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }

                // Assets Table
                Text("RELEASE ASSETS (${gh.assets.size})", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                gh.assets.forEach { asset ->
                    EzzCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = EzzCardVariant.OUTLINED,
                        borderColor = Color(0xFF1E2638)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = asset.name, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = "Size: ${formatBytes(asset.size)} • Downloads: ${asset.downloadCount}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }
                            if (asset.browserDownloadUrl != null) {
                                EzzButton(
                                    text = "Download",
                                    icon = Icons.Default.Download,
                                    size = EzzButtonSize.SMALL,
                                    variant = EzzButtonVariant.OUTLINE,
                                    onClick = { viewModel.platformBridge.openUrl(asset.browserDownloadUrl) }
                                )
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    EzzButton(
                        text = "Close",
                        variant = EzzButtonVariant.GHOST,
                        onClick = { selectedGitHubReleaseForDetails = null }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReleasePipelineBanner(
    title: String,
    message: String,
    color: Color,
    isLoading: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = color,
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column {
                Text(text = title, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = message, color = Color(0xFFCBD5E1), fontSize = 11.5.sp)
            }
        }
    }
}

private fun calculateLocalSha256(file: File): String? {
    return try {
        if (!file.exists() || !file.canRead()) return null
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Throwable) {
        null
    }
}

private fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return "Unknown"
    val mb = bytes / (1024.0 * 1024.0)
    return String.format("%.1f MB", mb)
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
