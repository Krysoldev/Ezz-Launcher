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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.ui.components.EzzBadge
import io.ezz.launcher.ui.components.EzzBadgeVariant
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.EzzCard
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
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(22.dp)) {
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
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.3.sp
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
                        onClick = onDismiss,
                        size = EzzButtonSize.SMALL,
                        variant = EzzButtonVariant.GHOST
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Sidebar + Detail Container
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Category Navigation
                    Column(
                        modifier = Modifier
                            .width(180.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF101010))
                            .border(1.dp, Color(0xFF202020), RoundedCornerShape(6.dp))
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        InstanceSettingSection.entries.forEach { section ->
                            val isSelected = activeSection == section
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelected) Color(0xFF222222) else Color.Transparent)
                                    .clickable { activeSection = section }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = section.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else Color(0xFF888888),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = section.title,
                                    color = if (isSelected) Color.White else Color(0xFF888888),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Content Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF121212))
                            .border(1.dp, Color(0xFF222222), RoundedCornerShape(6.dp))
                            .padding(18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            when (activeSection) {
                                InstanceSettingSection.GENERAL -> {
                                    EzzTextField(
                                        value = name,
                                        onValueChange = { name = it },
                                        label = "Instance Name",
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    EzzCard(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("Metadata & Location", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text("Game Version: Minecraft ${instance.minecraftVersion}", color = Color(0xFFA0A0A0), fontSize = 11.sp)
                                            Text("Mod Loader: ${instance.loaderType.name}", color = Color(0xFFA0A0A0), fontSize = 11.sp)
                                            Text("Root: ${viewModel.pathProvider.getInstanceDirectory(instance.id)}", color = Color(0xFF777777), fontSize = 10.sp)
                                        }
                                    }
                                }
                                InstanceSettingSection.JAVA -> {
                                    EzzSlider(
                                        value = maxRamMb,
                                        onValueChange = { maxRamMb = it },
                                        valueRange = 1024f..16384f,
                                        steps = 15,
                                        label = "RAM Allocation",
                                        valueDisplay = "${(maxRamMb / 1024).toInt()} GB"
                                    )

                                    EzzTextField(
                                        value = javaPath,
                                        onValueChange = { javaPath = it },
                                        label = "Custom Java Binary Path (leave blank for Auto)",
                                        placeholder = "C:\\Program Files\\Java\\jdk-21\\bin\\javaw.exe",
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                InstanceSettingSection.GAME -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        EzzTextField(
                                            value = windowWidth,
                                            onValueChange = { windowWidth = it },
                                            label = "Window Width (px)",
                                            modifier = Modifier.weight(1f)
                                        )
                                        EzzTextField(
                                            value = windowHeight,
                                            onValueChange = { windowHeight = it },
                                            label = "Window Height (px)",
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                InstanceSettingSection.MODS -> {
                                    Text("Installed Mods (${installedMods.size})", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    if (installedMods.isEmpty()) {
                                        Text(
                                            text = "No mods found in this instance. Use the Mods tab or drag .jar files into the mods folder.",
                                            color = Color(0xFF777777),
                                            fontSize = 12.sp
                                        )
                                    } else {
                                        installedMods.forEach { mod ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFF181818))
                                                    .border(1.dp, Color(0xFF242424), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(mod.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                Text(mod.version, color = Color(0xFF888888), fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                                InstanceSettingSection.ADVANCED -> {
                                    EzzTextField(
                                        value = customJvmArgs,
                                        onValueChange = { customJvmArgs = it },
                                        label = "Custom JVM Arguments",
                                        placeholder = "-XX:+UseG1GC -XX:+UnlockExperimentalVMOptions",
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footer Save Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EzzButton(
                        text = "Open Folder",
                        icon = Icons.Default.FolderOpen,
                        onClick = { viewModel.openInstanceFolder(instance.id) },
                        variant = EzzButtonVariant.SECONDARY,
                        size = EzzButtonSize.MEDIUM
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EzzButton(
                            text = "Cancel",
                            onClick = onDismiss,
                            variant = EzzButtonVariant.GHOST,
                            size = EzzButtonSize.MEDIUM
                        )
                        EzzButton(
                            text = "Save Changes",
                            onClick = {
                                val updated = instance.copy(
                                    name = name.ifBlank { instance.name },
                                    maxMemoryMb = maxRamMb.toInt(),
                                    javaPath = javaPath.ifBlank { null },
                                    windowWidth = windowWidth.toIntOrNull() ?: instance.windowWidth,
                                    windowHeight = windowHeight.toIntOrNull() ?: instance.windowHeight,
                                    customJvmArgs = customJvmArgs.split(" ").filter { it.isNotBlank() }
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
}
