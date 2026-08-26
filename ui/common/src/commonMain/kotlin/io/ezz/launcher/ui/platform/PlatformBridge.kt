package io.ezz.launcher.ui.platform

import okio.Path

interface PlatformBridge {
    fun openFolder(path: Path)
    fun openUrl(url: String)
}

class DefaultPlatformBridge(
    private val onOpenFolder: ((Path) -> Unit)? = null,
    private val onOpenUrl: ((String) -> Unit)? = null
) : PlatformBridge {
    override fun openFolder(path: Path) {
        onOpenFolder?.invoke(path)
    }

    override fun openUrl(url: String) {
        onOpenUrl?.invoke(url)
    }
}
