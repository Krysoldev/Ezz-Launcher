package io.ezz.launcher.core.runtime.skin

import io.ezz.launcher.core.model.account.Account
import io.ezz.launcher.core.model.account.AccountType
import io.ezz.launcher.core.model.account.OfflineAccount
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.skin.VaultSkin
import io.ezz.launcher.core.storage.path.PathProvider
import okio.FileSystem
import okio.Path

/**
 * Manages profile-keyed skin caching and legacy artifact cleanup.
 *
 * Responsibilities:
 * 1. Safely removes any legacy Ezz skin mod files from instance mods/ without touching user mods.
 * 2. Caches profile metadata and skin files in a deterministic, profile-keyed location:
 *    cache/profiles/<profile-uuid>/profile.json and cache/profiles/<profile-uuid>/skins/<skin-hash>.png
 * 3. Does NOT inject any custom mods into Minecraft instances.
 */
object OfflineSkinManager {

    fun syncOfflineSkin(
        instance: Instance,
        account: Account,
        skin: VaultSkin?,
        skinBytes: ByteArray?,
        pathProvider: PathProvider,
        fileSystem: FileSystem = FileSystem.SYSTEM
    ) {
        val gameDir = pathProvider.getInstanceGameDirectory(instance.id)

        // 1. Safe cleanup of legacy Ezz skin injection files if present
        cleanupLegacySkinMod(gameDir, fileSystem)

        // 2. Profile-specific caching
        try {
            val profileUuid = account.uuid
            val profileCacheDir = pathProvider.cacheDirectory.resolve("profiles").resolve(profileUuid)
            val profileSkinsDir = profileCacheDir.resolve("skins")

            if (!fileSystem.exists(profileSkinsDir)) {
                fileSystem.createDirectories(profileSkinsDir)
            }

            val skinName = skin?.name ?: "Default"
            val skinHash = skin?.fileHash?.ifBlank { "default_canonical" } ?: "default_canonical"
            val model = skin?.modelType?.name ?: "CLASSIC"

            val profileJson = """
                {
                  "type": "${account.type.name}",
                  "username": "${account.username}",
                  "uuid": "$profileUuid",
                  "skinName": "$skinName",
                  "skinHash": "$skinHash",
                  "model": "$model",
                  "updatedAt": ${System.currentTimeMillis()}
                }
            """.trimIndent()

            fileSystem.write(profileCacheDir.resolve("profile.json")) { writeUtf8(profileJson) }

            if (skinBytes != null && skinBytes.isNotEmpty()) {
                val skinFile = profileSkinsDir.resolve("$skinHash.png")
                fileSystem.write(skinFile) { write(skinBytes) }
            }
        } catch (e: Exception) {
            println("[OfflineSkinManager] Warning during profile skin cache update: ${e.message}")
        }
    }

    /**
     * Safely cleans up any legacy or active Ezz skin mod JARs without touching user mods.
     */
    fun cleanupLegacySkinMod(gameDir: Path, fileSystem: FileSystem = FileSystem.SYSTEM) {
        try {
            val legacyModJar = gameDir.resolve("mods").resolve("ezz_vault_skin.jar")
            if (fileSystem.exists(legacyModJar)) {
                fileSystem.delete(legacyModJar)
            }
            val legacyOverrideJar = gameDir.resolve(".ezz").resolve("vault_skin_override.jar")
            if (fileSystem.exists(legacyOverrideJar)) {
                fileSystem.delete(legacyOverrideJar)
            }
        } catch (e: Exception) {
            // Ignore deletion errors
        }
    }
}
