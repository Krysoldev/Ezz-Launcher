package io.ezz.launcher.ui.platform

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.COM.COMInvoker
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

enum class FileSelectionMode {
    FILES_ONLY,
    DIRECTORIES_ONLY,
    FILES_AND_DIRECTORIES
}

/**
 * Modern Windows native file picker using the Windows Common Item Dialog API
 * (IFileOpenDialog / IFileSaveDialog), providing the modern Windows 10/11 Explorer
 * experience (Quick Access, modern folder tree, breadcrumbs, search, Windows shell styling).
 *
 * This completely avoids the legacy Windows classic dialog (GetOpenFileName / "Look in:" / "Objects of type:").
 */
object WindowsModernFilePicker {

    private val isWindows: Boolean =
        System.getProperty("os.name")?.lowercase()?.contains("win") == true

    // COM GUID constants
    // CLSID_FileOpenDialog: {DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7}
    private val CLSID_FileOpenDialog = Guid.CLSID("{DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7}")
    // CLSID_FileSaveDialog: {C0B4E2F3-BA21-4773-8DBA-335EC946EB8B}
    private val CLSID_FileSaveDialog = Guid.CLSID("{C0B4E2F3-BA21-4773-8DBA-335EC946EB8B}")
    // IID_IFileDialog: {42F85136-DB7E-439C-85F1-E4075D135FC8}
    private val IID_IFileDialog = Guid.IID("{42F85136-DB7E-439C-85F1-E4075D135FC8}")

    // IID_IShellItem: {43826D1E-E718-42EE-BC55-A1E261C37BFE}
    private val IID_IShellItem = Guid.IID("{43826D1E-E718-42EE-BC55-A1E261C37BFE}")

    // File Open/Save Dialog Options
    private const val FOS_OVERWRITEPROMPT = 0x00000002
    private const val FOS_NOCHANGEDIR = 0x00000008
    private const val FOS_PICKFOLDERS = 0x00000020
    private const val FOS_FORCEFILESYSTEM = 0x00000040
    private const val FOS_FILEMUSTEXIST = 0x00001000
    private const val FOS_PATHMUSTEXIST = 0x00000800
    private const val FOS_NOREADONLYRETURN = 0x00008000

    // SIGDN_FILESYSPATH
    private const val SIGDN_FILESYSPATH = 0x80058000.toInt()

    // CLSCTX_INPROC_SERVER
    private const val CLSCTX_INPROC_SERVER = 1

    // Shell32 interface for parsing folders
    private interface Shell32Ext : StdCallLibrary {
        fun SHCreateItemFromParsingName(
            pszPath: WString,
            pbc: Pointer?,
            riid: Guid.REFIID,
            ppv: PointerByReference
        ): WinNT.HRESULT

        companion object {
            val INSTANCE: Shell32Ext by lazy {
                Native.load("shell32", Shell32Ext::class.java)
            }
        }
    }

    /**
     * COMDLG_FILTERSPEC structure for native file filters.
     */
    @Structure.FieldOrder("pszName", "pszSpec")
    class COMDLG_FILTERSPEC : Structure {
        @JvmField var pszName: WString? = null
        @JvmField var pszSpec: WString? = null

        constructor() : super()
        constructor(p: Pointer) : super(p) { read() }
        constructor(name: String, spec: String) : super() {
            this.pszName = WString(name)
            this.pszSpec = WString(spec)
        }
    }

    /**
     * Generic COM wrapper for IFileOpenDialog and IFileSaveDialog
     */
    private class FileDialogCOM(p: Pointer) : COMInvoker() {
        init {
            pointer = p
        }

        fun Show(hwndOwner: Pointer?): Int =
            _invokeNativeInt(3, arrayOf(pointer, hwndOwner))

        fun SetFileTypes(cFileTypes: Int, rgFilterSpec: Pointer?): Int =
            _invokeNativeInt(4, arrayOf(pointer, cFileTypes, rgFilterSpec))

        fun SetFileTypeIndex(iFileType: Int): Int =
            _invokeNativeInt(5, arrayOf(pointer, iFileType))

        fun SetOptions(fos: Int): Int =
            _invokeNativeInt(9, arrayOf(pointer, fos))

        fun GetOptions(pfos: Pointer): Int =
            _invokeNativeInt(10, arrayOf(pointer, pfos))

        fun SetFolder(psi: Pointer?): Int =
            _invokeNativeInt(12, arrayOf(pointer, psi))

        fun SetFileName(pszName: WString): Int =
            _invokeNativeInt(15, arrayOf(pointer, pszName))

        fun SetTitle(pszTitle: WString): Int =
            _invokeNativeInt(17, arrayOf(pointer, pszTitle))

        fun GetResult(ppsi: Pointer): Int =
            _invokeNativeInt(20, arrayOf(pointer, ppsi))

        fun SetDefaultExtension(pszDefaultExtension: WString): Int =
            _invokeNativeInt(22, arrayOf(pointer, pszDefaultExtension))

        fun Release(): Int =
            _invokeNativeInt(2, arrayOf(pointer))
    }

    /**
     * COM wrapper for IShellItem
     */
    private class ShellItemCOM(p: Pointer) : COMInvoker() {
        init {
            pointer = p
        }

        fun GetDisplayName(sigdnName: Int, ppszName: Pointer): Int =
            _invokeNativeInt(5, arrayOf(pointer, sigdnName, ppszName))

        fun Release(): Int =
            _invokeNativeInt(2, arrayOf(pointer))
    }

    /**
     * Helper to convert a set of file extensions and a dialog title into COMDLG filter specs.
     */
    fun extensionsToFilterSpecs(title: String, allowedExtensions: Set<String>): List<Pair<String, String>> {
        val cleanExts = allowedExtensions.map { it.removePrefix(".").lowercase() }.toSet()
        if (cleanExts.isEmpty()) {
            return listOf("All Files (*.*)" to "*.*")
        }

        val specs = mutableListOf<Pair<String, String>>()

        when {
            cleanExts == setOf("mrpack") -> {
                specs.add("Modrinth Modpack (*.mrpack)" to "*.mrpack")
            }
            cleanExts == setOf("png") -> {
                specs.add("PNG Images (*.png)" to "*.png")
            }
            cleanExts.containsAll(setOf("png", "jpg")) || cleanExts.contains("webp") -> {
                val patterns = cleanExts.joinToString(";") { "*.$it" }
                specs.add("Supported Images ($patterns)" to patterns)
                if (cleanExts.contains("png")) specs.add("PNG Images (*.png)" to "*.png")
                if (cleanExts.contains("jpg") || cleanExts.contains("jpeg")) specs.add("JPEG Images (*.jpg;*.jpeg)" to "*.jpg;*.jpeg")
                if (cleanExts.contains("webp")) specs.add("WEBP Images (*.webp)" to "*.webp")
            }
            cleanExts.contains("exe") && title.contains("Java", ignoreCase = true) -> {
                specs.add("Java Executable (java.exe)" to "java.exe;javaw.exe;*.exe")
                specs.add("All Executables (*.exe)" to "*.exe")
            }
            cleanExts.contains("zip") || title.contains("Release", ignoreCase = true) -> {
                val patterns = cleanExts.joinToString(";") { "*.$it" }
                specs.add("Release Artifacts ($patterns)" to patterns)
            }
            cleanExts.contains("exe") -> {
                val patterns = cleanExts.joinToString(";") { "*.$it" }
                specs.add("Supported Files ($patterns)" to patterns)
                specs.add("Executables (*.exe)" to "*.exe")
            }
            else -> {
                val patterns = cleanExts.joinToString(";") { "*.$it" }
                val label = if (cleanExts.size == 1) "${cleanExts.first().uppercase()} Files (*.${cleanExts.first()})" else "Supported Files ($patterns)"
                specs.add(label to patterns)
            }
        }

        specs.add("All Files (*.*)" to "*.*")
        return specs
    }

    /**
     * Open modern native Windows file picker for OPENING files.
     *
     * @param title Dialog title bar text.
     * @param filterSpecs List of pairs: (Display label, filter pattern like "*.mrpack" or "*.png;*.jpg").
     * @param initialDir Optional starting folder.
     * @param defaultExtension Optional default extension without dot (e.g. "mrpack").
     * @param isFolderPicker True if picking folders instead of files.
     */
    fun openFileDialog(
        title: String,
        filterSpecs: List<Pair<String, String>>,
        initialDir: File? = null,
        defaultExtension: String? = null,
        isFolderPicker: Boolean = false
    ): File? {
        if (!isWindows) {
            return fallbackAwtDialog(title, filterSpecs, initialDir, isSave = false, defaultName = null)
        }

        return try {
            openFileDialogCom(title, filterSpecs, initialDir, defaultExtension, isFolderPicker)
        } catch (t: Throwable) {
            println("[WindowsModernFilePicker] COM dialog failed (${t.message}), falling back to PowerShell OpenFileDialog")
            fallbackPowerShellDialog(title, filterSpecs, initialDir, isSave = false, defaultName = null)
        }
    }

    /**
     * Open modern native Windows file picker for SAVING files.
     */
    fun saveFileDialog(
        title: String,
        filterSpecs: List<Pair<String, String>>,
        initialDir: File? = null,
        defaultName: String? = null,
        defaultExtension: String? = null
    ): File? {
        if (!isWindows) {
            return fallbackAwtDialog(title, filterSpecs, initialDir, isSave = true, defaultName = defaultName)
        }

        return try {
            saveFileDialogCom(title, filterSpecs, initialDir, defaultName, defaultExtension)
        } catch (t: Throwable) {
            println("[WindowsModernFilePicker] COM save dialog failed (${t.message}), falling back to PowerShell SaveFileDialog")
            fallbackPowerShellDialog(title, filterSpecs, initialDir, isSave = true, defaultName = defaultName)
        }
    }

    /**
     * COM IFileOpenDialog implementation.
     */
    private fun openFileDialogCom(
        title: String,
        filterSpecs: List<Pair<String, String>>,
        initialDir: File?,
        defaultExtension: String?,
        isFolderPicker: Boolean
    ): File? {
        // Initialize COM library on current thread
        Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED)
        var dialogPointer: Pointer? = null

        try {
            val ppv = PointerByReference()
            val hrCreate = Ole32.INSTANCE.CoCreateInstance(
                CLSID_FileOpenDialog,
                null,
                CLSCTX_INPROC_SERVER,
                IID_IFileDialog,
                ppv
            )
            if (hrCreate.toInt() != 0 || ppv.value == null) {
                throw IllegalStateException("CoCreateInstance(CLSID_FileOpenDialog) failed: $hrCreate")
            }

            dialogPointer = ppv.value
            val dialog = FileDialogCOM(dialogPointer)

            // Set Title
            dialog.SetTitle(WString(title))

            // Options: modern shell navigation, force filesystem, file must exist
            val optionsRef = IntByReference()
            dialog.GetOptions(optionsRef.pointer)
            var options = optionsRef.value or FOS_FORCEFILESYSTEM or FOS_PATHMUSTEXIST or FOS_NOCHANGEDIR
            if (!isFolderPicker) {
                options = options or FOS_FILEMUSTEXIST
            } else {
                options = options or FOS_PICKFOLDERS
            }
            dialog.SetOptions(options)

            // Set Default Extension if specified
            if (!defaultExtension.isNullOrBlank()) {
                val cleanExt = defaultExtension.removePrefix(".")
                dialog.SetDefaultExtension(WString(cleanExt))
            }

            // Set File Types
            if (filterSpecs.isNotEmpty() && !isFolderPicker) {
                val filterArray = COMDLG_FILTERSPEC().toArray(filterSpecs.size) as Array<COMDLG_FILTERSPEC>

                for (i in filterSpecs.indices) {
                    val (name, spec) = filterSpecs[i]
                    filterArray[i].pszName = WString(name)
                    filterArray[i].pszSpec = WString(spec)
                    filterArray[i].write()
                }
                dialog.SetFileTypes(filterSpecs.size, filterArray[0].pointer)
                dialog.SetFileTypeIndex(1)
            }

            // Set Initial Directory
            val resolvedDir = resolveExistingDirectory(initialDir)
            if (resolvedDir != null) {
                try {
                    val psiRef = PointerByReference()
                    val hrShell = Shell32Ext.INSTANCE.SHCreateItemFromParsingName(
                        WString(resolvedDir.absolutePath),
                        null,
                        Guid.REFIID(IID_IShellItem),
                        psiRef
                    )
                    if (hrShell.toInt() == 0 && psiRef.value != null) {
                        val psi = ShellItemCOM(psiRef.value)
                        dialog.SetFolder(psiRef.value)
                        psi.Release()
                    }
                } catch (e: Throwable) {
                    // Ignore directory parsing errors, dialog will use system default
                }
            }

            // Show Dialog parented to foreground window
            val hwndOwner = try {
                User32.INSTANCE.GetForegroundWindow()?.pointer
            } catch (e: Throwable) {
                null
            }

            val hrShow = dialog.Show(hwndOwner)
            // 0 is S_OK. User cancel returns HRESULT_FROM_WIN32(ERROR_CANCELLED) = 0x800704C7
            if (hrShow != 0) {
                return null
            }

            // Retrieve Result
            val resultPsiRef = PointerByReference()
            val hrResult = dialog.GetResult(resultPsiRef.pointer)
            if (hrResult != 0 || resultPsiRef.value == null) {
                return null
            }

            val resultShellItem = ShellItemCOM(resultPsiRef.value)
            try {
                val pathStrRef = PointerByReference()
                val hrName = resultShellItem.GetDisplayName(SIGDN_FILESYSPATH, pathStrRef.pointer)
                if (hrName == 0 && pathStrRef.value != null) {
                    val path = pathStrRef.value.getWideString(0)
                    Ole32.INSTANCE.CoTaskMemFree(pathStrRef.value)
                    if (!path.isNullOrBlank()) {
                        return File(path)
                    }
                }
            } finally {
                resultShellItem.Release()
            }

            return null
        } finally {
            if (dialogPointer != null) {
                try {
                    FileDialogCOM(dialogPointer).Release()
                } catch (e: Throwable) {
                    // ignore
                }
            }
            try {
                Ole32.INSTANCE.CoUninitialize()
            } catch (e: Throwable) {
                // ignore
            }
        }
    }

    /**
     * COM IFileSaveDialog implementation.
     */
    private fun saveFileDialogCom(
        title: String,
        filterSpecs: List<Pair<String, String>>,
        initialDir: File?,
        defaultName: String?,
        defaultExtension: String?
    ): File? {
        Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED)
        var dialogPointer: Pointer? = null

        try {
            val ppv = PointerByReference()
            val hrCreate = Ole32.INSTANCE.CoCreateInstance(
                CLSID_FileSaveDialog,
                null,
                CLSCTX_INPROC_SERVER,
                IID_IFileDialog,
                ppv
            )
            if (hrCreate.toInt() != 0 || ppv.value == null) {
                throw IllegalStateException("CoCreateInstance(CLSID_FileSaveDialog) failed: $hrCreate")
            }

            dialogPointer = ppv.value
            val dialog = FileDialogCOM(dialogPointer)

            // Set Title
            dialog.SetTitle(WString(title))

            // Options: prompt overwrite, force filesystem
            val optionsRef = IntByReference()
            dialog.GetOptions(optionsRef.pointer)
            val options = optionsRef.value or FOS_FORCEFILESYSTEM or FOS_PATHMUSTEXIST or FOS_OVERWRITEPROMPT or FOS_NOCHANGEDIR
            dialog.SetOptions(options)

            // Set Default Extension
            if (!defaultExtension.isNullOrBlank()) {
                val cleanExt = defaultExtension.removePrefix(".")
                dialog.SetDefaultExtension(WString(cleanExt))
            }

            // Set Default File Name
            if (!defaultName.isNullOrBlank()) {
                dialog.SetFileName(WString(defaultName))
            }

            // Set File Types
            if (filterSpecs.isNotEmpty()) {
                val filterArray = COMDLG_FILTERSPEC().toArray(filterSpecs.size) as Array<COMDLG_FILTERSPEC>

                for (i in filterSpecs.indices) {
                    val (name, spec) = filterSpecs[i]
                    filterArray[i].pszName = WString(name)
                    filterArray[i].pszSpec = WString(spec)
                    filterArray[i].write()
                }
                dialog.SetFileTypes(filterSpecs.size, filterArray[0].pointer)
                dialog.SetFileTypeIndex(1)
            }

            // Set Initial Directory
            val resolvedDir = resolveExistingDirectory(initialDir)
            if (resolvedDir != null) {
                try {
                    val psiRef = PointerByReference()
                    val hrShell = Shell32Ext.INSTANCE.SHCreateItemFromParsingName(
                        WString(resolvedDir.absolutePath),
                        null,
                        Guid.REFIID(IID_IShellItem),
                        psiRef
                    )
                    if (hrShell.toInt() == 0 && psiRef.value != null) {
                        val psi = ShellItemCOM(psiRef.value)
                        dialog.SetFolder(psiRef.value)
                        psi.Release()
                    }
                } catch (e: Throwable) {
                    // Ignore directory parsing errors
                }
            }

            // Show Dialog
            val hwndOwner = try {
                User32.INSTANCE.GetForegroundWindow()?.pointer
            } catch (e: Throwable) {
                null
            }

            val hrShow = dialog.Show(hwndOwner)
            if (hrShow != 0) {
                return null
            }

            // Retrieve Result
            val resultPsiRef = PointerByReference()
            val hrResult = dialog.GetResult(resultPsiRef.pointer)
            if (hrResult != 0 || resultPsiRef.value == null) {
                return null
            }

            val resultShellItem = ShellItemCOM(resultPsiRef.value)
            try {
                val pathStrRef = PointerByReference()
                val hrName = resultShellItem.GetDisplayName(SIGDN_FILESYSPATH, pathStrRef.pointer)
                if (hrName == 0 && pathStrRef.value != null) {
                    val path = pathStrRef.value.getWideString(0)
                    Ole32.INSTANCE.CoTaskMemFree(pathStrRef.value)
                    if (!path.isNullOrBlank()) {
                        return File(path)
                    }
                }
            } finally {
                resultShellItem.Release()
            }

            return null
        } finally {
            if (dialogPointer != null) {
                try {
                    FileDialogCOM(dialogPointer).Release()
                } catch (e: Throwable) {
                    // ignore
                }
            }
            try {
                Ole32.INSTANCE.CoUninitialize()
            } catch (e: Throwable) {
                // ignore
            }
        }
    }

    private fun resolveExistingDirectory(initial: File?): File? {
        if (initial == null) return null
        return when {
            initial.exists() && initial.isDirectory -> initial
            initial.parentFile != null && initial.parentFile.exists() && initial.parentFile.isDirectory -> initial.parentFile
            else -> null
        }
    }

    /**
     * Fallback for Windows if COM invocation throws an unexpected exception.
     * Uses .NET System.Windows.Forms with AutoUpgradeEnabled = true (which renders the modern IFileOpenDialog).
     */
    private fun fallbackPowerShellDialog(
        title: String,
        filterSpecs: List<Pair<String, String>>,
        initialDir: File?,
        isSave: Boolean,
        defaultName: String?
    ): File? {
        return try {
            val filterString = if (filterSpecs.isNotEmpty()) {
                filterSpecs.joinToString("|") { "${it.first}|${it.second}" }
            } else {
                "All Files (*.*)|*.*"
            }

            val resolvedDir = resolveExistingDirectory(initialDir)?.absolutePath?.replace("'", "''") ?: ""
            val escapedTitle = title.replace("'", "''")
            val escapedDefaultName = (defaultName ?: "").replace("'", "''")

            val dialogClass = if (isSave) "SaveFileDialog" else "OpenFileDialog"

            val script = buildString {
                append("[System.Reflection.Assembly]::LoadWithPartialName('System.Windows.Forms') | Out-Null; ")
                append("\$d = New-Object System.Windows.Forms.$dialogClass; ")
                append("\$d.AutoUpgradeEnabled = \$true; ")
                append("\$d.Title = '$escapedTitle'; ")
                append("\$d.Filter = '$filterString'; ")
                if (resolvedDir.isNotBlank()) {
                    append("\$d.InitialDirectory = '$resolvedDir'; ")
                }
                if (isSave && escapedDefaultName.isNotBlank()) {
                    append("\$d.FileName = '$escapedDefaultName'; ")
                }
                append("if (\$d.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) { Write-Output \$d.FileName }")
            }

            val process = ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script)
                .redirectErrorStream(true)
                .start()

            val output = InputStreamReader(process.inputStream, StandardCharsets.UTF_8).use { it.readText() }.trim()
            process.waitFor()

            if (output.isNotBlank() && File(output).let { if (isSave) true else it.exists() }) {
                File(output)
            } else {
                null
            }
        } catch (e: Throwable) {
            println("[WindowsModernFilePicker] PowerShell fallback also failed: ${e.message}")
            null
        }
    }

    /**
     * Non-Windows fallback (Linux/macOS).
     */
    private fun fallbackAwtDialog(
        title: String,
        filterSpecs: List<Pair<String, String>>,
        initialDir: File?,
        isSave: Boolean,
        defaultName: String?
    ): File? {
        return try {
            val mode = if (isSave) java.awt.FileDialog.SAVE else java.awt.FileDialog.LOAD
            val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, mode)
            val resolvedDir = resolveExistingDirectory(initialDir)
            if (resolvedDir != null) {
                dialog.directory = resolvedDir.absolutePath
            }
            if (isSave && !defaultName.isNullOrBlank()) {
                dialog.file = defaultName
            }
            dialog.isVisible = true
            val selectedFile = dialog.file
            val selectedDir = dialog.directory
            if (selectedFile != null && selectedDir != null) {
                File(selectedDir, selectedFile)
            } else {
                null
            }
        } catch (e: Throwable) {
            null
        }
    }
}
