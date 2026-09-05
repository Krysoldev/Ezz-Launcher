package io.ezz.launcher.ui.instances

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.ui.audio.EzzAudioService
import io.ezz.launcher.ui.components.CompactRuntimeBadge
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.EzzTextField
import io.ezz.launcher.ui.components.InstanceArtworkIcon
import io.ezz.launcher.ui.image.ImageDecoder
import io.ezz.launcher.ui.viewmodel.AppViewModel
import io.ezz.launcher.ui.viewmodel.NavigationScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class InstanceSort(val label: String) {
    RECENT("Recently Played"),
    NAME("Name (A–Z)"),
    VERSION("Version"),
    CREATED("Newest Created")
}

@Composable
fun InstancesScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val instances by viewModel.instanceRepository.instances.collectAsState()
    val selectedInstance by viewModel.selectedInstance.collectAsState()
    val runningSessions by viewModel.runningSessions.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(InstanceSort.RECENT) }
    var isSortDropdownOpen by remember { mutableStateOf(false) }

    var instanceToDelete by remember { mutableStateOf<Instance?>(null) }

    // Filter & sort instances
    val filteredInstances = remember(instances, searchQuery, sortOption) {
        val filtered = if (searchQuery.isBlank()) {
            instances
        } else {
            val q = searchQuery.trim()
            instances.filter {
                it.name.contains(q, ignoreCase = true) ||
                it.minecraftVersion.contains(q, ignoreCase = true) ||
                it.loaderType.name.contains(q, ignoreCase = true)
            }
        }

        when (sortOption) {
            InstanceSort.RECENT -> filtered.sortedByDescending { it.lastPlayedAt ?: 0L }
            InstanceSort.NAME -> filtered.sortedBy { it.name.lowercase() }
            InstanceSort.VERSION -> filtered.sortedByDescending { it.minecraftVersion }
            InstanceSort.CREATED -> filtered.sortedByDescending { it.createdAt }
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
                .widthIn(max = 1240.dp)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // =========================================================
            // 1. HEADER CARD WITH CONTROLS (SEARCH, SORT, CREATE, IMPORT)
            // =========================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF10131A))
                    .border(1.dp, Color(0xFF1B1F2C), RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Title + Subtitle + Count Badge
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "INSTANCES",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF161A24))
                                    .border(1.dp, Color(0xFF222735), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${instances.size}",
                                    color = Color(0xFFA78BFA),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = "Your Minecraft worlds and installations.",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp
                        )
                    }

                    // Right: Elegant Controls Row (Search, Sort, Create, Import)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Search Bar
                        EzzTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = "Search instances...",
                            leadingIcon = Icons.Default.Search,
                            modifier = Modifier.width(220.dp),
                            cornerRadius = 8.dp
                        )

                        // Sort Dropdown
                        Box {
                            val sortInteraction = remember { MutableInteractionSource() }
                            val isSortHovered by sortInteraction.collectIsHoveredAsState()

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSortHovered) Color(0xFF181C28) else Color(0xFF141720))
                                    .border(1.dp, if (isSortHovered) Color(0xFF323A4E) else Color(0xFF222735), RoundedCornerShape(8.dp))
                                    .clickable(
                                        interactionSource = sortInteraction,
                                        indication = null,
                                        onClick = {
                                            EzzAudioService.playClick()
                                            isSortDropdownOpen = true
                                        }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = sortOption.label,
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Sort",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = isSortDropdownOpen,
                                onDismissRequest = { isSortDropdownOpen = false },
                                modifier = Modifier
                                    .background(Color(0xFF141720))
                                    .border(1.dp, Color(0xFF222735), RoundedCornerShape(8.dp))
                            ) {
                                InstanceSort.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = option.label,
                                                color = if (option == sortOption) Color.White else Color(0xFF94A3B8),
                                                fontSize = 12.5.sp,
                                                fontWeight = if (option == sortOption) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            EzzAudioService.playSelect()
                                            sortOption = option
                                            isSortDropdownOpen = false
                                        },
                                        colors = MenuDefaults.itemColors(textColor = Color.White)
                                    )
                                }
                            }
                        }

                        // Create Instance Button
                        EzzButton(
                            text = "Create",
                            onClick = { viewModel.showCreateInstanceDialog.value = true },
                            icon = Icons.Default.Add,
                            variant = EzzButtonVariant.PRIMARY,
                            size = EzzButtonSize.MEDIUM
                        )

                        // Import Button
                        EzzButton(
                            text = "Import",
                            onClick = { viewModel.openImportModpack() },
                            icon = Icons.Default.FileDownload,
                            variant = EzzButtonVariant.SECONDARY,
                            size = EzzButtonSize.MEDIUM
                        )
                    }
                }
            }

            // =========================================================
            // 2. INSTANCES GAME LIBRARY GRID / EMPTY STATE
            // =========================================================
            if (filteredInstances.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF10131A))
                        .border(1.dp, Color(0xFF1B1F2C), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF161A24))
                                .border(1.dp, Color(0xFF1B1F2C), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GridView,
                                contentDescription = null,
                                tint = Color(0xFFA78BFA),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "No Matching Instances" else "No Instances Yet",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (searchQuery.isNotBlank()) "No instances matched '$searchQuery'." else "Create an instance from scratch or import a Modrinth modpack (.mrpack).",
                                color = Color(0xFF64748B),
                                fontSize = 12.5.sp
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            EzzButton(
                                text = "Create Instance",
                                onClick = { viewModel.showCreateInstanceDialog.value = true },
                                icon = Icons.Default.Add,
                                variant = EzzButtonVariant.PRIMARY,
                                size = EzzButtonSize.MEDIUM
                            )
                            EzzButton(
                                text = "Import .mrpack",
                                onClick = { viewModel.openImportModpack() },
                                icon = Icons.Default.FileDownload,
                                variant = EzzButtonVariant.SECONDARY,
                                size = EzzButtonSize.MEDIUM
                            )
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 340.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredInstances, key = { it.id }) { instance ->
                        val instStartedAt = runningSessions[instance.id]?.startedAt

                        InstanceGridCard(
                            instance = instance,
                            isSelected = instance.id == selectedInstance?.id,
                            startedAt = instStartedAt,
                            viewModel = viewModel,
                            onCardClick = {
                                EzzAudioService.playSelect()
                                viewModel.openInstanceManager(instance)
                            },
                            onPlay = {
                                EzzAudioService.playLaunch()
                                viewModel.selectInstance(instance)
                                viewModel.launchInstance(instance)
                                viewModel.navigateTo(NavigationScreen.HOME)
                            },
                            onManage = {
                                EzzAudioService.playSelect()
                                viewModel.openInstanceManager(instance)
                            },
                            onEdit = { viewModel.showEditInstanceDialog.value = instance },
                            onDuplicate = { viewModel.duplicateInstance(instance.id, "${instance.name} (Copy)") },
                            onExport = { viewModel.openExportModpack(instance) },
                            onOpenFolder = { viewModel.openInstanceFolder(instance.id) },
                            onDelete = { instanceToDelete = instance }
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (instanceToDelete != null) {
        val target = instanceToDelete!!
        Dialog(
            onDismissRequest = { instanceToDelete = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .width(420.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF10131A))
                    .border(1.dp, Color(0xFF1B1F2C), RoundedCornerShape(10.dp))
                    .padding(22.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Delete Instance",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Are you sure you want to delete \"${target.name}\"? All worlds, mods, and instance settings will be permanently removed.",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EzzButton(
                            text = "Cancel",
                            onClick = { instanceToDelete = null },
                            variant = EzzButtonVariant.GHOST,
                            size = EzzButtonSize.SMALL
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        EzzButton(
                            text = "Delete",
                            onClick = {
                                val id = target.id
                                instanceToDelete = null
                                viewModel.deleteInstance(id)
                            },
                            variant = EzzButtonVariant.DANGER,
                            size = EzzButtonSize.SMALL
                        )
                    }
                }
            }
        }
    }
}

/**
 * Premium Instance Card:
 * ┌────────────────────────────────────────┐
 * │                                        │
 * │       INSTANCE IMAGE                   │
 * │                                        │
 * ├────────────────────────────────────────┤
 * │ Survival                               │
 * │ Minecraft 1.21.11 • Fabric             │
 * │                                        │
 * │ 142 Mods              Last played 2h   │
 * │                                        │
 * │ [ PLAY ]                          [...] │
 * └────────────────────────────────────────┘
 */
@Composable
private fun InstanceGridCard(
    instance: Instance,
    isSelected: Boolean,
    startedAt: Long? = null,
    viewModel: AppViewModel,
    onCardClick: () -> Unit,
    onPlay: () -> Unit,
    onManage: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onOpenFolder: () -> Unit,
    onDelete: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isInteractiveActive = isHovered || isFocused
    var isMenuOpen by remember { mutableStateOf(false) }

    // Real mod count dynamically evaluated
    var modCount by remember(instance.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(instance.id) {
        withContext(Dispatchers.IO) {
            try {
                val mods = viewModel.instanceManager.getMods(instance.id)
                modCount = mods.size
            } catch (_: Exception) {
                modCount = 0
            }
        }
    }

    // Audio cue on hover
    LaunchedEffect(isHovered) {
        if (isHovered) {
            EzzAudioService.playHover()
        }
    }

    // 2-3px lift without layout jarring bounce (160ms)
    val cardLift by animateDpAsState(
        targetValue = if (isInteractiveActive) (-2.5).dp else 0.dp,
        animationSpec = tween(160)
    )

    // Subtle surface brightness transition (160ms)
    val cardBg by animateColorAsState(
        targetValue = when {
            isSelected -> Color(0xFF131122)
            isInteractiveActive -> Color(0xFF151926)
            else -> Color(0xFF10131A)
        },
        animationSpec = tween(160)
    )

    // Subtle border transition (160ms)
    val cardBorder by animateColorAsState(
        targetValue = when {
            isSelected -> Color(0xFF8B5CF6).copy(alpha = 0.85f)
            isInteractiveActive -> Color(0xFF8B5CF6).copy(alpha = 0.5f)
            else -> Color(0xFF1B1F2C)
        },
        animationSpec = tween(160)
    )

    Box(
        modifier = Modifier
            .offset(y = cardLift)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(cardBg)
            .border(
                1.dp,
                cardBorder,
                RoundedCornerShape(10.dp)
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.Spacebar || event.key == Key.NumPadEnter)) {
                    onCardClick()
                    true
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onCardClick
            )
    ) {
        Column {
            // =========================================================
            // TOP: INSTANCE IMAGE BANNER (130dp)
            // =========================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .background(Color(0xFF0C0E14)),
                contentAlignment = Alignment.Center
            ) {
                InstanceBannerVisual(
                    instance = instance,
                    isHovered = isInteractiveActive
                )

                // Vignette gradient overlay into card body
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0x5507080A),
                                    cardBg
                                )
                            )
                        )
                )

                // Top-Left: Active Target Badge
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xCC8B5CF6))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ACTIVE",
                            color = Color.White,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.6.sp
                        )
                    }
                }

                // Top-Right: Running Session Pill
                if (startedAt != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                    ) {
                        CompactRuntimeBadge(startedAt = startedAt, onClick = onPlay)
                    }
                }
            }

            // =========================================================
            // BOTTOM: CARD BODY (METADATA + ACTIONS)
            // =========================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Name & Version/Loader
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = instance.name,
                        color = Color.White,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "Minecraft ${instance.minecraftVersion} • ${instance.loaderType.name}",
                        color = Color(0xFFA78BFA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Stats: Real Mod Count & Real Last Played
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val countText = if (modCount != null) {
                        if (modCount == 1) "1 Mod" else "$modCount Mods"
                    } else {
                        "..."
                    }

                    Text(
                        text = countText,
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    val lastPlayed = instance.lastPlayedAt
                    val lastPlayedStr = if (lastPlayed != null && lastPlayed > 0) {
                        val diffMs = System.currentTimeMillis() - lastPlayed
                        val hours = diffMs / (1000 * 60 * 60)
                        if (hours < 1) "Played recently" else if (hours < 24) "Played ${hours}h ago" else "Played ${hours / 24}d ago"
                    } else {
                        "Never played"
                    }

                    Text(
                        text = lastPlayedStr,
                        color = Color(0xFF64748B),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Action Row: [ PLAY ] + [...]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Play Button
                    EzzButton(
                        text = if (startedAt != null) "Running" else "PLAY",
                        onClick = onPlay,
                        icon = Icons.Default.PlayArrow,
                        variant = if (isSelected) EzzButtonVariant.PRIMARY else EzzButtonVariant.SECONDARY,
                        size = EzzButtonSize.SMALL
                    )

                    // Right: Context Menu [...]
                    Box {
                        val moreInteraction = remember { MutableInteractionSource() }
                        val isMoreHovered by moreInteraction.collectIsHoveredAsState()

                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isMoreHovered) Color(0xFF1E2332) else Color(0xFF141720))
                                .border(1.dp, if (isMoreHovered) Color(0xFF323A4E) else Color(0xFF1E2332), RoundedCornerShape(6.dp))
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable(
                                    interactionSource = moreInteraction,
                                    indication = null,
                                    onClick = {
                                        EzzAudioService.playClick()
                                        isMenuOpen = true
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = if (isMoreHovered) Color.White else Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = isMenuOpen,
                            onDismissRequest = { isMenuOpen = false },
                            modifier = Modifier
                                .background(Color(0xFF141720))
                                .border(1.dp, Color(0xFF222735), RoundedCornerShape(8.dp))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Manage Workspace", color = Color.White, fontSize = 12.5.sp) },
                                leadingIcon = { Icon(Icons.Default.GridView, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(15.dp)) },
                                onClick = {
                                    isMenuOpen = false
                                    onManage()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit Configuration", color = Color.White, fontSize = 12.5.sp) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(15.dp)) },
                                onClick = {
                                    isMenuOpen = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Duplicate Instance", color = Color.White, fontSize = 12.5.sp) },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(15.dp)) },
                                onClick = {
                                    isMenuOpen = false
                                    onDuplicate()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export (.zip)", color = Color.White, fontSize = 12.5.sp) },
                                leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(15.dp)) },
                                onClick = {
                                    isMenuOpen = false
                                    onExport()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Open Directory", color = Color.White, fontSize = 12.5.sp) },
                                leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(15.dp)) },
                                onClick = {
                                    isMenuOpen = false
                                    onOpenFolder()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = Color(0xFFEF4444), fontSize = 12.5.sp) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(15.dp)) },
                                onClick = {
                                    isMenuOpen = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Top Visual for Instance Card: Subtly zooms 1-2% on hover.
 */
@Composable
private fun InstanceBannerVisual(
    instance: Instance,
    isHovered: Boolean
) {
    val zoomScale by animateFloatAsState(
        targetValue = if (isHovered) 1.02f else 1.0f,
        animationSpec = tween(180)
    )

    // Resolve local custom icon file
    val iconFile = remember(instance.id, instance.customIconPath) {
        val path = instance.customIconPath
        val primaryFile = if (!path.isNullOrBlank()) {
            val f = File(path)
            if (f.exists() && f.length() > 0L) f else null
        } else null

        primaryFile ?: run {
            val userHome = System.getProperty("user.home") ?: "."
            val possibleRoots = listOf(
                File(userHome, ".ezz/instances/${instance.id}"),
                File(userHome, "AppData/Roaming/.ezz/instances/${instance.id}")
            )

            possibleRoots.flatMap { root ->
                listOf(
                    File(root, "icon.png"),
                    File(root, "pack.png"),
                    File(root, "icon.webp"),
                    File(root, "icon.jpg")
                )
            }.firstOrNull { it.exists() && it.length() > 0L }
        }
    }

    val customBitmap = remember(iconFile?.absolutePath, iconFile?.lastModified()) {
        ImageDecoder.decodeFile(iconFile)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .scale(zoomScale),
        contentAlignment = Alignment.Center
    ) {
        if (customBitmap != null) {
            Image(
                bitmap = customBitmap,
                contentDescription = instance.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High
            )
        } else {
            // Isometric block on deep atmospheric gradient
            InstanceArtworkIcon(
                instance = instance,
                size = 58.dp
            )
        }
    }
}
