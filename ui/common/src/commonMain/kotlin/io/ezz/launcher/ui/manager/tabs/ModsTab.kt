package io.ezz.launcher.ui.manager.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.draw.scale
import io.ezz.launcher.core.model.modrinth.ModConflict
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import io.ezz.launcher.ui.components.EzzSearchField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import okio.Path.Companion.toPath
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LocalMod
import io.ezz.launcher.core.model.modrinth.ModrinthBrowseState
import io.ezz.launcher.core.model.modrinth.ModrinthProjectHit
import io.ezz.launcher.ui.audio.EzzAudioService
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.ModrinthAsyncImage
import io.ezz.launcher.ui.components.PaginationBar
import io.ezz.launcher.ui.manager.dialogs.ModInspectDialog
import io.ezz.launcher.ui.viewmodel.AppViewModel

private enum class ModsSubTab(val title: String) {
    INSTALLED("Installed"),
    BROWSE("Browse Mods")
}

@Composable
fun ModsTab(
    instance: Instance,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    var subTab by remember { mutableStateOf(ModsSubTab.INSTALLED) }

    val installedMods by viewModel.manageMods.collectAsState()
    val missingDependencies by viewModel.missingDependencies.collectAsState()
    val compatibilityConflicts by viewModel.compatibilityConflicts.collectAsState()
    val browseState by viewModel.modsBrowseState.collectAsState()
    val queueState by viewModel.contentInstallationManager.queueState.collectAsState()

    var inspectModHit by remember(instance.id) { mutableStateOf<ModrinthProjectHit?>(null) }
    var localSearch by remember(instance.id) { mutableStateOf("") }
    var localFilter by remember(instance.id) { mutableStateOf("ALL") }

    // Bulk selection state
    var selectedModFiles by remember(instance.id) { mutableStateOf(setOf<String>()) }

    LaunchedEffect(instance.id) {
        viewModel.contentHydrator.hydrateInstance(instance.id, forceRefresh = false)
    }

    Column(
        modifier = modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Toolbar: Sub-Tabs & Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sub-Tab Switcher
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF10131A))
                    .border(1.dp, Color(0xFF1B1F2C), RoundedCornerShape(8.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ModsSubTab.entries.forEach { tab ->
                    val isActive = subTab == tab
                    val interactionSource = remember { MutableInteractionSource() }
                    val isHovered by interactionSource.collectIsHoveredAsState()

                    LaunchedEffect(isHovered) {
                        if (isHovered && !isActive) {
                            EzzAudioService.playHover()
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isActive) Color(0xFF1A182E) else if (isHovered) Color(0xFF161A24) else Color.Transparent)
                            .border(
                                1.dp,
                                if (isActive) Color(0xFF8B5CF6) else Color.Transparent,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                EzzAudioService.playSelect()
                                subTab = tab
                                if (tab == ModsSubTab.BROWSE && browseState.items.isEmpty()) {
                                    viewModel.searchMods()
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab.title,
                            color = if (isActive) Color.White else if (isHovered) Color(0xFFE2E8F0) else Color(0xFF94A3B8),
                            fontSize = 12.5.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // Quick Actions: Primary Import Mod & Secondary Open Mods Folder
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EzzButton(
                    text = "Import Mod",
                    onClick = { viewModel.importLocalMod(instance) },
                    icon = Icons.Default.Download,
                    variant = EzzButtonVariant.PRIMARY,
                    size = EzzButtonSize.SMALL
                )

                EzzButton(
                    text = "Open Mods Folder",
                    onClick = { viewModel.openModsFolder(instance.id) },
                    icon = Icons.Default.FolderOpen,
                    variant = EzzButtonVariant.SECONDARY,
                    size = EzzButtonSize.SMALL
                )
            }
        }

        // Active Installation Banner
        val activeInstall = queueState.activeItems.firstOrNull { it.instanceId == instance.id }
        if (activeInstall != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF101318))
                    .border(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .clickable { viewModel.contentInstallationManager.setFocusedItem(activeInstall) }
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF8B5CF6),
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "${activeInstall.stage.label}: ${activeInstall.name}",
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "${activeInstall.progressPercent}%",
                            color = Color(0xFF8B5CF6),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { if (activeInstall.progress < 0f) 0.5f else activeInstall.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFF8B5CF6),
                        trackColor = Color(0xFF141720)
                    )
                }
            }
        }

        // SubTab Content
        when (subTab) {
            ModsSubTab.INSTALLED -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (missingDependencies.isNotEmpty() || compatibilityConflicts.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF231215))
                                .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val totalIssues = missingDependencies.size + compatibilityConflicts.size
                                    Text(
                                        text = if (compatibilityConflicts.isNotEmpty() && missingDependencies.isNotEmpty()) "MOD COMPATIBILITY & DEPENDENCY ISSUES ($totalIssues)"
                                               else if (compatibilityConflicts.isNotEmpty()) "MOD INCOMPATIBILITY DETECTED (${compatibilityConflicts.size})"
                                               else "MISSING REQUIRED DEPENDENCIES (${missingDependencies.size})",
                                        color = Color(0xFFFCA5A5),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    for (conflict in compatibilityConflicts) {
                                        Text(
                                            text = "• ${conflict.reason}",
                                            color = Color(0xFFF87171),
                                            fontSize = 11.5.sp
                                        )
                                    }
                                    for (missing in missingDependencies) {
                                        Text(
                                            text = "• $missing",
                                            color = Color(0xFFE2E8F0),
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    InstalledModsView(
                        instance = instance,
                        viewModel = viewModel,
                        installedMods = installedMods,
                        missingDependencies = missingDependencies,
                        compatibilityConflicts = compatibilityConflicts,
                        localSearch = localSearch,
                        onSearchChange = { localSearch = it },
                        localFilter = localFilter,
                        onFilterChange = { localFilter = it },
                        selectedModFiles = selectedModFiles,
                        onSelectionChange = { selectedModFiles = it },
                        onToggleMod = { mod -> viewModel.toggleManageMod(mod.fileName, !mod.enabled) },
                        onDeleteMod = { mod -> viewModel.deleteManageMod(mod.fileName) },
                        onBulkToggle = { files, enable -> viewModel.bulkToggleMods(files, enable) },
                        onBulkDelete = { files -> viewModel.bulkDeleteMods(files) },
                        onBrowseClick = {
                            subTab = ModsSubTab.BROWSE
                            if (browseState.items.isEmpty()) viewModel.searchMods()
                        }
                    )
                }
            }
            ModsSubTab.BROWSE -> {
                BrowseModsView(
                    instance = instance,
                    viewModel = viewModel,
                    browseState = browseState,
                    onInspect = { hit -> inspectModHit = hit },
                    onInstall = { hit -> viewModel.openModInstaller(hit) }
                )
            }
        }

        // Mod Details / Inspect Dialog
        val activeInspectHit = inspectModHit
        if (activeInspectHit != null) {
            ModInspectDialog(
                projectHit = activeInspectHit,
                instance = instance,
                viewModel = viewModel,
                onDismiss = { inspectModHit = null }
            )
        }
    }
}

@Composable
private fun InstalledModsView(
    instance: Instance,
    viewModel: AppViewModel,
    installedMods: List<LocalMod>,
    missingDependencies: List<String>,
    compatibilityConflicts: List<ModConflict>,
    localSearch: String,
    onSearchChange: (String) -> Unit,
    localFilter: String,
    onFilterChange: (String) -> Unit,
    selectedModFiles: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onToggleMod: (LocalMod) -> Unit,
    onDeleteMod: (LocalMod) -> Unit,
    onBulkToggle: (List<String>, Boolean) -> Unit,
    onBulkDelete: (List<String>) -> Unit,
    onBrowseClick: () -> Unit
) {
    val filtered = remember(installedMods, localSearch, localFilter) {
        val q = localSearch.trim()
        installedMods.filter { mod ->
            val matchesSearch = q.isBlank() ||
                mod.name.contains(q, ignoreCase = true) ||
                mod.id.contains(q, ignoreCase = true) ||
                mod.fileName.contains(q, ignoreCase = true) ||
                (mod.author?.contains(q, ignoreCase = true) == true) ||
                (mod.description?.contains(q, ignoreCase = true) == true) ||
                mod.loader.contains(q, ignoreCase = true) ||
                mod.version.contains(q, ignoreCase = true)
            val matchesFilter = when (localFilter) {
                "ENABLED" -> mod.enabled
                "DISABLED" -> !mod.enabled
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Search & Filter Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search field
            EzzSearchField(
                value = localSearch,
                onValueChange = onSearchChange,
                placeholder = "Search installed mods...",
                modifier = Modifier.width(280.dp),
                onClear = { onSearchChange("") }
            )

            // Enabled/Disabled Filter Chips
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF101318))
                    .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(8.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "ALL" to "All",
                    "ENABLED" to "Enabled",
                    "DISABLED" to "Disabled"
                ).forEach { (key, label) ->
                    val isSelected = localFilter == key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Color(0xFF1A1E29) else Color.Transparent)
                            .border(1.dp, if (isSelected) Color.White else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable { onFilterChange(key) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Bulk Actions Bar (when mods are selected)
        if (selectedModFiles.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF141720))
                    .border(1.dp, Color(0xFF222735), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
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
                        Text(
                            text = "${selectedModFiles.size} mods selected",
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF101318))
                                .clickable { onSelectionChange(emptySet()) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Clear Selection", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        EzzButton(
                            text = "Enable",
                            onClick = {
                                onBulkToggle(selectedModFiles.toList(), true)
                                onSelectionChange(emptySet())
                            },
                            variant = EzzButtonVariant.SECONDARY,
                            size = EzzButtonSize.SMALL
                        )
                        EzzButton(
                            text = "Disable",
                            onClick = {
                                onBulkToggle(selectedModFiles.toList(), false)
                                onSelectionChange(emptySet())
                            },
                            variant = EzzButtonVariant.SECONDARY,
                            size = EzzButtonSize.SMALL
                        )
                        EzzButton(
                            text = "Delete",
                            onClick = {
                                onBulkDelete(selectedModFiles.toList())
                                onSelectionChange(emptySet())
                            },
                            variant = EzzButtonVariant.DANGER,
                            size = EzzButtonSize.SMALL
                        )
                    }
                }
            }
        }

        val isModsLoading by viewModel.isModsLoading.collectAsState()

        // Mod Rows or Tailored Empty State
        if (isModsLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF101318))
                    .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                    Text(
                        text = "Scanning Installed Mods...",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Validating mod archives and reading manifests from .minecraft/mods",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.5.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF101318))
                    .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF141720))
                            .border(1.dp, Color(0xFF222735), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Text(
                        text = if (installedMods.isEmpty()) "No Mods Installed" else "No Matching Mods Found",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = if (installedMods.isEmpty())
                            "Install mods from Modrinth or import .jar files into your mods folder."
                        else
                            "Try searching with a different name or clearing your active filters.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.5.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (installedMods.isEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EzzButton(
                                text = "Import Mod",
                                onClick = { viewModel.importLocalMod(instance) },
                                icon = Icons.Default.Download,
                                variant = EzzButtonVariant.PRIMARY,
                                size = EzzButtonSize.MEDIUM
                            )
                            EzzButton(
                                text = "Browse Mods",
                                onClick = onBrowseClick,
                                icon = Icons.Default.Search,
                                variant = EzzButtonVariant.SECONDARY,
                                size = EzzButtonSize.MEDIUM
                            )
                            EzzButton(
                                text = "Open Mods Folder",
                                onClick = { viewModel.openModsFolder(instance.id) },
                                icon = Icons.Default.FolderOpen,
                                variant = EzzButtonVariant.SECONDARY,
                                size = EzzButtonSize.MEDIUM
                            )
                        }
                    } else {
                        EzzButton(
                            text = "Clear Search & Filters",
                            onClick = {
                                onSearchChange("")
                                onFilterChange("ALL")
                            },
                            icon = Icons.Default.Clear,
                            variant = EzzButtonVariant.SECONDARY,
                            size = EzzButtonSize.MEDIUM
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.fileName }) { mod ->
                    val isSelected = selectedModFiles.contains(mod.fileName)
                    InstalledModRow(
                        mod = mod,
                        instance = instance,
                        viewModel = viewModel,
                        missingDependencies = missingDependencies,
                        compatibilityConflicts = compatibilityConflicts,
                        isSelected = isSelected,
                        onSelectToggle = {
                            onSelectionChange(
                                if (isSelected) selectedModFiles - mod.fileName else selectedModFiles + mod.fileName
                            )
                        },
                        onToggle = { onToggleMod(mod) },
                        onDelete = { onDeleteMod(mod) }
                    )
                }
            }
        }
    }
}

@Composable
private fun InstalledModRow(
    mod: LocalMod,
    instance: Instance,
    viewModel: AppViewModel,
    missingDependencies: List<String>,
    compatibilityConflicts: List<ModConflict>,
    isSelected: Boolean,
    onSelectToggle: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val isMissingDep = remember(missingDependencies, mod.name, mod.id) {
        missingDependencies.any { it.contains(mod.name, ignoreCase = true) || it.contains(mod.id, ignoreCase = true) }
    }
    val isConflict = remember(compatibilityConflicts, mod.name, mod.id) {
        compatibilityConflicts.any { it.reason.contains(mod.name, ignoreCase = true) || it.reason.contains(mod.id, ignoreCase = true) }
    }

    val (statusText, statusColor) = when {
        isConflict -> "Incompatible" to Color(0xFFF59E0B)
        isMissingDep -> "Missing Dependency" to Color(0xFFF87171)
        mod.enabled -> "Enabled" to Color(0xFF10B981)
        else -> "Disabled" to Color(0xFF64748B)
    }

    val rowInteraction = remember { MutableInteractionSource() }
    val isRowHovered by rowInteraction.collectIsHoveredAsState()
    val imgScale by animateFloatAsState(
        targetValue = if (isRowHovered) 1.03f else 1.0f,
        animationSpec = tween(120)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0xFF161A26) else if (isRowHovered) Color(0xFF131620) else Color(0xFF101318))
            .border(1.dp, if (isSelected) Color(0xFF8B5CF6) else if (isRowHovered) Color(0xFF2D3748) else Color(0xFF1A1D26), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Checkbox for bulk actions
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectToggle() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFF8B5CF6),
                        checkmarkColor = Color.White,
                        uncheckedColor = Color(0xFF64748B)
                    )
                )

                // Thumbnail with micro-hover scale
                Box(modifier = Modifier.scale(imgScale)) {
                    ModrinthAsyncImage(
                        url = mod.iconPath,
                        imageLoader = viewModel.imageLoader,
                        placeholderIcon = Icons.Default.Extension,
                        modifier = Modifier.size(46.dp),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = mod.name,
                            color = if (mod.enabled) Color.White else Color(0xFF64748B),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Metadata Row: Loader badge + Version + Status Dot & Text
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF141720))
                                .border(1.dp, Color(0xFF222735), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(text = instance.loaderType.name, color = Color(0xFFA78BFA), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                        }

                        if (mod.version.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF141720))
                                    .border(1.dp, Color(0xFF222735), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(text = "v${mod.version}", color = Color(0xFF94A3B8), fontSize = 10.5.sp)
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Text(
                                text = statusText,
                                color = statusColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Text(
                        text = if (!mod.description.isNullOrBlank()) mod.description!! else mod.fileName,
                        color = Color(0xFF64748B),
                        fontSize = 11.5.sp,
                        maxLines = 1
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Enable/Disable Switch
                Switch(
                    checked = mod.enabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF10B981),
                        uncheckedThumbColor = Color(0xFF94A3B8),
                        uncheckedTrackColor = Color(0xFF141720),
                        uncheckedBorderColor = Color(0xFF222735)
                    )
                )

                val isEzzSkinMod = mod.fileName.startsWith("ezz-skin-mod", ignoreCase = true) || mod.id.contains("ezzskin", ignoreCase = true)

                // Three-dot Action Menu
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Mod Actions",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier
                            .background(Color(0xFF141720))
                            .border(1.dp, Color(0xFF222735), RoundedCornerShape(8.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (mod.enabled) "Disable Mod" else "Enable Mod", color = Color.White, fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(
                                    if (mod.enabled) Icons.Default.ToggleOff else Icons.Default.ToggleOn,
                                    contentDescription = null,
                                    tint = if (mod.enabled) Color(0xFF94A3B8) else Color(0xFF10B981),
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            onClick = {
                                showMenu = false
                                onToggle()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Open File Location", color = Color.White, fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                            },
                            onClick = {
                                showMenu = false
                                val modsDir = viewModel.pathProvider.getInstanceDirectory(instance.id).resolve(".minecraft").resolve("mods").toFile()
                                if (!modsDir.exists()) modsDir.mkdirs()
                                viewModel.platformBridge.openFolder(modsDir.absolutePath.toPath())
                            }
                        )

                        if (!isEzzSkinMod) {
                            DropdownMenuItem(
                                text = { Text("Delete Mod", color = Color(0xFFEF4444), fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(15.dp))
                                },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrowseModsView(
    instance: Instance,
    viewModel: AppViewModel,
    browseState: ModrinthBrowseState,
    onInspect: (ModrinthProjectHit) -> Unit,
    onInstall: (ModrinthProjectHit) -> Unit
) {
    var searchQuery by remember(instance.id) { mutableStateOf(browseState.searchQuery) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Search & Filter Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EzzSearchField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.searchMods(query = it, debounceMs = 350L)
                },
                placeholder = "Search Modrinth mods...",
                modifier = Modifier.weight(1f),
                onClear = {
                    searchQuery = ""
                    viewModel.searchMods(query = "")
                }
            )

            // Sort Options
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF101318))
                    .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(8.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "relevance" to "Relevance",
                    "downloads" to "Downloads",
                    "follows" to "Follows",
                    "newest" to "Newest",
                    "updated" to "Updated"
                ).forEach { (sortKey, sortLabel) ->
                    val isSelected = browseState.selectedSort == sortKey
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Color(0xFF1A1E29) else Color.Transparent)
                            .border(1.dp, if (isSelected) Color.White else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable { viewModel.searchMods(sort = sortKey) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = sortLabel,
                            color = if (isSelected) Color.White else Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Active Search Filters Badge Banner
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Filtered for:", color = Color(0xFF94A3B8), fontSize = 11.5.sp)
            FilterBadge(text = "MC ${instance.minecraftVersion}", color = Color.White)
            FilterBadge(text = instance.loaderType.name, color = Color(0xFF10B981))
            FilterBadge(text = "Modrinth", color = Color(0xFF1BD96A))
        }

        // Results Container
        if (browseState.isLoading && browseState.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
            }
        } else if (browseState.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF101318))
                    .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(36.dp))
                    Text(browseState.error ?: "Error fetching mods from Modrinth", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    EzzButton(
                        text = "Retry",
                        onClick = { viewModel.searchMods() },
                        variant = EzzButtonVariant.SECONDARY,
                        size = EzzButtonSize.SMALL
                    )
                }
            }
        } else if (browseState.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF101318))
                    .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(36.dp))
                    Text("No results found", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Try a different search or clear your active filters.", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    if (searchQuery.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        EzzButton(
                            text = "Clear Search",
                            onClick = {
                                searchQuery = ""
                                viewModel.searchMods(query = "")
                            },
                            icon = Icons.Default.Clear,
                            variant = EzzButtonVariant.SECONDARY,
                            size = EzzButtonSize.SMALL
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(browseState.items, key = { it.projectId }) { hit ->
                    ModrinthModBrowseCard(
                        hit = hit,
                        instance = instance,
                        viewModel = viewModel,
                        onInspect = { onInspect(hit) },
                        onInstall = { onInstall(hit) }
                    )
                }

                item {
                    PaginationBar(
                        currentPage = browseState.page,
                        totalPages = browseState.totalPages,
                        totalHits = browseState.totalHits,
                        isLoading = browseState.isLoading,
                        onPrevious = { viewModel.setModsPage(browseState.page - 1) },
                        onNext = { viewModel.setModsPage(browseState.page + 1) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModrinthModBrowseCard(
    hit: ModrinthProjectHit,
    instance: Instance,
    viewModel: AppViewModel,
    onInspect: () -> Unit,
    onInstall: () -> Unit
) {
    val isInstalled = viewModel.isModInstalled(hit)

    val cardInteraction = remember { MutableInteractionSource() }
    val isHovered by cardInteraction.collectIsHoveredAsState()
    val imgScale by animateFloatAsState(
        targetValue = if (isHovered) 1.04f else 1.0f,
        animationSpec = tween(120)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isHovered) Color(0xFF131722) else Color(0xFF101318))
            .border(
                1.dp,
                if (isHovered) Color(0xFF8B5CF6).copy(alpha = 0.5f) else Color(0xFF1A1D26),
                RoundedCornerShape(10.dp)
            )
            .clickable(
                interactionSource = cardInteraction,
                indication = null,
                onClick = onInspect
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(modifier = Modifier.scale(imgScale)) {
                    ModrinthAsyncImage(
                        url = hit.previewImageUrl,
                        imageLoader = viewModel.imageLoader,
                        modifier = Modifier.size(50.dp).clip(RoundedCornerShape(6.dp)),
                        placeholderIcon = Icons.Default.Extension,
                        contentScale = ContentScale.Crop
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = hit.title,
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (hit.author.isNotBlank()) {
                            Text(
                                text = "by ${hit.author}",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    Text(
                        text = hit.description,
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = "${formatDownloads(hit.downloads)} downloads",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )

                        FilterBadge(text = "MC ${instance.minecraftVersion}", color = Color(0xFF10B981))
                        FilterBadge(text = instance.loaderType.name, color = Color(0xFFA78BFA))

                        hit.categories.take(2).forEach { cat ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF141720))
                                    .border(1.dp, Color(0xFF222735), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = cat,
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val websiteUrl = if (hit.slug.isNotBlank()) "https://modrinth.com/mod/${hit.slug}" else null
                if (!websiteUrl.isNullOrBlank()) {
                    EzzButton(
                        text = "Modrinth",
                        onClick = { viewModel.platformBridge.openUrl(websiteUrl) },
                        variant = EzzButtonVariant.SECONDARY,
                        size = EzzButtonSize.SMALL
                    )
                }

                if (isInstalled) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFF10B981), RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "INSTALLED",
                                color = Color(0xFF10B981),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    EzzButton(
                        text = "Inspect",
                        onClick = onInspect,
                        variant = EzzButtonVariant.SECONDARY,
                        size = EzzButtonSize.SMALL
                    )
                } else {
                    EzzButton(
                        text = "Inspect",
                        onClick = onInspect,
                        variant = EzzButtonVariant.SECONDARY,
                        size = EzzButtonSize.SMALL
                    )
                    EzzButton(
                        text = "INSTALL",
                        onClick = onInstall,
                        icon = Icons.Default.Download,
                        variant = EzzButtonVariant.PRIMARY,
                        size = EzzButtonSize.SMALL
                    )
                }
            }
        }
    }
}


@Composable
private fun FilterBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF141720))
            .border(1.dp, Color(0xFF222735), RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(text = text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatDownloads(downloads: Long): String {
    return when {
        downloads >= 1_000_000 -> "${(downloads / 1_000_000.0).toInt()}M"
        downloads >= 1_000 -> "${(downloads / 1_000.0).toInt()}k"
        else -> downloads.toString()
    }
}
