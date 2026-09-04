package io.ezz.launcher.ui.vault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.skin.SkinModelType
import io.ezz.launcher.core.model.skin.VaultSkin
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.EzzTextField
import io.ezz.launcher.ui.viewmodel.AppViewModel
import io.ezz.launcher.ui.viewmodel.NavigationScreen
import java.awt.FileDialog
import java.awt.Frame
import java.io.ByteArrayInputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

data class PendingSkinImport(
    val bytes: ByteArray,
    val name: String,
    val modelType: SkinModelType
)

/**
 * Ezz Launcher — Vault / Skin Studio (State Synchronized Single Source of Truth).
 *
 * Core State Invariants:
 * 1. Authoritative State: UI is driven directly by [AppViewModel.vaultState], eliminating desyncs.
 * 2. Distinction between Selected Skin (currently inspected in 3D) and Active Skin (assigned to account).
 * 3. Atomic Apply: Setting active skin persists to disk, invalidates head avatar cache, and updates UI synchronously.
 * 4. Cache-Busted 3D Renderer: Reacts instantly to texture and model switches with unique skinKey.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    // Single Authoritative Source of Truth
    val vaultState by viewModel.vaultState.collectAsState()

    val currentAccount = vaultState.currentAccount
    val activeSkin = vaultState.activeSkin
    val selectedSkin = vaultState.selectedSkin
    val selectedSkinBytes = vaultState.selectedSkinBytes
    val isCurrentlyActive = vaultState.isSelectedSkinActive
    val allSkins = vaultState.allSkins
    val stateVersion = vaultState.stateVersion

    // Dialog & Modal States
    var pendingImport by remember { mutableStateOf<PendingSkinImport?>(null) }
    var skinToRename by remember { mutableStateOf<VaultSkin?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var skinToDelete by remember { mutableStateOf<VaultSkin?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 3D Viewport Controls
    var resetTrigger by remember { mutableIntStateOf(0) }

    val displayedSkins = allSkins

    // Function to trigger file import with validation
    val launchSkinImportFlow = {
        openNativeSkinPicker { file ->
            if (file != null && file.exists()) {
                try {
                    val bytes = file.readBytes()
                    if (bytes.isEmpty()) {
                        errorMessage = "The selected skin file is empty."
                        return@openNativeSkinPicker
                    }

                    val img = ImageIO.read(ByteArrayInputStream(bytes))
                    if (img == null) {
                        errorMessage = "Invalid Minecraft skin: The selected file could not be decoded as a valid image."
                        return@openNativeSkinPicker
                    }

                    val width = img.width
                    val height = img.height

                    if (!((width == 64 && height == 64) || (width == 64 && height == 32) || (width == 128 && height == 128))) {
                        errorMessage = "Invalid Minecraft skin: Skin dimensions must be 64x64 (or 64x32 legacy). Got ${width}x${height}."
                        return@openNativeSkinPicker
                    }

                    val detectedModel = viewModel.vaultRepository.detectModelType(bytes)
                    val rawName = file.nameWithoutExtension.replace("_", " ").replace("-", " ").trim()
                    val defaultName = if (rawName.isNotBlank()) rawName else "My Skin"

                    pendingImport = PendingSkinImport(
                        bytes = bytes,
                        name = defaultName,
                        modelType = detectedModel
                    )
                } catch (e: Exception) {
                    errorMessage = "Invalid Minecraft skin: ${e.message ?: "Failed to read file."}"
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07080A)),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ==========================================
            // 1. TOP HEADER: TITLE + IMPORT BUTTON
            // ==========================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF101318))
                    .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(10.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "VAULT",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                        Text(
                            text = if (currentAccount != null) "Your Minecraft skins • Active for ${currentAccount.username}" else "Your Minecraft skins",
                            color = Color(0xFF64748B),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    EzzButton(
                        text = "+ Import Skin",
                        onClick = { launchSkinImportFlow() },
                        variant = EzzButtonVariant.PRIMARY,
                        size = EzzButtonSize.MEDIUM
                    )
                }
            }

            // ==========================================
            // 1.5. NO ACCOUNT SELECTED BANNER (IF APPLICABLE)
            // ==========================================
            if (currentAccount == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF161311))
                        .border(1.dp, Color(0xFF3B2818), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "No Account Selected",
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Add or select an offline account to manage your active Minecraft skin.",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    EzzButton(
                        text = "Add Offline Account",
                        onClick = { viewModel.navigateTo(NavigationScreen.ACCOUNTS) },
                        variant = EzzButtonVariant.SECONDARY,
                        size = EzzButtonSize.SMALL
                    )
                }
            }

            // ==========================================
            // 2. MAIN WORKSPACE (UPPER ~68% HEIGHT)
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.68f),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // LEFT: LARGE 3D SKIN PREVIEW HERO STAGE (~62%)
                Box(
                    modifier = Modifier
                        .weight(0.62f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF161A24),
                                    Color(0xFF10131A),
                                    Color(0xFF07080A)
                                )
                            )
                        )
                        .border(1.dp, Color(0xFF1B1F2C), RoundedCornerShape(10.dp))
                ) {
                    // 3D Player Model Viewport with unique skinKey for instantaneous texture updates
                    MinecraftPlayerModel3DView(
                        skinBytes = selectedSkinBytes,
                        modelType = selectedSkin?.modelType ?: SkinModelType.STEVE,
                        skinKey = "${selectedSkin?.id}_${selectedSkin?.modelType}_${selectedSkin?.fileHash}_$stateVersion",
                        resetTrigger = resetTrigger,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Floating Top-Right Controls
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(14.dp)
                    ) {
                        val resetInteraction = remember { MutableInteractionSource() }
                        val isResetHovered by resetInteraction.collectIsHoveredAsState()

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isResetHovered) Color(0xFF1E2433) else Color(0xCC10131A))
                                .border(1.dp, if (isResetHovered) Color(0xFF8B5CF6).copy(alpha = 0.6f) else Color(0xFF1B1F2C), RoundedCornerShape(6.dp))
                                .clickable(
                                    interactionSource = resetInteraction,
                                    indication = null,
                                    onClick = { resetTrigger++ }
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Reset View",
                                color = if (isResetHovered) Color.White else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Floating Bottom Controls Hint
                    Text(
                        text = "Drag to rotate  •  Scroll to zoom  •  Double-click a skin below to apply",
                        color = Color(0xFF64748B),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                    )
                }

                // RIGHT: SKIN DETAILS PANEL (~38%)
                Column(
                    modifier = Modifier
                        .weight(0.38f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF10131A))
                        .border(1.dp, Color(0xFF1B1F2C), RoundedCornerShape(10.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Section Header & Active Status Badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SKIN DETAILS",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            )

                            if (isCurrentlyActive) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF064E3B))
                                        .border(1.dp, Color(0xFF059669), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "● ACTIVE",
                                        color = Color(0xFF34D399),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF141720))
                                        .border(1.dp, Color(0xFF222735), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "INACTIVE",
                                        color = Color(0xFF64748B),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Skin Name
                        Text(
                            text = selectedSkin?.name ?: "Default Steve",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Model Selector: Steve (Classic) vs Alex (Slim)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Model",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF141720))
                                    .border(1.dp, Color(0xFF222735), RoundedCornerShape(6.dp))
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                val isSteve = (selectedSkin?.modelType ?: SkinModelType.STEVE) == SkinModelType.STEVE

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSteve) Color(0xFF1E2433) else Color.Transparent)
                                        .then(if (isSteve) Modifier.border(1.dp, Color.White, RoundedCornerShape(4.dp)) else Modifier)
                                        .clickable {
                                            if (selectedSkin != null) {
                                                viewModel.updateVaultSkinModel(selectedSkin.id, SkinModelType.STEVE)
                                            }
                                        }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Steve (Classic)",
                                        color = if (isSteve) Color.White else Color(0xFF94A3B8),
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSteve) FontWeight.Bold else FontWeight.Normal
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (!isSteve) Color(0xFF1E2433) else Color.Transparent)
                                        .then(if (!isSteve) Modifier.border(1.dp, Color.White, RoundedCornerShape(4.dp)) else Modifier)
                                        .clickable {
                                            if (selectedSkin != null) {
                                                viewModel.updateVaultSkinModel(selectedSkin.id, SkinModelType.ALEX)
                                            }
                                        }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Alex (Slim)",
                                        color = if (!isSteve) Color.White else Color(0xFF94A3B8),
                                        fontSize = 11.5.sp,
                                        fontWeight = if (!isSteve) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        // Metadata (Source, Added Date)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Source", color = Color(0xFF64748B), fontSize = 11.sp)
                                Text(
                                    text = if (selectedSkin != null) "Local Vault" else "Default Game Asset",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Added", color = Color(0xFF64748B), fontSize = 11.sp)
                                Text(
                                    text = formatDate(selectedSkin?.createdAt ?: 0L),
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Actions Area
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (selectedSkin != null) {
                            // Primary Action Button (Apply Skin / Active Skin)
                            if (currentAccount == null) {
                                EzzButton(
                                    text = "Select an Account to Apply",
                                    onClick = { viewModel.navigateTo(NavigationScreen.ACCOUNTS) },
                                    variant = EzzButtonVariant.SECONDARY,
                                    size = EzzButtonSize.MEDIUM,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else if (!isCurrentlyActive) {
                                EzzButton(
                                    text = "Apply Skin",
                                    onClick = {
                                        viewModel.setActiveVaultSkin(selectedSkin.id, currentAccount.id)
                                    },
                                    variant = EzzButtonVariant.PRIMARY,
                                    size = EzzButtonSize.MEDIUM,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF064E3B))
                                        .border(1.dp, Color(0xFF059669), RoundedCornerShape(8.dp))
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color(0xFF34D399),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "✓ Active Skin",
                                            color = Color(0xFF34D399),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Secondary Actions: Rename and Delete
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
                                    icon = Icons.Default.Edit,
                                    variant = EzzButtonVariant.SECONDARY,
                                    size = EzzButtonSize.SMALL,
                                    modifier = Modifier.weight(1f)
                                )

                                EzzButton(
                                    text = "Delete",
                                    onClick = { skinToDelete = selectedSkin },
                                    icon = Icons.Default.Delete,
                                    variant = EzzButtonVariant.DANGER,
                                    size = EzzButtonSize.SMALL,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            Text(
                                text = "Select or import a custom skin to apply.",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 3. BOTTOM SECTION: "MY SKINS" CAROUSEL (~32% HEIGHT)
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.32f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Section Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "MY SKINS",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF141720))
                                .border(1.dp, Color(0xFF222735), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${allSkins.size}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Empty State vs Horizontal Card List
                if (allSkins.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF101318))
                            .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(8.dp))
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = "YOUR VAULT IS EMPTY",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Import a Minecraft skin to create your first custom player.",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.5.sp
                                )
                            }

                            EzzButton(
                                text = "+ Import Skin",
                                onClick = { launchSkinImportFlow() },
                                variant = EzzButtonVariant.PRIMARY,
                                size = EzzButtonSize.SMALL
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        displayedSkins.forEach { skin ->
                            val isSkinActive = skin.id == activeSkin?.id
                            val isSkinSelected = skin.id == selectedSkin?.id
                            val bytes = viewModel.getVaultSkinBytes(skin)

                            SkinCard(
                                skin = skin,
                                skinBytes = bytes,
                                skinKey = "${skin.id}_${skin.modelType}_${skin.fileHash}_$stateVersion",
                                isActive = isSkinActive,
                                isSelected = isSkinSelected,
                                onClick = { viewModel.selectVaultSkin(skin) },
                                onDoubleClick = {
                                    viewModel.selectVaultSkin(skin)
                                    if (currentAccount != null) {
                                        viewModel.setActiveVaultSkin(skin.id, currentAccount.id)
                                    }
                                }
                            )
                        }

                        // Quick "+ Import" Card at end of row
                        val addCardInteraction = remember { MutableInteractionSource() }
                        val isAddCardHovered by addCardInteraction.collectIsHoveredAsState()

                        Box(
                            modifier = Modifier
                                .width(130.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isAddCardHovered) Color(0xFF181C28) else Color(0xFF101318))
                                .border(1.dp, if (isAddCardHovered) Color(0xFF323A4E) else Color(0xFF1A1D26), RoundedCornerShape(8.dp))
                                .clickable(
                                    interactionSource = addCardInteraction,
                                    indication = null,
                                    onClick = { launchSkinImportFlow() }
                                ),
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
                                        .background(Color(0xFF141720))
                                        .border(1.dp, Color(0xFF222735), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Import",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = "+ Import",
                                    color = if (isAddCardHovered) Color.White else Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // MODALS & DIALOGS
        // ==========================================

        // 1. Import Skin Preview Modal
        if (pendingImport != null) {
            val pending = pendingImport!!
            ImportPreviewModal(
                pending = pending,
                onDismiss = { pendingImport = null },
                onConfirm = { name, model ->
                    viewModel.importVaultSkin(pending.bytes, name, model) { result ->
                        pendingImport = null
                        result.onSuccess { importedSkin ->
                            viewModel.selectVaultSkin(importedSkin)
                        }
                        result.onFailure { err ->
                            errorMessage = err.message ?: "Failed to import skin."
                        }
                    }
                }
            )
        }

        // 2. Rename Skin Modal
        if (skinToRename != null) {
            val target = skinToRename!!
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC07080A))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(360.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF101318))
                        .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(10.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Rename Skin",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

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
                                val clean = renameInput.trim()
                                if (clean.isNotBlank()) {
                                    viewModel.renameVaultSkin(target.id, clean) {
                                        skinToRename = null
                                    }
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

        // 3. Delete Confirmation Modal
        if (skinToDelete != null) {
            val target = skinToDelete!!
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC07080A))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(360.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF101318))
                        .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(10.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Delete Skin?",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "This skin will be removed from your Vault. If active, it will automatically fall back to Steve.",
                        color = Color(0xFF94A3B8),
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

        // 4. Friendly Error Notice Modal
        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC07080A))
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Invalid Minecraft Skin",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFE0E0E0),
                        fontSize = 12.5.sp
                    )

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
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
}

/**
 * Polished Import Skin Preview Modal.
 * Allows confirming 3D model appearance, customizing skin name, and choosing Steve vs Alex.
 */
@Composable
private fun ImportPreviewModal(
    pending: PendingSkinImport,
    onDismiss: () -> Unit,
    onConfirm: (name: String, model: SkinModelType) -> Unit
) {
    var nameInput by remember { mutableStateOf(pending.name) }
    var selectedModel by remember { mutableStateOf(pending.modelType) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC07080A))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF101318))
                .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(12.dp))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Modal Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Import Skin",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color(0xFF64748B),
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable { onDismiss() }
                )
            }

            // 3D Model Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0A0C10))
                    .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(8.dp))
            ) {
                MinecraftPlayerModel3DView(
                    skinBytes = pending.bytes,
                    modelType = selectedModel,
                    skinKey = "preview_${selectedModel}_${pending.bytes.size}",
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Skin Name Input
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Skin Name",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
                EzzTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    placeholder = "Skin name",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Model Switcher: Steve vs Alex
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Model",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF141720))
                        .border(1.dp, Color(0xFF222735), RoundedCornerShape(6.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    val isSteve = selectedModel == SkinModelType.STEVE

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSteve) Color(0xFF1E2433) else Color.Transparent)
                            .then(if (isSteve) Modifier.border(1.dp, Color.White, RoundedCornerShape(4.dp)) else Modifier)
                            .clickable { selectedModel = SkinModelType.STEVE }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Steve (4px Classic)",
                            color = if (isSteve) Color.White else Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = if (isSteve) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (!isSteve) Color(0xFF1E2433) else Color.Transparent)
                            .then(if (!isSteve) Modifier.border(1.dp, Color.White, RoundedCornerShape(4.dp)) else Modifier)
                            .clickable { selectedModel = SkinModelType.ALEX }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Alex (3px Slim)",
                            color = if (!isSteve) Color.White else Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = if (!isSteve) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Footer Actions: Cancel vs Import
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EzzButton(
                    text = "Cancel",
                    onClick = { onDismiss() },
                    variant = EzzButtonVariant.SECONDARY,
                    size = EzzButtonSize.SMALL
                )
                Spacer(modifier = Modifier.width(8.dp))
                EzzButton(
                    text = "Import Skin",
                    onClick = {
                        val clean = nameInput.trim().ifBlank { "My Skin" }
                        onConfirm(clean, selectedModel)
                    },
                    variant = EzzButtonVariant.PRIMARY,
                    size = EzzButtonSize.SMALL
                )
            }
        }
    }
}

/**
 * Compact, modern Skin Card for the bottom collection shelf.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SkinCard(
    skin: VaultSkin,
    skinBytes: ByteArray?,
    skinKey: Any?,
    isActive: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.025f else 1.0f,
        animationSpec = tween(durationMillis = 150)
    )

    val borderColor = when {
        isSelected -> Color(0xFF8B5CF6)
        isActive -> Color(0xFF10B981)
        isHovered -> Color(0xFF2D3448)
        else -> Color(0xFF1B1F2C)
    }

    Box(
        modifier = Modifier
            .width(135.dp)
            .fillMaxHeight()
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0xFF131122) else if (isHovered) Color(0xFF161A24) else Color(0xFF10131A))
            .border(if (isSelected || isActive) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onDoubleClick = onDoubleClick
            )
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 2-Layer Minecraft Avatar Head Thumbnail
            SkinAvatarHeadThumbnail(
                skinBytes = skinBytes,
                skinKey = skinKey,
                size = 46.dp
            )

            // Name + Status
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = skin.name,
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isActive) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF064E3B))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "● ACTIVE",
                            color = Color(0xFF34D399),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = if (skin.modelType == SkinModelType.ALEX) "Alex (3px)" else "Steve (4px)",
                        color = Color(0xFF64748B),
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
                name.endsWith(".png", true)
            }
            dialog.isVisible = true
            val selected = dialog.file?.let { File(dialog.directory, it) }
            onFileSelected(selected)
        } else {
            val chooser = JFileChooser()
            chooser.dialogTitle = "Import Minecraft Skin (PNG)"
            chooser.fileFilter = FileNameExtensionFilter("Minecraft Skin (*.png)", "png")
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
