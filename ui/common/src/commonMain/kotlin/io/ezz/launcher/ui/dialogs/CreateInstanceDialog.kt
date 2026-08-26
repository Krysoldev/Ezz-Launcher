package io.ezz.launcher.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.ui.window.Dialog
import io.ezz.launcher.core.minecraft.loader.optifine.OptiFineCompatibilityValidator
import io.ezz.launcher.core.minecraft.loader.optifine.OptiFineVersionOption
import io.ezz.launcher.core.minecraft.version.JavaCompatibility
import io.ezz.launcher.core.minecraft.version.MinecraftVersionComparator
import io.ezz.launcher.core.minecraft.version.VersionSortOrder
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.model.minecraft.VersionSummary
import io.ezz.launcher.core.model.runtime.JavaRuntime
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
 * Ezz Launcher — Create Instance V3
 * Complete ground-up rebuild featuring the full Mojang Java release version catalog,
 * semantic version grouping & search, dynamic loaders, smart Java & RAM, live preview,
 * and multi-stage creation pipeline.
 */
@Composable
fun CreateInstanceDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val existingInstances by viewModel.instances.collectAsState()
    val availableReleases by viewModel.availableVersions.collectAsState()
    val snapshotVersions by viewModel.snapshotVersions.collectAsState()
    val betaVersions by viewModel.betaVersions.collectAsState()
    val alphaVersions by viewModel.alphaVersions.collectAsState()
    val allVersions by viewModel.allVersions.collectAsState()
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

    // 2. Version Filter State
    var selectedCategory by remember { mutableStateOf("RELEASE") } // "RELEASE", "SNAPSHOT", "BETA", "ALPHA", "ALL"
    var versionSearchQuery by remember { mutableStateOf("") }
    var expandedGroupPrefix by remember { mutableStateOf<String?>("1.21") }

    // Resolve current source version list based on active tab
    val currentSourceVersions = remember(selectedCategory, availableReleases, snapshotVersions, betaVersions, alphaVersions, allVersions) {
        when (selectedCategory) {
            "RELEASE" -> availableReleases
            "SNAPSHOT" -> snapshotVersions
            "BETA" -> betaVersions
            "ALPHA" -> alphaVersions
            "ALL" -> allVersions
            else -> availableReleases
        }
    }

    // Filter by search query (instant local search across official metadata)
    val filteredVersions = remember(currentSourceVersions, versionSearchQuery) {
        val q = versionSearchQuery.trim().lowercase()
        val list = if (q.isBlank()) {
            currentSourceVersions
        } else {
            currentSourceVersions.filter { it.id.lowercase().contains(q) }
        }
        MinecraftVersionComparator.sort(list, VersionSortOrder.NEWEST_FIRST)
    }

    // Group filtered versions by major.minor prefix (e.g. "1.21", "1.20", "1.12", "1.8", "Beta 1.7")
    val groupedVersions = remember(filteredVersions) {
        filteredVersions.groupBy { ver ->
            val id = ver.id
            when {
                id.startsWith("1.") -> {
                    val parts = id.split(".")
                    if (parts.size >= 2) "${parts[0]}.${parts[1]}" else id
                }
                id.startsWith("b1.") || id.startsWith("b") -> "Beta"
                id.startsWith("a1.") || id.startsWith("a") -> "Alpha"
                id.contains("w") -> "Snapshot ${id.take(3)}"
                else -> "Other"
            }
        }
    }

    // Selected Minecraft Version (Defaults to latest official release e.g. 1.21.4)
    var selectedMcVersion by remember(availableReleases, latestReleaseId) {
        mutableStateOf(latestReleaseId.ifBlank { availableReleases.firstOrNull()?.id ?: "1.21.4" })
    }

    // 3. Mod Loader Selection
    var selectedLoader by remember { mutableStateOf(LoaderType.VANILLA) }

    // Fabric Loader State
    var fabricLoaders by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedFabricLoader by remember { mutableStateOf<String?>(null) }
    var isLoadingFabricLoaders by remember { mutableStateOf(false) }

    // OptiFine State
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

    // 6. Creation Progress & Success States
    var creationStep by remember { mutableStateOf(0) } // 0: Idle, 1: Prep, 2: Metadata, 3: Loader, 4: Finalizing, 5: Done
    var creationProgressText by remember { mutableStateOf("") }
    var createdInstanceResult by remember { mutableStateOf<Instance?>(null) }
    var creationErrorMessage by remember { mutableStateOf<String?>(null) }

    // Initial version refresh if list is empty
    LaunchedEffect(Unit) {
        if (availableReleases.isEmpty()) {
            viewModel.refreshAvailableVersions(forceRefresh = false)
        }
    }

    // Auto-update instance name suggestions
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

    // Dynamic OptiFine discovery from verified catalog
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
        isNameEmpty -> "Instance name cannot be empty"
        hasInvalidFsChars -> "Instance name contains invalid characters (/ \\ : * ? \" < > |)"
        isDuplicateName -> "An instance with this name already exists. Choose a unique name."
        isFabricIncompatible -> "Fabric loader is not available for Minecraft $selectedMcVersion"
        isOptiFineIncompatible -> "OptiFine is not supported for Minecraft $selectedMcVersion"
        else -> null
    }
    val isReadyToCreate = validationError == null && creationStep == 0

    Dialog(onDismissRequest = { if (creationStep == 0 || creationStep == 5) onDismiss() }) {
        Box(
            modifier = Modifier
                .widthIn(min = 880.dp, max = 1020.dp)
                .heightIn(min = 600.dp, max = 740.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0C0C0C))
                .border(1.dp, Color(0xFF262626), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Crossfade(targetState = creationStep) { step ->
                when {
                    // Success View (Step 5)
                    step == 5 && createdInstanceResult != null -> {
                        val inst = createdInstanceResult!!
                        SuccessCreationView(
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

                    // Progress State (Step 1..4)
                    step in 1..4 -> {
                        CreationProgressView(
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

                    // Main Creation Form (Step 0)
                    else -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Dialog Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "CREATE NEW INSTANCE",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.8.sp
                                    )
                                    Text(
                                        text = "Create a localized, isolated Minecraft Java Edition installation",
                                        color = Color(0xFF888888),
                                        fontSize = 12.sp
                                    )
                                }

                                EzzIconButton(
                                    icon = Icons.Default.Close,
                                    onClick = onDismiss,
                                    size = io.ezz.launcher.ui.components.EzzButtonSize.SMALL,
                                    variant = io.ezz.launcher.ui.components.EzzButtonVariant.GHOST
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // 2-Column Responsive Body
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                // LEFT / MAIN COLUMN (~65%)
                                val scrollState = rememberScrollState()
                                Column(
                                    modifier = Modifier
                                        .weight(0.66f)
                                        .fillMaxHeight()
                                        .verticalScroll(scrollState),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    // SECTION 1: INSTANCE IDENTITY
                                    V3SectionCard(title = "INSTANCE IDENTITY") {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            // Icon Picker Thumbnail Box
                                            Box(
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color(0xFF161616))
                                                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(10.dp))
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
                                                    size = 64.dp,
                                                    customFile = customIconFile,
                                                    showBadge = false
                                                )
                                            }

                                            // Name Input + Upload Info
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text("Instance Name", color = Color(0xFFAAAAAA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                TextField(
                                                    value = name,
                                                    onValueChange = {
                                                        name = it
                                                        isUserCustomName = true
                                                    },
                                                    modifier = Modifier.fillMaxWidth().height(46.dp),
                                                    placeholder = { Text("e.g. Survival SMP, PvP Modded", color = Color(0xFF555555), fontSize = 13.sp) },
                                                    shape = RoundedCornerShape(6.dp),
                                                    colors = TextFieldDefaults.colors(
                                                        focusedContainerColor = Color(0xFF141414),
                                                        unfocusedContainerColor = Color(0xFF141414),
                                                        focusedTextColor = Color.White,
                                                        unfocusedTextColor = Color.White,
                                                        focusedIndicatorColor = Color.Transparent,
                                                        unfocusedIndicatorColor = Color.Transparent
                                                    ),
                                                    singleLine = true
                                                )
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text(
                                                        text = if (customIconFile != null) "Custom Icon: ${customIconFile?.name}" else "Click icon square to upload custom PNG/JPG",
                                                        color = Color(0xFF666666),
                                                        fontSize = 10.sp
                                                    )
                                                    if (customIconFile != null) {
                                                        Text(
                                                            text = "• Remove",
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

                                    // SECTION 2: MINECRAFT VERSION SELECTOR (Official Mojang Catalog)
                                    V3SectionCard(title = "MINECRAFT VERSION (OFFICIAL MOJANG CATALOG)") {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            // Top Bar: Filter Tabs & Search
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Category Tabs
                                                Row(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color(0xFF161616))
                                                        .padding(2.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    listOf("RELEASE", "SNAPSHOT", "BETA", "ALPHA", "ALL").forEach { cat ->
                                                        val isSelected = selectedCategory == cat
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(if (isSelected) Color(0xFF2C2C2C) else Color.Transparent)
                                                                .clickable { selectedCategory = cat }
                                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                        ) {
                                                            Text(
                                                                text = cat,
                                                                color = if (isSelected) Color.White else Color(0xFF888888),
                                                                fontSize = 10.5.sp,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                            )
                                                        }
                                                    }
                                                }

                                                // Search Field
                                                Box(
                                                    modifier = Modifier
                                                        .width(160.dp)
                                                        .height(30.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color(0xFF161616))
                                                        .border(1.dp, Color(0xFF262626), RoundedCornerShape(6.dp))
                                                        .padding(horizontal = 8.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF666666), modifier = Modifier.size(12.dp))
                                                        TextField(
                                                            value = versionSearchQuery,
                                                            onValueChange = { versionSearchQuery = it },
                                                            placeholder = { Text("Search versions...", color = Color(0xFF555555), fontSize = 11.sp) },
                                                            colors = TextFieldDefaults.colors(
                                                                focusedContainerColor = Color.Transparent,
                                                                unfocusedContainerColor = Color.Transparent,
                                                                focusedTextColor = Color.White,
                                                                unfocusedTextColor = Color.White,
                                                                focusedIndicatorColor = Color.Transparent,
                                                                unfocusedIndicatorColor = Color.Transparent
                                                            ),
                                                            singleLine = true,
                                                            modifier = Modifier.fillMaxWidth()
                                                        )
                                                    }
                                                }
                                            }

                                            // Latest Release Quick Shortcut Banner
                                            if (latestReleaseId.isNotBlank() && selectedCategory == "RELEASE" && versionSearchQuery.isBlank()) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color(0xFF182018))
                                                        .border(1.dp, Color(0xFF2E4C2E), RoundedCornerShape(6.dp))
                                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                                                        Text("LATEST RELEASE: Minecraft $latestReleaseId", color = Color(0xFFA5D6A7), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(Color(0xFF2E4C2E))
                                                            .clickable { selectedMcVersion = latestReleaseId }
                                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                                    ) {
                                                        Text("Use Latest", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }

                                            // Grouped Version Catalog Scroll List
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(130.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF101010))
                                                    .border(1.dp, Color(0xFF202020), RoundedCornerShape(6.dp))
                                            ) {
                                                if (isManifestLoading) {
                                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                                                            Text("Loading official Mojang catalog...", color = Color(0xFF888888), fontSize = 11.sp)
                                                        }
                                                    }
                                                } else if (manifestError != null && filteredVersions.isEmpty()) {
                                                    Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            Text(manifestError ?: "Unable to load Minecraft versions.", color = Color(0xFFEF9A9A), fontSize = 11.sp)
                                                            Button(
                                                                onClick = { viewModel.refreshAvailableVersions(forceRefresh = true) },
                                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1515), contentColor = Color.White),
                                                                shape = RoundedCornerShape(4.dp),
                                                                modifier = Modifier.height(28.dp)
                                                            ) {
                                                                Text("RETRY", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    }
                                                } else if (filteredVersions.isEmpty()) {
                                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                        Text("No Minecraft versions match '$versionSearchQuery'", color = Color(0xFF666666), fontSize = 11.sp)
                                                    }
                                                } else {
                                                    LazyColumn(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                                                        groupedVersions.forEach { (groupKey, versionsInGroup) ->
                                                            val isGroupExpanded = expandedGroupPrefix == groupKey || versionSearchQuery.isNotBlank() || groupedVersions.size <= 2

                                                            // Group Header Item
                                                            item(key = "header_$groupKey") {
                                                                Row(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .clip(RoundedCornerShape(4.dp))
                                                                        .background(Color(0xFF181818))
                                                                        .clickable {
                                                                            expandedGroupPrefix = if (expandedGroupPrefix == groupKey) null else groupKey
                                                                        }
                                                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                        Icon(
                                                                            imageVector = if (isGroupExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                                            contentDescription = null,
                                                                            tint = Color(0xFF888888),
                                                                            modifier = Modifier.size(14.dp)
                                                                        )
                                                                        Text(
                                                                            text = if (groupKey.startsWith("1.")) "Minecraft $groupKey" else groupKey,
                                                                            color = Color(0xFFCCCCCC),
                                                                            fontSize = 11.sp,
                                                                            fontWeight = FontWeight.Bold
                                                                        )
                                                                        Text(
                                                                            text = "(${versionsInGroup.size})",
                                                                            color = Color(0xFF666666),
                                                                            fontSize = 10.sp
                                                                        )
                                                                    }
                                                                }
                                                            }

                                                            // Group Children Items
                                                            if (isGroupExpanded) {
                                                                items(versionsInGroup, key = { it.id }) { ver ->
                                                                    val isSelected = selectedMcVersion == ver.id
                                                                    val reqJava = JavaCompatibility.getRequiredJavaMajorVersion(ver.id)
                                                                    Row(
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .clip(RoundedCornerShape(4.dp))
                                                                            .background(if (isSelected) Color(0xFF262626) else Color.Transparent)
                                                                            .clickable { selectedMcVersion = ver.id }
                                                                            .padding(start = 24.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
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
                                                                                    Text("LATEST", color = Color(0xFF4CAF50), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                                                                }
                                                                            }
                                                                        }

                                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                            Text("Java $reqJava", color = Color(0xFF666666), fontSize = 10.sp)
                                                                            Text(ver.releaseTime.take(10), color = Color(0xFF555555), fontSize = 10.sp)
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
                                        }
                                    }

                                    // SECTION 3: MOD LOADER SELECTION
                                    V3SectionCard(title = "MOD LOADER") {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                V3LoaderCard(
                                                    title = "VANILLA",
                                                    subtitle = "Official Minecraft",
                                                    icon = Icons.Default.VideogameAsset,
                                                    isSelected = selectedLoader == LoaderType.VANILLA,
                                                    onClick = { selectedLoader = LoaderType.VANILLA },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                V3LoaderCard(
                                                    title = "FABRIC",
                                                    subtitle = "Lightweight Mods",
                                                    icon = Icons.Default.Extension,
                                                    isSelected = selectedLoader == LoaderType.FABRIC,
                                                    onClick = { selectedLoader = LoaderType.FABRIC },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                V3LoaderCard(
                                                    title = "OPTIFINE",
                                                    subtitle = "Shaders & FPS",
                                                    icon = Icons.Default.Layers,
                                                    isSelected = selectedLoader == LoaderType.OPTIFINE,
                                                    onClick = { selectedLoader = LoaderType.OPTIFINE },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }

                                            // Loader Sub-configuration
                                            if (selectedLoader == LoaderType.FABRIC) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color(0xFF141414))
                                                        .border(1.dp, Color(0xFF242424), RoundedCornerShape(6.dp))
                                                        .padding(10.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("Fabric Loader Version", color = Color(0xFFAAAAAA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    if (isLoadingFabricLoaders) {
                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Color.White)
                                                            Text("Resolving compatible loader...", color = Color(0xFF888888), fontSize = 11.sp)
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
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color(0xFF141414))
                                                        .border(1.dp, Color(0xFF242424), RoundedCornerShape(6.dp))
                                                        .padding(10.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("OptiFine Version", color = Color(0xFFAAAAAA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    if (!isSupported) {
                                                        Text("Not compatible with $selectedMcVersion", color = Color(0xFFEF5350), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

                                    // SECTION 4: RUNTIME (JAVA & RAM)
                                    V3SectionCard(title = "JAVA RUNTIME & RAM") {
                                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                            // Java Info Row
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text("Recommended Java Requirement", color = Color(0xFFAAAAAA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    Text("Java $requiredJavaMajor (${JavaCompatibility.getJavaRequirementDescription(requiredJavaMajor)})", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0xFF1E281E))
                                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                                ) {
                                                    Text("AUTO-RESOLVED", color = Color(0xFF4CAF50), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            // Installed Java Runtime Quick Chips
                                            if (detectedJavaRuntimes.isNotEmpty()) {
                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text("Installed Java Runtimes", color = Color(0xFF888888), fontSize = 10.sp)
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        val isAuto = selectedJavaPath == null
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(if (isAuto) Color(0xFF282828) else Color(0xFF141414))
                                                                .border(1.dp, if (isAuto) Color.White else Color(0xFF262626), RoundedCornerShape(4.dp))
                                                                .clickable { selectedJavaPath = null }
                                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                        ) {
                                                            Text("Auto (Java $requiredJavaMajor)", color = if (isAuto) Color.White else Color(0xFF888888), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        }

                                                        detectedJavaRuntimes.take(3).forEach { rt ->
                                                            val isSelected = selectedJavaPath == rt.path
                                                            val isCompatible = JavaCompatibility.isJavaVersionCompatible(rt.majorVersion, requiredJavaMajor)
                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(if (isSelected) Color(0xFF282828) else Color(0xFF141414))
                                                                    .border(1.dp, if (isSelected) Color.White else Color(0xFF262626), RoundedCornerShape(4.dp))
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
                                            }

                                            // RAM Allocation Slider + Smart Preset Button
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Text("Memory Allocation (RAM)", color = Color(0xFFAAAAAA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(3.dp))
                                                                .background(Color(0xFF202020))
                                                                .clickable {
                                                                    maxRamMb = if (selectedLoader == LoaderType.VANILLA) 2048f else 4096f
                                                                }
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text("Recommended", color = Color(0xFFCCCCCC), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                    Text(
                                                        text = "${maxRamMb.toInt()} MB (${maxRamMb.toInt() / 1024} GB RAM)",
                                                        color = Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
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

                                    // SECTION 5: ADVANCED OPTIONS (Collapsible Accordion)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF111111))
                                            .border(1.dp, Color(0xFF222222), RoundedCornerShape(8.dp))
                                            .padding(12.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                                    Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFF888888), modifier = Modifier.size(14.dp))
                                                    Text("ADVANCED SETTINGS", color = Color(0xFFCCCCCC), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                                                }
                                                Icon(
                                                    imageVector = if (isAdvancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                    contentDescription = null,
                                                    tint = Color(0xFF888888),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            AnimatedVisibility(
                                                visible = isAdvancedExpanded,
                                                enter = expandVertically() + fadeIn(),
                                                exit = shrinkVertically() + fadeOut()
                                            ) {
                                                Column(
                                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                                    modifier = Modifier.padding(top = 8.dp)
                                                ) {
                                                    // Window Resolution
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            Text("Window Width", color = Color(0xFF888888), fontSize = 10.sp)
                                                            TextField(
                                                                value = windowWidth.toString(),
                                                                onValueChange = { windowWidth = it.toIntOrNull() ?: 1280 },
                                                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                                                colors = TextFieldDefaults.colors(
                                                                    focusedContainerColor = Color(0xFF181818),
                                                                    unfocusedContainerColor = Color(0xFF181818),
                                                                    focusedTextColor = Color.White,
                                                                    unfocusedTextColor = Color.White,
                                                                    focusedIndicatorColor = Color.Transparent,
                                                                    unfocusedIndicatorColor = Color.Transparent
                                                                ),
                                                                singleLine = true
                                                            )
                                                        }
                                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            Text("Window Height", color = Color(0xFF888888), fontSize = 10.sp)
                                                            TextField(
                                                                value = windowHeight.toString(),
                                                                onValueChange = { windowHeight = it.toIntOrNull() ?: 720 },
                                                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                                                colors = TextFieldDefaults.colors(
                                                                    focusedContainerColor = Color(0xFF181818),
                                                                    unfocusedContainerColor = Color(0xFF181818),
                                                                    focusedTextColor = Color.White,
                                                                    unfocusedTextColor = Color.White,
                                                                    focusedIndicatorColor = Color.Transparent,
                                                                    unfocusedIndicatorColor = Color.Transparent
                                                                ),
                                                                singleLine = true
                                                            )
                                                        }
                                                    }

                                                    // Fullscreen Toggle
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text("Launch in Fullscreen Mode", color = Color(0xFFCCCCCC), fontSize = 11.sp)
                                                        EzzToggle(
                                                            checked = isFullscreen,
                                                            onCheckedChange = { isFullscreen = it }
                                                        )
                                                    }

                                                    // Custom JVM Args
                                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Text("Custom JVM Arguments", color = Color(0xFF888888), fontSize = 10.sp)
                                                        TextField(
                                                            value = customJvmArgs,
                                                            onValueChange = { customJvmArgs = it },
                                                            placeholder = { Text("e.g. -XX:+UseG1GC", color = Color(0xFF555555), fontSize = 11.sp) },
                                                            modifier = Modifier.fillMaxWidth().height(42.dp),
                                                            colors = TextFieldDefaults.colors(
                                                                focusedContainerColor = Color(0xFF181818),
                                                                unfocusedContainerColor = Color(0xFF181818),
                                                                focusedTextColor = Color.White,
                                                                unfocusedTextColor = Color.White,
                                                                focusedIndicatorColor = Color.Transparent,
                                                                unfocusedIndicatorColor = Color.Transparent
                                                            ),
                                                            singleLine = true
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // RIGHT COLUMN (~34%): Live Instance Preview & Create CTA
                                Column(
                                    modifier = Modifier
                                        .weight(0.34f)
                                        .fillMaxHeight(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(
                                            text = "LIVE INSTANCE PREVIEW",
                                            color = Color(0xFF777777),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp
                                        )

                                        // Real-Time Live Preview Card
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(Color(0xFF181818), Color(0xFF0F0F0F))
                                                    )
                                                )
                                                .border(1.dp, Color(0xFF2C2C2C), RoundedCornerShape(10.dp))
                                                .padding(16.dp)
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                // Icon + Name
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    InstanceArtworkIcon(
                                                        instance = Instance(
                                                            id = "preview_instance",
                                                            name = if (name.isBlank()) "Minecraft $selectedMcVersion" else name,
                                                            minecraftVersion = selectedMcVersion,
                                                            loaderType = selectedLoader
                                                        ),
                                                        size = 56.dp,
                                                        customFile = customIconFile
                                                    )

                                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                        Text(
                                                            text = if (name.isBlank()) "New Instance" else name,
                                                            color = Color.White,
                                                            fontSize = 15.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1
                                                        )
                                                        Text(
                                                            text = "Minecraft $selectedMcVersion",
                                                            color = Color(0xFFAAAAAA),
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }

                                                // Badges Row
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                                        text = "${maxRamMb.toInt() / 1024} GB RAM",
                                                        variant = EzzBadgeVariant.NEUTRAL
                                                    )
                                                }

                                                // Metadata Breakdown
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color(0xFF0A0A0A))
                                                        .border(1.dp, Color(0xFF1C1C1C), RoundedCornerShape(6.dp))
                                                        .padding(10.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    PreviewMetaRow("Target Version", selectedMcVersion)
                                                    PreviewMetaRow("Loader Type", selectedLoader.name)
                                                    if (selectedLoader == LoaderType.FABRIC && selectedFabricLoader != null) {
                                                        PreviewMetaRow("Fabric Loader", selectedFabricLoader ?: "")
                                                    } else if (selectedLoader == LoaderType.OPTIFINE && selectedOptiFineVersion != null) {
                                                        PreviewMetaRow("OptiFine", selectedOptiFineVersion ?: "")
                                                    }
                                                    PreviewMetaRow("Local Path", "instances/${trimmedName.ifBlank { "new_instance" }}")
                                                    PreviewMetaRow("Custom Icon", if (customIconFile != null) "Yes (${customIconFile?.extension?.uppercase()})" else "Default (Ezz 3D)")
                                                }
                                            }
                                        }

                                        // Validation & Readiness Status
                                        if (validationError != null) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF2A1515))
                                                    .border(1.dp, Color(0xFFEF5350).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                                    .padding(10.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(14.dp))
                                                    Text(validationError, color = Color(0xFFEF9A9A), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                                }
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF142214))
                                                    .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                                    .padding(10.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                                                    Text("READY TO CREATE", color = Color(0xFFA5D6A7), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                                }
                                            }
                                        }
                                    }

                                    // Action Buttons Footer
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = onDismiss,
                                            modifier = Modifier.weight(1f).height(44.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFAAAAAA)),
                                            border = BorderStroke(1.dp, Color(0xFF333333))
                                        ) {
                                            Text("Cancel", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = {
                                                if (isReadyToCreate) {
                                                    coroutineScope.launch {
                                                        try {
                                                            creationStep = 1
                                                            creationProgressText = "Preparing isolated instance directory..."
                                                            delay(350)

                                                            creationStep = 2
                                                            creationProgressText = "Fetching official metadata for $selectedMcVersion..."
                                                            delay(300)

                                                            creationStep = 3
                                                            creationProgressText = when (selectedLoader) {
                                                                LoaderType.FABRIC -> "Resolving Fabric loader..."
                                                                LoaderType.OPTIFINE -> "Verifying OptiFine profile..."
                                                                LoaderType.VANILLA -> "Configuring official client profile..."
                                                            }
                                                            delay(250)

                                                            val finalLoaderVersion = when (selectedLoader) {
                                                                LoaderType.FABRIC -> selectedFabricLoader
                                                                LoaderType.OPTIFINE -> selectedOptiFineVersion
                                                                LoaderType.VANILLA -> null
                                                            }

                                                            creationStep = 4
                                                            creationProgressText = "Finalizing instance registration..."

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
                                            enabled = isReadyToCreate,
                                            modifier = Modifier.weight(1.4f).height(44.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color.White,
                                                contentColor = Color.Black,
                                                disabledContainerColor = Color(0xFF222222),
                                                disabledContentColor = Color(0xFF666666)
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
private fun CreationProgressView(
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
            modifier = Modifier.widthIn(max = 480.dp)
        ) {
            if (errorMessage != null) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(44.dp))
                Text("Creation Failed", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(errorMessage, color = Color(0xFFEF9A9A), fontSize = 12.sp)
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("BACK TO CONFIGURATION", fontWeight = FontWeight.Bold)
                }
            } else {
                CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 3.dp, color = Color.White)
                Text("Creating $instanceName...", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                Text("Minecraft $minecraftVersion • ${loaderType.name}", color = Color(0xFF888888), fontSize = 12.sp)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF141414))
                        .border(1.dp, Color(0xFF262626), RoundedCornerShape(8.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProgressStepRow("1. Preparing instance directory", isDone = currentStep > 1, isActive = currentStep == 1)
                    ProgressStepRow("2. Resolving official version manifest", isDone = currentStep > 2, isActive = currentStep == 2)
                    ProgressStepRow("3. Configuring ${loaderType.name} loader engine", isDone = currentStep > 3, isActive = currentStep == 3)
                    ProgressStepRow("4. Finalizing local registration", isDone = currentStep > 4, isActive = currentStep == 4)
                }

                Text(progressText, color = Color(0xFFAAAAAA), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ProgressStepRow(label: String, isDone: Boolean, isActive: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (isDone || isActive) Color.White else Color(0xFF666666),
            fontSize = 11.5.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
        if (isDone) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
        } else if (isActive) {
            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Color.White)
        }
    }
}

@Composable
private fun SuccessCreationView(
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
            modifier = Modifier.widthIn(max = 480.dp)
        ) {
            InstanceArtworkIcon(
                instance = instance,
                size = 72.dp,
                customFile = customIconFile,
                showBadge = true
            )

            Text("INSTANCE CREATED SUCCESSFULLY", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
            Text(
                text = "${instance.name} is ready with Minecraft ${instance.minecraftVersion} (${instance.loaderType.name})",
                color = Color(0xFFAAAAAA),
                fontSize = 12.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
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
                    border = BorderStroke(1.dp, Color(0xFF444444))
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
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFAAAAAA)),
                    border = BorderStroke(1.dp, Color(0xFF262626))
                ) {
                    Text("DONE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun V3SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF131313))
            .border(1.dp, Color(0xFF222222), RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = title,
                color = Color(0xFFAAAAAA),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
            content()
        }
    }
}

@Composable
private fun V3LoaderCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) Color(0xFF202020) else Color(0xFF101010))
            .border(1.dp, if (isSelected) Color.White else Color(0xFF242424), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else Color(0xFF777777),
                    modifier = Modifier.size(16.dp)
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
                fontWeight = FontWeight.Black
            )
            Text(
                text = subtitle,
                color = Color(0xFF666666),
                fontSize = 9.5.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PreviewMetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color(0xFF777777), fontSize = 10.sp)
        Text(text = value, color = Color(0xFFCCCCCC), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}
