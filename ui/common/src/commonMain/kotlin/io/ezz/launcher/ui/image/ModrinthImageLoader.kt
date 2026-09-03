package io.ezz.launcher.ui.image

import androidx.compose.ui.graphics.ImageBitmap
import io.ezz.launcher.core.network.modrinth.ModrinthService
import io.ezz.launcher.core.storage.path.PathProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance image caching and loading manager for Modrinth icons, covers, and screenshots.
 * - In-Memory Cache: Fast zero-latency access across re-renders.
 * - Disk Cache: Stored persistently in <cacheDir>/modrinth/images/<md5>.png/webp.
 * - Request Deduplication: In-flight requests for the same URL share a single coroutine Deferred.
 * - Multi-Format Support: Decodes WebP, PNG, JPEG, GIF, and BMP via ImageDecoder.
 * - Offline Cache: Previously cached images load immediately even without internet.
 */
class ModrinthImageLoader(
    private val pathProvider: PathProvider,
    private val modrinthService: ModrinthService,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val memoryCache = ConcurrentHashMap<String, ImageBitmap>()
    private val inFlightRequests = ConcurrentHashMap<String, Deferred<ImageBitmap?>>()

    private val diskCacheDir: File by lazy {
        val dir = pathProvider.cacheDirectory.resolve("modrinth").resolve("images").toFile()
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    /**
     * Retrieve an ImageBitmap from memory cache or disk if available synchronously.
     * If not available, triggers background fetch and deduplicates requests.
     */
    fun getImageBitmap(url: String?): ImageBitmap? {
        if (url.isNullOrBlank()) return null

        // 1. In-Memory Cache Hit
        memoryCache[url]?.let { return it }

        // 2. Direct Local File Check
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            val localFile = File(url)
            if (localFile.exists() && localFile.length() > 0) {
                val bitmap = ImageDecoder.decodeFile(localFile)
                if (bitmap != null) {
                    memoryCache[url] = bitmap
                    return bitmap
                }
            }
            return null
        }

        // 3. Persistent Disk Cache Check
        val diskFile = getDiskCacheFile(url)
        if (diskFile.exists() && diskFile.length() > 0) {
            val bitmap = ImageDecoder.decodeFile(diskFile)
            if (bitmap != null) {
                memoryCache[url] = bitmap
                return bitmap
            } else {
                // Corrupted cache file, discard
                diskFile.delete()
            }
        }

        // 4. Trigger async download if not already in-flight
        scope.launch {
            loadBitmap(url)
        }

        return null
    }

    /**
     * Suspending version that waits for image load with request deduplication.
     */
    suspend fun loadBitmap(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null

        // 1. Memory Cache
        memoryCache[url]?.let { return@withContext it }

        // 2. Direct Local File Check
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            val localFile = File(url)
            if (localFile.exists() && localFile.length() > 0) {
                val bitmap = ImageDecoder.decodeFile(localFile)
                if (bitmap != null) {
                    memoryCache[url] = bitmap
                    return@withContext bitmap
                }
            }
            return@withContext null
        }

        // 3. Disk Cache
        val diskFile = getDiskCacheFile(url)
        if (diskFile.exists() && diskFile.length() > 0) {
            val bitmap = ImageDecoder.decodeFile(diskFile)
            if (bitmap != null) {
                memoryCache[url] = bitmap
                return@withContext bitmap
            } else {
                diskFile.delete()
            }
        }

        // 3. Network Fetch with Deduplication
        val deferred = inFlightRequests.computeIfAbsent(url) {
            scope.async(Dispatchers.IO) {
                fetchAndCache(url, diskFile)
            }
        }

        try {
            deferred.await()
        } finally {
            inFlightRequests.remove(url)
        }
    }

    private suspend fun fetchAndCache(url: String, diskFile: File): ImageBitmap? = withContext(Dispatchers.IO) {
        try {
            val bytes = modrinthService.downloadImageBytes(url)
            if (bytes == null || bytes.isEmpty()) {
                println("[ModrinthImage] URL: $url | Status: FAILED | Result: FALLBACK")
                return@withContext null
            }

            // Save to disk cache
            diskFile.parentFile?.mkdirs()
            diskFile.writeBytes(bytes)

            // Decode image
            val bitmap = ImageDecoder.decodeBytes(bytes)
            if (bitmap != null) {
                memoryCache[url] = bitmap
                println("[ModrinthImage] URL: $url | Status: 200 | Cache: MISS | Saved: ${diskFile.name} | Result: SUCCESS")
                bitmap
            } else {
                diskFile.delete()
                println("[ModrinthImage] URL: $url | Decode: FAILED | Result: FALLBACK")
                null
            }
        } catch (e: Throwable) {
            println("[ModrinthImage] URL: $url | Error: ${e.message} | Result: FALLBACK")
            null
        }
    }

    /**
     * Load a local image file directly into an ImageBitmap and cache it in memory.
     */
    fun loadLocalFile(file: File): ImageBitmap? {
        val path = file.absolutePath
        memoryCache[path]?.let { return it }

        val bitmap = ImageDecoder.decodeFile(file)
        if (bitmap != null) {
            memoryCache[path] = bitmap
        }
        return bitmap
    }

    /**
     * Clear in-memory cache for a specific key or entirely.
     */
    fun clearMemoryCache(key: String? = null) {
        if (key != null) {
            memoryCache.remove(key)
        } else {
            memoryCache.clear()
        }
    }

    private fun getDiskCacheFile(url: String): File {
        val hash = hashUrl(url)
        return File(diskCacheDir, "$hash.img")
    }

    private fun hashUrl(url: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(url.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Throwable) {
            url.hashCode().toString()
        }
    }
}
