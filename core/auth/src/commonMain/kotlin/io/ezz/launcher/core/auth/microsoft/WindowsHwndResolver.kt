package io.ezz.launcher.core.auth.microsoft

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import java.awt.Component

/**
 * Robust Win32 HWND resolver for Ezz Launcher.
 * Resolves the actual native top-level window handle of the application for Windows Web Account Manager (WAM)
 * without using or relying on console windows (GetConsoleWindow()).
 */
object WindowsHwndResolver {
    private const val GA_ROOT = 2
    private const val GA_ROOTOWNER = 3

    /**
     * Resolves and validates the native Win32 HWND for the Ezz Launcher window.
     *
     * @param window An optional reference to the ComposeWindow or java.awt.Window/Component.
     * @param expectedTitle The expected window title (default: "Ezz Launcher").
     * @return Validated non-zero native Win32 HWND, or null if on non-Windows or unresolved.
     */
    fun resolve(window: Any? = null, expectedTitle: String = "Ezz Launcher"): Long? {
        val osName = System.getProperty("os.name")?.lowercase() ?: ""
        if (!osName.contains("win")) {
            println("[WindowsHwndResolver] Non-Windows OS detected ($osName), skipping HWND resolution.")
            return null
        }

        // Strategy 1: Compose Desktop Native API on ComposeWindow (window.windowHandle)
        if (window != null) {
            try {
                val method = window.javaClass.getMethod("getWindowHandle")
                val handle = (method.invoke(window) as? Number)?.toLong() ?: 0L
                if (handle != 0L) {
                    val normalized = normalizeAndValidate(handle, "ComposeWindow.getWindowHandle()")
                    if (normalized != null) return normalized
                }
            } catch (_: Throwable) {}

            // Strategy 2: Java AWT / JNA JAWT pointer on java.awt.Component
            if (window is Component) {
                try {
                    val ptr = com.sun.jna.Native.getComponentPointer(window)
                    if (ptr != null) {
                        val handle = Pointer.nativeValue(ptr)
                        if (handle != 0L) {
                            val normalized = normalizeAndValidate(handle, "JNA getComponentPointer(window)")
                            if (normalized != null) return normalized
                        }
                    }
                } catch (_: Throwable) {}
            }
        }

        // Strategy 3: FindWindow by exact title via Win32 User32
        try {
            val user32 = User32.INSTANCE
            val hwnd = user32.FindWindow(null, expectedTitle)
            if (hwnd != null) {
                val handle = Pointer.nativeValue(hwnd.pointer)
                if (handle != 0L) {
                    val normalized = normalizeAndValidate(handle, "User32.FindWindow('$expectedTitle')")
                    if (normalized != null) return normalized
                }
            }
        } catch (_: Throwable) {}

        // Strategy 4: Enumerate top-level visible windows belonging to current process
        try {
            val user32 = User32.INSTANCE
            val currentPid = Kernel32.INSTANCE.GetCurrentProcessId()
            var foundHandle: Long? = null

            user32.EnumWindows({ hwnd, _ ->
                val pidRef = com.sun.jna.ptr.IntByReference()
                user32.GetWindowThreadProcessId(hwnd, pidRef)
                if (pidRef.value == currentPid && user32.IsWindowVisible(hwnd)) {
                    val charArray = CharArray(512)
                    user32.GetWindowText(hwnd, charArray, 512)
                    val title = String(charArray).trimEnd { it == '\u0000' }
                    if (title.contains("Ezz", ignoreCase = true) || title.contains("Launcher", ignoreCase = true)) {
                        val rawHandle = Pointer.nativeValue(hwnd.pointer)
                        foundHandle = normalizeAndValidate(rawHandle, "User32.EnumWindows(pid=$currentPid, title='$title')")
                        return@EnumWindows false // Stop enumeration
                    }
                }
                true // Continue enumeration
            }, null)

            if (foundHandle != null) return foundHandle
        } catch (e: Throwable) {
            println("[WindowsHwndResolver] Notice during EnumWindows: ${e.message}")
        }

        println("[WindowsHwndResolver] WARNING: Could not resolve a valid Win32 HWND for '$expectedTitle'.")
        return null
    }

    /**
     * Validates that the handle is a valid Win32 window and normalizes it to the top-level owner HWND.
     */
    fun normalizeAndValidate(rawHandle: Long, sourceDescription: String): Long? {
        if (rawHandle == 0L) return null
        try {
            val user32 = User32.INSTANCE
            val rawHwnd = HWND(Pointer.createConstant(rawHandle))
            if (!user32.IsWindow(rawHwnd)) {
                println("[WindowsHwndResolver] Handle $rawHandle (0x${java.lang.Long.toHexString(rawHandle)}) from $sourceDescription is not a valid Win32 window.")
                return null
            }

            // Get root owner to ensure modal parenting works with top-level window
            val rootHwnd = user32.GetAncestor(rawHwnd, GA_ROOTOWNER)
            val effectiveHwnd = if (rootHwnd != null && user32.IsWindow(rootHwnd)) rootHwnd else rawHwnd
            val effectiveHandle = Pointer.nativeValue(effectiveHwnd.pointer)

            println("[WindowsHwndResolver] Successfully resolved native HWND: $effectiveHandle (0x${java.lang.Long.toHexString(effectiveHandle)}) via $sourceDescription")
            return effectiveHandle
        } catch (e: Throwable) {
            println("[WindowsHwndResolver] Error validating handle $rawHandle: ${e.message}")
            return rawHandle
        }
    }
}
