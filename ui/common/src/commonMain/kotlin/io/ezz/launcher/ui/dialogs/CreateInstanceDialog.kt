package io.ezz.launcher.ui.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.ezz.launcher.core.minecraft.loader.optifine.OptiFineCompatibilityValidator
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.model.minecraft.VersionSummary
import io.ezz.launcher.ui.components.EzzBadge
import io.ezz.launcher.ui.components.EzzBadgeVariant
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.EzzCard
import io.ezz.launcher.ui.components.EzzIconButton
import io.ezz.launcher.ui.components.EzzLoaderBadge
import io.ezz.launcher.ui.components.EzzSearchField
import io.ezz.launcher.ui.components.EzzSlider
import io.ezz.launcher.ui.components.EzzTextField
import io.ezz.launcher.ui.components.EzzToggle
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.AppViewModel
import kotlinx.coroutines.launch

private enum class VersionCategory(val title: String) {
    RELEASES("Releases"),
    SNAPSHOTS("Snapshots"),
    ALL("All (900+)"),
    OLD("Beta / Alpha")
}

@Composable
fun CreateInstanceDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val availableReleases by viewModel.availableVersions.collectAsState()
    val allVersions by viewModel.allVersions.collectAsState()
    val snapshotVersions by viewModel.snapshotVersions.collectAsState()
    val oldVersions by viewModel.oldVersions.collectAsState()
    val settings by viewModel.settingsRepository.settings.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var currentStep by remember { mutableStateOf(1) } // 1: Info & Version, 2: Loader, 3: Java & RAM, 4: Game Settings, 5: Summary
    var selectedCategory by remember { mutableStateOf(VersionCategory.RELEASES) }

    var name by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedMcVersion by remember { mutableStateOf(availableReleases.firstOrNull()?.id ?: "1.21.4") }
    var selectedLoader by remember { mutableStateOf(LoaderType.VANILLA) }
    var fabricLoaders by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedFabricLoader by remember { mutableStateOf<String?>(null) }
    var isLoadingFabricLoaders by remember { mutableStateOf(false) }

    var maxRamMb by remember { mutableStateOf(settings.defaultMaxMemoryMb.toFloat()) }
    var jvmArgs by remember { mutableStateOf(settings.globalJvmArgs.joinToString(" ")) }
    var windowWidth by remember { mutableStateOf(1280) }
    var windowHeight by remember { mutableStateOf(720) }
    var isFullscreen by remember { mutableStateOf(false) }

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

    val sourceList = when (selectedCategory) {
        VersionCategory.RELEASES -> if (availableReleases.isNotEmpty()) availableReleases else fallbackVersions
        VersionCategory.SNAPSHOTS -> snapshotVersions
        VersionCategory.ALL -> if (allVersions.isNotEmpty()) allVersions else availableReleases
        VersionCategory.OLD -> oldVersions
    }

    val versionsToDisplay = remember(sourceList, searchQuery) {
        if (searchQuery.isBlank()) {
            sourceList
        } else {
            sourceList.filter { it.id.contains(searchQuery, ignoreCase = true) }
        }
    }

    LaunchedEffect(availableReleases) {
        if (availableReleases.isNotEmpty() && name.isBlank()) {
            selectedMcVersion = availableReleases.first().id
            name = "Minecraft $selectedMcVersion"
        }
    }

    LaunchedEffect(selectedMcVersion, selectedLoader) {
        if (selectedLoader == LoaderType.FABRIC) {
            isLoadingFabricLoaders = true
            coroutineScope.launch {
                val loaders = viewModel.fabricMetaClient.getLoaderVersionsForGame(selectedMcVersion)
                fabricLoaders = loaders
                selectedFabricLoader = loaders.firstOrNull() ?: "0.16.10"
                isLoadingFabricLoaders = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .widthIn(min = 660.dp, max = 740.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0A0A0A))
                .border(1.dp, Color(0xFF282828), RoundedCornerShape(8.dp))
                .padding(24.dp)
        ) {
            Column {
                // Header & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CREATE MINECRAFT INSTANCE",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Step $currentStep of 5 • ${
                                when (currentStep) {
                                    1 -> "Basic Information & Version"
                                    2 -> "Mod Loader Selection"
                                    3 -> "Java Runtime & Memory"
                                    4 -> "Game Resolution & Settings"
                                    5 -> "Overview & Confirmation"
                                    else -> ""
                                }
                            }",
                            color = Color(0xFF888888),
                            fontSize = 12.sp
                        )
                    }

                    EzzIconButton(
                        icon = Icons.Default.Close,
                        onClick = onDismiss,
                        size = EzzButtonSize.SMALL,
                        variant = EzzButtonVariant.GHOST
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Step Pills Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (1..5).forEach { stepNum ->
                        val isActive = stepNum == currentStep
                        val isDone = stepNum < currentStep
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    when {
                                        isActive -> Color.White
                                        isDone -> Color(0xFF555555)
                                        else -> Color(0xFF1E1E1E)
                                    }
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Multi-step Content
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    modifier = Modifier.height(340.dp)
                ) { step ->
                    when (step) {
                        1 -> Step1VersionSelection(
                            name = name,
                            onNameChange = { name = it },
                            selectedCategory = selectedCategory,
                            onCategoryChange = { selectedCategory = it },
                            searchQuery = searchQuery,
                            onSearchChange = { searchQuery = it },
                            versions = versionsToDisplay,
                            selectedVersion = selectedMcVersion,
                            onSelectVersion = { v ->
                                selectedMcVersion = v
                                if (name.isBlank() || name.startsWith("Minecraft ")) {
                                    name = "Minecraft $v"
                                }
                            }
                        )
                        2 -> Step2LoaderSelection(
                            selectedLoader = selectedLoader,
                            onLoaderChange = { selectedLoader = it },
                            selectedMcVersion = selectedMcVersion,
                            fabricLoaders = fabricLoaders,
                            selectedFabricLoader = selectedFabricLoader,
                            onFabricLoaderChange = { selectedFabricLoader = it },
                            isLoadingFabric = isLoadingFabricLoaders
                        )
                        3 -> Step3JavaMemory(
                            maxRamMb = maxRamMb,
                            onRamChange = { maxRamMb = it },
                            jvmArgs = jvmArgs,
                            onJvmArgsChange = { jvmArgs = it }
                        )
                        4 -> Step4GameSettings(
                            windowWidth = windowWidth,
                            onWidthChange = { windowWidth = it },
                            windowHeight = windowHeight,
                            onHeightChange = { windowHeight = it },
                            isFullscreen = isFullscreen,
                            onFullscreenChange = { isFullscreen = it }
                        )
                        5 -> Step5Summary(
                            name = name,
                            mcVersion = selectedMcVersion,
                            loaderType = selectedLoader,
                            fabricLoader = selectedFabricLoader,
                            ramMb = maxRamMb.toInt(),
                            resolution = "${windowWidth}x${windowHeight}"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Footer Navigation Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep > 1) {
                        EzzButton(
                            text = "Back",
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            onClick = { currentStep-- },
                            variant = EzzButtonVariant.SECONDARY,
                            size = EzzButtonSize.MEDIUM
                        )
                    } else {
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    if (currentStep < 5) {
                        EzzButton(
                            text = "Next",
                            trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
                            onClick = { currentStep++ },
                            variant = EzzButtonVariant.PRIMARY,
                            size = EzzButtonSize.MEDIUM,
                            enabled = name.isNotBlank() && selectedMcVersion.isNotBlank()
                        )
                    } else {
                        EzzButton(
                            text = "Create Instance",
                            icon = Icons.Default.Check,
                            onClick = {
                                viewModel.createInstance(
                                    name = name.ifBlank { "Minecraft $selectedMcVersion" },
                                    minecraftVersion = selectedMcVersion,
                                    loaderType = selectedLoader,
                                    loaderVersion = if (selectedLoader == LoaderType.FABRIC) selectedFabricLoader else null,
                                    minMemoryMb = 1024,
                                    maxMemoryMb = maxRamMb.toInt(),
                                    customJvmArgs = jvmArgs.split(" ").filter { it.isNotBlank() }
                                )
                                onDismiss()
                            },
                            variant = EzzButtonVariant.PRIMARY,
                            size = EzzButtonSize.MEDIUM
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Step1VersionSelection(
    name: String,
    onNameChange: (String) -> Unit,
    selectedCategory: VersionCategory,
    onCategoryChange: (VersionCategory) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    versions: List<VersionSummary>,
    selectedVersion: String,
    onSelectVersion: (String) -> Unit
) {
    Column {
        EzzTextField(
            value = name,
            onValueChange = onNameChange,
            label = "Instance Name",
            placeholder = "e.g. Survival 1.21",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Tabs
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF141414))
                    .border(1.dp, Color(0xFF242424), RoundedCornerShape(6.dp))
                    .padding(2.dp)
            ) {
                VersionCategory.entries.forEach { cat ->
                    val isSelected = selectedCategory == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) Color(0xFF242424) else Color.Transparent)
                            .clickable { onCategoryChange(cat) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = cat.title,
                            color = if (isSelected) Color.White else Color(0xFF888888),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // Quick Search
            Box(modifier = Modifier.width(180.dp)) {
                EzzSearchField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = "Filter (e.g. 1.21)..."
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Versions List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF101010))
                .border(1.dp, Color(0xFF202020), RoundedCornerShape(6.dp))
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(versions, key = { it.id }) { v ->
                val isSelected = v.id == selectedVersion
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) Color(0xFF222222) else Color.Transparent)
                        .clickable { onSelectVersion(v.id) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = v.id,
                            color = if (isSelected) Color.White else Color(0xFFD4D4D4),
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color.White))
                        }
                    }

                    EzzBadge(
                        text = v.type.uppercase(),
                        variant = if (v.type == "release") EzzBadgeVariant.NEUTRAL else EzzBadgeVariant.INFO
                    )
                }
            }
        }
    }
}

@Composable
private fun Step2LoaderSelection(
    selectedLoader: LoaderType,
    onLoaderChange: (LoaderType) -> Unit,
    selectedMcVersion: String,
    fabricLoaders: List<String>,
    selectedFabricLoader: String?,
    onFabricLoaderChange: (String) -> Unit,
    isLoadingFabric: Boolean
) {
    val optifineSupported = OptiFineCompatibilityValidator.isVersionSupported(selectedMcVersion)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Select Minecraft Mod Loader",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LoaderCard(
                title = "Vanilla",
                subtitle = "Official pure Mojang build",
                isSelected = selectedLoader == LoaderType.VANILLA,
                onClick = { onLoaderChange(LoaderType.VANILLA) },
                modifier = Modifier.weight(1f)
            )

            LoaderCard(
                title = "Fabric",
                subtitle = "Fast, lightweight mod engine",
                isSelected = selectedLoader == LoaderType.FABRIC,
                onClick = { onLoaderChange(LoaderType.FABRIC) },
                modifier = Modifier.weight(1f)
            )

            LoaderCard(
                title = "OptiFine",
                subtitle = if (optifineSupported) "Shaders & FPS optimizations" else "Not supported on $selectedMcVersion",
                isSelected = selectedLoader == LoaderType.OPTIFINE,
                enabled = optifineSupported,
                onClick = { onLoaderChange(LoaderType.OPTIFINE) },
                modifier = Modifier.weight(1f)
            )
        }

        if (selectedLoader == LoaderType.FABRIC) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Fabric Loader Version",
                color = Color(0xFFA0A0A0),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            if (isLoadingFabric) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Fetching compatible Fabric builds...", color = Color(0xFF888888), fontSize = 12.sp)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    fabricLoaders.take(4).forEach { loaderVer ->
                        val isSelected = loaderVer == selectedFabricLoader
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) Color(0xFF252525) else Color(0xFF141414))
                                .border(1.dp, if (isSelected) Color.White else Color(0xFF282828), RoundedCornerShape(4.dp))
                                .clickable { onFabricLoaderChange(loaderVer) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = loaderVer,
                                color = if (isSelected) Color.White else Color(0xFF999999),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoaderCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) Color(0xFF1C1C1C) else Color(0xFF101010))
            .border(1.dp, if (isSelected) Color.White else Color(0xFF262626), RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = if (enabled) Color.White else Color(0xFF555555),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                if (isSelected) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = if (enabled) Color(0xFF888888) else Color(0xFF444444),
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun Step3JavaMemory(
    maxRamMb: Float,
    onRamChange: (Float) -> Unit,
    jvmArgs: String,
    onJvmArgsChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        EzzSlider(
            value = maxRamMb,
            onValueChange = onRamChange,
            valueRange = 1024f..16384f,
            steps = 15,
            label = "Maximum Memory (RAM Allocation)",
            valueDisplay = "${(maxRamMb / 1024).toInt()} GB"
        )

        EzzTextField(
            value = jvmArgs,
            onValueChange = onJvmArgsChange,
            label = "Custom JVM Arguments",
            placeholder = "-XX:+UseG1GC -XX:+UnlockExperimentalVMOptions",
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun Step4GameSettings(
    windowWidth: Int,
    onWidthChange: (Int) -> Unit,
    windowHeight: Int,
    onHeightChange: (Int) -> Unit,
    isFullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Game Window Resolution",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ResolutionPreset(
                label = "1280 x 720 (HD)",
                isSelected = windowWidth == 1280 && windowHeight == 720,
                onClick = { onWidthChange(1280); onHeightChange(720) },
                modifier = Modifier.weight(1f)
            )
            ResolutionPreset(
                label = "1920 x 1080 (FHD)",
                isSelected = windowWidth == 1920 && windowHeight == 1080,
                onClick = { onWidthChange(1920); onHeightChange(1080) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        EzzToggle(
            checked = isFullscreen,
            onCheckedChange = onFullscreenChange,
            label = "Launch in Fullscreen Mode",
            description = "Automatically expand game to full desktop monitor"
        )
    }
}

@Composable
private fun ResolutionPreset(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) Color(0xFF1E1E1E) else Color(0xFF101010))
            .border(1.dp, if (isSelected) Color.White else Color(0xFF242424), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else Color(0xFF888888),
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun Step5Summary(
    name: String,
    mcVersion: String,
    loaderType: LoaderType,
    fabricLoader: String?,
    ramMb: Int,
    resolution: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF121212))
            .border(1.dp, Color(0xFF282828), RoundedCornerShape(6.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "INSTANCE CONFIGURATION OVERVIEW",
                color = Color(0xFF888888),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )

            SummaryRow("Instance Name", name)
            SummaryRow("Minecraft Version", mcVersion)
            SummaryRow("Mod Engine", if (loaderType == LoaderType.FABRIC) "Fabric ($fabricLoader)" else loaderType.name)
            SummaryRow("Memory Allocated", "${ramMb / 1024} GB RAM")
            SummaryRow("Initial Resolution", resolution)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color(0xFF888888), fontSize = 12.sp)
        Text(text = value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
