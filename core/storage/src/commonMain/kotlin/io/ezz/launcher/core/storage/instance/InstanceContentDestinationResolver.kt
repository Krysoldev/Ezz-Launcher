package io.ezz.launcher.core.storage.instance

import io.ezz.launcher.core.model.instance.InstanceContentType
import io.ezz.launcher.core.storage.path.PathProvider
import java.io.File

/**
 * Single authoritative resolver and validator for Minecraft instance content destinations.
 * Enforces strict containment inside the instance's `.minecraft` directory and prevents
 * path traversal or cross-contamination of content directories.
 */
class InstanceContentDestinationResolver(
    private val pathProvider: PathProvider
) {
    /**
     * Resolves the authoritative directory for [contentType] inside the given [instanceId].
     *
     * 1. Resolves the correct instance root.
     * 2. Resolves `.minecraft`.
     * 3. Resolves the correct content directory.
     * 4. Normalizes the path.
     * 5. Ensures it remains inside the intended instance (anti-traversal).
     * 6. Creates the directory if missing.
     * 7. Returns the final destination File.
     */
    fun resolveContentDirectory(instanceId: String, contentType: InstanceContentType): File {
        require(instanceId.isNotBlank()) { "Instance ID must not be blank" }
        require(!instanceId.contains("..") && !instanceId.contains("/") && !instanceId.contains("\\")) {
            "Invalid instance ID format: $instanceId"
        }

        val gameDirPath = pathProvider.getInstanceGameDirectory(instanceId)
        val gameDirFile = gameDirPath.toFile().canonicalFile

        // Resolve target content directory
        val targetDir = File(gameDirFile, contentType.directoryName).canonicalFile

        // Path safety & Anti-traversal check: target must be directly inside gameDirFile
        val canonicalParent = targetDir.parentFile
        if (canonicalParent == null || canonicalParent != gameDirFile) {
            throw SecurityException(
                "Path traversal attempt detected: resolved path '${targetDir.path}' is not directly inside '${gameDirFile.path}'"
            )
        }

        if (!targetDir.exists()) {
            val created = targetDir.mkdirs()
            if (!created && !targetDir.exists()) {
                throw IllegalStateException("Failed to create destination directory: ${targetDir.absolutePath}")
            }
        }

        return targetDir
    }

    /**
     * Validates that [targetFile] resides directly within the authoritative directory
     * for [contentType] and [instanceId].
     *
     * If the destination does not match, throws [IllegalStateException] or [SecurityException]
     * to prevent writing to an incorrect directory.
     */
    fun validateDestination(instanceId: String, contentType: InstanceContentType, targetFile: File): File {
        val expectedDir = resolveContentDirectory(instanceId, contentType)
        val canonicalTarget = targetFile.canonicalFile
        val canonicalParent = canonicalTarget.parentFile
            ?: throw IllegalStateException("Target file has no parent directory: ${targetFile.path}")

        if (canonicalParent != expectedDir) {
            throw IllegalStateException(
                "Destination validation mismatch for $contentType in instance '$instanceId': " +
                "file '${targetFile.name}' parent is '${canonicalParent.path}', but expected '${expectedDir.path}'"
            )
        }

        return canonicalTarget
    }

    /**
     * Resolves and ensures the content directory exists, returning the result as an [okio.Path].
     */
    fun resolveContentPath(instanceId: String, contentType: InstanceContentType): okio.Path {
        resolveContentDirectory(instanceId, contentType)
        return pathProvider.getInstanceGameDirectory(instanceId) / contentType.directoryName
    }
}
