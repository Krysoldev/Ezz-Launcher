package io.ezz.launcher.ui.platform

import okio.Path
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

interface PlatformBridge {
    fun openFolder(path: Path)
    fun openUrl(url: String)
    fun copyToClipboard(text: String)
    fun pickImageFile(title: String = "Select Instance Icon (PNG, JPG, WEBP)"): File?
    fun pickSkinFile(title: String = "Import Minecraft Skin (*.png)"): File?
    fun pickImportInstanceFile(title: String = "Select Modrinth Modpack (*.mrpack)"): File?
    fun pickExportInstanceFile(defaultName: String, title: String = "Export Modrinth Modpack (*.mrpack)"): File?
    fun pickJavaExecutable(title: String = "Select Java Executable (java.exe)"): File?
    fun pickReleaseArtifact(title: String = "Select Release Artifact (*.zip, *.exe, *.msi)"): File?
}

class DefaultPlatformBridge(
    private val onOpenFolder: ((Path) -> Unit)? = null,
    private val onOpenUrl: ((String) -> Unit)? = null,
    private val onCopyToClipboard: ((String) -> Unit)? = null,
    private val onPickImageFile: ((String) -> File?)? = null,
    private val onPickSkinFile: ((String) -> File?)? = null,
    private val onPickImportFile: ((String) -> File?)? = null,
    private val onPickExportFile: ((String, String) -> File?)? = null,
    private val onPickJavaExecutable: ((String) -> File?)? = null,
    private val onPickReleaseArtifact: ((String) -> File?)? = null
) : PlatformBridge {
    override fun openFolder(path: Path) {
        onOpenFolder?.invoke(path)
    }

    override fun openUrl(url: String) {
        onOpenUrl?.invoke(url)
    }

    override fun copyToClipboard(text: String) {
        if (onCopyToClipboard != null) {
            onCopyToClipboard.invoke(text)
        } else {
            try {
                val selection = StringSelection(text)
                Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
            } catch (e: Throwable) {
                println("Failed to copy to clipboard: ${e.message}")
            }
        }
    }

    override fun pickImageFile(title: String): File? {
        if (onPickImageFile != null) return onPickImageFile.invoke(title)
        return WindowsModernFilePicker.openFileDialog(
            title = title,
            filterSpecs = listOf(
                "Supported Images (*.png;*.jpg;*.jpeg;*.webp)" to "*.png;*.jpg;*.jpeg;*.webp",
                "PNG Images (*.png)" to "*.png",
                "JPEG Images (*.jpg;*.jpeg)" to "*.jpg;*.jpeg",
                "WEBP Images (*.webp)" to "*.webp",
                "All Files (*.*)" to "*.*"
            )
        )
    }

    override fun pickSkinFile(title: String): File? {
        if (onPickSkinFile != null) return onPickSkinFile.invoke(title)
        return WindowsModernFilePicker.openFileDialog(
            title = title,
            filterSpecs = listOf(
                "PNG Images (*.png)" to "*.png",
                "All Files (*.*)" to "*.*"
            ),
            defaultExtension = "png"
        )
    }

    override fun pickImportInstanceFile(title: String): File? {
        if (onPickImportFile != null) return onPickImportFile.invoke(title)
        val userHome = System.getProperty("user.home", ".")
        val downloads = File(userHome, "Downloads")
        val initDir = if (downloads.exists() && downloads.isDirectory) downloads else File(userHome)
        return WindowsModernFilePicker.openFileDialog(
            title = title,
            filterSpecs = listOf(
                "Modrinth Modpack (*.mrpack)" to "*.mrpack",
                "All Files (*.*)" to "*.*"
            ),
            initialDir = initDir,
            defaultExtension = "mrpack"
        )
    }

    override fun pickExportInstanceFile(defaultName: String, title: String): File? {
        val fileNameWithExt = if (defaultName.endsWith(".mrpack", ignoreCase = true)) defaultName else "$defaultName.mrpack"
        if (onPickExportFile != null) return onPickExportFile.invoke(fileNameWithExt, title)
        return WindowsModernFilePicker.saveFileDialog(
            title = title,
            filterSpecs = listOf(
                "Modrinth Modpack (*.mrpack)" to "*.mrpack",
                "All Files (*.*)" to "*.*"
            ),
            defaultName = fileNameWithExt,
            defaultExtension = "mrpack"
        )
    }

    override fun pickJavaExecutable(title: String): File? {
        if (onPickJavaExecutable != null) return onPickJavaExecutable.invoke(title)
        return WindowsModernFilePicker.openFileDialog(
            title = title,
            filterSpecs = listOf(
                "Java Executable (java.exe)" to "java.exe;javaw.exe;*.exe",
                "All Executables (*.exe)" to "*.exe",
                "All Files (*.*)" to "*.*"
            ),
            defaultExtension = "exe"
        )
    }

    override fun pickReleaseArtifact(title: String): File? {
        if (onPickReleaseArtifact != null) return onPickReleaseArtifact.invoke(title)
        return WindowsModernFilePicker.openFileDialog(
            title = title,
            filterSpecs = listOf(
                "Release Artifacts (*.zip;*.exe;*.msi;*.jar;*.tar.gz)" to "*.zip;*.exe;*.msi;*.jar;*.tar.gz",
                "All Files (*.*)" to "*.*"
            )
        )
    }
}
