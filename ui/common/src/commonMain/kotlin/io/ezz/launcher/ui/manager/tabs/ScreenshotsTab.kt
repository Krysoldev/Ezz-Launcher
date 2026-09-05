package io.ezz.launcher.ui.manager.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import io.ezz.launcher.ui.components.EzzSearchField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LocalScreenshot
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.image.ImageDecoder
import io.ezz.launcher.ui.viewmodel.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScreenshotsTab(
    instance: Instance,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val screenshots by viewModel.manageScreenshots.collectAsState()
    var sortNewestFirst by remember { mutableStateOf(true) }

    var searchQuery by remember(instance.id) { mutableStateOf("") }

    val sortedScreenshots = remember(screenshots, sortNewestFirst) {
        if (sortNewestFirst) {
            screenshots.sortedByDescending { it.lastModified }
        } else {
            screenshots.sortedBy { it.lastModified }
        }
    }

    val filteredScreenshots = remember(sortedScreenshots, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) {
            sortedScreenshots
        } else {
            sortedScreenshots.filter { it.fileName.contains(q, ignoreCase = true) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EzzSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search screenshots...",
                modifier = Modifier.width(280.dp),
                onClear = { searchQuery = "" }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF101318))
                        .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(8.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (sortNewestFirst) Color(0xFF1A1E29) else Color.Transparent)
                            .border(1.dp, if (sortNewestFirst) Color.White else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable { sortNewestFirst = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Newest First", color = if (sortNewestFirst) Color.White else Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (!sortNewestFirst) Color(0xFF1A1E29) else Color.Transparent)
                            .border(1.dp, if (!sortNewestFirst) Color.White else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable { sortNewestFirst = false }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Oldest First", color = if (!sortNewestFirst) Color.White else Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                EzzButton(
                    text = "Refresh",
                    onClick = { viewModel.refreshManageData() },
                    icon = Icons.Default.Refresh,
                    variant = EzzButtonVariant.SECONDARY,
                    size = EzzButtonSize.SMALL
                )

                EzzButton(
                    text = "Open Folder",
                    onClick = {
                        val path = viewModel.pathProvider.getInstanceDirectory(instance.id).resolve(".minecraft").resolve("screenshots")
                        viewModel.platformBridge.openFolder(path)
                    },
                    icon = Icons.Default.FolderOpen,
                    variant = EzzButtonVariant.SECONDARY,
                    size = EzzButtonSize.SMALL
                )
            }
        }

        if (filteredScreenshots.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF101318))
                    .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(40.dp))
                    Text(
                        text = if (screenshots.isEmpty()) "No screenshots yet" else "No matching screenshots found",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (screenshots.isEmpty()) "Press F2 in-game to capture high-res Minecraft screenshots" else "Try searching with a different name.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                    if (screenshots.isNotEmpty() && searchQuery.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        EzzButton(
                            text = "Clear Search",
                            onClick = { searchQuery = "" },
                            icon = Icons.Default.Clear,
                            variant = EzzButtonVariant.SECONDARY,
                            size = EzzButtonSize.SMALL
                        )
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(220.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredScreenshots, key = { it.filePath }) { ss ->
                    ScreenshotCard(
                        screenshot = ss,
                        onClick = { viewModel.selectedScreenshotForViewer.value = ss },
                        onDelete = { viewModel.deleteScreenshot(ss.fileName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScreenshotCard(
    screenshot: LocalScreenshot,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(screenshot.lastModified) {
        SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()).format(Date(screenshot.lastModified))
    }

    var imageBitmap by remember(screenshot.filePath) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(screenshot.filePath) {
        withContext(Dispatchers.IO) {
            val file = File(screenshot.filePath)
            if (file.exists()) {
                imageBitmap = ImageDecoder.decodeFile(file)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF101318))
            .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Column {
            // Thumbnail Area with actual image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(135.dp)
                    .background(Color(0xFF141720)),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = imageBitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = screenshot.fileName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(36.dp))
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp).background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(15.dp))
                    }
                }
            }

            // Info Footer
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = screenshot.fileName,
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = "$dateStr • ${screenshot.fileSizeBytes / 1024} KB",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }
        }
    }
}
