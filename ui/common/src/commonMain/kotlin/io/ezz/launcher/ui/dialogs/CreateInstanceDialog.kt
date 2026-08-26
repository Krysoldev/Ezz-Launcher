package io.ezz.launcher.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
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
import io.ezz.launcher.ui.components.EzzBadge
import io.ezz.launcher.ui.components.EzzBadgeVariant
import io.ezz.launcher.ui.components.EzzIconButton
import io.ezz.launcher.ui.components.EzzToggle
import io.ezz.launcher.ui.components.InstanceArtworkIcon
import io.ezz.launcher.ui.viewmodel.AppViewModel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Ezz Launcher — Create Instance V2
 * Complete modern 2-column guided creation dialog.
 * Left: Instance Identity, Minecraft Version Selector, Loader Selection, Java & RAM.
 * Right: Live Real-time Instance Preview, Validation Breakdown, Creation Action.
 */
@Composable
fun CreateInstanceDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val existingInstances by viewModel.instances.collectAsState()
    val availableReleases by viewModel.availableVersions.collectAsState()
    val detectedJavaRuntimes by viewModel.detectedJavaRuntimes.collectAsState()
    val settings by viewModel.settingsRepository.settings.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // 1. Instance Identity
    var name by remember { mutableStateOf("") }
    var customIconFile by remember { mutableStateOf<File?>(null) }

    // 2. Version Selection State
    var versionFilterTab by remember { mutableStateOf("Release") } // "Release", "Snapshot", "All"
    var versionSearchQuery by remember { mutableStateOf("") }

    val fallbackVersions = remember {
        listOf(
            VersionSummary("1.21.4", "release", "", "", "2024-12-03"),
            VersionSummary("1.21.1", "release", "", "", "2024-08-08"),
            VersionSummary("1.20.4", "release", "", "", "2023-12-07"),
            VersionSummary("1.20.1", "release", "", "", "2023-06-12"),
            VersionSummary("1.19.4", "release", "", "", "2023-03-14"),
            VersionSummary("1.18.2", "release", "", "", "2022-02-28"),
            VersionSummary("1.16.5", "release", "", "", "2021-01-15"),
            VersionSummary("1.12.2", "release", "", "", "2017-09-18"),
            VersionSummary("1.8.9", "release", "", "", "2015-12-03")
        )
    }

    val sourceList = remember(availableReleases, versionFilterTab) {
        val list = if (availableReleases.isNotEmpty()) availableReleases else fallbackVersions
        when (versionFilterTab) {
            "Release" -> list.filter { it.type == "release" }
            "Snapshot" -> list.filter { it.type == "snapshot" }
            else -> list
        }
    }

    val filteredVersions = remember(sourceList, versionSearchQuery) {
        val q = versionSearchQuery.trim().lowercase()
        val res = if (q.isBlank()) sourceList else sourceList.filter { it.id.lowercase().contains(q) }
        MinecraftVersionComparator.sort(res, VersionSortOrder.NEWEST_FIRST)
    }

    var selectedMcVersion by remember {
        mutableStateOf(sourceList.firstOrNull()?.id ?: "1.21.4")
    }

    // 3. Mod Loader Selection
    var selectedLoader by remember { mutableStateOf(LoaderType.VANILLA) }

    // Fabric state
    var fabricLoaders by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedFabricLoader by remember { mutableStateOf<String?>(null) }
    var isLoadingFabricLoaders by remember { mutableStateOf(false) }

    // OptiFine state
    var optifineVersions by remember { mutableStateOf<List<OptiFineVersionOption>>(emptyList()) }
    var selectedOptiFineVersion by remember { mutableStateOf<String?>(null) }

    // 4. Java Runtime & RAM
    val requiredJavaMajor = remember(selectedMcVersion) {
        JavaCompatibility.getRequiredJavaMajorVersion(selectedMcVersion)
    }
    var selectedJavaPath by remember { mutableStateOf<String?>(null) }
    var maxRamMb by remember { mutableStateOf(settings.defaultMaxMemoryMb.toFloat()) }

    // 5. Advanced Configuration
    var isAdvancedExpanded by remember { mutableStateOf(false) }
    var windowWidth by remember { mutableStateOf(1280) }
    var windowHeight by remember { mutableStateOf(720) }
    var isFullscreen by remember { mutableStateOf(false) }
    var customJvmArgs by remember { mutableStateOf(settings.globalJvmArgs.joinToString(" ")) }

    // 6. Creation Progress / Submitting state
    var isCreating by remember { mutableStateOf(false) }
    var creationError by remember { mutableStateOf<String?>(null) }

    // Auto-update suggested name when version or loader changes if name is default
    var isUserCustomName by remember { mutableStateOf(false) }
    LaunchedEffect(selectedMcVersion, selectedLoader) {
        if (!isUserCustomName) {
            val prefix = when (selectedLoader) {
                LoaderType.VANILLA -> "Minecraft"
                LoaderType.FABRIC -> "Fabric"
                LoaderType.OPTIFINE -> "OptiFine"
            }
            name = "$prefix $selectedMcVersion"
        }
    }

    // Dynamic Fabric discovery
    LaunchedEffect(selectedMcVersion, selectedLoader) {
        if (selectedLoader == LoaderType.FABRIC) {
            isLoadingFabricLoaders = true
            coroutineScope.launch {
                val loaders = viewModel.fabricMetaClient.getLoaderVersionsForGame(selectedMcVersion)
                fabricLoaders = loaders
                selectedFabricLoader = loaders.firstOrNull()
                isLoadingFabricLoaders = false
            }
        }
    }

    // Dynamic OptiFine discovery
    LaunchedEffect(selectedMcVersion, selectedLoader) {
        if (selectedLoader == LoaderType.OPTIFINE) {
            val opts = OptiFineCompatibilityValidator.getAvailableOptiFineVersions(selectedMcVersion)
            optifineVersions = opts
            selectedOptiFineVersion = OptiFineCompatibilityValidator.getSuggestedOptiFineVersion(selectedMcVersion)
        }
    }

    // Synchronize default RAM based on loader
    LaunchedEffect(selectedLoader) {
        if (maxRamMb <= 2048f && selectedLoader != LoaderType.VANILLA) {
            maxRamMb = 4096f
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
        isDuplicateName -> "An instance with this name already exists"
        isFabricIncompatible -> "Fabric loader is not available for Minecraft $selectedMcVersion"
        isOptiFineIncompatible -> "OptiFine is not compatible with Minecraft $selectedMcVersion"
        else -> null
    }
    val isValid = validationError == null && !isCreating

    Dialog(onDismissRequest = { if (!isCreating) onDismiss() }) {
        Box(
            modifier = Modifier
                .widthIn(min = 860.dp, max = 980.dp)
                .heightIn(max = 700.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0F0F0F))
                .border(1.dp, Color(0xFF282828), RoundedCornerShape(14.dp))
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "CREATE INSTANCE",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Configure and launch a new local Minecraft Java Edition environment",
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

                Spacer(modifier = Modifier.height(18.dp))

                // 2-Column Responsive Body
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // LEFT COLUMN (~62%): Form Fields
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .weight(0.62f)
                            .fillMaxHeight()
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. INSTANCE IDENTITY
                        SectionCard(title = "INSTANCE IDENTITY") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Custom Icon Selector Box
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF181818))
                                        .border(1.dp, Color(0xFF333333), RoundedCornerShape(10.dp))
                                        .clickable {
                                            val picked = viewModel.platformBridge.pickImageFile("Select Instance Icon (PNG, JPG, WEBP)")
                                            if (picked != null && picked.exists()) {
                                                customIconFile = picked
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Instance artwork icon preview
                                    InstanceArtworkIcon(
                                        instance = Instance(
                                            id = "preview_temp",
                                            name = name,
                                            minecraftVersion = selectedMcVersion,
                                            loaderType = selectedLoader
                                        ),
                                        size = 64.dp,
                                        customFile = customIconFile,
                                        showBadge = false
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("Instance Name", color = Color(0xFFAAAAAA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    TextField(
                                        value = name,
                                        onValueChange = {
                                            name = it
                                            isUserCustomName = true
                                        },
                                        modifier = Modifier.fillMaxWidth().height(46.dp),
                                        placeholder = { Text("e.g. My Survival, Fabric SMP", color = Color(0xFF555555), fontSize = 13.sp) },
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
                                            text = if (customIconFile != null) "Custom Icon: ${customIconFile?.name}" else "Click icon to upload custom image",
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

                        // 2. MINECRAFT VERSION SELECTOR
                        SectionCard(title = "MINECRAFT VERSION") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // Filter Tabs + Search
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Tabs: Release, Snapshot, All
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF161616))
                                            .padding(2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        listOf("Release", "Snapshot", "All").forEach { tab ->
                                            val isSelected = versionFilterTab == tab
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(if (isSelected) Color(0xFF282828) else Color.Transparent)
                                                    .clickable { versionFilterTab = tab }
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = tab,
                                                    color = if (isSelected) Color.White else Color(0xFF888888),
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                )
                                            }
                                        }
                                    }

                                    // Search field
                                    Box(
                                        modifier = Modifier
                                            .width(140.dp)
                                            .height(30.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF161616))
                                            .border(1.dp, Color(0xFF242424), RoundedCornerShape(6.dp))
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
                                                placeholder = { Text("Filter...", color = Color(0xFF555555), fontSize = 11.sp) },
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

                                // Versions Scrollable List (Compact 110dp)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF121212))
                                        .border(1.dp, Color(0xFF202020), RoundedCornerShape(6.dp))
                                ) {
                                    if (filteredVersions.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("No matching Minecraft versions found", color = Color(0xFF666666), fontSize = 11.sp)
                                        }
                                    } else {
                                        LazyColumn(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                                            items(filteredVersions, key = { it.id }) { ver ->
                                                val isSelected = selectedMcVersion == ver.id
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(if (isSelected) Color(0xFF222222) else Color.Transparent)
                                                        .clickable { selectedMcVersion = ver.id }
                                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Text(
                                                            text = ver.id,
                                                            color = if (isSelected) Color.White else Color(0xFFBBBBBB),
                                                            fontSize = 12.5.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                        )
                                                        if (ver.id == sourceList.firstOrNull()?.id) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(3.dp))
                                                                    .background(Color(0xFF1E281E))
                                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                                            ) {
                                                                Text("LATEST", color = Color(0xFF4CAF50), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    }

                                                    Text(
                                                        text = ver.releaseTime.take(10),
                                                        color = Color(0xFF555555),
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. MOD LOADER CARDS
                        SectionCard(title = "MOD LOADER") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    LoaderCard(
                                        title = "VANILLA",
                                        subtitle = "Official Minecraft",
                                        icon = Icons.Default.VideogameAsset,
                                        isSelected = selectedLoader == LoaderType.VANILLA,
                                        onClick = { selectedLoader = LoaderType.VANILLA },
                                        modifier = Modifier.weight(1f)
                                    )
                                    LoaderCard(
                                        title = "FABRIC",
                                        subtitle = "Lightweight Mods",
                                        icon = Icons.Default.Extension,
                                        isSelected = selectedLoader == LoaderType.FABRIC,
                                        onClick = { selectedLoader = LoaderType.FABRIC },
                                        modifier = Modifier.weight(1f)
                                    )
                                    LoaderCard(
                                        title = "OPTIFINE",
                                        subtitle = "Shaders & FPS",
                                        icon = Icons.Default.Layers,
                                        isSelected = selectedLoader == LoaderType.OPTIFINE,
                                        onClick = { selectedLoader = LoaderType.OPTIFINE },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Loader Sub-options (Fabric / OptiFine version)
                                if (selectedLoader == LoaderType.FABRIC) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF141414))
                                            .border(1.dp, Color(0xFF222222), RoundedCornerShape(6.dp))
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Fabric Loader Version", color = Color(0xFFAAAAAA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        if (isLoadingFabricLoaders) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Color.White)
                                                Text("Fetching...", color = Color(0xFF888888), fontSize = 11.sp)
                                            }
                                        } else if (fabricLoaders.isEmpty()) {
                                            Text("No compatible loader found", color = Color(0xFFEF5350), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                            .border(1.dp, Color(0xFF222222), RoundedCornerShape(6.dp))
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("OptiFine Version", color = Color(0xFFAAAAAA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        if (!isSupported) {
                                            Text("Not available for $selectedMcVersion", color = Color(0xFFEF5350), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

                        // 4. JAVA & MEMORY
                        SectionCard(title = "JAVA RUNTIME & RAM") {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                // Java Info Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Target Java Requirement", color = Color(0xFFAAAAAA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text("Java $requiredJavaMajor (${JavaCompatibility.getJavaRequirementDescription(requiredJavaMajor)})", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF1E281E))
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Text("AUTO-MANAGED", color = Color(0xFF4CAF50), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

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
                                                    .background(if (isAuto) Color(0xFF242424) else Color(0xFF141414))
                                                    .border(1.dp, if (isAuto) Color.White else Color(0xFF282828), RoundedCornerShape(4.dp))
                                                    .clickable { selectedJavaPath = null }
                                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                                            ) {
                                                Text("Auto (Java $requiredJavaMajor)", color = if (isAuto) Color.White else Color(0xFF888888), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }

                                            detectedJavaRuntimes.take(2).forEach { rt ->
                                                val isSelected = selectedJavaPath == rt.path
                                                val isCompatible = JavaCompatibility.isJavaVersionCompatible(rt.majorVersion, requiredJavaMajor)
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(if (isSelected) Color(0xFF242424) else Color(0xFF141414))
                                                        .border(1.dp, if (isSelected) Color.White else Color(0xFF282828), RoundedCornerShape(4.dp))
                                                        .clickable { selectedJavaPath = rt.path }
                                                        .padding(horizontal = 8.dp, vertical = 5.dp)
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

                                // RAM Slider
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Memory Allocation (RAM)", color = Color(0xFFAAAAAA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

                        // 5. ADVANCED OPTIONS (Expandable)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF121212))
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
                                        Text("ADVANCED OPTIONS", color = Color(0xFFCCCCCC), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
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
                                        // Resolution
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text("Width", color = Color(0xFF888888), fontSize = 10.sp)
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
                                                Text("Height", color = Color(0xFF888888), fontSize = 10.sp)
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
                                            Text("Launch in Fullscreen", color = Color(0xFFCCCCCC), fontSize = 11.sp)
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

                    // RIGHT COLUMN (~38%): Live Instance Preview & Validation Card
                    Column(
                        modifier = Modifier
                            .weight(0.38f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                text = "LIVE INSTANCE PREVIEW",
                                color = Color(0xFF777777),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )

                            // Live Card
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color(0xFF181818), Color(0xFF101010))
                                        )
                                    )
                                    .border(1.dp, Color(0xFF2C2C2C), RoundedCornerShape(10.dp))
                                    .padding(16.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    // Top Artwork & Name
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

                                    // Metadata Summary Table
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF0C0C0C))
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
                                        PreviewMetaRow("Storage Path", "instances/${trimmedName.ifBlank { "new_instance" }}")
                                        PreviewMetaRow("Custom Icon", if (customIconFile != null) "Yes (${customIconFile?.extension?.uppercase()})" else "Default (Ezz 3D)")
                                    }
                                }
                            }

                            // Validation / Readiness Alert Box
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
                                        Text("Configuration ready to create", color = Color(0xFFA5D6A7), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }

                        // Bottom Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                enabled = !isCreating,
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFAAAAAA)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333))
                            ) {
                                Text("Cancel", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    if (isValid) {
                                        isCreating = true
                                        creationError = null
                                        val finalLoaderVersion = when (selectedLoader) {
                                            LoaderType.FABRIC -> selectedFabricLoader
                                            LoaderType.OPTIFINE -> selectedOptiFineVersion
                                            LoaderType.VANILLA -> null
                                        }
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
                                                isCreating = false
                                                onDismiss()
                                            }
                                        )
                                    }
                                },
                                enabled = isValid,
                                modifier = Modifier.weight(1.4f).height(44.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black,
                                    disabledContainerColor = Color(0xFF222222),
                                    disabledContentColor = Color(0xFF666666)
                                )
                            ) {
                                if (isCreating) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.Black)
                                        Text("Creating...", fontSize = 12.sp, fontWeight = FontWeight.Black)
                                    }
                                } else {
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

@Composable
private fun SectionCard(
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
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
            content()
        }
    }
}

@Composable
private fun LoaderCard(
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
                            .clip(androidx.compose.foundation.shape.CircleShape)
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
