package io.ezz.launcher.ui.manager.tabs

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LocalMod
import io.ezz.launcher.core.model.modrinth.ModrinthContentType
import io.ezz.launcher.core.model.modrinth.ModrinthProjectHit
import io.ezz.launcher.core.model.modrinth.ModUpdateCandidate
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.AppViewModel

private enum class ModsSubTab {
    INSTALLED,
    BROWSE,
    UPDATES
}

@Composable
fun ModsTab(
    instance: Instance,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val colors = EzzTheme.colors
    var subTab by remember { mutableStateOf(ModsSubTab.INSTALLED) }

    val installedMods by viewModel.manageMods.collectAsState()
    val searchResults by viewModel.modrinthSearchResults.collectAsState()
    val isSearching by viewModel.isModrinthSearching.collectAsState()
    val searchQuery by viewModel.modrinthSearchQuery.collectAsState()
    val downloadingProject by viewModel.modrinthDownloadingProject.collectAsState()
    val downloadProgress by viewModel.modrinthDownloadProgress.collectAsState()
    val updateCandidates by viewModel.modUpdateCandidates.collectAsState()
    val isCheckingUpdates by viewModel.isCheckingModUpdates.collectAsState()

    var localSearch by remember { mutableStateOf("") }
    var localFilter by remember { mutableStateOf("ALL") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Sub-Navigation Tabs Strip & Open Folder Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SubTabButton(
                    title = "Installed (${installedMods.size})",
                    selected = subTab == ModsSubTab.INSTALLED,
                    onClick = { subTab = ModsSubTab.INSTALLED }
                )
                SubTabButton(
                    title = "Browse Modrinth",
                    selected = subTab == ModsSubTab.BROWSE,
                    onClick = {
                        subTab = ModsSubTab.BROWSE
                        viewModel.modrinthContentType.value = ModrinthContentType.MOD
                        if (searchResults.isEmpty()) viewModel.searchModrinth()
                    }
                )
                SubTabButton(
                    title = "Updates" + if (updateCandidates.isNotEmpty()) " (${updateCandidates.size})" else "",
                    selected = subTab == ModsSubTab.UPDATES,
                    badge = if (updateCandidates.isNotEmpty()) "${updateCandidates.size}" else null,
                    onClick = {
                        subTab = ModsSubTab.UPDATES
                        viewModel.checkForModUpdates()
                    }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { viewModel.refreshManageData() },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Refresh")
                }

                Button(
                    onClick = { viewModel.openModsFolder(instance.id) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceLight, contentColor = colors.textPrimary)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Open Folder", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Mods Folder")
                }
            }
        }

        // Active SubTab Content
        when (subTab) {
            ModsSubTab.INSTALLED -> {
                InstalledModsView(
                    mods = installedMods,
                    searchQuery = localSearch,
                    onSearchChange = { localSearch = it },
                    filter = localFilter,
                    onFilterChange = { localFilter = it },
                    viewModel = viewModel
                )
            }
            ModsSubTab.BROWSE -> {
                BrowseModrinthModsView(
                    instance = instance,
                    query = searchQuery,
                    onQueryChange = { viewModel.modrinthSearchQuery.value = it },
                    onSearch = { viewModel.searchModrinth(it) },
                    results = searchResults,
                    isSearching = isSearching,
                    downloadingProject = downloadingProject,
                    downloadProgress = downloadProgress,
                    onInstall = { hit -> viewModel.installModrinthProject(hit) }
                )
            }
            ModsSubTab.UPDATES -> {
                ModUpdatesView(
                    candidates = updateCandidates,
                    isChecking = isCheckingUpdates,
                    onCheck = { viewModel.checkForModUpdates() },
                    onUpdate = { cand -> viewModel.updateModFromCandidate(cand) }
                )
            }
        }
    }
}

@Composable
private fun SubTabButton(
    title: String,
    selected: Boolean,
    badge: String? = null,
    onClick: () -> Unit
) {
    val colors = EzzTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) colors.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                color = if (selected) Color.Black else colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
            if (badge != null && !selected) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.accent)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = badge, color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun InstalledModsView(
    mods: List<LocalMod>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    filter: String,
    onFilterChange: (String) -> Unit,
    viewModel: AppViewModel
) {
    val colors = EzzTheme.colors
    val filtered = remember(mods, searchQuery, filter) {
        mods.filter { mod ->
            val matchSearch = mod.name.contains(searchQuery, ignoreCase = true) ||
                    mod.fileName.contains(searchQuery, ignoreCase = true) ||
                    (mod.author?.contains(searchQuery, ignoreCase = true) == true)
            val matchFilter = when (filter) {
                "ENABLED" -> mod.enabled
                "DISABLED" -> !mod.enabled
                else -> true
            }
            matchSearch && matchFilter
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Search & Filter controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search installed mods...", color = colors.textMuted, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = colors.textMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = true
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                    .padding(4.dp)
            ) {
                FilterPill("All (${mods.size})", filter == "ALL") { onFilterChange("ALL") }
                FilterPill("Enabled (${mods.count { it.enabled }})", filter == "ENABLED") { onFilterChange("ENABLED") }
                FilterPill("Disabled (${mods.count { !it.enabled }})", filter == "DISABLED") { onFilterChange("DISABLED") }
            }
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Extension, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(36.dp))
                    Text(
                        text = if (mods.isEmpty()) "No mods installed yet" else "No matching mods found",
                        color = colors.textSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (mods.isEmpty()) "Switch to 'Browse Modrinth' or drop .jar files into the mods folder" else "Try clearing your search query",
                        color = colors.textMuted,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.fileName }) { mod ->
                    ModCard(
                        mod = mod,
                        onToggle = { enable -> viewModel.toggleManageMod(mod.fileName, enable) },
                        onDelete = { viewModel.deleteManageMod(mod.fileName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = EzzTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) colors.surfaceLight else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (selected) colors.textPrimary else colors.textMuted,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun ModCard(
    mod: LocalMod,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val colors = EzzTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.cardBackground)
            .border(1.dp, if (mod.enabled) colors.border else colors.border.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Extension, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = mod.name,
                            color = if (mod.enabled) colors.textPrimary else colors.textMuted,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.surfaceLight)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "v${mod.version}",
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        if (mod.author != null) {
                            Text(
                                text = "by ${mod.author}",
                                color = colors.textMuted,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Text(
                        text = mod.description ?: mod.fileName,
                        color = colors.textMuted,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(
                    checked = mod.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = Color.White,
                        uncheckedThumbColor = colors.textMuted,
                        uncheckedTrackColor = colors.surfaceLight
                    )
                )

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colors.danger.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun BrowseModrinthModsView(
    instance: Instance,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    results: List<ModrinthProjectHit>,
    isSearching: Boolean,
    downloadingProject: String?,
    downloadProgress: Float,
    onInstall: (ModrinthProjectHit) -> Unit
) {
    val colors = EzzTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Search Input
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search Modrinth (e.g. Sodium, Lithium, Iris...)", color = colors.textMuted, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp)) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = true
            )

            Button(
                onClick = { onSearch(query) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
            ) {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.Black)
                } else {
                    Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text("Search")
            }
        }

        // Active Download Banner
        AnimatedVisibility(visible = downloadingProject != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.accent, RoundedCornerShape(8.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Downloading $downloadingProject...", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(text = "${(downloadProgress * 100).toInt()}%", color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = colors.accent,
                        trackColor = colors.surfaceLight,
                    )
                }
            }
        }

        // Results List
        if (results.isEmpty() && !isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (query.isEmpty()) "Search Modrinth to discover mods for Minecraft ${instance.minecraftVersion}" else "No mods found for '$query'",
                    color = colors.textMuted,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(results, key = { it.projectId }) { hit ->
                    ModrinthCard(
                        hit = hit,
                        isDownloading = downloadingProject == hit.title,
                        onInstall = { onInstall(hit) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModrinthCard(
    hit: ModrinthProjectHit,
    isDownloading: Boolean,
    onInstall: () -> Unit
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = hit.title,
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "by ${hit.author}",
                        color = colors.textMuted,
                        fontSize = 12.sp
                    )
                }

                Text(
                    text = hit.description,
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 2
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "↓ ${formatNumber(hit.downloads)} downloads",
                        color = colors.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    hit.categories.take(3).forEach { cat ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.surfaceLight)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(text = cat, color = colors.textMuted, fontSize = 10.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = onInstall,
                enabled = !isDownloading,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = Color.Black)
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.Black)
                } else {
                    Icon(Icons.Default.Download, contentDescription = "Install", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Install", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ModUpdatesView(
    candidates: List<ModUpdateCandidate>,
    isChecking: Boolean,
    onCheck: () -> Unit,
    onUpdate: (ModUpdateCandidate) -> Unit
) {
    val colors = EzzTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (candidates.isEmpty()) "All installed mods are up to date" else "${candidates.size} mod update(s) available",
                color = if (candidates.isNotEmpty()) colors.accent else colors.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = onCheck,
                enabled = !isChecking,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceLight, contentColor = colors.textPrimary)
            ) {
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.textPrimary)
                } else {
                    Icon(Icons.Default.SystemUpdate, contentDescription = "Check", modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text("Check for Updates")
            }
        }

        if (candidates.isEmpty() && !isChecking) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(36.dp))
                    Text("Everything is up to date", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("No newer compatible versions were found on Modrinth", color = colors.textMuted, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(candidates, key = { it.localMod.id }) { candidate ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.cardBackground)
                            .border(1.dp, colors.accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = candidate.projectTitle,
                                    color = colors.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(text = "Installed: v${candidate.localMod.version}", color = colors.textMuted, fontSize = 12.sp)
                                    Text(text = "→", color = colors.accent, fontSize = 12.sp)
                                    Text(text = "Latest: v${candidate.latestVersion.versionNumber}", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = { onUpdate(candidate) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = Color.Black)
                            ) {
                                Icon(Icons.Default.Update, contentDescription = "Update", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Update", fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatNumber(num: Long): String {
    return when {
        num >= 1_000_000 -> String.format("%.1fM", num / 1_000_000.0)
        num >= 1_000 -> String.format("%.1fK", num / 1_000.0)
        else -> num.toString()
    }
}
