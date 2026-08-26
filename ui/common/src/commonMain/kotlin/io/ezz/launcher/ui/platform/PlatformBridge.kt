package io.ezz.launcher.ui.platform

import okio.Path
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

interface PlatformBridge {
    fun openFolder(path: Path)
    fun openUrl(url: String)
    fun copyToClipboard(text: String)
    fun pickImageFile(title: String = "Select Instance Icon"): java.io.File?
}

class DefaultPlatformBridge(
    private val onOpenFolder: ((Path) -> Unit)? = null,
    private val onOpenUrl: ((String) -> Unit)? = null,
    private val onCopyToClipboard: ((String) -> Unit)? = null,
    private val onPickImageFile: ((String) -> java.io.File?)? = null
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
}
