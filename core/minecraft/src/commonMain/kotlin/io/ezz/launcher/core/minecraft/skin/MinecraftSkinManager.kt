package io.ezz.launcher.core.minecraft.skin

import io.ezz.launcher.core.model.account.Account
import io.ezz.launcher.core.storage.path.PathProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * High-performance Minecraft Skin & Head Avatar Service.
 * - Extracts and composites the 2-layer Minecraft head (Face + Hat/Helmet layer).
 * - Applies Nearest-Neighbor scaling for razor-sharp pixel art.
 * - Manages local disk caching (cache/skins/{uuid}_head.png).
 * - Provides immediate cached heads upon startup.
 */
class MinecraftSkinManager(
    private val pathProvider: PathProvider,
    private val httpClient: HttpClient,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val scope = CoroutineScope(dispatcher)
    private val _skinHeads = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    val skinHeads: StateFlow<Map<String, ByteArray>> = _skinHeads.asStateFlow()

    private val defaultSteveHeadBytes: ByteArray by lazy {
        generateSteveHeadPng()
    }

    init {
        pathProvider.initializeDirectories(fileSystem)
    }

    fun getHeadBytes(account: Account?): ByteArray {
        if (account == null) return defaultSteveHeadBytes
        val memory = _skinHeads.value[account.id]
        if (memory != null) return memory

        val cacheFile = getHeadCachePath(account)
        if (fileSystem.exists(cacheFile)) {
            try {
                val bytes = fileSystem.read(cacheFile) { readByteArray() }
                if (bytes.isNotEmpty()) {
                    val updated = _skinHeads.value.toMutableMap()
                    updated[account.id] = bytes
                    _skinHeads.value = updated
                    return bytes
                }
            } catch (e: Exception) {
                println("Note: could not read cached skin head for ${account.username}: ${e.message}")
            }
        }

        // Trigger background load/refresh
        loadOrRefreshSkin(account)

        return defaultSteveHeadBytes
    }

    fun loadOrRefreshSkin(account: Account) {
        scope.launch {
            try {
                val cacheFile = getHeadCachePath(account)
                var headBytes: ByteArray? = null

                // 1. If skinUrl is available from Microsoft authentication
                if (!account.skinUrl.isNullOrBlank()) {
                    try {
                        val response = httpClient.get(account.skinUrl!!)
                        if (response.status.isSuccess()) {
                            val skinBytes: ByteArray = response.body()
                            headBytes = extractHeadFromSkinBytes(skinBytes)
                        }
                    } catch (e: Exception) {
                        println("Note: fetching skin texture failed: ${e.message}")
                    }
                }

                // 2. If headBytes not obtained, query avatar head service
                if (headBytes == null) {
                    val candidateUrls = listOf(
                        "https://mc-heads.net/avatar/${account.username}/64",
                        "https://minotar.net/helm/${account.username}/64.png"
                    )

                    for (url in candidateUrls) {
                        try {
                            val resp = httpClient.get(url)
                            if (resp.status.isSuccess()) {
                                val bytes: ByteArray = resp.body()
                                if (bytes.isNotEmpty()) {
                                    headBytes = bytes
                                    break
                                }
                            }
                        } catch (e: Exception) {
                            // try next
                        }
                    }
                }

                // 3. Fallback to Steve head if network unavailable
                val finalBytes = headBytes ?: defaultSteveHeadBytes

                // Cache to disk
                try {
                    val parent = cacheFile.parent
                    if (parent != null && !fileSystem.exists(parent)) {
                        fileSystem.createDirectories(parent)
                    }
                    fileSystem.write(cacheFile) {
                        write(finalBytes)
                    }
                } catch (e: Exception) {
                    println("Note: failed to cache skin head to disk: ${e.message}")
                }

                // Update in-memory state
                withContext(Dispatchers.Main) {
                    val updated = _skinHeads.value.toMutableMap()
                    updated[account.id] = finalBytes
                    _skinHeads.value = updated
                }

                println("[ACCOUNT_SKIN_LOADED] Loaded skin head avatar for '${account.username}' (${finalBytes.size} bytes)")
            } catch (e: Exception) {
                println("Warning: skin head pipeline completed with notice: ${e.message}")
            }
        }
    }

    private fun getHeadCachePath(account: Account): Path {
        val cleanIdentifier = (account.uuid.ifBlank { account.username }).replace("-", "").lowercase()
        return pathProvider.skinsDirectory.resolve("${cleanIdentifier}_head.png")
    }

    private fun extractHeadFromSkinBytes(skinBytes: ByteArray): ByteArray {
        val skinImage = ImageIO.read(ByteArrayInputStream(skinBytes)) ?: return defaultSteveHeadBytes

        // Minecraft skins are 64x64 or 64x32
        val width = skinImage.width
        val height = skinImage.height

        // 8x8 base face at (8, 8, 8, 8)
        val head8 = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
        val g8: Graphics2D = head8.createGraphics()

        if (width >= 16 && height >= 16) {
            val face = skinImage.getSubimage(8, 8, 8, 8)
            g8.drawImage(face, 0, 0, null)
        }

        // 8x8 hat/helmet layer overlay at (40, 8, 8, 8)
        if (width >= 48 && height >= 16) {
            val hat = skinImage.getSubimage(40, 8, 8, 8)
            g8.drawImage(hat, 0, 0, null)
        }
        g8.dispose()

        // Upscale 8x8 -> 64x64 with Nearest-Neighbor for crisp Minecraft pixel art
        val scaled = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
        val gs: Graphics2D = scaled.createGraphics()
        gs.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        gs.drawImage(head8, 0, 0, 64, 64, null)
        gs.dispose()

        val baos = ByteArrayOutputStream()
        ImageIO.write(scaled, "PNG", baos)
        return baos.toByteArray()
    }

    private fun generateSteveHeadPng(): ByteArray {
        val steveFacePixels = intArrayOf(
            0xFF2A1C12.toInt(), 0xFF2A1C12.toInt(), 0xFF2A1C12.toInt(), 0xFF2A1C12.toInt(), 0xFF2A1C12.toInt(), 0xFF2A1C12.toInt(), 0xFF2A1C12.toInt(), 0xFF2A1C12.toInt(),
            0xFF2A1C12.toInt(), 0xFF2A1C12.toInt(), 0xFF2A1C12.toInt(), 0xFF2A1C12.toInt(), 0xFF2A1C12.toInt(), 0xFF2A1C12.toInt(), 0xFF2A1C12.toInt(), 0xFF2A1C12.toInt(),
            0xFF2A1C12.toInt(), 0xFF2A1C12.toInt(), 0xFFB6896C.toInt(), 0xFFB6896C.toInt(), 0xFFB6896C.toInt(), 0xFFB6896C.toInt(), 0xFF2A1C12.toInt(), 0xFF2A1C12.toInt(),
            0xFFB6896C.toInt(), 0xFFB6896C.toInt(), 0xFFB6896C.toInt(), 0xFFB6896C.toInt(), 0xFFB6896C.toInt(), 0xFFB6896C.toInt(), 0xFFB6896C.toInt(), 0xFFB6896C.toInt(),
            0xFFFFFFFF.toInt(), 0xFF3C44AA.toInt(), 0xFFB6896C.toInt(), 0xFF875A3C.toInt(), 0xFF875A3C.toInt(), 0xFFB6896C.toInt(), 0xFF3C44AA.toInt(), 0xFFFFFFFF.toInt(),
            0xFFB6896C.toInt(), 0xFFB6896C.toInt(), 0xFF875A3C.toInt(), 0xFF875A3C.toInt(), 0xFF875A3C.toInt(), 0xFF875A3C.toInt(), 0xFFB6896C.toInt(), 0xFFB6896C.toInt(),
            0xFFB6896C.toInt(), 0xFF4A3222.toInt(), 0xFF4A3222.toInt(), 0xFF4A3222.toInt(), 0xFF4A3222.toInt(), 0xFF4A3222.toInt(), 0xFF4A3222.toInt(), 0xFFB6896C.toInt(),
            0xFFB6896C.toInt(), 0xFFB6896C.toInt(), 0xFF4A3222.toInt(), 0xFF4A3222.toInt(), 0xFF4A3222.toInt(), 0xFF4A3222.toInt(), 0xFFB6896C.toInt(), 0xFFB6896C.toInt()
        )

        val steve8 = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
        steve8.setRGB(0, 0, 8, 8, steveFacePixels, 0, 8)

        val scaled = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
        val gs = scaled.createGraphics()
        gs.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        gs.drawImage(steve8, 0, 0, 64, 64, null)
        gs.dispose()

        val baos = ByteArrayOutputStream()
        ImageIO.write(scaled, "PNG", baos)
        return baos.toByteArray()
    }
}
