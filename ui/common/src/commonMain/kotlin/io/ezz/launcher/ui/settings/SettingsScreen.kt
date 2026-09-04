package io.ezz.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.auth.admin.AdminStatus
import io.ezz.launcher.core.model.account.MicrosoftAccount
import io.ezz.launcher.core.model.runtime.JavaRuntime
import io.ezz.launcher.core.storage.github.GitHubConnectionStatus
import io.ezz.launcher.core.storage.supabase.SupabaseLauncherReleaseDto
import io.ezz.launcher.ui.components.*
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.AppViewModel
import io.ezz.launcher.ui.viewmodel.JavaValidationResult
import io.ezz.launcher.ui.viewmodel.ReleasePublishStep
import java.io.File

/**
 * The 5 strictly scoped Settings sections of Ezz Launcher:
 * A. Java & Memory Allocation
 * B. Minecraft Window Defaults
 * C. Discord Rich Presence
 * D. Updates & Version
 * E. EZZ Launcher Admin Identity
 */
enum class SettingsSection(val title: String, val icon: ImageVector, val subtitle: String) {
    ALL("All Settings", Icons.Default.Tune, "Complete preferences overview"),
    JAVA_MEMORY("Java & Memory", Icons.Default.Memory, "JVM runtimes & RAM limits"),
    WINDOW_DEFAULTS("Window Defaults", Icons.Default.SportsEsports, "Resolution & display mode"),
    DISCORD_RPC("Discord Presence", Icons.Default.Chat, "Rich Presence activity status"),
    UPDATES("Updates & Version", Icons.Default.Refresh, "Release discovery & updater"),
    ADMIN_IDENTITY("Admin Identity", Icons.Default.Security, "Verified admin release system")
}

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    var activeSection by remember { mutableStateOf(SettingsSection.ALL) }
    val adminStatus by viewModel.adminStatus.collectAsState()

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07080A))
    ) {
        // Navigation Sidebar
        Column(
            modifier = Modifier
                .width(240.dp)
                .fillMaxHeight()
                .background(Color(0xFF0D0F14))
                .border(androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF191D26)))
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Text(
                text = "SETTINGS",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "Launcher & System Configuration",
                color = Color(0xFF64748B),
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            SettingsSection.entries.forEach { section ->
                val isSelected = activeSection == section
                val interactionSource = remember { MutableInteractionSource() }
                val isHovered by interactionSource.collectIsHoveredAsState()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                isSelected -> Color(0xFF1E2433)
                                isHovered -> Color(0xFF141822)
                                else -> Color.Transparent
                            }
                        )
                        .then(
                            if (isSelected) Modifier.border(1.dp, Color(0xFF38435C), RoundedCornerShape(8.dp))
                            else Modifier
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { activeSection = section }
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = section.icon,
                        contentDescription = null,
                        tint = when {
                            isSelected -> Color(0xFF8B5CF6)
                            isHovered -> Color.White
                            else -> Color(0xFF94A3B8)
                        },
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = section.title,
                            color = if (isSelected) Color.White else if (isHovered) Color.White else Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                        if (section == SettingsSection.ADMIN_IDENTITY && adminStatus is AdminStatus.VerifiedAdmin) {
                            Text(
                                text = "ADMIN VERIFIED",
                                color = Color(0xFF10B981),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            // Version Indicator footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF11141C))
                    .border(1.dp, Color(0xFF1A1F2B), RoundedCornerShape(6.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Ezz Launcher",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "v${viewModel.currentLauncherVersion} (Windows x64)",
                        color = Color(0xFF64748B),
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Settings Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 32.dp, vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Section Header
                Column(modifier = Modifier.padding(bottom = 6.dp)) {
                    Text(
                        text = when (activeSection) {
                            SettingsSection.ALL -> "PREFERENCES & SYSTEM"
                            SettingsSection.JAVA_MEMORY -> "JAVA & MEMORY ALLOCATION"
                            SettingsSection.WINDOW_DEFAULTS -> "MINECRAFT WINDOW DEFAULTS"
                            SettingsSection.DISCORD_RPC -> "DISCORD RICH PRESENCE"
                            SettingsSection.UPDATES -> "UPDATES & VERSION"
                            SettingsSection.ADMIN_IDENTITY -> "EZZ LAUNCHER ADMIN IDENTITY"
                        },
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = when (activeSection) {
                            SettingsSection.ALL -> "Manage Java runtimes, RAM limits, display defaults, and release distribution"
                            SettingsSection.JAVA_MEMORY -> "Configure runtime detection, memory allocation, and global JVM flags"
                            SettingsSection.WINDOW_DEFAULTS -> "Resolution and display settings applied when launching Minecraft"
                            SettingsSection.DISCORD_RPC -> "Discord status broadcast for game activity and playtime tracking"
                            SettingsSection.UPDATES -> "Release discovery from centralized Supabase metadata and GitHub distribution"
                            SettingsSection.ADMIN_IDENTITY -> "Cryptographically verified admin status and secure release manager"
                        },
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )
                }

                // Section A: Java & Memory Allocation
                if (activeSection == SettingsSection.ALL || activeSection == SettingsSection.JAVA_MEMORY) {
                    JavaAndMemoryCard(viewModel)
                }

                // Section B: Minecraft Window Defaults
                if (activeSection == SettingsSection.ALL || activeSection == SettingsSection.WINDOW_DEFAULTS) {
                    MinecraftWindowDefaultsCard(viewModel)
                }

                // Section C: Discord Rich Presence
                if (activeSection == SettingsSection.ALL || activeSection == SettingsSection.DISCORD_RPC) {
                    DiscordRichPresenceCard(viewModel)
                }

                // Section D: Updates & Version
                if (activeSection == SettingsSection.ALL || activeSection == SettingsSection.UPDATES) {
                    UpdatesAndVersionCard(viewModel)
                }

                // Section E: EZZ Launcher Admin Identity & Admin Release Manager
                if (activeSection == SettingsSection.ALL || activeSection == SettingsSection.ADMIN_IDENTITY) {
                    AdminIdentityAndReleaseCard(viewModel)
                }

                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

/**
 * Section A: Java & Memory Allocation Card
 */
@Composable
private fun JavaAndMemoryCard(viewModel: AppViewModel) {
    val settings by viewModel.settingsRepository.settings.collectAsState()
    val detectedRuntimes by viewModel.detectedJavaRuntimes.collectAsState()
    val isDetecting by viewModel.isDetectingJava.collectAsState()
    val memoryInfo by viewModel.systemMemoryInfo.collectAsState()

    var customJavaPathInput by remember(settings.defaultJavaPath) { mutableStateOf(settings.defaultJavaPath ?: "") }
    var jvmArgsInput by remember(settings.globalJvmArgs) { mutableStateOf(settings.globalJvmArgs.joinToString(" ")) }

    val javaPathValidation = remember(customJavaPathInput) {
        viewModel.validateCustomJavaPath(customJavaPathInput)
    }

    // Real Java Detection Evaluation
    val hasJava8 = detectedRuntimes.any { it.majorVersion == 8 }
    val hasJava17 = detectedRuntimes.any { it.majorVersion == 17 }
    val hasJava21 = detectedRuntimes.any { it.majorVersion == 21 }
    val hasJava25 = detectedRuntimes.any { it.majorVersion == 25 }

    val runtime8 = detectedRuntimes.firstOrNull { it.majorVersion == 8 }
    val runtime17 = detectedRuntimes.firstOrNull { it.majorVersion == 17 }
    val runtime21 = detectedRuntimes.firstOrNull { it.majorVersion == 21 }
    val runtime25 = detectedRuntimes.firstOrNull { it.majorVersion == 25 }

    val totalRamMb = memoryInfo.totalRamMb.coerceAtLeast(4096)
    val ramDisplayGb = String.format("%.1f", settings.defaultMaxMemoryMb / 1024.0)

    EzzCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 14.dp,
        backgroundColor = Color(0xFF101318),
        borderColor = Color(0xFF1E222D)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Card Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A1F2C)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Java & Memory Allocation",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Manage JVM runtime environments, default RAM limits, and global launch flags",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EzzButton(
                        text = if (isDetecting) "Detecting..." else "Auto-Detect",
                        size = EzzButtonSize.SMALL,
                        variant = EzzButtonVariant.SECONDARY,
                        icon = Icons.Default.Search,
                        isLoading = isDetecting,
                        onClick = { viewModel.refreshJavaRuntimes() }
                    )
                    EzzButton(
                        text = "Manage Runtimes",
                        size = EzzButtonSize.SMALL,
                        variant = EzzButtonVariant.SECONDARY,
                        icon = Icons.Default.OpenInNew,
                        onClick = { viewModel.platformBridge.openUrl("https://adoptium.net/temurin/releases/") }
                    )
                }
            }

            // Java Runtimes Status Grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "JAVA RUNTIME STATUS",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    JavaStatusPill(
                        major = 8,
                        title = "Java 8",
                        target = "MC <= 1.16.5",
                        isInstalled = hasJava8,
                        runtime = runtime8,
                        modifier = Modifier.weight(1f)
                    )
                    JavaStatusPill(
                        major = 17,
                        title = "Java 17",
                        target = "MC 1.17 - 1.20.4",
                        isInstalled = hasJava17,
                        runtime = runtime17,
                        modifier = Modifier.weight(1f)
                    )
                    JavaStatusPill(
                        major = 21,
                        title = "Java 21",
                        target = "MC 1.20.5+",
                        isInstalled = hasJava21,
                        runtime = runtime21,
                        modifier = Modifier.weight(1f)
                    )
                    JavaStatusPill(
                        major = 25,
                        title = "Java 25",
                        target = "Experimental",
                        isInstalled = hasJava25,
                        runtime = runtime25,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Divider(color = Color(0xFF1E222D), thickness = 1.dp)

            // RAM Allocation Slider
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Default Maximum RAM Allocation",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Maximum system memory available for instances ($totalRamMb MB total installed)",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }
                    EzzBadge(
                        text = "${settings.defaultMaxMemoryMb} MB ($ramDisplayGb GB)",
                        variant = EzzBadgeVariant.PRIMARY
                    )
                }

                EzzSlider(
                    value = settings.defaultMaxMemoryMb.toFloat().coerceIn(1024f, totalRamMb.toFloat()),
                    onValueChange = { newMb ->
                        val roundedMb = (newMb / 512).toInt() * 512
                        viewModel.updateMemorySettings(settings.defaultMinMemoryMb, roundedMb)
                    },
                    valueRange = 1024f..totalRamMb.toFloat(),
                    steps = ((totalRamMb - 1024) / 512).coerceAtLeast(1)
                )
            }

            Divider(color = Color(0xFF1E222D), thickness = 1.dp)

            // Custom Java Executable Path
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Custom Java Executable Path",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Leave empty to use automatic system discovery or bundled JVM",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }

                    // Validation state badge
                    when (javaPathValidation) {
                        JavaValidationResult.Empty -> {
                            EzzBadge(text = "Default Auto-Discovery", variant = EzzBadgeVariant.NEUTRAL)
                        }
                        JavaValidationResult.Valid -> {
                            EzzBadge(text = "Valid Executable", variant = EzzBadgeVariant.SUCCESS)
                        }
                        JavaValidationResult.NotFound -> {
                            EzzBadge(text = "File Not Found", variant = EzzBadgeVariant.WARNING)
                        }
                        JavaValidationResult.IsDirectory -> {
                            EzzBadge(text = "Path Is Directory", variant = EzzBadgeVariant.WARNING)
                        }
                        JavaValidationResult.NotJavaExecutable -> {
                            EzzBadge(text = "Invalid Java Binary", variant = EzzBadgeVariant.DANGER)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EzzTextField(
                        value = customJavaPathInput,
                        onValueChange = {
                            customJavaPathInput = it
                            viewModel.updateCustomJavaPath(it)
                        },
                        placeholder = "e.g. C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.2\\bin\\javaw.exe",
                        modifier = Modifier.weight(1f)
                    )

                    EzzButton(
                        text = "Browse",
                        icon = Icons.Default.FolderOpen,
                        size = EzzButtonSize.MEDIUM,
                        variant = EzzButtonVariant.SECONDARY,
                        onClick = {
                            val picked = viewModel.platformBridge.pickJavaExecutable()
                            if (picked != null) {
                                customJavaPathInput = picked.absolutePath
                                viewModel.updateCustomJavaPath(picked.absolutePath)
                            }
                        }
                    )

                    if (customJavaPathInput.isNotBlank()) {
                        EzzButton(
                            text = "Clear",
                            size = EzzButtonSize.MEDIUM,
                            variant = EzzButtonVariant.SECONDARY,
                            onClick = {
                                customJavaPathInput = ""
                                viewModel.updateCustomJavaPath("")
                            }
                        )
                    }
                }
            }

            Divider(color = Color(0xFF1E222D), thickness = 1.dp)

            // Custom JVM Launch Arguments
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Custom JVM Launch Arguments",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Additional JVM flags passed to every Minecraft process. User arguments are preserved.",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp
                )

                EzzTextField(
                    value = jvmArgsInput,
                    onValueChange = {
                        jvmArgsInput = it
                        val tokens = it.split(" ").map { t -> t.trim() }.filter { t -> t.isNotEmpty() }
                        viewModel.updateGlobalJvmArgs(tokens)
                    },
                    placeholder = "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Java status pill component with clean visual indication
 */
@Composable
private fun JavaStatusPill(
    major: Int,
    title: String,
    target: String,
    isInstalled: Boolean,
    runtime: JavaRuntime?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isInstalled) Color(0xFF0F1B17) else Color(0xFF191712))
            .border(
                1.dp,
                if (isInstalled) Color(0xFF1B3D2B) else Color(0xFF332717),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                if (isInstalled) {
                    EzzBadge(
                        text = "Installed",
                        variant = EzzBadgeVariant.SUCCESS
                    )
                } else {
                    EzzBadge(
                        text = "Missing",
                        variant = EzzBadgeVariant.WARNING
                    )
                }
            }

            Text(
                text = target,
                color = Color(0xFF64748B),
                fontSize = 10.sp
            )

            if (isInstalled && runtime != null) {
                Text(
                    text = runtime.vendor.take(18),
                    color = Color(0xFF10B981),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "Not detected",
                    color = Color(0xFFF59E0B).copy(alpha = 0.8f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * Section B: Minecraft Window Defaults Card
 */
@Composable
private fun MinecraftWindowDefaultsCard(viewModel: AppViewModel) {
    val settings by viewModel.settingsRepository.settings.collectAsState()

    var widthInput by remember(settings.defaultWindowWidth) { mutableStateOf(settings.defaultWindowWidth.toString()) }
    var heightInput by remember(settings.defaultWindowHeight) { mutableStateOf(settings.defaultWindowHeight.toString()) }

    val resolutionPresets = listOf(
        Pair(854, 480) to "854 × 480 (Default)",
        Pair(1280, 720) to "1280 × 720 (HD)",
        Pair(1920, 1080) to "1920 × 1080 (FHD)",
        Pair(2560, 1440) to "2560 × 1440 (2K)"
    )

    EzzCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 14.dp,
        backgroundColor = Color(0xFF101318),
        borderColor = Color(0xFF1E222D)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Card Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A1F2C)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Minecraft Window Defaults",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Resolution and display mode applied whenever Minecraft instances launch",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
            }

            // Resolution Inputs Grouped
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "DEFAULT RESOLUTION (PX)",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Width", color = Color(0xFF64748B), fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        EzzTextField(
                            value = widthInput,
                            onValueChange = { newVal ->
                                val filtered = newVal.filter { it.isDigit() }
                                widthInput = filtered
                                val num = filtered.toIntOrNull()
                                if (num != null && num in 320..7680) {
                                    viewModel.updateWindowDefaults(num, settings.defaultWindowHeight, settings.defaultFullscreen)
                                }
                            },
                            placeholder = "854",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Text(
                        text = "×",
                        color = Color(0xFF64748B),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Height", color = Color(0xFF64748B), fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        EzzTextField(
                            value = heightInput,
                            onValueChange = { newVal ->
                                val filtered = newVal.filter { it.isDigit() }
                                heightInput = filtered
                                val num = filtered.toIntOrNull()
                                if (num != null && num in 240..4320) {
                                    viewModel.updateWindowDefaults(settings.defaultWindowWidth, num, settings.defaultFullscreen)
                                }
                            },
                            placeholder = "480",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Preset Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    resolutionPresets.forEach { (res, label) ->
                        val isSelected = settings.defaultWindowWidth == res.first && settings.defaultWindowHeight == res.second
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) Color(0xFF1E2433) else Color(0xFF141720))
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFF8B5CF6) else Color(0xFF222735),
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    widthInput = res.first.toString()
                                    heightInput = res.second.toString()
                                    viewModel.updateWindowDefaults(res.first, res.second, settings.defaultFullscreen)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Divider(color = Color(0xFF1E222D), thickness = 1.dp)

            // Fullscreen Toggle
            EzzToggle(
                checked = settings.defaultFullscreen,
                onCheckedChange = { checked ->
                    viewModel.updateWindowDefaults(settings.defaultWindowWidth, settings.defaultWindowHeight, checked)
                },
                label = "Launch Minecraft in Fullscreen",
                description = "Always start game instances in borderless or exclusive fullscreen display mode"
            )
        }
    }
}

/**
 * Section C: Discord Rich Presence Card
 */
@Composable
private fun DiscordRichPresenceCard(viewModel: AppViewModel) {
    val settings by viewModel.settingsRepository.settings.collectAsState()

    EzzCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 14.dp,
        backgroundColor = Color(0xFF101318),
        borderColor = Color(0xFF1E222D)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A1F2C)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = null,
                        tint = Color(0xFF5865F2),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Discord Rich Presence",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Real-time game activity broadcast across local Discord IPC",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
                EzzBadge(
                    text = if (settings.enableDiscordRpc) "ACTIVE" else "DISABLED",
                    variant = if (settings.enableDiscordRpc) EzzBadgeVariant.SUCCESS else EzzBadgeVariant.NEUTRAL
                )
            }

            // Exact Prompt Requirement Toggle
            EzzToggle(
                checked = settings.enableDiscordRpc,
                onCheckedChange = { viewModel.updateDiscordRpc(it) },
                label = "Enable Discord Rich Presence",
                description = "Show active instance name, Minecraft version, and session playtime in your Discord status."
            )

            // Preview Info Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF141722))
                    .border(1.dp, Color(0xFF22293A), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF5865F2),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (settings.enableDiscordRpc)
                            "Discord status is currently broadcasting automatically when you launch Minecraft."
                        else
                            "Discord status is turned off. Your Minecraft gameplay will not be displayed on your profile.",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/**
 * Section D: Updates & Version Card
 */
@Composable
private fun UpdatesAndVersionCard(viewModel: AppViewModel) {
    val updateResult by viewModel.updateCheckResult.collectAsState()
    val isChecking by viewModel.isCheckingForUpdates.collectAsState()
    val checkError by viewModel.updateCheckError.collectAsState()

    var showChangelogDialog by remember { mutableStateOf<SupabaseLauncherReleaseDto?>(null) }

    val currentVer = viewModel.currentLauncherVersion
    val latestRelease = updateResult?.latestRelease
    val latestVer = latestRelease?.version ?: currentVer
    val hasUpdate = updateResult?.hasUpdate == true

    EzzCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 14.dp,
        backgroundColor = Color(0xFF101318),
        borderColor = Color(0xFF1E222D)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A1F2C)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Updates & Version",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Check Supabase centralized release metadata against your installed build",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
                EzzButton(
                    text = if (isChecking) "Checking..." else "Check for Updates",
                    size = EzzButtonSize.SMALL,
                    variant = EzzButtonVariant.PRIMARY,
                    icon = Icons.Default.Refresh,
                    isLoading = isChecking,
                    onClick = { viewModel.checkForUpdates() }
                )
            }

            // Version Comparison Dashboard
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Installed Version
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF141720))
                        .border(1.dp, Color(0xFF222735), RoundedCornerShape(8.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "CURRENT VERSION", color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(text = "v$currentVer", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        Text(text = "Installed on this device", color = Color(0xFF94A3B8), fontSize = 10.sp)
                    }
                }

                // Latest Remote Version
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF141720))
                        .border(1.dp, Color(0xFF222735), RoundedCornerShape(8.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "LATEST VERSION", color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(text = "v$latestVer", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        Text(
                            text = if (hasUpdate) "New build available" else "Up to date",
                            color = if (hasUpdate) Color(0xFFF59E0B) else Color(0xFF10B981),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Update Status Banner
            when {
                isChecking -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF141720))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color(0xFF8B5CF6),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Checking for new releases via Supabase...",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                checkError != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2A1414))
                            .border(1.dp, Color(0xFF4A1F1F), RoundedCornerShape(8.dp))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(text = "Could not check for updates.", color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(text = "Try again later.", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }
                        }
                    }
                }
                hasUpdate && latestRelease != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E162B))
                            .border(1.dp, Color(0xFF472A6B), RoundedCornerShape(8.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF8B5CF6)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Update Available: Version ${latestRelease.version}",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "A newer official build is ready for installation.",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                EzzButton(
                                    text = "View Release Notes",
                                    size = EzzButtonSize.SMALL,
                                    variant = EzzButtonVariant.SECONDARY,
                                    onClick = { showChangelogDialog = latestRelease }
                                )
                                val downloadUrl = latestRelease.downloadUrl
                                if (downloadUrl != null) {
                                    EzzButton(
                                        text = "Download Update",
                                        size = EzzButtonSize.SMALL,
                                        variant = EzzButtonVariant.PRIMARY,
                                        icon = Icons.Default.Download,
                                        onClick = { viewModel.platformBridge.openUrl(downloadUrl) }
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F1B17))
                            .border(1.dp, Color(0xFF1B3D2B), RoundedCornerShape(8.dp))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(text = "You're up to date.", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(text = "Ezz Launcher v$currentVer is the latest approved release.", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Release Notes Modal Dialog
    if (showChangelogDialog != null) {
        val rel = showChangelogDialog!!
        AlertDialog(
            onDismissRequest = { showChangelogDialog = null },
            confirmButton = {
                EzzButton(
                    text = "Close",
                    size = EzzButtonSize.SMALL,
                    variant = EzzButtonVariant.SECONDARY,
                    onClick = { showChangelogDialog = null }
                )
            },
            title = {
                Text(
                    text = "Release Notes — v${rel.version}",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = rel.releaseNotes ?: "No detailed changelog provided for this release.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp
                    )
                }
            },
            containerColor = Color(0xFF101318),
            textContentColor = Color.White
        )
    }
}

/**
 * Section E: EZZ Launcher Admin Identity & Admin Release Manager
 */
@Composable
private fun AdminIdentityAndReleaseCard(viewModel: AppViewModel) {
    val selectedAccount by viewModel.accountRepository.selectedAccount.collectAsState()
    val adminStatus by viewModel.adminStatus.collectAsState()
    val isCheckingAdmin by viewModel.isCheckingAdmin.collectAsState()
    val githubStatus by viewModel.githubConnectionStatus.collectAsState()
    val releasePublishStep by viewModel.releasePublishStep.collectAsState()

    var showGitHubTokenDialog by remember { mutableStateOf(false) }
    var showPublishConfirmDialog by remember { mutableStateOf(false) }

    // Release Form State (Admin Only)
    var releaseVersionInput by remember { mutableStateOf("") }
    var releaseTitleInput by remember { mutableStateOf("") }
    var releaseNotesInput by remember { mutableStateOf("") }
    var selectedArtifactFile by remember { mutableStateOf<File?>(null) }
    var isDraftRelease by remember { mutableStateOf(false) }

    val isVerifiedAdmin = adminStatus is AdminStatus.VerifiedAdmin
    val isMicrosoft = selectedAccount is MicrosoftAccount

    EzzCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 14.dp,
        backgroundColor = Color(0xFF101318),
        borderColor = if (isVerifiedAdmin) Color(0xFF2A3D2F) else Color(0xFF1E222D)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Section Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isVerifiedAdmin) Color(0xFF10281D) else Color(0xFF1A1F2C)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isVerifiedAdmin) Icons.Default.VerifiedUser else Icons.Default.Security,
                        contentDescription = null,
                        tint = if (isVerifiedAdmin) Color(0xFF10B981) else Color(0xFF8B5CF6),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "EZZ Launcher Admin Identity",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Cryptographically authorized administrative identity verification",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }

                // Verified Status Badge
                if (isCheckingAdmin) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color(0xFF8B5CF6),
                        strokeWidth = 2.dp
                    )
                } else if (isVerifiedAdmin) {
                    EzzBadge(
                        text = "ADMIN VERIFIED",
                        variant = EzzBadgeVariant.SUCCESS
                    )
                } else {
                    EzzBadge(
                        text = "NORMAL USER",
                        variant = EzzBadgeVariant.NEUTRAL
                    )
                }
            }

            // Identity Breakdown Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Microsoft Account Identity Box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF141720))
                        .border(1.dp, Color(0xFF222735), RoundedCornerShape(8.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = "MICROSOFT ACCOUNT", color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = selectedAccount?.username ?: "No Active Account",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isMicrosoft) Color(0xFF10B981) else Color(0xFFF59E0B))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isMicrosoft) "Microsoft • CONNECTED" else "Offline • NOT CONNECTED",
                                color = if (isMicrosoft) Color(0xFF10B981) else Color(0xFFF59E0B),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // GitHub Authorization Box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF141720))
                        .border(1.dp, Color(0xFF222735), RoundedCornerShape(8.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = "GITHUB RELEASE PIPELINE", color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold)

                        when (val gh = githubStatus) {
                            is GitHubConnectionStatus.Connected -> {
                                Text(
                                    text = gh.username,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF10B981))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "CONNECTED • AUTHORIZED",
                                        color = Color(0xFF10B981),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isVerifiedAdmin) {
                                        Text(
                                            text = "Disconnect",
                                            color = Color(0xFFEF4444),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.clickable { viewModel.disconnectGitHub() }
                                        )
                                    }
                                }
                            }
                            else -> {
                                Text(
                                    text = "Not Connected",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isVerifiedAdmin) "Ready to connect" else "Not authorized",
                                        color = Color(0xFF64748B),
                                        fontSize = 11.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isVerifiedAdmin) {
                                        EzzButton(
                                            text = "Connect GitHub",
                                            size = EzzButtonSize.SMALL,
                                            variant = EzzButtonVariant.SECONDARY,
                                            onClick = { showGitHubTokenDialog = true }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // ADMIN RELEASE MANAGER (ONLY FOR ADMIN!)
            // ==========================================
            if (isVerifiedAdmin) {
                Divider(color = Color(0xFF1E222D), thickness = 1.dp)

                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Publish, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ADMIN RELEASE MANAGER",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        EzzBadge(text = "Current: v${viewModel.currentLauncherVersion}", variant = EzzBadgeVariant.PRIMARY)
                    }

                    // Release Publishing Form
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "New Version (e.g. 1.0.1)", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            EzzTextField(
                                value = releaseVersionInput,
                                onValueChange = { releaseVersionInput = it },
                                placeholder = "1.0.1",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Column(modifier = Modifier.weight(2f)) {
                            Text(text = "Release Title", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            EzzTextField(
                                value = releaseTitleInput,
                                onValueChange = { releaseTitleInput = it },
                                placeholder = "Ezz Launcher v1.0.1 - Security & Performance Update",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Column {
                        Text(text = "Release Notes / Changelog", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        EzzTextField(
                            value = releaseNotesInput,
                            onValueChange = { releaseNotesInput = it },
                            placeholder = "Describe features, bug fixes, and upgrade instructions...",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Artifact Picker
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Build / Artifact Binary", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF141720))
                                    .border(1.dp, Color(0xFF222735), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    text = selectedArtifactFile?.let { "${it.name} (${String.format("%.1f", it.length() / (1024.0 * 1024.0))} MB)" }
                                        ?: "No binary artifact selected (optional)",
                                    color = if (selectedArtifactFile != null) Color.White else Color(0xFF64748B),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        EzzButton(
                            text = "Select Build",
                            icon = Icons.Default.FolderOpen,
                            size = EzzButtonSize.MEDIUM,
                            variant = EzzButtonVariant.SECONDARY,
                            modifier = Modifier.padding(top = 18.dp),
                            onClick = {
                                val picked = viewModel.platformBridge.pickReleaseArtifact()
                                if (picked != null) {
                                    selectedArtifactFile = picked
                                }
                            }
                        )
                    }

                    // Options & Publish Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EzzToggle(
                            checked = isDraftRelease,
                            onCheckedChange = { isDraftRelease = it },
                            label = "Draft Release Only",
                            description = "Keep release hidden until finalized on GitHub",
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        val canPublish = releaseVersionInput.isNotBlank() &&
                                releaseTitleInput.isNotBlank() &&
                                githubStatus is GitHubConnectionStatus.Connected

                        EzzButton(
                            text = "Publish Release",
                            icon = Icons.Default.Publish,
                            size = EzzButtonSize.MEDIUM,
                            variant = EzzButtonVariant.PRIMARY,
                            enabled = canPublish,
                            onClick = { showPublishConfirmDialog = true }
                        )
                    }

                    // Progress Status Bar
                    when (val step = releasePublishStep) {
                        ReleasePublishStep.Idle -> {}
                        ReleasePublishStep.Preparing -> {
                            PublishProgressBanner("Preparing release parameters...", Color(0xFF8B5CF6))
                        }
                        ReleasePublishStep.Uploading -> {
                            PublishProgressBanner("Uploading artifact binary to GitHub Release Assets...", Color(0xFF8B5CF6))
                        }
                        ReleasePublishStep.Publishing -> {
                            PublishProgressBanner("Creating release tag and payload on GitHub...", Color(0xFF8B5CF6))
                        }
                        ReleasePublishStep.SyncingSupabase -> {
                            PublishProgressBanner("Synchronizing release metadata with Supabase...", Color(0xFF3B82F6))
                        }
                        is ReleasePublishStep.Success -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0F2618))
                                    .border(1.dp, Color(0xFF1B4D2E), RoundedCornerShape(8.dp))
                                    .padding(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "Release Published Successfully!", color = Color(0xFF10B981), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(text = "GitHub release created and Supabase update catalog synchronized.", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                                    }
                                    EzzButton(
                                        text = "View on GitHub",
                                        size = EzzButtonSize.SMALL,
                                        variant = EzzButtonVariant.SECONDARY,
                                        onClick = { viewModel.platformBridge.openUrl(step.releaseUrl) }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    EzzButton(
                                        text = "Dismiss",
                                        size = EzzButtonSize.SMALL,
                                        variant = EzzButtonVariant.SECONDARY,
                                        onClick = { viewModel.resetReleasePublishState() }
                                    )
                                }
                            }
                        }
                        is ReleasePublishStep.PartialSuccess -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF2E2210))
                                    .border(1.dp, Color(0xFF593F16), RoundedCornerShape(8.dp))
                                    .padding(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "Partial Success Warning", color = Color(0xFFF59E0B), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(text = step.message, color = Color(0xFFCBD5E1), fontSize = 11.sp)
                                    }
                                    EzzButton(
                                        text = "Dismiss",
                                        size = EzzButtonSize.SMALL,
                                        variant = EzzButtonVariant.SECONDARY,
                                        onClick = { viewModel.resetReleasePublishState() }
                                    )
                                }
                            }
                        }
                        is ReleasePublishStep.Failed -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF2B1414))
                                    .border(1.dp, Color(0xFF592222), RoundedCornerShape(8.dp))
                                    .padding(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "Release Publishing Failed", color = Color(0xFFEF4444), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(text = step.error, color = Color(0xFFCBD5E1), fontSize = 11.sp)
                                    }
                                    EzzButton(
                                        text = "Dismiss",
                                        size = EzzButtonSize.SMALL,
                                        variant = EzzButtonVariant.SECONDARY,
                                        onClick = { viewModel.resetReleasePublishState() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Connect GitHub Dialog
    if (showGitHubTokenDialog) {
        var tokenInput by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var isConnecting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showGitHubTokenDialog = false },
            confirmButton = {
                EzzButton(
                    text = if (isConnecting) "Verifying..." else "Authorize & Save",
                    size = EzzButtonSize.SMALL,
                    variant = EzzButtonVariant.PRIMARY,
                    isLoading = isConnecting,
                    onClick = {
                        if (tokenInput.isBlank()) {
                            errorMessage = "Token cannot be empty."
                            return@EzzButton
                        }
                        isConnecting = true
                        errorMessage = null
                        viewModel.connectGitHub(tokenInput) { success, err ->
                            isConnecting = false
                            if (success) {
                                showGitHubTokenDialog = false
                            } else {
                                errorMessage = err ?: "Failed to verify token with GitHub."
                            }
                        }
                    }
                )
            },
            dismissButton = {
                EzzButton(
                    text = "Cancel",
                    size = EzzButtonSize.SMALL,
                    variant = EzzButtonVariant.SECONDARY,
                    onClick = { showGitHubTokenDialog = false }
                )
            },
            title = {
                Text(
                    text = "Connect GitHub Release Pipeline",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter a GitHub Personal Access Token (classic with 'repo' scope or fine-grained with Contents & Releases write permission).",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Token is encrypted and stored locally in your SecureVault. It is NEVER bundled or shipped.",
                        color = Color(0xFF10B981),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    EzzTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        placeholder = "ghp_xxxxxxxxxxxxxxxxxxxx",
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = Color(0xFFEF4444),
                            fontSize = 11.sp
                        )
                    }
                }
            },
            containerColor = Color(0xFF101318),
            textContentColor = Color.White
        )
    }

    // Publish Confirmation Modal
    if (showPublishConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPublishConfirmDialog = false },
            confirmButton = {
                EzzButton(
                    text = "Confirm & Publish",
                    size = EzzButtonSize.SMALL,
                    variant = EzzButtonVariant.PRIMARY,
                    onClick = {
                        showPublishConfirmDialog = false
                        viewModel.publishAdminRelease(
                            version = releaseVersionInput,
                            title = releaseTitleInput,
                            changelog = releaseNotesInput,
                            artifactFile = selectedArtifactFile,
                            isDraft = isDraftRelease
                        )
                    }
                )
            },
            dismissButton = {
                EzzButton(
                    text = "Cancel",
                    size = EzzButtonSize.SMALL,
                    variant = EzzButtonVariant.SECONDARY,
                    onClick = { showPublishConfirmDialog = false }
                )
            },
            title = {
                Text(
                    text = "Confirm Release Publishing",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Publish this release to GitHub and make it available to Ezz Launcher users?",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp
                )
            },
            containerColor = Color(0xFF101318),
            textContentColor = Color.White
        )
    }
}

@Composable
private fun PublishProgressBanner(message: String, accentColor: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF141720))
            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = accentColor,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                color = Color.White,
                fontSize = 12.sp
            )
        }
    }
}
