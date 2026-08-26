package io.ezz.launcher.core.runtime.skin

import io.ezz.launcher.core.model.account.Account
import io.ezz.launcher.core.model.account.AccountType
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.model.skin.VaultSkin
import io.ezz.launcher.core.storage.path.PathProvider
import okio.FileSystem
import okio.Path

/**
 * Manages the local offline player's Vault skin lifecycle.
 *
 * Responsibilities:
 * 1. Synchronizes active Vault skin metadata and raw PNG to the instance's .ezz directory.
 * 2. For Fabric instances with an active Vault skin on an offline account:
 *    Builds the isolated, local-player-only skin mod JAR into instance/mods/ezz_vault_skin.jar.
 * 3. For Microsoft accounts or default offline accounts:
 *    Ensures ezz_vault_skin.jar is removed, leaving user mods completely untouched.
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
        val modsDir = gameDir.resolve("mods")
        val ezzModJar = modsDir.resolve("ezz_vault_skin.jar")

        // 1. Online / Microsoft accounts: Never inject Vault skin into Minecraft, remove mod JAR if present
        if (account.type != AccountType.OFFLINE) {
            cleanupLegacySkinMod(gameDir, fileSystem)
            return
        }

        // 2. Offline account handling
        val ezzDir = gameDir.resolve(".ezz")
        try {
            if (!fileSystem.exists(ezzDir)) {
                fileSystem.createDirectories(ezzDir)
            }

            val skinName = skin?.name ?: "Default"
            val skinHash = skin?.fileHash?.ifBlank { "default_canonical" } ?: "default_canonical"
            val model = skin?.modelType?.name ?: "CLASSIC"

            val activeSkinJson = """
                {
                  "type": "OFFLINE",
                  "username": "${account.username}",
                  "uuid": "${account.uuid}",
                  "skinName": "$skinName",
                  "skinHash": "$skinHash",
                  "model": "$model"
                }
            """.trimIndent()
            fileSystem.write(ezzDir.resolve("active_skin.json")) { writeUtf8(activeSkinJson) }

            if (skinBytes != null && skinBytes.isNotEmpty()) {
                fileSystem.write(ezzDir.resolve("active_skin.png")) { write(skinBytes) }
            }

            // 3. For Fabric instances with an active custom skin: build the local-player-only mod JAR
            if (instance.loaderType == LoaderType.FABRIC && skin != null && skinBytes != null && skinBytes.isNotEmpty()) {
                FabricSkinModBuilder.buildModJar(
                    skin = skin,
                    skinBytes = skinBytes,
                    destinationJarPath = ezzModJar,
                    fileSystem = fileSystem
                )
            } else {
                // If Vanilla / Default Steve or no active skin, remove ezz_vault_skin.jar from mods
                if (fileSystem.exists(ezzModJar)) {
                    fileSystem.delete(ezzModJar)
                }
            }
        } catch (e: Exception) {
            println("[OfflineSkinManager] Warning during offline skin sync: ${e.message}")
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
