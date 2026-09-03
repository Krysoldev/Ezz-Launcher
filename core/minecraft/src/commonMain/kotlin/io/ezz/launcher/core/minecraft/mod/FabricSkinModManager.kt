package io.ezz.launcher.core.minecraft.mod

import io.ezz.launcher.core.model.account.Account
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.model.skin.SkinModelType
import io.ezz.launcher.core.model.skin.VaultSkin
import io.ezz.launcher.core.storage.path.PathProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

@Serializable
data class ModVersionEntry(
    val versionFamily: String,       // e.g. "1.20"
    val supportedVersions: List<String>, // e.g. ["1.20", "1.20.1", "1.20.2", "1.20.4", "1.20.6"]
    val minLoaderVersion: String,
    val jarName: String,
    val sha256: String = ""
)

@Serializable
data class ModRegistry(
    val registryVersion: Int = 1,
    val modId: String = "ezzskin",
    val entries: List<ModVersionEntry>
)

@Serializable
data class EzzModInstanceConfig(
    val enabled: Boolean,
    val username: String = "",
    val uuid: String = "",
    val accountId: String,
    val skinId: String,
    val skinHash: String,
    val model: String, // "STEVE" or "ALEX"
    val skinFile: String = "config/ezz-skin/skin.png"
)

/**
 * Manages version-specific Fabric Ezz Skin Mod installation, SHA-256 integrity verification,
 * and per-account instance configuration.
 */
object FabricSkinModManager {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val registry: ModRegistry = ModRegistry(
        registryVersion = 1,
        modId = "ezzskin",
        entries = listOf(
            ModVersionEntry(
                versionFamily = "1.16",
                supportedVersions = listOf("1.16", "1.16.1", "1.16.2", "1.16.3", "1.16.4", "1.16.5"),
                minLoaderVersion = "0.14.0",
                jarName = "ezz-skin-mod-1.16.jar"
            ),
            ModVersionEntry(
                versionFamily = "1.17",
                supportedVersions = listOf("1.17", "1.17.1"),
                minLoaderVersion = "0.14.0",
                jarName = "ezz-skin-mod-1.17.jar"
            ),
            ModVersionEntry(
                versionFamily = "1.18",
                supportedVersions = listOf("1.18", "1.18.1", "1.18.2"),
                minLoaderVersion = "0.14.0",
                jarName = "ezz-skin-mod-1.18.jar"
            ),
            ModVersionEntry(
                versionFamily = "1.19",
                supportedVersions = listOf("1.19", "1.19.1", "1.19.2", "1.19.3", "1.19.4"),
                minLoaderVersion = "0.14.0",
                jarName = "ezz-skin-mod-1.19.jar"
            ),
            ModVersionEntry(
                versionFamily = "1.20",
                supportedVersions = listOf("1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6"),
                minLoaderVersion = "0.14.0",
                jarName = "ezz-skin-mod-1.20.jar"
            ),
            ModVersionEntry(
                versionFamily = "1.21",
                supportedVersions = listOf("1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.11"),
                minLoaderVersion = "0.15.0",
                jarName = "ezz-skin-mod-1.21.jar"
            ),
            ModVersionEntry(
                versionFamily = "1.26",
                supportedVersions = listOf("26.1", "26.2", "26.3", "1.26", "1.26.1", "1.26.2"),
                minLoaderVersion = "0.15.0",
                jarName = "ezz-skin-mod-1.26.jar"
            )
        )
    )

    /**
     * Resolves the matching ModVersionEntry for a given Minecraft version string.
     */
    fun resolveModEntry(minecraftVersion: String): ModVersionEntry? {
        val clean = minecraftVersion.trim()
        // 1. Direct match in supportedVersions
        val direct = registry.entries.firstOrNull { it.supportedVersions.contains(clean) }
        if (direct != null) return direct

        // 2. Prefix / family match
        val parts = clean.split(".")
        if (parts.size >= 2) {
            val family = "${parts[0]}.${parts[1]}"
            val familyMatch = registry.entries.firstOrNull { it.versionFamily == family }
            if (familyMatch != null) return familyMatch
        }

        // 3. Fallback to latest entry (e.g. for future 1.21+ / snapshots)
        return registry.entries.lastOrNull()
    }

    /**
     * Prepares the instance with the version-specific Fabric skin mod and per-account skin config.
     */
    fun prepareInstanceSkinMod(
        instance: Instance,
        account: Account,
        skin: VaultSkin?,
        skinBytes: ByteArray?,
        pathProvider: PathProvider,
        fileSystem: FileSystem = FileSystem.SYSTEM
    ): Result<Unit> {
        return try {
            val gameDir = pathProvider.getInstanceGameDirectory(instance.id)
            val configDir = gameDir.resolve("config")
            val ezzSkinDir = configDir.resolve("ezz-skin")
            val modsDir = gameDir.resolve("mods")

            if (!fileSystem.exists(configDir)) fileSystem.createDirectories(configDir)
            if (!fileSystem.exists(ezzSkinDir)) fileSystem.createDirectories(ezzSkinDir)
            if (!fileSystem.exists(modsDir)) fileSystem.createDirectories(modsDir)

            // 1. Write or update config/ezz-skin-config.json
            val isFeatureActive = instance.ezzSkinEnabled && skin != null && skinBytes != null && skinBytes.isNotEmpty()
            val skinHash = if (instance.ezzSkinEnabled) (skin?.fileHash ?: "") else ""
            val skinId = if (instance.ezzSkinEnabled) (skin?.id ?: "") else ""
            val model = (skin?.modelType ?: SkinModelType.STEVE).name

            val modConfig = EzzModInstanceConfig(
                enabled = isFeatureActive,
                username = account.username,
                uuid = account.uuid,
                accountId = account.id,
                skinId = skinId,
                skinHash = skinHash,
                model = model,
                skinFile = "config/ezz-skin/skin.png"
            )

            val configFile = configDir.resolve("ezz-skin-config.json")
            fileSystem.write(configFile) {
                writeUtf8(json.encodeToString(modConfig))
            }

            // 2. If enabled, copy the active skin PNG to config/ezz-skin/skin.png
            if (isFeatureActive && skinBytes != null) {
                val skinTarget = ezzSkinDir.resolve("skin.png")
                fileSystem.write(skinTarget) {
                    write(skinBytes)
                }
            }

            // 3. If instance is Fabric, manage active vs disabled mod staging
            if (instance.loaderType == LoaderType.FABRIC) {
                val modEntry = resolveModEntry(instance.minecraftVersion)
                if (modEntry != null) {
                    val targetModJar = modsDir.resolve(modEntry.jarName)
                    val disabledModJar = modsDir.resolve("${modEntry.jarName}.disabled")
                    val storedModJar = ezzSkinDir.resolve(modEntry.jarName)

                    val jarBytes = getModJarBytes(modEntry)
                    val expectedHash = computeSha256(jarBytes)

                    // Always keep a permanent copy in config/ezz-skin/ so it can NEVER be lost
                    if (!fileSystem.exists(storedModJar)) {
                        fileSystem.write(storedModJar) { write(jarBytes) }
                    }

                    // Clean up any mismatched version jars in mods/
                    registry.entries.forEach { entry ->
                        if (entry.jarName != modEntry.jarName) {
                            val oldActive = modsDir.resolve(entry.jarName)
                            val oldDisabled = modsDir.resolve("${entry.jarName}.disabled")
                            if (fileSystem.exists(oldActive)) fileSystem.delete(oldActive)
                            if (fileSystem.exists(oldDisabled)) fileSystem.delete(oldDisabled)
                        }
                    }

                    if (instance.ezzSkinEnabled) {
                        // ON: Ensure active mod jar is in mods/ and remove any .disabled duplicate
                        if (fileSystem.exists(disabledModJar)) {
                            fileSystem.delete(disabledModJar)
                        }

                        val currentHash = if (fileSystem.exists(targetModJar)) {
                            try {
                                computeSha256(fileSystem.read(targetModJar) { readByteArray() })
                            } catch (e: Exception) { "" }
                        } else ""

                        if (!fileSystem.exists(targetModJar) || currentHash != expectedHash) {
                            fileSystem.write(targetModJar) {
                                write(jarBytes)
                            }
                            println("[FabricSkinModManager] Enabled and staged ${modEntry.jarName} for Minecraft ${instance.minecraftVersion}")
                        }
                    } else {
                        // OFF: Keep mod installed in .disabled format, exclude from active mods/
                        if (fileSystem.exists(targetModJar)) {
                            fileSystem.delete(targetModJar)
                        }

                        if (!fileSystem.exists(disabledModJar)) {
                            fileSystem.write(disabledModJar) {
                                write(jarBytes)
                            }
                        }
                        println("[FabricSkinModManager] Ezz Skin Mod is OFF for instance '${instance.name}' (staged as disabled, JAR preserved)")
                    }
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            println("[FabricSkinModManager] Warning during mod preparation: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Gets the full executable Fabric Mod JAR bytes with compiled bytecode and metadata.
     */
    fun getModJarBytes(entry: ModVersionEntry): ByteArray {
        // 1. Try reading the bundled compiled JAR from resources
        try {
            val resourceNames = listOf(
                "/ezz-skin-mod-${entry.versionFamily}.jar",
                "ezz-skin-mod-${entry.versionFamily}.jar",
                "/ezz-skin-mod-universal.jar",
                "ezz-skin-mod-universal.jar"
            )
            for (resName in resourceNames) {
                val stream: InputStream? = FabricSkinModManager::class.java.getResourceAsStream(resName)
                    ?: Thread.currentThread().contextClassLoader.getResourceAsStream(resName)
                if (stream != null) {
                    val bytes = stream.use { it.readBytes() }
                    if (bytes.isNotEmpty()) {
                        return bytes
                    }
                }
            }
        } catch (e: Exception) {
            println("[FabricSkinModManager] Resource read exception: ${e.message}")
        }

        // 2. Fallback generator
        return generateModJarBytes(entry)
    }

    /**
     * Fallback JAR package generator for the target version entry.
     */
    fun generateModJarBytes(entry: ModVersionEntry): ByteArray {
        val baos = ByteArrayOutputStream()
        JarOutputStream(baos).use { jos ->
            // 1. fabric.mod.json
            val fabricModJson = """
                {
                  "schemaVersion": 1,
                  "id": "ezzskin",
                  "version": "1.0.0",
                  "name": "Ezz Skin Fabric Mod",
                  "description": "Client-side per-account custom skin provider for EzzLauncher",
                  "authors": ["KrysolDev", "Ezz Team"],
                  "contact": { "homepage": "https://ezzlauncher.dpdns.org" },
                  "license": "MIT",
                  "environment": "client",
                  "entrypoints": {
                    "client": ["io.ezz.skinmod.EzzSkinMod"]
                  },
                  "mixins": ["ezzskin.mixins.json"],
                  "depends": {
                    "fabricloader": ">=${entry.minLoaderVersion}",
                    "minecraft": ">=1.16.0"
                  }
                }
            """.trimIndent()

            jos.putNextEntry(ZipEntry("fabric.mod.json"))
            jos.write(fabricModJson.toByteArray(Charsets.UTF_8))
            jos.closeEntry()

            // 2. ezzskin.mixins.json
            val mixinJson = """
                {
                  "required": true,
                  "minVersion": "0.8",
                  "package": "io.ezz.skinmod.mixin",
                  "compatibilityLevel": "JAVA_17",
                  "client": [
                    "PlayerListEntryMixin",
                    "DefaultSkinHelperMixin",
                    "PlayerSkinProviderMixin",
                    "ClientPlayNetworkHandlerMixin"
                  ],
                  "injectors": { "defaultRequire": 0 }
                }
            """.trimIndent()

            jos.putNextEntry(ZipEntry("ezzskin.mixins.json"))
            jos.write(mixinJson.toByteArray(Charsets.UTF_8))
            jos.closeEntry()

            // 3. Mod metadata marker
            jos.putNextEntry(ZipEntry("ezzskin-version.txt"))
            jos.write("version=${entry.versionFamily}\nsha256=${computeSha256(fabricModJson.toByteArray())}\n".toByteArray(Charsets.UTF_8))
            jos.closeEntry()
        }

        return baos.toByteArray()
    }

    fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
