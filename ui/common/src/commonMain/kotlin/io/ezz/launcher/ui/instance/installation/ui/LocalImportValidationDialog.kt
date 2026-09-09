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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.instance.installation.model.ContentInstallationItem
import io.ezz.launcher.ui.instance.installation.model.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

enum class ImportStage(val stepNumber: Int, val label: String) {
    SELECTED(1, "File Selected"),
    READING(2, "Reading Archive"),
    VALIDATING(3, "Validating Manifest"),
    INSTALLING(4, "Installing"),
    COMPLETE(5, "Verified & Installed"),
    FAILED(-1, "Validation Failed")
}

@Composable
fun LocalImportValidationDialog(
    request: io.ezz.launcher.ui.instance.installation.model.LocalImportRequest,
    onDismiss: () -> Unit,
    onCompleted: () -> Unit = {}
) {
    LocalImportValidationDialog(
        file = request.file,
        contentType = request.contentType,
        instance = request.instance,
        targetDirectory = request.targetDirectory,
        onImportComplete = onCompleted,
        onDismiss = onDismiss
    )
}

@Composable
fun LocalImportValidationDialog(
    file: File,
    contentType: ContentType,
    instance: Instance,
    targetDirectory: File,
    onImportComplete: () -> Unit,
    onDismiss: () -> Unit
) {
    var stage by remember { mutableStateOf(ImportStage.SELECTED) }
    var detectedDetails by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0.15f) }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(150),
        label = "ImportProgress"
    )

    LaunchedEffect(file.absolutePath) {
        withContext(Dispatchers.IO) {
            try {
                // Step 1: Selected
                stage = ImportStage.SELECTED
                progress = 0.20f
                delay(200)

                // Step 2: Reading
                stage = ImportStage.READING
                progress = 0.40f

                if (!file.exists() || !file.isFile || file.length() <= 0) {
                    throw IllegalArgumentException("The selected file does not exist or is empty.")
                }

                // Step 3: Validating
                stage = ImportStage.VALIDATING
                progress = 0.65f
                delay(250)

                if (contentType == ContentType.MOD) {
                    if (!file.name.endsWith(".jar", ignoreCase = true)) {
                        throw IllegalArgumentException("Mod files must have a .jar extension.")
                    }
                    ZipFile(file).use { zip ->
                        val hasFabric = zip.getEntry("fabric.mod.json") != null
                        val hasForge = zip.getEntry("META-INF/mods.toml") != null || zip.getEntry("mcmod.info") != null
                        val hasQuilt = zip.getEntry("quilt.mod.json") != null

                        val loaderDetected = when {
                            hasFabric && hasQuilt -> "Fabric / Quilt"
                            hasFabric -> "Fabric"
                            hasQuilt -> "Quilt"
                            hasForge -> "Forge / NeoForge"
                            else -> "Standard Jar"
                        }
                        detectedDetails = "Mod archive verified ($loaderDetected)"
                    }
                } else if (contentType == ContentType.RESOURCE_PACK) {
                    ZipFile(file).use { zip ->
                        val hasMcmeta = zip.getEntry("pack.mcmeta") != null
                        if (!hasMcmeta) {
                            throw IllegalArgumentException("Missing pack.mcmeta in archive. Not a valid resource pack.")
                        }
                        detectedDetails = "Resource pack archive verified"
                    }
                } else if (contentType == ContentType.SHADER) {
                    ZipFile(file).use { zip ->
                        val hasShaders = zip.entries().asSequence().any { it.name.startsWith("shaders/") }
                        if (!hasShaders) {
                            detectedDetails = "Shader archive loaded"
                        } else {
                            detectedDetails = "Shader pack archive verified (GLSL shaders found)"
                        }
                    }
                }

                // Step 4: Installing
                stage = ImportStage.INSTALLING
                progress = 0.85f
                delay(200)

                if (!targetDirectory.exists()) targetDirectory.mkdirs()
                val targetFile = File(targetDirectory, file.name)
                file.copyTo(targetFile, overwrite = true)

                // Step 5: Complete
                progress = 1.0f
                stage = ImportStage.COMPLETE
                withContext(Dispatchers.Main) {
                    onImportComplete()
                }
            } catch (e: Throwable) {
                stage = ImportStage.FAILED
                errorMessage = e.message ?: "Failed to validate or import file."
            }
        }
    }

    Dialog(
        onDismissRequest = {
            if (stage == ImportStage.COMPLETE || stage == ImportStage.FAILED) onDismiss()
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
                    onClick = { if (stage == ImportStage.COMPLETE || stage == ImportStage.FAILED) onDismiss() }
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.50f)
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
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF141722))
                                    .border(1.dp, Color(0xFF222736), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(18.dp))
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Importing ${contentType.displayName}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("Target: ${instance.name}", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }
                        }

                        if (stage == ImportStage.COMPLETE || stage == ImportStage.FAILED) {
                            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Stepper
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
                        val steps = listOf(
                            ImportStage.READING to "Read",
                            ImportStage.VALIDATING to "Validate",
                            ImportStage.INSTALLING to "Install",
                            ImportStage.COMPLETE to "Verified"
                        )

                        steps.forEach { (step, label) ->
                            val isPassed = stage.stepNumber >= step.stepNumber && stage != ImportStage.FAILED
                            val isCurrent = stage == step
                            val color = when {
                                isPassed -> Color(0xFF10B981)
                                isCurrent -> Color(0xFF8B5CF6)
                                else -> Color(0xFF475569)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (isPassed) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
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

                    // Status Body
                    when (stage) {
                        ImportStage.FAILED -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                                    .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(14.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(24.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text("Import Failed", color = Color(0xFFFCA5A5), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                        Text(errorMessage ?: "Invalid file", color = Color(0xFFE2E8F0), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        ImportStage.COMPLETE -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF10B981).copy(alpha = 0.12f))
                                    .border(1.dp, Color(0xFF10B981).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(14.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(24.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("Import Complete", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                        Text(detectedDetails.ifBlank { "${file.name} was successfully added." }, color = Color(0xFFCBD5E1), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        else -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stage.label,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
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
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = Color(0xFF8B5CF6),
                                    trackColor = Color(0xFF141722)
                                )

                                Text(
                                    text = "${file.name} • ${ContentInstallationItem.formatBytes(file.length())}",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }

                    // Bottom CTA
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        EzzButton(
                            text = if (stage == ImportStage.COMPLETE) "Done" else "Close",
                            onClick = onDismiss,
                            variant = EzzButtonVariant.PRIMARY,
                            size = EzzButtonSize.MEDIUM
                        )
                    }
                }
            }
        }
    }
}
