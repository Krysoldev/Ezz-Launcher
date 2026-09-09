package io.ezz.launcher.ui.instance.installation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.ModrinthAsyncImage
import io.ezz.launcher.ui.instance.installation.model.ContentInstallationItem
import io.ezz.launcher.ui.instance.installation.model.DependencyInstallStatus
import io.ezz.launcher.ui.instance.installation.model.InstallationDependencyItem
import io.ezz.launcher.ui.instance.installation.model.InstallationStage

@Composable
fun ContentInstallationProgressModal(
    item: ContentInstallationItem,
    imageLoader: io.ezz.launcher.ui.image.ModrinthImageLoader? = null,
    onCancel: () -> Unit = {},
    onRetry: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = if (item.progress < 0f) 0f else item.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 200),
        label = "SmoothInstallProgress"
    )

    Dialog(
        onDismissRequest = {
            if (item.isFinished) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (item.isFinished) onDismiss() }
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.58f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0C0E14))
                    .border(1.dp, Color(0xFF1B1F2C), RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // TOP BAR: Identity & Close Icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ModrinthAsyncImage(
                                url = item.iconUrl,
                                imageLoader = imageLoader,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, Color(0xFF2E364F), RoundedCornerShape(10.dp)),
                                placeholderIcon = Icons.Default.Extension,
                                contentScale = ContentScale.Crop
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = item.name,
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Target Instance: ${item.instanceName} • ${item.versionName}",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        if (item.isFinished) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF141722))
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // STAGES STEPPER
                    StageStepperRow(currentStage = item.stage)

                    // LIVE PROGRESS BAR & STATS
                    when (item.stage) {
                        InstallationStage.COMPLETED -> {
                            SuccessBanner(item = item)
                        }
                        InstallationStage.FAILED -> {
                            ErrorBanner(item = item)
                        }
                        InstallationStage.CANCELLED -> {
                            CancelledBanner()
                        }
                        else -> {
                            ActiveProgressBlock(
                                item = item,
                                animatedProgress = animatedProgress
                            )
                        }
                    }

                    // DEPENDENCY TREE (if any dependencies exist)
                    if (item.dependencies.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "DEPENDENCY CHAIN (${item.dependencies.size})",
                                color = Color(0xFF64748B),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF10131A))
                                    .border(1.dp, Color(0xFF1A1F2E), RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(item.dependencies, key = { it.modId }) { dep ->
                                    DependencyRow(dep = dep)
                                }
                            }
                        }
                    }

                    // BOTTOM BUTTONS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when {
                            item.stage == InstallationStage.COMPLETED -> {
                                EzzButton(
                                    text = "Done",
                                    onClick = onDismiss,
                                    variant = EzzButtonVariant.PRIMARY,
                                    size = EzzButtonSize.MEDIUM
                                )
                            }
                            item.stage == InstallationStage.FAILED -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    EzzButton(
                                        text = "Dismiss",
                                        onClick = onDismiss,
                                        variant = EzzButtonVariant.SECONDARY,
                                        size = EzzButtonSize.MEDIUM
                                    )
                                    if (onRetry != null) {
                                        EzzButton(
                                            text = "Retry Installation",
                                            icon = Icons.Default.Refresh,
                                            onClick = onRetry,
                                            variant = EzzButtonVariant.PRIMARY,
                                            size = EzzButtonSize.MEDIUM
                                        )
                                    }
                                }
                            }
                            else -> {
                                EzzButton(
                                    text = "Cancel Installation",
                                    onClick = onCancel,
                                    variant = EzzButtonVariant.DANGER,
                                    size = EzzButtonSize.SMALL
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
private fun StageStepperRow(currentStage: InstallationStage) {
    val stages = listOf(
        InstallationStage.VALIDATING to "Validate",
        InstallationStage.RESOLVING_DEPENDENCIES to "Dependencies",
        InstallationStage.DOWNLOADING_MAIN to "Download",
        InstallationStage.INSTALLING to "Install",
        InstallationStage.VERIFYING to "Verify"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF10131A))
            .border(1.dp, Color(0xFF1A1F2E), RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        stages.forEachIndexed { index, (stage, label) ->
            val isPassed = currentStage.stepNumber > stage.stepNumber || currentStage == InstallationStage.COMPLETED
            val isCurrent = currentStage.stepNumber == stage.stepNumber
            val color = when {
                isPassed -> Color(0xFF10B981)
                isCurrent -> Color(0xFF8B5CF6)
                else -> Color(0xFF475569)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (isPassed || isCurrent) color.copy(alpha = 0.2f) else Color.Transparent)
                        .border(1.dp, color, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPassed) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = color, modifier = Modifier.size(11.dp))
                    } else {
                        Text(
                            text = "${index + 1}",
                            color = color,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = label,
                    color = if (isPassed || isCurrent) Color.White else Color(0xFF64748B),
                    fontSize = 11.5.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ActiveProgressBlock(
    item: ContentInstallationItem,
    animatedProgress: Float
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF8B5CF6),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = item.stage.label,
                    color = Color(0xFFE2E8F0),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "${(animatedProgress * 100).toInt()}%",
                color = Color(0xFF8B5CF6),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = Color(0xFF8B5CF6),
            trackColor = Color(0xFF141722)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${item.downloadedFormatted} / ${item.totalFormatted}",
                color = Color(0xFF94A3B8),
                fontSize = 11.5.sp
            )
            if (item.speedText.isNotBlank()) {
                Text(
                    text = item.speedText,
                    color = Color(0xFF10B981),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun DependencyRow(dep: InstallationDependencyItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF0C0E14))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("•", color = Color(0xFF8B5CF6), fontSize = 14.sp)
            Text(dep.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            if (dep.versionText != null) {
                Text(dep.versionText, color = Color(0xFF64748B), fontSize = 11.sp)
            }
        }

        val (statusText, statusColor) = when (dep.status) {
            DependencyInstallStatus.VERIFIED_INSTALLED -> "Already Installed ✓" to Color(0xFF10B981)
            DependencyInstallStatus.COMPLETED -> "Installed ✓" to Color(0xFF10B981)
            DependencyInstallStatus.DOWNLOADING -> "Downloading..." to Color(0xFF38BDF8)
            DependencyInstallStatus.QUEUED -> "Queued" to Color(0xFF94A3B8)
            DependencyInstallStatus.FAILED -> "Failed" to Color(0xFFEF4444)
            DependencyInstallStatus.OPTIONAL_SKIPPED -> "Skipped" to Color(0xFF64748B)
        }

        Text(statusText, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SuccessBanner(item: ContentInstallationItem) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF10B981).copy(alpha = 0.12f))
            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "Installed Successfully",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${item.name} and all required dependencies are ready to play.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.5.sp
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(item: ContentInstallationItem) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFEF4444).copy(alpha = 0.12f))
            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Installation Failed",
                    color = Color(0xFFFCA5A5),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = item.errorSummary ?: "An unexpected error occurred during installation.",
                    color = Color(0xFFE2E8F0),
                    fontSize = 12.sp
                )
                Text(
                    text = "Partial files have been safely rolled back. Your instance files are intact.",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.5.sp
                )
            }
        }
    }
}

@Composable
private fun CancelledBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF141722))
            .border(1.dp, Color(0xFF2E364F), RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        Text("Installation was cancelled by user.", color = Color(0xFF94A3B8), fontSize = 12.5.sp)
    }
}
