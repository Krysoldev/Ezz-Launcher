package io.ezz.launcher.ui.platform

import okio.Path
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

interface PlatformBridge {
    fun openFolder(path: Path)
    fun openUrl(url: String)
    fun copyToClipboard(text: String)
    fun pickImageFile(title: String = "Select Instance Icon"): java.io.File?
    fun pickImportInstanceFile(title: String = "Select Modrinth Modpack (*.mrpack)"): java.io.File?
    fun pickExportInstanceFile(defaultName: String, title: String = "Export Modrinth Modpack (*.mrpack)"): java.io.File?
    fun pickJavaExecutable(title: String = "Select Java Executable (java.exe)"): java.io.File?
    fun pickReleaseArtifact(title: String = "Select Release Artifact (*.zip, *.exe, *.msi)"): java.io.File?
}

class DefaultPlatformBridge(
    private val onOpenFolder: ((Path) -> Unit)? = null,
    private val onOpenUrl: ((String) -> Unit)? = null,
    private val onCopyToClipboard: ((String) -> Unit)? = null,
    private val onPickImageFile: ((String) -> java.io.File?)? = null,
    private val onPickImportFile: ((String) -> java.io.File?)? = null,
    private val onPickExportFile: ((String, String) -> java.io.File?)? = null
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

    override fun pickImageFile(title: String): java.io.File? {
        if (onPickImageFile != null) {
            return onPickImageFile.invoke(title)
        }
        return try {
            val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.LOAD)
            dialog.setFilenameFilter { _, name ->
                val lower = name.lowercase()
                lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp")
            }
            dialog.isVisible = true
            val dir = dialog.directory
            val file = dialog.file
            if (dir != null && file != null) {
                java.io.File(dir, file)
            } else {
                null
            }
        } catch (e: Throwable) {
            try {
                val chooser = javax.swing.JFileChooser()
                chooser.dialogTitle = title
                chooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
                    "Image files (PNG, JPG, WEBP)", "png", "jpg", "jpeg", "webp"
                )
                val res = chooser.showOpenDialog(null)
                if (res == javax.swing.JFileChooser.APPROVE_OPTION) {
                    chooser.selectedFile
                } else null
            } catch (e2: Throwable) {
                null
            }
        }
    }

    override fun pickImportInstanceFile(title: String): java.io.File? {
        if (onPickImportFile != null) {
            return onPickImportFile.invoke(title)
        }
        return try {
            val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.LOAD)
            dialog.setFilenameFilter { _, name ->
                val lower = name.lowercase()
                lower.endsWith(".mrpack") || lower.endsWith(".zip")
            }
            dialog.isVisible = true
            val dir = dialog.directory
            val file = dialog.file
            if (dir != null && file != null) {
                java.io.File(dir, file)
            } else {
                null
            }
        } catch (e: Throwable) {
            try {
                val chooser = javax.swing.JFileChooser()
                chooser.dialogTitle = title
                chooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
                    "Modrinth Modpack (*.mrpack)", "mrpack", "zip"
                )
                val res = chooser.showOpenDialog(null)
                if (res == javax.swing.JFileChooser.APPROVE_OPTION) {
                    chooser.selectedFile
                } else null
            } catch (e2: Throwable) {
                null
            }
        }
    }

    override fun pickExportInstanceFile(defaultName: String, title: String): java.io.File? {
        if (onPickExportFile != null) {
            return onPickExportFile.invoke(defaultName, title)
        }
        val fileNameWithExt = if (defaultName.endsWith(".mrpack", ignoreCase = true)) defaultName else "$defaultName.mrpack"
        return try {
            val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.SAVE)
            dialog.file = fileNameWithExt
            dialog.isVisible = true
            val dir = dialog.directory
            val file = dialog.file
            if (dir != null && file != null) {
                val chosen = java.io.File(dir, file)
                if (chosen.name.endsWith(".mrpack", ignoreCase = true)) chosen else java.io.File(dir, "${chosen.name}.mrpack")
            } else {
                null
            }
        } catch (e: Throwable) {
            try {
                val chooser = javax.swing.JFileChooser()
                chooser.dialogTitle = title
                chooser.selectedFile = java.io.File(fileNameWithExt)
                chooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
                    "Modrinth Modpack (*.mrpack)", "mrpack"
                )
                val res = chooser.showSaveDialog(null)
                if (res == javax.swing.JFileChooser.APPROVE_OPTION) {
                    val chosen = chooser.selectedFile
                    if (chosen != null && !chosen.name.endsWith(".mrpack", ignoreCase = true)) {
                        java.io.File(chosen.parentFile, "${chosen.name}.mrpack")
                    } else {
                        chosen
                    }
                } else null
            } catch (e2: Throwable) {
                null
            }
        }
    }

    override fun pickJavaExecutable(title: String): java.io.File? {
        return try {
            val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.LOAD)
            dialog.setFilenameFilter { _, name ->
                val lower = name.lowercase()
                lower == "java.exe" || lower == "javaw.exe" || lower == "java" || lower.endsWith(".exe")
            }
            dialog.isVisible = true
            val dir = dialog.directory
            val file = dialog.file
            if (dir != null && file != null) {
                java.io.File(dir, file)
            } else {
                null
            }
        } catch (e: Throwable) {
            try {
                val chooser = javax.swing.JFileChooser()
                chooser.dialogTitle = title
                val res = chooser.showOpenDialog(null)
                if (res == javax.swing.JFileChooser.APPROVE_OPTION) {
                    chooser.selectedFile
                } else null
            } catch (e2: Throwable) {
                null
            }
        }
    }

    override fun pickReleaseArtifact(title: String): java.io.File? {
        return try {
            val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.LOAD)
            dialog.setFilenameFilter { _, name ->
                val lower = name.lowercase()
                lower.endsWith(".zip") || lower.endsWith(".exe") || lower.endsWith(".msi") || lower.endsWith(".jar") || lower.endsWith(".tar.gz")
            }
            dialog.isVisible = true
            val dir = dialog.directory
            val file = dialog.file
            if (dir != null && file != null) {
                java.io.File(dir, file)
            } else {
                null
            }
        } catch (e: Throwable) {
            try {
                val chooser = javax.swing.JFileChooser()
                chooser.dialogTitle = title
                val res = chooser.showOpenDialog(null)
                if (res == javax.swing.JFileChooser.APPROVE_OPTION) {
                    chooser.selectedFile
                } else null
            } catch (e2: Throwable) {
                null
            }
        }
    }
}
