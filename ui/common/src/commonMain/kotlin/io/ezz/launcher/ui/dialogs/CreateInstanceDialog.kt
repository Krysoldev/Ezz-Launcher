package io.ezz.launcher.ui.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
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
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun CreateInstanceDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val colors = EzzTheme.colors
    val availableVersions by viewModel.availableVersions.collectAsState()
    val settings by viewModel.settingsRepository.settings.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var currentStep by remember { mutableStateOf(1) } // 1: Version & Loader, 2: Config & RAM

    var name by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedMcVersion by remember { mutableStateOf(availableVersions.firstOrNull()?.id ?: "1.21.4") }
    var selectedLoader by remember { mutableStateOf(LoaderType.VANILLA) }
    var fabricLoaders by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedFabricLoader by remember { mutableStateOf<String?>(null) }
    var isLoadingFabricLoaders by remember { mutableStateOf(false) }

    var maxRamMb by remember { mutableStateOf(settings.defaultMaxMemoryMb.toFloat()) }

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

    val versionsToDisplay = remember(availableVersions, searchQuery) {
        val list = if (availableVersions.isNotEmpty()) availableVersions else fallbackVersions
        if (searchQuery.isBlank()) list.take(30) else list.filter { it.id.contains(searchQuery, ignoreCase = true) }
    }

    LaunchedEffect(availableVersions) {
        if (availableVersions.isNotEmpty() && name.isBlank()) {
            selectedMcVersion = availableVersions.first().id
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
                .widthIn(max = 560.dp)
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Column {
                // Header Row with Step Indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (currentStep == 1) "Choose Version & Loader" else "Configure Instance",
                            color = colors.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Step $currentStep of 2",
                            color = colors.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    EzzIconButton(
                        icon = Icons.Default.Close,
                        onClick = onDismiss,
                        contentDescription = "Close",
                        tint = colors.textMuted
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "CreateWizardStep"
                ) { step ->
                    if (step == 1) {
                        // Step 1: Version & Loader Selection
                        Column {
                            // Search Version
                            EzzSearchField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = "Search Minecraft version (e.g. 1.21)...",
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Version Picker List
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.surfaceVariant)
                                    .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                                    .padding(6.dp)
                            ) {
                                LazyColumn {
                                    items(versionsToDisplay) { ver ->
                                        val isSelected = ver.id == selectedMcVersion
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isSelected) colors.primary.copy(alpha = 0.15f) else Color.Transparent)
                                                .clickable {
                                                    selectedMcVersion = ver.id
                                                    if (name.startsWith("Minecraft") || name.isBlank()) {
                                                        name = "Minecraft ${ver.id}"
                                                    }
                                                }
                                                .padding(horizontal = 10.dp, vertical = 7.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = ver.id,
                                                    color = if (isSelected) colors.primary else colors.textPrimary,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 13.sp
                                                )
                                                if (isSelected) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = colors.primary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = ver.releaseTime.take(10),
                                                color = colors.textMuted,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Loader Selector Tabs
                            Text("Mod Loader", color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                LoaderPill(
                                    name = "Vanilla",
                                    isSelected = selectedLoader == LoaderType.VANILLA,
                                    onClick = { selectedLoader = LoaderType.VANILLA },
                                    modifier = Modifier.weight(1f)
                                )
                                LoaderPill(
                                    name = "Fabric",
                                    isSelected = selectedLoader == LoaderType.FABRIC,
                                    onClick = { selectedLoader = LoaderType.FABRIC },
                                    modifier = Modifier.weight(1f)
                                )
                                LoaderPill(
                                    name = "OptiFine",
                                    isSelected = selectedLoader == LoaderType.OPTIFINE,
                                    onClick = { selectedLoader = LoaderType.OPTIFINE },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (selectedLoader == LoaderType.FABRIC) {
                                Spacer(modifier = Modifier.height(8.dp))
                                if (isLoadingFabricLoaders) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = colors.primary, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Fetching Fabric loaders...", color = colors.textSecondary, fontSize = 11.sp)
                                    }
                                } else {
                                    Text("Fabric Loader: ${selectedFabricLoader ?: "Latest"}", color = colors.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            } else if (selectedLoader == LoaderType.OPTIFINE) {
                                Spacer(modifier = Modifier.height(8.dp))
                                val optiVer = OptiFineCompatibilityValidator.getSuggestedOptiFineVersion(selectedMcVersion)
                                Text("OptiFine Profile: $optiVer", color = colors.warning, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                        // Step 2: Instance Name & Performance
                        Column {
                            EzzTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = "Instance Name",
                                placeholder = "e.g. Survival 1.21.4",
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Summary Preview Box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.surfaceVariant)
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(text = "Target Configuration", color = colors.textMuted, fontSize = 11.sp)
                                        Text(text = "Minecraft $selectedMcVersion", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                    EzzLoaderBadge(loaderType = selectedLoader)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // RAM Allocation Slider
                            EzzSlider(
                                value = maxRamMb,
                                onValueChange = { maxRamMb = it },
                                valueRange = 1024f..16384f,
                                steps = 15,
                                label = "Memory Allocation (RAM)",
                                valueDisplay = "${(maxRamMb / 1024).toInt()} GB"
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Footer Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep > 1) {
                        EzzButton(
                            text = "Back",
                            onClick = { currentStep -= 1 },
                            variant = EzzButtonVariant.SECONDARY,
                            size = EzzButtonSize.MEDIUM,
                            icon = Icons.AutoMirrored.Filled.ArrowBack
                        )
                    } else {
                        EzzButton(
                            text = "Cancel",
                            onClick = onDismiss,
                            variant = EzzButtonVariant.GHOST,
                            size = EzzButtonSize.MEDIUM
                        )
                    }

                    if (currentStep == 1) {
                        EzzButton(
                            text = "Next",
                            onClick = { currentStep = 2 },
                            variant = EzzButtonVariant.PRIMARY,
                            size = EzzButtonSize.MEDIUM,
                            trailingIcon = Icons.AutoMirrored.Filled.ArrowForward
                        )
                    } else {
                        EzzButton(
                            text = "Create Instance",
                            onClick = {
                                val finalLoaderVersion = when (selectedLoader) {
                                    LoaderType.FABRIC -> selectedFabricLoader ?: "0.16.10"
                                    LoaderType.OPTIFINE -> OptiFineCompatibilityValidator.getSuggestedOptiFineVersion(selectedMcVersion)
                                    LoaderType.VANILLA -> null
                                }
                                viewModel.createInstance(
                                    name = name.ifBlank { "Minecraft $selectedMcVersion" },
                                    minecraftVersion = selectedMcVersion,
                                    loaderType = selectedLoader,
                                    loaderVersion = finalLoaderVersion,
                                    minMemoryMb = 1024,
                                    maxMemoryMb = maxRamMb.toInt(),
                                    customJvmArgs = emptyList()
                                )
                            },
                            variant = EzzButtonVariant.PRIMARY,
                            size = EzzButtonSize.MEDIUM,
                            icon = Icons.Default.Check
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoaderPill(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = EzzTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) colors.primary.copy(alpha = 0.15f) else colors.surfaceVariant)
            .border(1.dp, if (isSelected) colors.primary else colors.border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            color = if (isSelected) colors.primary else colors.textSecondary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp
        )
    }
}
