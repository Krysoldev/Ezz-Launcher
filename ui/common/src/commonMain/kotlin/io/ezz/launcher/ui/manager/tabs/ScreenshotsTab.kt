package io.ezz.launcher.ui.manager.tabs

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LocalScreenshot
import io.ezz.launcher.ui.theme.EzzTheme
import io.ezz.launcher.ui.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScreenshotsTab(
    instance: Instance,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val colors = EzzTheme.colors
    val screenshots by viewModel.manageScreenshots.collectAsState()
    var sortNewestFirst by remember { mutableStateOf(true) }

    val sortedScreenshots = remember(screenshots, sortNewestFirst) {
        if (sortNewestFirst) {
            screenshots.sortedByDescending { it.lastModified }
        } else {
            screenshots.sortedBy { it.lastModified }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SCREENSHOTS GALLERY",
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "${screenshots.size} screenshot(s) saved",
                    color = colors.textMuted,
                    fontSize = 13.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { sortNewestFirst = !sortNewestFirst },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary)
                ) {
                    Text(if (sortNewestFirst) "Sort: Newest First" else "Sort: Oldest First", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { viewModel.refreshManageData() },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Refresh")
                }

                Button(
                    onClick = {
                        val path = viewModel.pathProvider.getInstanceDirectory(instance.id).resolve(".minecraft").resolve("screenshots")
                        viewModel.platformBridge.openFolder(path)
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceLight, contentColor = colors.textPrimary)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Folder")
                }
            }
        }

        if (sortedScreenshots.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(40.dp))
                    Text(text = "No screenshots yet", color = colors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Press F2 in-game to take screenshots", color = colors.textMuted, fontSize = 12.sp)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(220.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sortedScreenshots, key = { it.filePath }) { ss ->
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
    val colors = EzzTheme.colors
    val dateStr = remember(screenshot.lastModified) {
        SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(screenshot.lastModified))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.cardBackground)
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Column {
            // Thumbnail Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(Color(0xFF141414)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Image, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(36.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colors.danger, modifier = Modifier.size(16.dp))
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
                    color = colors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "$dateStr • ${screenshot.fileSizeBytes / 1024} KB",
                    color = colors.textMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}
