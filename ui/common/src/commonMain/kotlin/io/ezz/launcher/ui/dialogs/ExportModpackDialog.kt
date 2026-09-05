package io.ezz.launcher.ui.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.modrinth.MrpackExportOptions
import io.ezz.launcher.ui.components.EzzBadge
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.EzzTextField
import io.ezz.launcher.ui.viewmodel.AppViewModel
import okio.Path.Companion.toOkioPath
import org.jetbrains.skia.Image as SkiaImage
import java.io.File

private enum class ExportDialogStage {
    CONFIGURE,
    EXPORTING,
    SUCCESS
}

/**
 * Modern, studio-grade Modal Dialog for exporting an Instance into a valid .mrpack archive:
 * - Shows instance metadata.
 * - Custom pack metadata (name, summary, version).
 * - Granular content inclusion toggles.
 * - Live packaging progress.
 * - Success state with direct file location access.
 */
@Composable
fun ExportModpackDialog(
    instance: Instance,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    var stage by remember { mutableStateOf(ExportDialogStage.CONFIGURE) }
    var packName by remember { mutableStateOf(instance.name) }
    var packSummary by remember { mutableStateOf("Custom modpack exported from Ezz Launcher") }
    var versionId by remember { mutableStateOf("1.0.0") }

    var includeConfigs by remember { mutableStateOf(true) }
    var includeMods by remember { mutableStateOf(true) }
    var includeResourcePacks by remember { mutableStateOf(true) }
    var includeShaderPacks by remember { mutableStateOf(true) }

    var progressText by remember { mutableStateOf("Preparing export...") }
    var progressPercent by remember { mutableStateOf(0f) }
    var exportedFile by remember { mutableStateOf<File?>(null) }

    Dialog(
        onDismissRequest = {
            if (stage != ExportDialogStage.EXPORTING) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF101318))
                .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(12.dp))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
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
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF141720))
                                .border(1.dp, Color(0xFF222735), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.FileUpload,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "EXPORT INSTANCE",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Modrinth Modpack (.mrpack)",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (stage != ExportDialogStage.EXPORTING) {
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
                }

                AnimatedContent(
                    targetState = stage,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) }
                ) { currentStage ->
                    when (currentStage) {
                        ExportDialogStage.CONFIGURE -> {
                            ConfigureView(
                                instance = instance,
                                packName = packName,
                                onPackNameChange = { packName = it },
                                packSummary = packSummary,
                                onPackSummaryChange = { packSummary = it },
                                versionId = versionId,
                                onVersionIdChange = { versionId = it },
                                includeConfigs = includeConfigs,
                                onToggleConfigs = { includeConfigs = !includeConfigs },
                                includeMods = includeMods,
                                onToggleMods = { includeMods = !includeMods },
                                includeResourcePacks = includeResourcePacks,
                                onToggleResourcePacks = { includeResourcePacks = !includeResourcePacks },
                                includeShaderPacks = includeShaderPacks,
                                onToggleShaderPacks = { includeShaderPacks = !includeShaderPacks },
                                onCancel = onDismiss,
                                onExport = {
                                    val defaultFileName = "${packName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}_$versionId.mrpack"
                                    viewModel.openFilePicker(
                                        title = "Export Modrinth Modpack",
                                        description = "Choose location to save .mrpack file",
                                        allowedExtensions = setOf("mrpack"),
                                        isSaveMode = true,
                                        defaultSaveName = defaultFileName,
                                        onFileSelected = { target ->
                                            if (target != null) {
                                                val finalTarget = if (target.name.endsWith(".mrpack", ignoreCase = true)) target else java.io.File(target.parentFile, "${target.name}.mrpack")
                                                stage = ExportDialogStage.EXPORTING
                                                exportedFile = finalTarget
                                                val options = MrpackExportOptions(
                                                    customName = packName,
                                                    customSummary = packSummary,
                                                    versionId = versionId,
                                                    includeConfigs = includeConfigs,
                                                    includeMods = includeMods,
                                                    includeResourcePacks = includeResourcePacks,
                                                    includeShaderPacks = includeShaderPacks
                                                )
                                                viewModel.executeExportMrpack(
                                                    instance = instance,
                                                    targetFile = finalTarget,
                                                    options = options,
                                                    onProgress = { step, pct ->
                                                        progressText = step
                                                        progressPercent = pct
                                                    },
                                                    onComplete = { success ->
                                                        if (success) {
                                                            stage = ExportDialogStage.SUCCESS
                                                        } else {
                                                            stage = ExportDialogStage.CONFIGURE
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    )
                                }
                            )
                        }

                        ExportDialogStage.EXPORTING -> {
                            ExportingView(
                                progressText = progressText,
                                progressPercent = progressPercent
                            )
                        }

                        ExportDialogStage.SUCCESS -> {
                            SuccessView(
                                exportedFile = exportedFile,
                                onOpenFileLocation = {
                                    exportedFile?.parentFile?.let { folder ->
                                        viewModel.platformBridge.openFolder(folder.toOkioPath())
                                    }
                                    onDismiss()
                                },
                                onDone = onDismiss
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigureView(
    instance: Instance,
    packName: String,
    onPackNameChange: (String) -> Unit,
    packSummary: String,
    onPackSummaryChange: (String) -> Unit,
    versionId: String,
    onVersionIdChange: (String) -> Unit,
    includeConfigs: Boolean,
    onToggleConfigs: () -> Unit,
    includeMods: Boolean,
    onToggleMods: () -> Unit,
    includeResourcePacks: Boolean,
    onToggleResourcePacks: () -> Unit,
    includeShaderPacks: Boolean,
    onToggleShaderPacks: () -> Unit,
    onCancel: () -> Unit,
    onExport: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Instance Summary Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF07080A))
                .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                val iconFile = instance.customIconPath?.let { File(it) }?.takeIf { it.exists() }
                val bitmap = remember(iconFile) {
                    try {
                        iconFile?.let { SkiaImage.makeFromEncoded(it.readBytes()).toComposeImageBitmap() }
                    } catch (_: Throwable) {
                        null
                    }
                }

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = instance.name,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFF222735), RoundedCornerShape(6.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF141720))
                            .border(1.dp, Color(0xFF222735), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Extension,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = instance.name,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EzzBadge(text = "MC ${instance.minecraftVersion}")
                        EzzBadge(text = instance.loaderType.name)
                        instance.loaderVersion?.let { EzzBadge(text = it) }
                    }
                }
            }
        }

        // Pack Details Input
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.weight(2f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "MODPACK NAME",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                EzzTextField(
                    value = packName,
                    onValueChange = onPackNameChange,
                    placeholder = "Modpack Name...",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "VERSION",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                EzzTextField(
                    value = versionId,
                    onValueChange = onVersionIdChange,
                    placeholder = "1.0.0",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "SUMMARY / DESCRIPTION",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            EzzTextField(
                value = packSummary,
                onValueChange = onPackSummaryChange,
                placeholder = "Short description for the modpack...",
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Content Inclusion Checklist
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "INCLUDED CONTENT",
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
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InclusionCheckboxItem(
                        title = "Configurations & Settings (config/, options.txt)",
                        checked = includeConfigs,
                        onToggle = onToggleConfigs
                    )
                    InclusionCheckboxItem(
                        title = "Installed Mods (mods/ folder)",
                        checked = includeMods,
                        onToggle = onToggleMods
                    )
                    InclusionCheckboxItem(
                        title = "Resource Packs (resourcepacks/ folder)",
                        checked = includeResourcePacks,
                        onToggle = onToggleResourcePacks
                    )
                    InclusionCheckboxItem(
                        title = "Shader Packs (shaderpacks/ folder)",
                        checked = includeShaderPacks,
                        onToggle = onToggleShaderPacks
                    )
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
                onClick = onCancel,
                variant = EzzButtonVariant.SECONDARY,
                size = EzzButtonSize.MEDIUM,
                modifier = Modifier.weight(1f)
            )

            EzzButton(
                text = "Export Modpack",
                onClick = onExport,
                enabled = packName.isNotBlank(),
                icon = Icons.Default.FileUpload,
                variant = EzzButtonVariant.PRIMARY,
                size = EzzButtonSize.MEDIUM,
                modifier = Modifier.weight(1.4f)
            )
        }
    }
}

@Composable
private fun InclusionCheckboxItem(
    title: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) Color.White else Color(0xFF141720))
                .border(1.dp, if (checked) Color.White else Color(0xFF2E3648), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        Text(
            text = title,
            color = if (checked) Color.White else Color(0xFF64748B),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ExportingView(
    progressText: String,
    progressPercent: Float
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF07080A))
                .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(10.dp))
                .padding(20.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = Color.White,
                    strokeWidth = 2.5.dp
                )

                Text(
                    text = progressText,
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )

                LinearProgressIndicator(
                    progress = { progressPercent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color.White,
                    trackColor = Color(0xFF1A1D26),
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun SuccessView(
    exportedFile: File?,
    onOpenFileLocation: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFF141720))
                .border(1.dp, Color(0xFF222735), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "EXPORT COMPLETE",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Text(
                text = exportedFile?.name ?: "Modpack created successfully",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            EzzButton(
                text = "Open Folder",
                onClick = onOpenFileLocation,
                icon = Icons.Default.FolderOpen,
                variant = EzzButtonVariant.SECONDARY,
                size = EzzButtonSize.MEDIUM,
                modifier = Modifier.weight(1f)
            )

            EzzButton(
                text = "Done",
                onClick = onDone,
                variant = EzzButtonVariant.PRIMARY,
                size = EzzButtonSize.MEDIUM,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
