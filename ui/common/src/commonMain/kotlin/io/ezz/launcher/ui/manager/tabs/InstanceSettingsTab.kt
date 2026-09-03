package io.ezz.launcher.ui.manager.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.minecraft.mods.PerformanceModAdvisor
import io.ezz.launcher.core.model.instance.FpsMode
import io.ezz.launcher.core.model.instance.GarbageCollectorType
import io.ezz.launcher.core.model.instance.GpuPreference
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.model.instance.PerformanceProfile
import io.ezz.launcher.core.model.instance.ProcessPriority
import io.ezz.launcher.core.runtime.detector.JavaRuntimeDetector
import io.ezz.launcher.ui.components.EzzBadge
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.EzzTextField
import io.ezz.launcher.ui.components.InstanceArtworkIcon
import io.ezz.launcher.ui.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InstanceSettingsTab(
    instance: Instance,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val detectedRuntimes by viewModel.detectedJavaRuntimes.collectAsState()
    val detectedGpus by viewModel.detectedGpus.collectAsState()
    val systemMemory by viewModel.systemMemoryInfo.collectAsState()
    val installedMods by viewModel.installedMods.collectAsState()

    var name by remember(instance.id) { mutableStateOf(instance.name) }
    var minMemoryMb by remember(instance.id) { mutableStateOf(instance.minMemoryMb) }
    var maxMemoryMb by remember(instance.id) { mutableStateOf(instance.maxMemoryMb) }
    var customJvmArgs by remember(instance.id) { mutableStateOf(instance.customJvmArgs.joinToString(" ")) }
    var customJavaPath by remember(instance.id) { mutableStateOf(instance.javaPath ?: "") }
    var windowWidth by remember(instance.id) { mutableStateOf(instance.windowWidth.toString()) }
    var windowHeight by remember(instance.id) { mutableStateOf(instance.windowHeight.toString()) }

    var performanceProfile by remember(instance.id) { mutableStateOf(instance.performanceProfile) }
    var fpsMode by remember(instance.id) { mutableStateOf(instance.fpsMode) }
    var customFpsLimit by remember(instance.id) { mutableStateOf(instance.customFpsLimit.toString()) }
    var gpuPreference by remember(instance.id) { mutableStateOf(instance.gpuPreference) }
    var processPriority by remember(instance.id) { mutableStateOf(instance.processPriority) }
    var gcType by remember(instance.id) { mutableStateOf(instance.gcType) }
    var enableDiagnostics by remember(instance.id) { mutableStateOf(instance.enableDiagnostics) }
    var ezzSkinEnabled by remember(instance.id) { mutableStateOf(instance.ezzSkinEnabled) }

    var statusMessage by remember { mutableStateOf<String?>(null) }

    val activeJava = remember(customJavaPath, detectedRuntimes, instance.minecraftVersion) {
        if (customJavaPath.isNotBlank()) {
            detectedRuntimes.firstOrNull { it.path.equals(customJavaPath.trim(), ignoreCase = true) }
                ?: io.ezz.launcher.core.runtime.detector.JavaRuntimeDetector.inspectJavaHome(customJavaPath.trim())
        } else {
            io.ezz.launcher.core.runtime.detector.JavaRuntimeDetector.findBestRuntime(instance.minecraftVersion, detectedRuntimes)
        }
    }

    val modRecommendations = remember(instance, installedMods) {
        PerformanceModAdvisor.evaluatePerformanceMods(instance, installedMods)
    }

    val hwProfile = remember { io.ezz.launcher.core.runtime.detector.HardwareDetector.detectHardware() }
    var diagnosticReport by remember { mutableStateOf<io.ezz.launcher.core.runtime.diagnostics.LiveDiagnosticReport?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header & Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "INSTANCE & PERFORMANCE CONFIGURATION",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Hardware profiling, dedicated GPU binding, minimal JVM tuning, and live performance diagnostics",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.5.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EzzButton(
                    text = "Run Diagnostics",
                    onClick = {
                        val pathProvider = io.ezz.launcher.core.storage.path.DefaultPathProvider.createDefault()
                        val gameDir = pathProvider.getInstanceGameDirectory(instance.id).toFile()
                        val report = io.ezz.launcher.core.runtime.diagnostics.PerformanceDiagnosticService.generateDiagnosticReport(
                            instance = instance,
                            gameDir = gameDir,
                            javaRuntime = activeJava ?: io.ezz.launcher.core.model.runtime.JavaRuntime(
                                path = "java",
                                majorVersion = 21,
                                fullVersion = "21.0.0",
                                is64Bit = true,
                                vendor = "Temurin"
                            )
                        )
                        diagnosticReport = report
                        statusMessage = "Diagnostics generated. Detected Bottleneck: ${report.bottleneck.title} | FPS Cap Source: ${report.fpsCapSource}."
                    },
                    icon = Icons.Default.CheckCircle,
                    variant = EzzButtonVariant.SECONDARY,
                    size = EzzButtonSize.MEDIUM
                )

                EzzButton(
                    text = "Auto-Optimize for My PC",
                    onClick = {
                        val recMax = hwProfile.recommendedMaxRamMb
                        val recMin = hwProfile.recommendedMinRamMb
                        val recommendedProfile = hwProfile.recommendedProfile
                        val recGpuPref = hwProfile.recommendedGpuPreference

                        maxMemoryMb = recMax
                        minMemoryMb = recMin
                        performanceProfile = recommendedProfile
                        fpsMode = FpsMode.UNLIMITED
                        customFpsLimit = "260"
                        gpuPreference = recGpuPref
                        gcType = GarbageCollectorType.AUTO
                        processPriority = ProcessPriority.ABOVE_NORMAL
                        customJavaPath = ""

                        val updated = instance.copy(
                            minMemoryMb = recMin,
                            maxMemoryMb = recMax,
                            performanceProfile = recommendedProfile,
                            fpsMode = FpsMode.UNLIMITED,
                            customFpsLimit = 260,
                            gpuPreference = recGpuPref,
                            gcType = GarbageCollectorType.AUTO,
                            processPriority = ProcessPriority.ABOVE_NORMAL,
                            javaPath = null
                        )
                        viewModel.updateInstance(updated)
                        statusMessage = "Auto-optimized for ${hwProfile.cpuModel}: Allocated ${recMax} MB RAM, set ${recommendedProfile.displayName} profile, Unlimited FPS, and ${hwProfile.primaryGpu}."
                    },
                    icon = Icons.Default.AutoFixHigh,
                    variant = EzzButtonVariant.SECONDARY,
                    size = EzzButtonSize.MEDIUM
                )

                EzzButton(
                    text = "Save Configuration",
                    onClick = {
                        val updated = instance.copy(
                            name = name.trim(),
                            minMemoryMb = minMemoryMb,
                            maxMemoryMb = maxMemoryMb,
                            customJvmArgs = if (customJvmArgs.isNotBlank()) customJvmArgs.trim().split(" ") else emptyList(),
                            javaPath = if (customJavaPath.isNotBlank()) customJavaPath.trim() else null,
                            windowWidth = windowWidth.toIntOrNull() ?: 1280,
                            windowHeight = windowHeight.toIntOrNull() ?: 720,
                            performanceProfile = performanceProfile,
                            fpsMode = fpsMode,
                            customFpsLimit = customFpsLimit.toIntOrNull() ?: 260,
                            gpuPreference = gpuPreference,
                            processPriority = processPriority,
                            gcType = gcType,
                            enableDiagnostics = enableDiagnostics,
                            ezzSkinEnabled = ezzSkinEnabled
                        )
                        viewModel.updateInstance(updated)
                        statusMessage = "Configuration, performance profile, and Ezz Skin mode saved successfully!"
                    },
                    icon = Icons.Default.Save,
                    variant = EzzButtonVariant.PRIMARY,
                    size = EzzButtonSize.MEDIUM
                )
            }
        }

        if (statusMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF10B981).copy(alpha = 0.15f))
                    .border(1.dp, Color(0xFF10B981), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                    Text(statusMessage!!, color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
            }
        }

        // Section: SAFE ROLLBACK TO KNOWN GOOD CONFIGURATION (if available)
        val snapshot = instance.knownGoodSnapshot
        if (snapshot != null) {
            val dateStr = remember(snapshot.timestamp) {
                SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(snapshot.timestamp))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("KNOWN-GOOD CONFIGURATION BACKUP", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            EzzBadge(text = "Verified at $dateStr")
                        }
                        Text(
                            text = "Last verified stable session: ${snapshot.maxMemoryMb / 1024} GB RAM, ${snapshot.performanceProfile.displayName} profile, ${snapshot.gpuPreference.displayName}.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.5.sp
                        )
                    }

                    EzzButton(
                        text = "Restore Known-Good Settings",
                        onClick = {
                            val restored = instance.rollbackToSnapshot(snapshot)
                            viewModel.updateInstance(restored)
                            minMemoryMb = snapshot.minMemoryMb
                            maxMemoryMb = snapshot.maxMemoryMb
                            customJvmArgs = snapshot.customJvmArgs.joinToString(" ")
                            customJavaPath = snapshot.javaPath ?: ""
                            windowWidth = snapshot.windowWidth.toString()
                            windowHeight = snapshot.windowHeight.toString()
                            performanceProfile = snapshot.performanceProfile
                            fpsMode = snapshot.fpsMode
                            customFpsLimit = snapshot.customFpsLimit.toString()
                            gpuPreference = snapshot.gpuPreference
                            processPriority = snapshot.processPriority
                            gcType = snapshot.gcType
                            statusMessage = "Restored instance configuration from known-good backup ($dateStr)."
                        },
                        icon = Icons.Default.Restore,
                        variant = EzzButtonVariant.SECONDARY,
                        size = EzzButtonSize.SMALL
                    )
                }
            }
        }

        // Section 1: SYSTEM HARDWARE & ACTIVE RUNTIME CONTEXT
        SettingsCard(title = "HARDWARE CONTEXT & DETECTED SPECIFICATIONS") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InfoTile(
                        label = "PROCESSOR (CPU)",
                        value = hwProfile.cpuModel,
                        subtitle = "${hwProfile.cpuCores} Logical Threads / Cores",
                        badge = "${hwProfile.cpuCores} Threads",
                        modifier = Modifier.weight(1.2f)
                    )

                    InfoTile(
                        label = "PRIMARY GRAPHICS (GPU)",
                        value = hwProfile.primaryGpu,
                        subtitle = "Preference: ${gpuPreference.displayName}",
                        badge = if (hwProfile.hasDedicatedGpu) "Dedicated GPU" else "Integrated GPU",
                        modifier = Modifier.weight(1.2f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InfoTile(
                        label = "SYSTEM MEMORY",
                        value = "${hwProfile.totalRamMb / 1024} GB Total RAM",
                        subtitle = "Safe recommended cap: ${hwProfile.recommendedMaxRamMb / 1024} GB (Leaves 4 GB for OS)",
                        badge = "${hwProfile.availableRamMb / 1024} GB Free",
                        modifier = Modifier.weight(1f)
                    )

                    InfoTile(
                        label = "DISPLAY CONTEXT",
                        value = hwProfile.displayResolution,
                        subtitle = "${hwProfile.displayRefreshRateHz} Hz Refresh Rate",
                        badge = "${hwProfile.displayRefreshRateHz} Hz",
                        modifier = Modifier.weight(1f)
                    )

                    InfoTile(
                        label = "ACTIVE JAVA RUNTIME",
                        value = if (activeJava != null) "Java ${activeJava.majorVersion} (${if (activeJava.is64Bit) "64-Bit" else "32-Bit"})" else "Auto (Java 21)",
                        subtitle = activeJava?.vendor?.takeIf { it.isNotBlank() } ?: "Adoptium / Temurin",
                        badge = if (activeJava?.is64Bit == true) "64-Bit" else "32-Bit Warning",
                        isWarning = activeJava?.is64Bit == false,
                        modifier = Modifier.weight(1.2f)
                    )
                }
            }
        }

        // Section: LIVE PERFORMANCE DIAGNOSTICS & BOTTLENECK REPORT (if generated)
        diagnosticReport?.let { report ->
            SettingsCard(title = "LIVE PERFORMANCE & BOTTLENECK DIAGNOSTIC REPORT") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InfoTile(
                            label = "MINECRAFT ACTIVE GPU",
                            value = report.actualMinecraftGpu,
                            subtitle = "VRAM: ${report.vramUsedMb ?: 0} / ${report.vramTotalMb ?: 4096} MB | GPU Temp: ${report.gpuTempC ?: 0}°C",
                            badge = if (report.actualMinecraftGpu.contains("NVIDIA", ignoreCase = true)) "Dedicated GPU Active" else "Integrated GPU",
                            modifier = Modifier.weight(1.3f)
                        )

                        InfoTile(
                            label = "POWER & THERMAL CONTEXT",
                            value = report.powerSource,
                            subtitle = "Power Scheme: ${report.windowsPowerScheme}",
                            badge = if (report.powerSource.contains("Battery", ignoreCase = true)) "Battery Throttled" else "Max Performance AC",
                            isWarning = report.powerSource.contains("Battery", ignoreCase = true),
                            modifier = Modifier.weight(1.2f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InfoTile(
                            label = "FPS CAP SOURCE & VSYNC",
                            value = report.fpsCapSource,
                            subtitle = "VSync: ${if (report.vsync) "ON" else "OFF"} | Minecraft Max FPS: ${report.maxFps}",
                            badge = if (report.isFpsCapped) "Limiter Active" else "Uncapped FPS",
                            isWarning = report.isFpsCapped,
                            modifier = Modifier.weight(1.3f)
                        )

                        InfoTile(
                            label = "LIMITER ENGINE STATUS",
                            value = "Sodium: ${report.sodiumLimiterStatus}",
                            subtitle = "Iris: ${report.irisLimiterStatus} | NVIDIA: ${report.nvidiaLimiterStatus}",
                            badge = "Display: ${report.refreshRateHz} Hz",
                            modifier = Modifier.weight(1.2f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("DIAGNOSED BOTTLENECK:", color = Color(0xFF60A5FA), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(report.bottleneck.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("Root Cause: ${report.bottleneckExplanation}", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                            Text("Recommended Action: ${report.recommendedFix}", color = Color(0xFF34D399), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Section 2: PERFORMANCE PROFILE
        SettingsCard(title = "MINECRAFT PERFORMANCE PROFILE") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Select an automated runtime profile to optimize Minecraft video settings and frame-time delivery:",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.5.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PerformanceProfileCard(
                        title = "Default",
                        badge = "Native Settings",
                        description = "Leaves in-game options unmodified. Retains whatever settings are currently set inside Minecraft.",
                        selected = performanceProfile == PerformanceProfile.DEFAULT,
                        onClick = { performanceProfile = PerformanceProfile.DEFAULT },
                        modifier = Modifier.weight(1f)
                    )

                    PerformanceProfileCard(
                        title = "Balanced",
                        badge = "Fidelity + FPS",
                        description = "12 chunk render distance, fancy graphics, all particles, smooth lighting, Uncapped FPS. High visual fidelity.",
                        selected = performanceProfile == PerformanceProfile.BALANCED,
                        onClick = { performanceProfile = PerformanceProfile.BALANCED },
                        modifier = Modifier.weight(1f)
                    )

                    PerformanceProfileCard(
                        title = "Performance",
                        badge = "High Throughput",
                        description = "10 chunk render distance, fast graphics, decreased particles, min lighting, 75% entity distance, Uncapped FPS.",
                        selected = performanceProfile == PerformanceProfile.PERFORMANCE,
                        onClick = { performanceProfile = PerformanceProfile.PERFORMANCE },
                        modifier = Modifier.weight(1f)
                    )

                    PerformanceProfileCard(
                        title = "Extreme FPS",
                        badge = "500+ FPS Target",
                        description = "Maximum real-time FPS. 8 chunks, fast graphics, minimal particles, off clouds/fog/shadows, 50% entity scaling, zero biome blend, disabled VSync.",
                        selected = performanceProfile == PerformanceProfile.EXTREME_FPS,
                        onClick = { performanceProfile = PerformanceProfile.EXTREME_FPS },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (performanceProfile == PerformanceProfile.EXTREME_FPS) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E1B4B))
                            .border(1.dp, Color(0xFF6366F1), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(16.dp))
                                Text("EXTREME HIGH-FPS MODE ACTIVE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Text(
                                text = "Optimized for the highest real-time frame rates. Visual quality is adjusted (clouds/shadows disabled, fast leaves, minimal particles, 50% entity distance, multidraw Sodium meshing). Shaders should remain disabled for maximum FPS.",
                                color = Color(0xFFC7D2FE),
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // Section 2.5: FPS LIMITER & FRAMERATE MODE
        SettingsCard(title = "FPS LIMITER & FRAMERATE MODE") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Control Minecraft maximum framerate and vertical synchronization (VSync):",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.5.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PerformanceProfileCard(
                        title = "Unlimited",
                        badge = "Unconstrained FPS",
                        description = "Unlocks maximum real-time framerate (Max FPS = Unlimited, VSync = OFF). Recommended for testing peak FPS.",
                        selected = fpsMode == FpsMode.UNLIMITED,
                        onClick = { fpsMode = FpsMode.UNLIMITED },
                        modifier = Modifier.weight(1f)
                    )

                    PerformanceProfileCard(
                        title = "Display Limit",
                        badge = "${hwProfile.displayRefreshRateHz} Hz Sync",
                        description = "Caps framerate to your display's native refresh rate (${hwProfile.displayRefreshRateHz} FPS) without VSync input lag.",
                        selected = fpsMode == FpsMode.DISPLAY_LIMIT,
                        onClick = { fpsMode = FpsMode.DISPLAY_LIMIT },
                        modifier = Modifier.weight(1f)
                    )

                    PerformanceProfileCard(
                        title = "Custom Limit",
                        badge = "${customFpsLimit} FPS",
                        description = "Sets a custom numeric framerate ceiling to balance power consumption, thermals, and smoothness.",
                        selected = fpsMode == FpsMode.CUSTOM,
                        onClick = { fpsMode = FpsMode.CUSTOM },
                        modifier = Modifier.weight(1f)
                    )

                    PerformanceProfileCard(
                        title = "Default",
                        badge = "In-Game Options",
                        description = "Preserves existing in-game options without any launcher modifications.",
                        selected = fpsMode == FpsMode.DEFAULT,
                        onClick = { fpsMode = FpsMode.DEFAULT },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (fpsMode == FpsMode.CUSTOM) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Custom Target Framerate (FPS)", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EzzTextField(
                                value = customFpsLimit,
                                onValueChange = { customFpsLimit = it },
                                placeholder = "260 (Unlimited)",
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PresetChip("60 FPS", customFpsLimit == "60") { customFpsLimit = "60" }
                                PresetChip("120 FPS", customFpsLimit == "120") { customFpsLimit = "120" }
                                PresetChip("144 FPS", customFpsLimit == "144") { customFpsLimit = "144" }
                                PresetChip("240 FPS", customFpsLimit == "240") { customFpsLimit = "240" }
                                PresetChip("360 FPS", customFpsLimit == "360") { customFpsLimit = "360" }
                                PresetChip("500 FPS", customFpsLimit == "500") { customFpsLimit = "500" }
                            }
                        }
                    }
                }
            }
        }

        // Section 3: MEMORY ALLOCATION (RAM)
        SettingsCard(title = "MEMORY ALLOCATION (RAM)") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Allocated RAM (Maximum Heap)", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                            EzzBadge(text = "System: ${systemMemory.totalRamMb / 1024} GB")
                            EzzBadge(text = "Recommended: ${systemMemory.recommendedMaxMb / 1024} GB")
                        }
                        Text("Avoid excessive allocation to prevent garbage collection stutter. 4-6 GB is optimal for modern modpacks.", color = Color(0xFF64748B), fontSize = 11.5.sp)
                    }
                    Text(
                        text = "${maxMemoryMb / 1024} GB (${maxMemoryMb} MB)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp
                    )
                }

                Slider(
                    value = maxMemoryMb.toFloat(),
                    onValueChange = { maxMemoryMb = it.toInt() },
                    valueRange = 1024f..(systemMemory.totalRamMb.coerceAtLeast(16384).toFloat()),
                    steps = 15,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color(0xFF222735)
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MemoryPresetButton("2 GB", maxMemoryMb == 2048) { maxMemoryMb = 2048 }
                    MemoryPresetButton("3 GB", maxMemoryMb == 3072) { maxMemoryMb = 3072 }
                    MemoryPresetButton("4 GB (Vanilla / Light)", maxMemoryMb == 4096) { maxMemoryMb = 4096 }
                    MemoryPresetButton("6 GB (Modded)", maxMemoryMb == 6144) { maxMemoryMb = 6144 }
                    MemoryPresetButton("8 GB (Heavy Modpack)", maxMemoryMb == 8192) { maxMemoryMb = 8192 }
                    MemoryPresetButton("12 GB", maxMemoryMb == 12288) { maxMemoryMb = 12288 }
                }
            }
        }

        // Section 4: GPU & PROCESS PRIORITY
        SettingsCard(title = "GRAPHICS ADAPTER & PROCESS SCHEDULING") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // GPU Preference
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("GPU Preference", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PresetChip("Auto", gpuPreference == GpuPreference.AUTO) { gpuPreference = GpuPreference.AUTO }
                        PresetChip("High Performance (Dedicated)", gpuPreference == GpuPreference.HIGH_PERFORMANCE) { gpuPreference = GpuPreference.HIGH_PERFORMANCE }
                        PresetChip("Power Saving", gpuPreference == GpuPreference.POWER_SAVING) { gpuPreference = GpuPreference.POWER_SAVING }
                    }
                    Text("Enforces dedicated GPU binding on hybrid laptops and multi-GPU desktops.", color = Color(0xFF64748B), fontSize = 11.sp)
                }

                // Process Priority
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Process Scheduling Priority", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PresetChip("Normal (OS Default)", processPriority == ProcessPriority.NORMAL) { processPriority = ProcessPriority.NORMAL }
                        PresetChip("Above Normal (Fast Response)", processPriority == ProcessPriority.ABOVE_NORMAL) { processPriority = ProcessPriority.ABOVE_NORMAL }
                    }
                    Text("Above Normal gives Minecraft CPU priority over background desktop indexing.", color = Color(0xFF64748B), fontSize = 11.sp)
                }
            }
        }

        // Section 5: JAVA RUNTIME & GARBAGE COLLECTOR
        SettingsCard(title = "JAVA RUNTIME & GARBAGE COLLECTOR") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Detected runtimes selection
                if (detectedRuntimes.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val reqJava = JavaRuntimeDetector.getRequiredJavaMajorVersion(instance.minecraftVersion)
                        Text("Detected Java Runtimes (Minecraft ${instance.minecraftVersion} recommends Java $reqJava)", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PresetChip(
                                label = "Auto Detect (Java $reqJava Recommended)",
                                selected = customJavaPath.isBlank()
                            ) {
                                customJavaPath = ""
                            }

                            detectedRuntimes.forEach { runtime ->
                                val isSelected = customJavaPath.equals(runtime.path, ignoreCase = true)
                                val (isCompat, _) = JavaRuntimeDetector.checkRuntimeCompatibility(runtime, instance.minecraftVersion)
                                val tag = if (isCompat) "✓" else "⚠"
                                PresetChip(
                                    label = "$tag Java ${runtime.majorVersion} (${if (runtime.is64Bit) "64-Bit" else "32-Bit"}) — ${runtime.vendor}",
                                    selected = isSelected
                                ) {
                                    customJavaPath = runtime.path
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Custom Java Executable Path (Optional)", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    EzzTextField(
                        value = customJavaPath,
                        onValueChange = { customJavaPath = it },
                        placeholder = "Leave empty to use automatic 64-bit Java detection",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Garbage Collector
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Garbage Collector Algorithm", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PresetChip("Auto (G1GC Balanced)", gcType == GarbageCollectorType.AUTO) { gcType = GarbageCollectorType.AUTO }
                        PresetChip("G1GC (Low Latency)", gcType == GarbageCollectorType.G1GC) { gcType = GarbageCollectorType.G1GC }
                        PresetChip("ZGC (Ultra-Low Latency, Java 17+)", gcType == GarbageCollectorType.ZGC) { gcType = GarbageCollectorType.ZGC }
                        PresetChip("Shenandoah GC", gcType == GarbageCollectorType.SHENANDOAH) { gcType = GarbageCollectorType.SHENANDOAH }
                    }
                }

                // Custom JVM Args
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Custom JVM Arguments", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF141720))
                                    .border(1.dp, Color(0xFF222735), RoundedCornerShape(4.dp))
                                    .clickable {
                                        customJvmArgs = "-XX:+UseG1GC -XX:+UnlockExperimentalVMOptions -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=32M"
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Aikar G1GC Preset", color = Color(0xFFCBD5E1), fontSize = 10.5.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF141720))
                                    .border(1.dp, Color(0xFF222735), RoundedCornerShape(4.dp))
                                    .clickable { customJvmArgs = "" }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Clear", color = Color(0xFF94A3B8), fontSize = 10.5.sp)
                            }
                        }
                    }
                    EzzTextField(
                        value = customJvmArgs,
                        onValueChange = { customJvmArgs = it },
                        placeholder = "-XX:+UseG1GC -XX:+UnlockExperimentalVMOptions",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Diagnostics Checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.clickable { enableDiagnostics = !enableDiagnostics }
                ) {
                    Checkbox(
                        checked = enableDiagnostics,
                        onCheckedChange = { enableDiagnostics = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color.White,
                            checkmarkColor = Color.Black,
                            uncheckedColor = Color(0xFF64748B)
                        )
                    )
                    Column {
                        Text("Enable Detailed Launch Diagnostics", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Prints complete JVM arguments, memory map, CPU context, and GPU profile to launcher console logs.", color = Color(0xFF64748B), fontSize = 11.sp)
                    }
                }
            }
        }

        // Section 5.5: EZZ SKIN SYSTEM (ON / OFF TOGGLE)
        SettingsCard(title = "EZZ SKIN") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ezz Skin Mod",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Launcher-integrated skin system. Use Ezz Skin features in this instance.",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (ezzSkinEnabled) "Status: ● Enabled" else "Status: ○ Disabled",
                            color = if (ezzSkinEnabled) Color(0xFF10B981) else Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        EzzButton(
                            text = if (ezzSkinEnabled) "ON" else "OFF",
                            onClick = {
                                val nextState = !ezzSkinEnabled
                                ezzSkinEnabled = nextState
                                val updated = instance.copy(ezzSkinEnabled = nextState)
                                viewModel.updateInstance(updated)
                                statusMessage = if (nextState) "Ezz Skin enabled for instance '${instance.name}'." else "Ezz Skin disabled for instance '${instance.name}' (JAR preserved)."
                            },
                            variant = if (ezzSkinEnabled) EzzButtonVariant.PRIMARY else EzzButtonVariant.SECONDARY,
                            size = EzzButtonSize.SMALL
                        )
                    }
                }

                if (!ezzSkinEnabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF141720))
                            .border(1.dp, Color(0xFF222735), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Ezz Skin features are disabled for this instance. The Ezz Skin Mod is still installed and can be enabled again at any time.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Section 6: PERFORMANCE MOD ADVISOR (For Fabric Instances)
        if (instance.loaderType == LoaderType.FABRIC) {
            SettingsCard(title = "PERFORMANCE MOD ADVISOR (FABRIC)") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Detected optimization mods and recommended additions for your Fabric instance:",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        modRecommendations.forEach { rec ->
                            ModAdvisorBadge(recommendation = rec)
                        }
                    }
                }
            }
        }

        // Section 7: INSTANCE IDENTITY & ARTWORK
        SettingsCard(title = "INSTANCE IDENTITY & ARTWORK") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Instance Name", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    EzzTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Instance Icon / Artwork", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        InstanceArtworkIcon(
                            instance = instance,
                            size = 72.dp
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                EzzButton(
                                    text = "Upload Custom Icon",
                                    onClick = {
                                        val picked = viewModel.platformBridge.pickImageFile("Select Instance Icon (PNG, JPG, WEBP)")
                                        if (picked != null && picked.exists()) {
                                            viewModel.changeInstanceCustomIcon(instance.id, picked)
                                        }
                                    },
                                    variant = EzzButtonVariant.SECONDARY,
                                    size = EzzButtonSize.SMALL
                                )

                                if (!instance.customIconPath.isNullOrBlank()) {
                                    EzzButton(
                                        text = "Reset to Default",
                                        onClick = { viewModel.removeInstanceCustomIcon(instance.id) },
                                        variant = EzzButtonVariant.DANGER,
                                        size = EzzButtonSize.SMALL
                                    )
                                }
                            }

                            Text(
                                text = "Supported formats: PNG, JPG, JPEG, WEBP. Icon persists offline.",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // Section 8: GAME WINDOW RESOLUTION
        SettingsCard(title = "GAME WINDOW RESOLUTION") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Window Width (px)", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        EzzTextField(
                            value = windowWidth,
                            onValueChange = { windowWidth = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Window Height (px)", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        EzzTextField(
                            value = windowHeight,
                            onValueChange = { windowHeight = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResolutionPresetButton("854x480 (480p)", windowWidth == "854" && windowHeight == "480") {
                        windowWidth = "854"
                        windowHeight = "480"
                    }
                    ResolutionPresetButton("1280x720 (720p)", windowWidth == "1280" && windowHeight == "720") {
                        windowWidth = "1280"
                        windowHeight = "720"
                    }
                    ResolutionPresetButton("1920x1080 (1080p)", windowWidth == "1920" && windowHeight == "1080") {
                        windowWidth = "1920"
                        windowHeight = "1080"
                    }
                    ResolutionPresetButton("2560x1440 (2K)", windowWidth == "2560" && windowHeight == "1440") {
                        windowWidth = "2560"
                        windowHeight = "1440"
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoTile(
    label: String,
    value: String,
    subtitle: String,
    badge: String,
    isWarning: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0C0E14))
            .border(1.dp, if (isWarning) Color(0xFFEF4444) else Color(0xFF1E2330), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, color = Color(0xFF64748B), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                EzzBadge(text = badge)
            }
            Text(value, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color(0xFF94A3B8), fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun PerformanceProfileCard(
    title: String,
    badge: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color(0xFF1A1E29) else if (isHovered) Color(0xFF141720) else Color(0xFF0C0E14))
            .border(
                1.dp,
                if (selected) Color.White else if (isHovered) Color(0xFF3B465E) else Color(0xFF1E2330),
                RoundedCornerShape(8.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = if (selected) Color.White else Color(0xFFCBD5E1), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (selected) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                } else {
                    EzzBadge(text = badge)
                }
            }
            Text(description, color = Color(0xFF94A3B8), fontSize = 11.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun ModAdvisorBadge(recommendation: io.ezz.launcher.core.minecraft.mods.PerformanceModRecommendation) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (recommendation.isInstalled) Color(0xFF064E3B).copy(alpha = 0.25f) else Color(0xFF141720))
            .border(
                1.dp,
                if (recommendation.isInstalled) Color(0xFF10B981) else Color(0xFF222735),
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (recommendation.isInstalled) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(13.dp))
            }
            Column {
                Text(
                    text = "${recommendation.name} — ${if (recommendation.isInstalled) "Installed" else "Recommended"}",
                    color = if (recommendation.isInstalled) Color(0xFF10B981) else Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${recommendation.category} (${recommendation.impact})",
                    color = Color(0xFF64748B),
                    fontSize = 10.5.sp
                )
            }
        }
    }
}

@Composable
private fun PresetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Color(0xFF1A1E29) else if (isHovered) Color(0xFF181C28) else Color(0xFF141720))
            .border(
                1.dp,
                if (selected) Color.White else if (isHovered) Color(0xFF323A4E) else Color(0xFF222735),
                RoundedCornerShape(6.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else if (isHovered) Color.White else Color(0xFF94A3B8),
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF101318))
            .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(10.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = title,
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
            content()
        }
    }
}

@Composable
private fun MemoryPresetButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Color(0xFF1A1E29) else if (isHovered) Color(0xFF181C28) else Color(0xFF141720))
            .border(
                1.dp,
                if (selected) Color.White else if (isHovered) Color(0xFF323A4E) else Color(0xFF222735),
                RoundedCornerShape(6.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else if (isHovered) Color.White else Color(0xFF94A3B8),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun ResolutionPresetButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Color(0xFF1A1E29) else if (isHovered) Color(0xFF181C28) else Color(0xFF141720))
            .border(
                1.dp,
                if (selected) Color.White else if (isHovered) Color(0xFF323A4E) else Color(0xFF222735),
                RoundedCornerShape(6.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else if (isHovered) Color.White else Color(0xFF94A3B8),
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
