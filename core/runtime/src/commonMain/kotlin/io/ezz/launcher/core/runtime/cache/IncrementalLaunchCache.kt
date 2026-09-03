package io.ezz.launcher.core.runtime.cache

import okio.FileSystem
import okio.Path
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance in-memory and incremental validation cache for Minecraft libraries,
 * client jars, assets, and extracted natives.
 *
 * Avoids repeated disk latency and expensive SHA-1 recalculations when files have not been modified.
 */
object IncrementalLaunchCache {

    private data class CacheEntry(
        val size: Long,
        val lastModified: Long,
        val isValid: Boolean
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    /**
     * Checks if a local library or jar file is already validated and unchanged.
     */
    fun isFileValid(path: Path, expectedSize: Long? = null, fileSystem: FileSystem = FileSystem.SYSTEM): Boolean {
        return isFileValid(path.toNioPath().toFile(), expectedSize)
    }

    /**
     * Checks if a file matches the cached validated state.
     */
    fun isFileValid(file: File, expectedSize: Long? = null): Boolean {
        if (!file.exists() || !file.isFile) return false
        val currentSize = file.length()
        if (currentSize <= 0L) return false
        if (expectedSize != null && expectedSize > 0L && currentSize != expectedSize) return false

        val currentMtime = file.lastModified()
        val key = file.absolutePath
        val entry = cache[key]

        if (entry != null && entry.isValid && entry.size == currentSize && entry.lastModified == currentMtime) {
            return true
        }

        // Fresh validation
        val isValid = currentSize > 0L
        if (isValid) {
            cache[key] = CacheEntry(size = currentSize, lastModified = currentMtime, isValid = true)
        }
        return isValid
    }

    /**
     * Explicitly marks a path as verified and valid.
     */
    fun markValid(file: File) {
        if (file.exists() && file.isFile && file.length() > 0L) {
            cache[file.absolutePath] = CacheEntry(
                size = file.length(),
                lastModified = file.lastModified(),
                isValid = true
            )
        }
    }

    /**
     * Invalidates a file from the cache (e.g. if a download failed or corrupted).
     */
    fun invalidate(file: File) {
        cache.remove(file.absolutePath)
    }

    /**
     * Clears all cached validation states.
     */
    fun clear() {
        cache.clear()
    }
}
