package io.ezz.launcher.ui.platform

import okio.Path
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

interface PlatformBridge {
    fun openFolder(path: Path)
    fun openUrl(url: String)
    fun copyToClipboard(text: String)
}

class DefaultPlatformBridge(
    private val onOpenFolder: ((Path) -> Unit)? = null,
    private val onOpenUrl: ((String) -> Unit)? = null,
    private val onCopyToClipboard: ((String) -> Unit)? = null
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
}
