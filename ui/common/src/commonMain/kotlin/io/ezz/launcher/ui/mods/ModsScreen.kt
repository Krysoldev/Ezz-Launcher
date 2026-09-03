package io.ezz.launcher.ui.mods

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import io.ezz.launcher.core.model.instance.ModMetadata
import io.ezz.launcher.ui.components.EzzBadge
import io.ezz.launcher.ui.components.EzzBadgeVariant
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.EzzCard
import io.ezz.launcher.ui.components.EzzEmptyState
import io.ezz.launcher.ui.components.EzzIconButton
import io.ezz.launcher.ui.components.EzzSearchField
import io.ezz.launcher.ui.components.EzzToggle
import io.ezz.launcher.core.model.instance.InstanceManagerTab
import io.ezz.launcher.ui.viewmodel.AppViewModel
import io.ezz.launcher.ui.viewmodel.NavigationScreen

enum class ModFilter {
    ALL,
    ENABLED,
    DISABLED
}

@Composable
fun ModsScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val instances by viewModel.instanceRepository.instances.collectAsState()
    val selectedInstance by viewModel.selectedInstance.collectAsState()
    val installedMods by viewModel.installedMods.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(ModFilter.ALL) }
    var isInstanceDropdownOpen by remember { mutableStateOf(false) }

    val filteredMods = remember(installedMods, searchQuery, selectedFilter) {
        installedMods.filter { mod ->
            val matchesSearch = searchQuery.isBlank() ||
                    mod.name.contains(searchQuery, ignoreCase = true) ||
                    mod.description?.contains(searchQuery, ignoreCase = true) == true ||
                    mod.fileName.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                ModFilter.ALL -> true
                ModFilter.ENABLED -> mod.enabled
                ModFilter.DISABLED -> !mod.enabled
            }

            matchesSearch && matchesFilter
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07080A))
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Card with Instance Switcher
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF101318))
                .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(10.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "MODS",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "· ${installedMods.size} Installed",
                            color = Color(0xFF64748B),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (selectedInstance != null) "Managing mods for ${selectedInstance!!.name} (${selectedInstance!!.minecraftVersion})" else "Select an instance to manage mods",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Instance Switcher
                    Box {
                        val interactionSource = remember { MutableInteractionSource() }
                        val isHovered by interactionSource.collectIsHoveredAsState()

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isHovered) Color(0xFF181C28) else Color(0xFF141720))
                                .border(1.dp, if (isHovered) Color(0xFF323A4E) else Color(0xFF222735), RoundedCornerShape(8.dp))
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = { isInstanceDropdownOpen = true }
                                )
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = selectedInstance?.name ?: "Select Instance",
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isInstanceDropdownOpen,
                            onDismissRequest = { isInstanceDropdownOpen = false },
                            modifier = Modifier
                                .background(Color(0xFF141720))
                                .border(1.dp, Color(0xFF222735), RoundedCornerShape(6.dp))
                        ) {
                            instances.forEach { inst ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = inst.name,
                                                color = if (inst.id == selectedInstance?.id) Color.White else Color(0xFFCBD5E1),
                                                fontSize = 13.sp,
                                                fontWeight = if (inst.id == selectedInstance?.id) FontWeight.Bold else FontWeight.Normal
                                            )
                                            Text(
                                                text = "(${inst.minecraftVersion})",
                                                color = Color(0xFF64748B),
                                                fontSize = 11.5.sp
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.selectInstance(inst)
                                        isInstanceDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }

                    EzzButton(
                        text = "Browse Modrinth",
                        onClick = {
                            selectedInstance?.let { inst ->
                                viewModel.openInstanceManager(inst, InstanceManagerTab.MODS)
                            }
                        },
                        icon = Icons.Default.Extension,
                        variant = EzzButtonVariant.PRIMARY,
                        size = EzzButtonSize.MEDIUM,
                        enabled = selectedInstance != null
                    )

                    EzzButton(
                        text = "Open Folder",
                        onClick = { viewModel.openModsFolder() },
                        icon = Icons.Default.FolderOpen,
                        variant = EzzButtonVariant.SECONDARY,
                        size = EzzButtonSize.MEDIUM,
                        enabled = selectedInstance != null
                    )

                    EzzIconButton(
                        icon = Icons.Default.Refresh,
                        onClick = { viewModel.refreshMods() },
                        contentDescription = "Refresh Mods",
                        size = EzzButtonSize.MEDIUM
                    )
                }
            }
        }

        if (selectedInstance == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF101318))
                    .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                EzzEmptyState(
                    title = "No Instance Selected",
                    description = "Please select or create a Minecraft instance first to manage its mods.",
                    actionLabel = "Go to Instances",
                    onAction = { viewModel.navigateTo(NavigationScreen.INSTANCES) }
                )
            }
            return@Column
        }

        // 2. Search & Filter Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(320.dp)) {
                EzzSearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "Search installed mods..."
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF101318))
                    .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(8.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                listOf(
                    Pair(ModFilter.ALL, "All (${installedMods.size})"),
                    Pair(ModFilter.ENABLED, "Enabled (${installedMods.count { it.enabled }})"),
                    Pair(ModFilter.DISABLED, "Disabled (${installedMods.count { !it.enabled }})")
                ).forEach { (filter, label) ->
                    val isSelected = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Color(0xFF1A1E29) else Color.Transparent)
                            .then(
                                if (isSelected) Modifier.border(1.dp, Color.White, RoundedCornerShape(6.dp))
                                else Modifier
                            )
                            .clickable { selectedFilter = filter }
                            .padding(horizontal = 11.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 3. Mod List
        if (filteredMods.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF101318))
                    .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                EzzEmptyState(
                    title = if (installedMods.isEmpty()) "No Mods Installed" else "No Matching Mods",
                    description = if (installedMods.isEmpty()) "Drop your Fabric .jar mod files into the instance mods folder." else "No installed mods matched '$searchQuery'.",
                    actionLabel = if (installedMods.isEmpty()) "Open Mods Folder" else "Clear Search",
                    onAction = {
                        if (installedMods.isEmpty()) viewModel.openModsFolder()
                        else searchQuery = ""
                    }
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredMods, key = { it.fileName }) { mod ->
                    ModCard(
                        mod = mod,
                        onToggle = { enable ->
                            viewModel.toggleMod(selectedInstance!!.id, mod.fileName, enable)
                        },
                        onDelete = {
                            viewModel.deleteMod(selectedInstance!!.id, mod.fileName)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModCard(
    mod: ModMetadata,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    EzzCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (mod.enabled) Color(0xFF1A1D26) else Color(0xFF141720),
        backgroundColor = if (mod.enabled) Color(0xFF101318) else Color(0xFF0C0E12)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Icon + Mod Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (mod.enabled) Color(0xFF141720) else Color(0xFF101318))
                        .border(1.dp, if (mod.enabled) Color(0xFF222735) else Color(0xFF1A1D26), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        tint = if (mod.enabled) Color.White else Color(0xFF64748B),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = mod.name,
                            color = if (mod.enabled) Color.White else Color(0xFF64748B),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        EzzBadge(
                            text = "v${mod.version}",
                            variant = EzzBadgeVariant.NEUTRAL
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        EzzBadge(
                            text = mod.loader.uppercase(),
                            variant = EzzBadgeVariant.NEUTRAL
                        )
                    }

                    val desc = mod.description
                    if (!desc.isNullOrBlank()) {
                        Text(
                            text = desc,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Text(
                        text = "${mod.fileName} • ${formatFileSize(mod.fileSize)}",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Right: Toggle Switch & Delete Action
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EzzToggle(
                    checked = mod.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.width(44.dp)
                )

                EzzIconButton(
                    icon = Icons.Default.Delete,
                    onClick = onDelete,
                    contentDescription = "Delete Mod",
                    size = EzzButtonSize.SMALL,
                    variant = EzzButtonVariant.DANGER
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        "%.1f MB".format(mb)
    } else {
        "%.0f KB".format(kb)
    }
}
