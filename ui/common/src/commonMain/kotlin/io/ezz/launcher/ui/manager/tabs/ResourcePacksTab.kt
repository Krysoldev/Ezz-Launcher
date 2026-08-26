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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LocalResourcePack
import io.ezz.launcher.core.model.modrinth.ModrinthContentType
import io.ezz.launcher.core.model.modrinth.ModrinthProjectHit
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.AppViewModel

private enum class PacksSubTab {
    INSTALLED,
    BROWSE
}

@Composable
fun ResourcePacksTab(
    instance: Instance,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val colors = EzzTheme.colors
    var subTab by remember { mutableStateOf(PacksSubTab.INSTALLED) }

    val installedPacks by viewModel.manageResourcePacks.collectAsState()
    val searchResults by viewModel.modrinthSearchResults.collectAsState()
    val isSearching by viewModel.isModrinthSearching.collectAsState()
    val searchQuery by viewModel.modrinthSearchQuery.collectAsState()
    val downloadingProject by viewModel.modrinthDownloadingProject.collectAsState()
    val downloadProgress by viewModel.modrinthDownloadProgress.collectAsState()

    var localSearch by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with SubTabs and Folder Action
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
                SubTabPill("Installed (${installedPacks.size})", subTab == PacksSubTab.INSTALLED) {
                    subTab = PacksSubTab.INSTALLED
                }
                SubTabPill("Browse Modrinth", subTab == PacksSubTab.BROWSE) {
                    subTab = PacksSubTab.BROWSE
                    viewModel.modrinthContentType.value = ModrinthContentType.RESOURCE_PACK
                    if (searchResults.isEmpty()) viewModel.searchModrinth()
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { viewModel.refreshManageData() },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Refresh")
                }

                Button(
                    onClick = {
                        val path = viewModel.pathProvider.getInstanceDirectory(instance.id).resolve(".minecraft").resolve("resourcepacks")
                        viewModel.platformBridge.openFolder(path)
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceLight, contentColor = colors.textPrimary)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Folder")
                }
            }
        }

        when (subTab) {
            PacksSubTab.INSTALLED -> {
                InstalledPacksList(
                    packs = installedPacks,
                    searchQuery = localSearch,
                    onSearchChange = { localSearch = it },
                    onToggle = { pack, enable -> viewModel.toggleManageResourcePack(pack.fileName, enable) },
                    onDelete = { pack -> viewModel.deleteManageResourcePack(pack.fileName) }
                )
            }
            PacksSubTab.BROWSE -> {
                BrowseModrinthPacksList(
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
        }
    }
}

@Composable
private fun SubTabPill(title: String, selected: Boolean, onClick: () -> Unit) {
    val colors = EzzTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) colors.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            color = if (selected) Color.Black else colors.textSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun InstalledPacksList(
    packs: List<LocalResourcePack>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onToggle: (LocalResourcePack, Boolean) -> Unit,
    onDelete: (LocalResourcePack) -> Unit
) {
    val colors = EzzTheme.colors
    val filtered = remember(packs, searchQuery) {
        packs.filter { it.name.contains(searchQuery, ignoreCase = true) || it.fileName.contains(searchQuery, ignoreCase = true) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search installed resource packs...", color = colors.textMuted, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
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

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(10.dp)).background(colors.surface).border(1.dp, colors.border, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (packs.isEmpty()) "No resource packs installed" else "No matching packs found",
                    color = colors.textMuted,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.fileName }) { pack ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.cardBackground)
                            .border(1.dp, if (pack.enabled) colors.border else colors.border.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Box(
                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(colors.surfaceLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Palette, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(text = pack.name, color = if (pack.enabled) colors.textPrimary else colors.textMuted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text(text = pack.description ?: pack.fileName, color = colors.textMuted, fontSize = 12.sp, maxLines = 1)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Switch(
                                    checked = pack.enabled,
                                    onCheckedChange = { onToggle(pack, it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.Black,
                                        checkedTrackColor = Color.White,
                                        uncheckedThumbColor = colors.textMuted,
                                        uncheckedTrackColor = colors.surfaceLight
                                    )
                                )
                                IconButton(onClick = { onDelete(pack) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colors.danger.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseModrinthPacksList(
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search Modrinth Resource Packs (e.g. Bare Bones, Faithful...)", color = colors.textMuted, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.weight(1f).height(48.dp),
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
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text("Search")
            }
        }

        AnimatedVisibility(visible = downloadingProject != null) {
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.surface).border(1.dp, colors.accent, RoundedCornerShape(8.dp)).padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Downloading $downloadingProject...", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(text = "${(downloadProgress * 100).toInt()}%", color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = colors.accent,
                        trackColor = colors.surfaceLight
                    )
                }
            }
        }

        if (results.isEmpty() && !isSearching) {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(10.dp)).background(colors.surface).border(1.dp, colors.border, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (query.isEmpty()) "Search Modrinth to discover resource packs" else "No resource packs found for '$query'",
                    color = colors.textMuted,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(results, key = { it.projectId }) { hit ->
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.cardBackground).border(1.dp, colors.border, RoundedCornerShape(10.dp)).padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(text = hit.title, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Black)
                                Text(text = hit.description, color = colors.textSecondary, fontSize = 13.sp, maxLines = 2)
                                Text(text = "by ${hit.author} • ↓ ${hit.downloads} downloads", color = colors.textMuted, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Button(
                                onClick = { onInstall(hit) },
                                enabled = downloadingProject != hit.title,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = Color.Black)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Install", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
