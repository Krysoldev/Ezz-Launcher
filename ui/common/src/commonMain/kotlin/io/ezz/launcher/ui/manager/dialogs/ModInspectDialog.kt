package io.ezz.launcher.ui.manager.dialogs

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.ModrinthAsyncImage
import io.ezz.launcher.ui.viewmodel.AppViewModel
import kotlinx.coroutines.launch

private enum class ModInspectTab(val label: String) {
    DESCRIPTION("Description"),
    VERSIONS("Versions"),
    SCREENSHOTS("Screenshots")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModInspectDialog(
    projectHit: ModrinthProjectHit,
    instance: Instance,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    var activeTab by remember { mutableStateOf(ModInspectTab.DESCRIPTION) }
    var versions by remember { mutableStateOf<List<ModrinthVersion>>(emptyList()) }
    var isLoadingVersions by remember { mutableStateOf(true) }

    val downloadingProject by viewModel.modrinthDownloadingProject.collectAsState()
    val isInstalled = viewModel.isModInstalled(projectHit)

    LaunchedEffect(projectHit.projectId) {
        isLoadingVersions = true
        val loaders = if (instance.loaderType != LoaderType.VANILLA) listOf(instance.loaderType.name.lowercase()) else null
        val gameVersions = listOf(instance.minecraftVersion)
        versions = viewModel.modrinth.getProjectVersions(projectHit.projectId, loaders, gameVersions)
        if (versions.isEmpty()) {
            // Fallback to all versions
            versions = viewModel.modrinth.getProjectVersions(projectHit.projectId)
        }
        isLoadingVersions = false
    }

    var showInstaller by remember { mutableStateOf(false) }

    if (showInstaller) {
        io.ezz.launcher.ui.dialogs.InstallModDialog(
            project = projectHit,
            viewModel = viewModel,
            onDismiss = { showInstaller = false }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(780.dp)
                    .height(600.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF101318))
                    .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // block click through
                    )
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // TOP: Header
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
                                    url = projectHit.previewImageUrl,
                                    imageLoader = viewModel.imageLoader,
                                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                                    placeholderIcon = Icons.Default.Extension,
                                    contentScale = ContentScale.Crop
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = projectHit.title,
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (projectHit.author.isNotBlank()) {
                                            Text(
                                                text = "by ${projectHit.author}",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(13.dp))
                                            Text(
                                                text = "${projectHit.downloads} downloads",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(13.dp))
                                            Text(
                                                text = "${projectHit.follows} followers",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

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

                        // Categories FlowRow
                        if (projectHit.categories.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                projectHit.categories.forEach { cat ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF141720))
                                            .border(1.dp, Color(0xFF222735), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 7.dp, vertical = 3.dp)
                                    ) {
                                        Text(cat, color = Color(0xFFCBD5E1), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        // Sub-Tab Switcher
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF141720))
                                .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(8.dp))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ModInspectTab.values().forEach { tab ->
                                val isSelected = activeTab == tab
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) Color(0xFF1A1E29) else Color.Transparent)
                                        .border(1.dp, if (isSelected) Color.White else Color.Transparent, RoundedCornerShape(6.dp))
                                        .clickable { activeTab = tab }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tab.label,
                                        color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // MIDDLE: Content Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF141720))
                            .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(8.dp))
                            .padding(14.dp)
                    ) {
                        when (activeTab) {
                            ModInspectTab.DESCRIPTION -> {
                                val scrollState = rememberScrollState()
                                Column(
                                    modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "About this project",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = projectHit.description.ifBlank { "No summary available for this project." },
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Client & Server Compatibility",
                                        color = Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "• Client side: ${projectHit.clientSide}\n• Server side: ${projectHit.serverSide}",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }

                            ModInspectTab.VERSIONS -> {
                                if (isLoadingVersions) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                                    }
                                } else if (versions.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("No compatible versions found for this instance.", color = Color(0xFF94A3B8), fontSize = 13.sp)
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(versions, key = { it.id }) { ver ->
                                            VersionRowItem(
                                                version = ver,
                                                onInstall = {
                                                    showInstaller = true
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            ModInspectTab.SCREENSHOTS -> {
                                if (projectHit.gallery.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("No screenshots provided for this mod.", color = Color(0xFF94A3B8), fontSize = 13.sp)
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(projectHit.gallery) { galleryUrl ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(200.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFF101318))
                                                    .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(8.dp))
                                            ) {
                                                ModrinthAsyncImage(
                                                    url = galleryUrl,
                                                    imageLoader = viewModel.imageLoader,
                                                    modifier = Modifier.fillMaxSize(),
                                                    placeholderIcon = Icons.Default.Image,
                                                    contentScale = ContentScale.Fit
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // BOTTOM: Action Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EzzButton(
                            text = "Close",
                            onClick = onDismiss,
                            variant = EzzButtonVariant.SECONDARY,
                            size = EzzButtonSize.MEDIUM
                        )

                        if (isInstalled) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                    .border(1.dp, Color(0xFF10B981), RoundedCornerShape(6.dp))
                                    .clickable { showInstaller = true }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                                    Text("INSTALLED", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            val isDownloading = downloadingProject == projectHit.title
                            EzzButton(
                                text = if (isDownloading) "INSTALLING..." else "INSTALL MOD",
                                onClick = {
                                    showInstaller = true
                                },
                                icon = if (!isDownloading) Icons.Default.Download else null,
                                variant = EzzButtonVariant.PRIMARY,
                                size = EzzButtonSize.MEDIUM,
                                enabled = !isDownloading
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionRowItem(
    version: ModrinthVersion,
    onInstall: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF101318))
            .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = version.name.ifBlank { "Version ${version.versionNumber}" },
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "MC: ${version.gameVersions.joinToString(", ")} • ${version.loaders.joinToString(", ") { it.uppercase() }}",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }

            EzzButton(
                text = "Install",
                onClick = onInstall,
                variant = EzzButtonVariant.PRIMARY,
                size = EzzButtonSize.SMALL
            )
        }
    }
}
