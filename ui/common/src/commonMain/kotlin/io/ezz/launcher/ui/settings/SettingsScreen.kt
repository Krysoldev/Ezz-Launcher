package io.ezz.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import io.ezz.launcher.ui.viewmodel.AppViewModel
import io.ezz.launcher.ui.viewmodel.JavaValidationResult
import io.ezz.launcher.ui.viewmodel.ReleasePublishStep
import java.io.File

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settingsRepository.settings.collectAsState()
    val adminStatus by viewModel.adminStatus.collectAsState()
    val isCheckingAdmin by viewModel.isCheckingAdmin.collectAsState()
    val githubStatus by viewModel.githubConnectionStatus.collectAsState()
    val selectedAccount by viewModel.accountRepository.selectedAccount.collectAsState()

    val detectedRuntimes by viewModel.detectedJavaRuntimes.collectAsState()
    val isDetectingJava by viewModel.isDetectingJava.collectAsState()
    val memoryInfo by viewModel.systemMemoryInfo.collectAsState()

    val updateResult by viewModel.updateCheckResult.collectAsState()
    val isCheckingUpdates by viewModel.isCheckingForUpdates.collectAsState()
    val updateError by viewModel.updateCheckError.collectAsState()

    var showAdminReleaseModal by remember { mutableStateOf(false) }
    var showGitHubConnectModal by remember { mutableStateOf(false) }
    var showChangelogModal by remember { mutableStateOf<SupabaseLauncherReleaseDto?>(null) }

    val isMicrosoft = selectedAccount is MicrosoftAccount && selectedAccount?.type == io.ezz.launcher.core.model.account.AccountType.MICROSOFT
    val isVerifiedAdmin = isMicrosoft && (adminStatus is AdminStatus.VerifiedAdmin)
    val currentVer = viewModel.currentLauncherVersion
    val latestRelease = updateResult?.latestRelease
    val latestVer = latestRelease?.version ?: currentVer
    val hasUpdate = updateResult?.hasUpdate == true

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07080A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 36.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Page Header
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "SETTINGS",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Configure Java, Minecraft launch behavior, Discord presence, updates, and account administration.",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp
                )
            }

            // =========================================================================
            // SECTION 1: JAVA & MEMORY ALLOCATION
            // =========================================================================
            SettingsSectionHeader("JAVA & MEMORY")

            EzzCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 10.dp,
                backgroundColor = Color(0xFF10131A),
                borderColor = Color(0xFF1B1F2C)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Java Runtimes Row Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Java Runtimes",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Installed JVM environments detected on your system",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }
                        EzzButton(
                            text = "Manage Java Runtimes",
                            size = EzzButtonSize.SMALL,
                            variant = EzzButtonVariant.SECONDARY,
                            icon = Icons.Default.OpenInNew,
                            onClick = { viewModel.platformBridge.openUrl("https://adoptium.net/temurin/releases/") }
                        )
                    }

                    // Runtimes 4-Pill Grid
                    val hasJava8 = detectedRuntimes.any { it.majorVersion == 8 }
                    val hasJava17 = detectedRuntimes.any { it.majorVersion == 17 }
                    val hasJava21 = detectedRuntimes.any { it.majorVersion == 21 }
                    val hasJava25 = detectedRuntimes.any { it.majorVersion == 25 }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        JavaRuntimeStatusPill(
                            title = "Java 8",
                            isInstalled = hasJava8,
                            subtext = "MC <= 1.16.5",
                            modifier = Modifier.weight(1f)
                        )
                        JavaRuntimeStatusPill(
                            title = "Java 17",
                            isInstalled = hasJava17,
                            subtext = "MC 1.17 - 1.20.4",
                            modifier = Modifier.weight(1f)
                        )
                        JavaRuntimeStatusPill(
                            title = "Java 21",
                            isInstalled = hasJava21,
                            subtext = "MC 1.20.5+",
                            modifier = Modifier.weight(1f)
                        )
                        JavaRuntimeStatusPill(
                            title = "Java 25",
                            isInstalled = hasJava25,
                            subtext = "Experimental",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(color = Color(0xFF181C26))

                    // Memory Allocation Slider
                    val totalRamMb = memoryInfo.totalRamMb.coerceAtLeast(4096)
                    val ramGbFormatted = String.format("%.1f", settings.defaultMaxMemoryMb / 1024.0)

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Memory Allocation",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Default maximum RAM passed to Minecraft instances ($totalRamMb MB system total)",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp
                                )
                            }
                            EzzBadge(
                                text = "${settings.defaultMaxMemoryMb} MB ($ramGbFormatted GB)",
                                variant = EzzBadgeVariant.PRIMARY
                            )
                        }

                        EzzSlider(
                            value = settings.defaultMaxMemoryMb.toFloat().coerceIn(1024f, totalRamMb.toFloat()),
                            onValueChange = { newMb ->
                                val rounded = (newMb / 512).toInt() * 512
                                viewModel.updateMemorySettings(settings.defaultMinMemoryMb, rounded)
                            },
                            valueRange = 1024f..totalRamMb.toFloat(),
                            steps = ((totalRamMb - 1024) / 512).coerceAtLeast(1)
                        )
                    }

                    HorizontalDivider(color = Color(0xFF181C26))

                    // Java Executable Path Input
                    var javaPathInput by remember(settings.defaultJavaPath) {
                        mutableStateOf(settings.defaultJavaPath ?: "")
                    }
                    val pathValidation = remember(javaPathInput) {
                        viewModel.validateCustomJavaPath(javaPathInput)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Java Executable",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            when (pathValidation) {
                                JavaValidationResult.Empty -> {
                                    Text(
                                        text = "Auto system default",
                                        color = Color(0xFF64748B),
                                        fontSize = 11.sp
                                    )
                                }
                                JavaValidationResult.Valid -> {
                                    EzzBadge(text = "Valid Executable", variant = EzzBadgeVariant.SUCCESS)
                                }
                                JavaValidationResult.NotFound -> {
                                    EzzBadge(text = "Not Found", variant = EzzBadgeVariant.WARNING)
                                }
                                JavaValidationResult.IsDirectory -> {
                                    EzzBadge(text = "Is Directory", variant = EzzBadgeVariant.WARNING)
                                }
                                JavaValidationResult.NotJavaExecutable -> {
                                    EzzBadge(text = "Invalid Java Binary", variant = EzzBadgeVariant.DANGER)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EzzTextField(
                                value = javaPathInput,
                                onValueChange = {
                                    javaPathInput = it
                                    viewModel.updateCustomJavaPath(it)
                                },
                                placeholder = "Path to javaw.exe or java binary (leave blank for automatic selection)",
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
                                        javaPathInput = picked.absolutePath
                                        viewModel.updateCustomJavaPath(picked.absolutePath)
                                    }
                                }
                            )
                            EzzButton(
                                text = if (isDetectingJava) "Scanning..." else "Auto-Detect",
                                icon = Icons.Default.Search,
                                size = EzzButtonSize.MEDIUM,
                                variant = EzzButtonVariant.SECONDARY,
                                isLoading = isDetectingJava,
                                onClick = { viewModel.refreshJavaRuntimes() }
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFF181C26))

                    // JVM Launch Arguments
                    var jvmArgsInput by remember(settings.globalJvmArgs) {
                        mutableStateOf(settings.globalJvmArgs.joinToString(" "))
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "JVM Arguments",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Global launch flags passed to all instances. User-defined arguments are preserved.",
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

            // =========================================================================
            // SECTION 2: MINECRAFT WINDOW DEFAULTS
            // =========================================================================
            SettingsSectionHeader("MINECRAFT WINDOW DEFAULTS")

            EzzCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 10.dp,
                backgroundColor = Color(0xFF10131A),
                borderColor = Color(0xFF1B1F2C)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    var widthInput by remember(settings.defaultWindowWidth) {
                        mutableStateOf(settings.defaultWindowWidth.toString())
                    }
                    var heightInput by remember(settings.defaultWindowHeight) {
                        mutableStateOf(settings.defaultWindowHeight.toString())
                    }

                    val presets = listOf(
                        Pair(854, 480) to "854 × 480",
                        Pair(1280, 720) to "1280 × 720",
                        Pair(1920, 1080) to "1920 × 1080",
                        Pair(2560, 1440) to "2560 × 1440"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Width", color = Color(0xFF64748B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            EzzTextField(
                                value = widthInput,
                                onValueChange = { raw ->
                                    val digits = raw.filter { it.isDigit() }
                                    widthInput = digits
                                    val num = digits.toIntOrNull()
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
                            color = Color(0xFF475569),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Height", color = Color(0xFF64748B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            EzzTextField(
                                value = heightInput,
                                onValueChange = { raw ->
                                    val digits = raw.filter { it.isDigit() }
                                    heightInput = digits
                                    val num = digits.toIntOrNull()
                                    if (num != null && num in 240..4320) {
                                        viewModel.updateWindowDefaults(settings.defaultWindowWidth, num, settings.defaultFullscreen)
                                    }
                                },
                                placeholder = "480",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Presets Chips
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            presets.forEach { (res, label) ->
                                val isSelected = settings.defaultWindowWidth == res.first && settings.defaultWindowHeight == res.second
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) Color(0xFF1E2433) else Color(0xFF12151D))
                                        .border(
                                            1.dp,
                                            if (isSelected) Color(0xFF8B5CF6) else Color(0xFF1F2432),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable {
                                            widthInput = res.first.toString()
                                            heightInput = res.second.toString()
                                            viewModel.updateWindowDefaults(res.first, res.second, settings.defaultFullscreen)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF181C26))

                    // Launch in Fullscreen Toggle Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Launch in Fullscreen",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Start Minecraft in borderless or exclusive fullscreen display mode",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }
                        EzzToggle(
                            checked = settings.defaultFullscreen,
                            onCheckedChange = { checked ->
                                viewModel.updateWindowDefaults(settings.defaultWindowWidth, settings.defaultWindowHeight, checked)
                            }
                        )
                    }
                }
            }

            // =========================================================================
            // SECTION 3: DISCORD RICH PRESENCE
            // =========================================================================
            SettingsSectionHeader("DISCORD RICH PRESENCE")

            EzzCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 10.dp,
                backgroundColor = Color(0xFF10131A),
                borderColor = Color(0xFF1B1F2C)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Discord Rich Presence",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            EzzBadge(
                                text = if (settings.enableDiscordRpc) "ACTIVE" else "OFF",
                                variant = if (settings.enableDiscordRpc) EzzBadgeVariant.SUCCESS else EzzBadgeVariant.NEUTRAL
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Show active instance name, Minecraft version, and session playtime in your Discord status.",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }

                    EzzToggle(
                        checked = settings.enableDiscordRpc,
                        onCheckedChange = { viewModel.updateDiscordRpc(it) }
                    )
                }
            }

            // =========================================================================
            // SECTION 4: UPDATES & VERSION
            // =========================================================================
            SettingsSectionHeader("UPDATES & VERSION")

            EzzCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 10.dp,
                backgroundColor = Color(0xFF10131A),
                borderColor = Color(0xFF1B1F2C)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Updates & Version",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                EzzBadge(
                                    text = "v$currentVer",
                                    variant = EzzBadgeVariant.PRIMARY
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                when {
                                    hasUpdate -> {
                                        EzzBadge(text = "Update Available: v$latestVer", variant = EzzBadgeVariant.WARNING)
                                    }
                                    isCheckingUpdates -> {
                                        EzzBadge(text = "Checking...", variant = EzzBadgeVariant.NEUTRAL)
                                    }
                                    else -> {
                                        EzzBadge(text = "Up to date", variant = EzzBadgeVariant.SUCCESS)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Check for launcher updates and manage releases.",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EzzButton(
                                text = if (isCheckingUpdates) "Checking..." else "Check for Updates",
                                icon = Icons.Default.Refresh,
                                size = EzzButtonSize.SMALL,
                                variant = EzzButtonVariant.SECONDARY,
                                isLoading = isCheckingUpdates,
                                onClick = { viewModel.checkForUpdates() }
                            )

                            // Admin Release Manager Button: ONLY for genuine authorized admin
                            if (isVerifiedAdmin) {
                                EzzButton(
                                    text = "Admin Release Manager",
                                    icon = Icons.Default.Publish,
                                    size = EzzButtonSize.SMALL,
                                    variant = EzzButtonVariant.PRIMARY,
                                    onClick = { showAdminReleaseModal = true }
                                )
                            }
                        }
                    }

                    // Update Notice Banner if update exists
                    if (hasUpdate && latestRelease != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1B1426))
                                .border(1.dp, Color(0xFF4C2A78), RoundedCornerShape(6.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Official Release v${latestRelease.version} Ready",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "An official build is published and available for download.",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.sp
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    EzzButton(
                                        text = "Release Notes",
                                        size = EzzButtonSize.SMALL,
                                        variant = EzzButtonVariant.SECONDARY,
                                        onClick = { showChangelogModal = latestRelease }
                                    )
                                    val dlUrl = latestRelease.downloadUrl
                                    if (dlUrl != null) {
                                        EzzButton(
                                            text = "Download",
                                            icon = Icons.Default.Download,
                                            size = EzzButtonSize.SMALL,
                                            variant = EzzButtonVariant.PRIMARY,
                                            onClick = { viewModel.platformBridge.openUrl(dlUrl) }
                                        )
                                    }
                                }
                            }
                        }
                    } else if (updateError != null) {
                        Text(
                            text = updateError ?: "Could not check for updates. Try again later.",
                            color = Color(0xFFEF4444),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // =========================================================================
            // SECTION 5: EZZ LAUNCHER ADMIN IDENTITY
            // =========================================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "EZZ LAUNCHER ADMIN",
                    color = Color(0xFF8B5CF6),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                if (isCheckingAdmin) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color(0xFF8B5CF6))
                } else if (isVerifiedAdmin) {
                    EzzBadge(text = "ADMIN VERIFIED", variant = EzzBadgeVariant.SUCCESS)
                } else {
                    EzzBadge(text = "NOT AUTHORIZED", variant = EzzBadgeVariant.NEUTRAL)
                }
            }
            HorizontalDivider(color = Color(0xFF1E222D))

            EzzCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 10.dp,
                backgroundColor = Color(0xFF10131A),
                borderColor = if (isVerifiedAdmin) Color(0xFF1B3D2B) else Color(0xFF1B1F2C)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val accountName = selectedAccount?.username ?: "No Active Account"

                    // Microsoft Account Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "Microsoft Account", color = Color(0xFF64748B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(text = accountName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isMicrosoft) Color(0xFF10B981) else Color(0xFFF59E0B))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isMicrosoft) "Microsoft • CONNECTED" else "Offline Account • NOT CONNECTED",
                                    color = if (isMicrosoft) Color(0xFF10B981) else Color(0xFFF59E0B),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        if (isVerifiedAdmin) {
                            EzzBadge(text = "KrysolDev Verified", variant = EzzBadgeVariant.SUCCESS)
                        }
                    }

                    if (!isVerifiedAdmin) {
                        Text(
                            text = "Admin access requires the authorized Microsoft account.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }

                    // GitHub Pipeline Row: strictly accessible to verified admin only
                    if (isVerifiedAdmin) {
                        HorizontalDivider(color = Color(0xFF181C26))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = "GitHub", color = Color(0xFF64748B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                when (val gh = githubStatus) {
                                    is GitHubConnectionStatus.Connected -> {
                                        Text(text = gh.username, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF10B981)))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(text = "CONNECTED • AUTHORIZED", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    else -> {
                                        Text(text = "Ready to connect", color = Color(0xFF94A3B8), fontSize = 13.sp)
                                    }
                                }
                            }

                            when (githubStatus) {
                                is GitHubConnectionStatus.Connected -> {
                                    EzzButton(
                                        text = "Disconnect GitHub",
                                        size = EzzButtonSize.SMALL,
                                        variant = EzzButtonVariant.SECONDARY,
                                        onClick = { viewModel.disconnectGitHub() }
                                    )
                                }
                                else -> {
                                    EzzButton(
                                        text = "Connect GitHub",
                                        icon = Icons.Default.Link,
                                        size = EzzButtonSize.SMALL,
                                        variant = EzzButtonVariant.PRIMARY,
                                        onClick = { showGitHubConnectModal = true }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // =========================================================================
    // ADMIN RELEASE MANAGER MODAL (Exclusively for KrysolDev)
    // =========================================================================
    if (showAdminReleaseModal && isVerifiedAdmin) {
        AdminReleaseManagerModal(
            viewModel = viewModel,
            onDismiss = { showAdminReleaseModal = false }
        )
    }

    // GitHub Connection Modal
    if (showGitHubConnectModal) {
        GitHubConnectModal(
            viewModel = viewModel,
            onDismiss = { showGitHubConnectModal = false }
        )
    }

    // Release Notes Modal
    if (showChangelogModal != null) {
        val rel = showChangelogModal!!
        AlertDialog(
            onDismissRequest = { showChangelogModal = null },
            confirmButton = {
                EzzButton(
                    text = "Close",
                    size = EzzButtonSize.SMALL,
                    variant = EzzButtonVariant.SECONDARY,
                    onClick = { showChangelogModal = null }
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
                Text(
                    text = rel.releaseNotes ?: "No detailed changelog provided for this release.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp
                )
            },
            containerColor = Color(0xFF101318),
            textContentColor = Color.White
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            color = Color(0xFF8B5CF6),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        HorizontalDivider(color = Color(0xFF1E222D))
    }
}

@Composable
private fun JavaRuntimeStatusPill(
    title: String,
    isInstalled: Boolean,
    subtext: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isInstalled) Color(0xFF0F1A14) else Color(0xFF1A150F))
            .border(
                1.dp,
                if (isInstalled) Color(0xFF1B3D28) else Color(0xFF382A15),
                RoundedCornerShape(6.dp)
            )
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isInstalled) Color(0xFF10B981) else Color(0xFFF59E0B))
                )
            }
            Text(
                text = if (isInstalled) "Installed" else "Missing",
                color = if (isInstalled) Color(0xFF10B981) else Color(0xFFF59E0B),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtext,
                color = Color(0xFF64748B),
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun AdminReleaseManagerModal(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    var versionInput by remember { mutableStateOf("") }
    var titleInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }
    var selectedArtifact by remember { mutableStateOf<File?>(null) }
    var isDraft by remember { mutableStateOf(false) }
    var showConfirmPublish by remember { mutableStateOf(false) }

    val releaseStep by viewModel.releasePublishStep.collectAsState()
    val githubStatus by viewModel.githubConnectionStatus.collectAsState()

    AlertDialog(
        onDismissRequest = {
            viewModel.resetReleasePublishState()
            onDismiss()
        },
        confirmButton = {},
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Publish, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ADMIN RELEASE MANAGER",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
                EzzBadge(text = "Current: v${viewModel.currentLauncherVersion}", variant = EzzBadgeVariant.PRIMARY)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Version & Title
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "New Version", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        EzzTextField(
                            value = versionInput,
                            onValueChange = { versionInput = it },
                            placeholder = "1.0.1",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Column(modifier = Modifier.weight(2f)) {
                        Text(text = "Release Title", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        EzzTextField(
                            value = titleInput,
                            onValueChange = { titleInput = it },
                            placeholder = "Ezz Launcher v1.0.1 Update",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Notes
                Column {
                    Text(text = "Release Notes / Changelog", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    EzzTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        placeholder = "Details on enhancements, bug fixes, and upgrade notes...",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Artifact
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Build / Binary Artifact", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF141720))
                                .border(1.dp, Color(0xFF222735), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 9.dp)
                        ) {
                            Text(
                                text = selectedArtifact?.let { "${it.name} (${String.format("%.1f", it.length() / (1024.0 * 1024.0))} MB)" }
                                    ?: "No binary artifact selected (optional)",
                                color = if (selectedArtifact != null) Color.White else Color(0xFF64748B),
                                fontSize = 11.sp,
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
                            if (picked != null) selectedArtifact = picked
                        }
                    )
                }

                // Draft toggle & Publish
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    EzzToggle(
                        checked = isDraft,
                        onCheckedChange = { isDraft = it },
                        label = "Draft Release",
                        description = "Keep release hidden until finalized on GitHub"
                    )

                    val canPublish = versionInput.isNotBlank() &&
                            titleInput.isNotBlank() &&
                            githubStatus is GitHubConnectionStatus.Connected

                    EzzButton(
                        text = "Publish Release",
                        icon = Icons.Default.Publish,
                        size = EzzButtonSize.MEDIUM,
                        variant = EzzButtonVariant.PRIMARY,
                        enabled = canPublish,
                        onClick = { showConfirmPublish = true }
                    )
                }

                // Publish Progress Status Banner
                when (val step = releaseStep) {
                    ReleasePublishStep.Idle -> {}
                    ReleasePublishStep.Preparing -> StepBanner("Preparing release parameters...", Color(0xFF8B5CF6))
                    ReleasePublishStep.Uploading -> StepBanner("Uploading binary artifact to GitHub Release Assets...", Color(0xFF8B5CF6))
                    ReleasePublishStep.Publishing -> StepBanner("Creating GitHub Release tag...", Color(0xFF8B5CF6))
                    ReleasePublishStep.SyncingSupabase -> StepBanner("Synchronizing release metadata with Supabase...", Color(0xFF3B82F6))
                    is ReleasePublishStep.Success -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0F2618))
                                .border(1.dp, Color(0xFF1B4D2E), RoundedCornerShape(6.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(text = "Release Published Successfully!", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(text = "GitHub release created and Supabase update catalog synchronized.", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    EzzButton(
                                        text = "View on GitHub",
                                        size = EzzButtonSize.SMALL,
                                        variant = EzzButtonVariant.SECONDARY,
                                        onClick = { viewModel.platformBridge.openUrl(step.releaseUrl) }
                                    )
                                    EzzButton(
                                        text = "Done",
                                        size = EzzButtonSize.SMALL,
                                        variant = EzzButtonVariant.PRIMARY,
                                        onClick = {
                                            viewModel.resetReleasePublishState()
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                        }
                    }
                    is ReleasePublishStep.PartialSuccess -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF2E2210))
                                .border(1.dp, Color(0xFF593F16), RoundedCornerShape(6.dp))
                                .padding(12.dp)
                        ) {
                            Text(text = step.message, color = Color(0xFFF59E0B), fontSize = 11.sp)
                        }
                    }
                    is ReleasePublishStep.Failed -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF2B1414))
                                .border(1.dp, Color(0xFF592222), RoundedCornerShape(6.dp))
                                .padding(12.dp)
                        ) {
                            Text(text = step.error, color = Color(0xFFEF4444), fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFF101318),
        textContentColor = Color.White
    )

    if (showConfirmPublish) {
        AlertDialog(
            onDismissRequest = { showConfirmPublish = false },
            confirmButton = {
                EzzButton(
                    text = "Confirm & Publish",
                    size = EzzButtonSize.SMALL,
                    variant = EzzButtonVariant.PRIMARY,
                    onClick = {
                        showConfirmPublish = false
                        viewModel.publishAdminRelease(
                            version = versionInput,
                            title = titleInput,
                            changelog = notesInput,
                            artifactFile = selectedArtifact,
                            isDraft = isDraft
                        )
                    }
                )
            },
            dismissButton = {
                EzzButton(
                    text = "Cancel",
                    size = EzzButtonSize.SMALL,
                    variant = EzzButtonVariant.SECONDARY,
                    onClick = { showConfirmPublish = false }
                )
            },
            title = { Text(text = "Confirm Release Publishing", color = Color.White, fontWeight = FontWeight.Bold) },
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
private fun StepBanner(message: String, accent: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF141720))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = accent, strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = message, color = Color.White, fontSize = 11.sp)
        }
    }
}

@Composable
private fun GitHubConnectModal(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    var tokenInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isConnecting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                            onDismiss()
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
                onClick = onDismiss
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Text(text = errorMessage!!, color = Color(0xFFEF4444), fontSize = 11.sp)
                }
            }
        },
        containerColor = Color(0xFF101318),
        textContentColor = Color.White
    )
}
