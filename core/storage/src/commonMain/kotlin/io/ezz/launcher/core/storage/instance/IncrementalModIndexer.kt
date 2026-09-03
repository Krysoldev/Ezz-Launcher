package io.ezz.launcher.core.storage.instance

import io.ezz.launcher.core.model.instance.LocalMod
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Fast in-memory cache for parsed mod metadata.
 * Avoids repeatedly decompressing ZIP archives (`fabric.mod.json`, `mcmod.info`)
 * when mod files have not changed.
 */
object IncrementalModIndexer {

    private data class ModCacheEntry(
        val fileSize: Long,
        val lastModified: Long,
        val mod: LocalMod
    )

    private val cache = ConcurrentHashMap<String, ModCacheEntry>()

    /**
     * Gets cached mod metadata if file is unchanged.
     */
    fun getCached(file: File): LocalMod? {
        val entry = cache[file.absolutePath] ?: return null
        if (entry.fileSize == file.length() && entry.lastModified == file.lastModified()) {
            val isEnabled = !file.name.endsWith(".disabled", ignoreCase = true)
            return if (entry.mod.enabled != isEnabled) {
                entry.mod.copy(enabled = isEnabled)
            } else {
                entry.mod
            }
        }
        return null
    }

    /**
     * Stores parsed mod metadata in cache.
     */
    fun put(file: File, mod: LocalMod) {
        if (file.exists()) {
            cache[file.absolutePath] = ModCacheEntry(
                fileSize = file.length(),
                lastModified = file.lastModified(),
                mod = mod
            )
        }
    }

    /**
     * Invalidates a file from the mod cache.
     */
    fun invalidate(file: File) {
        cache.remove(file.absolutePath)
    }

    /**
     * Clears all cached mod indexes.
     */
    fun clear() {
        cache.clear()
    }
}
