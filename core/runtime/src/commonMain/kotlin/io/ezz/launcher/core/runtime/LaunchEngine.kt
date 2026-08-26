package io.ezz.launcher.core.runtime

import io.ezz.launcher.core.auth.AuthManager
import io.ezz.launcher.core.minecraft.launch.LaunchArgumentBuilder
import io.ezz.launcher.core.minecraft.loader.fabric.FabricInstaller
import io.ezz.launcher.core.minecraft.loader.optifine.OptiFineInstaller
import io.ezz.launcher.core.minecraft.manifest.VersionManifestService
import io.ezz.launcher.core.minecraft.manifest.VersionMerger
import io.ezz.launcher.core.minecraft.resolver.AssetResolver
import io.ezz.launcher.core.minecraft.resolver.LibraryResolver
import io.ezz.launcher.core.minecraft.resolver.NativeExtractor
import io.ezz.launcher.core.minecraft.resolver.OperatingSystem
import io.ezz.launcher.core.model.account.Account
import io.ezz.launcher.core.model.download.DownloadProgress
import io.ezz.launcher.core.model.download.DownloadResult
import io.ezz.launcher.core.model.download.DownloadTask
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.model.minecraft.VersionInfo
import io.ezz.launcher.core.model.runtime.LaunchError
import io.ezz.launcher.core.model.runtime.ProcessState
import io.ezz.launcher.core.network.downloader.DownloadManager
import io.ezz.launcher.core.runtime.detector.JavaRuntimeDetector
import io.ezz.launcher.core.runtime.process.ProcessEvent
import io.ezz.launcher.core.runtime.process.ProcessLauncher
import io.ezz.launcher.core.storage.path.PathProvider
import io.ezz.launcher.core.storage.repository.InstanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okio.FileSystem
import okio.Path
import java.io.File

sealed interface LaunchEvent {
    data class StateChanged(val state: ProcessState) : LaunchEvent
    data class ProgressUpdate(val progress: DownloadProgress) : LaunchEvent
    data class LogReceived(val line: String, val isError: Boolean) : LaunchEvent
}

class LaunchEngine(
    private val pathProvider: PathProvider,
    private val versionManifestService: VersionManifestService,
    private val libraryResolver: LibraryResolver,
    private val assetResolver: AssetResolver,
    private val fabricInstaller: FabricInstaller,
    private val optiFineInstaller: OptiFineInstaller,
    private val downloadManager: DownloadManager,
    private val authManager: AuthManager,
    private val instanceRepository: InstanceRepository,
    private val processLauncher: ProcessLauncher,
    private val vaultSkinRepository: io.ezz.launcher.core.storage.repository.VaultSkinRepository? = null,
    private val fileSystem: FileSystem = FileSystem.SYSTEM
) {

    fun launch(
        instance: Instance,
        account: Account
    ): Flow<LaunchEvent> = flow {
        try {
            emit(LaunchEvent.StateChanged(ProcessState.Preparing("Validating account...")))
            val validAccount = authManager.getValidSession(account)

            emit(LaunchEvent.StateChanged(ProcessState.Preparing("Checking Java runtime...")))
            val javaRuntime = resolveJavaRuntime(instance)
            val requiredJavaMajor = JavaRuntimeDetector.getRequiredJavaMajorVersion(instance.minecraftVersion)

            // Validate Java executable (on desktop systems)
            val isAndroid = JavaRuntimeDetector.isAndroid()
            val javaFile = File(javaRuntime.path)
            if (!isAndroid && javaFile.isAbsolute && (!javaFile.exists() || !javaFile.canExecute())) {
                emit(LaunchEvent.StateChanged(ProcessState.Failed(
                    LaunchError.MissingJavaRuntime("Java executable not found at '${javaRuntime.path}'. Please install Java $requiredJavaMajor or select a valid Java runtime in Instance Settings.")
                )))
                return@flow
            }

            // Validate Java version compatibility
            if (!isAndroid && javaRuntime.majorVersion < requiredJavaMajor && javaRuntime.fullVersion != "System PATH") {
                emit(LaunchEvent.StateChanged(ProcessState.Failed(
                    LaunchError.IncompatibleJava(required = requiredJavaMajor, found = javaRuntime.majorVersion)
                )))
                return@flow
            }

            emit(LaunchEvent.StateChanged(ProcessState.Preparing("Resolving version metadata...")))
            val versionInfo = resolveVersionInfo(instance)

            emit(LaunchEvent.StateChanged(ProcessState.Preparing("Resolving required files...")))
            val clientDownloadTask = resolveClientJarTask(versionInfo, instance.minecraftVersion)
            val libraryResolved = libraryResolver.resolveLibraries(versionInfo)
            val libraryTasks = libraryResolved.mapNotNull { it.downloadTask }
            val assetTasks = assetResolver.resolveAssetTasks(versionInfo)

            val allDownloadTasks = mutableListOf<DownloadTask>()
            if (clientDownloadTask != null) {
                allDownloadTasks.add(clientDownloadTask)
            }
            allDownloadTasks.addAll(libraryTasks)
            allDownloadTasks.addAll(assetTasks)

            // Filter out existing verified files
            val missingTasks = allDownloadTasks.filter { task ->
                val f = File(task.destinationPath)
                !f.exists() || f.length() == 0L
            }

            if (missingTasks.isNotEmpty()) {
                emit(LaunchEvent.StateChanged(ProcessState.Preparing("Downloading files (0/${missingTasks.size})...", 0f)))
                val downloadResult = downloadManager.downloadAll(missingTasks) { progress: DownloadProgress ->
                    // download progress
                }

                if (downloadResult is DownloadResult.Failure) {
                    emit(LaunchEvent.StateChanged(ProcessState.Failed(LaunchError.DownloadFailed(downloadResult.message))))
                    return@flow
                }
            }

            // Extract natives
            emit(LaunchEvent.StateChanged(ProcessState.Preparing("Extracting native binaries...")))
            val nativeJars = libraryResolved.filter { it.isNative }.map { it.localPath }
            val nativesDir = pathProvider.getInstanceNativesDirectory(instance.id)
            NativeExtractor.extractNatives(nativeJars, nativesDir, fileSystem)

            // Build classpath
            var classpath = libraryResolved.filter { !it.isNative }.map { it.localPath }
            val clientJarPath = pathProvider.versionsDirectory
                .resolve(instance.minecraftVersion)
                .resolve("${instance.minecraftVersion}.jar")

            val gameDir = pathProvider.getInstanceGameDirectory(instance.id)
            val assetsDir = pathProvider.assetsDirectory

            // Apply Vault Skin for Offline accounts
            var skinResolved = false
            var skinPrepared = false
            var effectiveSkinSource = if (validAccount.type == io.ezz.launcher.core.model.account.AccountType.OFFLINE) "VAULT" else "ONLINE"
            var effectiveSkinHash = ""
            var activeVaultSkinName = "Default"
            var activeVaultSkinModel = "CLASSIC"
            var activeSkinFileName = "default.png"
            var clientIntegrationStatus = "READY"

            if (validAccount.type == io.ezz.launcher.core.model.account.AccountType.OFFLINE && vaultSkinRepository != null) {
                try {
                    val activeSkin = vaultSkinRepository.getActiveSkin(validAccount.id)
                    if (activeSkin != null) {
                        skinResolved = true
                        activeVaultSkinName = activeSkin.name
                        activeVaultSkinModel = activeSkin.modelType.name
                        activeSkinFileName = activeSkin.fileName
                        effectiveSkinHash = activeSkin.fileHash
                        val skinBytes = vaultSkinRepository.getSkinBytes(activeSkin)
                        val skinResult = io.ezz.launcher.core.runtime.skin.OfflineSkinInjector.applyVaultSkin(
                            instance = instance,
                            account = validAccount,
                            skin = activeSkin,
                            skinBytes = skinBytes,
                            pathProvider = pathProvider,
                            fileSystem = fileSystem
                        )
                        skinPrepared = skinResult.applied
                        if (skinResult.applied && skinResult.overrideJarPath != null && fileSystem.exists(skinResult.overrideJarPath)) {
                            // Prepend override JAR to classpath so ClassLoader directly returns custom player textures
                            classpath = listOf(skinResult.overrideJarPath) + classpath
                            clientIntegrationStatus = if (instance.loaderType == io.ezz.launcher.core.model.instance.LoaderType.FABRIC) {
                                "APPLIED (Fabric Mod, Classpath & ResourcePack)"
                            } else {
                                "APPLIED (Classpath & ResourcePack)"
                            }
                        } else {
                            clientIntegrationStatus = "FAILED"
                        }
                    } else {
                        effectiveSkinSource = "DEFAULT"
                        // Also apply canonical default skin for offline accounts with no custom skin
                        val defaultResult = io.ezz.launcher.core.runtime.skin.OfflineSkinInjector.applyVaultSkin(
                            instance = instance,
                            account = validAccount,
                            skin = null,
                            skinBytes = null,
                            pathProvider = pathProvider,
                            fileSystem = fileSystem
                        )
                        skinPrepared = defaultResult.applied
                        if (defaultResult.applied && defaultResult.overrideJarPath != null && fileSystem.exists(defaultResult.overrideJarPath)) {
                            classpath = listOf(defaultResult.overrideJarPath) + classpath
                            clientIntegrationStatus = "DEFAULT APPLIED (Classpath & ResourcePack)"
                        }
                    }
                } catch (e: Exception) {
                    clientIntegrationStatus = "ERROR: ${e.message}"
                }
            } else if (validAccount.type == io.ezz.launcher.core.model.account.AccountType.MICROSOFT) {
                effectiveSkinSource = "ONLINE"
                effectiveSkinHash = validAccount.skinHash ?: validAccount.skinUrl?.substringAfterLast("/") ?: "official_profile"
                clientIntegrationStatus = "AUTHENTICATED (Official Mojang Profile)"
            }

            emit(LaunchEvent.StateChanged(ProcessState.Preparing("Building launch command...")))
            val launchCommand = LaunchArgumentBuilder.buildLaunchCommand(
                instance = instance,
                account = validAccount,
                versionInfo = versionInfo,
                classpathEntries = classpath,
                clientJarPath = clientJarPath,
                nativesDir = nativesDir,
                assetsDir = assetsDir,
                gameDir = gameDir,
                javaBinaryPath = javaRuntime.path
            )

            // Update instance last played
            instanceRepository.updateInstance(
                instance.copy(lastPlayedAt = System.currentTimeMillis())
            )

            val launchStartTime = System.currentTimeMillis()
            val workingDir = gameDir.toFile()
            workingDir.mkdirs()

            // Emit full diagnostics log before launch
            emit(LaunchEvent.LogReceived("==================== [EZZ-SKIN] LAUNCH DIAGNOSTICS ====================", isError = false))
            emit(LaunchEvent.LogReceived("[EZZ-SKIN] Account                  : ${validAccount.username}", isError = false))
            emit(LaunchEvent.LogReceived("[EZZ-SKIN] UUID                     : ${validAccount.uuid}", isError = false))
            emit(LaunchEvent.LogReceived("[EZZ-SKIN] Type                     : ${validAccount.type}", isError = false))
            emit(LaunchEvent.LogReceived("[EZZ-SKIN] Skin Source              : $effectiveSkinSource", isError = false))
            emit(LaunchEvent.LogReceived("[EZZ-SKIN] Vault Skin               : $activeVaultSkinName ($activeVaultSkinModel)", isError = false))
            emit(LaunchEvent.LogReceived("[EZZ-SKIN] Skin File                : $activeSkinFileName", isError = false))
            emit(LaunchEvent.LogReceived("[EZZ-SKIN] Skin Hash                : ${effectiveSkinHash.ifBlank { "default_canonical" }}", isError = false))
            emit(LaunchEvent.LogReceived("[EZZ-SKIN] Minecraft Version        : ${instance.minecraftVersion}", isError = false))
            emit(LaunchEvent.LogReceived("[EZZ-SKIN] Loader                   : ${instance.loaderType}", isError = false))
            emit(LaunchEvent.LogReceived("[EZZ-SKIN] Texture Registration     : ${if (skinPrepared) "SUCCESS" else "BYPASSED"}", isError = false))
            emit(LaunchEvent.LogReceived("[EZZ-SKIN] Player Skin Resolution   : ${if (skinResolved) "SUCCESS" else "DEFAULT"}", isError = false))
            emit(LaunchEvent.LogReceived("[EZZ-SKIN] Player Skin Bound        : ${if (skinPrepared) "SUCCESS" else "BYPASSED"}", isError = false))
            emit(LaunchEvent.LogReceived("[EZZ-SKIN] Client Integration       : $clientIntegrationStatus", isError = false))
            emit(LaunchEvent.LogReceived("------------------------------------------------------------------------", isError = false))
            emit(LaunchEvent.LogReceived("Instance ID       : ${instance.id}", isError = false))
            emit(LaunchEvent.LogReceived("Instance Name     : ${instance.name}", isError = false))
            emit(LaunchEvent.LogReceived("Java Runtime      : ${javaRuntime.path} (Java ${javaRuntime.majorVersion}, ${javaRuntime.vendor})", isError = false))
            emit(LaunchEvent.LogReceived("Required Java     : Java $requiredJavaMajor", isError = false))
            emit(LaunchEvent.LogReceived("Game Directory    : $gameDir", isError = false))
            emit(LaunchEvent.LogReceived("Natives Directory : $nativesDir", isError = false))
            emit(LaunchEvent.LogReceived("Libraries Count   : ${classpath.size} JARs", isError = false))
            emit(LaunchEvent.LogReceived("Working Directory : $workingDir", isError = false))
            emit(LaunchEvent.LogReceived("Memory Allocation : ${instance.minMemoryMb}MB min / ${instance.maxMemoryMb}MB max", isError = false))

            val sanitizedCommand = launchCommand.map { arg ->
                when {
                    arg.length > 50 && (arg.contains("eyJ") || arg.contains("token") || arg.contains("auth")) -> "[REDACTED_TOKEN]"
                    arg == validAccount.uuid -> "[PLAYER_UUID]"
                    else -> arg
                }
            }
            emit(LaunchEvent.LogReceived("Launch Arguments  : ${sanitizedCommand.joinToString(" ")}", isError = false))
            emit(LaunchEvent.LogReceived("========================================================================", isError = false))

            // Preparing to start process
            emit(LaunchEvent.StateChanged(ProcessState.Preparing("Starting Minecraft Java Process...", 0.99f)))

            processLauncher.launch(launchCommand, workingDir).collect { event ->
                when (event) {
                    is ProcessEvent.Started -> {
                        val processStartedAt = System.currentTimeMillis()
                        emit(LaunchEvent.StateChanged(ProcessState.Running(processId = event.pid, startedAt = processStartedAt)))
                        emit(LaunchEvent.LogReceived("=== Process Started (PID: ${event.pid}) ===", isError = false))
                    }
                    is ProcessEvent.LogOutput -> {
                        emit(LaunchEvent.LogReceived(event.line, event.isError))
                    }
                    is ProcessEvent.Terminated -> {
                        val playTimeSeconds = (System.currentTimeMillis() - launchStartTime) / 1000L
                        val updated = instance.copy(
                            totalPlayTimeSeconds = instance.totalPlayTimeSeconds + playTimeSeconds
                        )
                        instanceRepository.updateInstance(updated)

                        if (event.exitCode != 0) {
                            emit(LaunchEvent.LogReceived("=== Minecraft exited with non-zero exit code: ${event.exitCode} ===", isError = true))

                            val crashDir = gameDir.resolve("crash-reports").toFile()
                            val latestCrash = crashDir.listFiles()
                                ?.filter { it.isFile && it.name.endsWith(".txt") }
                                ?.maxByOrNull { it.lastModified() }

                            if (latestCrash != null && latestCrash.exists()) {
                                emit(LaunchEvent.LogReceived("Crash report: ${latestCrash.absolutePath}", isError = true))
                                try {
                                    latestCrash.readLines().take(25).forEach {
                                        emit(LaunchEvent.LogReceived(it, isError = true))
                                    }
                                } catch (e: Exception) {
                                    // ignore crash report read error
                                }
                            }
                        } else {
                            emit(LaunchEvent.LogReceived("=== Minecraft Process Terminated Cleanly (Exit Code 0) ===", isError = false))
                        }

                        emit(LaunchEvent.StateChanged(ProcessState.Exited(event.exitCode)))
                    }
                    is ProcessEvent.Error -> {
                        emit(LaunchEvent.LogReceived("=== Process Execution Error: ${event.message} ===", isError = true))
                        emit(LaunchEvent.StateChanged(ProcessState.Failed(LaunchError.ExecutionFailed(event.message, event.cause))))
                    }
                }
            }

        } catch (e: Exception) {
            emit(LaunchEvent.StateChanged(ProcessState.Failed(LaunchError.ExecutionFailed(e.message ?: "Launch failed", e))))
        }
    }

    private suspend fun resolveVersionInfo(instance: Instance): VersionInfo {
        val baseVersion = versionManifestService.getVersionInfo(instance.minecraftVersion)

        return when (instance.loaderType) {
            LoaderType.VANILLA -> baseVersion
            LoaderType.FABRIC -> {
                val loaderVer = instance.loaderVersion ?: "0.16.9"
                val fabricProfile = fabricInstaller.install(instance.minecraftVersion, loaderVer)
                VersionMerger.merge(fabricProfile, baseVersion)
            }
            LoaderType.OPTIFINE -> {
                val optifineVer = instance.loaderVersion ?: "HD_U_I7"
                val optifineProfile = optiFineInstaller.install(instance.minecraftVersion, optifineVer)
                VersionMerger.merge(optifineProfile, baseVersion)
            }
        }
    }

    private fun resolveClientJarTask(versionInfo: VersionInfo, versionId: String): DownloadTask? {
        val clientArtifact = versionInfo.downloads?.client ?: return null
        val clientJarPath = pathProvider.versionsDirectory.resolve(versionId).resolve("$versionId.jar")

        return DownloadTask(
            url = clientArtifact.url,
            destinationPath = clientJarPath.toString(),
            expectedSha1 = clientArtifact.sha1,
            expectedSize = clientArtifact.size,
            description = "Minecraft Client JAR ($versionId)"
        )
    }

    private fun resolveJavaRuntime(instance: Instance): io.ezz.launcher.core.model.runtime.JavaRuntime {
        val javaPath = instance.javaPath
        if (!javaPath.isNullOrBlank()) {
            val custom = JavaRuntimeDetector.inspectJavaHome(javaPath)
            if (custom != null) return custom
            val file = File(javaPath)
            if (file.exists()) {
                return io.ezz.launcher.core.model.runtime.JavaRuntime(
                    path = javaPath,
                    majorVersion = JavaRuntimeDetector.getRequiredJavaMajorVersion(instance.minecraftVersion),
                    fullVersion = "Custom Runtime",
                    vendor = "Custom"
                )
            }
        }

        val detected = JavaRuntimeDetector.detectInstalledRuntimes()
        return JavaRuntimeDetector.findBestRuntime(instance.minecraftVersion, detected)
    }
}
