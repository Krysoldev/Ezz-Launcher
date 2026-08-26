package io.ezz.launcher.ui.mods

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
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
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
import io.ezz.launcher.ui.components.EzzTabs
import io.ezz.launcher.ui.components.EzzToggle
import io.ezz.launcher.ui.components.TabItem
import io.ezz.launcher.ui.theme.EzzTheme
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
    val colors = EzzTheme.colors
    val selectedInstance by viewModel.selectedInstance.collectAsState()
    val installedMods by viewModel.installedMods.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(ModFilter.ALL) }

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
                        text = "Mod Manager",
                        color = colors.textPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    if (selectedInstance != null) {
                        EzzBadge(
                            text = "${installedMods.count { it.enabled }} / ${installedMods.size} Enabled",
                            variant = EzzBadgeVariant.PRIMARY
                        )
                    }
                }

                Text(
                    text = if (selectedInstance != null) "Managing mods for: ${selectedInstance!!.name} (${selectedInstance!!.minecraftVersion})" else "Select an instance to manage mods",
                    color = colors.textSecondary,
                    fontSize = 14.sp
                )
            }

            // Action Buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                EzzButton(
                    text = "Open Mods Folder",
                    onClick = { viewModel.openModsFolder() },
                    variant = EzzButtonVariant.SECONDARY,
                    size = EzzButtonSize.MEDIUM,
                    icon = Icons.Default.FolderOpen,
                    enabled = selectedInstance != null
                )

                Spacer(modifier = Modifier.width(10.dp))

                EzzIconButton(
                    icon = Icons.Default.Refresh,
                    onClick = { viewModel.refreshMods() },
                    contentDescription = "Refresh Mods",
                    tint = colors.textSecondary,
                    backgroundColor = colors.surfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (selectedInstance == null) {
            EzzEmptyState(
                title = "No Instance Selected",
                description = "Please select or create a Minecraft instance first to manage its mods.",
                actionButtonText = "Go to Instances",
                onActionClick = { viewModel.navigateTo(NavigationScreen.INSTANCES) }
            )
            return@Column
        }

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
                placeholder = "Search installed mods..."
            )

            EzzTabs(
                items = listOf(
                    TabItem(ModFilter.ALL, "All (${installedMods.size})"),
                    TabItem(ModFilter.ENABLED, "Enabled (${installedMods.count { it.enabled }})"),
                    TabItem(ModFilter.DISABLED, "Disabled (${installedMods.count { !it.enabled }})")
                ),
                selectedItem = selectedFilter,
                onItemSelected = { selectedFilter = it }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Mod List
        if (filteredMods.isEmpty()) {
            if (installedMods.isEmpty()) {
                EzzEmptyState(
                    title = "No Mods Installed",
                    description = "Drop your Fabric or Forge .jar mod files into the mods folder to manage them here.",
                    actionButtonText = "Open Mods Folder",
                    onActionClick = { viewModel.openModsFolder() }
                )
            } else {
                EzzEmptyState(
                    title = "No Matching Mods",
                    description = "No installed mods matched your search query '$searchQuery'.",
                    actionButtonText = "Clear Search",
                    onActionClick = { searchQuery = "" }
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
    val colors = EzzTheme.colors

    EzzCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (mod.enabled) colors.border else colors.border.copy(alpha = 0.3f),
        backgroundColor = if (mod.enabled) colors.cardBackground else colors.surface.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Icon + Mod Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Mod Icon / Avatar
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (mod.enabled) colors.primaryGlow else colors.surfaceVariant)
                        .border(1.dp, if (mod.enabled) colors.primary.copy(alpha = 0.5f) else colors.border, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        tint = if (mod.enabled) colors.primary else colors.textMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = mod.name,
                            color = if (mod.enabled) colors.textPrimary else colors.textMuted,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        EzzBadge(
                            text = "v${mod.version}",
                            variant = if (mod.enabled) EzzBadgeVariant.NEUTRAL else EzzBadgeVariant.NEUTRAL
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        EzzBadge(
                            text = mod.loader.uppercase(),
                            variant = if (mod.loader.equals("FABRIC", ignoreCase = true)) EzzBadgeVariant.INFO else EzzBadgeVariant.WARNING
                        )
                    }

                    val desc = mod.description
                    if (!desc.isNullOrBlank()) {
                        Text(
                            text = desc,
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Text(
                        text = "${mod.fileName} • ${formatFileSize(mod.fileSize)}",
                        color = colors.textMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Right: Toggle Switch & Delete Action
            Row(verticalAlignment = Alignment.CenterVertically) {
                EzzToggle(
                    checked = mod.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.width(50.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                EzzIconButton(
                    icon = Icons.Default.Delete,
                    onClick = onDelete,
                    contentDescription = "Delete Mod",
                    tint = colors.danger,
                    backgroundColor = colors.surfaceVariant
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
