package io.ezz.launcher.ui.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.ezz.launcher.core.network.modrinth.ModrinthService
import io.ezz.launcher.core.storage.path.PathProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

/**
 * High-performance image caching and loading manager for Modrinth icons, covers, and screenshots.
 * - In-Memory Cache: Fast zero-latency access across re-renders.
 * - Disk Cache: Stored persistently in <cacheDir>/modrinth/images/<md5>.png.
 * - Background Async Fetch: Does not block UI thread.
 */
class ModrinthImageLoader(
    private val pathProvider: PathProvider,
    private val modrinthService: ModrinthService,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val memoryCache = ConcurrentHashMap<String, ImageBitmap>()
    private val loadingUrls = ConcurrentHashMap.newKeySet<String>()

    private val diskCacheDir: File by lazy {
        val dir = pathProvider.cacheDirectory.resolve("modrinth").resolve("images").toFile()
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    /**
     * Retrieve an ImageBitmap from memory cache or disk if available.
     * If not available, triggers background fetch and notifies state.
     */
    fun getImageBitmap(url: String?): ImageBitmap? {
        if (url.isNullOrBlank()) return null

        // 1. Memory cache hit
        memoryCache[url]?.let { return it }

        // 2. Check disk cache
        val diskFile = getDiskCacheFile(url)
        if (diskFile.exists() && diskFile.length() > 0) {
            try {
                val bytes = diskFile.readBytes()
                val bufferedImage = ImageIO.read(ByteArrayInputStream(bytes))
                if (bufferedImage != null) {
                    val bitmap = bufferedImage.toComposeImageBitmap()
                    memoryCache[url] = bitmap
                    return bitmap
                }
            } catch (e: Throwable) {
                // Corrupted cache file, delete
                diskFile.delete()
            }
        }

        // 3. Trigger async download if not already fetching
        if (loadingUrls.add(url)) {
            scope.launch {
                fetchAndCache(url)
            }
        }

        return null
    }

    /**
     * Suspending version that waits for image load.
     */
    suspend fun loadBitmap(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        memoryCache[url]?.let { return@withContext it }

        val diskFile = getDiskCacheFile(url)
        if (diskFile.exists() && diskFile.length() > 0) {
            try {
                val bytes = diskFile.readBytes()
                val bufferedImage = ImageIO.read(ByteArrayInputStream(bytes))
                if (bufferedImage != null) {
                    val bitmap = bufferedImage.toComposeImageBitmap()
                    memoryCache[url] = bitmap
                    return@withContext bitmap
                }
            } catch (e: Throwable) {
                diskFile.delete()
            }
        }

        fetchAndCache(url)
    }

    private suspend fun fetchAndCache(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        try {
            val bytes = modrinthService.downloadImageBytes(url) ?: return@withContext null
            val diskFile = getDiskCacheFile(url)
            diskFile.parentFile?.mkdirs()
            diskFile.writeBytes(bytes)

            val bufferedImage = ImageIO.read(ByteArrayInputStream(bytes))
            if (bufferedImage != null) {
                val bitmap = bufferedImage.toComposeImageBitmap()
                memoryCache[url] = bitmap
                bitmap
            } else {
                null
            }
        } catch (e: Throwable) {
            null
        } finally {
            loadingUrls.remove(url)
        }
    }

    private fun getDiskCacheFile(url: String): File {
        val hash = hashUrl(url)
        return File(diskCacheDir, "$hash.png")
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
