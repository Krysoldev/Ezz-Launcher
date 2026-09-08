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
import io.ezz.launcher.core.model.account.AccountType
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import okio.FileSystem
import java.io.File

sealed class LaunchEvent {
    data class StateChanged(val state: ProcessState) : LaunchEvent()
    data class ProgressUpdate(
        val operationId: String,
        val stage: String,
        val status: String,
        val progress: Float,
        val displayProgress: Float = progress,
        val completedWork: Long = 0L,
        val totalWork: Long = 0L,
        val isIndeterminate: Boolean = false
    ) : LaunchEvent()
    data class DownloadProgressUpdate(val progress: DownloadProgress) : LaunchEvent()
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
        account: Account? = null,
        operationId: String = java.util.UUID.randomUUID().toString(),
        playClickTime: Long = System.currentTimeMillis(),
        skipDisplayInterpolation: Boolean = false
    ): Flow<LaunchEvent> = channelFlow {
        val launchPreparationStartTime = System.currentTimeMillis()
        var runningPid: Long = 0L

        var realProgress = 0f
        var currentStage = "PREPARING LAUNCH"
        var currentStatus = "Preparing launch environment..."
        var currentCompletedWork = 0L
        var currentTotalWork = 0L
        var currentIsIndeterminate = false

        var realProgressStartTime = 0L
        var realProgress25Time = 0L
        var realProgress50Time = 0L
        var realProgress75Time = 0L
        var realProgressReadyTime = 0L
        var displayProgress100Time = 0L
        var processSpawnStartTime = 0L
        var processSpawnedTime = 0L

        fun emit(event: LaunchEvent) {
            trySend(event)
        }

        println("[PerformanceTiming] PLAY_CLICK: $playClickTime")
        println("[PerformanceTiming] LAUNCH_PREPARATION_START: $launchPreparationStartTime (+${launchPreparationStartTime - playClickTime}ms from click)")
        emit(LaunchEvent.LogReceived("[PerformanceTiming] PLAY_CLICK: $playClickTime", isError = false))
        emit(LaunchEvent.LogReceived("[PerformanceTiming] LAUNCH_PREPARATION_START: $launchPreparationStartTime (+${launchPreparationStartTime - playClickTime}ms)", isError = false))

        var displayProgress = 0f
        val isDisplayComplete = kotlinx.coroutines.CompletableDeferred<Unit>()

        var jumpStartProgress = 0f
        var jumpTargetProgress = 0f
        var jumpStartTime = System.currentTimeMillis()
        var jumpDurationMs = 0

        fun updateProgress(
            stage: String,
            status: String,
            rawProgress: Float,
            completedWork: Long = 0L,
            totalWork: Long = 0L,
            isIndeterminate: Boolean = false
        ) {
            val clamped = rawProgress.coerceIn(0f, 1f)
            val safe = maxOf(realProgress, clamped)
            realProgress = safe
            currentStage = stage
            currentStatus = status
            currentCompletedWork = completedWork
            currentTotalWork = totalWork
            currentIsIndeterminate = isIndeterminate

            val now = System.currentTimeMillis()
            if (realProgressStartTime == 0L && safe >= 0.01f) {
                realProgressStartTime = now
                println("[PerformanceTiming] REAL_PROGRESS_START: $realProgressStartTime (+${realProgressStartTime - playClickTime}ms from click)")
                emit(LaunchEvent.LogReceived("[PerformanceTiming] REAL_PROGRESS_START: $realProgressStartTime (+${realProgressStartTime - playClickTime}ms from click)", isError = false))
            }
            if (realProgress25Time == 0L && safe >= 0.25f) {
                realProgress25Time = now
                println("[PerformanceTiming] REAL_PROGRESS_25: $realProgress25Time (+${realProgress25Time - playClickTime}ms from click)")
                emit(LaunchEvent.LogReceived("[PerformanceTiming] REAL_PROGRESS_25: $realProgress25Time (+${realProgress25Time - playClickTime}ms from click)", isError = false))
            }
            if (realProgress50Time == 0L && safe >= 0.50f) {
                realProgress50Time = now
                println("[PerformanceTiming] REAL_PROGRESS_50: $realProgress50Time (+${realProgress50Time - playClickTime}ms from click)")
                emit(LaunchEvent.LogReceived("[PerformanceTiming] REAL_PROGRESS_50: $realProgress50Time (+${realProgress50Time - playClickTime}ms from click)", isError = false))
            }
            if (realProgress75Time == 0L && safe >= 0.75f) {
                realProgress75Time = now
                println("[PerformanceTiming] REAL_PROGRESS_75: $realProgress75Time (+${realProgress75Time - playClickTime}ms from click)")
                emit(LaunchEvent.LogReceived("[PerformanceTiming] REAL_PROGRESS_75: $realProgress75Time (+${realProgress75Time - playClickTime}ms from click)", isError = false))
            }

            if (safe > jumpTargetProgress) {
                jumpStartProgress = displayProgress
                jumpTargetProgress = safe
                jumpStartTime = now
                jumpDurationMs = io.ezz.launcher.core.model.runtime.computeDisplayInterpolationDuration(
                    jumpTargetProgress - jumpStartProgress,
                    isTargetFinal = false
                )
            }

            if (skipDisplayInterpolation) {
                displayProgress = safe
                emit(LaunchEvent.StateChanged(ProcessState.Preparing(status, safe)))
                emit(LaunchEvent.ProgressUpdate(
                    operationId = operationId,
                    stage = stage,
                    status = status,
                    progress = safe,
                    displayProgress = safe,
                    completedWork = completedWork,
                    totalWork = totalWork,
                    isIndeterminate = isIndeterminate
                ))
            }
        }

        val displayInterpolatorJob = if (!skipDisplayInterpolation) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch {
                var lastEmittedPct = -1
                var lastEmittedFloat = 0f

                while (isActive) {
                    val now = System.currentTimeMillis()
                    val target = jumpTargetProgress
                    val start = jumpStartProgress
                    val dur = jumpDurationMs

                    if (target > displayProgress) {
                        val elapsed = now - jumpStartTime
                        val t = if (dur > 0) (elapsed.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 1f
                        val interpolated = start + (target - start) * t
                        // Guarantee displayProgress never exceeds realProgress
                        displayProgress = interpolated.coerceIn(0f, realProgress)

                        val pct = io.ezz.launcher.core.model.runtime.computeDisplayPercentage(
                            displayProgress = displayProgress,
                            isFinished = realProgress >= 1.0f && displayProgress >= 1.0f
                        )
                        if (pct != lastEmittedPct || kotlin.math.abs(displayProgress - lastEmittedFloat) >= 0.002f || displayProgress >= 1.0f) {
                            lastEmittedPct = pct
                            lastEmittedFloat = displayProgress
                            emit(LaunchEvent.ProgressUpdate(
                                operationId = operationId,
                                stage = currentStage,
                                status = currentStatus,
                                progress = realProgress,
                                displayProgress = displayProgress,
                                completedWork = currentCompletedWork,
                                totalWork = currentTotalWork,
                                isIndeterminate = currentIsIndeterminate
                            ))
                            emit(LaunchEvent.StateChanged(ProcessState.Preparing(currentStatus, displayProgress)))
                        }
                    }

                    if (realProgress >= 1.0f && displayProgress >= 1.0f) {
                        isDisplayComplete.complete(Unit)
                        break
                    }
                    kotlinx.coroutines.delay(16L) // ~60 FPS smooth ticker
                }
            }
        } else {
            null
        }

        try {
            updateProgress(
                stage = "PREPARING LAUNCH",
                status = "Preparing launch environment...",
                rawProgress = 0.01f
            )

            val validAccount = account ?: OfflineAccount(
                id = "default-offline",
                username = "Player",
                uuid = "00000000-0000-0000-0000-000000000000"
            )

            // Resolve and validate Java runtime
            val currentSettings = settingsRepository?.settings?.value ?: LauncherSettings()
            updateProgress(
                stage = "RESOLVING JAVA RUNTIME",
                status = "Resolving Java Runtime...",
                rawProgress = 0.03f
            )
            val requiredJavaMajor = JavaRuntimeDetector.getRequiredJavaMajorVersion(instance.minecraftVersion)
            val javaRuntime = resolveJavaRuntime(instance, currentSettings.defaultJavaPath)

            // Check if Java runtime binary actually exists
            val javaFile = File(javaRuntime.path)
            if (!javaFile.exists() && javaRuntime.fullVersion != "System PATH") {
                emit(LaunchEvent.StateChanged(ProcessState.Failed(
                    LaunchError.MissingJavaRuntime("Java executable not found at '${javaRuntime.path}'. Please install Java $requiredJavaMajor (64-Bit) or select a valid Java runtime in Instance Settings.")
                )))
                return@channelFlow
            }

            // Validate Java version compatibility
            if (javaRuntime.majorVersion < requiredJavaMajor && javaRuntime.fullVersion != "System PATH") {
                emit(LaunchEvent.StateChanged(ProcessState.Failed(
                    LaunchError.IncompatibleJava(required = requiredJavaMajor, found = javaRuntime.majorVersion)
                )))
                return@channelFlow
            }

            // Warning if 32-bit Java is detected
            if (!javaRuntime.is64Bit) {
                emit(LaunchEvent.LogReceived("[WARNING] Detected 32-Bit Java runtime. Maximum usable memory is ~1.5 GB. 64-Bit Java is strongly recommended for optimal FPS and stability.", isError = true))
            }

            updateProgress(
                stage = "RESOLVING JAVA RUNTIME",
                status = "Java ${javaRuntime.majorVersion} (${javaRuntime.vendor}) verified",
                rawProgress = 0.05f
            )

            updateProgress(
                stage = "RESOLVING VERSION METADATA",
                status = "Resolving version metadata...",
                rawProgress = 0.08f
            )
            val versionInfo = resolveVersionInfo(instance)

            updateProgress(
                stage = "RESOLVING VERSION METADATA",
                status = "Minecraft ${instance.minecraftVersion} metadata resolved",
                rawProgress = 0.15f
            )

            updateProgress(
                stage = "RESOLVING DEPENDENCIES",
                status = "Resolving required dependencies...",
                rawProgress = 0.16f
            )
            val clientDownloadTask = resolveClientJarTask(versionInfo, instance.minecraftVersion)
            val libraryResolved = libraryResolver.resolveLibraries(versionInfo)
            val libraryTasks = libraryResolved.mapNotNull { it.downloadTask }
            val assetTasks = assetResolver.resolveAssetTasks(versionInfo)

            val allDownloadTasks = (listOfNotNull(clientDownloadTask) + libraryTasks + assetTasks)
                .distinctBy { it.destinationPath }

            // Fast incremental verification of cached files with continuous real-time progress
            val totalTasksCount = allDownloadTasks.size
            val missingTasks = mutableListOf<DownloadTask>()

            var lastProgressEmitTime = 0L
            allDownloadTasks.forEachIndexed { index, task ->
                currentCoroutineContext().ensureActive()
                val f = File(task.destinationPath)
                val isValid = IncrementalLaunchCache.isFileValid(f, task.expectedSize)
                if (!isValid) {
                    missingTasks.add(task)
                }
                val now = System.currentTimeMillis()
                val isLast = (index == totalTasksCount - 1)
                if (totalTasksCount > 0 && (isLast || now - lastProgressEmitTime >= 40L)) {
                    lastProgressEmitTime = now
                    val fraction = (index + 1).toFloat() / totalTasksCount.toFloat()
                    val p = 0.16f + (fraction * 0.14f) // 16% to 30%
                    updateProgress(
                        stage = "VERIFYING DEPENDENCIES",
                        status = "Checking ${task.description.ifBlank { f.name }} (${index + 1}/$totalTasksCount)",
                        rawProgress = p,
                        completedWork = (index + 1).toLong(),
                        totalWork = totalTasksCount.toLong()
                    )
                }
            }

            if (missingTasks.isNotEmpty()) {
                val totalMissingBytes = missingTasks.sumOf { it.expectedSize }
                updateProgress(
                    stage = "DOWNLOADING REQUIRED FILES",
                    status = "Downloading 0/${missingTasks.size} files...",
                    rawProgress = 0.30f,
                    completedWork = 0L,
                    totalWork = totalMissingBytes
                )

                val downloadResult = downloadManager.downloadAll(missingTasks) { dlProgress: DownloadProgress ->
                    val byteFraction = if (dlProgress.totalBytes > 0) {
                        dlProgress.bytesDownloaded.toDouble() / dlProgress.totalBytes.toDouble()
                    } else {
                        dlProgress.currentTaskIndex.toDouble() / dlProgress.totalTasks.coerceAtLeast(1).toDouble()
                    }
                    val overallProgress = (0.30 + (byteFraction.coerceIn(0.0, 1.0) * 0.45)).toFloat() // 30% to 75%
                    val mbDownloaded = dlProgress.bytesDownloaded / (1024 * 1024)
                    val mbTotal = dlProgress.totalBytes / (1024 * 1024)
                    val speedText = if (mbTotal > 0) "$mbDownloaded MB / $mbTotal MB" else "${dlProgress.bytesDownloaded / 1024} KB"

                    updateProgress(
                        stage = "DOWNLOADING REQUIRED FILES",
                        status = "${dlProgress.currentItemName} ($speedText • ${dlProgress.currentTaskIndex}/${dlProgress.totalTasks})",
                        rawProgress = overallProgress,
                        completedWork = dlProgress.bytesDownloaded,
                        totalWork = dlProgress.totalBytes
                    )
                }

                if (downloadResult is DownloadResult.Failure) {
                    emit(LaunchEvent.StateChanged(ProcessState.Failed(LaunchError.DownloadFailed(downloadResult.message))))
                    return@channelFlow
                }

                // Mark successfully downloaded files in incremental cache
                missingTasks.forEach { task: DownloadTask ->
                    IncrementalLaunchCache.markValid(File(task.destinationPath))
                }

                updateProgress(
                    stage = "DOWNLOADING REQUIRED FILES",
                    status = "All ${missingTasks.size} files downloaded successfully",
                    rawProgress = 0.75f,
                    completedWork = totalMissingBytes,
                    totalWork = totalMissingBytes
                )
            } else {
                updateProgress(
                    stage = "VERIFYING DEPENDENCIES",
                    status = "All $totalTasksCount dependencies verified from cache",
                    rawProgress = 0.75f,
                    completedWork = totalTasksCount.toLong(),
                    totalWork = totalTasksCount.toLong()
                )
            }

            val dependenciesReadyTime = System.currentTimeMillis()
            println("[PerformanceTiming] DEPENDENCIES_READY: $dependenciesReadyTime (+${dependenciesReadyTime - launchPreparationStartTime}ms from prep start)")
            emit(LaunchEvent.LogReceived("[PerformanceTiming] DEPENDENCIES_READY: $dependenciesReadyTime (+${dependenciesReadyTime - launchPreparationStartTime}ms from prep start)", isError = false))

            // Extract natives with fine-grained jar-by-jar progress
            updateProgress(
                stage = "EXTRACTING NATIVE LIBRARIES",
                status = "Extracting native libraries...",
                rawProgress = 0.75f
            )
            val nativeJars = libraryResolved.filter { it.isNative }.map { it.localPath }
            val nativesDir = pathProvider.getInstanceNativesDirectory(instance.id)
            val totalNatives = nativeJars.size
            NativeExtractor.extractNatives(nativeJars, nativesDir, fileSystem) { extractedCount, totalCount, jarName ->
                val fraction = extractedCount.toFloat() / totalCount.coerceAtLeast(1).toFloat()
                val progress = 0.75f + (fraction * 0.07f) // 75% to 82%
                updateProgress(
                    stage = "EXTRACTING NATIVE LIBRARIES",
                    status = "Extracting $jarName ($extractedCount/$totalCount)",
                    rawProgress = progress,
                    completedWork = extractedCount.toLong(),
                    totalWork = totalCount.toLong()
                )
            }

            updateProgress(
                stage = "EXTRACTING NATIVE LIBRARIES",
                status = "Native libraries extracted ($totalNatives/$totalNatives)",
                rawProgress = 0.82f,
                completedWork = totalNatives.toLong(),
                totalWork = totalNatives.toLong()
            )

            val filesReadyTime = System.currentTimeMillis()
            println("[PerformanceTiming] FILES_READY: $filesReadyTime (+${filesReadyTime - dependenciesReadyTime}ms)")
            emit(LaunchEvent.LogReceived("[PerformanceTiming] FILES_READY: $filesReadyTime (+${filesReadyTime - dependenciesReadyTime}ms)", isError = false))

            // Build classpath
            val classpath = libraryResolved.filter { !it.isNative }.map { it.localPath }.distinct()
            val clientJarPath = pathProvider.versionsDirectory
                .resolve(instance.minecraftVersion)
                .resolve("${instance.minecraftVersion}.jar")

            val gameDir = pathProvider.getInstanceGameDirectory(instance.id)
            val assetsDir = pathProvider.assetsDirectory
            val workingDir = gameDir.toFile()
            val hwProfile = io.ezz.launcher.core.runtime.detector.HardwareDetector.detectHardware()

            updateProgress(
                stage = "CONFIGURING SETTINGS",
                status = "Applying performance profile and video settings...",
                rawProgress = 0.83f
            )

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
                val totalMods = installedModJars.size
                for ((modIdx, modJar) in installedModJars.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    val fraction = (modIdx + 1).toFloat() / totalMods.coerceAtLeast(1).toFloat()
                    val p = 0.88f + (fraction * 0.05f) // 88% to 93%
                    updateProgress(
                        stage = "VALIDATING MODS",
                        status = "Validating ${modJar.name} (${modIdx + 1}/$totalMods)",
                        rawProgress = p,
                        completedWork = (modIdx + 1).toLong(),
                        totalWork = totalMods.toLong()
                    )

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
                        return@channelFlow
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
                            return@channelFlow
                        } else if (launchReport.missingDependencies.isNotEmpty()) {
                            val firstMissing = launchReport.missingDependencies.first()
                            val missingMsg = "[MISSING REQUIRED DEPENDENCY ERROR] $firstMissing"
                            emit(LaunchEvent.LogReceived(missingMsg, isError = true))
                            emit(LaunchEvent.StateChanged(ProcessState.Failed(LaunchError.ExecutionFailed(missingMsg))))
                            return@channelFlow
                        }
                    }
                } catch (e: Throwable) {
                    println("[LaunchEngine] Mod launch validation notice: ${e.message}")
                }
            } else {
                updateProgress(
                    stage = "VALIDATING MODS",
                    status = "No mods to validate",
                    rawProgress = 0.93f
                )
            }

            updateProgress(
                stage = "BUILDING LAUNCH COMMAND",
                status = "Building optimized launch command...",
                rawProgress = 0.96f
            )
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

            // Enforce Windows DirectX GPU Preference for high-performance dedicated graphics (cached in memory)
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

            val commandReadyTime = System.currentTimeMillis()
            println("[PerformanceTiming] COMMAND_READY: $commandReadyTime (+${commandReadyTime - filesReadyTime}ms)")
            emit(LaunchEvent.LogReceived("[PerformanceTiming] COMMAND_READY: $commandReadyTime (+${commandReadyTime - filesReadyTime}ms)", isError = false))

            emit(LaunchEvent.LogReceived("==================== EZZ LAUNCHER PROFILE ====================", isError = false))
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
            emit(LaunchEvent.LogReceived("===============================================================", isError = false))

            // Everything required for process spawn is completely ready.
            realProgressReadyTime = System.currentTimeMillis()
            println("[PerformanceTiming] REAL_PROGRESS_READY: $realProgressReadyTime (+${realProgressReadyTime - playClickTime}ms from click)")
            emit(LaunchEvent.LogReceived("[PerformanceTiming] REAL_PROGRESS_READY: $realProgressReadyTime (+${realProgressReadyTime - playClickTime}ms from click)", isError = false))

            updateProgress(
                stage = "STARTING MINECRAFT",
                status = "Starting Minecraft...",
                rawProgress = 1.0f
            )

            // Await display progress smoothly finishing (e.g. 98 -> 99 -> 100)
            if (displayProgress < 1.0f && !skipDisplayInterpolation) {
                isDisplayComplete.await()
            }
            displayProgress = 1.0f
            displayInterpolatorJob?.cancel()

            // Emit authoritative final 100% progress state
            emit(LaunchEvent.ProgressUpdate(
                operationId = operationId,
                stage = "STARTING MINECRAFT",
                status = "Starting Minecraft...",
                progress = 1.0f,
                displayProgress = 1.0f,
                completedWork = 1L,
                totalWork = 1L
            ))
            emit(LaunchEvent.StateChanged(ProcessState.Preparing("Starting Minecraft...", 1.0f)))

            displayProgress100Time = System.currentTimeMillis()
            println("[PerformanceTiming] DISPLAY_PROGRESS_100: $displayProgress100Time (+${displayProgress100Time - playClickTime}ms from click)")
            emit(LaunchEvent.LogReceived("[PerformanceTiming] DISPLAY_PROGRESS_100: $displayProgress100Time (+${displayProgress100Time - playClickTime}ms from click)", isError = false))

            processSpawnStartTime = System.currentTimeMillis()
            val d100ToSpawnGap = processSpawnStartTime - displayProgress100Time
            println("[PerformanceTiming] PROCESS_SPAWN_START: $processSpawnStartTime (Gap from 100%: ${d100ToSpawnGap}ms)")
            emit(LaunchEvent.LogReceived("[PerformanceTiming] PROCESS_SPAWN_START: $processSpawnStartTime (Gap from 100%: ${d100ToSpawnGap}ms)", isError = false))

            processLauncher.launch(
                command = launchCommand,
                workingDirectory = workingDir,
                environment = gpuEnvironment,
                processPriority = instance.processPriority
            ).collect { event ->
                when (event) {
                    is ProcessEvent.Started -> {
                        runningPid = event.pid
                        processSpawnedTime = System.currentTimeMillis()
                        val spawnDuration = processSpawnedTime - processSpawnStartTime
                        val totalPlayToSpawn = processSpawnedTime - playClickTime

                        println("[PerformanceTiming] PROCESS_SPAWNED (PID: ${event.pid}): $processSpawnedTime")
                        println("[PerformanceTiming] DISPLAY_PROGRESS_100 -> PROCESS_SPAWN_START: ${d100ToSpawnGap}ms")
                        println("[PerformanceTiming] PROCESS_SPAWN_START -> PROCESS_SPAWNED: ${spawnDuration}ms")
                        println("[PerformanceTiming] TOTAL PLAY_CLICK -> PROCESS_SPAWNED: ${totalPlayToSpawn}ms")

                        emit(LaunchEvent.LogReceived("[PerformanceTiming] PROCESS_SPAWNED (PID: ${event.pid}): $processSpawnedTime", isError = false))
                        emit(LaunchEvent.LogReceived("[PerformanceTiming] DISPLAY_PROGRESS_100 -> PROCESS_SPAWN_START: ${d100ToSpawnGap}ms", isError = false))
                        emit(LaunchEvent.LogReceived("[PerformanceTiming] PROCESS_SPAWN_START -> PROCESS_SPAWNED: ${spawnDuration}ms", isError = false))
                        emit(LaunchEvent.LogReceived("[PerformanceTiming] TOTAL PLAY_CLICK -> PROCESS_SPAWNED: ${totalPlayToSpawn}ms", isError = false))

                        updateProgress(
                            stage = "MINECRAFT STARTED",
                            status = "Minecraft running successfully (PID: ${event.pid})",
                            rawProgress = 1.0f,
                            completedWork = 1L,
                            totalWork = 1L
                        )
                        emit(LaunchEvent.StateChanged(ProcessState.Running(processId = event.pid, startedAt = processSpawnedTime)))
                        emit(LaunchEvent.LogReceived("=== Process Started (PID: ${event.pid}) ===", isError = false))

                        val resolvedAvatarUrl = when (validAccount.type) {
                            AccountType.MICROSOFT -> {
                                validAccount.avatarUrl?.takeIf { it.startsWith("http", ignoreCase = true) }
                                    ?: "https://minotar.net/helm/${validAccount.uuid.replace("-", "")}/128.png"
                            }
                            AccountType.OFFLINE -> {
                                validAccount.avatarUrl?.takeIf { it.startsWith("http", ignoreCase = true) }
                                    ?: "https://minotar.net/helm/${validAccount.username}/128.png"
                            }
                        }

                        discordRpcService?.setMinecraftPresence(
                            playerUsername = validAccount.username,
                            minecraftVersion = instance.minecraftVersion,
                            instanceName = instance.name,
                            playerUuid = validAccount.uuid,
                            avatarUrl = resolvedAvatarUrl,
                            startedAtMs = processSpawnedTime,
                            processId = event.pid,
                            enabled = currentSettings.enableDiscordRpc
                        )

                        // Asynchronously generate telemetry and diagnostic report in background
                        // Passes event.pid so GpuDetector checks the actual running process!
                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val diagnosticReport = io.ezz.launcher.core.runtime.diagnostics.PerformanceDiagnosticService.generateDiagnosticReport(
                                    instance = instance,
                                    gameDir = workingDir,
                                    javaRuntime = effectiveJavaRuntime,
                                    runningPid = event.pid
                                )
                                emit(LaunchEvent.LogReceived(diagnosticReport.formattedReport, isError = false))
                            } catch (e: Throwable) {
                                println("[LaunchEngine] Notice generating background diagnostics: ${e.message}")
                            }
                        }

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
                        discordRpcService?.onMinecraftExited(processId = runningPid)
                        val playTimeSeconds = (System.currentTimeMillis() - processSpawnStartTime) / 1000L

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
                        discordRpcService?.onMinecraftExited(processId = runningPid)
                        emit(LaunchEvent.LogReceived("=== Process Execution Error: ${event.message} ===", isError = true))
                        emit(LaunchEvent.StateChanged(ProcessState.Failed(LaunchError.ExecutionFailed(event.message, event.cause))))
                    }
                }
            }

        } catch (e: Exception) {
            displayInterpolatorJob?.cancel()
            discordRpcService?.onMinecraftExited(processId = runningPid)
            if (e is kotlinx.coroutines.CancellationException) {
                emit(LaunchEvent.LogReceived("=== Launch cancelled by user ===", isError = false))
                throw e
            } else {
                emit(LaunchEvent.StateChanged(ProcessState.Failed(LaunchError.ExecutionFailed(e.message ?: "Launch failed", e))))
            }
        } finally {
            displayInterpolatorJob?.cancel()
        }
    }.flowOn(Dispatchers.IO)

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
