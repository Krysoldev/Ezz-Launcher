package io.ezz.launcher.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.ui.components.EzzBadge
import io.ezz.launcher.ui.components.EzzBadgeVariant
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.EzzIconButton
import io.ezz.launcher.ui.components.EzzLoaderBadge
import io.ezz.launcher.ui.components.EzzSlider
import io.ezz.launcher.ui.components.EzzTextField
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.AppViewModel

private enum class InstanceSettingSection(val title: String, val icon: ImageVector) {
    GENERAL("General", Icons.Default.Settings),
    JAVA("Java & RAM", Icons.Default.Memory),
    GAME("Game & Screen", Icons.Default.DisplaySettings),
    MODS("Mods & Packs", Icons.Default.Extension),
    ADVANCED("Advanced", Icons.Default.Tune)
}

@Composable
fun EditInstanceDialog(
    instance: Instance,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val colors = EzzTheme.colors
    var activeSection by remember { mutableStateOf(InstanceSettingSection.GENERAL) }

    var name by remember { mutableStateOf(instance.name) }
    var maxRamMb by remember { mutableStateOf(instance.maxMemoryMb.toFloat()) }
    var javaPath by remember { mutableStateOf(instance.javaPath ?: "") }
    var windowWidth by remember { mutableStateOf(instance.windowWidth.toString()) }
    var windowHeight by remember { mutableStateOf(instance.windowHeight.toString()) }
    var customJvmArgs by remember { mutableStateOf(instance.customJvmArgs.joinToString(" ")) }
    val installedMods by viewModel.installedMods.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(520.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, colors.borderLight, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(
                                text = "Configure ${instance.name}",
                                color = colors.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                EzzLoaderBadge(loaderType = instance.loaderType)
                                Spacer(modifier = Modifier.width(6.dp))
                                EzzBadge(text = "Minecraft ${instance.minecraftVersion}", variant = EzzBadgeVariant.NEUTRAL)
                            }
                        }
                    }

                    EzzIconButton(
                        icon = Icons.Default.Close,
                        onClick = onDismiss
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Split Layout: Nav Tabs on left, Settings panel on right
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    // Left Navigation Tabs
                    Column(
                        modifier = Modifier
                            .width(160.dp)
                            .fillMaxHeight()
                            .background(colors.surfaceVariant)
                            .clip(RoundedCornerShape(8.dp))
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        InstanceSettingSection.values().forEach { section ->
                            val isSelected = activeSection == section
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) colors.primary.copy(alpha = 0.15f) else colors.surfaceVariant)
                                    .border(1.dp, if (isSelected) colors.primary else colors.border, RoundedCornerShape(6.dp))
                                    .clickable { activeSection = section }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = section.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) colors.primary else colors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = section.title,
                                    color = if (isSelected) colors.textPrimary else colors.textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Right Content Area
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(colors.surface)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        when (activeSection) {
                            InstanceSettingSection.GENERAL -> {
                                Text(text = "GENERAL SETTINGS", color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                EzzTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    label = "Instance Display Name",
                                    placeholder = "Survival SMP"
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(text = "Minecraft Version: ${instance.minecraftVersion}", color = colors.textSecondary, fontSize = 13.sp)
                                Text(text = "Mod Loader: ${instance.loaderType.name}", color = colors.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }

                            InstanceSettingSection.JAVA -> {
                                Text(text = "JAVA RUNTIME & RAM", color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                EzzSlider(
                                    value = maxRamMb,
                                    onValueChange = { maxRamMb = it },
                                    valueRange = 1024f..16384f,
                                    steps = 15,
                                    label = "RAM Allocation",
                                    valueDisplay = "${(maxRamMb / 1024).toInt()} GB"
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                EzzTextField(
                                    value = javaPath,
                                    onValueChange = { javaPath = it },
                                    label = "Custom Java Path",
                                    placeholder = "Auto-detect system Java"
                                )
                            }

                            InstanceSettingSection.GAME -> {
                                Text(text = "GAME DIRECTORY & DISPLAY", color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    EzzTextField(
                                        value = windowWidth,
                                        onValueChange = { windowWidth = it },
                                        label = "Window Width",
                                        placeholder = "1280",
                                        modifier = Modifier.weight(1f)
                                    )
                                    EzzTextField(
                                        value = windowHeight,
                                        onValueChange = { windowHeight = it },
                                        label = "Window Height",
                                        placeholder = "720",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                EzzButton(
                                    text = "Open Game Directory",
                                    icon = Icons.Default.FolderOpen,
                                    onClick = { viewModel.openInstanceFolder(instance.id) },
                                    variant = EzzButtonVariant.SECONDARY,
                                    size = EzzButtonSize.SMALL
                                )
                            }

                            InstanceSettingSection.MODS -> {
                                Text(text = "INSTALLED MODS (${installedMods.size})", color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                if (installedMods.isEmpty()) {
                                    Text(text = "No mods installed in this instance.", color = colors.textSecondary, fontSize = 13.sp)
                                } else {
                                    installedMods.forEach { mod ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = mod.name, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                            Text(text = if (mod.enabled) "Active" else "Disabled", color = if (mod.enabled) colors.accent else colors.textMuted, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }

                            InstanceSettingSection.ADVANCED -> {
                                Text(text = "ADVANCED JVM FLAGS", color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                EzzTextField(
                                    value = customJvmArgs,
                                    onValueChange = { customJvmArgs = it },
                                    label = "Custom JVM Arguments",
                                    placeholder = "-XX:+UseG1GC -XX:MaxGCPauseMillis=50"
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footer Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    EzzButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        variant = EzzButtonVariant.GHOST,
                        size = EzzButtonSize.MEDIUM
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    EzzButton(
                        text = "Save Settings",
                        onClick = {
                            val updated = instance.copy(
                                name = name.ifBlank { instance.name },
                                maxMemoryMb = maxRamMb.toInt(),
                                javaPath = javaPath.ifBlank { null },
                                windowWidth = windowWidth.toIntOrNull() ?: 1280,
                                windowHeight = windowHeight.toIntOrNull() ?: 720,
                                customJvmArgs = if (customJvmArgs.isBlank()) emptyList() else customJvmArgs.split(" ")
                            )
                            viewModel.updateInstance(updated)
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
