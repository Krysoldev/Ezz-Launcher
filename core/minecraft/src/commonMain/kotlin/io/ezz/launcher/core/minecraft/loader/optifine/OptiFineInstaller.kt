package io.ezz.launcher.core.minecraft.loader.optifine

import io.ezz.launcher.core.model.minecraft.Library
import io.ezz.launcher.core.model.minecraft.VersionInfo
import io.ezz.launcher.core.storage.path.PathProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem

object OptiFineCompatibilityValidator {
    private val supportedVersions = listOf(
        "1.21.4", "1.21.1", "1.21",
        "1.20.4", "1.20.2", "1.20.1",
        "1.19.4", "1.19.2",
        "1.18.2", "1.17.1", "1.16.5",
        "1.12.2", "1.8.9", "1.7.10"
    )

    fun isVersionSupported(minecraftVersion: String): Boolean {
        return supportedVersions.any { minecraftVersion.startsWith(it) }
    }

    fun getSuggestedOptiFineVersion(minecraftVersion: String): String {
        return when {
            minecraftVersion.startsWith("1.21") -> "HD_U_I7"
            minecraftVersion.startsWith("1.20") -> "HD_U_I6"
            minecraftVersion.startsWith("1.19") -> "HD_U_I5"
            minecraftVersion.startsWith("1.18") -> "HD_U_H9"
            minecraftVersion.startsWith("1.16.5") -> "HD_U_G8"
            minecraftVersion.startsWith("1.12.2") -> "HD_U_G5"
            minecraftVersion.startsWith("1.8.9") -> "HD_U_M5"
            else -> "HD_U_L5"
        }
    }
}

class OptiFineInstaller(
    private val pathProvider: PathProvider,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun install(gameVersion: String, optifineVersion: String): VersionInfo = withContext(dispatcher) {
        if (!OptiFineCompatibilityValidator.isVersionSupported(gameVersion)) {
            throw IllegalArgumentException("OptiFine is not compatible with Minecraft $gameVersion. Please select a supported version or use Fabric with Iris.")
        }

        val versionId = "$gameVersion-OptiFine_$optifineVersion"
        val versionDir = pathProvider.versionsDirectory.resolve(versionId)
        val versionFile = versionDir.resolve("$versionId.json")

        val isLegacy = gameVersion.startsWith("1.12") || gameVersion.startsWith("1.8") || gameVersion.startsWith("1.7")

        val optifineLibrary = Library(
            name = "optifine:OptiFine:$gameVersion-$optifineVersion"
        )

        val profile = VersionInfo(
            id = versionId,
            inheritsFrom = gameVersion,
            mainClass = if (isLegacy) "net.minecraft.launchwrapper.Launch" else "net.minecraft.client.main.Main",
            libraries = listOf(optifineLibrary),
            minecraftArguments = if (isLegacy) "--tweakClass optifine.OptiFineTweaker" else null
        )

        fileSystem.createDirectories(versionDir)
        fileSystem.write(versionFile) {
            writeUtf8(json.encodeToString(profile))
        }

        profile
    }
}
