package io.ezz.launcher.ui.instances

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.ui.components.CompactRuntimeBadge
import io.ezz.launcher.ui.components.InstanceArtworkIcon
import io.ezz.launcher.ui.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sort options for instances.
 */
enum class InstanceSort(val label: String) {
    RECENT("Recently Played"),
    NAME("Name (A–Z)"),
    VERSION("Version"),
    CREATED("Newest Created")
}

/**
 * Loader filter options.
 */
enum class LoaderFilter(val label: String, val loaderType: LoaderType?) {
    ALL("All Loaders", null),
    FABRIC("Fabric", LoaderType.FABRIC),
    VANILLA("Vanilla", LoaderType.VANILLA),
    OPTIFINE("OptiFine", LoaderType.OPTIFINE)
}

/**
 * Rebuilt Instance Manager Screen:
 * - Black + white foundation with high contrast and tonal hierarchy
 * - Integrated toolbar: Search, Loader filter, Favorites toggle, Sort, Import, Create
 * - Responsive cards with artwork, favorite toggle, 3-dot overflow menu, spec badges, and isolated 1-click PLAY button
 * - Empty and zero-result states
 * - Safe delete confirmation modal
 */
@Composable
fun InstancesScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val instances by viewModel.instanceRepository.instances.collectAsState()
    val runningSessions by viewModel.runningSessions.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(InstanceSort.RECENT) }
    var loaderFilter by remember { mutableStateOf(LoaderFilter.ALL) }
    var favoritesOnly by remember { mutableStateOf(false) }

    var isSortDropdownOpen by remember { mutableStateOf(false) }
    var isLoaderDropdownOpen by remember { mutableStateOf(false) }
    var instanceToDelete by remember { mutableStateOf<Instance?>(null) }

    // Filter and sort instances
    val filteredInstances = remember(instances, searchQuery, sortOption, loaderFilter, favoritesOnly) {
        var list = instances

        // Loader filter
        if (loaderFilter.loaderType != null) {
            list = list.filter { it.loaderType == loaderFilter.loaderType }
        }

        // Favorites only filter
        if (favoritesOnly) {
            list = list.filter { it.isFavorite }
        }

        // Search filter
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter { inst ->
                inst.name.lowercase().contains(q) ||
                    inst.minecraftVersion.lowercase().contains(q) ||
                    inst.loaderType.name.lowercase().contains(q)
            }
        }

        // Sorting
        when (sortOption) {
            InstanceSort.RECENT -> list.sortedWith(
                compareByDescending<Instance> { it.isFavorite }
                    .thenByDescending { it.lastPlayedAt ?: 0L }
                    .thenByDescending { it.createdAt }
            )
            InstanceSort.NAME -> list.sortedWith(
                compareByDescending<Instance> { it.isFavorite }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
            InstanceSort.VERSION -> list.sortedWith(
                compareByDescending<Instance> { it.isFavorite }
                    .thenByDescending { it.minecraftVersion }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
            InstanceSort.CREATED -> list.sortedWith(
                compareByDescending<Instance> { it.isFavorite }
                    .thenByDescending { it.createdAt }
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090A0F))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 24.dp)
        ) {
            // Header & Unified Toolbar
            InstancesHeader(
                totalCount = instances.size,
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                loaderFilter = loaderFilter,
                onLoaderFilterChange = { loaderFilter = it },
                isLoaderDropdownOpen = isLoaderDropdownOpen,
                onLoaderDropdownOpenChange = { isLoaderDropdownOpen = it },
                favoritesOnly = favoritesOnly,
                onFavoritesOnlyToggle = { favoritesOnly = !favoritesOnly },
                sortOption = sortOption,
                onSortOptionChange = { sortOption = it },
                isSortDropdownOpen = isSortDropdownOpen,
                onSortDropdownOpenChange = { isSortDropdownOpen = it },
                onImportClick = { viewModel.openImportModpack() },
                onCreateClick = { viewModel.showCreateInstanceDialog.value = true }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Main Content Area
            if (instances.isEmpty()) {
                EmptyInstancesState(
                    onCreateClick = { viewModel.showCreateInstanceDialog.value = true },
                    onImportClick = { viewModel.openImportModpack() }
                )
            } else if (filteredInstances.isEmpty()) {
                NoMatchesState(
                    searchQuery = searchQuery,
                    favoritesOnly = favoritesOnly,
                    onClearFilters = {
                        searchQuery = ""
                        loaderFilter = LoaderFilter.ALL
                        favoritesOnly = false
                    }
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 320.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = filteredInstances,
                        key = { it.id }
                    ) { instance ->
                        val isRunning = runningSessions.containsKey(instance.id)
                        InstanceCard(
                            instance = instance,
                            isRunning = isRunning,
                            onCardClick = { viewModel.openInstanceManager(instance) },
                            onPlayClick = {
                                if (isRunning) {
                                    viewModel.stopInstance(instance.id)
                                } else {
                                    viewModel.launchInstance(instance)
                                }
                            },
                            onToggleFavorite = { viewModel.toggleFavoriteInstance(instance) },
                            onOpenDetails = { viewModel.openInstanceManager(instance) },
                            onEdit = { viewModel.showEditInstanceDialog.value = instance },
                            onDuplicate = { viewModel.duplicateInstance(instance.id, "${instance.name} (Copy)") },
                            onOpenFolder = { viewModel.openInstanceFolder(instance.id) },
                            onDelete = { instanceToDelete = instance }
                        )
                    }
                }
            }
        }

        // Safe Delete Confirmation Dialog
        instanceToDelete?.let { target ->
            DeleteInstanceDialog(
                instance = target,
                onDismiss = { instanceToDelete = null },
                onConfirmDelete = {
                    viewModel.deleteInstance(target.id)
                    instanceToDelete = null
                }
            )
        }
    }
}

/**
 * Top Header & Unified Control Toolbar.
 */
@Composable
private fun InstancesHeader(
    totalCount: Int,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    loaderFilter: LoaderFilter,
    onLoaderFilterChange: (LoaderFilter) -> Unit,
    isLoaderDropdownOpen: Boolean,
    onLoaderDropdownOpenChange: (Boolean) -> Unit,
    favoritesOnly: Boolean,
    onFavoritesOnlyToggle: () -> Unit,
    sortOption: InstanceSort,
    onSortOptionChange: (InstanceSort) -> Unit,
    isSortDropdownOpen: Boolean,
    onSortDropdownOpenChange: (Boolean) -> Unit,
    onImportClick: () -> Unit,
    onCreateClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Title & Count
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Instances",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF8FAFC)
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF161923),
                border = BorderStroke(1.dp, Color(0xFF2E364A))
            ) {
                Text(
                    text = "$totalCount ${if (totalCount == 1) "installation" else "installations"}",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // Right Toolbar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Search Input
            SearchBox(
                query = searchQuery,
                onQueryChange = onSearchChange,
                modifier = Modifier.width(220.dp)
            )

            // Loader Filter Dropdown
            Box {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (loaderFilter != LoaderFilter.ALL) Color(0xFF1E2332) else Color(0xFF10131B),
                    border = BorderStroke(1.dp, if (loaderFilter != LoaderFilter.ALL) Color(0xFF8B5CF6) else Color(0xFF2E364A)),
                    modifier = Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onLoaderDropdownOpenChange(!isLoaderDropdownOpen) }
                        .pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = loaderFilter.label,
                            color = if (loaderFilter != LoaderFilter.ALL) Color(0xFFF8FAFC) else Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = isLoaderDropdownOpen,
                    onDismissRequest = { onLoaderDropdownOpenChange(false) },
                    modifier = Modifier
                        .background(Color(0xFF161923))
                        .border(1.dp, Color(0xFF2E364A), RoundedCornerShape(8.dp))
                ) {
                    LoaderFilter.values().forEach { filter ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = filter.label,
                                    color = if (loaderFilter == filter) Color(0xFF8B5CF6) else Color(0xFFE2E8F0),
                                    fontSize = 13.sp,
                                    fontWeight = if (loaderFilter == filter) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onLoaderFilterChange(filter)
                                onLoaderDropdownOpenChange(false)
                            },
                            colors = MenuDefaults.itemColors(textColor = Color(0xFFE2E8F0))
                        )
                    }
                }
            }

            // Favorites Filter Button
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (favoritesOnly) Color(0x22F43F5E) else Color(0xFF10131B),
                border = BorderStroke(1.dp, if (favoritesOnly) Color(0xFFF43F5E) else Color(0xFF2E364A)),
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onFavoritesOnlyToggle() }
                    .pointerHoverIcon(PointerIcon.Hand)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = if (favoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorites",
                        tint = if (favoritesOnly) Color(0xFFF43F5E) else Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Favorites",
                        color = if (favoritesOnly) Color(0xFFF43F5E) else Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Sort Dropdown
            Box {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF10131B),
                    border = BorderStroke(1.dp, Color(0xFF2E364A)),
                    modifier = Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSortDropdownOpenChange(!isSortDropdownOpen) }
                        .pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = sortOption.label,
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = isSortDropdownOpen,
                    onDismissRequest = { onSortDropdownOpenChange(false) },
                    modifier = Modifier
                        .background(Color(0xFF161923))
                        .border(1.dp, Color(0xFF2E364A), RoundedCornerShape(8.dp))
                ) {
                    InstanceSort.values().forEach { sort ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = sort.label,
                                    color = if (sortOption == sort) Color(0xFF8B5CF6) else Color(0xFFE2E8F0),
                                    fontSize = 13.sp,
                                    fontWeight = if (sortOption == sort) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onSortOptionChange(sort)
                                onSortDropdownOpenChange(false)
                            },
                            colors = MenuDefaults.itemColors(textColor = Color(0xFFE2E8F0))
                        )
                    }
                }
            }

            // Import Button
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF161923),
                border = BorderStroke(1.dp, Color(0xFF2E364A)),
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onImportClick() }
                    .pointerHoverIcon(PointerIcon.Hand)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Import",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Import",
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Create Instance CTA
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF8B5CF6),
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onCreateClick() }
                    .pointerHoverIcon(PointerIcon.Hand)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Instance",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "New Instance",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Modern Search Input Box with clear action.
 */
@Composable
private fun SearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF10131B),
        border = BorderStroke(1.dp, if (query.isNotEmpty()) Color(0xFF8B5CF6) else Color(0xFF2E364A)),
        modifier = modifier.height(38.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search instances...",
                        color = Color(0xFF64748B),
                        fontSize = 13.sp
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color(0xFFF8FAFC),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(Color(0xFF8B5CF6)),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .clickable { onQueryChange("") }
                        .pointerHoverIcon(PointerIcon.Hand)
                )
            }
        }
    }
}

/**
 * High-quality Instance Card:
 * - Visual artwork header with running badge, favorite button, and 3-dot menu
 * - Middle spec strip (RAM, Java, Loader, Version)
 * - Footer with last played status and isolated 1-click PLAY button
 */
@Composable
private fun InstanceCard(
    instance: Instance,
    isRunning: Boolean,
    onCardClick: () -> Unit,
    onPlayClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenDetails: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onOpenFolder: () -> Unit,
    onDelete: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var isMenuOpen by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = when {
            isRunning -> Color(0xFF10B981)
            isHovered -> Color(0xFF6366F1)
            else -> Color(0xFF1E2332)
        },
        animationSpec = tween(150)
    )

    val offsetY by animateDpAsState(
        targetValue = if (isHovered) (-2).dp else 0.dp,
        animationSpec = tween(150)
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF10131B),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .height(236.dp)
            .offset(y = offsetY)
            .clip(RoundedCornerShape(14.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onCardClick() }
            .pointerHoverIcon(PointerIcon.Hand)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Artwork Banner Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(108.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1A1F2E),
                                Color(0xFF131722)
                            )
                        )
                    )
            ) {
                // Background subtle gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0x158B5CF6), Color.Transparent),
                                radius = 240f
                            )
                        )
                )

                // Main Artwork Icon centered
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    InstanceArtworkIcon(
                        instance = instance,
                        size = 56.dp
                    )
                }

                // Top-Left Badges: Running / Loader
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                ) {
                    if (isRunning) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0x3310B981),
                            border = BorderStroke(1.dp, Color(0xFF10B981))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF10B981), CircleShape)
                                )
                                Text(
                                    text = "RUNNING",
                                    color = Color(0xFF10B981),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Loader pill
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0x55000000),
                        border = BorderStroke(1.dp, Color(0xFF2E364A))
                    ) {
                        Text(
                            text = instance.loaderType.name,
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Top-Right Actions: Favorite Toggle & 3-Dot Menu
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    // Favorite Toggle Button
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(0x66000000))
                            .clickable { onToggleFavorite() }
                            .pointerHoverIcon(PointerIcon.Hand),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (instance.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (instance.isFavorite) Color(0xFFF43F5E) else Color(0xAAFFFFFF),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // 3-Dot Overflow Menu
                    Box {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(0x66000000))
                                .clickable { isMenuOpen = true }
                                .pointerHoverIcon(PointerIcon.Hand),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Actions",
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = isMenuOpen,
                            onDismissRequest = { isMenuOpen = false },
                            modifier = Modifier
                                .background(Color(0xFF161923))
                                .border(1.dp, Color(0xFF2E364A), RoundedCornerShape(8.dp))
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isRunning) "Stop Instance" else "Play Instance", color = if (isRunning) Color(0xFFEF4444) else Color(0xFF10B981)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = if (isRunning) Color(0xFFEF4444) else Color(0xFF10B981)
                                    )
                                },
                                onClick = {
                                    isMenuOpen = false
                                    onPlayClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Open Details / Workspace", color = Color(0xFFF8FAFC)) },
                                leadingIcon = { Icon(Icons.Default.GridView, null, tint = Color(0xFF94A3B8)) },
                                onClick = {
                                    isMenuOpen = false
                                    onOpenDetails()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit Configuration", color = Color(0xFFF8FAFC)) },
                                leadingIcon = { Icon(Icons.Default.Edit, null, tint = Color(0xFF94A3B8)) },
                                onClick = {
                                    isMenuOpen = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Duplicate Instance", color = Color(0xFFF8FAFC)) },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = Color(0xFF94A3B8)) },
                                onClick = {
                                    isMenuOpen = false
                                    onDuplicate()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Open Folder", color = Color(0xFFF8FAFC)) },
                                leadingIcon = { Icon(Icons.Default.FolderOpen, null, tint = Color(0xFF94A3B8)) },
                                onClick = {
                                    isMenuOpen = false
                                    onOpenFolder()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Instance", color = Color(0xFFEF4444)) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444)) },
                                onClick = {
                                    isMenuOpen = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            // Middle Info & Specs Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = instance.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF8FAFC),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = "Minecraft ${instance.minecraftVersion}",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Specs Strip (RAM & Java)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF161923),
                        border = BorderStroke(1.dp, Color(0xFF242C3E))
                    ) {
                        Text(
                            text = "${instance.maxMemoryMb / 1024} GB RAM",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF161923),
                        border = BorderStroke(1.dp, Color(0xFF242C3E))
                    ) {
                        Text(
                            text = if (instance.javaPath.isNullOrBlank()) "Auto Java" else "Custom Java",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Footer: Last played info and isolated PLAY button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D1017))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Last played status
                    Text(
                        text = formatLastPlayed(instance.lastPlayedAt),
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    )

                    // Dedicated PLAY / STOP Button
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isRunning) Color(0xFFEF4444) else Color(0xFF8B5CF6),
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onPlayClick() }
                            .pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (isRunning) "Stop" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (isRunning) "STOP" else "PLAY",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Empty state when no instances exist yet.
 */
@Composable
private fun EmptyInstancesState(
    onCreateClick: () -> Unit,
    onImportClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 80.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(420.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF161923),
                border = BorderStroke(1.dp, Color(0xFF2E364A)),
                modifier = Modifier.size(76.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = null,
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "No Minecraft Instances Yet",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF8FAFC)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Create a clean Minecraft environment or import an existing modpack (.mrpack) from Modrinth or CurseForge.",
                fontSize = 13.sp,
                color = Color(0xFF94A3B8),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF161923),
                    border = BorderStroke(1.dp, Color(0xFF2E364A)),
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onImportClick() }
                        .pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Import Modpack",
                            color = Color(0xFFCBD5E1),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF8B5CF6),
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onCreateClick() }
                        .pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 18.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Create Instance",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * State when search or filters return 0 results.
 */
@Composable
private fun NoMatchesState(
    searchQuery: String,
    favoritesOnly: Boolean,
    onClearFilters: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 80.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(360.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF161923),
                border = BorderStroke(1.dp, Color(0xFF2E364A)),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "No matching instances found",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF8FAFC)
            )

            Spacer(modifier = Modifier.height(6.dp))

            val reason = when {
                searchQuery.isNotBlank() && favoritesOnly -> "No favorite instances matched \"$searchQuery\"."
                searchQuery.isNotBlank() -> "No instances matched \"$searchQuery\"."
                favoritesOnly -> "You haven't marked any instances as favorites yet."
                else -> "No instances match the current filters."
            }

            Text(
                text = reason,
                fontSize = 13.sp,
                color = Color(0xFF94A3B8),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF161923),
                border = BorderStroke(1.dp, Color(0xFF2E364A)),
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onClearFilters() }
                    .pointerHoverIcon(PointerIcon.Hand)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Reset All Filters",
                        color = Color(0xFFF8FAFC),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Safe Delete Instance Confirmation Modal.
 */
@Composable
private fun DeleteInstanceDialog(
    instance: Instance,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xAA000000)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF161923),
                border = BorderStroke(1.dp, Color(0xFF2E364A)),
                modifier = Modifier
                    .width(440.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0x22EF4444),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "Delete Instance?",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF8FAFC)
                            )
                            Text(
                                text = "This action cannot be undone",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Are you sure you want to delete \"${instance.name}\"?",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE2E8F0)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "All associated world saves, installed mods, configs, screenshots, and logs will be permanently deleted from your drive.",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF10131B),
                            border = BorderStroke(1.dp, Color(0xFF2E364A)),
                            modifier = Modifier
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onDismiss() }
                                .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                Text(
                                    text = "Cancel",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFEF4444),
                            modifier = Modifier
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onConfirmDelete() }
                                .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                Text(
                                    text = "Delete Permanently",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Format timestamp into human-readable relative/date string.
 */
private fun formatLastPlayed(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0L) return "Never played"
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 2 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}
