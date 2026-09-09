package io.ezz.launcher.ui.platform

/**
 * Controller interface allowing cross-module window visibility manipulation
 * (e.g., hiding the desktop launcher window during Minecraft gameplay and restoring it).
 */
interface WindowVisibilityController {
    /**
     * Shows or hides the launcher desktop window.
     * When visible = false: window is hidden from screen and taskbar.
     * When visible = true: window is restored with its prior dimensions, position,
     * and window state, then brought to front with focus.
     */
    fun setVisible(visible: Boolean)

    /**
     * Returns whether the launcher desktop window is currently visible.
     */
    fun isVisible(): Boolean
}
