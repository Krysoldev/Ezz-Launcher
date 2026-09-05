package io.ezz.launcher.ui.platform

import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.ptr.PointerByReference
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WindowsModernFilePickerTest {

    @Test
    fun testComdlgFilterspecInitialization() {
        val spec = WindowsModernFilePicker.COMDLG_FILTERSPEC("Modrinth Modpack (*.mrpack)", "*.mrpack")
        assertNotNull(spec.pszName)
        assertNotNull(spec.pszSpec)
        assertEquals("Modrinth Modpack (*.mrpack)", spec.pszName.toString())
        assertEquals("*.mrpack", spec.pszSpec.toString())
    }

    @Test
    fun testNativeWindowsComInstanceCreationOnWindows() {
        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
        if (!isWindows) {
            println("Skipping Windows COM test on non-Windows OS")
            return
        }

        // Verify that Ole32 and IFileOpenDialog can be instantiated and configured via COM
        Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED)
        try {
            val ppv = PointerByReference()
            val clsidFileOpenDialog = Guid.CLSID("{DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7}")
            val clsidFileSaveDialog = Guid.CLSID("{C0B4E2F3-BA21-4773-8DBA-335EC946EB8B}")
            val iidFileDialog = Guid.IID("{42F85136-DB7E-439C-85F1-E4075D135FC8}")

            // Test Open Dialog creation
            val hrOpen = Ole32.INSTANCE.CoCreateInstance(
                clsidFileOpenDialog,
                null,
                1, // CLSCTX_INPROC_SERVER
                iidFileDialog,
                ppv
            )
            assertEquals(0, hrOpen.toInt(), "CoCreateInstance(CLSID_FileOpenDialog, IID_IFileDialog) should return 0")
            assertNotNull(ppv.value)
            com.sun.jna.platform.win32.COM.Unknown(ppv.value).Release()

            // Test Save Dialog creation
            val ppvSave = PointerByReference()
            val hrSave = Ole32.INSTANCE.CoCreateInstance(
                clsidFileSaveDialog,
                null,
                1,
                iidFileDialog,
                ppvSave
            )
            assertEquals(0, hrSave.toInt(), "CoCreateInstance(CLSID_FileSaveDialog, IID_IFileDialog) should return 0")
            assertNotNull(ppvSave.value)
            com.sun.jna.platform.win32.COM.Unknown(ppvSave.value).Release()
        } finally {
            Ole32.INSTANCE.CoUninitialize()
        }
    }

    @Test
    fun testExtensionsToFilterSpecsModpack() {
        val specs = WindowsModernFilePicker.extensionsToFilterSpecs("Select Modrinth Modpack", setOf("mrpack"))
        assertEquals(2, specs.size)
        assertEquals("Modrinth Modpack (*.mrpack)", specs[0].first)
        assertEquals("*.mrpack", specs[0].second)
        assertEquals("All Files (*.*)", specs[1].first)
        assertEquals("*.*", specs[1].second)
    }

    @Test
    fun testExtensionsToFilterSpecsSkin() {
        val specs = WindowsModernFilePicker.extensionsToFilterSpecs("Import Minecraft Skin", setOf("png"))
        assertEquals(2, specs.size)
        assertEquals("PNG Images (*.png)", specs[0].first)
        assertEquals("*.png", specs[0].second)
        assertEquals("All Files (*.*)", specs[1].first)
        assertEquals("*.*", specs[1].second)
    }

    @Test
    fun testExtensionsToFilterSpecsInstanceIcon() {
        val specs = WindowsModernFilePicker.extensionsToFilterSpecs("Select Instance Icon", setOf("png", "jpg", "jpeg", "webp"))
        assertTrue(specs.isNotEmpty())
        assertTrue(specs[0].first.startsWith("Supported Images"))
        assertTrue(specs[0].second.contains("*.png"))
        assertTrue(specs[0].second.contains("*.jpg"))
        assertTrue(specs[0].second.contains("*.webp"))
        assertEquals("All Files (*.*)", specs.last().first)
    }

    @Test
    fun testExtensionsToFilterSpecsJava() {
        val specs = WindowsModernFilePicker.extensionsToFilterSpecs("Select Java Executable", setOf("exe"))
        assertEquals(3, specs.size)
        assertEquals("Java Executable (java.exe)", specs[0].first)
        assertEquals("java.exe;javaw.exe;*.exe", specs[0].second)
        assertEquals("All Executables (*.exe)", specs[1].first)
        assertEquals("*.exe", specs[1].second)
        assertEquals("All Files (*.*)", specs[2].first)
    }

    @Test
    fun testExtensionsToFilterSpecsReleaseArtifacts() {
        val specs = WindowsModernFilePicker.extensionsToFilterSpecs("Select Release Artifact", setOf("zip", "exe", "msi", "jar"))
        assertTrue(specs.isNotEmpty())
        assertTrue(specs[0].first.startsWith("Release Artifacts"))
        assertEquals("All Files (*.*)", specs.last().first)
    }
}
