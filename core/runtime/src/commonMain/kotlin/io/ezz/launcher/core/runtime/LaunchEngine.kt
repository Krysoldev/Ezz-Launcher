package io.ezz.launcher.core.runtime

import io.ezz.launcher.core.minecraft.launch.LaunchArgumentBuilder
import io.ezz.launcher.core.minecraft.loader.fabric.FabricInstaller
import io.ezz.launcher.core.minecraft.loader.optifine.OptiFineInstaller
import io.ezz.launcher.core.minecraft.manifest.VersionManifestService
import io.ezz.launcher.core.minecraft.manifest.VersionMerger
import io.ezz.launcher.core.minecraft.options.MinecraftOptionsManager
import io.ezz.launcher.core.minecraft.resolver.AssetResolver
import io.ezz.launcher.core.minecraft.resolver.LibraryResolver
import io.ezz.launcher.core.minecraft.resolver.NativeExtractor
import io.ezz.launcher.core.minecraft.resolver.OperatingSystem
import io.ezz.launcher.core.model.account.Account
import io.ezz.launcher.core.model.account.OfflineAccount
import io.ezz.launcher.core.model.download.DownloadProgress
import io.ezz.launcher.core.model.download.DownloadResult
import io.ezz.launcher.core.model.download.DownloadTask
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.model.instance.PerformanceProfile
import io.ezz.launcher.core.model.minecraft.VersionInfo
import io.ezz.launcher.core.model.runtime.JavaRuntime
import io.ezz.launcher.core.model.runtime.LaunchError
import io.ezz.launcher.core.model.runtime.ProcessState
import io.ezz.launcher.core.network.downloader.DownloadManager
import io.ezz.launcher.core.runtime.cache.IncrementalLaunchCache
import io.ezz.launcher.core.runtime.detector.GpuDetector
import io.ezz.launcher.core.runtime.detector.JavaRuntimeDetector
import io.ezz.launcher.core.runtime.process.ProcessEvent
import io.ezz.launcher.core.runtime.process.ProcessLauncher
import io.ezz.launcher.core.model.runtime.LauncherSettings
import io.ezz.launcher.core.runtime.discord.DiscordRpcService
import io.ezz.launcher.core.storage.path.PathProvider
import io.ezz.launcher.core.storage.repository.InstanceRepository
import io.ezz.launcher.core.storage.repository.SettingsRepository
import io.ezz.launcher.core.storage.repository.VaultSkinRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okio.FileSystem
import java.io.File

sealed class LaunchEvent {
    data class StateChanged(val state: ProcessState) : LaunchEvent()
    data class ProgressUpdate(val progress: DownloadProgress) : LaunchEvent()
    data class LogReceived(val line: String, val isError: Boolean = false) : LaunchEvent()
}

class LaunchEngine(
    private val pathProvider: PathProvider,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val versionManifestService: VersionManifestService,
    private val fabricInstaller: FabricInstaller,
    private val optiFineInstaller: OptiFineInstaller,
    private val libraryResolver: LibraryResolver,
    private val assetResolver: AssetResolver,
    private val downloadManager: DownloadManager,
    private val processLauncher: ProcessLauncher,
    private val instanceRepository: InstanceRepository,
    private val vaultSkinRepository: VaultSkinRepository? = null,
    private val settingsRepository: SettingsRepository? = null,
    private val discordRpcService: DiscordRpcService? = null
) {
    fun launch(
        instance: Instance,
        account: Account? = null
    ): Flow<LaunchEvent> = flow {
        val launchPreparationStartTime = System.currentTimeMillis()

        try {
            emit(LaunchEvent.StateChanged(ProcessState.Preparing("Preparing launch environment...")))

            val validAccount = account ?: OfflineAccount(
                id = "default-offline",
                username = "Player",
                uuid = "00000000-0000-0000-0000-000000000000"
            )

            // Resolve and validate Java runtime
            val currentSettings = settingsRepository?.settings?.value ?: LauncherSettings()
            emit(LaunchEvent.StateChanged(ProcessState.Preparing("Resolving Java Runtime...")))
            val requiredJavaMajor = JavaRuntimeDetector.getRequiredJavaMajorVersion(instance.minecraftVersion)
            val javaRuntime = resolveJavaRuntime(instance, currentSettings.defaultJavaPath)

            // Check if Java runtime binary actually exists
            val javaFile = File(javaRuntime.path)
            if (!javaFile.exists() && javaRuntime.fullVersion != "System PATH") {
                emit(LaunchEvent.StateChanged(ProcessState.Failed(
                    LaunchError.MissingJavaRuntime("Java executable not found at '${javaRuntime.path}'. Please install Java $requiredJavaMajor (64-Bit) or select a valid Java runtime in Instance Settings.")
                )))
                return@flow
            }

            // Validate Java version compatibility
            if (javaRuntime.majorVersion < requiredJavaMajor && javaRuntime.fullVersion != "System PATH") {
                emit(LaunchEvent.StateChanged(ProcessState.Failed(
                    LaunchError.IncompatibleJava(required = requiredJavaMajor, found = javaRuntime.majorVersion)
                )))
                return@flow
            }

            // Warning if 32-bit Java is detected
            if (!javaRuntime.is64Bit) {
                emit(LaunchEvent.LogReceived("[WARNING] Detected 32-Bit Java runtime. Maximum usable memory is ~1.5 GB. 64-Bit Java is strongly recommended for optimal FPS and stability.", isError = true))
            }

            emit(LaunchEvent.StateChanged(ProcessState.Preparing("Resolving version metadata...")))
            val versionInfo = resolveVersionInfo(instance)

            emit(LaunchEvent.StateChanged(ProcessState.Preparing("Resolving required dependencies...")))
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

            // Fast incremental verification of cached files
            val missingTasks = allDownloadTasks.filter { task: DownloadTask ->
                val f = File(task.destinationPath)
                !IncrementalLaunchCache.isFileValid(f, task.expectedSize)
            }

            if (missingTasks.isNotEmpty()) {
                emit(LaunchEvent.StateChanged(ProcessState.Preparing("Downloading files (0/${missingTasks.size})...", 0f)))
                val downloadResult = downloadManager.downloadAll(missingTasks) { _: DownloadProgress -> }

                if (downloadResult is DownloadResult.Failure) {
                    emit(LaunchEvent.StateChanged(ProcessState.Failed(LaunchError.DownloadFailed(downloadResult.message))))
                    return@flow
                }

                // Mark successfully downloaded files in incremental cache
                missingTasks.forEach { task: DownloadTask ->
                    IncrementalLaunchCache.markValid(File(task.destinationPath))
                }
            }

            // Extract natives
            emit(LaunchEvent.StateChanged(ProcessState.Preparing("Extracting native libraries...")))
            val nativeJars = libraryResolved.filter { it.isNative }.map { it.localPath }
            val nativesDir = pathProvider.getInstanceNativesDirectory(instance.id)
            NativeExtractor.extractNatives(nativeJars, nativesDir, fileSystem)

            // Build classpath
            val classpath = libraryResolved.filter { !it.isNative }.map { it.localPath }
            val clientJarPath = pathProvider.versionsDirectory
                .resolve(instance.minecraftVersion)
                .resolve("${instance.minecraftVersion}.jar")

            val gameDir = pathProvider.getInstanceGameDirectory(instance.id)
            val assetsDir = pathProvider.assetsDirectory
            val workingDir = gameDir.toFile()
            val hwProfile = io.ezz.launcher.core.runtime.detector.HardwareDetector.detectHardware()

            // Apply Video / Performance Profile to options.txt if configured
            MinecraftOptionsManager.applyPerformanceProfile(
                gameDir = workingDir,
                profile = instance.performanceProfile,
                fpsMode = instance.fpsMode,
                customFpsLimit = instance.customFpsLimit,
                displayRefreshRate = hwProfile.displayRefreshRateHz
            )
            io.ezz.launcher.core.minecraft.options.SodiumConfigManager.optimizeSodiumConfig(workingDir, instance.performanceProfile)

            val hasShaders = MinecraftOptionsManager.hasActiveShaders(workingDir)
            if (hasShaders && (instance.performanceProfile == PerformanceProfile.MAX_FPS || instance.performanceProfile == PerformanceProfile.EXTREME_FPS)) {
                emit(LaunchEvent.LogReceived("[PERFORMANCE NOTICE] Active shaders detected. Shaders are heavily GPU-bound and will limit maximum framerate. For 500+ FPS, disabling shaders is recommended.", isError = false))
            }

            // Build GPU environment
            val gpuEnvironment = GpuDetector.buildGpuEnvironment(instance.gpuPreference)
            val detectedGpus = GpuDetector.detectGpus()
            val sysMemory = JavaRuntimeDetector.getSystemMemoryInfo()

            // Sync Vault skin metadata launcher-side
            var activeVaultSkinName = "Default"
            var activeVaultSkinModel = "CLASSIC"

            try {
                val activeSkin = vaultSkinRepository?.getActiveSkin(validAccount.id)
                val skinBytes = if (activeSkin != null) vaultSkinRepository?.getSkinBytes(activeSkin) else null

                if (activeSkin != null) {
                    activeVaultSkinName = activeSkin.name
                    activeVaultSkinModel = activeSkin.modelType.name
                }

                // Prepare Fabric client skin mod and per-account config
                io.ezz.launcher.core.minecraft.mod.FabricSkinModManager.prepareInstanceSkinMod(
                    instance = instance,
                    account = validAccount,
                    skin = activeSkin,
                    skinBytes = skinBytes,
                    pathProvider = pathProvider,
                    fileSystem = fileSystem
                )

                // Sync deterministic profile-keyed skin cache for offline operation
                io.ezz.launcher.core.runtime.skin.OfflineSkinManager.syncOfflineSkin(
                    instance = instance,
                    account = validAccount,
                    skin = activeSkin,
                    skinBytes = skinBytes,
                    pathProvider = pathProvider,
                    fileSystem = fileSystem
                )
            } catch (e: Exception) {
                println("[LaunchEngine] Notice during skin mod preparation: ${e.message}")
            }

            var effectiveJavaRuntime = javaRuntime
            val (isCompatible, compatMsg) = JavaRuntimeDetector.checkRuntimeCompatibility(javaRuntime, instance.minecraftVersion)
            if (!isCompatible && instance.javaPath.isNullOrBlank()) {
                val detectedRuntimes = JavaRuntimeDetector.detectInstalledRuntimes()
                val safeRuntime = JavaRuntimeDetector.findBestRuntime(instance.minecraftVersion, detectedRuntimes)
                if (safeRuntime.path != javaRuntime.path && safeRuntime.majorVersion != javaRuntime.majorVersion) {
                    emit(LaunchEvent.LogReceived("[JAVA COMPATIBILITY NOTICE] System default Java is ${javaRuntime.majorVersion} (${javaRuntime.fullVersion}). Minecraft ${instance.minecraftVersion} requires Java ${JavaRuntimeDetector.getRequiredJavaMajorVersion(instance.minecraftVersion)}. Automatically switching to compatible Java ${safeRuntime.majorVersion} (${safeRuntime.path}).", isError = false))
                    effectiveJavaRuntime = safeRuntime
                } else if (!isCompatible) {
                    emit(LaunchEvent.LogReceived("[JAVA COMPATIBILITY WARNING] $compatMsg", isError = true))
                }
            } else if (!isCompatible) {
                emit(LaunchEvent.LogReceived("[JAVA COMPATIBILITY WARNING] $compatMsg", isError = true))
            }

            // Inspect mods directory for Java classfile compatibility with selected Java runtime & whole-instance mod compatibility
            val modsDirectory = gameDir.resolve("mods").toFile()
            if (modsDirectory.exists() && modsDirectory.isDirectory) {
                val installedModJars = modsDirectory.listFiles { _, name -> name.endsWith(".jar") } ?: emptyArray()
                for (modJar in installedModJars) {
                    val compatCheck = io.ezz.launcher.core.minecraft.mod.ModBytecodeValidator.validateJarFile(
                        jarFile = modJar,
                        javaMajorVersion = effectiveJavaRuntime.majorVersion
                    )
                    if (compatCheck is io.ezz.launcher.core.minecraft.mod.ModCompatibilityResult.Incompatible) {
                        if (modJar.name.startsWith("ezz-skin-mod", ignoreCase = true) || modJar.name.contains("ezzskin", ignoreCase = true)) {
                            emit(LaunchEvent.LogReceived("[EZZ SKIN NOTICE] Ezz Skin is unavailable for Java ${effectiveJavaRuntime.majorVersion} (requires Java ${compatCheck.requiredJavaVersion}+). Disabling Ezz Skin for this session to ensure safe launch.", isError = false))
                            val disabledTarget = File(modJar.parentFile, "${modJar.name}.disabled")
                            modJar.renameTo(disabledTarget)
                            continue
                        }

                        val errMsg = compatCheck.errorMessage
                        emit(LaunchEvent.LogReceived(errMsg, isError = true))
                        emit(LaunchEvent.StateChanged(ProcessState.Failed(LaunchError.ExecutionFailed(errMsg))))
                        return@flow
                    }
                }

                // Whole-instance launch compatibility validation
                try {
                    val parsedMods = installedModJars.map { jarFile ->
                        io.ezz.launcher.core.minecraft.mods.LocalModScanner.scanSingleMod(jarFile)
                    }
                    val launchReport = io.ezz.launcher.core.minecraft.mods.ModCompatibilityResolver.validateLaunchCompatibility(
                        minecraftVersion = instance.minecraftVersion,
                        loader = instance.loaderType.name,
                        installedMods = parsedMods
                    )

                    emit(LaunchEvent.LogReceived(launchReport.summaryLine, isError = false))
                    emit(LaunchEvent.LogReceived(launchReport.formattedReport, isError = false))

                    if (launchReport.warnings.isNotEmpty()) {
                        launchReport.warnings.forEach { warning ->
                            emit(LaunchEvent.LogReceived("[MOD COMPATIBILITY NOTICE] $warning", isError = false))
                        }
                    }

                    if (!launchReport.isReadyToLaunch) {
                        if (launchReport.explicitConflicts.isNotEmpty()) {
                            val firstConflict = launchReport.explicitConflicts.first()
                            val conflictMsg = "[MOD COMPATIBILITY ERROR] ${firstConflict.reason}"
                            emit(LaunchEvent.LogReceived(conflictMsg, isError = true))
                            emit(LaunchEvent.StateChanged(ProcessState.Failed(LaunchError.ExecutionFailed(conflictMsg))))
                            return@flow
                        } else if (launchReport.missingDependencies.isNotEmpty()) {
                            val firstMissing = launchReport.missingDependencies.first()
                            val missingMsg = "[MISSING REQUIRED DEPENDENCY ERROR] $firstMissing"
                            emit(LaunchEvent.LogReceived(missingMsg, isError = true))
                            emit(LaunchEvent.StateChanged(ProcessState.Failed(LaunchError.ExecutionFailed(missingMsg))))
                            return@flow
                        }
                    }
                } catch (e: Throwable) {
                    println("[LaunchEngine] Mod launch validation notice: ${e.message}")
                }
            }

            emit(LaunchEvent.StateChanged(ProcessState.Preparing("Building optimized launch command...")))
            val launchCommand = LaunchArgumentBuilder.buildLaunchCommand(
                instance = instance,
                account = validAccount,
                versionInfo = versionInfo,
                classpathEntries = classpath,
                clientJarPath = clientJarPath,
                nativesDir = nativesDir,
                assetsDir = assetsDir,
                gameDir = gameDir,
                javaBinaryPath = effectiveJavaRuntime.path,
                defaultWindowWidth = currentSettings.defaultWindowWidth,
                defaultWindowHeight = currentSettings.defaultWindowHeight,
                defaultFullscreen = currentSettings.defaultFullscreen,
                globalJvmArgs = currentSettings.globalJvmArgs
            )

            // Enforce Windows DirectX GPU Preference for high-performance dedicated graphics
            GpuDetector.ensureWindowsGpuPreference(effectiveJavaRuntime.path, instance.gpuPreference)

            val preparationDurationMs = System.currentTimeMillis() - launchPreparationStartTime
            val cpuCores = Runtime.getRuntime().availableProcessors()

            // Update instance last played and preparation time
            instanceRepository.updateInstance(
                instance.copy(
                    lastPlayedAt = System.currentTimeMillis(),
                    lastLaunchPreparationMs = preparationDurationMs
                )
            )

            // Generate Pre-Launch Live Diagnostic Report
            val diagnosticReport = io.ezz.launcher.core.runtime.diagnostics.PerformanceDiagnosticService.generateDiagnosticReport(
                instance = instance,
                gameDir = workingDir,
                javaRuntime = effectiveJavaRuntime
            )

            emit(LaunchEvent.LogReceived("==================== EZZ LAUNCHER DIAGNOSTIC & PROFILE ====================", isError = false))
            emit(LaunchEvent.LogReceived("Instance Name     : ${instance.name} (MC ${instance.minecraftVersion}, ${instance.loaderType.name})", isError = false))
            emit(LaunchEvent.LogReceived("Account           : ${validAccount.username} (${validAccount.type})", isError = false))
            emit(LaunchEvent.LogReceived("Player Skin       : $activeVaultSkinName ($activeVaultSkinModel)", isError = false))
            emit(LaunchEvent.LogReceived("Launch Prep Time  : ${preparationDurationMs}ms (Incremental validation active)", isError = false))
            emit(LaunchEvent.LogReceived("Java Runtime      : ${effectiveJavaRuntime.path}", isError = false))
            emit(LaunchEvent.LogReceived("Java Details      : Java ${effectiveJavaRuntime.majorVersion} (${if (effectiveJavaRuntime.is64Bit) "64-Bit" else "32-Bit"}), ${effectiveJavaRuntime.vendor} - ${effectiveJavaRuntime.fullVersion}", isError = false))
            emit(LaunchEvent.LogReceived("Memory Allocation : ${instance.minMemoryMb} MB Min / ${instance.maxMemoryMb} MB Max (System Total: ${sysMemory.totalRamMb} MB)", isError = false))
            emit(LaunchEvent.LogReceived("Garbage Collector : ${instance.gcType.displayName}", isError = false))
            emit(LaunchEvent.LogReceived("Performance Mode  : ${instance.performanceProfile.displayName} — ${instance.performanceProfile.description}", isError = false))
            emit(LaunchEvent.LogReceived("FPS Mode / Limit  : ${instance.fpsMode.displayName} (${if (instance.fpsMode == io.ezz.launcher.core.model.instance.FpsMode.CUSTOM) "${instance.customFpsLimit} FPS" else if (instance.fpsMode == io.ezz.launcher.core.model.instance.FpsMode.DISPLAY_LIMIT) "${hwProfile.displayRefreshRateHz} Hz Display Limit" else if (instance.fpsMode == io.ezz.launcher.core.model.instance.FpsMode.UNLIMITED) "Unlimited" else "In-Game Default"})", isError = false))
            emit(LaunchEvent.LogReceived("GPU Preference    : ${instance.gpuPreference.displayName} (Detected: ${detectedGpus.joinToString { it.name }})", isError = false))
            emit(LaunchEvent.LogReceived("Process Priority  : ${instance.processPriority.displayName}", isError = false))
            emit(LaunchEvent.LogReceived("Hardware Context  : $cpuCores CPU Cores, ${sysMemory.totalRamMb / 1024} GB System RAM", isError = false))
            emit(LaunchEvent.LogReceived("Resolution        : ${instance.windowWidth}x${instance.windowHeight}", isError = false))
            emit(LaunchEvent.LogReceived("Game Directory    : $gameDir", isError = false))
            emit(LaunchEvent.LogReceived("Libraries Count   : ${classpath.size} JARs", isError = false))

            val sanitizedCommand = launchCommand.map { arg ->
                when {
                    arg.length > 50 && (arg.contains("eyJ") || arg.contains("token") || arg.contains("auth")) -> "[REDACTED_TOKEN]"
                    arg == validAccount.uuid -> "[PLAYER_UUID]"
                    else -> arg
                }
            }
            emit(LaunchEvent.LogReceived("Launch Command    : ${sanitizedCommand.joinToString(" ")}", isError = false))
            emit(LaunchEvent.LogReceived("===========================================================================", isError = false))
            emit(LaunchEvent.LogReceived(diagnosticReport.formattedReport, isError = false))

            // Preparing to start process
            emit(LaunchEvent.StateChanged(ProcessState.Preparing("Starting Minecraft Java Process...", 0.99f)))

            val launchStartTime = System.currentTimeMillis()

            processLauncher.launch(
                command = launchCommand,
                workingDirectory = workingDir,
                environment = gpuEnvironment,
                processPriority = instance.processPriority
            ).collect { event ->
                when (event) {
                    is ProcessEvent.Started -> {
                        val processStartedAt = System.currentTimeMillis()
                        emit(LaunchEvent.StateChanged(ProcessState.Running(processId = event.pid, startedAt = processStartedAt)))
                        emit(LaunchEvent.LogReceived("=== Process Started (PID: ${event.pid}) ===", isError = false))

                        discordRpcService?.updateActivity(
                            instanceName = instance.name,
                            minecraftVersion = instance.minecraftVersion,
                            startedAtMs = processStartedAt,
                            processId = event.pid,
                            enabled = currentSettings.enableDiscordRpc
                        )

                        val activeNvidiaGpuInfo = GpuDetector.getActiveNvidiaGpuProcessInfo(event.pid)
                        if (activeNvidiaGpuInfo != null) {
                            emit(LaunchEvent.LogReceived("[GPU TELEMETRY] Confirmed Minecraft javaw.exe (PID: ${event.pid}) active on NVIDIA GPU ($activeNvidiaGpuInfo)", isError = false))
                        } else {
                            emit(LaunchEvent.LogReceived("[GPU TELEMETRY] Process launched with ${instance.gpuPreference.displayName} GPU preference.", isError = false))
                        }
                    }
                    is ProcessEvent.LogOutput -> {
                        emit(LaunchEvent.LogReceived(event.line, event.isError))
                    }
                    is ProcessEvent.Terminated -> {
                        discordRpcService?.clearActivity()
                        val playTimeSeconds = (System.currentTimeMillis() - launchStartTime) / 1000L

                        if (event.exitCode == 0) {
                            // Successful session: save Known Good Configuration snapshot
                            val updated = instance.copy(
                                totalPlayTimeSeconds = instance.totalPlayTimeSeconds + playTimeSeconds,
                                knownGoodSnapshot = instance.createPerformanceSnapshot(),
                                lastLaunchPreparationMs = preparationDurationMs
                            )
                            instanceRepository.updateInstance(updated)
                            emit(LaunchEvent.LogReceived("=== Process Terminated Cleanly (Exit Code 0). Known-Good Configuration updated. ===", isError = false))
                        } else {
                            val updated = instance.copy(
                                totalPlayTimeSeconds = instance.totalPlayTimeSeconds + playTimeSeconds
                            )
                            instanceRepository.updateInstance(updated)
                            emit(LaunchEvent.LogReceived("=== Process Exited with code ${event.exitCode} ===", isError = true))

                            // Perform automatic root cause crash diagnosis
                            val diagnosis = io.ezz.launcher.core.runtime.diagnostics.CrashDiagnosticAnalyzer.analyzeCrash(
                                gameDir = workingDir,
                                instance = instance,
                                runtime = effectiveJavaRuntime,
                                exitCode = event.exitCode
                            )
                            emit(LaunchEvent.LogReceived("==================== CRASH DIAGNOSTIC REPORT ====================", isError = true))
                            emit(LaunchEvent.LogReceived("Category       : ${diagnosis.category.title}", isError = true))
                            emit(LaunchEvent.LogReceived("Diagnosis      : ${diagnosis.summary}", isError = true))
                            emit(LaunchEvent.LogReceived("Recommendation : ${diagnosis.recommendation}", isError = true))
                            diagnosis.problematicFrame?.let {
                                emit(LaunchEvent.LogReceived("Problem Frame  : $it", isError = true))
                            }
                            diagnosis.reportFilePath?.let {
                                emit(LaunchEvent.LogReceived("Crash File     : $it", isError = true))
                            }
                            emit(LaunchEvent.LogReceived("=================================================================", isError = true))

                            if (instance.knownGoodSnapshot != null) {
                                emit(LaunchEvent.LogReceived("[ROLLBACK TIP] If this crash was caused by experimental JVM or performance settings, you can restore your Known-Good Configuration in Instance Settings.", isError = true))
                            }
                        }

                        emit(LaunchEvent.StateChanged(ProcessState.Exited(event.exitCode)))
                    }
                    is ProcessEvent.Error -> {
                        discordRpcService?.clearActivity()
                        emit(LaunchEvent.LogReceived("=== Process Execution Error: ${event.message} ===", isError = true))
                        emit(LaunchEvent.StateChanged(ProcessState.Failed(LaunchError.ExecutionFailed(event.message, event.cause))))
                    }
                }
            }

        } catch (e: Exception) {
            discordRpcService?.clearActivity()
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

    private fun resolveJavaRuntime(instance: Instance, defaultJavaPath: String? = null): JavaRuntime {
        val javaPath = if (!instance.javaPath.isNullOrBlank()) instance.javaPath else defaultJavaPath
        if (!javaPath.isNullOrBlank()) {
            val custom = JavaRuntimeDetector.inspectJavaHome(javaPath)
            if (custom != null) return custom
            val file = File(javaPath)
            if (file.exists()) {
                return JavaRuntime(
                    path = javaPath,
                    majorVersion = JavaRuntimeDetector.getRequiredJavaMajorVersion(instance.minecraftVersion),
                    fullVersion = "Custom Runtime",
                    vendor = "Custom",
                    is64Bit = true
                )
            }
        }

        val detected = JavaRuntimeDetector.detectInstalledRuntimes()
        return JavaRuntimeDetector.findBestRuntime(instance.minecraftVersion, detected)
    }
}
