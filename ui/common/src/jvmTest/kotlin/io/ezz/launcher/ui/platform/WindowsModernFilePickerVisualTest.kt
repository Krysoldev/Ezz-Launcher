package io.ezz.launcher.ui.platform

import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertNull

class WindowsModernFilePickerVisualTest {

    @Test
    fun testModernDialogShowsAndCancels() {
        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
        if (!isWindows) return

        var selectedFile: File? = null
        val latch = CountDownLatch(1)
        val thread = Thread {
            selectedFile = WindowsModernFilePicker.openFileDialog(
                title = "Select Modrinth Modpack",
                filterSpecs = listOf(
                    "Modrinth Modpack (*.mrpack)" to "*.mrpack",
                    "All Files (*.*)" to "*.*"
                )
            )
            latch.countDown()
        }
        thread.isDaemon = true
        thread.start()

        // Wait for native dialog to appear on screen
        Thread.sleep(1500)

        val hwnd = User32.INSTANCE.FindWindow(null, "Select Modrinth Modpack")
        println("Found dialog HWND: $hwnd")

        // Capture screenshot of the dialog window
        if (hwnd != null) {
            try {
                val image = com.sun.jna.platform.win32.GDI32Util.getScreenshot(hwnd)
                val artifactDir = File("C:/Users/shivp/.gemini/antigravity-ide/brain/5ac35c55-fbdf-4317-9935-71ee4675df82")
                artifactDir.mkdirs()
                val screenshotFile = File(artifactDir, "modern_file_picker.png")
                ImageIO.write(image, "png", screenshotFile)
                println("Saved window screenshot to: ${screenshotFile.absolutePath} (${image.width}x${image.height})")
            } catch (e: Throwable) {
                println("Screenshot capture failed: ${e.message}")
            }

            // Send WM_CLOSE to cancel
            val WM_CLOSE = 0x0010
            User32.INSTANCE.PostMessage(hwnd, WM_CLOSE, WinDef.WPARAM(0), WinDef.LPARAM(0))
        }

        val finished = latch.await(4, TimeUnit.SECONDS)
        println("Dialog finished: $finished, selectedFile: $selectedFile")
        assertNull(selectedFile, "Cancelling the dialog must return null")
    }

    @Test
    fun testModernSaveDialogShowsAndCancels() {
        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
        if (!isWindows) return

        var selectedFile: File? = null
        val latch = CountDownLatch(1)
        val thread = Thread {
            selectedFile = WindowsModernFilePicker.saveFileDialog(
                title = "Export Modrinth Modpack (*.mrpack)",
                filterSpecs = listOf(
                    "Modrinth Modpack (*.mrpack)" to "*.mrpack",
                    "All Files (*.*)" to "*.*"
                ),
                defaultName = "MyAwesomePack.mrpack",
                defaultExtension = "mrpack"
            )
            latch.countDown()
        }
        thread.isDaemon = true
        thread.start()

        Thread.sleep(1500)

        val hwnd = User32.INSTANCE.FindWindow(null, "Export Modrinth Modpack (*.mrpack)")
        println("Found Save dialog HWND: $hwnd")

        if (hwnd != null) {
            try {
                val image = com.sun.jna.platform.win32.GDI32Util.getScreenshot(hwnd)
                val artifactDir = File("C:/Users/shivp/.gemini/antigravity-ide/brain/5ac35c55-fbdf-4317-9935-71ee4675df82")
                val screenshotFile = File(artifactDir, "modern_save_file_picker.png")
                ImageIO.write(image, "png", screenshotFile)
                println("Saved save-dialog window screenshot to: ${screenshotFile.absolutePath} (${image.width}x${image.height})")
            } catch (e: Throwable) {
                println("Save screenshot capture failed: ${e.message}")
            }

            val WM_CLOSE = 0x0010
            User32.INSTANCE.PostMessage(hwnd, WM_CLOSE, WinDef.WPARAM(0), WinDef.LPARAM(0))
        }

        val finished = latch.await(4, TimeUnit.SECONDS)
        println("Save dialog finished: $finished, selectedFile: $selectedFile")
        assertNull(selectedFile, "Cancelling the save dialog must return null")
    }
}
