package io.ezz.launcher.ui.instance.manager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.ui.instance.dialogs.InstanceDeleteDialog
import io.ezz.launcher.ui.instance.dialogs.InstanceRenameDialog
import io.ezz.launcher.ui.instance.model.InstanceFilterChip
import io.ezz.launcher.ui.instance.model.InstanceSortOrder
import io.ezz.launcher.ui.instance.model.InstanceViewMode
import io.ezz.launcher.ui.viewmodel.AppViewModel
import io.ezz.launcher.ui.viewmodel.NavigationScreen

@Composable
fun InstanceManagerScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val instances by viewModel.instances.collectAsState()
    val runningSessions by viewModel.runningSessions.collectAsState()
    val launchProgress by viewModel.launchProgressState.collectAsState()
    val activeLaunchId = launchProgress?.instanceId

    val queueState by viewModel.contentInstallationManager.queueState.collectAsState()
    val focusedInstallItem by viewModel.contentInstallationManager.focusedItem.collectAsState()
    val activeLocalImport by viewModel.activeLocalImportRequest.collectAsState()

    // UI Controls State
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(InstanceFilterChip.ALL) }
    var selectedSort by remember { mutableStateOf(InstanceSortOrder.RECENT) }
    var viewMode by remember { mutableStateOf(InstanceViewMode.CINEMATIC) }

    // Dialog States
    var instanceToDelete by remember { mutableStateOf<Instance?>(null) }
    var instanceToRename by remember { mutableStateOf<Instance?>(null) }

    // Telemetry aggregations
    val runningCount = runningSessions.size
    val totalPlaytimeSeconds = instances.sumOf { it.totalPlayTimeSeconds }
    val totalPlaytimeHours = totalPlaytimeSeconds / 3600

    // Filter Pipeline
    val filteredInstances = remember(instances, searchQuery, selectedFilter, runningSessions) {
        instances.filter { inst ->
            // Search matching
            val queryMatches = if (searchQuery.isBlank()) {
                true
            } else {
                inst.name.contains(searchQuery, ignoreCase = true) ||
                    inst.minecraftVersion.contains(searchQuery, ignoreCase = true) ||
                    inst.loaderType.name.contains(searchQuery, ignoreCase = true)
            }

            // Filter chip matching
            val filterMatches = when (selectedFilter) {
                InstanceFilterChip.ALL -> true
                InstanceFilterChip.FAVORITES -> inst.isFavorite
                InstanceFilterChip.RUNNING -> runningSessions.containsKey(inst.id)
                InstanceFilterChip.FABRIC -> inst.loaderType == LoaderType.FABRIC
                InstanceFilterChip.OPTIFINE -> inst.loaderType == LoaderType.OPTIFINE
                InstanceFilterChip.VANILLA -> inst.loaderType == LoaderType.VANILLA
            }

            queryMatches && filterMatches
        }
    }

    // Sorting Pipeline
    val sortedInstances = remember(filteredInstances, selectedSort) {
        when (selectedSort) {
            InstanceSortOrder.RECENT -> filteredInstances.sortedWith(
                compareByDescending<Instance> { it.isFavorite }
                    .thenByDescending { it.lastPlayedAt ?: 0L }
            )
            InstanceSortOrder.NAME -> filteredInstances.sortedWith(
                compareByDescending<Instance> { it.isFavorite }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
            InstanceSortOrder.VERSION -> filteredInstances.sortedWith(
                compareByDescending<Instance> { it.isFavorite }
                    .thenByDescending { it.minecraftVersion }
            )
            InstanceSortOrder.PLAYTIME -> filteredInstances.sortedWith(
                compareByDescending<Instance> { it.isFavorite }
                    .thenByDescending { it.totalPlayTimeSeconds }
            )
            InstanceSortOrder.CREATED -> filteredInstances.sortedWith(
                compareByDescending<Instance> { it.isFavorite }
                    .thenByDescending { it.createdAt }
            )
        }
    }

    // Callbacks
    val onOpenWorkspace: (Instance) -> Unit = { instance ->
        viewModel.selectInstance(instance)
        viewModel.navigateTo(NavigationScreen.INSTANCE_MANAGER)
    }

    val onPlayClick: (Instance) -> Unit = { instance ->
        if (runningSessions.containsKey(instance.id)) {
            viewModel.stopInstance(instance.id)
        } else {
            viewModel.launchInstance(instance)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07080A))
    ) {
        // TOP FLEET HEADER
        InstanceFleetHeader(
            totalInstances = instances.size,
            runningCount = runningCount,
            totalPlaytimeHours = totalPlaytimeHours,
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it },
            selectedFilter = selectedFilter,
            onFilterSelect = { selectedFilter = it },
            selectedSort = selectedSort,
            onSortSelect = { selectedSort = it },
            viewMode = viewMode,
            onViewModeChange = { viewMode = it },
            onCreateInstance = { viewModel.showCreateInstanceDialog.value = true },
            onImportModpack = { viewModel.openImportModpack() },
            activeDownloadsCount = queueState.totalActiveCount,
            onOpenActivityDrawer = { viewModel.contentInstallationManager.openDrawer() }
        )

        // MAIN CONTENT AREA
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                instances.isEmpty() -> {
                    InstanceFleetEmptyState(
                        isFiltered = false,
                        onCreateInstance = { viewModel.showCreateInstanceDialog.value = true },
                        onImportModpack = { viewModel.openImportModpack() },
                        onClearFilters = {
                            searchQuery = ""
                            selectedFilter = InstanceFilterChip.ALL
                        }
                    )
                }

                sortedInstances.isEmpty() -> {
                    InstanceFleetEmptyState(
                        isFiltered = true,
                        onCreateInstance = { viewModel.showCreateInstanceDialog.value = true },
                        onImportModpack = { viewModel.openImportModpack() },
                        onClearFilters = {
                            searchQuery = ""
                            selectedFilter = InstanceFilterChip.ALL
                        }
                    )
                }

                viewMode == InstanceViewMode.CINEMATIC -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 290.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(sortedInstances, key = { it.id }) { instance ->
                            InstanceCard(
                                instance = instance,
                                isRunning = runningSessions.containsKey(instance.id),
                                isLaunching = activeLaunchId == instance.id,
                                onCardClick = { onOpenWorkspace(instance) },
                                onPlayClick = { onPlayClick(instance) },
                                onFavoriteToggle = { viewModel.toggleFavoriteInstance(instance) },
                                onOpenFolder = { viewModel.openInstanceFolder(instance.id) },
                                onDuplicate = {
                                    viewModel.duplicateInstance(instance.id, "${instance.name} (Copy)")
                                },
                                onExport = {
                                    // Trigger export flow
                                },
                                onRename = { instanceToRename = instance },
                                onRepair = { viewModel.repairInstance(instance) },
                                onDelete = { instanceToDelete = instance }
                            )
                        }
                    }
                }

                viewMode == InstanceViewMode.COMPACT -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 250.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(sortedInstances, key = { it.id }) { instance ->
                            InstanceCardCompact(
                                instance = instance,
                                isRunning = runningSessions.containsKey(instance.id),
                                isLaunching = activeLaunchId == instance.id,
                                onCardClick = { onOpenWorkspace(instance) },
                                onPlayClick = { onPlayClick(instance) },
                                onFavoriteToggle = { viewModel.toggleFavoriteInstance(instance) },
                                onOpenFolder = { viewModel.openInstanceFolder(instance.id) },
                                onDuplicate = {
                                    viewModel.duplicateInstance(instance.id, "${instance.name} (Copy)")
                                },
                                onExport = {
                                    // Trigger export flow
                                },
                                onRename = { instanceToRename = instance },
                                onRepair = { viewModel.repairInstance(instance) },
                                onDelete = { instanceToDelete = instance }
                            )
                        }
                    }
                }

                viewMode == InstanceViewMode.LIST -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // List Table Header
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "INSTANCE",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(2.5f)
                                )
                                Text(
                                    text = "MODLOADER",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1.5f)
                                )
                                Text(
                                    text = "PLAYTIME",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1.2f)
                                )
                                Text(
                                    text = "LAST PLAYED",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1.2f)
                                )
                                Text(
                                    text = "ACTIONS",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1.6f)
                                )
                            }
                        }

                        items(sortedInstances, key = { it.id }) { instance ->
                            InstanceRow(
                                instance = instance,
                                isRunning = runningSessions.containsKey(instance.id),
                                isLaunching = activeLaunchId == instance.id,
                                onRowClick = { onOpenWorkspace(instance) },
                                onPlayClick = { onPlayClick(instance) },
                                onFavoriteToggle = { viewModel.toggleFavoriteInstance(instance) },
                                onOpenFolder = { viewModel.openInstanceFolder(instance.id) },
                                onDuplicate = {
                                    viewModel.duplicateInstance(instance.id, "${instance.name} (Copy)")
                                },
                                onExport = {
                                    // Trigger export flow
                                },
                                onRename = { instanceToRename = instance },
                                onRepair = { viewModel.repairInstance(instance) },
                                onDelete = { instanceToDelete = instance }
                            )
                        }
                    }
                }
            }
        }
    }

    // MODALS & DIALOGS
    instanceToDelete?.let { inst ->
        InstanceDeleteDialog(
            instance = inst,
            onDismiss = { instanceToDelete = null },
            onConfirmDelete = {
                viewModel.deleteInstance(inst.id)
                instanceToDelete = null
            }
        )
    }

    instanceToRename?.let { inst ->
        InstanceRenameDialog(
            instance = inst,
            onDismiss = { instanceToRename = null },
            onConfirmRename = { newName ->
                viewModel.updateInstance(inst.copy(name = newName))
                instanceToRename = null
            }
        )
    }

    // INSTALLATION OVERLAYS & DRAWER (V2)
    val focused = focusedInstallItem
    if (focused != null) {
        io.ezz.launcher.ui.instance.installation.ui.ContentInstallationProgressModal(
            item = focused,
            imageLoader = viewModel.imageLoader,
            onCancel = { viewModel.contentInstallationManager.cancelInstallation(focused.id) },
            onRetry = {
                val inst = instances.find { it.id == focused.instanceId }
                val mod = focused.mod
                if (inst != null && mod != null) {
                    viewModel.contentInstallationManager.installCurseForgeMod(inst, mod, focused.chosenFile)
                }
            },
            onDismiss = { viewModel.contentInstallationManager.setFocusedItem(null) }
        )
    }

    val importRequest = activeLocalImport
    if (importRequest != null) {
        io.ezz.launcher.ui.instance.installation.ui.LocalImportValidationDialog(
            file = importRequest.file,
            contentType = importRequest.contentType,
            instance = importRequest.instance,
            targetDirectory = importRequest.targetDirectory,
            onImportComplete = {
                viewModel.refreshManageData()
                viewModel.refreshMods(importRequest.instance.id)
            },
            onDismiss = { viewModel.activeLocalImportRequest.value = null }
        )
    }

    io.ezz.launcher.ui.instance.installation.ui.InstallationActivityDrawer(
        queueState = queueState,
        imageLoader = viewModel.imageLoader,
        onClose = { viewModel.contentInstallationManager.closeDrawer() },
        onSelectItem = { item -> viewModel.contentInstallationManager.setFocusedItem(item) },
        onCancelItem = { id -> viewModel.contentInstallationManager.cancelInstallation(id) },
        onClearCompleted = { viewModel.contentInstallationManager.clearCompleted() }
    )
}
