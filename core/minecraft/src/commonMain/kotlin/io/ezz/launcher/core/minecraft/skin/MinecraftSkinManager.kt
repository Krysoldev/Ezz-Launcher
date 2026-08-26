package io.ezz.launcher.core.minecraft.skin

import io.ezz.launcher.core.model.account.Account
import io.ezz.launcher.core.model.account.AccountType
import io.ezz.launcher.core.model.skin.SkinModelType
import io.ezz.launcher.core.storage.path.PathProvider
import io.ezz.launcher.core.storage.repository.VaultSkinRepository
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
import java.security.MessageDigest
import javax.imageio.ImageIO

/**
 * Unified Skin & Head Avatar Service.
 * - Single source of truth for effective player skin resolution.
 * - Manages skin caching keyed by [accountId] + [skinContentHash].
 * - Generates razor-sharp 2-layer pixel-art avatar heads (Face + Hat/Helmet layer).
 * - Reactively updates in-memory and UI state on skin modifications without launcher restart.
 */
class MinecraftSkinManager(
    private val pathProvider: PathProvider,
    private val httpClient: HttpClient,
    private val vaultSkinRepository: VaultSkinRepository? = null,
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

    /**
     * Computes the effective skin hash for an account.
     */
    fun getEffectiveSkinHash(account: Account): String {
        return when (account.type) {
            AccountType.OFFLINE -> {
                val vaultSkin = vaultSkinRepository?.getActiveSkin(account.id)
                vaultSkin?.fileHash ?: "default_steve"
            }
            AccountType.MICROSOFT -> {
                account.skinHash ?: account.skinUrl?.substringAfterLast("/")?.takeIf { it.isNotBlank() } ?: "default_steve"
            }
        }
    }

    /**
     * Gets the 2-layer composite head bytes for the account.
     * Checks memory cache, then disk cache with skinHash, and triggers background refresh if missing.
     */
    fun getHeadBytes(account: Account?): ByteArray {
        if (account == null) return defaultSteveHeadBytes

        val currentHash = getEffectiveSkinHash(account)
        val cacheKey = "${account.id}_$currentHash"

        // 1. Check in-memory composite cache
        val memory = _skinHeads.value[cacheKey] ?: _skinHeads.value[account.id]
        if (memory != null) return memory

        // 2. Check offline Vault skin directly if available
        if (account.type == AccountType.OFFLINE && vaultSkinRepository != null) {
            val vaultSkin = vaultSkinRepository.getActiveSkin(account.id)
            if (vaultSkin != null) {
                val skinBytes = vaultSkinRepository.getSkinBytes(vaultSkin)
                if (skinBytes != null && skinBytes.isNotEmpty()) {
                    val head = extractHeadFromSkinBytes(skinBytes)
                    cacheHeadToDiskAndMemory(account, currentHash, head)
                    return head
                }
            }
        }

        // 3. Check disk cache
        val diskCacheFile = getHeadCachePath(account, currentHash)
        if (fileSystem.exists(diskCacheFile)) {
            try {
                val bytes = fileSystem.read(diskCacheFile) { readByteArray() }
                if (bytes.isNotEmpty()) {
                    val updated = _skinHeads.value.toMutableMap()
                    updated[cacheKey] = bytes
                    updated[account.id] = bytes
                    _skinHeads.value = updated
                    return bytes
                }
            } catch (e: Exception) {
                // Ignore disk read error
            }
        }

        // 4. Trigger asynchronous fetch/refresh
        loadOrRefreshSkin(account)

        return defaultSteveHeadBytes
    }

    /**
     * Called when an account's skin is explicitly set or changed in Vault/online.
     * Immediately generates the head and updates UI StateFlow reactively.
     */
    fun onSkinChanged(account: Account?, newSkinBytes: ByteArray?) {
        if (account == null) return
        if (newSkinBytes != null && newSkinBytes.isNotEmpty()) {
            val hash = computeSha256(newSkinBytes)
            val headBytes = extractHeadFromSkinBytes(newSkinBytes)
            cacheHeadToDiskAndMemory(account, hash, headBytes)
        } else {
            invalidateAccountSkin(account.id)
        }
    }

    /**
     * Invalidates cached avatar in memory for the given account.
     */
    fun invalidateAccountSkin(accountId: String) {
        val updated = _skinHeads.value.toMutableMap()
        val keysToRemove = updated.keys.filter { it == accountId || it.startsWith("${accountId}_") }
        keysToRemove.forEach { updated.remove(it) }
        _skinHeads.value = updated
    }

    /**
     * Fetches the official or Vault skin, extracts head avatar, caches to disk, and updates state.
     */
    fun loadOrRefreshSkin(account: Account) {
        scope.launch {
            try {
                val currentHash = getEffectiveSkinHash(account)
                var headBytes: ByteArray? = null

                // A. For Offline Account: load from Vault
                if (account.type == AccountType.OFFLINE && vaultSkinRepository != null) {
                    val activeSkin = vaultSkinRepository.getActiveSkin(account.id)
                    if (activeSkin != null) {
                        val skinBytes = vaultSkinRepository.getSkinBytes(activeSkin)
                        if (skinBytes != null && skinBytes.isNotEmpty()) {
                            headBytes = extractHeadFromSkinBytes(skinBytes)
                        }
                    }
                }

                // B. For Online Account: download from official skinUrl
                if (headBytes == null && !account.skinUrl.isNullOrBlank()) {
                    try {
                        val response = httpClient.get(account.skinUrl!!)
                        if (response.status.isSuccess()) {
                            val skinBytes: ByteArray = response.body()
                            headBytes = extractHeadFromSkinBytes(skinBytes)

                            // Save full skin to cache/skins/
                            val skinHash = computeSha256(skinBytes)
                            val skinCacheFile = pathProvider.skinsDirectory.resolve("${skinHash}.png")
                            if (!fileSystem.exists(skinCacheFile)) {
                                fileSystem.write(skinCacheFile) { write(skinBytes) }
                            }

                            // Also register in Vault if official account skin
                            if (vaultSkinRepository != null) {
                                try {
                                    val modelType = if (account.skinModel?.equals("slim", ignoreCase = true) == true) {
                                        SkinModelType.ALEX
                                    } else {
                                        SkinModelType.STEVE
                                    }
                                    vaultSkinRepository.cacheOfficialAccountSkin(
                                        accountUsername = account.username,
                                        bytes = skinBytes,
                                        explicitModel = modelType
                                    )
                                } catch (e: Exception) {
                                    // Non-fatal
                                }
                            }
                        }
                    } catch (e: Exception) {
                        println("Note: official skin fetch failed for ${account.username}: ${e.message}")
                    }
                }

                // C. Fallback: Steve head
                val finalBytes = headBytes ?: defaultSteveHeadBytes
                cacheHeadToDiskAndMemory(account, currentHash, finalBytes)

                println("[ACCOUNT_SKIN_LOADED] Loaded skin head avatar for '${account.username}' (${finalBytes.size} bytes, hash: $currentHash)")
            } catch (e: Exception) {
                println("Warning: skin head pipeline notice: ${e.message}")
            }
        }
    }

    private fun cacheHeadToDiskAndMemory(account: Account, hash: String, headBytes: ByteArray) {
        val cacheKey = "${account.id}_$hash"
        val diskFile = getHeadCachePath(account, hash)

        try {
            val parent = diskFile.parent
            if (parent != null && !fileSystem.exists(parent)) {
                fileSystem.createDirectories(parent)
            }
            fileSystem.write(diskFile) {
                write(headBytes)
            }
        } catch (e: Exception) {
            // Ignore disk cache write failures
        }

        val updated = _skinHeads.value.toMutableMap()
        updated[cacheKey] = headBytes
        updated[account.id] = headBytes
        _skinHeads.value = updated
    }

    private fun getHeadCachePath(account: Account, hash: String): Path {
        val cleanIdentifier = (account.uuid.ifBlank { account.username }).replace("-", "").lowercase()
        return pathProvider.skinsDirectory.resolve("${cleanIdentifier}_${hash}_head.png")
    }

    fun extractHeadFromSkinBytes(skinBytes: ByteArray): ByteArray {
        val skinImage = ImageIO.read(ByteArrayInputStream(skinBytes)) ?: return defaultSteveHeadBytes

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

    fun generateSteveHeadPng(): ByteArray {
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

    private fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
