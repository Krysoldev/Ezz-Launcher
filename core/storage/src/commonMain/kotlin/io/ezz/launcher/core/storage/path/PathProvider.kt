package io.ezz.launcher.core.storage.path

import okio.Path
import okio.Path.Companion.toPath

interface PathProvider {
    val rootDirectory: Path
    // Filesystem directories strictly store Minecraft game runtime files (JARs, assets, libraries, mods)
    val instancesDirectory: Path get() = rootDirectory.resolve("instances")
    val versionsDirectory: Path get() = rootDirectory.resolve("versions")
    val librariesDirectory: Path get() = rootDirectory.resolve("libraries")
    val assetsDirectory: Path get() = rootDirectory.resolve("assets")
    val assetsIndexesDirectory: Path get() = assetsDirectory.resolve("indexes")
    val assetsObjectsDirectory: Path get() = assetsDirectory.resolve("objects")
    val cacheDirectory: Path get() = rootDirectory.resolve("cache")
    val skinsDirectory: Path get() = cacheDirectory.resolve("skins")
    val vaultDirectory: Path get() = rootDirectory.resolve("vault")
    val vaultSkinsDirectory: Path get() = vaultDirectory.resolve("skins")

    fun getInstanceDirectory(instanceId: String): Path = instancesDirectory.resolve(instanceId)
    fun getInstanceGameDirectory(instanceId: String): Path = getInstanceDirectory(instanceId).resolve(".minecraft")
    fun getInstanceNativesDirectory(instanceId: String): Path = getInstanceDirectory(instanceId).resolve("natives")

    fun initializeDirectories(fileSystem: okio.FileSystem = okio.FileSystem.SYSTEM) {
        if (!fileSystem.exists(rootDirectory)) fileSystem.createDirectories(rootDirectory)
        if (!fileSystem.exists(instancesDirectory)) fileSystem.createDirectories(instancesDirectory)
        if (!fileSystem.exists(versionsDirectory)) fileSystem.createDirectories(versionsDirectory)
        if (!fileSystem.exists(librariesDirectory)) fileSystem.createDirectories(librariesDirectory)
        if (!fileSystem.exists(assetsDirectory)) fileSystem.createDirectories(assetsDirectory)
        if (!fileSystem.exists(assetsIndexesDirectory)) fileSystem.createDirectories(assetsIndexesDirectory)
        if (!fileSystem.exists(assetsObjectsDirectory)) fileSystem.createDirectories(assetsObjectsDirectory)
        if (!fileSystem.exists(cacheDirectory)) fileSystem.createDirectories(cacheDirectory)
        if (!fileSystem.exists(skinsDirectory)) fileSystem.createDirectories(skinsDirectory)
        if (!fileSystem.exists(vaultDirectory)) fileSystem.createDirectories(vaultDirectory)
        if (!fileSystem.exists(vaultSkinsDirectory)) fileSystem.createDirectories(vaultSkinsDirectory)
    }
}

class DefaultPathProvider(override val rootDirectory: Path) : PathProvider {
    companion object {
        fun createDefault(): DefaultPathProvider {
            val userHome = System.getProperty("user.home") ?: "."
            val osName = System.getProperty("os.name")?.lowercase() ?: ""
            val root = when {
                osName.contains("win") -> {
                    val appData = System.getenv("APPDATA") ?: "$userHome/AppData/Roaming"
                    "$appData/.ezzlauncher".toPath()
                }
                osName.contains("mac") -> {
                    "$userHome/Library/Application Support/ezzlauncher".toPath()
                }
                else -> {
                    "$userHome/.ezzlauncher".toPath()
                }
            }
            return DefaultPathProvider(root)
        }
    }
}
