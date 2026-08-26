package io.ezz.launcher.ui.manager.tabs

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LocalResourcePack
import io.ezz.launcher.core.model.modrinth.ModrinthBrowseState
import io.ezz.launcher.core.model.modrinth.ModrinthProjectHit
import io.ezz.launcher.ui.components.ModrinthAsyncImage
import io.ezz.launcher.ui.components.PaginationBar
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.AppViewModel

private enum class PacksSubTab(val title: String) {
    INSTALLED("Installed"),
    BROWSE("Browse Modrinth")
}

@Composable
fun ResourcePacksTab(
    instance: Instance,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    var subTab by remember { mutableStateOf(PacksSubTab.INSTALLED) }

    val installedPacks by viewModel.manageResourcePacks.collectAsState()
    val browseState by viewModel.resourcePacksBrowseState.collectAsState()
    val downloadingProject by viewModel.modrinthDownloadingProject.collectAsState()
    val downloadProgress by viewModel.modrinthDownloadProgress.collectAsState()

    var localSearch by remember { mutableStateOf("") }
    var localFilter by remember { mutableStateOf("ALL") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Sub-Navigation Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF141414))
                    .border(1.dp, Color(0xFF222222), RoundedCornerShape(8.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PacksSubTab.values().forEach { tab ->
                    val isActive = subTab == tab
                    val badgeCount = if (tab == PacksSubTab.INSTALLED) installedPacks.size else null

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isActive) Color(0xFF222222) else Color.Transparent)
                            .clickable {
                                subTab = tab
                                if (tab == PacksSubTab.BROWSE && browseState.items.isEmpty()) {
                                    viewModel.searchResourcePacks()
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = tab.title,
                                color = if (isActive) Color.White else Color(0xFF888888),
                                fontSize = 13.sp,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                            )
                            if (badgeCount != null && badgeCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color(0xFF333333))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = badgeCount.toString(),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick Actions
            if (subTab == PacksSubTab.INSTALLED) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1C1C1C))
                        .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(6.dp))
                        .clickable { viewModel.openInstanceFolder(instance.id) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text("Open Folder", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // Active Download Banner
        if (downloadingProject != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF141414))
                    .border(1.dp, Color(0xFF2E7D32).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Installing $downloadingProject...",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${(downloadProgress * 100).toInt()}%",
                            color = Color(0xFF4CAF50),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFF4CAF50),
                        trackColor = Color(0xFF222222)
                    )
                }
            }
        }

        // SubTab Content
        when (subTab) {
            PacksSubTab.INSTALLED -> {
                InstalledPacksView(
                    installedPacks = installedPacks,
                    localSearch = localSearch,
                    onSearchChange = { localSearch = it },
                    localFilter = localFilter,
                    onFilterChange = { localFilter = it },
                    onTogglePack = { pack -> viewModel.toggleManageResourcePack(pack.fileName, !pack.enabled) },
                    onDeletePack = { pack -> viewModel.deleteManageResourcePack(pack.fileName) },
                    onBrowseClick = {
                        subTab = PacksSubTab.BROWSE
                        if (browseState.items.isEmpty()) viewModel.searchResourcePacks()
                    }
                )
            }
            PacksSubTab.BROWSE -> {
                BrowseResourcePacksView(
                    instance = instance,
                    viewModel = viewModel,
                    browseState = browseState,
                    onInstall = { hit -> viewModel.installModrinthProject(hit) }
                )
            }
        }
    }
}

@Composable
private fun InstalledPacksView(
    installedPacks: List<LocalResourcePack>,
    localSearch: String,
    onSearchChange: (String) -> Unit,
    localFilter: String,
    onFilterChange: (String) -> Unit,
    onTogglePack: (LocalResourcePack) -> Unit,
    onDeletePack: (LocalResourcePack) -> Unit,
    onBrowseClick: () -> Unit
) {
    val filtered = installedPacks.filter { pack ->
        val matchesSearch = localSearch.isBlank() ||
                pack.name.contains(localSearch, ignoreCase = true) ||
                pack.fileName.contains(localSearch, ignoreCase = true)
        val matchesFilter = when (localFilter) {
            "ENABLED" -> pack.enabled
            "DISABLED" -> !pack.enabled
            else -> true
        }
        matchesSearch && matchesFilter
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Search Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = localSearch,
                onValueChange = onSearchChange,
                placeholder = { Text("Filter installed resource packs...", color = Color(0xFF666666), fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF666666), modifier = Modifier.size(16.dp)) },
                trailingIcon = {
                    if (localSearch.isNotBlank()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF666666), modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF141414)),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF141414),
                    unfocusedContainerColor = Color(0xFF141414),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true
            )

            // Status Filter Chips
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF141414))
                    .border(1.dp, Color(0xFF222222), RoundedCornerShape(8.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("ALL" to "All", "ENABLED" to "Enabled", "DISABLED" to "Disabled").forEach { (key, label) ->
                    val isSelected = localFilter == key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Color(0xFF242424) else Color.Transparent)
                            .clickable { onFilterChange(key) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else Color(0xFF888888),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF121212))
                    .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = Color(0xFF444444),
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = if (installedPacks.isEmpty()) "No resource packs installed in this instance." else "No resource packs matched your filter.",
                        color = Color(0xFF888888),
                        fontSize = 14.sp
                    )
                    if (installedPacks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .clickable { onBrowseClick() }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text("Browse Resource Packs", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.fileName }) { pack ->
                    InstalledPackRow(
                        pack = pack,
                        onToggle = { onTogglePack(pack) },
                        onDelete = { onDeletePack(pack) }
                    )
                }
            }
        }
    }
}

@Composable
private fun InstalledPackRow(
    pack: LocalResourcePack,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF141414))
            .border(1.dp, Color(0xFF222222), RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
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
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (pack.enabled) Color(0xFF1E1E1E) else Color(0xFF161616))
                        .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = if (pack.enabled) Color(0xFF2196F3) else Color(0xFF666666),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = pack.name,
                        color = if (pack.enabled) Color.White else Color(0xFF777777),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    val desc = pack.description
                    Text(
                        text = if (!desc.isNullOrBlank()) desc else pack.fileName,
                        color = Color(0xFF666666),
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Switch(
                    checked = pack.enabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF1976D2),
                        uncheckedThumbColor = Color(0xFF888888),
                        uncheckedTrackColor = Color(0xFF242424),
                        uncheckedBorderColor = Color(0xFF333333)
                    )
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Pack",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowseResourcePacksView(
    instance: Instance,
    viewModel: AppViewModel,
    browseState: ModrinthBrowseState,
    onInstall: (ModrinthProjectHit) -> Unit
) {
    var searchQuery by remember(browseState.searchQuery) { mutableStateOf(browseState.searchQuery) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Search & Sort Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.searchResourcePacks(query = it, debounceMs = 350L)
                },
                placeholder = { Text("Search Modrinth Resource Packs (e.g. Faithful, Bare Bones, Fresh Animations)...", color = Color(0xFF666666), fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF666666), modifier = Modifier.size(16.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            viewModel.searchResourcePacks(query = "")
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF666666), modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF141414)),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF141414),
                    unfocusedContainerColor = Color(0xFF141414),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true
            )

            // Sort Options
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF141414))
                    .border(1.dp, Color(0xFF222222), RoundedCornerShape(8.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "relevance" to "Relevance",
                    "downloads" to "Downloads",
                    "newest" to "Newest",
                    "updated" to "Updated"
                ).forEach { (sortKey, sortLabel) ->
                    val isSelected = browseState.selectedSort == sortKey
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Color(0xFF242424) else Color.Transparent)
                            .clickable { viewModel.searchResourcePacks(sort = sortKey) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = sortLabel,
                            color = if (isSelected) Color.White else Color(0xFF888888),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Active Version Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Filtering Resource Packs for:", color = Color(0xFF666666), fontSize = 12.sp)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1976D2).copy(alpha = 0.15f))
                    .border(1.dp, Color(0xFF1976D2).copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("MC ${instance.minecraftVersion}", color = Color(0xFF1976D2), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
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
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF141414))
                    .border(1.dp, Color(0xFF222222), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(36.dp))
                    Text(browseState.error ?: "Error fetching resource packs", color = Color(0xFF888888), fontSize = 13.sp)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF222222))
                            .clickable { viewModel.searchResourcePacks() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("Retry", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (browseState.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF141414))
                    .border(1.dp, Color(0xFF222222), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("No resource packs found matching your query.", color = Color(0xFF888888), fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(browseState.items, key = { it.projectId }) { hit ->
                    ResourcePackBrowseCard(
                        hit = hit,
                        viewModel = viewModel,
                        onInstall = { onInstall(hit) }
                    )
                }

                item {
                    PaginationBar(
                        currentPage = browseState.page,
                        totalPages = browseState.totalPages,
                        totalHits = browseState.totalHits,
                        isLoading = browseState.isLoading,
                        onPrevious = { viewModel.setResourcePacksPage(browseState.page - 1) },
                        onNext = { viewModel.setResourcePacksPage(browseState.page + 1) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ResourcePackBrowseCard(
    hit: ModrinthProjectHit,
    viewModel: AppViewModel,
    onInstall: () -> Unit
) {
    val isInstalled = viewModel.isResourcePackInstalled(hit)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF141414))
            .border(1.dp, Color(0xFF222222), RoundedCornerShape(10.dp))
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
                ModrinthAsyncImage(
                    url = hit.iconUrl,
                    imageLoader = viewModel.imageLoader,
                    modifier = Modifier.size(54.dp),
                    placeholderIcon = Icons.Default.Palette,
                    contentScale = ContentScale.Fit
                )

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
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (hit.author.isNotBlank()) {
                            Text(
                                text = "by ${hit.author}",
                                color = Color(0xFF777777),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Text(
                        text = hit.description,
                        color = Color(0xFFAAAAAA),
                        fontSize = 13.sp,
                        maxLines = 2
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = "${formatDownloads(hit.downloads)} downloads",
                            color = Color(0xFF777777),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )

                        hit.categories.take(3).forEach { cat ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF202020))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = cat,
                                    color = Color(0xFF888888),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Action Button
            if (isInstalled) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E281E))
                        .border(1.dp, Color(0xFF2E7D32).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "INSTALLED",
                            color = Color(0xFF4CAF50),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White)
                        .clickable { onInstall() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "INSTALL",
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun formatDownloads(downloads: Long): String {
    return when {
        downloads >= 1_000_000 -> "${(downloads / 1_000_000.0).toInt()}M"
        downloads >= 1_000 -> "${(downloads / 1_000.0).toInt()}k"
        else -> downloads.toString()
    }
}
