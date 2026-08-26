package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.model.skin.SkinModelType
import io.ezz.launcher.core.model.skin.VaultManifest
import io.ezz.launcher.core.model.skin.VaultSkin
import io.ezz.launcher.core.storage.path.PathProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID
import javax.imageio.ImageIO

interface VaultSkinRepository {
    val manifest: StateFlow<VaultManifest>
    val skins: StateFlow<List<VaultSkin>>
    val activeSkinId: StateFlow<String?>

    fun getActiveSkin(accountId: String? = null): VaultSkin?
    fun getSkin(skinId: String): VaultSkin?
    fun getSkinFilePath(skin: VaultSkin): Path
    fun getSkinBytes(skin: VaultSkin): ByteArray?
    suspend fun importSkin(bytes: ByteArray, preferredName: String?, explicitModel: SkinModelType? = null): Result<VaultSkin>
    suspend fun setActiveSkin(skinId: String?, accountId: String? = null)
    suspend fun renameSkin(skinId: String, newName: String): Result<VaultSkin>
    suspend fun updateSkinModel(skinId: String, modelType: SkinModelType): Result<VaultSkin>
    suspend fun deleteSkin(skinId: String): Boolean
    suspend fun cacheOfficialAccountSkin(accountUsername: String, bytes: ByteArray, explicitModel: SkinModelType? = null): VaultSkin
    fun findDuplicateByHash(fileHash: String): VaultSkin?
    fun detectModelType(bytes: ByteArray): SkinModelType
    fun computeSha256(bytes: ByteArray): String
}

class LocalVaultSkinRepository(
    private val pathProvider: PathProvider,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : VaultSkinRepository {

    private val scope = CoroutineScope(dispatcher)
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val manifestPath: Path get() = pathProvider.vaultDirectory.resolve("vault_manifest.json")

    private val _manifest = MutableStateFlow(VaultManifest())
    override val manifest: StateFlow<VaultManifest> = _manifest.asStateFlow()

    private val _skins = MutableStateFlow<List<VaultSkin>>(emptyList())
    override val skins: StateFlow<List<VaultSkin>> = _skins.asStateFlow()

    private val _activeSkinId = MutableStateFlow<String?>(null)
    override val activeSkinId: StateFlow<String?> = _activeSkinId.asStateFlow()

    init {
        pathProvider.initializeDirectories(fileSystem)
        loadManifest()
    }

    private fun loadManifest() {
        if (!fileSystem.exists(manifestPath)) {
            saveManifest(VaultManifest())
            return
        }

        try {
            val content = fileSystem.read(manifestPath) { readUtf8() }
            val loaded = json.decodeFromString<VaultManifest>(content)
            _manifest.value = loaded
            _skins.value = loaded.skins
            _activeSkinId.value = loaded.activeSkinId
        } catch (e: Exception) {
            println("Warning: failed to parse vault_manifest.json: ${e.message}. Rebuilding clean state.")
            val fresh = VaultManifest()
            saveManifest(fresh)
        }
    }

    private fun saveManifest(manifest: VaultManifest) {
        try {
            val parent = manifestPath.parent
            if (parent != null && !fileSystem.exists(parent)) {
                fileSystem.createDirectories(parent)
            }
            val content = json.encodeToString(manifest)
            fileSystem.write(manifestPath) {
                writeUtf8(content)
            }
            _manifest.value = manifest
            _skins.value = manifest.skins
            _activeSkinId.value = manifest.activeSkinId
        } catch (e: Exception) {
            println("Error saving vault manifest: ${e.message}")
        }
    }

    override fun getActiveSkin(accountId: String?): VaultSkin? {
        val currentManifest = _manifest.value
        val targetId = if (accountId != null && currentManifest.accountSkinMappings.containsKey(accountId)) {
            currentManifest.accountSkinMappings[accountId]
        } else {
            currentManifest.activeSkinId
        }

        return targetId?.let { id -> currentManifest.skins.firstOrNull { it.id == id } }
            ?: currentManifest.skins.firstOrNull()
    }

    override fun getSkin(skinId: String): VaultSkin? {
        return _skins.value.firstOrNull { it.id == skinId }
    }

    override fun getSkinFilePath(skin: VaultSkin): Path {
        return pathProvider.vaultSkinsDirectory.resolve(skin.fileName)
    }

    override fun getSkinBytes(skin: VaultSkin): ByteArray? {
        val file = getSkinFilePath(skin)
        if (!fileSystem.exists(file)) return null
        return try {
            fileSystem.read(file) { readByteArray() }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun importSkin(
        bytes: ByteArray,
        preferredName: String?,
        explicitModel: SkinModelType?
    ): Result<VaultSkin> = withContext(dispatcher) {
        try {
            require(bytes.isNotEmpty()) { "Skin file is empty" }

            // Validate PNG image and dimensions
            val img = ImageIO.read(ByteArrayInputStream(bytes))
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid image file. Must be a valid PNG."))

            val width = img.width
            val height = img.height

            if (!((width == 64 && height == 64) || (width == 64 && height == 32) || (width == 128 && height == 128))) {
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid skin dimensions (${width}x${height}). Minecraft skins must be 64x64 (or 64x32 legacy).")
                )
            }

            // Standardize 64x32 legacy skins to 64x64
            val processedBytes: ByteArray
            val finalImage = if (width == 64 && height == 32) {
                val expanded = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
                val g = expanded.createGraphics()
                g.drawImage(img, 0, 0, null)
                // Mirror right leg to left leg (legacy 1.7)
                g.drawImage(img.getSubimage(0, 16, 16, 16), 16, 48, null)
                // Mirror right arm to left arm (legacy 1.7)
                g.drawImage(img.getSubimage(40, 16, 16, 16), 32, 48, null)
                g.dispose()
                val baos = ByteArrayOutputStream()
                ImageIO.write(expanded, "PNG", baos)
                processedBytes = baos.toByteArray()
                expanded
            } else {
                processedBytes = bytes
                img
            }

            val model = explicitModel ?: detectModelFromImage(finalImage)
            val hash = computeSha256(processedBytes)
            val skinId = UUID.randomUUID().toString()
            val fileName = "$skinId.png"
            val targetPath = pathProvider.vaultSkinsDirectory.resolve(fileName)

            fileSystem.write(targetPath) {
                write(processedBytes)
            }

            val now = System.currentTimeMillis()
            val cleanName = preferredName?.trim()?.ifBlank { "My Skin" } ?: "My Skin"
            val newSkin = VaultSkin(
                id = skinId,
                name = cleanName,
                fileName = fileName,
                modelType = model,
                fileHash = hash,
                createdAt = now,
                updatedAt = now
            )

            val current = _manifest.value
            val updatedSkins = current.skins + newSkin
            val newActive = if (current.activeSkinId == null || current.skins.isEmpty()) skinId else current.activeSkinId

            saveManifest(
                current.copy(
                    activeSkinId = newActive,
                    skins = updatedSkins
                )
            )

            Result.success(newSkin)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setActiveSkin(skinId: String?, accountId: String?) = withContext(dispatcher) {
        val current = _manifest.value
        if (accountId != null) {
            val updatedMappings = current.accountSkinMappings.toMutableMap()
            if (skinId == null) {
                updatedMappings.remove(accountId)
            } else {
                updatedMappings[accountId] = skinId
            }
            saveManifest(current.copy(accountSkinMappings = updatedMappings))
        } else {
            saveManifest(current.copy(activeSkinId = skinId))
        }
    }

    override suspend fun renameSkin(skinId: String, newName: String): Result<VaultSkin> = withContext(dispatcher) {
        val clean = newName.trim()
        if (clean.isBlank()) return@withContext Result.failure(IllegalArgumentException("Skin name cannot be blank"))

        val current = _manifest.value
        val skin = current.skins.firstOrNull { it.id == skinId }
            ?: return@withContext Result.failure(IllegalArgumentException("Skin not found"))

        val updated = skin.copy(name = clean, updatedAt = System.currentTimeMillis())
        val updatedList = current.skins.map { if (it.id == skinId) updated else it }

        saveManifest(current.copy(skins = updatedList))
        Result.success(updated)
    }

    override suspend fun updateSkinModel(skinId: String, modelType: SkinModelType): Result<VaultSkin> = withContext(dispatcher) {
        val current = _manifest.value
        val skin = current.skins.firstOrNull { it.id == skinId }
            ?: return@withContext Result.failure(IllegalArgumentException("Skin not found"))

        val updated = skin.copy(modelType = modelType, updatedAt = System.currentTimeMillis())
        val updatedList = current.skins.map { if (it.id == skinId) updated else it }

        saveManifest(current.copy(skins = updatedList))
        Result.success(updated)
    }

    override suspend fun deleteSkin(skinId: String): Boolean = withContext(dispatcher) {
        val current = _manifest.value
        val target = current.skins.firstOrNull { it.id == skinId } ?: return@withContext false

        // Remove disk file
        try {
            val file = getSkinFilePath(target)
            if (fileSystem.exists(file)) {
                fileSystem.delete(file)
            }
        } catch (e: Exception) {
            println("Warning: could not delete skin file ${target.fileName}: ${e.message}")
        }

        val updatedList = current.skins.filter { it.id != skinId }
        val newActive = if (current.activeSkinId == skinId) {
            updatedList.firstOrNull()?.id
        } else {
            current.activeSkinId
        }

        val updatedMappings = current.accountSkinMappings.filterValues { it != skinId }

        saveManifest(
            current.copy(
                activeSkinId = newActive,
                accountSkinMappings = updatedMappings,
                skins = updatedList
            )
        )
        true
    }

    override suspend fun cacheOfficialAccountSkin(
        accountUsername: String,
        bytes: ByteArray,
        explicitModel: SkinModelType?
    ): VaultSkin = withContext(dispatcher) {
        val hash = computeSha256(bytes)
        val existing = findDuplicateByHash(hash)
        if (existing != null) return@withContext existing
        val result = importSkin(bytes, "$accountUsername's Skin", explicitModel)
        result.getOrThrow()
    }

    override fun findDuplicateByHash(fileHash: String): VaultSkin? {
        if (fileHash.isBlank()) return null
        return _skins.value.firstOrNull { it.fileHash.equals(fileHash, ignoreCase = true) }
    }

    override fun detectModelType(bytes: ByteArray): SkinModelType {
        return try {
            val img = ImageIO.read(ByteArrayInputStream(bytes)) ?: return SkinModelType.STEVE
            detectModelFromImage(img)
        } catch (e: Exception) {
            SkinModelType.STEVE
        }
    }

    private fun detectModelFromImage(img: BufferedImage): SkinModelType {
        if (img.width < 64 || img.height < 64) return SkinModelType.STEVE

        // Alex skin detection check: check transparency of the rightmost column of the right arm / sleeve
        // In Steve (4px arm), pixels at X in [54, 55], Y in [20, 31] are opaque.
        // In Alex (3px arm), pixels at X=54..55, Y=20..31 are completely transparent (alpha = 0).
        var fullyTransparentCount = 0
        val samplePoints = listOf(
            Pair(54, 20), Pair(54, 25), Pair(54, 30),
            Pair(55, 20), Pair(55, 25), Pair(55, 30),
            Pair(46, 52), Pair(46, 58), Pair(46, 62)
        )

        for ((x, y) in samplePoints) {
            if (x < img.width && y < img.height) {
                val alpha = (img.getRGB(x, y) ushr 24) and 0xFF
                if (alpha == 0) {
                    fullyTransparentCount++
                }
            }
        }

        return if (fullyTransparentCount >= (samplePoints.size / 2)) {
            SkinModelType.ALEX
        } else {
            SkinModelType.STEVE
        }
    }

    override fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
