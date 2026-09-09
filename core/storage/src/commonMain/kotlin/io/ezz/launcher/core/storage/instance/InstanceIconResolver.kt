package io.ezz.launcher.core.storage.instance

import io.ezz.launcher.core.model.instance.Instance
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

/**
 * Authoritative resolver and validator for Minecraft instance icons and artwork.
 * Provides consistent icon discovery and image validation across:
 * - UI rendering (Instance card, header, dialogs)
 * - Modpack export (.mrpack archive creation)
 * - Modpack import (.mrpack extraction and instance registration)
 */
object InstanceIconResolver {

    private val ICON_FILE_NAMES = listOf(
        "icon.png",
        "icon.webp",
        "icon.jpg",
        "icon.jpeg",
        "icon.gif",
        "icon.bmp",
        "pack.png",
        "logo.png"
    )

    /**
     * Resolves the authoritative local icon file for an instance.
     * Follows a strict priority hierarchy:
     * 1. Explicit `instance.customIconPath` (absolute or relative to instanceDir / gameDir)
     * 2. Instance directory root (`instanceDir/icon.png`, `icon.webp`, `pack.png`, etc.)
     * 3. Minecraft game directory (`gameDir/icon.png`, `gameDir/pack.png`, etc.)
     * 4. User data roaming directories for this instance ID
     */
    fun resolveIconFile(
        instance: Instance,
        instanceDir: File? = null,
        gameDir: File? = instanceDir?.let { File(it, ".minecraft") }
    ): File? {
        // 1. Explicit customIconPath
        val explicitPath = instance.customIconPath?.trim()
        if (!explicitPath.isNullOrBlank()) {
            val directFile = File(explicitPath)
            if (directFile.exists() && directFile.length() > 0L) {
                return directFile
            }

            if (instanceDir != null) {
                val relInstance = File(instanceDir, explicitPath)
                if (relInstance.exists() && relInstance.length() > 0L) return relInstance
            }

            if (gameDir != null) {
                val relGame = File(gameDir, explicitPath)
                if (relGame.exists() && relGame.length() > 0L) return relGame
            }
        }

        // 2. Search inside instanceDir
        if (instanceDir != null && instanceDir.exists()) {
            for (name in ICON_FILE_NAMES) {
                val file = File(instanceDir, name)
                if (file.exists() && file.length() > 0L) return file
            }
        }

        // 3. Search inside gameDir (.minecraft)
        if (gameDir != null && gameDir.exists()) {
            for (name in ICON_FILE_NAMES) {
                val file = File(gameDir, name)
                if (file.exists() && file.length() > 0L) return file
            }
        }

        // 4. Fallback search in standard launcher instance storage roots
        val userHome = System.getProperty("user.home") ?: "."
        val fallbackRoots = listOf(
            File(userHome, ".ezzlauncher/instances/${instance.id}"),
            File(userHome, "AppData/Roaming/.ezzlauncher/instances/${instance.id}"),
            File(userHome, ".ezz/instances/${instance.id}"),
            File(userHome, "AppData/Roaming/.ezz/instances/${instance.id}")
        )

        for (root in fallbackRoots) {
            if (!root.exists()) continue
            for (name in ICON_FILE_NAMES) {
                val rootFile = File(root, name)
                if (rootFile.exists() && rootFile.length() > 0L) return rootFile

                val dotMcFile = File(File(root, ".minecraft"), name)
                if (dotMcFile.exists() && dotMcFile.length() > 0L) return dotMcFile
            }
        }

        return null
    }

    /**
     * Resolves and validates instance icon bytes specifically for .mrpack export.
     * Ensures:
     * - Checks whether instance has a custom logo assigned or discoverable.
     * - If no custom logo exists, returns `Result.success(null)` to preserve existing fallback behavior.
     * - If an icon is present, verifies file existence, readability, non-zero size, and valid image header.
     * - Returns exact PNG bytes directly (preserving full quality and alpha transparency).
     * - Converts non-PNG images (JPEG, GIF, BMP) to standard PNG format for cross-launcher compatibility.
     * - If reading or validation fails, returns `Result.failure` with an explanatory reason.
     */
    fun resolveIconBytesForExport(
        instance: Instance,
        instanceDir: File,
        gameDir: File = File(instanceDir, ".minecraft")
    ): Result<ByteArray?> {
        val iconFile = resolveIconFile(instance, instanceDir, gameDir)

        if (iconFile == null) {
            val declaredPath = instance.customIconPath?.trim()
            if (!declaredPath.isNullOrBlank()) {
                return Result.failure(
                    IOException("MRPACK_ICON_EXPORT_FAILED: Declared icon path '$declaredPath' was not found on disk.")
                )
            }
            // Instance has no custom logo assigned -> Valid standard fallback
            return Result.success(null)
        }

        if (!iconFile.exists()) {
            return Result.failure(
                IOException("MRPACK_ICON_EXPORT_FAILED: Icon file '${iconFile.absolutePath}' does not exist.")
            )
        }

        if (!iconFile.canRead()) {
            return Result.failure(
                SecurityException("MRPACK_ICON_EXPORT_FAILED: Icon file '${iconFile.absolutePath}' is not readable (permission denied).")
            )
        }

        val length = iconFile.length()
        if (length <= 0L) {
            return Result.failure(
                IllegalArgumentException("MRPACK_ICON_EXPORT_FAILED: Icon file '${iconFile.name}' is empty (0 bytes).")
            )
        }

        val bytes = try {
            iconFile.readBytes()
        } catch (e: Throwable) {
            return Result.failure(
                IOException("MRPACK_ICON_EXPORT_FAILED: Failed to read bytes from '${iconFile.name}': ${e.message}", e)
            )
        }

        if (!isValidImageHeader(bytes)) {
            return Result.failure(
                IllegalArgumentException("MRPACK_ICON_EXPORT_FAILED: File '${iconFile.name}' does not have a supported image signature (PNG, JPEG, WebP, GIF, BMP).")
            )
        }

        // If already a valid PNG: return original bytes directly without altering
        if (isPng(bytes)) {
            return Result.success(bytes)
        }

        // Non-PNG format: convert to standard PNG format for full cross-launcher .mrpack compatibility
        try {
            val bufferedImage = ImageIO.read(ByteArrayInputStream(bytes))
            if (bufferedImage != null) {
                val baos = ByteArrayOutputStream()
                val written = ImageIO.write(bufferedImage, "PNG", baos)
                if (written) {
                    val convertedBytes = baos.toByteArray()
                    if (convertedBytes.isNotEmpty() && isPng(convertedBytes)) {
                        return Result.success(convertedBytes)
                    }
                }
            }
        } catch (_: Throwable) {
            // If conversion fails, fallback to passing validated image bytes
        }

        return Result.success(bytes)
    }

    /**
     * Checks if the byte array starts with the official 8-byte PNG file signature:
     * 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
     */
    fun isPng(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        return bytes[0] == 0x89.toByte() &&
               bytes[1] == 0x50.toByte() &&
               bytes[2] == 0x4E.toByte() &&
               bytes[3] == 0x47.toByte() &&
               bytes[4] == 0x0D.toByte() &&
               bytes[5] == 0x0A.toByte() &&
               bytes[6] == 0x1A.toByte() &&
               bytes[7] == 0x0A.toByte()
    }

    /**
     * Validates header bytes against standard image formats (PNG, JPEG, GIF, WebP, BMP).
     */
    fun isValidImageHeader(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false

        // PNG: 89 50 4E 47
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) {
            return true
        }

        // JPEG: FF D8 FF
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
            return true
        }

        // GIF: GIF87a or GIF89a
        if (bytes.size >= 6 &&
            bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte()) {
            return true
        }

        // WebP: RIFF....WEBP
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() && bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()) {
            return true
        }

        // BMP: BM (0x42, 0x4D)
        if (bytes.size >= 2 && bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte()) {
            return true
        }

        return false
    }
}
