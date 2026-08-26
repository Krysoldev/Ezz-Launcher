package io.ezz.launcher.ui.manager.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.AppViewModel

@Composable
fun InstanceSettingsTab(
    instance: Instance,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val colors = EzzTheme.colors
    val scrollState = rememberScrollState()

    var name by remember(instance.id) { mutableStateOf(instance.name) }
    var minMemoryMb by remember(instance.id) { mutableStateOf(instance.minMemoryMb) }
    var maxMemoryMb by remember(instance.id) { mutableStateOf(instance.maxMemoryMb) }
    var customJvmArgs by remember(instance.id) { mutableStateOf(instance.customJvmArgs.joinToString(" ")) }
    var customJavaPath by remember(instance.id) { mutableStateOf(instance.javaPath ?: "") }
    var windowWidth by remember(instance.id) { mutableStateOf(instance.windowWidth.toString()) }
    var windowHeight by remember(instance.id) { mutableStateOf(instance.windowHeight.toString()) }

    var isSavedToast by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "INSTANCE SETTINGS",
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Customize dedicated memory, Java, and launch options",
                    color = colors.textMuted,
                    fontSize = 13.sp
                )
            }

            Button(
                onClick = {
                    val updated = instance.copy(
                        name = name.trim(),
                        minMemoryMb = minMemoryMb,
                        maxMemoryMb = maxMemoryMb,
                        customJvmArgs = if (customJvmArgs.isNotBlank()) customJvmArgs.trim().split(" ") else emptyList(),
                        javaPath = if (customJavaPath.isNotBlank()) customJavaPath.trim() else null,
                        windowWidth = windowWidth.toIntOrNull() ?: 1280,
                        windowHeight = windowHeight.toIntOrNull() ?: 720
                    )
                    viewModel.updateInstance(updated)
                    isSavedToast = true
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Settings", fontWeight = FontWeight.Black)
            }
        }

        if (isSavedToast) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF10B981).copy(alpha = 0.2f))
                    .border(1.dp, Color(0xFF10B981), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text("Settings saved successfully!", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // Section 1: INSTANCE IDENTITY & CUSTOM LOGO
        SettingsCard(title = "INSTANCE IDENTITY") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Name
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Instance Name", color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true
                    )
                }

                // Custom Icon
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Instance Icon", color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        // Live Icon Preview
                        io.ezz.launcher.ui.components.InstanceArtworkIcon(
                            instance = instance,
                            size = 80.dp
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        val picked = viewModel.platformBridge.pickImageFile("Select Instance Icon (PNG, JPG, WEBP)")
                                        if (picked != null && picked.exists()) {
                                            viewModel.changeInstanceCustomIcon(instance.id, picked)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("CHANGE ICON", fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 0.5.sp)
                                }

                                if (!instance.customIconPath.isNullOrBlank()) {
                                    androidx.compose.material3.OutlinedButton(
                                        onClick = {
                                            viewModel.removeInstanceCustomIcon(instance.id)
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color(0xFFEF5350)
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("REMOVE ICON", fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.5.sp)
                                    }
                                }
                            }

                            Text(
                                text = "Supported formats: PNG, JPG, JPEG, WEBP. Icon is copied locally and persists offline.",
                                color = Color(0xFF777777),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // Section 2: Memory & Java
        SettingsCard(title = "Memory Allocation & Java Runtime") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Allocated RAM (Maximum)", color = colors.textSecondary, fontSize = 13.sp)
                    Text(
                        text = "${maxMemoryMb / 1024} GB (${maxMemoryMb} MB)",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    )
                }

                Slider(
                    value = maxMemoryMb.toFloat(),
                    onValueChange = { maxMemoryMb = it.toInt() },
                    valueRange = 1024f..16384f,
                    steps = 15,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = colors.surfaceLight
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MemoryPresetButton("2 GB", maxMemoryMb == 2048) { maxMemoryMb = 2048 }
                    MemoryPresetButton("4 GB", maxMemoryMb == 4096) { maxMemoryMb = 4096 }
                    MemoryPresetButton("6 GB", maxMemoryMb == 6144) { maxMemoryMb = 6144 }
                    MemoryPresetButton("8 GB", maxMemoryMb == 8192) { maxMemoryMb = 8192 }
                    MemoryPresetButton("12 GB", maxMemoryMb == 12288) { maxMemoryMb = 12288 }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text("Custom Java Executable Path (Optional)", color = colors.textSecondary, fontSize = 13.sp)
                TextField(
                    value = customJavaPath,
                    onValueChange = { customJavaPath = it },
                    placeholder = { Text("Leave empty to use auto-detected Java runtime", color = colors.textMuted, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true
                )

                Text("Custom JVM Arguments", color = colors.textSecondary, fontSize = 13.sp)
                TextField(
                    value = customJvmArgs,
                    onValueChange = { customJvmArgs = it },
                    placeholder = { Text("-XX:+UseG1GC -XX:+UnlockExperimentalVMOptions", color = colors.textMuted, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true
                )
            }
        }

        // Section 3: Window Resolution
        SettingsCard(title = "Game Window Resolution") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Width", color = colors.textSecondary, fontSize = 13.sp)
                    TextField(
                        value = windowWidth,
                        onValueChange = { windowWidth = it },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Height", color = colors.textSecondary, fontSize = 13.sp)
                    TextField(
                        value = windowHeight,
                        onValueChange = { windowHeight = it },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
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

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    val colors = EzzTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.cardBackground)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(text = title, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun MemoryPresetButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = EzzTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Color.White else colors.surface)
            .border(1.dp, if (selected) Color.White else colors.border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
