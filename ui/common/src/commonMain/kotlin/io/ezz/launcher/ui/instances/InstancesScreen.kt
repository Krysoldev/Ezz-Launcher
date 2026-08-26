package io.ezz.launcher.ui.instances

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.ui.components.EzzBadge
import io.ezz.launcher.ui.components.EzzBadgeVariant
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.EzzCard
import io.ezz.launcher.ui.components.EzzEmptyState
import io.ezz.launcher.ui.components.EzzIconButton
import io.ezz.launcher.ui.components.EzzLoaderBadge
import io.ezz.launcher.ui.components.EzzSearchField
import io.ezz.launcher.ui.components.EzzTabs
import io.ezz.launcher.ui.components.TabItem
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.AppViewModel
import io.ezz.launcher.ui.viewmodel.NavigationScreen

enum class InstanceFilter {
    ALL,
    VANILLA,
    FABRIC,
    OPTIFINE
}

@Composable
fun InstancesScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val colors = EzzTheme.colors
    val instances by viewModel.instanceRepository.instances.collectAsState()
    val selectedInstance by viewModel.selectedInstance.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(InstanceFilter.ALL) }

    val filteredInstances = remember(instances, searchQuery, selectedFilter) {
        instances.filter { inst ->
            val matchesSearch = searchQuery.isBlank() ||
                    inst.name.contains(searchQuery, ignoreCase = true) ||
                    inst.minecraftVersion.contains(searchQuery, ignoreCase = true) ||
                    inst.loaderType.name.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                InstanceFilter.ALL -> true
                InstanceFilter.VANILLA -> inst.loaderType == LoaderType.VANILLA
                InstanceFilter.FABRIC -> inst.loaderType == LoaderType.FABRIC
                InstanceFilter.OPTIFINE -> inst.loaderType == LoaderType.OPTIFINE
            }

            matchesSearch && matchesFilter
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(32.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Instances",
                        color = colors.textPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    EzzBadge(
                        text = "${instances.size} Installed",
                        variant = EzzBadgeVariant.NEUTRAL
                    )
                }
                Text(
                    text = "Manage your isolated Minecraft profiles, modpacks, and versions",
                    color = colors.textSecondary,
                    fontSize = 14.sp
                )
            }

            EzzButton(
                text = "New Instance",
                onClick = { viewModel.showCreateInstanceDialog.value = true },
                variant = EzzButtonVariant.PRIMARY,
                size = EzzButtonSize.MEDIUM,
                icon = Icons.Default.Add
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Search and Filter Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EzzSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.width(360.dp),
                placeholder = "Search instances..."
            )

            EzzTabs(
                items = listOf(
                    TabItem(InstanceFilter.ALL, "All (${instances.size})"),
                    TabItem(InstanceFilter.FABRIC, "Fabric (${instances.count { it.loaderType == LoaderType.FABRIC }})"),
                    TabItem(InstanceFilter.OPTIFINE, "OptiFine (${instances.count { it.loaderType == LoaderType.OPTIFINE }})"),
                    TabItem(InstanceFilter.VANILLA, "Vanilla (${instances.count { it.loaderType == LoaderType.VANILLA }})")
                ),
                selectedItem = selectedFilter,
                onItemSelected = { selectedFilter = it }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Grid
        if (filteredInstances.isEmpty()) {
            if (instances.isEmpty()) {
                EzzEmptyState(
                    title = "No Instances Created Yet",
                    description = "Create your first isolated Minecraft instance to start playing Vanilla, Fabric, or OptiFine.",
                    actionButtonText = "Create Instance",
                    onActionClick = { viewModel.showCreateInstanceDialog.value = true }
                )
            } else {
                EzzEmptyState(
                    title = "No Matching Instances",
                    description = "No instances match your search query '$searchQuery'.",
                    actionButtonText = "Clear Search",
                    onActionClick = { searchQuery = "" }
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 340.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredInstances, key = { it.id }) { instance ->
                    InstanceGridCard(
                        instance = instance,
                        isSelected = instance.id == selectedInstance?.id,
                        onSelect = { viewModel.selectInstance(instance) },
                        onPlay = {
                            viewModel.selectInstance(instance)
                            viewModel.launchInstance(instance)
                            viewModel.navigateTo(NavigationScreen.HOME)
                        },
                        onManageMods = {
                            viewModel.selectInstance(instance)
                            viewModel.navigateTo(NavigationScreen.MODS)
                        },
                        onEdit = { viewModel.showEditInstanceDialog.value = instance },
                        onDuplicate = { viewModel.duplicateInstance(instance.id, "${instance.name} (Copy)") },
                        onOpenFolder = { viewModel.openInstanceFolder(instance.id) },
                        onDelete = { viewModel.deleteInstance(instance.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun InstanceGridCard(
    instance: Instance,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onPlay: () -> Unit,
    onManageMods: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onOpenFolder: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = EzzTheme.colors

    EzzCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (isSelected) colors.primary else colors.border,
        backgroundColor = if (isSelected) colors.surfaceVariant else colors.cardBackground,
        onClick = onSelect
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // Top Meta Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EzzLoaderBadge(loaderType = instance.loaderType)

                    Text(
                        text = "MC ${instance.minecraftVersion}",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title and RAM
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) colors.primaryGlow else colors.surface)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = if (isSelected) colors.primary else colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = instance.name,
                            color = colors.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = colors.textMuted,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${instance.maxMemoryMb / 1024} GB RAM",
                                color = colors.textMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EzzButton(
                        text = "Play",
                        onClick = onPlay,
                        variant = EzzButtonVariant.PRIMARY,
                        size = EzzButtonSize.SMALL,
                        icon = Icons.Default.PlayArrow
                    )

                    if (instance.loaderType == LoaderType.FABRIC) {
                        Spacer(modifier = Modifier.width(8.dp))
                        EzzButton(
                            text = "Mods",
                            onClick = onManageMods,
                            variant = EzzButtonVariant.SECONDARY,
                            size = EzzButtonSize.SMALL,
                            icon = Icons.Default.Extension
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    EzzIconButton(
                        icon = Icons.Default.FolderOpen,
                        onClick = onOpenFolder,
                        contentDescription = "Open Folder",
                        tint = colors.textMuted
                    )
                    EzzIconButton(
                        icon = Icons.Default.Edit,
                        onClick = onEdit,
                        contentDescription = "Edit Instance",
                        tint = colors.textMuted
                    )
                    EzzIconButton(
                        icon = Icons.Default.ContentCopy,
                        onClick = onDuplicate,
                        contentDescription = "Duplicate Instance",
                        tint = colors.textMuted
                    )
                    EzzIconButton(
                        icon = Icons.Default.Delete,
                        onClick = onDelete,
                        contentDescription = "Delete Instance",
                        tint = colors.danger
                    )
                }
            }
        }
    }
}
