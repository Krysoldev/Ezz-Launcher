package io.ezz.launcher.ui.instance.installation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.ezz.launcher.core.minecraft.mods.CurseForgeDependencyResolver
import io.ezz.launcher.core.model.curseforge.CurseForgeDependencyRelationType
import io.ezz.launcher.core.model.curseforge.CurseForgeFile
import io.ezz.launcher.core.model.curseforge.CurseForgeFileReleaseType
import io.ezz.launcher.core.model.curseforge.CurseForgeMod
import io.ezz.launcher.core.model.curseforge.CurseForgeModLoaderType
import io.ezz.launcher.core.model.curseforge.CurseForgeResolutionResult
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LocalMod
import io.ezz.launcher.core.network.curseforge.CurseForgeService
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.ModrinthAsyncImage
import io.ezz.launcher.ui.instance.installation.model.ContentInstallationItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ModDetailsTab(val label: String) {
    OVERVIEW("Overview"),
    FILES("Versions & Files"),
    DEPENDENCIES("Dependencies")
}

@Composable
fun CurseForgeModDetailsDialog(
    mod: CurseForgeMod,
    instance: Instance,
    viewModel: io.ezz.launcher.ui.viewmodel.AppViewModel,
    onDismiss: () -> Unit,
    onInstallFile: (CurseForgeFile?) -> Unit
) {
    val installedMods by viewModel.manageMods.collectAsState()
    val isInstalled = viewModel.isCurseForgeModInstalled(mod)
    CurseForgeModDetailsDialog(
        mod = mod,
        instance = instance,
        curseForgeService = viewModel.curseForge,
        installedMods = installedMods,
        imageLoader = viewModel.imageLoader,
        isAlreadyInstalled = isInstalled,
        onInstall = onInstallFile,
        onOpenUrl = { viewModel.platformBridge.openUrl(it) },
        onDismiss = onDismiss
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CurseForgeModDetailsDialog(
    mod: CurseForgeMod,
    instance: Instance,
    curseForgeService: CurseForgeService,
    installedMods: List<LocalMod>,
    imageLoader: io.ezz.launcher.ui.image.ModrinthImageLoader,
    isAlreadyInstalled: Boolean,
    onInstall: (CurseForgeFile?) -> Unit,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var activeTab by remember { mutableStateOf(ModDetailsTab.OVERVIEW) }
    var candidateFiles by remember { mutableStateOf<List<CurseForgeFile>>(emptyList()) }
    var isLoadingFiles by remember { mutableStateOf(true) }
    var resolutionResult by remember { mutableStateOf<CurseForgeResolutionResult?>(null) }
    var selectedFile by remember { mutableStateOf<CurseForgeFile?>(null) }
    var isResolvingCompatibility by remember { mutableStateOf(true) }

    val targetMc = instance.minecraftVersion.trim()
    val loaderName = instance.loaderType.name

    LaunchedEffect(mod.id) {
        isLoadingFiles = true
        isResolvingCompatibility = true
        try {
            val loaderType = CurseForgeModLoaderType.fromLoaderName(loaderName)
            val files = withContext(Dispatchers.IO) {
                curseForgeService.getModFiles(
                    modId = mod.id,
                    gameVersion = targetMc,
                    modLoaderType = loaderType,
                    pageSize = 30
                )
            }
            candidateFiles = files

            val resolution = withContext(Dispatchers.IO) {
                CurseForgeDependencyResolver.resolveCompatibility(
                    minecraftVersion = targetMc,
                    loader = loaderName,
                    installedMods = installedMods,
                    mod = mod,
                    candidateFiles = files
                )
            }
            resolutionResult = resolution
            selectedFile = resolution.recommendedFile ?: resolution.latestFile ?: files.firstOrNull()
        } catch (e: Throwable) {
            println("[CurseForgeModDetails] Error loading files: ${e.message}")
        } finally {
            isLoadingFiles = false
            isResolvingCompatibility = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .fillMaxHeight(0.88f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0C0E14))
                    .border(1.dp, Color(0xFF1B1F2C), RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // prevent dismissing
                    )
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. HERO BANNER & HEADER
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF1E2333), Color(0xFF0C0E14))
                                )
                            )
                    ) {
                        // Dismiss close button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0x66000000))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                        }

                        // Identity Row (Icon + Name + Author)
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            ModrinthAsyncImage(
                                url = mod.logo?.thumbnailUrl ?: mod.logo?.url,
                                imageLoader = imageLoader,
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFF2E364F), RoundedCornerShape(12.dp)),
                                placeholderIcon = Icons.Default.Extension,
                                contentScale = ContentScale.Crop
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = mod.name,
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.3).sp
                                    )

                                    if (isAlreadyInstalled) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                                .border(1.dp, Color(0xFF10B981), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
                                                Text("INSTALLED", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    val authorName = mod.authors.firstOrNull()?.name
                                    if (!authorName.isNullOrBlank()) {
                                        Text(text = "by $authorName", color = Color(0xFF94A3B8), fontSize = 12.5.sp)
                                    }
                                    Text(text = "•", color = Color(0xFF475569), fontSize = 12.sp)
                                    Text(
                                        text = "${ContentInstallationItem.formatBytes(mod.downloadCount.toLong())} downloads",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.5.sp
                                    )
                                }
                            }
                        }
                    }

                    // 2. ENVIRONMENT & COMPATIBILITY BAR
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF10131A))
                            .border(1.dp, Color(0xFF1A1F2E))
                            .padding(horizontal = 24.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Target instance environment
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Instance Target:", color = Color(0xFF64748B), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                EnvironmentBadge(text = "Minecraft $targetMc", color = Color.White)
                                EnvironmentBadge(text = loaderName, color = Color(0xFFA78BFA))
                            }

                            // Right: Real Compatibility Status
                            if (isResolvingCompatibility) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Text("Evaluating compatibility...", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                }
                            } else {
                                val isCompat = resolutionResult?.hasCompatibleVersion == true
                                val conflict = resolutionResult?.primaryConflictText
                                val hasDeps = resolutionResult?.recommendedFile?.dependencies?.any {
                                    it.relationTypeEnum == CurseForgeDependencyRelationType.REQUIRED_DEPENDENCY
                                } == true

                                val (badgeText, badgeColor, badgeBg) = when {
                                    conflict != null -> Triple("INCOMPATIBLE", Color(0xFFEF4444), Color(0xFFEF4444).copy(alpha = 0.15f))
                                    !isCompat -> Triple("NO COMPATIBLE VERSION", Color(0xFFF59E0B), Color(0xFFF59E0B).copy(alpha = 0.15f))
                                    hasDeps -> Triple("REQUIRES DEPENDENCIES", Color(0xFF38BDF8), Color(0xFF38BDF8).copy(alpha = 0.15f))
                                    else -> Triple("COMPATIBLE", Color(0xFF10B981), Color(0xFF10B981).copy(alpha = 0.15f))
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(badgeBg)
                                        .border(1.dp, badgeColor, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(badgeText, color = badgeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // 3. TABS SWITCHER
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0C0E14))
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ModDetailsTab.entries.forEach { tab ->
                            val isTabActive = activeTab == tab
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isTabActive) Color(0xFF1B1F2C) else Color.Transparent)
                                    .border(1.dp, if (isTabActive) Color(0xFF2E364F) else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable { activeTab = tab }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = tab.label,
                                    color = if (isTabActive) Color.White else Color(0xFF94A3B8),
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isTabActive) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    // 4. TAB BODY CONTENT
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        when (activeTab) {
                            ModDetailsTab.OVERVIEW -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Summary Section
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("ABOUT THIS MOD", color = Color(0xFF64748B), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = mod.summary.ifBlank { "No description available from CurseForge." },
                                            color = Color(0xFFE2E8F0),
                                            fontSize = 13.5.sp,
                                            lineHeight = 20.sp
                                        )
                                    }

                                    // Categories
                                    if (mod.categories.isNotEmpty()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("CATEGORIES", color = Color(0xFF64748B), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                mod.categories.forEach { cat ->
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(Color(0xFF141722))
                                                            .border(1.dp, Color(0xFF1F2436), RoundedCornerShape(6.dp))
                                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                                    ) {
                                                        Text(cat.name, color = Color(0xFFCBD5E1), fontSize = 11.5.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // External Links
                                    val links = mod.links
                                    val cfUrl = mod.links?.websiteUrl ?: if (mod.slug.isNotBlank()) "https://www.curseforge.com/minecraft/mc-mods/${mod.slug}" else null
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("EXTERNAL LINKS", color = Color(0xFF64748B), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            if (!cfUrl.isNullOrBlank()) {
                                                LinkButton(label = "CurseForge Page", onClick = { onOpenUrl(cfUrl) })
                                            }
                                            if (!links?.sourceUrl.isNullOrBlank()) {
                                                LinkButton(label = "Source Code", onClick = { onOpenUrl(links!!.sourceUrl!!) })
                                            }
                                            if (!links?.issuesUrl.isNullOrBlank()) {
                                                LinkButton(label = "Issue Tracker", onClick = { onOpenUrl(links!!.issuesUrl!!) })
                                            }
                                            if (!links?.wikiUrl.isNullOrBlank()) {
                                                LinkButton(label = "Wiki", onClick = { onOpenUrl(links!!.wikiUrl!!) })
                                            }
                                        }
                                    }

                                    // Recommended File Preview
                                    val rec = resolutionResult?.recommendedFile
                                    if (rec != null) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF10131A))
                                                .border(1.dp, Color(0xFF1E2436), RoundedCornerShape(10.dp))
                                                .padding(14.dp)
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("RECOMMENDED RELEASE", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    ReleaseTypeBadge(rec.releaseTypeEnum)
                                                }
                                                Text(
                                                    text = rec.displayName.ifBlank { rec.fileName },
                                                    color = Color.White,
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "${ContentInstallationItem.formatBytes(rec.fileLength)} • ${rec.fileDate.take(10)}",
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 11.5.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            ModDetailsTab.FILES -> {
                                if (isLoadingFiles) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                                    }
                                } else if (candidateFiles.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("No compatible files found for this Minecraft version.", color = Color(0xFF94A3B8), fontSize = 13.sp)
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(candidateFiles, key = { it.id }) { file ->
                                            val isSelected = selectedFile?.id == file.id
                                            val isRecommended = resolutionResult?.recommendedFile?.id == file.id

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) Color(0xFF1A1F2E) else Color(0xFF10131A))
                                                    .border(1.dp, if (isSelected) Color(0xFF8B5CF6) else Color(0xFF1B1F2C), RoundedCornerShape(8.dp))
                                                    .clickable { selectedFile = file }
                                                    .padding(12.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            Text(
                                                                text = file.displayName.ifBlank { file.fileName },
                                                                color = Color.White,
                                                                fontSize = 13.sp,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                            ReleaseTypeBadge(file.releaseTypeEnum)
                                                            if (isRecommended) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .clip(RoundedCornerShape(4.dp))
                                                                        .background(Color(0xFF10B981).copy(alpha = 0.2f))
                                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                ) {
                                                                    Text("RECOMMENDED", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                                }
                                                            }
                                                        }
                                                        Text(
                                                            text = "${ContentInstallationItem.formatBytes(file.fileLength)} • ${file.fileDate.take(10)} • ${ContentInstallationItem.formatBytes(file.downloadCount)} downloads",
                                                            color = Color(0xFF94A3B8),
                                                            fontSize = 11.sp
                                                        )
                                                    }

                                                    if (isSelected) {
                                                        Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = Color(0xFF8B5CF6), modifier = Modifier.size(20.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            ModDetailsTab.DEPENDENCIES -> {
                                val target = selectedFile ?: resolutionResult?.recommendedFile
                                if (target == null) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("Select a release file to inspect dependencies.", color = Color(0xFF94A3B8), fontSize = 13.sp)
                                    }
                                } else if (target.dependencies.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(32.dp))
                                            Text("No External Dependencies Required", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                            Text("This mod operates standalone on $loaderName.", color = Color(0xFF94A3B8), fontSize = 12.5.sp)
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(target.dependencies) { dep ->
                                            val relation = dep.relationTypeEnum
                                            val isReq = relation == CurseForgeDependencyRelationType.REQUIRED_DEPENDENCY ||
                                                    relation == CurseForgeDependencyRelationType.INCLUDE

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFF10131A))
                                                    .border(1.dp, Color(0xFF1B1F2C), RoundedCornerShape(8.dp))
                                                    .padding(12.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                        Text("Mod ID #${dep.modId}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                        Text("Relation: ${relation.displayName}", color = Color(0xFF94A3B8), fontSize = 11.5.sp)
                                                    }

                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(if (isReq) Color(0xFFEF4444).copy(alpha = 0.15f) else Color(0xFF38BDF8).copy(alpha = 0.15f))
                                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                                    ) {
                                                        Text(
                                                            text = if (isReq) "REQUIRED" else "OPTIONAL",
                                                            color = if (isReq) Color(0xFFF87171) else Color(0xFF38BDF8),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 5. BOTTOM ACTION DOCK
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF07080A))
                            .border(1.dp, Color(0xFF1B1F2C))
                            .padding(horizontal = 24.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // File summary text
                            val fileToInstall = selectedFile ?: resolutionResult?.recommendedFile
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                if (fileToInstall != null) {
                                    Text(
                                        text = "Selected: ${fileToInstall.displayName.ifBlank { fileToInstall.fileName }}",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${ContentInstallationItem.formatBytes(fileToInstall.fileLength)} • Automatically resolves dependencies",
                                        color = Color(0xFF64748B),
                                        fontSize = 11.sp
                                    )
                                } else {
                                    Text("No release selected", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                }
                            }

                            // Action buttons
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                EzzButton(
                                    text = "Close",
                                    onClick = onDismiss,
                                    variant = EzzButtonVariant.SECONDARY,
                                    size = EzzButtonSize.MEDIUM
                                )

                                EzzButton(
                                    text = if (isAlreadyInstalled) "Reinstall Mod" else "Install Mod",
                                    onClick = {
                                        onInstall(fileToInstall)
                                        onDismiss()
                                    },
                                    icon = Icons.Default.Download,
                                    variant = EzzButtonVariant.PRIMARY,
                                    size = EzzButtonSize.MEDIUM,
                                    enabled = fileToInstall != null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnvironmentBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF141722))
            .border(1.dp, Color(0xFF1F2436), RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(text = text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ReleaseTypeBadge(type: CurseForgeFileReleaseType) {
    val (color, bg) = when (type) {
        CurseForgeFileReleaseType.RELEASE -> Color(0xFF10B981) to Color(0xFF10B981).copy(alpha = 0.15f)
        CurseForgeFileReleaseType.BETA -> Color(0xFF38BDF8) to Color(0xFF38BDF8).copy(alpha = 0.15f)
        CurseForgeFileReleaseType.ALPHA -> Color(0xFFF59E0B) to Color(0xFFF59E0B).copy(alpha = 0.15f)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(type.displayName.uppercase(), color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LinkButton(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF141722))
            .border(1.dp, Color(0xFF1F2436), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xFF94A3B8), fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(12.dp))
    }
}
