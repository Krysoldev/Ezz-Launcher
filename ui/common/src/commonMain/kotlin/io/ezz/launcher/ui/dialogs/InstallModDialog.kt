package io.ezz.launcher.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.model.modrinth.ModrinthProjectHit
import io.ezz.launcher.core.model.modrinth.ModrinthVersion
import io.ezz.launcher.core.model.modrinth.ResolvedModDependency
import io.ezz.launcher.ui.components.EzzBadge
import io.ezz.launcher.ui.components.EzzBadgeVariant
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.ModrinthAsyncImage
import io.ezz.launcher.ui.viewmodel.AppViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InstallModDialog(
    project: ModrinthProjectHit,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val selectedInstance by viewModel.selectedInstance.collectAsState()
    val instances by viewModel.instanceRepository.instances.collectAsState()
    val scope = rememberCoroutineScope()

    var activeInstance by remember { mutableStateOf(selectedInstance ?: instances.firstOrNull()) }
    var installedModsForInstance by remember { mutableStateOf<List<io.ezz.launcher.core.model.instance.LocalMod>>(emptyList()) }

    // Dynamic Version and Loader metadata
    var availableGameVersions by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedGameVersion by remember { mutableStateOf("") }
    var availableLoadersForVersion by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedLoader by remember { mutableStateOf("") }

    // Versions and Releases
    var compatibleVersions by remember { mutableStateOf<List<ModrinthVersion>>(emptyList()) }
    var selectedVersion by remember { mutableStateOf<ModrinthVersion?>(null) }
    var resolvedDependencies by remember { mutableStateOf<List<ResolvedModDependency>>(emptyList()) }
    var resolutionResult by remember { mutableStateOf<io.ezz.launcher.core.model.modrinth.ModResolutionResult?>(null) }

    // UI Loading and Progress States
    var isLoadingMetadata by remember { mutableStateOf(true) }
    var isLoadingVersions by remember { mutableStateOf(false) }
    var isLoadingDependencies by remember { mutableStateOf(false) }
    var isInstalling by remember { mutableStateOf(false) }
    var installStage by remember { mutableStateOf("") }
    var installProgress by remember { mutableStateOf(0f) }
    var installComplete by remember { mutableStateOf(false) }
    var installError by remember { mutableStateOf<String?>(null) }

    var isVersionDropdownOpen by remember { mutableStateOf(false) }

    // Load instance-specific mods whenever activeInstance changes
    LaunchedEffect(activeInstance?.id) {
        val inst = activeInstance ?: return@LaunchedEffect
        installedModsForInstance = viewModel.instanceManager.getMods(inst.id)
    }

    // Check if mod is already installed in target instance
    val installedMod = remember(activeInstance, project, installedModsForInstance) {
        val slug = project.slug.lowercase()
        val pId = project.projectId.lowercase()
        val title = project.title.lowercase()
        installedModsForInstance.firstOrNull { m ->
            m.id.equals(slug, ignoreCase = true) ||
            m.id.equals(pId, ignoreCase = true) ||
            m.name.equals(title, ignoreCase = true) ||
            m.fileName.lowercase().contains(slug) ||
            m.fileName.lowercase().contains(pId)
        }
    }

    // Step 1: Initialize metadata and smart defaults
    LaunchedEffect(project.projectId, activeInstance?.id) {
        isLoadingMetadata = true
        installError = null
        try {
            val supportedVersions = viewModel.modrinth.getProjectSupportedVersions(project.projectId)
            availableGameVersions = supportedVersions

            // Smart Default: Pick active instance version if supported, otherwise latest supported version
            val instVersion = activeInstance?.minecraftVersion
            val initialVersion = when {
                instVersion != null && supportedVersions.contains(instVersion) -> instVersion
                supportedVersions.isNotEmpty() -> supportedVersions.first()
                else -> instVersion ?: "1.21.1"
            }
            selectedGameVersion = initialVersion

            // Fetch loaders supported for this version
            val supportedLoaders = viewModel.modrinth.getProjectSupportedLoadersForVersion(project.projectId, initialVersion)
            availableLoadersForVersion = supportedLoaders

            // Smart Default: Pick active instance loader if supported, otherwise first available
            val instLoader = activeInstance?.loaderType?.name?.lowercase() ?: "fabric"
            val initialLoader = when {
                supportedLoaders.contains(instLoader) -> instLoader
                supportedLoaders.isNotEmpty() -> supportedLoaders.first()
                else -> "fabric"
            }
            selectedLoader = initialLoader

            isLoadingMetadata = false
        } catch (e: Throwable) {
            installError = "Failed to load mod metadata from Modrinth: ${e.message}"
            isLoadingMetadata = false
        }
    }

    // Step 2: Whenever selectedGameVersion, selectedLoader, or installedModsForInstance changes, fetch releases & run Smart Compatibility Resolver
    LaunchedEffect(selectedGameVersion, selectedLoader, installedModsForInstance) {
        if (selectedGameVersion.isBlank()) return@LaunchedEffect
        isLoadingVersions = true
        selectedVersion = null
        resolvedDependencies = emptyList()
        resolutionResult = null

        try {
            // Update loaders for this version
            val loaders = viewModel.modrinth.getProjectSupportedLoadersForVersion(project.projectId, selectedGameVersion)
            availableLoadersForVersion = loaders
            if (selectedLoader.isNotBlank() && !loaders.contains(selectedLoader) && loaders.isNotEmpty()) {
                selectedLoader = loaders.first()
            }

            val versions = viewModel.modrinth.getProjectVersions(
                projectIdOrSlug = project.projectId,
                loaders = if (selectedLoader.isNotBlank()) listOf(selectedLoader) else null,
                gameVersions = listOf(selectedGameVersion)
            )
            compatibleVersions = versions

            // Run authoritative whole-instance compatibility resolution against installed mods
            val res = io.ezz.launcher.core.minecraft.mods.ModCompatibilityResolver.resolve(
                minecraftVersion = selectedGameVersion,
                loader = selectedLoader,
                installedMods = installedModsForInstance,
                project = project,
                candidateVersions = versions
            )
            resolutionResult = res

            // Smartly select the recommended compatible version (or fallback to first matching)
            selectedVersion = res.recommendedVersion ?: versions.firstOrNull()

            isLoadingVersions = false
        } catch (e: Throwable) {
            installError = "Failed to fetch compatible releases: ${e.message}"
            isLoadingVersions = false
        }
    }

    // Step 3: Whenever selectedVersion changes, resolve dependencies
    LaunchedEffect(selectedVersion?.id, installedModsForInstance) {
        val ver = selectedVersion ?: return@LaunchedEffect
        if (ver.dependencies.isEmpty()) {
            resolvedDependencies = emptyList()
            return@LaunchedEffect
        }

        isLoadingDependencies = true
        try {
            val installedModNames = installedModsForInstance.map { it.id }.toSet()
            val deps = viewModel.modrinth.resolveDependencies(
                dependencies = ver.dependencies,
                gameVersion = selectedGameVersion,
                loader = selectedLoader,
                installedMods = installedModsForInstance,
                installedModIds = installedModNames,
                rootProjectTitle = project.title,
                rootVersionNumber = ver.versionNumber,
                rootProjectIdOrSlug = project.slug
            )
            resolvedDependencies = deps
            isLoadingDependencies = false
        } catch (e: Throwable) {
            println("Warning resolving dependencies: ${e.message}")
            isLoadingDependencies = false
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isInstalling) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.80f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (!isInstalling) onDismiss()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(720.dp)
                    .height(640.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF101318))
                    .border(1.dp, Color(0xFF1F2430), RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // block click through
                    )
                    .padding(22.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // ==========================================
                    // 1. HEADER (Icon, Title, Target Instance)
                    // ==========================================
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                ModrinthAsyncImage(
                                    url = project.previewImageUrl,
                                    imageLoader = viewModel.imageLoader,
                                    modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)),
                                    placeholderIcon = Icons.Default.Extension,
                                    contentScale = ContentScale.Crop
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = project.title,
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (project.author.isNotBlank()) {
                                            Text(
                                                text = "by ${project.author}",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    // Target Instance Badge
                                    if (activeInstance != null) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text("Target Instance:", color = Color(0xFF64748B), fontSize = 11.5.sp)
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFF141720))
                                                    .border(1.dp, Color(0xFF222735), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "${activeInstance!!.name} (${activeInstance!!.minecraftVersion} ${activeInstance!!.loaderType.name})",
                                                    color = Color(0xFF10B981),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (!isInstalling) {
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ==========================================
                    // 2. MAIN WIZARD CONTENT (Scrollable)
                    // ==========================================
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0C0E12))
                            .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(10.dp))
                            .padding(16.dp)
                    ) {
                        if (isLoadingMetadata) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                                    Text("Fetching project compatibility data from Modrinth...", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                }
                            }
                        } else {
                            val scrollState = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // -------------------------------------------------------------
                                // A. SELECT MINECRAFT VERSION & LOADER
                                // -------------------------------------------------------------
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "1. MINECRAFT VERSION & MOD LOADER",
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
                                        // Minecraft Version Dropdown
                                        Column(modifier = Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Minecraft Version", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            Box {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(38.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color(0xFF141720))
                                                        .border(1.dp, Color(0xFF222735), RoundedCornerShape(6.dp))
                                                        .clickable(enabled = !isInstalling) { isVersionDropdownOpen = true }
                                                        .padding(horizontal = 12.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = selectedGameVersion.ifBlank { "Select Version" },
                                                            color = Color.White,
                                                            fontSize = 12.5.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                                                    }
                                                }

                                                DropdownMenu(
                                                    expanded = isVersionDropdownOpen,
                                                    onDismissRequest = { isVersionDropdownOpen = false },
                                                    modifier = Modifier
                                                        .background(Color(0xFF141720))
                                                        .border(1.dp, Color(0xFF222735), RoundedCornerShape(6.dp))
                                                ) {
                                                    availableGameVersions.forEach { ver ->
                                                        val isSelected = ver == selectedGameVersion
                                                        DropdownMenuItem(
                                                            text = {
                                                                Text(
                                                                    text = ver,
                                                                    color = if (isSelected) Color(0xFF10B981) else Color.White,
                                                                    fontSize = 12.5.sp,
                                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                                )
                                                            },
                                                            onClick = {
                                                                selectedGameVersion = ver
                                                                isVersionDropdownOpen = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Mod Loader Selectors
                                        Column(modifier = Modifier.weight(1.8f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Mod Loader", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                listOf("fabric", "neoforge", "forge", "quilt").forEach { loader ->
                                                    val isSupported = availableLoadersForVersion.contains(loader)
                                                    val isSelected = selectedLoader == loader

                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(38.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(
                                                                when {
                                                                    isSelected -> Color(0xFF10B981).copy(alpha = 0.2f)
                                                                    isSupported -> Color(0xFF141720)
                                                                    else -> Color(0xFF0F1117).copy(alpha = 0.5f)
                                                                }
                                                            )
                                                            .border(
                                                                1.dp,
                                                                when {
                                                                    isSelected -> Color(0xFF10B981)
                                                                    isSupported -> Color(0xFF222735)
                                                                    else -> Color(0xFF191D26)
                                                                },
                                                                RoundedCornerShape(6.dp)
                                                            )
                                                            .clickable(enabled = isSupported && !isInstalling) {
                                                                selectedLoader = loader
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = loader.replaceFirstChar { it.uppercase() },
                                                            color = when {
                                                                isSelected -> Color(0xFF10B981)
                                                                isSupported -> Color.White
                                                                else -> Color(0xFF475569)
                                                            },
                                                            fontSize = 11.5.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // -------------------------------------------------------------
                                // B. COMPATIBLE RELEASES & FILES
                                // -------------------------------------------------------------
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "2. COMPATIBLE MOD RELEASES",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )

                                        if (compatibleVersions.isNotEmpty()) {
                                            Text(
                                                text = "${compatibleVersions.size} versions evaluated",
                                                color = Color(0xFF64748B),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    // Why This Version Banner
                                    if (resolutionResult != null && !resolutionResult!!.isLatestCompatible && resolutionResult!!.recommendedVersion != null) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF0F2231))
                                                .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                                .padding(12.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.Top,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text(
                                                        text = "Why this version?",
                                                        color = Color(0xFF38BDF8),
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = resolutionResult!!.selectionReason ?: "Selected for maximum compatibility with your installed mods.",
                                                        color = Color(0xFFBAE6FD),
                                                        fontSize = 11.5.sp,
                                                        lineHeight = 16.sp
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Co-Upgrade Suggestion if applicable
                                    if (resolutionResult?.coUpgradeOption != null) {
                                        val opt = resolutionResult!!.coUpgradeOption!!
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF181828))
                                                .border(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                                .padding(12.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.Top,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(20.dp))
                                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text("Co-Upgrade Recommendation", color = Color(0xFFA78BFA), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    Text(opt.explanation, color = Color(0xFFDDD6FE), fontSize = 11.5.sp)
                                                }
                                            }
                                        }
                                    }

                                    if (isLoadingVersions) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(100.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                        }
                                    } else if (compatibleVersions.isEmpty() || (resolutionResult != null && !resolutionResult!!.hasCompatibleVersion)) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF1A1315))
                                                .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                                .padding(14.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                                                Column {
                                                    Text(
                                                        text = "NO COMPATIBLE VERSION FOUND",
                                                        color = Color(0xFFFCA5A5),
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = resolutionResult?.selectionReason ?: "No version is compatible with Minecraft $selectedGameVersion, $selectedLoader, and your installed mods.",
                                                        color = Color(0xFF94A3B8),
                                                        fontSize = 11.5.sp
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            compatibleVersions.take(5).forEach { ver ->
                                                val isSelected = selectedVersion?.id == ver.id
                                                val isRelease = ver.versionType.equals("release", ignoreCase = true)
                                                val isBeta = ver.versionType.equals("beta", ignoreCase = true)
                                                val eval = resolutionResult?.candidateEvaluations?.get(ver.id)
                                                val isVerCompatible = eval?.isCompatible ?: true
                                                val isRecommended = resolutionResult?.recommendedVersion?.id == ver.id

                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(
                                                            when {
                                                                isSelected && !isVerCompatible -> Color(0xFF261418)
                                                                isSelected -> Color(0xFF1A1E29)
                                                                !isVerCompatible -> Color(0xFF161013)
                                                                else -> Color(0xFF141720)
                                                            }
                                                        )
                                                        .border(
                                                            1.dp,
                                                            when {
                                                                isSelected && !isVerCompatible -> Color(0xFFEF4444)
                                                                isSelected -> Color(0xFF10B981)
                                                                !isVerCompatible -> Color(0xFFEF4444).copy(alpha = 0.3f)
                                                                else -> Color(0xFF222735)
                                                            },
                                                            RoundedCornerShape(6.dp)
                                                        )
                                                        .clickable(enabled = !isInstalling) {
                                                            selectedVersion = ver
                                                        }
                                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            // Radio Circle Indicator
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(14.dp)
                                                                    .clip(RoundedCornerShape(7.dp))
                                                                    .background(
                                                                        when {
                                                                            isSelected && !isVerCompatible -> Color(0xFFEF4444)
                                                                            isSelected -> Color(0xFF10B981)
                                                                            else -> Color.Transparent
                                                                        }
                                                                    )
                                                                    .border(
                                                                        1.5.dp,
                                                                        when {
                                                                            isSelected && !isVerCompatible -> Color(0xFFEF4444)
                                                                            isSelected -> Color(0xFF10B981)
                                                                            else -> Color(0xFF64748B)
                                                                        },
                                                                        RoundedCornerShape(7.dp)
                                                                    )
                                                            )

                                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                                ) {
                                                                    Text(
                                                                        text = ver.name.ifBlank { "v${ver.versionNumber}" },
                                                                        color = if (isVerCompatible) Color.White else Color(0xFFFCA5A5),
                                                                        fontSize = 12.5.sp,
                                                                        fontWeight = FontWeight.SemiBold
                                                                    )

                                                                    // Version Type Badge
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .clip(RoundedCornerShape(3.dp))
                                                                            .background(
                                                                                when {
                                                                                    isRelease -> Color(0xFF10B981).copy(alpha = 0.15f)
                                                                                    isBeta -> Color(0xFF3B82F6).copy(alpha = 0.15f)
                                                                                    else -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                                                                                }
                                                                            )
                                                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                                                    ) {
                                                                        Text(
                                                                            text = ver.versionType.uppercase(),
                                                                            color = when {
                                                                                isRelease -> Color(0xFF10B981)
                                                                                isBeta -> Color(0xFF60A5FA)
                                                                                else -> Color(0xFFFBBF24)
                                                                            },
                                                                            fontSize = 9.5.sp,
                                                                            fontWeight = FontWeight.Bold
                                                                        )
                                                                    }

                                                                    if (isRecommended) {
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .clip(RoundedCornerShape(3.dp))
                                                                                .background(Color(0xFF10B981).copy(alpha = 0.2f))
                                                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                                                        ) {
                                                                            Text(
                                                                                text = "✓ RECOMMENDED",
                                                                                color = Color(0xFF10B981),
                                                                                fontSize = 9.5.sp,
                                                                                fontWeight = FontWeight.Bold
                                                                            )
                                                                        }
                                                                    }

                                                                    if (!isVerCompatible) {
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .clip(RoundedCornerShape(3.dp))
                                                                                .background(Color(0xFFEF4444).copy(alpha = 0.2f))
                                                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                                                        ) {
                                                                            Text(
                                                                                text = "⚠ CONFLICT",
                                                                                color = Color(0xFFEF4444),
                                                                                fontSize = 9.5.sp,
                                                                                fontWeight = FontWeight.Bold
                                                                            )
                                                                        }
                                                                    }
                                                                }

                                                                if (!isVerCompatible && eval != null && eval.conflicts.isNotEmpty()) {
                                                                    Text(
                                                                        text = eval.conflicts.first().reason,
                                                                        color = Color(0xFFF87171),
                                                                        fontSize = 11.sp
                                                                    )
                                                                } else {
                                                                    Text(
                                                                        text = "MC: ${ver.gameVersions.joinToString(", ")} • ${ver.loaders.joinToString(", ") { it.uppercase() }}",
                                                                        color = Color(0xFF64748B),
                                                                        fontSize = 11.sp
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        val primaryFile = ver.files.firstOrNull { it.primary } ?: ver.files.firstOrNull()
                                                        if (primaryFile != null) {
                                                            Text(
                                                                text = formatFileSize(primaryFile.size),
                                                                color = Color(0xFF94A3B8),
                                                                fontSize = 11.sp
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // -------------------------------------------------------------
                                // C. COMPATIBILITY CHECKLIST
                                // -------------------------------------------------------------
                                if (selectedVersion != null && resolutionResult != null) {
                                    val currentEval = resolutionResult!!.candidateEvaluations[selectedVersion!!.id]
                                    val isCurrentCompatible = currentEval?.isCompatible != false

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "3. COMPATIBILITY CHECKLIST",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isCurrentCompatible) Color(0xFF0D1B14) else Color(0xFF241316))
                                                .border(
                                                    1.dp,
                                                    if (isCurrentCompatible) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFFEF4444).copy(alpha = 0.4f),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .padding(12.dp)
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isCurrentCompatible) Icons.Default.Check else Icons.Default.Warning,
                                                        contentDescription = null,
                                                        tint = if (isCurrentCompatible) Color(0xFF10B981) else Color(0xFFEF4444),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = if (isCurrentCompatible) "Selected version is compatible with instance" else "Incompatibility Detected",
                                                        color = if (isCurrentCompatible) Color(0xFF10B981) else Color(0xFFFCA5A5),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }

                                                Column(modifier = Modifier.padding(start = 24.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                    Text("✓ Minecraft $selectedGameVersion", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                                    Text("✓ ${selectedLoader.uppercase()} Mod Loader", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                                    if (currentEval != null && currentEval.conflicts.isNotEmpty()) {
                                                        currentEval.conflicts.forEach { c ->
                                                            Text("❌ ${c.reason}", color = Color(0xFFF87171), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                                        }
                                                    } else {
                                                        val installedModsCount = viewModel.manageMods.value.size
                                                        if (installedModsCount > 0) {
                                                            Text("✓ Compatible with all $installedModsCount installed mods", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                                        }
                                                        Text("✓ Dependencies satisfied & no conflicts", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // -------------------------------------------------------------
                                // D. INSTALLATION PLAN & DEPENDENCIES SECTION
                                // -------------------------------------------------------------
                                if (resolvedDependencies.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "3. INSTALLATION PLAN & DEPENDENCIES",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )

                                        resolvedDependencies.forEach { dep ->
                                            val hasError = dep.failureReason != null
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (hasError) Color(0xFF241215) else Color(0xFF141720))
                                                    .border(1.dp, if (hasError) Color(0xFFEF4444).copy(alpha = 0.4f) else Color(0xFF222735), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        modifier = Modifier.weight(1f),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        if (dep.isAlreadyInstalled) {
                                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(15.dp))
                                                        } else if (hasError) {
                                                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(15.dp))
                                                        } else if (dep.isRequired) {
                                                            Icon(Icons.Default.Download, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(15.dp))
                                                        } else {
                                                            Checkbox(
                                                                checked = dep.selectedToInstall,
                                                                onCheckedChange = { dep.selectedToInstall = it },
                                                                colors = CheckboxDefaults.colors(
                                                                    checkedColor = Color(0xFF10B981),
                                                                    checkmarkColor = Color.Black,
                                                                    uncheckedColor = Color(0xFF64748B)
                                                                ),
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        }

                                                        Column {
                                                            Text(
                                                                text = "${dep.project?.title ?: (dep.dependency.projectId ?: "Dependency")}${if (dep.version != null) " (v${dep.version!!.versionNumber})" else ""}",
                                                                color = if (hasError) Color(0xFFFCA5A5) else Color.White,
                                                                fontSize = 12.5.sp,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                            Text(
                                                                text = when {
                                                                    hasError -> "❌ ${dep.failureReason}"
                                                                    dep.isAlreadyInstalled -> "Already installed in instance (v${dep.installedVersion ?: ""})"
                                                                    dep.isRequired -> "Required by ${dep.requiredBy ?: project.title} — will be installed automatically"
                                                                    else -> "Optional dependency"
                                                                },
                                                                color = when {
                                                                    hasError -> Color(0xFFF87171)
                                                                    dep.isAlreadyInstalled -> Color(0xFF10B981)
                                                                    dep.isRequired -> Color(0xFF34D399)
                                                                    else -> Color(0xFF64748B)
                                                                },
                                                                fontSize = 11.sp
                                                            )
                                                        }
                                                    }

                                                    if (dep.isAlreadyInstalled) {
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text("INSTALLED", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    } else if (dep.isRequired) {
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(if (hasError) Color(0xFFEF4444).copy(alpha = 0.15f) else Color(0xFF10B981).copy(alpha = 0.15f))
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(if (hasError) "MISSING" else "REQUIRED", color = if (hasError) Color(0xFFEF4444) else Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // -------------------------------------------------------------
                                // D. ALREADY INSTALLED WARNING / UPDATE NOTIFICATION
                                // -------------------------------------------------------------
                                if (installedMod != null) {
                                    val isInstalledVersionOlder = selectedVersion != null &&
                                            selectedVersion!!.versionNumber != installedMod.version

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF121B28))
                                            .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                            .padding(12.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(20.dp))
                                            Column {
                                                Text(
                                                    text = if (isInstalledVersionOlder) "Update Available" else "Already Installed",
                                                    color = Color.White,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = if (isInstalledVersionOlder) {
                                                        "Installed: v${installedMod.version} → Selected: v${selectedVersion?.versionNumber}. Installing will update to the selected version."
                                                    } else {
                                                        "This mod is already present in ${activeInstance?.name ?: "the instance"} (${installedMod.fileName})."
                                                    },
                                                    color = Color(0xFF93C5FD),
                                                    fontSize = 11.5.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                // -------------------------------------------------------------
                                // E. ACTIVE INSTALLATION PROGRESS
                                // -------------------------------------------------------------
                                if (isInstalling || installComplete) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF101318))
                                            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                            .padding(12.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = installStage,
                                                    color = Color.White,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "${(installProgress * 100).toInt()}%",
                                                    color = Color(0xFF10B981),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            LinearProgressIndicator(
                                                progress = { installProgress },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(5.dp)
                                                    .clip(RoundedCornerShape(2.5.dp)),
                                                color = Color(0xFF10B981),
                                                trackColor = Color(0xFF141720)
                                            )
                                        }
                                    }
                                }

                                if (installError != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF1A1315))
                                            .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(8.dp))
                                            .padding(10.dp)
                                    ) {
                                        Text(installError!!, color = Color(0xFFFCA5A5), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // ==========================================
                    // 3. ACTION BAR (Cancel / Install / Done)
                    // ==========================================
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EzzButton(
                            text = if (installComplete) "Close" else "Cancel",
                            onClick = onDismiss,
                            variant = EzzButtonVariant.SECONDARY,
                            size = EzzButtonSize.MEDIUM,
                            enabled = !isInstalling
                        )

                        if (installComplete) {
                            EzzButton(
                                text = "Done",
                                onClick = onDismiss,
                                variant = EzzButtonVariant.PRIMARY,
                                size = EzzButtonSize.MEDIUM
                            )
                        } else {
                            val currentEval = selectedVersion?.let { resolutionResult?.candidateEvaluations?.get(it.id) }
                            val isSelectedCompatible = currentEval?.isCompatible == true
                            val hasAnyCompatible = resolutionResult?.hasCompatibleVersion == true
                            val hasUnresolvedRequiredDep = resolvedDependencies.any { it.isRequired && !it.isAlreadyInstalled && (it.version == null || it.failureReason != null) }
                            val totalModsToInstall = 1 + resolvedDependencies.count { it.selectedToInstall && it.version != null }
                            val canInstall = activeInstance != null &&
                                    selectedVersion != null &&
                                    !isInstalling &&
                                    isSelectedCompatible &&
                                    !hasUnresolvedRequiredDep &&
                                    compatibleVersions.isNotEmpty()

                            EzzButton(
                                text = when {
                                    isInstalling -> "Installing..."
                                    hasUnresolvedRequiredDep -> "Required Dependency Missing"
                                    !hasAnyCompatible -> "No Compatible Version"
                                    !isSelectedCompatible -> "Incompatible with Instance"
                                    totalModsToInstall > 1 -> "Install $totalModsToInstall Mods"
                                    installedMod != null -> "Reinstall / Update"
                                    else -> "Install Mod"
                                },
                                onClick = {
                                    val targetInst = activeInstance ?: return@EzzButton
                                    val targetVer = selectedVersion ?: return@EzzButton
                                    if (!isSelectedCompatible) return@EzzButton

                                    isInstalling = true
                                    installStage = "Starting download..."
                                    installProgress = 0.05f
                                    installError = null

                                    scope.launch {
                                        val result = viewModel.installModWithDependencies(
                                            instance = targetInst,
                                            project = project,
                                            mainVersion = targetVer,
                                            selectedDependencies = resolvedDependencies,
                                            onProgress = { stage, progress ->
                                                installStage = stage
                                                installProgress = progress
                                            }
                                        )

                                        isInstalling = false
                                        if (result.isSuccess) {
                                            installComplete = true
                                            installStage = "✓ ${project.title} installed successfully!"
                                            installProgress = 1f
                                        } else {
                                            installError = result.exceptionOrNull()?.message ?: "Installation failed"
                                        }
                                    }
                                },
                                icon = if (!isInstalling && canInstall) Icons.Default.Download else null,
                                variant = if (!isSelectedCompatible) EzzButtonVariant.SECONDARY else EzzButtonVariant.PRIMARY,
                                size = EzzButtonSize.MEDIUM,
                                enabled = canInstall
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> "${(mb * 10).toInt() / 10.0} MB"
        kb >= 1.0 -> "${(kb * 10).toInt() / 10.0} KB"
        else -> "$bytes B"
    }
}
