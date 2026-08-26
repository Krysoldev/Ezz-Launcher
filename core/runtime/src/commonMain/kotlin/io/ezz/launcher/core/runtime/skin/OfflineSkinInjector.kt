package io.ezz.launcher.core.runtime.skin

import io.ezz.launcher.core.model.account.Account
import io.ezz.launcher.core.model.account.AccountType
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.skin.VaultSkin
import io.ezz.launcher.core.storage.path.PathProvider
import okio.FileSystem
import okio.Path
import java.io.File

object OfflineSkinInjector {

    fun applyVaultSkin(
        instance: Instance,
        account: Account,
        skin: VaultSkin?,
        skinBytes: ByteArray?,
        pathProvider: PathProvider,
        fileSystem: FileSystem = FileSystem.SYSTEM
    ): Boolean {
        // Only apply to OFFLINE accounts
        if (account.type != AccountType.OFFLINE) {
            println("[VAULT_SKIN] Bypassing Vault skin injection for authenticated online account '${account.username}'")
            return false
        }

        if (skin == null || skinBytes == null || skinBytes.isEmpty()) {
            println("[VAULT_SKIN] No active Vault skin selected for '${account.username}'")
            return false
        }

        return try {
            val gameDir = pathProvider.getInstanceGameDirectory(instance.id)
            val resourcePacksDir = gameDir.resolve("resourcepacks")
            val skinPackDir = resourcePacksDir.resolve("EzzVaultSkin")

            // Ensure directories exist
            if (!fileSystem.exists(skinPackDir)) {
                fileSystem.createDirectories(skinPackDir)
            }

            // 1. Write pack.mcmeta
            val packMcmeta = skinPackDir.resolve("pack.mcmeta")
            val packJson = """
                {
                  "pack": {
                    "pack_format": 34,
                    "supported_formats": [1, 99],
                    "description": "Ezz Launcher Vault Skin (${skin.name})"
                  }
                }
            """.trimIndent()
            fileSystem.write(packMcmeta) { writeUtf8(packJson) }

            // 2. Texture destinations for all Minecraft version formats
            val texturesDir = skinPackDir.resolve("assets").resolve("minecraft").resolve("textures")
            val entityDir = texturesDir.resolve("entity")
            val playerDir = entityDir.resolve("player")
            val wideDir = playerDir.resolve("wide")
            val slimDir = playerDir.resolve("slim")

            listOf(wideDir, slimDir, entityDir).forEach { dir ->
                if (!fileSystem.exists(dir)) fileSystem.createDirectories(dir)
            }

            // Modern 1.20.2+ wide/slim player textures
            fileSystem.write(wideDir.resolve("steve.png")) { write(skinBytes) }
            fileSystem.write(slimDir.resolve("alex.png")) { write(skinBytes) }
            fileSystem.write(slimDir.resolve("steve.png")) { write(skinBytes) }
            fileSystem.write(wideDir.resolve("alex.png")) { write(skinBytes) }

            // 1.8 - 1.20.1 entity/steve.png & entity/alex.png
            fileSystem.write(entityDir.resolve("steve.png")) { write(skinBytes) }
            fileSystem.write(entityDir.resolve("alex.png")) { write(skinBytes) }

            // Legacy <= 1.7 entity/steve.png
            val legacyTexturesDir = skinPackDir.resolve("textures").resolve("entity")
            if (!fileSystem.exists(legacyTexturesDir)) fileSystem.createDirectories(legacyTexturesDir)
            fileSystem.write(legacyTexturesDir.resolve("steve.png")) { write(skinBytes) }

            // 3. Configure options.txt to enable EzzVaultSkin pack by default
            val optionsFile = gameDir.resolve("options.txt")
            updateOptionsTxt(optionsFile, fileSystem)

            println("[VAULT_SKIN] Successfully prepared Vault skin '${skin.name}' (${skin.modelType}) for offline instance '${instance.name}'")
            true
        } catch (e: Exception) {
            println("[VAULT_SKIN_WARN] Failed to apply Vault skin: ${e.message}")
            false
        }
    }

    private fun updateOptionsTxt(optionsPath: Path, fileSystem: FileSystem) {
        try {
            val packEntry = "file/EzzVaultSkin"
            if (!fileSystem.exists(optionsPath)) {
                val initialContent = "resourcePacks:[\"vanilla\",\"$packEntry\"]\n"
                fileSystem.write(optionsPath) { writeUtf8(initialContent) }
                return
            }

            val lines = fileSystem.read(optionsPath) { readUtf8() }.lines()
            var foundResourcePacks = false
            val updatedLines = lines.map { line ->
                if (line.startsWith("resourcePacks:")) {
                    foundResourcePacks = true
                    if (!line.contains(packEntry)) {
                        // Insert packEntry into array
                        val rawArray = line.substringAfter("resourcePacks:").trim()
                        if (rawArray.endsWith("]")) {
                            val inner = rawArray.removePrefix("[").removeSuffix("]").trim()
                            val parts = if (inner.isBlank()) mutableListOf() else inner.split(",").map { it.trim() }.toMutableList()
                            if (!parts.contains("\"$packEntry\"")) {
                                parts.add("\"$packEntry\"")
                            }
                            "resourcePacks:[${parts.joinToString(",")}]"
                        } else {
                            "resourcePacks:[\"vanilla\",\"$packEntry\"]"
                        }
                    } else {
                        line
                    }
                } else {
                    line
                }
            }.toMutableList()

            if (!foundResourcePacks) {
                updatedLines.add("resourcePacks:[\"vanilla\",\"$packEntry\"]")
            }

            fileSystem.write(optionsPath) {
                writeUtf8(updatedLines.joinToString("\n"))
            }
        } catch (e: Exception) {
            // Ignore options.txt modification errors to ensure fail-safe launch
        }
    }
}
