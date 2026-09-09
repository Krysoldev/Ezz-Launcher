package io.ezz.launcher.ui.manager.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.packsmc.PacksMcBrowseState
import io.ezz.launcher.core.model.packsmc.PacksMcPack
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.EzzSearchField
import io.ezz.launcher.ui.components.ModrinthAsyncImage
import io.ezz.launcher.ui.viewmodel.AppViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrowsePacksMcView(
    instance: Instance,
    viewModel: AppViewModel,
    browseState: PacksMcBrowseState,
    onInspect: (PacksMcPack) -> Unit,
    onDownload: (PacksMcPack) -> Unit
) {
    val apiKey by viewModel.packsMcApiKey.collectAsState()
    var searchQuery by remember(instance.id) { mutableStateOf(browseState.searchQuery) }
    var filterByCurrentVersion by remember(instance.id) { mutableStateOf(false) }
    var showApiKeyModal by remember { mutableStateOf(false) }

    val resolutions = listOf("ALL", "16x", "32x", "64x", "128x", "256x", "512x")

    // Client-side version filter on returned pack metadata
    val displayedPacks = remember(browseState.items, filterByCurrentVersion, instance.minecraftVersion) {
        if (!filterByCurrentVersion) {
            browseState.items
        } else {
            val target = instance.minecraftVersion.trim()
            browseState.items.filter { pack ->
                pack.mcVersions.isEmpty() && pack.versions.isEmpty() ||
                pack.mcVersions.any { it.equals(target, ignoreCase = true) } ||
                pack.versions.any { it.mcVersion.equals(target, ignoreCase = true) }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Search & Filter Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EzzSearchField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.searchPacksMc(query = it, debounceMs = 350L)
                },
                placeholder = "Search PacksMC (e.g. Bare Bones, Faithful)...",
                modifier = Modifier.weight(1f),
                onClear = {
                    searchQuery = ""
                    viewModel.searchPacksMc(query = "")
                }
            )

            // Sort Options (PacksMC API contract: recent, downloads, likes)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF101318))
                    .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(8.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "recent" to "Recent",
                    "downloads" to "Downloads",
                    "likes" to "Most Liked"
                ).forEach { (sortKey, sortLabel) ->
                    val isSelected = browseState.selectedSort == sortKey
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Color(0xFF1A1E29) else Color.Transparent)
                            .border(1.dp, if (isSelected) Color.White else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable { viewModel.searchPacksMc(sort = sortKey) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = sortLabel,
                            color = if (isSelected) Color.White else Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Secondary Toolbar: Resolution Filters & Version Checkbox & API Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Resolution selectors
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Resolution:",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.5.sp
                )
                resolutions.forEach { resKey ->
                    val isSelected = (browseState.selectedResolution == null && resKey == "ALL") ||
                            (browseState.selectedResolution == resKey)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) Color(0xFF1E2433) else Color(0xFF101318))
                            .border(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF1A1D26), RoundedCornerShape(4.dp))
                            .clickable {
                                val targetRes = if (resKey == "ALL") null else resKey
                                viewModel.searchPacksMc(resolution = targetRes)
                            }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = resKey,
                            color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Client-side Version Filter Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Checkbox(
                    checked = filterByCurrentVersion,
                    onCheckedChange = { filterByCurrentVersion = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFF10B981),
                        uncheckedColor = Color(0xFF475569),
                        checkmarkColor = Color.Black
                    ),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Match MC ${instance.minecraftVersion}",
                    color = if (filterByCurrentVersion) Color(0xFF34D399) else Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = if (filterByCurrentVersion) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.clickable { filterByCurrentVersion = !filterByCurrentVersion }
                )

                Spacer(modifier = Modifier.width(8.dp))

                // API Key status indicator / setup button
                if (apiKey.isNullOrBlank()) {
                    EzzButton(
                        text = "Setup API Key",
                        icon = Icons.Default.Key,
                        variant = EzzButtonVariant.SECONDARY,
                        size = EzzButtonSize.SMALL,
                        onClick = { showApiKeyModal = true }
                    )
                }
            }
        }

        // Rate Limit Warning Banner if active
        if (browseState.isRateLimited) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF78350F).copy(alpha = 0.3f))
                    .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Rate Limited",
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "PacksMC API rate limit reached. Retrying automatically after backoff window...",
                        color = Color(0xFFFDE68A),
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Results Section
        if (apiKey.isNullOrBlank()) {
            // Missing API Key State Card
            PacksMcApiKeyRequiredCard(
                onConfigureClick = { showApiKeyModal = true }
            )
        } else if (browseState.isLoading && browseState.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                    Text("Fetching resource packs from PacksMC...", color = Color(0xFF94A3B8), fontSize = 13.sp)
                }
            }
        } else if (browseState.error != null) {
            // Error State
            val isAuthError = browseState.error?.contains("401", ignoreCase = true) == true ||
                    browseState.error?.contains("unauthenticated", ignoreCase = true) == true ||
                    browseState.error?.contains("unauthorized", ignoreCase = true) == true

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF101318))
                    .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = if (isAuthError) "PacksMC Authentication Error" else (browseState.error ?: "Error loading PacksMC catalog"),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = if (isAuthError) "Your API key is invalid, missing or expired." else "Please check your network connection and retry.",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        EzzButton(
                            text = "Retry",
                            icon = Icons.Default.Refresh,
                            onClick = { viewModel.searchPacksMc(resetPagination = true) },
                            variant = EzzButtonVariant.SECONDARY,
                            size = EzzButtonSize.SMALL
                        )
                        if (isAuthError) {
                            EzzButton(
                                text = "Update API Key",
                                icon = Icons.Default.Key,
                                onClick = { showApiKeyModal = true },
                                variant = EzzButtonVariant.PRIMARY,
                                size = EzzButtonSize.SMALL
                            )
                        }
                    }
                }
            }
        } else if (displayedPacks.isEmpty()) {
            // Empty Results
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF101318))
                    .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(36.dp))
                    Text("No resource packs found", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        text = if (filterByCurrentVersion) {
                            "No packs matched your search for Minecraft ${instance.minecraftVersion}. Try disabling the version filter."
                        } else {
                            "Try a different search query or change your resolution filter."
                        },
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                    if (searchQuery.isNotBlank() || filterByCurrentVersion || browseState.selectedResolution != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        EzzButton(
                            text = "Reset Filters",
                            onClick = {
                                searchQuery = ""
                                filterByCurrentVersion = false
                                viewModel.searchPacksMc(query = "", resolution = null, resetPagination = true)
                            },
                            icon = Icons.Default.Clear,
                            variant = EzzButtonVariant.SECONDARY,
                            size = EzzButtonSize.SMALL
                        )
                    }
                }
            }
        } else {
            // Packs List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(displayedPacks, key = { it.id.ifBlank { it.slug } }) { pack ->
                    PacksMcCard(
                        pack = pack,
                        instance = instance,
                        viewModel = viewModel,
                        onInspect = { onInspect(pack) },
                        onDownload = { onDownload(pack) }
                    )
                }

                // Load More / Pagination for recent sort
                if (browseState.selectedSort == "recent" && browseState.nextCursor != null) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (browseState.isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                EzzButton(
                                    text = "Load More Packs",
                                    onClick = { viewModel.loadMorePacksMc() },
                                    variant = EzzButtonVariant.SECONDARY,
                                    size = EzzButtonSize.MEDIUM
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal to configure PacksMC API key in-place
    if (showApiKeyModal) {
        PacksMcApiKeyModal(
            currentKey = apiKey ?: "",
            onSave = { newKey, onResult ->
                viewModel.savePacksMcApiKey(newKey, onResult)
            },
            onDismiss = { showApiKeyModal = false }
        )
    }
}

@Composable
private fun PacksMcCard(
    pack: PacksMcPack,
    instance: Instance,
    viewModel: AppViewModel,
    onInspect: () -> Unit,
    onDownload: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF101318))
            .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(10.dp))
            .clickable { onInspect() }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF161A22)),
                    contentAlignment = Alignment.Center
                ) {
                    ModrinthAsyncImage(
                        url = pack.thumbnailUrl,
                        imageLoader = viewModel.imageLoader,
                        modifier = Modifier.fillMaxSize(),
                        placeholderIcon = Icons.Default.Palette,
                        contentScale = ContentScale.Crop,
                        shape = RoundedCornerShape(6.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = pack.name.ifBlank { "Resource Pack" },
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )

                        if (pack.isExclusive) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFF8B5CF6).copy(alpha = 0.2f))
                                    .border(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "Exclusive",
                                    color = Color(0xFFA78BFA),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        val res = pack.resolution
                        if (!res.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFF2563EB).copy(alpha = 0.2f))
                                    .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = res,
                                    color = Color(0xFF60A5FA),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    val desc = pack.description
                    if (!desc.isNullOrBlank()) {
                        Text(
                            text = desc,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            maxLines = 2
                        )
                    }

                    // Metadata Badges Row: Author, Downloads, Likes, Versions
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val authorName = pack.author?.displayName?.ifBlank { pack.author?.username } ?: pack.author?.username ?: "Unknown"
                        val isAuthorVerified = pack.author?.verified == true

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "by $authorName",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (isAuthorVerified) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Verified Creator",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = formatShortNumber(pack.downloads),
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = formatShortNumber(pack.likes),
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }

                        // Version badge if current instance is supported
                        val isCurrentSupported = pack.mcVersions.any { it.equals(instance.minecraftVersion, ignoreCase = true) } ||
                                pack.versions.any { it.mcVersion.equals(instance.minecraftVersion, ignoreCase = true) }
                        if (isCurrentSupported) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFF065F46).copy(alpha = 0.3f))
                                    .border(1.dp, Color(0xFF10B981).copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "MC ${instance.minecraftVersion}",
                                    color = Color(0xFF34D399),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EzzButton(
                    text = "Details",
                    onClick = onInspect,
                    variant = EzzButtonVariant.SECONDARY,
                    size = EzzButtonSize.SMALL
                )

                EzzButton(
                    text = "Get Pack",
                    icon = Icons.Default.OpenInBrowser,
                    onClick = onDownload,
                    variant = EzzButtonVariant.PRIMARY,
                    size = EzzButtonSize.SMALL
                )
            }
        }
    }
}

@Composable
private fun PacksMcApiKeyRequiredCard(
    onConfigureClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF101318))
            .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(10.dp))
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E2433)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = "API Key Required",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(24.dp)
                )
            }

            Text(
                text = "PacksMC API Key Required",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "PacksMC requires an API key for catalog browsing and search. Add your free key to unlock thousands of custom resource packs.",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            EzzButton(
                text = "Enter PacksMC API Key",
                icon = Icons.Default.Key,
                onClick = onConfigureClick,
                variant = EzzButtonVariant.PRIMARY,
                size = EzzButtonSize.MEDIUM
            )
        }
    }
}

@Composable
private fun PacksMcApiKeyModal(
    currentKey: String,
    onSave: (String, (Boolean, String?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var keyInput by remember { mutableStateOf(currentKey) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        Box(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0D0F14))
                .border(1.dp, Color(0xFF222736), RoundedCornerShape(12.dp))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Configure PacksMC API Key",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Enter your personal PacksMC API token starting with pmc_. It will be stored securely in your local vault.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp
                )

                OutlinedTextField(
                    value = keyInput,
                    onValueChange = {
                        keyInput = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("pmc_...", color = Color(0xFF64748B)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color(0xFF222736),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF141720),
                        unfocusedContainerColor = Color(0xFF141720)
                    )
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EzzButton(
                        text = "Cancel",
                        variant = EzzButtonVariant.SECONDARY,
                        size = EzzButtonSize.SMALL,
                        onClick = onDismiss
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    EzzButton(
                        text = if (isSubmitting) "Verifying..." else "Save & Connect",
                        variant = EzzButtonVariant.PRIMARY,
                        size = EzzButtonSize.SMALL,
                        onClick = {
                            if (keyInput.isBlank()) {
                                errorMessage = "API key cannot be empty"
                                return@EzzButton
                            }
                            isSubmitting = true
                            errorMessage = null
                            onSave(keyInput) { success, err ->
                                isSubmitting = false
                                if (success) {
                                    onDismiss()
                                } else {
                                    errorMessage = err ?: "Failed to verify key"
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun formatShortNumber(n: Long): String {
    return when {
        n >= 1_000_000 -> "${n / 1_000_000}M"
        n >= 1_000 -> "${n / 1_000}k"
        else -> n.toString()
    }
}
