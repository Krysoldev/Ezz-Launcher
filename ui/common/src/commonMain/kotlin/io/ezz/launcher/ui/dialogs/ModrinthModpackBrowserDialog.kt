package io.ezz.launcher.ui.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.ezz.launcher.core.model.modrinth.ModrinthProjectHit
import io.ezz.launcher.core.model.modrinth.ModrinthVersion
import io.ezz.launcher.ui.components.EzzBadge
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.EzzEmptyState
import io.ezz.launcher.ui.components.EzzSearchField
import io.ezz.launcher.ui.components.EzzTextField
import io.ezz.launcher.ui.components.ModrinthAsyncImage
import io.ezz.launcher.ui.components.PaginationBar
import io.ezz.launcher.ui.image.ModrinthImageLoader
import io.ezz.launcher.ui.viewmodel.AppViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

enum class ModpackSortOption(val apiValue: String, val label: String) {
    DOWNLOADS("downloads", "Popular"),
    FOLLOWS("follows", "Follows"),
    NEWEST("newest", "Newest"),
    RELEVANCE("relevance", "Relevance")
}

/**
 * Modern, studio-grade discovery browser for Modrinth Modpacks:
 * - Real-time debounced search.
 * - Filter pills for mod loaders (ALL, FABRIC, NEOFORGE, FORGE, QUILT).
 * - Sort options.
 * - Fast paginated browsing (20 hits/page).
 * - Responsive cards with cached artwork and download metrics.
 * - Detailed Modpack Inspection Modal with gallery and 1-click install.
 */
@Composable
fun ModrinthModpackBrowserDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val activeDownload by viewModel.activeDownloadState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedLoader by remember { mutableStateOf<String?>(null) } // null = All
    var selectedSort by remember { mutableStateOf(ModpackSortOption.DOWNLOADS) }

    var currentPage by remember { mutableStateOf(1) }
    val pageSize = 20
    var totalHits by remember { mutableStateOf(0) }
    var totalPages by remember { mutableStateOf(1) }

    var isLoading by remember { mutableStateOf(false) }
    var networkError by remember { mutableStateOf<String?>(null) }
    var modpacks by remember { mutableStateOf<List<ModrinthProjectHit>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    // Selected modpack for full detail modal
    var detailModpack by remember { mutableStateOf<ModrinthProjectHit?>(null) }
    // Selected modpack for direct install flow
    var modpackToInstall by remember { mutableStateOf<ModrinthProjectHit?>(null) }

    fun fetchModpacks(page: Int = currentPage) {
        searchJob?.cancel()
        searchJob = coroutineScope.launch {
            isLoading = true
            networkError = null
            delay(280) // 280ms debounce

            try {
                val loadersList = selectedLoader?.let { listOf(it) }
                val offset = (page - 1) * pageSize
                val response = viewModel.modrinth.searchModpacks(
                    query = searchQuery,
                    loaders = loadersList,
                    index = selectedSort.apiValue,
                    offset = offset,
                    limit = pageSize
                )
                modpacks = response.hits
                totalHits = response.totalHits
                totalPages = maxOf(1, ceil(response.totalHits.toDouble() / pageSize.toDouble()).toInt())
                currentPage = page
                isLoading = false
            } catch (e: Throwable) {
                networkError = e.message ?: "Failed to connect to Modrinth"
                isLoading = false
            }
        }
    }

    LaunchedEffect(searchQuery, selectedLoader, selectedSort) {
        currentPage = 1
        fetchModpacks(1)
    }

    Dialog(
        onDismissRequest = { if (activeDownload == null) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xDD07080A))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    if (activeDownload == null) onDismiss()
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(1040.dp)
                    .height(720.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF101318))
                    .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(12.dp))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Top Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "BROWSE MODPACKS",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            )

                            if (totalHits > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF141720))
                                        .border(1.dp, Color(0xFF222735), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$totalHits packs",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF64748B),
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .clickable { onDismiss() }
                        )
                    }

                    // 2. Search & Controls Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EzzSearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = "Search Modrinth modpacks...",
                            modifier = Modifier.weight(1f)
                        )

                        // Sort Selector
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF141720))
                                .border(1.dp, Color(0xFF222735), RoundedCornerShape(8.dp))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            ModpackSortOption.values().forEach { sort ->
                                val isSelected = selectedSort == sort
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) Color.White else Color.Transparent)
                                        .clickable { selectedSort = sort }
                                        .padding(horizontal = 9.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = sort.label,
                                        color = if (isSelected) Color.Black else Color(0xFF94A3B8),
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // 3. Mod Loader Filter Tabs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val loaders = listOf(
                            null to "ALL LOADERS",
                            "fabric" to "FABRIC",
                            "neoforge" to "NEOFORGE",
                            "forge" to "FORGE",
                            "quilt" to "QUILT"
                        )

                        loaders.forEach { (loaderId, label) ->
                            val isSelected = selectedLoader == loaderId
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0xFF1E2433) else Color(0xFF141720))
                                    .border(
                                        1.dp,
                                        if (isSelected) Color(0xFF3B4866) else Color(0xFF222735),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { selectedLoader = loaderId }
                                    .padding(horizontal = 11.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else Color(0xFF64748B),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    // 4. Modpack Grid / Loading / Error States
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (isLoading && modpacks.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        color = Color.White,
                                        strokeWidth = 2.5.dp
                                    )
                                    Text(
                                        text = "Loading modpacks from Modrinth...",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        } else if (networkError != null && modpacks.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = networkError ?: "Network error",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    EzzButton(
                                        text = "Retry",
                                        onClick = { fetchModpacks(currentPage) },
                                        icon = Icons.Default.Refresh,
                                        variant = EzzButtonVariant.SECONDARY,
                                        size = EzzButtonSize.SMALL
                                    )
                                }
                            }
                        } else if (modpacks.isEmpty()) {
                            EzzEmptyState(
                                icon = Icons.Default.Search,
                                title = "No Modpacks Found",
                                description = "Try another search term or change your mod loader filter.",
                                actionLabel = if (searchQuery.isNotEmpty()) "Clear Search" else null,
                                onAction = { searchQuery = "" },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 440.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(modpacks, key = { it.projectId }) { modpack ->
                                    ModpackCard(
                                        modpack = modpack,
                                        imageLoader = viewModel.imageLoader,
                                        onViewDetails = { detailModpack = modpack },
                                        onInstall = { modpackToInstall = modpack }
                                    )
                                }
                            }
                        }
                    }

                    // 5. Pagination Bar
                    if (totalPages > 1) {
                        PaginationBar(
                            currentPage = currentPage,
                            totalPages = totalPages,
                            totalHits = totalHits,
                            isLoading = isLoading,
                            onPrevious = {
                                if (currentPage > 1) {
                                    fetchModpacks(currentPage - 1)
                                }
                            },
                            onNext = {
                                if (currentPage < totalPages) {
                                    fetchModpacks(currentPage + 1)
                                }
                            }
                        )
                    }
                }

                // Detail Modal
                if (detailModpack != null) {
                    ModpackDetailDialog(
                        modpack = detailModpack!!,
                        viewModel = viewModel,
                        onDismiss = { detailModpack = null },
                        onInstallClick = {
                            val pack = detailModpack!!
                            detailModpack = null
                            modpackToInstall = pack
                        }
                    )
                }

                // Install Modal
                if (modpackToInstall != null) {
                    InstallModpackDialog(
                        modpack = modpackToInstall!!,
                        viewModel = viewModel,
                        onDismiss = { modpackToInstall = null }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModpackCard(
    modpack: ModrinthProjectHit,
    imageLoader: ModrinthImageLoader,
    onViewDetails: () -> Unit,
    onInstall: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isHovered) Color(0xFF141720) else Color(0xFF0C0E12))
            .border(1.dp, if (isHovered) Color(0xFF2E3648) else Color(0xFF1A1D26), RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Icon
            ModrinthAsyncImage(
                url = modpack.iconUrl,
                imageLoader = imageLoader,
                contentDescription = modpack.title,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF141720))
                    .border(1.dp, Color(0xFF222735), RoundedCornerShape(8.dp))
            )

            // Info Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = modpack.title,
                        color = Color.White,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "by ${modpack.author}",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = modpack.description,
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Stats & Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Metrics & Categories
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = formatNumber(modpack.downloads),
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Loaders
                        modpack.displayCategories.take(2).forEach { cat ->
                            EzzBadge(text = cat.uppercase())
                        }
                    }

                    // Action buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EzzButton(
                            text = "Details",
                            onClick = onViewDetails,
                            variant = EzzButtonVariant.SECONDARY,
                            size = EzzButtonSize.SMALL
                        )

                        EzzButton(
                            text = "Install",
                            onClick = onInstall,
                            icon = Icons.Default.Download,
                            variant = EzzButtonVariant.PRIMARY,
                            size = EzzButtonSize.SMALL
                        )
                    }
                }
            }
        }
    }
}

/**
 * Detailed view modal for exploring a modpack's full description, gallery, and version options.
 */
@Composable
private fun ModpackDetailDialog(
    modpack: ModrinthProjectHit,
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
    onInstallClick: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .width(620.dp)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF101318))
                .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(12.dp))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ModrinthAsyncImage(
                            url = modpack.iconUrl,
                            imageLoader = viewModel.imageLoader,
                            contentDescription = modpack.title,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF141720))
                                .border(1.dp, Color(0xFF222735), RoundedCornerShape(8.dp))
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = modpack.title,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Created by ${modpack.author}",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF64748B),
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable { onDismiss() }
                    )
                }

                // Stats Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF07080A))
                        .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetricStat(icon = Icons.Default.Download, label = "Downloads", value = formatNumber(modpack.downloads))
                    MetricStat(icon = Icons.Default.Favorite, label = "Follows", value = formatNumber(modpack.follows))
                    MetricStat(icon = Icons.Default.Info, label = "Versions", value = "${modpack.versions.size}")
                }

                // Gallery Previews
                if (modpack.gallery.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "GALLERY",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(modpack.gallery) { imgUrl ->
                                ModrinthAsyncImage(
                                    url = imgUrl,
                                    imageLoader = viewModel.imageLoader,
                                    modifier = Modifier
                                        .height(130.dp)
                                        .width(220.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF141720))
                                        .border(1.dp, Color(0xFF222735), RoundedCornerShape(6.dp))
                                )
                            }
                        }
                    }
                }

                // Description
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "DESCRIPTION",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF07080A))
                            .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(8.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = modpack.description,
                            color = Color.White,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                // Categories & Supported Loaders
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "CATEGORIES & LOADERS",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        modpack.displayCategories.forEach { cat ->
                            EzzBadge(text = cat.uppercase())
                        }
                        modpack.categories.forEach { cat ->
                            EzzBadge(text = cat)
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Bottom CTA
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    EzzButton(
                        text = "Close",
                        onClick = onDismiss,
                        variant = EzzButtonVariant.SECONDARY,
                        size = EzzButtonSize.MEDIUM,
                        modifier = Modifier.weight(1f)
                    )

                    EzzButton(
                        text = "Install Modpack",
                        onClick = onInstallClick,
                        icon = Icons.Default.Download,
                        variant = EzzButtonVariant.PRIMARY,
                        size = EzzButtonSize.MEDIUM,
                        modifier = Modifier.weight(1.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = value,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = label,
            color = Color(0xFF64748B),
            fontSize = 10.5.sp
        )
    }
}

@Composable
private fun InstallModpackDialog(
    modpack: ModrinthProjectHit,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    var nameInput by remember { mutableStateOf(modpack.title) }
    var versions by remember { mutableStateOf<List<ModrinthVersion>>(emptyList()) }
    var selectedVersion by remember { mutableStateOf<ModrinthVersion?>(null) }
    var isLoadingVersions by remember { mutableStateOf(true) }

    LaunchedEffect(modpack.projectId) {
        isLoadingVersions = true
        val list = viewModel.modrinth.getProjectVersions(modpack.projectId)
        versions = list
        selectedVersion = list.firstOrNull { it.featured } ?: list.firstOrNull()
        isLoadingVersions = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF101318))
                .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(12.dp))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Install Modpack",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF64748B),
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .clickable { onDismiss() }
                    )
                }

                // Summary Header
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModrinthAsyncImage(
                        url = modpack.iconUrl,
                        imageLoader = viewModel.imageLoader,
                        contentDescription = modpack.title,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF141720))
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = modpack.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "by ${modpack.author}",
                            color = Color(0xFF64748B),
                            fontSize = 11.5.sp
                        )
                    }
                }

                // Instance Name Input
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "INSTANCE NAME",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    EzzTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        placeholder = "Name your instance...",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Version Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "SELECT VERSION",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    if (isLoadingVersions) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Loading compatible versions...",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    } else if (versions.isEmpty()) {
                        Text(
                            text = "No compatible versions found.",
                            color = Color(0xFFFF8A80),
                            fontSize = 12.sp
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(versions.take(8)) { ver ->
                                val isSelected = selectedVersion?.id == ver.id
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) Color.White else Color(0xFF141720))
                                        .border(
                                            1.dp,
                                            if (isSelected) Color.White else Color(0xFF222735),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { selectedVersion = ver }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = ver.name.ifBlank { ver.versionNumber },
                                            color = if (isSelected) Color.Black else Color.White,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = ver.gameVersions.firstOrNull() ?: "",
                                            color = if (isSelected) Color(0xFF475569) else Color(0xFF64748B),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    EzzButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        variant = EzzButtonVariant.SECONDARY,
                        size = EzzButtonSize.MEDIUM,
                        modifier = Modifier.weight(1f)
                    )

                    EzzButton(
                        text = "Install Modpack",
                        onClick = {
                            viewModel.installModrinthModpack(
                                hit = modpack,
                                version = selectedVersion,
                                customName = nameInput.trim()
                            )
                            onDismiss()
                        },
                        enabled = nameInput.isNotBlank() && selectedVersion != null,
                        icon = Icons.Default.Download,
                        variant = EzzButtonVariant.PRIMARY,
                        size = EzzButtonSize.MEDIUM,
                        modifier = Modifier.weight(1.4f)
                    )
                }
            }
        }
    }
}

private fun formatNumber(count: Long): String {
    return when {
        count >= 1_000_000 -> "${count / 1_000_000}.${(count % 1_000_000) / 100_000}M"
        count >= 1_000 -> "${count / 1_000}k"
        else -> "$count"
    }
}
