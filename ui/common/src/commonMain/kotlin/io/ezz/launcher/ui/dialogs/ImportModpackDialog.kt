package io.ezz.launcher.ui.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.modrinth.MrpackImportProgress
import io.ezz.launcher.core.model.modrinth.MrpackImportStage
import io.ezz.launcher.core.model.modrinth.MrpackPreview
import io.ezz.launcher.ui.components.EzzBadge
import io.ezz.launcher.ui.components.EzzButton
import io.ezz.launcher.ui.components.EzzButtonSize
import io.ezz.launcher.ui.components.EzzButtonVariant
import io.ezz.launcher.ui.components.EzzTextField
import io.ezz.launcher.ui.viewmodel.AppViewModel
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image as SkiaImage
import java.io.File

/**
 * Step states for the import modpack modal.
 */
enum class ImportDialogStep {
    DROPZONE,
    PREVIEW,
    IMPORTING,
    SUCCESS
}

/**
 * Modern, studio-grade Modal Dialog for importing Modrinth Modpacks (.mrpack):
 * 1. Clean state lifecycle with guaranteed reset on mount/dismiss.
 * 2. Dropzone & File Picker (supports .mrpack exclusively).
 * 3. Manifest validation & metadata preview (icon, MC version, loader, mod count, custom name).
 * 4. Asynchronous live multi-stage progress with cancellation support.
 * 5. Success confirmation with exact imported instance name and 1-click launch / view instance / import another.
 */
@Composable
fun ImportModpackDialog(
    viewModel: AppViewModel,
    initialFile: File? = null,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val instances by viewModel.instances.collectAsState()
    val importProgress by viewModel.mrpackImportProgress.collectAsState()
    val isImporting by viewModel.isImportingMrpack.collectAsState()

    var selectedFile by remember { mutableStateOf<File?>(null) }
    var previewData by remember { mutableStateOf<MrpackPreview?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var isValidating by remember { mutableStateOf(false) }

    var instanceNameInput by remember { mutableStateOf("") }
    var currentStep by remember { mutableStateOf(ImportDialogStep.DROPZONE) }
    var importedInstance by remember { mutableStateOf<Instance?>(null) }

    fun handleDismiss() {
        if (!isImporting) {
            viewModel.closeImportModpack()
            onDismiss()
        }
    }

    fun resetToDropzone() {
        viewModel.resetImportState()
        selectedFile = null
        previewData = null
        validationError = null
        isValidating = false
        instanceNameInput = ""
        importedInstance = null
        currentStep = ImportDialogStep.DROPZONE
    }

    // Validate and load preview whenever a file is selected
    fun validateAndLoadPreview(file: File) {
        if (!file.name.endsWith(".mrpack", ignoreCase = true)) {
            validationError = "Invalid file. Please select a valid .mrpack file."
            currentStep = ImportDialogStep.DROPZONE
            return
        }
        selectedFile = file
        isValidating = true
        validationError = null
        coroutineScope.launch {
            val result = viewModel.instanceManager.mrpackManager.previewMrpack(file)
            isValidating = false
            if (result.isSuccess) {
                val preview = result.getOrNull()!!
                previewData = preview
                instanceNameInput = preview.name
                currentStep = ImportDialogStep.PREVIEW
            } else {
                validationError = "Invalid file. Please select a valid .mrpack file."
                currentStep = ImportDialogStep.DROPZONE
            }
        }
    }

    // Fresh initialization on mount or initialFile change
    LaunchedEffect(initialFile) {
        if (initialFile != null) {
            validateAndLoadPreview(initialFile)
        } else {
            resetToDropzone()
        }
    }

    // React only to live active import progress
    LaunchedEffect(importProgress) {
        val progress = importProgress
        if (currentStep == ImportDialogStep.IMPORTING) {
            if (progress?.stage == MrpackImportStage.COMPLETE) {
                currentStep = ImportDialogStep.SUCCESS
            } else if (progress?.stage == MrpackImportStage.FAILED) {
                validationError = progress.message.ifBlank { "Import failed" }
                currentStep = ImportDialogStep.PREVIEW
            }
        }
    }

    Dialog(
        onDismissRequest = { handleDismiss() },
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
                                Icons.Default.FileDownload,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "IMPORT MODPACK",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Modrinth Modpack (*.mrpack)",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (!isImporting) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF64748B),
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .clickable { handleDismiss() }
                        )
                    }
                }

                // Error Banner
                AnimatedVisibility(
                    visible = validationError != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2B1214))
                            .border(1.dp, Color(0xFF5C2328), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = validationError ?: "",
                                color = Color(0xFFFF8A80),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Step Content
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                    label = "ImportStepTransition"
                ) { step ->
                    when (step) {
                        ImportDialogStep.DROPZONE -> {
                            DropzoneView(
                                isValidating = isValidating,
                                onChooseFile = {
                                    viewModel.openFilePicker(
                                        title = "Select Modrinth Modpack",
                                        description = "Select a .mrpack file",
                                        allowedExtensions = setOf("mrpack"),
                                        onFileSelected = { file ->
                                            if (file != null) {
                                                validateAndLoadPreview(file)
                                            }
                                        }
                                    )
                                }
                            )
                        }

                        ImportDialogStep.PREVIEW -> {
                            previewData?.let { preview ->
                                val conflictingInstance = instances.firstOrNull { it.name.equals(instanceNameInput.trim(), ignoreCase = true) }
                                val hasNameCollision = conflictingInstance != null

                                PreviewView(
                                    preview = preview,
                                    instanceName = instanceNameInput,
                                    onInstanceNameChange = { instanceNameInput = it },
                                    hasNameCollision = hasNameCollision,
                                    onCreateCopy = {
                                        var copyIndex = 1
                                        var candidate = "${instanceNameInput.trim()} (Copy)"
                                        while (instances.any { it.name.equals(candidate, ignoreCase = true) }) {
                                            copyIndex++
                                            candidate = "${instanceNameInput.trim()} (Copy $copyIndex)"
                                        }
                                        instanceNameInput = candidate
                                    },
                                    onReplaceExisting = {
                                        if (selectedFile != null && conflictingInstance != null) {
                                            viewModel.deleteInstance(conflictingInstance.id)
                                            currentStep = ImportDialogStep.IMPORTING
                                            viewModel.executeImportMrpack(
                                                file = selectedFile!!,
                                                instanceName = instanceNameInput.trim()
                                            ) { res ->
                                                if (res.isSuccess) {
                                                    importedInstance = res.getOrNull()
                                                    instanceNameInput = importedInstance?.name ?: instanceNameInput
                                                    currentStep = ImportDialogStep.SUCCESS
                                                }
                                            }
                                        }
                                    },
                                    onBack = {
                                        resetToDropzone()
                                    },
                                    onStartImport = {
                                        if (selectedFile != null) {
                                            currentStep = ImportDialogStep.IMPORTING
                                            viewModel.executeImportMrpack(
                                                file = selectedFile!!,
                                                instanceName = instanceNameInput.trim()
                                            ) { res ->
                                                if (res.isSuccess) {
                                                    importedInstance = res.getOrNull()
                                                    instanceNameInput = importedInstance?.name ?: instanceNameInput
                                                    currentStep = ImportDialogStep.SUCCESS
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        ImportDialogStep.IMPORTING -> {
                            ImportingProgressView(
                                progress = importProgress,
                                onCancel = {
                                    viewModel.cancelMrpackImport()
                                    currentStep = ImportDialogStep.PREVIEW
                                }
                            )
                        }

                        ImportDialogStep.SUCCESS -> {
                            SuccessView(
                                instanceName = instanceNameInput,
                                importedInstance = importedInstance,
                                preview = previewData,
                                onPlayNow = {
                                    importedInstance?.let { inst ->
                                        viewModel.selectInstance(inst)
                                        viewModel.launchInstance(inst)
                                    }
                                    handleDismiss()
                                },
                                onOpenInstance = {
                                    importedInstance?.let { inst ->
                                        viewModel.openInstanceManager(inst)
                                    }
                                    handleDismiss()
                                },
                                onImportAnother = {
                                    resetToDropzone()
                                },
                                onDone = { handleDismiss() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DropzoneView(
    isValidating: Boolean,
    onChooseFile: () -> Unit
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
                .clickable { if (!isValidating) onChooseFile() }
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isValidating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                    Text(
                        text = "Validating Modrinth modpack...",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF141720))
                            .border(1.dp, Color(0xFF222735), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Drop .mrpack file here",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "or click to browse local files",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        EzzButton(
            text = "Choose .mrpack File",
            onClick = onChooseFile,
            enabled = !isValidating,
            icon = Icons.Default.FolderOpen,
            variant = EzzButtonVariant.PRIMARY,
            size = EzzButtonSize.MEDIUM,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PreviewView(
    preview: MrpackPreview,
    instanceName: String,
    onInstanceNameChange: (String) -> Unit,
    hasNameCollision: Boolean,
    onCreateCopy: () -> Unit,
    onReplaceExisting: () -> Unit,
    onBack: () -> Unit,
    onStartImport: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Summary Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF07080A))
                .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(8.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                val iconBytes = preview.iconBytes
                if (iconBytes != null && iconBytes.isNotEmpty()) {
                    val bitmap = remember(iconBytes) {
                        try {
                            SkiaImage.makeFromEncoded(iconBytes).toComposeImageBitmap()
                        } catch (_: Throwable) {
                            null
                        }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = preview.name,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF222735), RoundedCornerShape(8.dp))
                        )
                    } else {
                        FallbackIcon()
                    }
                } else {
                    FallbackIcon()
                }

                // Info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = preview.name,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val summary = preview.summary
                    if (!summary.isNullOrBlank()) {
                        Text(
                            text = summary,
                            color = Color(0xFF94A3B8),
                            fontSize = 11.5.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EzzBadge(text = "MC ${preview.minecraftVersion}")
                        EzzBadge(text = preview.loaderType.name)
                        if (preview.totalFiles > 0) {
                            EzzBadge(text = "${preview.totalFiles} files")
                        }
                    }
                }
            }
        }

        // Destination Instance Name Input & Collision Banner
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "INSTANCE NAME",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            EzzTextField(
                value = instanceName,
                onValueChange = onInstanceNameChange,
                placeholder = "Enter instance name...",
                modifier = Modifier.fillMaxWidth()
            )

            if (hasNameCollision) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF261E0A))
                        .border(1.dp, Color(0xFF6B4E12), RoundedCornerShape(6.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Instance '$instanceName' already exists.",
                                color = Color(0xFFFBBF24),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EzzButton(
                                text = "Create Copy",
                                onClick = onCreateCopy,
                                variant = EzzButtonVariant.SECONDARY,
                                size = EzzButtonSize.SMALL
                            )
                            EzzButton(
                                text = "Replace Existing",
                                onClick = onReplaceExisting,
                                variant = EzzButtonVariant.DANGER,
                                size = EzzButtonSize.SMALL
                            )
                        }
                    }
                }
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            EzzButton(
                text = "Choose Another",
                onClick = onBack,
                variant = EzzButtonVariant.SECONDARY,
                size = EzzButtonSize.MEDIUM,
                modifier = Modifier.weight(1f)
            )

            EzzButton(
                text = if (hasNameCollision) "Import as '$instanceName'" else "Import Modpack",
                onClick = onStartImport,
                enabled = instanceName.isNotBlank(),
                icon = Icons.Default.FileDownload,
                variant = EzzButtonVariant.PRIMARY,
                size = EzzButtonSize.MEDIUM,
                modifier = Modifier.weight(1.4f)
            )
        }
    }
}

@Composable
private fun ImportingProgressView(
    progress: MrpackImportProgress?,
    onCancel: () -> Unit
) {
    val currentProgress = progress?.progress ?: 0.05f
    val stage = progress?.stage ?: MrpackImportStage.READING_MANIFEST

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Progress Bar Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF07080A))
                .border(1.dp, Color(0xFF1A1D26), RoundedCornerShape(10.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stage.label,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${(currentProgress * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Custom Animated Track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF1A1D26))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(currentProgress.coerceIn(0f, 1f))
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFFFFFFFF), Color(0xFF94A3B8))
                                )
                            )
                    )
                }

                // Current File / Sub-detail label
                val msg = progress?.message ?: "Preparing modpack import..."
                Text(
                    text = msg,
                    color = Color(0xFF64748B),
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Live Checklist
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StageChecklistItem(
                title = "Reading modrinth.index.json manifest",
                isPassed = currentProgress > 0.08f,
                isActive = stage == MrpackImportStage.READING_MANIFEST
            )
            StageChecklistItem(
                title = "Validating package integrity & loader dependencies",
                isPassed = currentProgress > 0.12f,
                isActive = stage == MrpackImportStage.VALIDATING_STRUCTURE
            )
            StageChecklistItem(
                title = "Creating isolated instance directory workspace",
                isPassed = currentProgress > 0.18f,
                isActive = stage == MrpackImportStage.CREATING_INSTANCE
            )
            StageChecklistItem(
                title = "Extracting configuration overrides & modpack icon",
                isPassed = currentProgress > 0.24f,
                isActive = stage == MrpackImportStage.EXTRACTING_OVERRIDES
            )
            StageChecklistItem(
                title = "Downloading modpack files from Modrinth CDN",
                isPassed = currentProgress >= 0.90f,
                isActive = stage == MrpackImportStage.DOWNLOADING_MODS
            )
            StageChecklistItem(
                title = "Finalizing instance installation",
                isPassed = currentProgress >= 0.98f,
                isActive = stage == MrpackImportStage.FINALIZING
            )
        }

        // Cancel Button
        EzzButton(
            text = "Cancel Import",
            onClick = onCancel,
            variant = EzzButtonVariant.SECONDARY,
            size = EzzButtonSize.MEDIUM,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StageChecklistItem(
    title: String,
    isPassed: Boolean,
    isActive: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isPassed) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(15.dp)
            )
        } else if (isActive) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color(0xFF2E3648), CircleShape)
            )
        }

        Text(
            text = title,
            color = if (isPassed || isActive) Color.White else Color(0xFF64748B),
            fontSize = 12.5.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun SuccessView(
    instanceName: String,
    importedInstance: Instance?,
    preview: MrpackPreview?,
    onPlayNow: () -> Unit,
    onOpenInstance: () -> Unit,
    onImportAnother: () -> Unit,
    onDone: () -> Unit
) {
    val displayName = importedInstance?.name?.takeIf { it.isNotBlank() }
        ?: instanceName.takeIf { it.isNotBlank() }
        ?: preview?.name?.takeIf { it.isNotBlank() }
        ?: "Instance"

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
                text = "INSTANCE IMPORTED",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "'$displayName' is ready to launch!",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        if (preview != null || importedInstance != null) {
            val mcVer = importedInstance?.minecraftVersion ?: preview?.minecraftVersion ?: ""
            val loader = importedInstance?.loaderType?.name ?: preview?.loaderType?.name ?: ""
            val fileCount = preview?.totalFiles ?: 0

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (mcVer.isNotBlank()) EzzBadge(text = "MC $mcVer")
                if (loader.isNotBlank()) EzzBadge(text = loader)
                if (fileCount > 0) {
                    EzzBadge(text = "$fileCount mods installed")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EzzButton(
                text = "Import Another",
                onClick = onImportAnother,
                variant = EzzButtonVariant.GHOST,
                size = EzzButtonSize.MEDIUM,
                modifier = Modifier.weight(1f)
            )

            EzzButton(
                text = "View Instance",
                onClick = onOpenInstance,
                variant = EzzButtonVariant.SECONDARY,
                size = EzzButtonSize.MEDIUM,
                modifier = Modifier.weight(1f)
            )

            EzzButton(
                text = "Play Now",
                onClick = onPlayNow,
                icon = Icons.Default.PlayArrow,
                variant = EzzButtonVariant.PRIMARY,
                size = EzzButtonSize.MEDIUM,
                modifier = Modifier.weight(1.2f)
            )
        }
    }
}

@Composable
private fun FallbackIcon() {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF141720))
            .border(1.dp, Color(0xFF222735), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Extension,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}
