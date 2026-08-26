package io.ezz.launcher.ui.dialogs

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.ezz.launcher.core.minecraft.loader.optifine.OptiFineCompatibilityValidator
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.ui.theme.EzzColors
import io.ezz.launcher.ui.viewmodel.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun CreateInstanceDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val availableVersions by viewModel.availableVersions.collectAsState()
    val settings by viewModel.settingsRepository.settings.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var selectedMcVersion by remember { mutableStateOf(availableVersions.firstOrNull()?.id ?: "1.21.4") }
    var selectedLoader by remember { mutableStateOf(LoaderType.VANILLA) }
    var fabricLoaders by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedFabricLoader by remember { mutableStateOf<String?>(null) }
    var isLoadingFabricLoaders by remember { mutableStateOf(false) }

    var maxRamMb by remember { mutableStateOf(settings.defaultMaxMemoryMb) }
    var minRamMb by remember { mutableStateOf(settings.defaultMinMemoryMb) }

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
                selectedFabricLoader = loaders.firstOrNull() ?: "0.16.9"
                isLoadingFabricLoaders = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(20.dp))
                .background(EzzColors.Surface)
                .border(1.dp, EzzColors.Border, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Create New Instance",
                    color = EzzColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Configure version, mod loader, and isolated directory",
                    color = EzzColors.TextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Instance Name
                Text("Instance Name", color = EzzColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("e.g. Survival 1.21.4", color = EzzColors.TextMuted) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = EzzColors.SurfaceVariant,
                        unfocusedContainerColor = EzzColors.SurfaceVariant,
                        focusedTextColor = EzzColors.TextPrimary,
                        unfocusedTextColor = EzzColors.TextPrimary,
                        focusedIndicatorColor = EzzColors.Primary,
                        unfocusedIndicatorColor = EzzColors.Border
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Minecraft Version Picker
                val fallbackVersions = remember {
                    listOf(
                        io.ezz.launcher.core.model.minecraft.VersionSummary("1.21.4", "release", "", "", "2024-12-03"),
                        io.ezz.launcher.core.model.minecraft.VersionSummary("1.21.1", "release", "", "", "2024-08-08"),
                        io.ezz.launcher.core.model.minecraft.VersionSummary("1.20.4", "release", "", "", "2023-12-07"),
                        io.ezz.launcher.core.model.minecraft.VersionSummary("1.20.1", "release", "", "", "2023-06-12"),
                        io.ezz.launcher.core.model.minecraft.VersionSummary("1.19.4", "release", "", "", "2023-03-14"),
                        io.ezz.launcher.core.model.minecraft.VersionSummary("1.18.2", "release", "", "", "2022-02-28"),
                        io.ezz.launcher.core.model.minecraft.VersionSummary("1.16.5", "release", "", "", "2021-01-15"),
                        io.ezz.launcher.core.model.minecraft.VersionSummary("1.12.2", "release", "", "", "2017-09-18"),
                        io.ezz.launcher.core.model.minecraft.VersionSummary("1.8.9", "release", "", "", "2015-12-03")
                    )
                }
                val versionsToDisplay = if (availableVersions.isNotEmpty()) availableVersions.take(30) else fallbackVersions

                Text("Minecraft Version", color = EzzColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(EzzColors.SurfaceVariant)
                        .border(1.dp, EzzColors.Border, RoundedCornerShape(10.dp))
                        .padding(8.dp)
                ) {
                    LazyColumn {
                        items(versionsToDisplay) { ver ->
                            val isSelected = ver.id == selectedMcVersion
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) EzzColors.PrimaryGlow else Color.Transparent)
                                    .clickable {
                                        selectedMcVersion = ver.id
                                        if (name.startsWith("Minecraft")) {
                                            name = "Minecraft ${ver.id}"
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = ver.id,
                                        color = if (isSelected) EzzColors.Primary else EzzColors.TextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = ver.releaseTime.take(10),
                                        color = EzzColors.TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Loader Type Selector
                Text("Installation Type", color = EzzColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LoaderOptionButton(
                        name = "Vanilla",
                        isSelected = selectedLoader == LoaderType.VANILLA,
                        onClick = { selectedLoader = LoaderType.VANILLA },
                        modifier = Modifier.weight(1f)
                    )
                    LoaderOptionButton(
                        name = "Fabric",
                        isSelected = selectedLoader == LoaderType.FABRIC,
                        onClick = { selectedLoader = LoaderType.FABRIC },
                        modifier = Modifier.weight(1f)
                    )
                    LoaderOptionButton(
                        name = "OptiFine",
                        isSelected = selectedLoader == LoaderType.OPTIFINE,
                        onClick = { selectedLoader = LoaderType.OPTIFINE },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Fabric Loader version indicator
                if (selectedLoader == LoaderType.FABRIC) {
                    Spacer(modifier = Modifier.height(10.dp))
                    if (isLoadingFabricLoaders) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = EzzColors.Primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Fetching Fabric loader versions...", color = EzzColors.TextSecondary, fontSize = 12.sp)
                        }
                    } else {
                        Text(
                            text = "Fabric Loader: ${selectedFabricLoader ?: "Latest"}",
                            color = EzzColors.Primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else if (selectedLoader == LoaderType.OPTIFINE) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val optiVer = OptiFineCompatibilityValidator.getSuggestedOptiFineVersion(selectedMcVersion)
                    Text(
                        text = "OptiFine Profile: $optiVer",
                        color = Color(0xFFEC4899),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = EzzColors.SurfaceVariant),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel", color = EzzColors.TextPrimary)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            val finalLoaderVersion = when (selectedLoader) {
                                LoaderType.FABRIC -> selectedFabricLoader ?: "0.16.9"
                                LoaderType.OPTIFINE -> OptiFineCompatibilityValidator.getSuggestedOptiFineVersion(selectedMcVersion)
                                LoaderType.VANILLA -> null
                            }
                            viewModel.createInstance(
                                name = name.ifBlank { "Minecraft $selectedMcVersion" },
                                minecraftVersion = selectedMcVersion,
                                loaderType = selectedLoader,
                                loaderVersion = finalLoaderVersion,
                                minMemoryMb = minRamMb,
                                maxMemoryMb = maxRamMb,
                                customJvmArgs = emptyList()
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EzzColors.Primary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Create", color = Color(0xFF0B0F19), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoaderOptionButton(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) EzzColors.PrimaryGlow else EzzColors.SurfaceVariant)
            .border(1.dp, if (isSelected) EzzColors.Primary else EzzColors.Border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            color = if (isSelected) EzzColors.Primary else EzzColors.TextSecondary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp
        )
    }
}
