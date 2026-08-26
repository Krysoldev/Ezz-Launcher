package io.ezz.launcher.ui.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.ui.components.EzzBadge
import io.ezz.launcher.ui.components.EzzBadgeVariant
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.EzzCard
import io.ezz.launcher.ui.components.EzzIconButton
import io.ezz.launcher.ui.components.EzzLogo
import io.ezz.launcher.ui.components.EzzSlider
import io.ezz.launcher.ui.components.EzzTextField
import io.ezz.launcher.ui.components.EzzToggle
import io.ezz.launcher.ui.components.ToastManager
import io.ezz.launcher.ui.components.ToastType
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.theme.ThemePreset
import io.ezz.launcher.ui.viewmodel.AppViewModel
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

enum class SettingsCategory(val title: String, val icon: ImageVector) {
    LAUNCHER("General", Icons.Default.Settings),
    APPEARANCE("Appearance", Icons.Default.Palette),
    JAVA_PERFORMANCE("Java & RAM", Icons.Default.Memory),
    CLOUD("Cloud & Supabase", Icons.Default.Cloud),
    DIAGNOSTICS("Diagnostics", Icons.Default.Terminal),
    ABOUT("About", Icons.Default.Info)
}

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val colors = EzzTheme.colors
    var activeCategory by remember { mutableStateOf(SettingsCategory.LAUNCHER) }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Settings Sub-Sidebar
        Column(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .background(colors.surface)
                .border(androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.5f)))
                .padding(20.dp)
        ) {
            Text(
                text = "Settings",
                color = colors.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Preferences",
                color = colors.textSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            SettingsCategory.values().forEach { category ->
                val isSelected = activeCategory == category
                val interactionSource = remember { MutableInteractionSource() }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) colors.primary.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { activeCategory = category }
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = category.title,
                        tint = if (isSelected) colors.primary else colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = category.title,
                        color = if (isSelected) colors.primary else colors.textSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Category Content Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            when (activeCategory) {
                SettingsCategory.LAUNCHER -> GeneralSettingsSection(viewModel)
                SettingsCategory.APPEARANCE -> AppearanceSettingsSection()
                SettingsCategory.JAVA_PERFORMANCE -> JavaSettingsSection(viewModel)
                SettingsCategory.CLOUD -> CloudSettingsSection(viewModel)
                SettingsCategory.DIAGNOSTICS -> DiagnosticsSettingsSection(viewModel)
                SettingsCategory.ABOUT -> AboutSettingsSection(viewModel)
            }
        }
    }
}

@Composable
private fun GeneralSettingsSection(viewModel: AppViewModel) {
    val settings by viewModel.settingsRepository.settings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionHeader("General Launcher Settings", "Configure startup, process lifecycle, and default actions")

        EzzCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                EzzToggle(
                    checked = settings.closeLauncherOnLaunch,
                    onCheckedChange = { viewModel.updateCloseOnLaunch(it) },
                    label = "Close Launcher on Game Start",
                    description = "Minimizes background resource consumption by closing the launcher window when Minecraft starts"
                )
            }
        }
    }
}

@Composable
private fun AppearanceSettingsSection() {
    val colors = EzzTheme.colors
    val themeState = EzzTheme.state

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionHeader("Appearance & Customization", "Personalize themes, accent colors, density, and animation effects")

        Text("Theme Preset", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ThemePreset.values().forEach { preset ->
                val isSelected = themeState.preset == preset
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) colors.primary.copy(alpha = 0.15f) else colors.surfaceVariant)
                        .border(1.dp, if (isSelected) colors.primary else colors.border, RoundedCornerShape(10.dp))
                        .clickable { themeState.preset = preset }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = preset.displayName.substringBefore(" ("),
                        color = if (isSelected) colors.primary else colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Custom Accent Color", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))

        val palette = listOf(
            Color(0xFF00E5FF), // Cyan
            Color(0xFF10B981), // Emerald
            Color(0xFFA855F7), // Amethyst Purple
            Color(0xFFF59E0B), // Cyber Gold
            Color(0xFFEF4444), // Crimson
            Color(0xFF3B82F6), // Azure Blue
            Color(0xFFEC4899)  // Hot Pink
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            palette.forEach { c ->
                val isPicked = themeState.customPrimaryColor == c
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(if (isPicked) 3.dp else 1.dp, if (isPicked) Color.White else colors.border, CircleShape)
                        .clickable { themeState.customPrimaryColor = c }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        EzzCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                EzzToggle(
                    checked = themeState.enableAnimations,
                    onCheckedChange = { themeState.enableAnimations = it },
                    label = "Enable Smooth UI Animations",
                    description = "Enables interactive button scales, transitions, and glow effects"
                )
            }
        }
    }
}

@Composable
private fun JavaSettingsSection(viewModel: AppViewModel) {
    val colors = EzzTheme.colors
    val settings by viewModel.settingsRepository.settings.collectAsState()
    val detectedJava by viewModel.detectedJavaRuntimes.collectAsState()

    var maxRam by remember(settings.defaultMaxMemoryMb) { mutableStateOf(settings.defaultMaxMemoryMb.toFloat()) }
    var jvmArgs by remember(settings.globalJvmArgs) { mutableStateOf(settings.globalJvmArgs.joinToString(" ")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionHeader("Java Runtime & Memory Configuration", "Configure system-wide default RAM heap sizes, GC parameters, and detected JREs")

        EzzCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                EzzSlider(
                    value = maxRam,
                    onValueChange = {
                        maxRam = it
                        viewModel.updateMemorySettings(1024, it.toInt())
                    },
                    valueRange = 1024f..16384f,
                    steps = 15,
                    label = "Global Maximum Memory (RAM)",
                    valueDisplay = "${(maxRam / 1024).toInt()} GB"
                )

                Spacer(modifier = Modifier.height(20.dp))

                EzzTextField(
                    value = jvmArgs,
                    onValueChange = {
                        jvmArgs = it
                        viewModel.updateGlobalJvmArgs(if (it.isBlank()) emptyList() else it.split(" "))
                    },
                    label = "Global JVM Arguments",
                    placeholder = "-XX:+UseG1GC -XX:MaxGCPauseMillis=50"
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Installed Java Runtimes (${detectedJava.size})", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            EzzButton(
                text = "Scan Java",
                onClick = { viewModel.refreshJavaRuntimes() },
                variant = EzzButtonVariant.SECONDARY,
                size = EzzButtonSize.SMALL,
                icon = Icons.Default.Refresh
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        detectedJava.forEach { jre ->
            EzzCard(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                backgroundColor = colors.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Java ${jre.majorVersion}", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            EzzBadge(text = if (jre.is64Bit) "64-bit" else "32-bit", variant = EzzBadgeVariant.NEUTRAL)
                        }
                        Text(jre.path, color = colors.textMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    }

                    if (jre.majorVersion in listOf(8, 17, 21)) {
                        EzzBadge(text = "LTS SUPPORTED", variant = EzzBadgeVariant.SUCCESS)
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudSettingsSection(viewModel: AppViewModel) {
    val colors = EzzTheme.colors
    val isConnected by viewModel.isSupabaseConnected.collectAsState()
    val isTesting by viewModel.isTestingSupabaseConnection.collectAsState()
    val statusMsg by viewModel.supabaseStatusMessage.collectAsState()

    var supabaseUrl by remember { mutableStateOf(viewModel.supabaseClient?.config?.supabaseUrl ?: "https://idywzmspumhahzzfsdjx.supabase.co") }
    var anonKey by remember { mutableStateOf(viewModel.supabaseClient?.config?.anonKey ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionHeader("Supabase Cloud Database", "Configure Supabase PostgreSQL backend connection for cloud profiles and public data")

        EzzCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isConnected == true) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (isConnected == true) colors.accent else colors.warning,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("PostgreSQL Cloud Service", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (isConnected == true) "Connected & Synchronized" else "Offline / Local Cache Active",
                                color = if (isConnected == true) colors.accent else colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    EzzBadge(
                        text = if (isConnected == true) "ONLINE" else "LOCAL ONLY",
                        variant = if (isConnected == true) EzzBadgeVariant.SUCCESS else EzzBadgeVariant.NEUTRAL
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                EzzTextField(
                    value = supabaseUrl,
                    onValueChange = { supabaseUrl = it },
                    label = "Supabase Project URL",
                    placeholder = "https://your-project.supabase.co",
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                EzzTextField(
                    value = anonKey,
                    onValueChange = { anonKey = it },
                    label = "Supabase Anon Key (Public API Key)",
                    placeholder = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                    modifier = Modifier.fillMaxWidth()
                )

                if (statusMsg != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = statusMsg ?: "",
                        color = if (isConnected == true) colors.accent else colors.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EzzButton(
                        text = if (isTesting) "Testing Connection..." else "Save & Test Connection",
                        onClick = { viewModel.updateSupabaseCredentials(supabaseUrl, anonKey) },
                        variant = EzzButtonVariant.PRIMARY,
                        size = EzzButtonSize.MEDIUM,
                        isLoading = isTesting
                    )

                    EzzButton(
                        text = "Sync Public Data",
                        onClick = { viewModel.loadPublicData() },
                        variant = EzzButtonVariant.SECONDARY,
                        size = EzzButtonSize.MEDIUM
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsSettingsSection(viewModel: AppViewModel) {
    val osName = System.getProperty("os.name") ?: "Windows"
    val osArch = System.getProperty("os.arch") ?: "x64"
    val javaVersion = System.getProperty("java.version") ?: "21"
    val cores = Runtime.getRuntime().availableProcessors()
    val totalRamGb = (Runtime.getRuntime().maxMemory() / (1024 * 1024 * 1024.0)).toInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader("System Diagnostics", "Hardware, JVM architecture, and diagnostic telemetry")
            EzzButton(
                text = "Copy Diagnostics",
                onClick = {
                    val report = buildString {
                        appendLine("=== EZZ LAUNCHER DIAGNOSTIC REPORT ===")
                        appendLine("Launcher Version: 1.0.0")
                        appendLine("OS: $osName ($osArch)")
                        appendLine("JVM Version: $javaVersion")
                        appendLine("Logical CPU Cores: $cores")
                        appendLine("Max JVM Memory: ${totalRamGb} GB")
                    }
                    viewModel.platformBridge.copyToClipboard(report)
                },
                variant = EzzButtonVariant.SECONDARY,
                size = EzzButtonSize.SMALL
            )
        }

        EzzCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                DiagnosticRow("Operating System", "$osName ($osArch)")
                DiagnosticRow("Host CPU Cores", "$cores Logical Processors")
                DiagnosticRow("Runtime Java", "Java $javaVersion")
                DiagnosticRow("Instance Data Root", viewModel.pathProvider.instancesDirectory.toString())
            }
        }
    }
}

@Composable
private fun AboutSettingsSection(viewModel: AppViewModel) {
    val colors = EzzTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionHeader("About Ezz Launcher", "Modern, high-performance Minecraft Java Edition client platform")

        EzzCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EzzLogo(size = 54.dp, shapeRadius = 12.dp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("EZZ", color = colors.primary, fontSize = 22.sp, fontWeight = FontWeight.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("LAUNCHER", color = colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        }
                        Text("Version 1.0.0 (Windows Production Desktop)", color = colors.textSecondary, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Built with Kotlin Multiplatform and Jetpack Compose for Desktop. Powered by Supabase PostgreSQL for cloud profile synchronization.",
                    color = colors.textMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                EzzButton(
                    text = "Open GitHub Repository",
                    onClick = { viewModel.platformBridge.openUrl("https://github.com/Krysoldev/Ezz-Launcher") },
                    variant = EzzButtonVariant.SECONDARY,
                    size = EzzButtonSize.MEDIUM
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    val colors = EzzTheme.colors
    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        Text(text = title, color = colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(text = subtitle, color = colors.textSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    val colors = EzzTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = colors.textSecondary, fontSize = 13.sp)
        Text(value, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
