package io.ezz.launcher.ui.vault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
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
import io.ezz.launcher.core.model.skin.SkinModelType
import io.ezz.launcher.core.model.skin.VaultSkin
import io.ezz.launcher.ui.components.EzzBadge
import io.ezz.launcher.ui.components.EzzBadgeVariant
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.EzzEmptyState
import io.ezz.launcher.ui.components.EzzIconButton
import io.ezz.launcher.ui.components.EzzSearchField
import io.ezz.launcher.ui.components.EzzTextField
import io.ezz.launcher.ui.viewmodel.AppViewModel
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

enum class VaultSortOption {
    RECENT,
    NAME,
    ACTIVE_FIRST
}

@Composable
fun VaultScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val skins by viewModel.vaultSkins.collectAsState()
    val activeSkinId by viewModel.activeVaultSkinId.collectAsState()
    val selectedSkinState by viewModel.selectedVaultSkin.collectAsState()

    val selectedAccount by viewModel.accountRepository.selectedAccount.collectAsState()
    val activeSkin = remember(skins, activeSkinId, selectedAccount) {
        viewModel.vaultRepository.getActiveSkin(selectedAccount?.id)
    }

    val selectedSkin = selectedSkinState ?: activeSkin ?: skins.firstOrNull()

    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(VaultSortOption.RECENT) }

    // Dialog States
    var skinToRename by remember { mutableStateOf<VaultSkin?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var skinToDelete by remember { mutableStateOf<VaultSkin?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 3D Controls
    var autoRotate by remember { mutableStateOf(true) }
    var resetTrigger by remember { mutableStateOf(0) }

    // Filter and Sort Skins
    val displayedSkins = remember(skins, searchQuery, sortOption, activeSkinId) {
        val filtered = skins.filter { skin ->
            searchQuery.isBlank() || skin.name.contains(searchQuery, ignoreCase = true)
        }
        when (sortOption) {
            VaultSortOption.RECENT -> filtered.sortedByDescending { it.createdAt }
            VaultSortOption.NAME -> filtered.sortedBy { it.name.lowercase() }
            VaultSortOption.ACTIVE_FIRST -> filtered.sortedByDescending { it.id == activeSkinId }
        }
    }

    val selectedSkinBytes = remember(selectedSkin) {
        if (selectedSkin != null) {
            viewModel.getVaultSkinBytes(selectedSkin)
        } else {
            null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .padding(24.dp)
    ) {
        // Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "VAULT",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    EzzBadge(
                        text = "${skins.size} skins",
                        variant = EzzBadgeVariant.NEUTRAL
                    )
                }
                Text(
                    text = "Local Minecraft skin repository, real 3D player models, and offline client injection",
                    color = Color(0xFF888888),
                    fontSize = 12.sp
                )
            }

            EzzButton(
                text = "+ Import Skin",
                onClick = {
                    openNativeSkinPicker { file ->
                        if (file != null && file.exists()) {
                            try {
                                val bytes = file.readBytes()
                                val preferredName = file.nameWithoutExtension.replace("_", " ").replace("-", " ")
                                viewModel.importVaultSkin(bytes, preferredName) { result ->
                                    result.onFailure { err ->
                                        errorMessage = err.message ?: "Failed to import skin file."
                                    }
                                }
                            } catch (e: Exception) {
                                errorMessage = "Failed to read skin file: ${e.message}"
                            }
                        }
                    }
                },
                variant = EzzButtonVariant.PRIMARY,
                size = EzzButtonSize.MEDIUM
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Main 2-Pane Studio Workspace
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // LEFT PANE: Skin Collection (~56%)
            Column(
                modifier = Modifier
                    .weight(0.56f)
                    .fillMaxHeight()
            ) {
                // Search & Sort Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        EzzSearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = "Search skins by name...",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Sort Tabs
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF141414))
                            .border(1.dp, Color(0xFF242424), RoundedCornerShape(6.dp))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        listOf(
                            Pair(VaultSortOption.RECENT, "Recent"),
                            Pair(VaultSortOption.NAME, "Name"),
                            Pair(VaultSortOption.ACTIVE_FIRST, "Active First")
                        ).forEach { (opt, label) ->
                            val isSel = sortOption == opt
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSel) Color(0xFF242424) else Color.Transparent)
                                    .clickable { sortOption = opt }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSel) Color.White else Color(0xFF888888),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Skin Grid / Empty State
                if (displayedSkins.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0D0D0D))
                            .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        EzzEmptyState(
                            title = if (searchQuery.isNotBlank()) "No skins match \"$searchQuery\"" else "Your Vault is empty",
                            description = if (searchQuery.isNotBlank()) "Try a different search query." else "Import standard 64x64 PNG Minecraft skins to customize your player model.",
                            actionLabel = "+ Import Skin",
                            onAction = {
                                openNativeSkinPicker { file ->
                                    if (file != null && file.exists()) {
                                        val bytes = file.readBytes()
                                        val preferredName = file.nameWithoutExtension.replace("_", " ").replace("-", " ")
                                        viewModel.importVaultSkin(bytes, preferredName) { result ->
                                            result.onFailure { err ->
                                                errorMessage = err.message ?: "Failed to import skin file."
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 190.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(displayedSkins, key = { it.id }) { skin ->
                            val isActive = skin.id == activeSkinId
                            val isSelected = skin.id == selectedSkin?.id

                            VaultSkinCard(
                                skin = skin,
                                isActive = isActive,
                                isSelected = isSelected,
                                onSelect = { viewModel.selectVaultSkin(skin) },
                                onSetActive = { viewModel.setActiveVaultSkin(skin.id, selectedAccount?.id) },
                                onRename = {
                                    skinToRename = skin
                                    renameInput = skin.name
                                },
                                onDelete = { skinToDelete = skin }
                            )
                        }
                    }
                }
            }

            // RIGHT PANE: 3D Studio Preview & Model Controls (~44%)
            Column(
                modifier = Modifier
                    .weight(0.44f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 3D Player Viewport Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0D0D0D))
                        .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                ) {
                    // 3D Canvas
                    androidx.compose.runtime.key(resetTrigger) {
                        MinecraftPlayerModel3DView(
                            skinBytes = selectedSkinBytes,
                            modelType = selectedSkin?.modelType ?: SkinModelType.STEVE,
                            autoRotate = autoRotate,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Top Bar Floating Controls inside 3D Viewport
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Model Type Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xCC000000))
                                .border(1.dp, Color(0xFF333333), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (selectedSkin?.modelType == SkinModelType.ALEX) "ALEX (3px Slim)" else "STEVE (4px Classic)",
                                color = Color(0xFFE0E0E0),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Camera Actions
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Auto-Rotate Toggle Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (autoRotate) Color(0xFF222222) else Color(0xCC000000))
                                    .border(1.dp, if (autoRotate) Color.White else Color(0xFF333333), RoundedCornerShape(6.dp))
                                    .clickable { autoRotate = !autoRotate }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Auto Rotate",
                                        tint = if (autoRotate) Color.White else Color(0xFF888888),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = if (autoRotate) "Rotate: ON" else "Rotate: OFF",
                                        color = if (autoRotate) Color.White else Color(0xFF888888),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Reset Camera Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xCC000000))
                                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(6.dp))
                                    .clickable { resetTrigger++ }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Reset View",
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Bottom Floating Instructions
                    Text(
                        text = "Drag to rotate  •  Scroll to zoom",
                        color = Color(0xFF555555),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp)
                    )
                }

                // Selected Skin Info Card
                if (selectedSkin != null) {
                    val isCurrentlyActive = selectedSkin.id == activeSkinId

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF111111))
                            .border(1.dp, Color(0xFF222222), RoundedCornerShape(8.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = selectedSkin.name,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "Added on ${formatDate(selectedSkin.createdAt)}",
                                    color = Color(0xFF777777),
                                    fontSize = 11.sp
                                )
                            }

                            if (isCurrentlyActive) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "✓ ACTIVE SKIN",
                                        color = Color.Black,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }

                        // Model Type Switcher Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Model Architecture", color = Color(0xFF888888), fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF181818))
                                    .border(1.dp, Color(0xFF282828), RoundedCornerShape(6.dp))
                                    .padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (selectedSkin.modelType == SkinModelType.STEVE) Color(0xFF282828) else Color.Transparent)
                                        .clickable { viewModel.updateVaultSkinModel(selectedSkin.id, SkinModelType.STEVE) }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Steve (4px)",
                                        color = if (selectedSkin.modelType == SkinModelType.STEVE) Color.White else Color(0xFF777777),
                                        fontSize = 11.sp,
                                        fontWeight = if (selectedSkin.modelType == SkinModelType.STEVE) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (selectedSkin.modelType == SkinModelType.ALEX) Color(0xFF282828) else Color.Transparent)
                                        .clickable { viewModel.updateVaultSkinModel(selectedSkin.id, SkinModelType.ALEX) }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Alex (3px)",
                                        color = if (selectedSkin.modelType == SkinModelType.ALEX) Color.White else Color(0xFF777777),
                                        fontSize = 11.sp,
                                        fontWeight = if (selectedSkin.modelType == SkinModelType.ALEX) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Set Active Button
                        if (!isCurrentlyActive) {
                            EzzButton(
                                text = "SET AS ACTIVE VAULT SKIN",
                                onClick = { viewModel.setActiveVaultSkin(selectedSkin.id, selectedAccount?.id) },
                                variant = EzzButtonVariant.PRIMARY,
                                size = EzzButtonSize.MEDIUM,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Offline Injection Guarantee Notice
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF141414))
                                .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(6.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF888888), modifier = Modifier.size(14.dp))
                            Text(
                                text = "Applied to Minecraft offline accounts on launch. Server plugins take priority in multiplayer.",
                                color = Color(0xFF888888),
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Rename Dialog
    if (skinToRename != null) {
        val target = skinToRename!!
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC000000))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(380.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF141414))
                    .border(1.dp, Color(0xFF282828), RoundedCornerShape(10.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Rename Skin", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                EzzTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    placeholder = "Skin name",
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EzzButton(
                        text = "Cancel",
                        onClick = { skinToRename = null },
                        variant = EzzButtonVariant.SECONDARY,
                        size = EzzButtonSize.SMALL
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    EzzButton(
                        text = "Save",
                        onClick = {
                            viewModel.renameVaultSkin(target.id, renameInput) {
                                skinToRename = null
                            }
                        },
                        variant = EzzButtonVariant.PRIMARY,
                        size = EzzButtonSize.SMALL,
                        enabled = renameInput.isNotBlank()
                    )
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (skinToDelete != null) {
        val target = skinToDelete!!
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC000000))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(380.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF141414))
                    .border(1.dp, Color(0xFF282828), RoundedCornerShape(10.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Delete Skin", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "Are you sure you want to delete \"${target.name}\" from Vault? This cannot be undone.",
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.5.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EzzButton(
                        text = "Cancel",
                        onClick = { skinToDelete = null },
                        variant = EzzButtonVariant.SECONDARY,
                        size = EzzButtonSize.SMALL
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    EzzButton(
                        text = "Delete",
                        onClick = {
                            viewModel.deleteVaultSkin(target.id)
                            skinToDelete = null
                        },
                        variant = EzzButtonVariant.DANGER,
                        size = EzzButtonSize.SMALL
                    )
                }
            }
        }
    }

    // Error Notice Dialog
    if (errorMessage != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC000000))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(400.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF161111))
                    .border(1.dp, Color(0xFF442222), RoundedCornerShape(10.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(18.dp))
                    Text("Skin Import Notice", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Text(errorMessage ?: "", color = Color(0xFFE0E0E0), fontSize = 12.5.sp)
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    EzzButton(
                        text = "Dismiss",
                        onClick = { errorMessage = null },
                        variant = EzzButtonVariant.SECONDARY,
                        size = EzzButtonSize.SMALL
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultSkinCard(
    skin: VaultSkin,
    isActive: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onSetActive: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val borderColor = when {
        isSelected -> Color.White
        isActive -> Color(0xFF666666)
        isHovered -> Color(0xFF383838)
        else -> Color(0xFF1E1E1E)
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0xFF181818) else Color(0xFF111111))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onSelect)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Thumbnail & Badges Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = skin.name,
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = if (skin.modelType == SkinModelType.ALEX) "Alex (3px)" else "Steve (4px)",
                    color = Color(0xFF777777),
                    fontSize = 10.5.sp
                )
            }

            if (isActive) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "ACTIVE",
                        color = Color.Black,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isActive) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF202020))
                        .border(1.dp, Color(0xFF303030), RoundedCornerShape(4.dp))
                        .clickable(onClick = onSetActive)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Set Active", color = Color(0xFFCCCCCC), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Text("✓ Current default", color = Color(0xFF888888), fontSize = 10.5.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                EzzIconButton(
                    icon = Icons.Default.Edit,
                    onClick = onRename,
                    contentDescription = "Rename Skin",
                    size = EzzButtonSize.SMALL
                )
                EzzIconButton(
                    icon = Icons.Default.Delete,
                    onClick = onDelete,
                    contentDescription = "Delete Skin",
                    size = EzzButtonSize.SMALL
                )
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0L) return "Recent"
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun openNativeSkinPicker(onFileSelected: (File?) -> Unit) {
    try {
        val os = System.getProperty("os.name")?.lowercase() ?: ""
        if (os.contains("mac")) {
            val dialog = FileDialog(null as Frame?, "Import Minecraft Skin", FileDialog.LOAD)
            dialog.setFilenameFilter { _, name ->
                name.endsWith(".png", true) || name.endsWith(".jpg", true) || name.endsWith(".webp", true)
            }
            dialog.isVisible = true
            val selected = dialog.file?.let { File(dialog.directory, it) }
            onFileSelected(selected)
        } else {
            val chooser = JFileChooser()
            chooser.dialogTitle = "Import Minecraft Skin (PNG)"
            chooser.fileFilter = FileNameExtensionFilter("Minecraft Skin (*.png, *.jpg, *.webp)", "png", "jpg", "jpeg", "webp")
            val res = chooser.showOpenDialog(null)
            if (res == JFileChooser.APPROVE_OPTION) {
                onFileSelected(chooser.selectedFile)
            } else {
                onFileSelected(null)
            }
        }
    } catch (e: Exception) {
        println("File picker error: ${e.message}")
        onFileSelected(null)
    }
}
