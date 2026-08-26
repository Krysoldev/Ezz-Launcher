package io.ezz.launcher.ui.vault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
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

/**
 * Vault V2 — Personal Minecraft Skin Studio.
 * - Large 3D Player Model Hero Stage (58-62% upper-left).
 * - Compact Skin Information & Control Panel (38-42% upper-right).
 * - "MY SKINS" visual collection cards row at the bottom.
 * - Non-empty default Steve character when zero custom skins exist.
 */
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

    // 3D Viewport Controls
    var autoRotate by remember { mutableStateOf(true) }
    var resetTrigger by remember { mutableIntStateOf(0) }

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
            .background(Color(0xFF080808))
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==========================================
        // 1. TOP HEADER
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "VAULT",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Skin Studio",
                        color = Color(0xFF888888),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
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

        // ==========================================
        // 2. HERO STUDIO STAGE + INFO PANEL (UPPER ~68%)
        // ==========================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.68f),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // LEFT: LARGE 3D CHARACTER HERO STAGE (~62%)
            Box(
                modifier = Modifier
                    .weight(0.62f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0D0D0D))
                    .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(10.dp))
            ) {
                // 3D Canvas
                MinecraftPlayerModel3DView(
                    skinBytes = selectedSkinBytes,
                    modelType = selectedSkin?.modelType ?: SkinModelType.STEVE,
                    autoRotate = autoRotate,
                    resetTrigger = resetTrigger,
                    modifier = Modifier.fillMaxSize()
                )

                // Floating Top-Right 3D Camera Controls
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Auto-Rotate Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (autoRotate) Color(0xFF222222) else Color(0xCC000000))
                            .border(1.dp, if (autoRotate) Color.White else Color(0xFF333333), RoundedCornerShape(6.dp))
                            .clickable { autoRotate = !autoRotate }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Auto Rotate",
                                tint = if (autoRotate) Color.White else Color(0xFF888888),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (autoRotate) "Auto Rotate: ON" else "Auto Rotate: OFF",
                                color = if (autoRotate) Color.White else Color(0xFF888888),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Reset View Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xCC000000))
                            .border(1.dp, Color(0xFF333333), RoundedCornerShape(6.dp))
                            .clickable { resetTrigger++ }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "Reset View",
                            color = Color(0xFFAAAAAA),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Bottom Floating Hint
                Text(
                    text = "Drag to rotate  •  Scroll to zoom",
                    color = Color(0xFF555555),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                )

                // Empty State Overlay Banner (if no skins exist)
                if (skins.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xEE121212))
                            .border(1.dp, Color(0xFF282828), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column {
                                Text("YOUR VAULT IS EMPTY", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Black)
                                Text("Import a Minecraft skin to customize your player.", color = Color(0xFF888888), fontSize = 10.5.sp)
                            }
                        }
                    }
                }
            }

            // RIGHT: SKIN INFORMATION & ACTION PANEL (~38%)
            Column(
                modifier = Modifier
                    .weight(0.38f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF111111))
                    .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(10.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Panel Header & Active Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SKIN INFORMATION",
                            color = Color(0xFF777777),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )

                        val isCurrentlyActive = selectedSkin?.id != null && selectedSkin.id == activeSkinId
                        if (isCurrentlyActive) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White)
                                    .padding(horizontal = 7.dp, vertical = 2.5.dp)
                            ) {
                                Text(
                                    text = "● ACTIVE SKIN",
                                    color = Color.Black,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        } else {
                            Text(
                                text = "INACTIVE",
                                color = Color(0xFF555555),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Skin Name & Metadata
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = selectedSkin?.name ?: "Default Steve",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = if (selectedSkin != null) "Source: Local Vault • ${formatDate(selectedSkin.createdAt)}" else "Source: Default Game Asset",
                            color = Color(0xFF777777),
                            fontSize = 11.sp
                        )
                    }

                    // Model Architecture Switcher
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "MODEL ARCHITECTURE",
                            color = Color(0xFF777777),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF161616))
                                .border(1.dp, Color(0xFF262626), RoundedCornerShape(6.dp))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            val isSteve = (selectedSkin?.modelType ?: SkinModelType.STEVE) == SkinModelType.STEVE
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSteve) Color(0xFF262626) else Color.Transparent)
                                    .clickable {
                                        if (selectedSkin != null) {
                                            viewModel.updateVaultSkinModel(selectedSkin.id, SkinModelType.STEVE)
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Steve (4px Classic)",
                                    color = if (isSteve) Color.White else Color(0xFF777777),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSteve) FontWeight.Bold else FontWeight.Medium
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (!isSteve) Color(0xFF262626) else Color.Transparent)
                                    .clickable {
                                        if (selectedSkin != null) {
                                            viewModel.updateVaultSkinModel(selectedSkin.id, SkinModelType.ALEX)
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Alex (3px Slim)",
                                    color = if (!isSteve) Color.White else Color(0xFF777777),
                                    fontSize = 11.sp,
                                    fontWeight = if (!isSteve) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Action Buttons (Set Active / Rename / Delete)
                    if (selectedSkin != null) {
                        val isCurrentlyActive = selectedSkin.id == activeSkinId

                        if (!isCurrentlyActive) {
                            EzzButton(
                                text = "SET AS ACTIVE SKIN",
                                onClick = { viewModel.setActiveVaultSkin(selectedSkin.id, selectedAccount?.id) },
                                variant = EzzButtonVariant.PRIMARY,
                                size = EzzButtonSize.MEDIUM,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            EzzButton(
                                text = "Rename",
                                onClick = {
                                    skinToRename = selectedSkin
                                    renameInput = selectedSkin.name
                                },
                                variant = EzzButtonVariant.SECONDARY,
                                size = EzzButtonSize.SMALL,
                                modifier = Modifier.weight(1f)
                            )
                            EzzButton(
                                text = "Delete",
                                onClick = { skinToDelete = selectedSkin },
                                variant = EzzButtonVariant.DANGER,
                                size = EzzButtonSize.SMALL,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Offline Injection Notice
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF141414))
                        .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(6.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF777777), modifier = Modifier.size(14.dp))
                    Text(
                        text = "Applied to Minecraft offline accounts on launch. Server plugins always take priority in multiplayer.",
                        color = Color(0xFF777777),
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        // ==========================================
        // 3. BOTTOM SECTION: "MY SKINS" COLLECTION (LOWER ~32%)
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.32f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Collection Header & Compact Filter Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "MY SKINS",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    EzzBadge(
                        text = "${skins.size}",
                        variant = EzzBadgeVariant.NEUTRAL
                    )
                }

                if (skins.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.width(220.dp)) {
                            EzzSearchField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = "Search skins...",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Compact Sort Selector
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF141414))
                                .border(1.dp, Color(0xFF222222), RoundedCornerShape(6.dp))
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            listOf(
                                Pair(VaultSortOption.RECENT, "Recent"),
                                Pair(VaultSortOption.NAME, "Name"),
                                Pair(VaultSortOption.ACTIVE_FIRST, "Active")
                            ).forEach { (opt, label) ->
                                val isSel = sortOption == opt
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSel) Color(0xFF242424) else Color.Transparent)
                                        .clickable { sortOption = opt }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSel) Color.White else Color(0xFF777777),
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Visual Cards Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rendered Skin Cards
                displayedSkins.forEach { skin ->
                    val isActive = skin.id == activeSkinId
                    val isSelected = skin.id == selectedSkin?.id
                    val bytes = viewModel.getVaultSkinBytes(skin)

                    SkinCollectionCard(
                        skin = skin,
                        skinBytes = bytes,
                        isActive = isActive,
                        isSelected = isSelected,
                        onClick = { viewModel.selectVaultSkin(skin) }
                    )
                }

                // Add Skin Quick Card
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF111111))
                        .border(1.dp, Color(0xFF222222), RoundedCornerShape(8.dp))
                        .clickable {
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
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E1E1E)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Import", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Text("+ Import Skin", color = Color(0xFFAAAAAA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // ==========================================
    // MODALS & DIALOGS
    // ==========================================

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
                    .width(360.dp)
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
                    .width(360.dp)
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

    // Error Dialog
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
                    .width(380.dp)
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

/**
 * Compact Visual Skin Card for the bottom collection row.
 */
@Composable
private fun SkinCollectionCard(
    skin: VaultSkin,
    skinBytes: ByteArray?,
    isActive: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.025f else 1.0f,
        animationSpec = tween(durationMillis = 150)
    )

    val borderColor = when {
        isSelected -> Color.White
        isActive -> Color(0xFF666666)
        isHovered -> Color(0xFF3A3A3A)
        else -> Color(0xFF1E1E1E)
    }

    Box(
        modifier = Modifier
            .width(140.dp)
            .fillMaxHeight()
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0xFF181818) else Color(0xFF111111))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top: 2-Layer Avatar Head Thumbnail
            SkinAvatarHeadThumbnail(
                skinBytes = skinBytes,
                size = 48.dp
            )

            // Bottom: Name + Status Badge
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = skin.name,
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (isActive) {
                    Text(
                        text = "✓ ACTIVE",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                } else {
                    Text(
                        text = if (skin.modelType == SkinModelType.ALEX) "Alex (3px)" else "Steve (4px)",
                        color = Color(0xFF666666),
                        fontSize = 9.5.sp
                    )
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0L) return "Recently"
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
