package io.ezz.launcher.core.runtime.skin

import io.ezz.launcher.core.model.account.Account
import io.ezz.launcher.core.model.account.AccountType
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.skin.VaultSkin
import io.ezz.launcher.core.storage.path.PathProvider
import okio.FileSystem
import okio.Path

/**
 * Launcher-Side Offline Skin Management & Legacy Mod Cleanup.
 *
 * Responsibilities:
 * 1. Manages launcher-side skin metadata for offline accounts.
 * 2. Safely cleans up legacy Ezz skin mod files (ezz_vault_skin.jar, vault_skin_override.jar)
 *    from older versions without touching any user-installed mods.
 * 3. Does NOT install, inject, or depend on any Minecraft client mod.
 */
object OfflineSkinManager {

    /**
     * Prepares offline account skin metadata launcher-side and cleans up legacy injection artifacts.
     */
    fun syncOfflineSkin(
        instance: Instance,
        account: Account,
        skin: VaultSkin?,
        pathProvider: PathProvider,
        fileSystem: FileSystem = FileSystem.SYSTEM
    ) {
        val gameDir = pathProvider.getInstanceGameDirectory(instance.id)

        // 1. Safe cleanup of legacy Ezz skin injection files if present
        cleanupLegacySkinMod(gameDir, fileSystem)

        // 2. For offline accounts, persist active skin metadata in .ezz/ for launcher-side state tracking
        if (account.type == AccountType.OFFLINE) {
            val ezzDir = gameDir.resolve(".ezz")
            try {
                if (!fileSystem.exists(ezzDir)) {
                    fileSystem.createDirectories(ezzDir)
                }
                val activeSkinJson = """
                    {
                      "username": "${account.username}",
                      "uuid": "${account.uuid}",
                      "skinName": "${skin?.name ?: "Default"}",
                      "skinHash": "${skin?.fileHash ?: "default_canonical"}",
                      "model": "${skin?.modelType ?: "CLASSIC"}"
                    }
                """.trimIndent()
                fileSystem.write(ezzDir.resolve("active_skin.json")) { writeUtf8(activeSkinJson) }
            } catch (e: Exception) {
                // Non-critical launcher-side state write
            }
        }
    }

    /**
     * Safely cleans up any legacy Ezz skin mod JARs without touching user mods.
     */
    fun cleanupLegacySkinMod(gameDir: Path, fileSystem: FileSystem = FileSystem.SYSTEM) {
        try {
            val legacyModJar = gameDir.resolve("mods").resolve("ezz_vault_skin.jar")
            if (fileSystem.exists(legacyModJar)) {
                fileSystem.delete(legacyModJar)
                println("[EZZ-CLEANUP] Removed legacy Ezz skin mod from instance mods directory: $legacyModJar")
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
