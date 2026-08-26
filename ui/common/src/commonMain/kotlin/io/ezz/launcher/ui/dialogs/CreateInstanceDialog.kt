package io.ezz.launcher.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.minecraft.loader.optifine.OptiFineCompatibilityValidator
import io.ezz.launcher.core.minecraft.loader.optifine.OptiFineVersionOption
import io.ezz.launcher.core.minecraft.version.JavaCompatibility
import io.ezz.launcher.core.minecraft.version.MinecraftVersionComparator
import io.ezz.launcher.core.minecraft.version.VersionSortOrder
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.model.minecraft.VersionSummary
import io.ezz.launcher.ui.components.EzzBadge
import io.ezz.launcher.ui.components.EzzBadgeVariant
import io.ezz.launcher.ui.components.EzzIconButton
import io.ezz.launcher.ui.components.EzzToggle
import io.ezz.launcher.ui.components.InstanceArtworkIcon
import io.ezz.launcher.ui.viewmodel.AppViewModel
import io.ezz.launcher.ui.viewmodel.NavigationScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Ezz Launcher — Create Instance Studio (Rebuilt from Scratch)
 * Full-frame, studio-grade creation experience for Minecraft Java Edition.
 */
@Composable
fun CreateInstanceDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val existingInstances by viewModel.instances.collectAsState()
    val availableReleases by viewModel.availableVersions.collectAsState()
    val isManifestLoading by viewModel.isVersionManifestLoading.collectAsState()
    val manifestError by viewModel.versionManifestError.collectAsState()
    val latestReleaseId by viewModel.latestReleaseVersion.collectAsState()
    val detectedJavaRuntimes by viewModel.detectedJavaRuntimes.collectAsState()
    val settings by viewModel.settingsRepository.settings.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // 1. Instance Identity
    var name by remember { mutableStateOf("") }
    var isUserCustomName by remember { mutableStateOf(false) }
    var customIconFile by remember { mutableStateOf<File?>(null) }

    // 2. Minecraft Version Catalog (Official Release Versions Only)
    var versionSearchQuery by remember { mutableStateOf("") }

    val filteredVersions = remember(availableReleases, versionSearchQuery) {
        val q = versionSearchQuery.trim().lowercase()
        val list = if (q.isBlank()) availableReleases else availableReleases.filter { it.id.lowercase().contains(q) }
        MinecraftVersionComparator.sort(list, VersionSortOrder.NEWEST_FIRST)
    }

    // Selected Version (defaults to latest official Mojang release)
    var selectedMcVersion by remember(availableReleases, latestReleaseId) {
        mutableStateOf(latestReleaseId.ifBlank { availableReleases.firstOrNull()?.id ?: "1.21.4" })
    }

    // 3. Mod Loader Selection
    var selectedLoader by remember { mutableStateOf(LoaderType.VANILLA) }
    var fabricLoaders by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedFabricLoader by remember { mutableStateOf<String?>(null) }
    var isLoadingFabricLoaders by remember { mutableStateOf(false) }

    var optifineVersions by remember { mutableStateOf<List<OptiFineVersionOption>>(emptyList()) }
    var selectedOptiFineVersion by remember { mutableStateOf<String?>(null) }

    // 4. Java Runtime & RAM
    val requiredJavaMajor = remember(selectedMcVersion) {
        JavaCompatibility.getRequiredJavaMajorVersion(selectedMcVersion)
    }
    var selectedJavaPath by remember { mutableStateOf<String?>(null) }
    var maxRamMb by remember { mutableStateOf(settings.defaultMaxMemoryMb.toFloat().coerceIn(1024f, 16384f)) }

    // 5. Advanced Settings
    var isAdvancedExpanded by remember { mutableStateOf(false) }
    var windowWidth by remember { mutableStateOf(1280) }
    var windowHeight by remember { mutableStateOf(720) }
    var isFullscreen by remember { mutableStateOf(false) }
    var customJvmArgs by remember { mutableStateOf(settings.globalJvmArgs.joinToString(" ")) }

    // 6. Multi-Stage Creation Pipeline
    var creationStep by remember { mutableStateOf(0) } // 0: Form, 1..4: Creating, 5: Success
    var creationProgressText by remember { mutableStateOf("") }
    var createdInstanceResult by remember { mutableStateOf<Instance?>(null) }
    var creationErrorMessage by remember { mutableStateOf<String?>(null) }

    // Initial manifest load if empty
    LaunchedEffect(Unit) {
        if (availableReleases.isEmpty()) {
            viewModel.refreshAvailableVersions(forceRefresh = false)
        }
    }

    // Default instance name auto-population
    LaunchedEffect(selectedMcVersion, selectedLoader) {
        if (!isUserCustomName) {
            val prefix = when (selectedLoader) {
                LoaderType.VANILLA -> "Vanilla"
                LoaderType.FABRIC -> "Fabric"
                LoaderType.OPTIFINE -> "OptiFine"
            }
            name = "$prefix $selectedMcVersion"
        }
    }

    // Dynamic Fabric Loader resolution from Fabric Meta API
    LaunchedEffect(selectedMcVersion, selectedLoader) {
        if (selectedLoader == LoaderType.FABRIC) {
            isLoadingFabricLoaders = true
            coroutineScope.launch {
                try {
                    val loaders = viewModel.fabricMetaClient.getLoaderVersionsForGame(selectedMcVersion)
                    fabricLoaders = loaders
                    selectedFabricLoader = loaders.firstOrNull()
                } catch (e: Exception) {
                    fabricLoaders = emptyList()
                    selectedFabricLoader = null
                } finally {
                    isLoadingFabricLoaders = false
                }
            }
        }
    }

    // Dynamic OptiFine version resolution
    LaunchedEffect(selectedMcVersion, selectedLoader) {
        if (selectedLoader == LoaderType.OPTIFINE) {
            val opts = OptiFineCompatibilityValidator.getAvailableOptiFineVersions(selectedMcVersion)
            optifineVersions = opts
            selectedOptiFineVersion = OptiFineCompatibilityValidator.getSuggestedOptiFineVersion(selectedMcVersion)
        }
    }

    // Validation Logic
    val trimmedName = name.trim()
    val isNameEmpty = trimmedName.isBlank()
    val hasInvalidFsChars = trimmedName.any { ch -> ch in "/\\:*?\"<>|" }
    val isDuplicateName = existingInstances.any { inst -> inst.name.equals(trimmedName, ignoreCase = true) }
    val isFabricIncompatible = selectedLoader == LoaderType.FABRIC && !isLoadingFabricLoaders && fabricLoaders.isEmpty()
    val isOptiFineIncompatible = selectedLoader == LoaderType.OPTIFINE && !OptiFineCompatibilityValidator.isVersionSupported(selectedMcVersion)

    val validationError = when {
        isNameEmpty -> "Instance name is required"
        hasInvalidFsChars -> "Name contains invalid filesystem characters (/ \\ : * ? \" < > |)"
        isDuplicateName -> "An instance with this name already exists"
        isFabricIncompatible -> "Fabric loader is not available for Minecraft $selectedMcVersion"
        isOptiFineIncompatible -> "OptiFine is not supported for Minecraft $selectedMcVersion"
        else -> null
    }
    val isFormReady = validationError == null && creationStep == 0

    // Fullscreen Overlay Container
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6050505))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (creationStep == 0 || creationStep == 5) onDismiss() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 960.dp, max = 1060.dp)
                .heightIn(min = 640.dp, max = 740.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0D0D0D))
                .border(1.dp, Color(0xFF262626), RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // Consume inner clicks
                )
                .padding(24.dp)
        ) {
            Crossfade(targetState = creationStep) { step ->
                when {
                    // Success View (Step 5)
                    step == 5 && createdInstanceResult != null -> {
                        val inst = createdInstanceResult!!
                        StudioSuccessView(
                            instance = inst,
                            customIconFile = customIconFile,
                            onPlayNow = {
                                viewModel.selectInstance(inst)
                                viewModel.launchInstance(inst)
                                onDismiss()
                            },
                            onOpenInstance = {
                                viewModel.selectInstance(inst)
                                viewModel.navigateTo(NavigationScreen.INSTANCE_MANAGER)
                                onDismiss()
                            },
                            onDone = onDismiss
                        )
                    }

                    // Multi-Stage Progress View (Step 1..4)
                    step in 1..4 -> {
                        StudioProgressView(
                            instanceName = trimmedName,
                            minecraftVersion = selectedMcVersion,
                            loaderType = selectedLoader,
                            currentStep = step,
                            progressText = creationProgressText,
                            errorMessage = creationErrorMessage,
                            onRetry = {
                                creationStep = 0
                                creationErrorMessage = null
                            }
                        )
                    }

                    // Main Creation Workspace (Step 0)
                    else -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top Bar Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF1E1E1E))
                                            .clickable(onClick = onDismiss),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = "CREATE MINECRAFT INSTANCE",
                                                color = Color.White,
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = 0.6.sp
                                            )
                                            if (availableReleases.isNotEmpty()) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0xFF142414))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text("OFFICIAL MOJANG MANIFEST", color = Color(0xFF4CAF50), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        Text(
                                            text = "Set up an isolated instance with custom loader, memory, and runtime settings",
                                            color = Color(0xFF777777),
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    EzzIconButton(
                                        icon = Icons.Default.Refresh,
                                        onClick = { viewModel.refreshAvailableVersions(forceRefresh = true) },
                                        size = io.ezz.launcher.ui.components.EzzButtonSize.SMALL,
                                        variant = io.ezz.launcher.ui.components.EzzButtonVariant.GHOST
                                    )
                                    EzzIconButton(
                                        icon = Icons.Default.Close,
                                        onClick = onDismiss,
                                        size = io.ezz.launcher.ui.components.EzzButtonSize.SMALL,
                                        variant = io.ezz.launcher.ui.components.EzzButtonVariant.GHOST
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // 2-Column Studio Body
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                // LEFT COLUMN (~64%): Configuration Panels
                                val scrollState = rememberScrollState()
                                Column(
                                    modifier = Modifier
                                        .weight(0.64f)
                                        .fillMaxHeight()
                                        .verticalScroll(scrollState),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    // 1. INSTANCE IDENTITY
                                    StudioCard(title = "INSTANCE IDENTITY") {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            // Icon Picker Thumbnail Box
                                            Box(
                                                modifier = Modifier
                                                    .size(72.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color(0xFF141414))
                                                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp))
                                                    .clickable {
                                                        val picked = viewModel.platformBridge.pickImageFile("Select Instance Icon (PNG, JPG, WEBP)")
                                                        if (picked != null && picked.exists()) {
                                                            customIconFile = picked
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                InstanceArtworkIcon(
                                                    instance = Instance(
                                                        id = "preview_icon",
                                                        name = name,
                                                        minecraftVersion = selectedMcVersion,
                                                        loaderType = selectedLoader
                                                    ),
                                                    size = 72.dp,
                                                    customFile = customIconFile,
                                                    showBadge = false
                                                )
                                            }

                                            // Name Input & File info
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text("Instance Name", color = Color(0xFFAAAAAA), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                                io.ezz.launcher.ui.components.EzzTextField(
                                                    value = name,
                                                    onValueChange = {
                                                        name = it
                                                        isUserCustomName = true
                                                    },
                                                    placeholder = "e.g. Survival 1.21, Fabric SMP",
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = if (customIconFile != null) "Custom Icon: ${customIconFile?.name}" else "Click square to upload PNG / JPG / WEBP",
                                                        color = Color(0xFF666666),
                                                        fontSize = 10.sp
                                                    )
                                                    if (customIconFile != null) {
                                                        Text(
                                                            text = "Reset Icon",
                                                            color = Color(0xFFEF5350),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.clickable { customIconFile = null }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 2. MOD LOADER ENGINE
                                    StudioCard(title = "MOD ENGINE") {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                StudioLoaderCard(
                                                    title = "Vanilla",
                                                    description = "Official Minecraft",
                                                    icon = Icons.Default.VideogameAsset,
                                                    isSelected = selectedLoader == LoaderType.VANILLA,
                                                    onClick = { selectedLoader = LoaderType.VANILLA },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                StudioLoaderCard(
                                                    title = "Fabric",
                                                    description = "Lightweight Mods",
                                                    icon = Icons.Default.Extension,
                                                    isSelected = selectedLoader == LoaderType.FABRIC,
                                                    onClick = { selectedLoader = LoaderType.FABRIC },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                StudioLoaderCard(
                                                    title = "OptiFine",
                                                    description = "Shaders & FPS",
                                                    icon = Icons.Default.Layers,
                                                    isSelected = selectedLoader == LoaderType.OPTIFINE,
                                                    onClick = { selectedLoader = LoaderType.OPTIFINE },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }

                                            // Sub-configuration for Fabric / OptiFine
                                            if (selectedLoader == LoaderType.FABRIC) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color(0xFF141414))
                                                        .border(1.dp, Color(0xFF222222), RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("Fabric Loader Version", color = Color(0xFF888888), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                                    if (isLoadingFabricLoaders) {
                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Color.White)
                                                            Text("Resolving compatible loader...", color = Color(0xFF666666), fontSize = 11.sp)
                                                        }
                                                    } else if (fabricLoaders.isEmpty()) {
                                                        Text("No compatible Fabric loader for $selectedMcVersion", color = Color(0xFFEF5350), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    } else {
                                                        Text(
                                                            text = selectedFabricLoader ?: "Latest Stable",
                                                            color = Color.White,
                                                            fontSize = 11.5.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            } else if (selectedLoader == LoaderType.OPTIFINE) {
                                                val isSupported = OptiFineCompatibilityValidator.isVersionSupported(selectedMcVersion)
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color(0xFF141414))
                                                        .border(1.dp, Color(0xFF222222), RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("OptiFine Edition", color = Color(0xFF888888), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                                    if (!isSupported) {
                                                        Text("Not supported for $selectedMcVersion", color = Color(0xFFEF5350), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    } else {
                                                        Text(
                                                            text = selectedOptiFineVersion ?: "HD_U_I7",
                                                            color = Color.White,
                                                            fontSize = 11.5.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 3. MINECRAFT VERSION CATALOG (RELEASES ONLY)
                                    StudioCard(title = "MINECRAFT RELEASE VERSION") {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            // Search Input
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(34.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF141414))
                                                    .border(1.dp, Color(0xFF242424), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 10.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF666666), modifier = Modifier.size(14.dp))
                                                    TextField(
                                                        value = versionSearchQuery,
                                                        onValueChange = { versionSearchQuery = it },
                                                        placeholder = { Text("Search release versions (e.g. 1.21, 1.20, 1.12, 1.8)...", color = Color(0xFF444444), fontSize = 11.5.sp) },
                                                        colors = TextFieldDefaults.colors(
                                                            focusedContainerColor = Color.Transparent,
                                                            unfocusedContainerColor = Color.Transparent,
                                                            focusedTextColor = Color.White,
                                                            unfocusedTextColor = Color.White,
                                                            focusedIndicatorColor = Color.Transparent,
                                                            unfocusedIndicatorColor = Color.Transparent
                                                        ),
                                                        singleLine = true,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    if (versionSearchQuery.isNotBlank()) {
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "Clear",
                                                            tint = Color(0xFF888888),
                                                            modifier = Modifier
                                                                .size(14.dp)
                                                                .clickable { versionSearchQuery = "" }
                                                        )
                                                    }
                                                }
                                            }

                                            // Latest Release Quick Pick Banner
                                            if (latestReleaseId.isNotBlank() && versionSearchQuery.isBlank()) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color(0xFF121B12))
                                                        .border(1.dp, Color(0xFF233B23), RoundedCornerShape(6.dp))
                                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(12.dp))
                                                        Text("Latest Release: Minecraft $latestReleaseId", color = Color(0xFFA5D6A7), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(Color(0xFF233B23))
                                                            .clickable { selectedMcVersion = latestReleaseId }
                                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("Use Latest", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }

                                            // Scrollable Version Catalog
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(130.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFF0F0F0F))
                                                    .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                                            ) {
                                                if (isManifestLoading) {
                                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                                                            Text("Loading official version manifest...", color = Color(0xFF777777), fontSize = 11.sp)
                                                        }
                                                    }
                                                } else if (manifestError != null && filteredVersions.isEmpty()) {
                                                    Box(modifier = Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
                                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            Text(manifestError ?: "Unable to fetch versions", color = Color(0xFFEF9A9A), fontSize = 11.sp)
                                                            Button(
                                                                onClick = { viewModel.refreshAvailableVersions(forceRefresh = true) },
                                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1515), contentColor = Color.White),
                                                                shape = RoundedCornerShape(4.dp),
                                                                modifier = Modifier.height(26.dp)
                                                            ) {
                                                                Text("Retry", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    }
                                                } else if (filteredVersions.isEmpty()) {
                                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                        Text("No versions match '$versionSearchQuery'", color = Color(0xFF555555), fontSize = 11.sp)
                                                    }
                                                } else {
                                                    LazyColumn(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                                                        items(filteredVersions, key = { it.id }) { ver ->
                                                            val isSelected = selectedMcVersion == ver.id
                                                            val reqJava = JavaCompatibility.getRequiredJavaMajorVersion(ver.id)
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .clip(RoundedCornerShape(6.dp))
                                                                    .background(if (isSelected) Color(0xFF222222) else Color.Transparent)
                                                                    .border(1.dp, if (isSelected) Color(0xFF444444) else Color.Transparent, RoundedCornerShape(6.dp))
                                                                    .clickable { selectedMcVersion = ver.id }
                                                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                    Text(
                                                                        text = ver.id,
                                                                        color = if (isSelected) Color.White else Color(0xFFBBBBBB),
                                                                        fontSize = 12.sp,
                                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                                    )
                                                                    if (ver.id == latestReleaseId) {
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .clip(RoundedCornerShape(3.dp))
                                                                                .background(Color(0xFF1E281E))
                                                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                                                        ) {
                                                                            Text("LATEST", color = Color(0xFF4CAF50), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                                        }
                                                                    }
                                                                }

                                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                    Text("Java $reqJava", color = Color(0xFF555555), fontSize = 10.sp)
                                                                    Text(ver.releaseTime.take(10), color = Color(0xFF444444), fontSize = 10.sp)
                                                                    if (isSelected) {
                                                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 4. RUNTIME (JAVA & RAM)
                                    StudioCard(title = "JAVA RUNTIME & RAM") {
                                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            // Java Info Row
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text("Java Environment", color = Color(0xFF888888), fontSize = 10.sp)
                                                    Text("Recommended: Java $requiredJavaMajor (${JavaCompatibility.getJavaRequirementDescription(requiredJavaMajor)})", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0xFF1E281E))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text("AUTO-RESOLVED", color = Color(0xFF4CAF50), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            // Installed Java Runtime Quick Chips
                                            if (detectedJavaRuntimes.isNotEmpty()) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    val isAuto = selectedJavaPath == null
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(if (isAuto) Color(0xFF262626) else Color(0xFF141414))
                                                            .border(1.dp, if (isAuto) Color.White else Color(0xFF222222), RoundedCornerShape(4.dp))
                                                            .clickable { selectedJavaPath = null }
                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Text("Auto (Java $requiredJavaMajor)", color = if (isAuto) Color.White else Color(0xFF777777), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }

                                                    detectedJavaRuntimes.take(3).forEach { rt ->
                                                        val isSelected = selectedJavaPath == rt.path
                                                        val isCompatible = JavaCompatibility.isJavaVersionCompatible(rt.majorVersion, requiredJavaMajor)
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(if (isSelected) Color(0xFF262626) else Color(0xFF141414))
                                                                .border(1.dp, if (isSelected) Color.White else Color(0xFF222222), RoundedCornerShape(4.dp))
                                                                .clickable { selectedJavaPath = rt.path }
                                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                        ) {
                                                            Text(
                                                                "Java ${rt.majorVersion}${if (isCompatible) " ✓" else ""}",
                                                                color = if (isSelected) Color.White else if (isCompatible) Color(0xFFB0BEC5) else Color(0xFF78909C),
                                                                fontSize = 10.sp,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            // RAM Allocation with Quick Presets & Slider
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("RAM Allocation", color = Color(0xFF888888), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                                    Text(
                                                        text = "${maxRamMb.toInt()} MB (${maxRamMb.toInt() / 1024} GB)",
                                                        color = Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }

                                                // Quick Presets Row
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    listOf(2048 to "2 GB", 4096 to "4 GB", 6144 to "6 GB", 8192 to "8 GB", 12288 to "12 GB").forEach { (mb, label) ->
                                                        val isSelected = maxRamMb.toInt() == mb
                                                        Box(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(if (isSelected) Color(0xFF262626) else Color(0xFF141414))
                                                                .border(1.dp, if (isSelected) Color(0xFF444444) else Color(0xFF222222), RoundedCornerShape(4.dp))
                                                                .clickable { maxRamMb = mb.toFloat() }
                                                                .padding(vertical = 4.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = label,
                                                                color = if (isSelected) Color.White else Color(0xFF777777),
                                                                fontSize = 10.sp,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                            )
                                                        }
                                                    }
                                                }

                                                Slider(
                                                    value = maxRamMb,
                                                    onValueChange = { maxRamMb = it },
                                                    valueRange = 1024f..16384f,
                                                    steps = 15,
                                                    colors = SliderDefaults.colors(
                                                        thumbColor = Color.White,
                                                        activeTrackColor = Color.White,
                                                        inactiveTrackColor = Color(0xFF222222)
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    // 5. ADVANCED OPTIONS (Accordion)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF101010))
                                            .border(1.dp, Color(0xFF202020), RoundedCornerShape(10.dp))
                                            .padding(12.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { isAdvancedExpanded = !isAdvancedExpanded },
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFF777777), modifier = Modifier.size(13.dp))
                                                    Text("ADVANCED SETTINGS", color = Color(0xFFBBBBBB), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                                }
                                                Icon(
                                                    imageVector = if (isAdvancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                    contentDescription = null,
                                                    tint = Color(0xFF777777),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            AnimatedVisibility(
                                                visible = isAdvancedExpanded,
                                                enter = expandVertically() + fadeIn(),
                                                exit = shrinkVertically() + fadeOut()
                                            ) {
                                                Column(
                                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                                    modifier = Modifier.padding(top = 6.dp)
                                                ) {
                                                    // Window Resolution
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                            Text("Width", color = Color(0xFF777777), fontSize = 10.sp)
                                                            io.ezz.launcher.ui.components.EzzTextField(
                                                                value = windowWidth.toString(),
                                                                onValueChange = { windowWidth = it.toIntOrNull() ?: 1280 },
                                                                modifier = Modifier.fillMaxWidth()
                                                            )
                                                        }
                                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                            Text("Height", color = Color(0xFF777777), fontSize = 10.sp)
                                                            io.ezz.launcher.ui.components.EzzTextField(
                                                                value = windowHeight.toString(),
                                                                onValueChange = { windowHeight = it.toIntOrNull() ?: 720 },
                                                                modifier = Modifier.fillMaxWidth()
                                                            )
                                                        }
                                                    }

                                                    // Fullscreen Toggle
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text("Launch in Fullscreen Mode", color = Color(0xFFBBBBBB), fontSize = 11.sp)
                                                        EzzToggle(
                                                            checked = isFullscreen,
                                                            onCheckedChange = { isFullscreen = it }
                                                        )
                                                    }

                                                    // Custom JVM Args
                                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                        Text("Custom JVM Arguments", color = Color(0xFF777777), fontSize = 10.sp)
                                                        io.ezz.launcher.ui.components.EzzTextField(
                                                            value = customJvmArgs,
                                                            onValueChange = { customJvmArgs = it },
                                                            placeholder = "e.g. -XX:+UseG1GC",
                                                            modifier = Modifier.fillMaxWidth()
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // RIGHT COLUMN (~36%): Hero Live Preview Panel
                                Column(
                                    modifier = Modifier
                                        .weight(0.36f)
                                        .fillMaxHeight(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(
                                            text = "LIVE PREVIEW",
                                            color = Color(0xFF666666),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp
                                        )

                                        // Hero Preview Card
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(Color(0xFF161616), Color(0xFF0D0D0D))
                                                    )
                                                )
                                                .border(1.dp, Color(0xFF262626), RoundedCornerShape(12.dp))
                                                .padding(18.dp)
                                        ) {
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                // Center Artwork Icon
                                                InstanceArtworkIcon(
                                                    instance = Instance(
                                                        id = "preview_hero",
                                                        name = if (name.isBlank()) "Minecraft $selectedMcVersion" else name,
                                                        minecraftVersion = selectedMcVersion,
                                                        loaderType = selectedLoader
                                                    ),
                                                    size = 72.dp,
                                                    customFile = customIconFile,
                                                    showBadge = true
                                                )

                                                // Title & Subtitle
                                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text(
                                                        text = if (name.isBlank()) "New Instance" else name,
                                                        color = Color.White,
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Black,
                                                        maxLines = 1
                                                    )
                                                    Text(
                                                        text = "Minecraft $selectedMcVersion",
                                                        color = Color(0xFF888888),
                                                        fontSize = 11.5.sp
                                                    )
                                                }

                                                // Dynamic Badges Row
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    EzzBadge(
                                                        text = selectedLoader.name,
                                                        variant = when (selectedLoader) {
                                                            LoaderType.FABRIC -> EzzBadgeVariant.SUCCESS
                                                            LoaderType.OPTIFINE -> EzzBadgeVariant.INFO
                                                            LoaderType.VANILLA -> EzzBadgeVariant.NEUTRAL
                                                        }
                                                    )
                                                    EzzBadge(
                                                        text = "Java $requiredJavaMajor",
                                                        variant = EzzBadgeVariant.NEUTRAL
                                                    )
                                                    EzzBadge(
                                                        text = "${maxRamMb.toInt() / 1024} GB",
                                                        variant = EzzBadgeVariant.NEUTRAL
                                                    )
                                                }

                                                // Metadata Breakdown
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color(0xFF080808))
                                                        .border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(8.dp))
                                                        .padding(10.dp),
                                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                                ) {
                                                    StudioMetaRow("Engine", selectedLoader.name)
                                                    if (selectedLoader == LoaderType.FABRIC && selectedFabricLoader != null) {
                                                        StudioMetaRow("Fabric Loader", selectedFabricLoader ?: "")
                                                    } else if (selectedLoader == LoaderType.OPTIFINE && selectedOptiFineVersion != null) {
                                                        StudioMetaRow("OptiFine", selectedOptiFineVersion ?: "")
                                                    }
                                                    StudioMetaRow("RAM Limit", "${maxRamMb.toInt()} MB")
                                                    StudioMetaRow("Local Path", "instances/${trimmedName.ifBlank { "new_instance" }}")
                                                }
                                            }
                                        }

                                        // Status / Validation Alert Box
                                        if (validationError != null) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF261212))
                                                    .border(1.dp, Color(0xFFEF5350).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(13.dp))
                                                    Text(validationError, color = Color(0xFFEF9A9A), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                                }
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF111E11))
                                                    .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(13.dp))
                                                    Text("Configuration ready to launch", color = Color(0xFFA5D6A7), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                                }
                                            }
                                        }
                                    }

                                    // Bottom Action Buttons
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = onDismiss,
                                            modifier = Modifier.weight(1f).height(44.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF888888)),
                                            border = BorderStroke(1.dp, Color(0xFF282828))
                                        ) {
                                            Text("Cancel", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = {
                                                if (isFormReady) {
                                                    coroutineScope.launch {
                                                        try {
                                                            creationStep = 1
                                                            creationProgressText = "Preparing isolated directory..."
                                                            delay(300)

                                                            creationStep = 2
                                                            creationProgressText = "Resolving Minecraft $selectedMcVersion manifest..."
                                                            delay(250)

                                                            creationStep = 3
                                                            creationProgressText = "Configuring ${selectedLoader.name} engine..."
                                                            delay(250)

                                                            val finalLoaderVersion = when (selectedLoader) {
                                                                LoaderType.FABRIC -> selectedFabricLoader
                                                                LoaderType.OPTIFINE -> selectedOptiFineVersion
                                                                LoaderType.VANILLA -> null
                                                            }

                                                            creationStep = 4
                                                            creationProgressText = "Registering instance locally..."

                                                            viewModel.createInstance(
                                                                name = trimmedName,
                                                                minecraftVersion = selectedMcVersion,
                                                                loaderType = selectedLoader,
                                                                loaderVersion = finalLoaderVersion,
                                                                minMemoryMb = 1024,
                                                                maxMemoryMb = maxRamMb.toInt(),
                                                                customJvmArgs = customJvmArgs.split(" ").filter { it.isNotBlank() },
                                                                javaPath = selectedJavaPath,
                                                                windowWidth = windowWidth,
                                                                windowHeight = windowHeight,
                                                                customIconFile = customIconFile,
                                                                onSuccess = {
                                                                    createdInstanceResult = viewModel.instances.value.find { it.name == trimmedName }
                                                                        ?: Instance(
                                                                            id = "created_temp",
                                                                            name = trimmedName,
                                                                            minecraftVersion = selectedMcVersion,
                                                                            loaderType = selectedLoader,
                                                                            loaderVersion = finalLoaderVersion,
                                                                            maxMemoryMb = maxRamMb.toInt()
                                                                        )
                                                                    creationStep = 5
                                                                }
                                                            )
                                                        } catch (e: Exception) {
                                                            creationErrorMessage = "Failed to create instance: ${e.message}"
                                                        }
                                                    }
                                                }
                                            },
                                            enabled = isFormReady,
                                            modifier = Modifier.weight(1.5f).height(44.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color.White,
                                                contentColor = Color.Black,
                                                disabledContainerColor = Color(0xFF1E1E1E),
                                                disabledContentColor = Color(0xFF555555)
                                            )
                                        ) {
                                            Text("CREATE INSTANCE", fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioProgressView(
    instanceName: String,
    minecraftVersion: String,
    loaderType: LoaderType,
    currentStep: Int,
    progressText: String,
    errorMessage: String?,
    onRetry: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.widthIn(max = 440.dp)
        ) {
            if (errorMessage != null) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(42.dp))
                Text("Creation Failed", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(errorMessage, color = Color(0xFFEF9A9A), fontSize = 12.sp)
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Back to Configuration", fontWeight = FontWeight.Bold)
                }
            } else {
                CircularProgressIndicator(modifier = Modifier.size(44.dp), strokeWidth = 3.dp, color = Color.White)
                Text("Creating $instanceName...", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black, letterSpacing = 0.4.sp)
                Text("Minecraft $minecraftVersion • ${loaderType.name}", color = Color(0xFF777777), fontSize = 12.sp)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF121212))
                        .border(1.dp, Color(0xFF222222), RoundedCornerShape(8.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudioProgressStepRow("1. Preparing isolated instance directory", isDone = currentStep > 1, isActive = currentStep == 1)
                    StudioProgressStepRow("2. Resolving official version manifest", isDone = currentStep > 2, isActive = currentStep == 2)
                    StudioProgressStepRow("3. Configuring ${loaderType.name} loader engine", isDone = currentStep > 3, isActive = currentStep == 3)
                    StudioProgressStepRow("4. Finalizing local registration", isDone = currentStep > 4, isActive = currentStep == 4)
                }

                Text(progressText, color = Color(0xFF888888), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun StudioProgressStepRow(label: String, isDone: Boolean, isActive: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (isDone || isActive) Color.White else Color(0xFF555555),
            fontSize = 11.5.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
        if (isDone) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(13.dp))
        } else if (isActive) {
            CircularProgressIndicator(modifier = Modifier.size(11.dp), strokeWidth = 1.5.dp, color = Color.White)
        }
    }
}

@Composable
private fun StudioSuccessView(
    instance: Instance,
    customIconFile: File?,
    onPlayNow: () -> Unit,
    onOpenInstance: () -> Unit,
    onDone: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.widthIn(max = 440.dp)
        ) {
            InstanceArtworkIcon(
                instance = instance,
                size = 72.dp,
                customFile = customIconFile,
                showBadge = true
            )

            Text("INSTANCE CREATED SUCCESSFULLY", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp)
            Text(
                text = "${instance.name} is ready with Minecraft ${instance.minecraftVersion} (${instance.loaderType.name})",
                color = Color(0xFF888888),
                fontSize = 12.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPlayNow,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("PLAY NOW", fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }

                OutlinedButton(
                    onClick = onOpenInstance,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFF333333))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("MANAGE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = onDone,
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF888888)),
                    border = BorderStroke(1.dp, Color(0xFF222222))
                ) {
                    Text("DONE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StudioCard(
    title: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF111111))
            .border(1.dp, Color(0xFF202020), RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                color = Color(0xFF888888),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
            content()
        }
    }
}

@Composable
private fun StudioLoaderCard(
    title: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(if (isSelected) Color(0xFF222222) else Color(0xFF131313))
    val borderCol by animateColorAsState(if (isSelected) Color.White else Color(0xFF222222))

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, borderCol, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else Color(0xFF666666),
                    modifier = Modifier.size(15.dp)
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
            Text(
                text = title,
                color = if (isSelected) Color.White else Color(0xFF999999),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = Color(0xFF555555),
                fontSize = 9.5.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StudioMetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color(0xFF666666), fontSize = 10.sp)
        Text(text = value, color = Color(0xFFCCCCCC), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}
