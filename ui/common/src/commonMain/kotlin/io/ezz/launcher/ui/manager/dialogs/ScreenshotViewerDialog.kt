package io.ezz.launcher.ui.manager.dialogs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.ezz.launcher.core.model.instance.LocalScreenshot
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.image.ImageDecoder
import io.ezz.launcher.ui.viewmodel.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScreenshotViewerDialog(
    screenshot: LocalScreenshot,
    allScreenshots: List<LocalScreenshot>,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val currentIndex = remember(screenshot, allScreenshots) { allScreenshots.indexOfFirst { it.fileName == screenshot.fileName } }

    val currentScreenshot = if (currentIndex in allScreenshots.indices) {
        allScreenshots[currentIndex]
    } else {
        screenshot
    }

    var imageBitmap by remember(currentScreenshot.filePath) { mutableStateOf<ImageBitmap?>(null) }
    var isLoadingImage by remember(currentScreenshot.filePath) { mutableStateOf(true) }

    LaunchedEffect(currentScreenshot.filePath) {
        isLoadingImage = true
        withContext(Dispatchers.IO) {
            val file = File(currentScreenshot.filePath)
            if (file.exists()) {
                imageBitmap = ImageDecoder.decodeFile(file)
            }
        }
        isLoadingImage = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.Escape -> {
                                onDismiss()
                                true
                            }
                            Key.DirectionLeft -> {
                                if (currentIndex > 0) {
                                    viewModel.selectedScreenshotForViewer.value = allScreenshots[currentIndex - 1]
                                }
                                true
                            }
                            Key.DirectionRight -> {
                                if (currentIndex in 0 until allScreenshots.lastIndex) {
                                    viewModel.selectedScreenshotForViewer.value = allScreenshots[currentIndex + 1]
                                }
                                true
                            }
                            else -> false
                        }
                    } else false
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Bar: Info + Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = currentScreenshot.fileName,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${SimpleDateFormat("MMM d, yyyy • HH:mm:ss", Locale.getDefault()).format(Date(currentScreenshot.lastModified))} • ${currentScreenshot.fileSizeBytes / 1024} KB • (${currentIndex + 1} of ${allScreenshots.size})",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        EzzButton(
                            text = "Copy Path",
                            onClick = {
                                viewModel.platformBridge.copyToClipboard(currentScreenshot.filePath)
                            },
                            icon = Icons.Default.ContentCopy,
                            variant = EzzButtonVariant.SECONDARY,
                            size = EzzButtonSize.SMALL
                        )

                        EzzButton(
                            text = "Open Folder",
                            onClick = {
                                val file = File(currentScreenshot.filePath)
                                if (file.exists() && file.parentFile != null) {
                                    viewModel.platformBridge.openFolder(file.parentFile.absolutePath.toPath())
                                }
                            },
                            icon = Icons.Default.FolderOpen,
                            variant = EzzButtonVariant.SECONDARY,
                            size = EzzButtonSize.SMALL
                        )

                        EzzButton(
                            text = "Delete",
                            onClick = {
                                viewModel.deleteScreenshot(currentScreenshot.fileName)
                                if (allScreenshots.size <= 1) {
                                    onDismiss()
                                } else {
                                    val nextIdx = (currentIndex).coerceAtMost(allScreenshots.size - 2)
                                    viewModel.selectedScreenshotForViewer.value = allScreenshots.filterNot { it.fileName == currentScreenshot.fileName }[nextIdx]
                                }
                            },
                            icon = Icons.Default.Delete,
                            variant = EzzButtonVariant.DANGER,
                            size = EzzButtonSize.SMALL
                        )

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp).background(Color.White.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Middle: Screenshot Image View
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = imageBitmap
                    if (isLoadingImage) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
                    } else if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = currentScreenshot.fileName,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(60.dp))
                            Text("Unable to render image preview", color = Color(0xFF94A3B8), fontSize = 13.sp)
                        }
                    }

                    // Navigation arrows
                    if (currentIndex > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                .clickable {
                                    viewModel.selectedScreenshotForViewer.value = allScreenshots[currentIndex - 1]
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }

                    if (currentIndex in 0 until allScreenshots.lastIndex) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                .clickable {
                                    viewModel.selectedScreenshotForViewer.value = allScreenshots[currentIndex + 1]
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // Bottom Hint
                Text(
                    text = "Press Left / Right arrows to navigate • ESC to close",
                    color = Color(0xFF64748B),
                    fontSize = 11.5.sp
                )
            }
        }
    }
}
