package io.ezz.launcher.ui.manager.dialogs

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.packsmc.PacksMcPack
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.ModrinthAsyncImage
import io.ezz.launcher.ui.viewmodel.AppViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PacksMcInspectDialog(
    initialPack: PacksMcPack,
    instance: Instance,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    var pack by remember { mutableStateOf(initialPack) }
    var isLoadingDetails by remember { mutableStateOf(true) }
    var selectedImageIndex by remember { mutableStateOf(0) }

    LaunchedEffect(initialPack.id, initialPack.slug) {
        isLoadingDetails = true
        val fullDetails = viewModel.fetchPacksMcPackDetails(initialPack.slug.ifBlank { initialPack.id })
        if (fullDetails != null) {
            pack = fullDetails
        }
        isLoadingDetails = false
    }

    val galleryImages = remember(pack) {
        val list = mutableListOf<String>()
        pack.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { list.add(it) }
        pack.gallery.filter { it.isNotBlank() && !list.contains(it) }.forEach { list.add(it) }
        list
    }

    val authorName = pack.author?.displayName?.ifBlank { pack.author?.username } ?: pack.author?.username ?: "Unknown"
    val isAuthorVerified = pack.author?.verified == true

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(880.dp)
                    .fillMaxHeight(0.88f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0C0E12))
                    .border(1.dp, Color(0xFF1F2430), RoundedCornerShape(14.dp))
                    .clickable(enabled = false) {}
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF10131A))
                            .border(1.dp, Color(0xFF1A1F2B), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF181D26)),
                                contentAlignment = Alignment.Center
                            ) {
                                ModrinthAsyncImage(
                                    url = pack.thumbnailUrl,
                                    imageLoader = viewModel.imageLoader,
                                    modifier = Modifier.fillMaxSize(),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = pack.name.ifBlank { "Resource Pack" },
                                        color = Color.White,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (pack.isExclusive) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF8B5CF6).copy(alpha = 0.2f))
                                                .border(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Exclusive",
                                                color = Color(0xFFA78BFA),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    val res = pack.resolution
                                    if (!res.isNullOrBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF2563EB).copy(alpha = 0.2f))
                                                .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = res,
                                                color = Color(0xFF60A5FA),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "by $authorName",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp
                                    )
                                    if (isAuthorVerified) {
                                        Icon(
                                            imageVector = Icons.Default.Verified,
                                            contentDescription = "Verified Creator",
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(14.dp)
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
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Content Scroll Area
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        // Hero preview + Gallery
                        if (galleryImages.isNotEmpty()) {
                            val activeImg = galleryImages.getOrNull(selectedImageIndex) ?: galleryImages.first()
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF07090C))
                                    .border(1.dp, Color(0xFF1E232E), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                ModrinthAsyncImage(
                                    url = activeImg,
                                    imageLoader = viewModel.imageLoader,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }

                            if (galleryImages.size > 1) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(galleryImages.indices.toList()) { idx ->
                                        val img = galleryImages[idx]
                                        val isSelected = idx == selectedImageIndex
                                        Box(
                                            modifier = Modifier
                                                .size(width = 80.dp, height = 50.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF14171F))
                                                .border(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) Color.White else Color(0xFF232836),
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                                .clickable { selectedImageIndex = idx }
                                        ) {
                                            ModrinthAsyncImage(
                                                url = img,
                                                imageLoader = viewModel.imageLoader,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Stats Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF10131A))
                                .border(1.dp, Color(0xFF1E232E), RoundedCornerShape(8.dp))
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatItem(
                                icon = Icons.Default.Download,
                                label = "Downloads",
                                value = formatCount(pack.downloads)
                            )
                            StatItem(
                                icon = Icons.Default.Favorite,
                                label = "Likes",
                                value = formatCount(pack.likes)
                            )
                            StatItem(
                                icon = Icons.Default.Visibility,
                                label = "Views",
                                value = formatCount(pack.views)
                            )
                            StatItem(
                                icon = Icons.Default.Image,
                                label = "Resolution",
                                value = pack.resolution ?: "Default"
                            )
                        }

                        // Description
                        val desc = pack.description
                        if (!desc.isNullOrBlank()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "About",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = desc,
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                )
                            }
                        }

                        // Features & Tags
                        if (pack.features.isNotEmpty() || pack.tags.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Features & Tags",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    for (feature in pack.features) {
                                        BadgeChip(text = feature, bgColor = Color(0xFF1E293B), textColor = Color(0xFF93C5FD))
                                    }
                                    for (tag in pack.tags) {
                                        BadgeChip(text = "#$tag", bgColor = Color(0xFF131720), textColor = Color(0xFF94A3B8))
                                    }
                                }
                            }
                        }

                        // Supported Minecraft Versions
                        val allVersions = remember(pack) {
                            val vList = pack.mcVersions.toMutableList()
                            pack.versions.forEach { b ->
                                if (b.mcVersion.isNotBlank() && !vList.contains(b.mcVersion)) {
                                    vList.add(b.mcVersion)
                                }
                            }
                            vList
                        }
                        if (allVersions.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Supported Minecraft Versions",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    for (ver in allVersions) {
                                        val isCurrentInstance = ver.equals(instance.minecraftVersion, ignoreCase = true)
                                        BadgeChip(
                                            text = ver,
                                            bgColor = if (isCurrentInstance) Color(0xFF065F46) else Color(0xFF141720),
                                            textColor = if (isCurrentInstance) Color(0xFF34D399) else Color(0xFF94A3B8),
                                            borderColor = if (isCurrentInstance) Color(0xFF10B981) else Color(0xFF232938)
                                        )
                                    }
                                }
                            }
                        }

                        // License & Redistribution Information
                        val lic = pack.license
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF10131A))
                                .border(1.dp, Color(0xFF1E232E), RoundedCornerShape(8.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "License",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "License & Redistribution (${lic?.label ?: lic?.code ?: "Custom"})",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                LicensePill(
                                    label = "Redistribution",
                                    allowed = lic?.allowsRedistribution == true
                                )
                                LicensePill(
                                    label = "Attribution Required",
                                    allowed = lic?.requiresCredit == true,
                                    invertColor = true
                                )
                            }
                            Text(
                                text = "Downloads are provided directly via PacksMC. File re-hosting or scraping is strictly prohibited under PacksMC license.",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }

                        // Credits
                        if (pack.credits.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Credits",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                for (credit in pack.credits) {
                                    val cName = credit.displayName?.ifBlank { credit.username } ?: credit.username
                                    val cRole = credit.role
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "• $cName",
                                            color = Color(0xFFE2E8F0),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (!cRole.isNullOrBlank()) {
                                            Text(
                                                text = "($cRole)",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Action Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF10131A))
                            .border(1.dp, Color(0xFF1A1F2B), RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EzzButton(
                            text = "Import Downloaded ZIP",
                            icon = Icons.Default.FolderOpen,
                            variant = EzzButtonVariant.SECONDARY,
                            size = EzzButtonSize.MEDIUM,
                            onClick = {
                                viewModel.importLocalResourcePack(instance)
                            }
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EzzButton(
                                text = "Close",
                                variant = EzzButtonVariant.SECONDARY,
                                size = EzzButtonSize.MEDIUM,
                                onClick = onDismiss
                            )

                            EzzButton(
                                text = "Get Pack on PacksMC",
                                icon = Icons.Default.OpenInBrowser,
                                variant = EzzButtonVariant.PRIMARY,
                                size = EzzButtonSize.MEDIUM,
                                onClick = {
                                    viewModel.openPacksMcDownload(pack)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = value,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = label,
            color = Color(0xFF64748B),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun BadgeChip(
    text: String,
    bgColor: Color,
    textColor: Color,
    borderColor: Color = Color.Transparent
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun LicensePill(
    label: String,
    allowed: Boolean,
    invertColor: Boolean = false
) {
    val isGood = if (invertColor) !allowed else allowed
    val chipBg = if (isGood) Color(0xFF065F46).copy(alpha = 0.3f) else Color(0xFF7F1D1D).copy(alpha = 0.3f)
    val chipText = if (isGood) Color(0xFF34D399) else Color(0xFFF87171)
    val statusText = if (allowed) "Allowed" else "Prohibited"

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(chipBg)
            .border(1.dp, chipText.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = "$label: $statusText",
            color = chipText,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatCount(count: Long): String {
    return when {
        count >= 1_000_000 -> "${count / 1_000_000}M"
        count >= 1_000 -> "${count / 1_000}k"
        else -> count.toString()
    }
}
