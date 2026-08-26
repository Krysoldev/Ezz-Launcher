package io.ezz.launcher.ui.instances

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
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
import io.ezz.launcher.ui.components.EzzEmptyState
import io.ezz.launcher.ui.components.EzzIconButton
import io.ezz.launcher.ui.components.EzzLoaderBadge
import io.ezz.launcher.ui.components.EzzSearchField
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

    val tickerTime by viewModel.tickerTime.collectAsState()
    val runningSessions by viewModel.runningSessions.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .padding(24.dp)
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
                        text = "INSTANCES",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    EzzBadge(
                        text = "${instances.size}",
                        variant = EzzBadgeVariant.NEUTRAL
                    )
                }
                Text(
                    text = "Isolated Minecraft environments, custom profiles, and mod configurations",
                    color = Color(0xFF888888),
                    fontSize = 12.sp
                )
            }

            EzzButton(
                text = "Create Instance",
                onClick = { viewModel.showCreateInstanceDialog.value = true },
                variant = EzzButtonVariant.PRIMARY,
                size = EzzButtonSize.MEDIUM,
                icon = Icons.Default.Add
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Search & Filters Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(320.dp)) {
                EzzSearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "Search instances, versions..."
                )
            }

            // Filter Chips
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF141414))
                    .border(1.dp, Color(0xFF242424), RoundedCornerShape(6.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf(
                    Pair(InstanceFilter.ALL, "All (${instances.size})"),
                    Pair(InstanceFilter.FABRIC, "Fabric (${instances.count { it.loaderType == LoaderType.FABRIC }})"),
                    Pair(InstanceFilter.OPTIFINE, "OptiFine (${instances.count { it.loaderType == LoaderType.OPTIFINE }})"),
                    Pair(InstanceFilter.VANILLA, "Vanilla (${instances.count { it.loaderType == LoaderType.VANILLA }})")
                ).forEach { (filter, label) ->
                    val isSelected = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) Color(0xFF242424) else Color.Transparent)
                            .clickable { selectedFilter = filter }
                            .padding(horizontal = 11.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else Color(0xFF888888),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Responsive Grid
        if (filteredInstances.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F0F0F))
                    .border(1.dp, Color(0xFF202020), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                EzzEmptyState(
                    title = if (instances.isEmpty()) "No Instances Created Yet" else "No Matching Instances",
                    description = if (instances.isEmpty()) "Create your first isolated Minecraft instance to start playing." else "No instance matched '$searchQuery'.",
                    actionLabel = if (instances.isEmpty()) "Create Instance" else "Clear Search",
                    onAction = {
                        if (instances.isEmpty()) viewModel.showCreateInstanceDialog.value = true
                        else searchQuery = ""
                    }
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 320.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredInstances, key = { it.id }) { instance ->
                    val isInstRunning = viewModel.isInstanceRunning(instance.id)
                    val instRuntime = if (isInstRunning) viewModel.getInstanceRuntimeFormatted(instance.id) else null

                    InstanceGridCard(
                        instance = instance,
                        isSelected = instance.id == selectedInstance?.id,
                        isRunning = isInstRunning,
                        runtimeFormatted = instRuntime,
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
    isRunning: Boolean = false,
    runtimeFormatted: String? = null,
    onSelect: () -> Unit,
    onPlay: () -> Unit,
    onManageMods: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onOpenFolder: () -> Unit,
    onDelete: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.01f else 1.0f,
        animationSpec = tween(120)
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) Color(0xFF181818) else Color(0xFF101010))
            .border(
                1.dp,
                if (isSelected) Color.White else if (isHovered) Color(0xFF383838) else Color(0xFF222222),
                RoundedCornerShape(6.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect
            )
            .padding(18.dp)
    ) {
        Column {
            // Header Row: Loader + MC Version
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EzzLoaderBadge(loaderType = instance.loaderType)

                Text(
                    text = "MC ${instance.minecraftVersion}",
                    color = Color(0xFFA0A0A0),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Body: Icon + Title + RAM
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) Color(0xFF242424) else Color(0xFF161616)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else Color(0xFF888888),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = instance.name,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = Color(0xFF666666),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${instance.maxMemoryMb / 1024} GB RAM",
                            color = Color(0xFF777777),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isRunning) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF161616))
                                .border(1.dp, Color(0xFF10B981).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = runtimeFormatted ?: "00:00:00",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        EzzButton(
                            text = "Launch",
                            icon = Icons.Default.PlayArrow,
                            onClick = onPlay,
                            variant = if (isSelected) EzzButtonVariant.PRIMARY else EzzButtonVariant.SECONDARY,
                            size = EzzButtonSize.SMALL
                        )
                    }

                    if (instance.loaderType == LoaderType.FABRIC) {
                        EzzButton(
                            text = "Mods",
                            icon = Icons.Default.Extension,
                            onClick = onManageMods,
                            variant = EzzButtonVariant.SECONDARY,
                            size = EzzButtonSize.SMALL
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    EzzIconButton(
                        icon = Icons.Default.FolderOpen,
                        onClick = onOpenFolder,
                        contentDescription = "Open Directory",
                        size = EzzButtonSize.SMALL,
                        variant = EzzButtonVariant.GHOST
                    )
                    EzzIconButton(
                        icon = Icons.Default.Edit,
                        onClick = onEdit,
                        contentDescription = "Edit Configuration",
                        size = EzzButtonSize.SMALL,
                        variant = EzzButtonVariant.GHOST
                    )
                    EzzIconButton(
                        icon = Icons.Default.ContentCopy,
                        onClick = onDuplicate,
                        contentDescription = "Duplicate Profile",
                        size = EzzButtonSize.SMALL,
                        variant = EzzButtonVariant.GHOST
                    )
                    EzzIconButton(
                        icon = Icons.Default.Delete,
                        onClick = onDelete,
                        contentDescription = "Delete Profile",
                        size = EzzButtonSize.SMALL,
                        variant = EzzButtonVariant.DANGER
                    )
                }
            }
        }
    }
}
