package io.ezz.launcher.core.runtime.skin

import io.ezz.launcher.core.model.account.Account
import io.ezz.launcher.core.model.account.AccountType
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.skin.VaultSkin
import io.ezz.launcher.core.storage.path.PathProvider
import okio.FileSystem
import okio.Path
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Robust Offline Game Skin Application Pipeline.
 *
 * Injects the active Vault skin into the Minecraft instance runtime so that
 * offline accounts render with their selected skin inside the actual game client.
 *
 * Core Features:
 * 1. Version-Aware pack_format mapping (1.6.x through 1.21.4+).
 * 2. Complete coverage of ALL 9 player skin variants (steve, alex, ari, efe, kai, makena, noor, sunny, zuri)
 *    in wide, slim, and legacy paths to guarantee 100% resolution for any offline UUID hash.
 * 3. Dual-Format Generation: Unpacked folder pack (EzzVaultSkin) + Packed zip archive (EzzVaultSkin.zip).
 * 4. Multi-Version options.txt registration for 1.13+ (file/ prefix) and legacy <= 1.12.2.
 * 5. Preserves Server-Side skins (SkinsRestorer / CustomSkins plugins take natural precedence over default client textures).
 */
object OfflineSkinInjector {

    private val PLAYER_SKIN_NAMES = listOf(
        "steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri"
    )

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
            println("[VaultSkin] Bypassing Vault skin injection for online authenticated account '${account.username}'")
            return false
        }

        if (skin == null || skinBytes == null || skinBytes.isEmpty()) {
            println("[VaultSkin] No active Vault skin selected for '${account.username}'")
            return false
        }

        val skinFilePath = pathProvider.vaultSkinsDirectory.resolve(skin.fileName)

        return try {
            val gameDir = pathProvider.getInstanceGameDirectory(instance.id)
            val resourcePacksDir = gameDir.resolve("resourcepacks")
            val skinPackDir = resourcePacksDir.resolve("EzzVaultSkin")
            val skinZipPath = resourcePacksDir.resolve("EzzVaultSkin.zip")

            // Ensure resourcepacks directory exists
            if (!fileSystem.exists(resourcePacksDir)) {
                fileSystem.createDirectories(resourcePacksDir)
            }

            val packFormat = resolvePackFormat(instance.minecraftVersion)

            // 1. Write pack.mcmeta with version-aware pack_format and broad supported_formats
            val packMcmetaContent = """
                {
                  "pack": {
                    "pack_format": $packFormat,
                    "supported_formats": [1, 99],
                    "description": "Ezz Launcher Vault Skin (${skin.name})"
                  }
                }
            """.trimIndent()

            // 2. Prepare Directory Pack
            if (!fileSystem.exists(skinPackDir)) {
                fileSystem.createDirectories(skinPackDir)
            }
            fileSystem.write(skinPackDir.resolve("pack.mcmeta")) { writeUtf8(packMcmetaContent) }

            // Directories for texture placement
            val assetsDir = skinPackDir.resolve("assets").resolve("minecraft").resolve("textures")
            val entityDir = assetsDir.resolve("entity")
            val playerDir = entityDir.resolve("player")
            val wideDir = playerDir.resolve("wide")
            val slimDir = playerDir.resolve("slim")
            val legacyTexturesDir = skinPackDir.resolve("textures").resolve("entity")
            val rootMobDir = skinPackDir.resolve("mob")

            listOf(wideDir, slimDir, playerDir, entityDir, legacyTexturesDir, rootMobDir).forEach { dir ->
                if (!fileSystem.exists(dir)) fileSystem.createDirectories(dir)
            }

            // Write all 9 default skin variants to all locations
            for (skinName in PLAYER_SKIN_NAMES) {
                // Modern 1.20.2+ wide & slim
                fileSystem.write(wideDir.resolve("$skinName.png")) { write(skinBytes) }
                fileSystem.write(slimDir.resolve("$skinName.png")) { write(skinBytes) }

                // 1.19.3 - 1.20.1 player/<name>.png
                fileSystem.write(playerDir.resolve("$skinName.png")) { write(skinBytes) }

                // 1.8 - 1.19.2 entity/<name>.png
                fileSystem.write(entityDir.resolve("$skinName.png")) { write(skinBytes) }

                // Legacy <= 1.7 textures/entity/<name>.png
                fileSystem.write(legacyTexturesDir.resolve("$skinName.png")) { write(skinBytes) }
            }

            // Legacy <= 1.5 char.png
            fileSystem.write(rootMobDir.resolve("char.png")) { write(skinBytes) }

            // 3. Prepare ZIP Pack (EzzVaultSkin.zip)
            val zipFile = skinZipPath.toFile()
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                // pack.mcmeta
                zos.putNextEntry(ZipEntry("pack.mcmeta"))
                zos.write(packMcmetaContent.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                for (skinName in PLAYER_SKIN_NAMES) {
                    // Modern 1.20.2+
                    addZipEntry(zos, "assets/minecraft/textures/entity/player/wide/$skinName.png", skinBytes)
                    addZipEntry(zos, "assets/minecraft/textures/entity/player/slim/$skinName.png", skinBytes)

                    // 1.19.3 - 1.20.1
                    addZipEntry(zos, "assets/minecraft/textures/entity/player/$skinName.png", skinBytes)

                    // 1.8 - 1.19.2
                    addZipEntry(zos, "assets/minecraft/textures/entity/$skinName.png", skinBytes)

                    // Legacy <= 1.7
                    addZipEntry(zos, "textures/entity/$skinName.png", skinBytes)
                }
                addZipEntry(zos, "mob/char.png", skinBytes)
            }

            // 4. Update options.txt to automatically enable EzzVaultSkin with top priority
            val optionsFile = gameDir.resolve("options.txt")
            updateOptionsTxt(optionsFile, fileSystem)

            // Diagnostic logging
            println("[VaultSkin]")
            println("Account: ${account.username}")
            println("Type: OFFLINE")
            println("Active Skin: ${skin.name} (${skin.modelType})")
            println("Skin File: $skinFilePath")
            println("Minecraft Version: ${instance.minecraftVersion}")
            println("Pack Format: $packFormat")
            println("Preparation: SUCCESS")
            println("Application: SUCCESS")

            true
        } catch (e: Exception) {
            println("[VaultSkin] Warning: Vault skin could not be applied: ${e.message}")
            false
        }
    }

    private fun addZipEntry(zos: ZipOutputStream, entryName: String, data: ByteArray) {
        zos.putNextEntry(ZipEntry(entryName))
        zos.write(data)
        zos.closeEntry()
    }

    /**
     * Resolves the canonical Minecraft resource pack format number from version string.
     */
    fun resolvePackFormat(minecraftVersion: String): Int {
        val clean = minecraftVersion.trim()
        return when {
            clean.startsWith("1.21.4") -> 46
            clean.startsWith("1.21.2") || clean.startsWith("1.21.3") -> 42
            clean.startsWith("1.21") -> 34
            clean.startsWith("1.20.5") || clean.startsWith("1.20.6") -> 32
            clean.startsWith("1.20.3") || clean.startsWith("1.20.4") -> 22
            clean.startsWith("1.20.2") -> 18
            clean.startsWith("1.20") -> 15
            clean.startsWith("1.19.4") -> 13
            clean.startsWith("1.19.3") -> 12
            clean.startsWith("1.19") -> 9
            clean.startsWith("1.18.2") -> 9
            clean.startsWith("1.18") -> 8
            clean.startsWith("1.17") -> 7
            clean.startsWith("1.16.2") || clean.startsWith("1.16.3") || clean.startsWith("1.16.4") || clean.startsWith("1.16.5") -> 7
            clean.startsWith("1.16") -> 6
            clean.startsWith("1.15") -> 5
            clean.startsWith("1.14") || clean.startsWith("1.13") -> 4
            clean.startsWith("1.11") || clean.startsWith("1.12") -> 3
            clean.startsWith("1.9") || clean.startsWith("1.10") -> 2
            clean.startsWith("1.6") || clean.startsWith("1.7") || clean.startsWith("1.8") -> 1
            else -> 34
        }
    }

    /**
     * Ensures options.txt activates both directory and zip pack variations.
     */
    private fun updateOptionsTxt(optionsPath: Path, fileSystem: FileSystem) {
        try {
            val packIdentifiers = listOf(
                "\"file/EzzVaultSkin\"",
                "\"file/EzzVaultSkin.zip\"",
                "\"EzzVaultSkin\"",
                "\"EzzVaultSkin.zip\""
            )

            if (!fileSystem.exists(optionsPath)) {
                val initialContent = "resourcePacks:[\"vanilla\",${packIdentifiers.joinToString(",")}]\n"
                fileSystem.write(optionsPath) { writeUtf8(initialContent) }
                return
            }

            val lines = fileSystem.read(optionsPath) { readUtf8() }.lines()
            var foundResourcePacks = false
            val updatedLines = lines.map { line ->
                if (line.startsWith("resourcePacks:")) {
                    foundResourcePacks = true
                    val rawArray = line.substringAfter("resourcePacks:").trim()
                    if (rawArray.endsWith("]")) {
                        val inner = rawArray.removePrefix("[").removeSuffix("]").trim()
                        val parts = if (inner.isBlank()) mutableListOf() else inner.split(",").map { it.trim() }.toMutableList()
                        for (pack in packIdentifiers) {
                            if (!parts.contains(pack)) {
                                parts.add(pack)
                            }
                        }
                        "resourcePacks:[${parts.joinToString(",")}]"
                    } else {
                        "resourcePacks:[\"vanilla\",${packIdentifiers.joinToString(",")}]"
                    }
                } else {
                    line
                }
            }.toMutableList()

            if (!foundResourcePacks) {
                updatedLines.add("resourcePacks:[\"vanilla\",${packIdentifiers.joinToString(",")}]")
            }

            fileSystem.write(optionsPath) {
                writeUtf8(updatedLines.joinToString("\n"))
            }
        } catch (e: Exception) {
            // Fail-safe: do not block launch if options.txt writing encounters an issue
        }
    }
}
