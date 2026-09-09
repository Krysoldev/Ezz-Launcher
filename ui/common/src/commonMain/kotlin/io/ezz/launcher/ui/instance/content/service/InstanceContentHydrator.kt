package io.ezz.launcher.ui.instance.content.service

import io.ezz.launcher.core.minecraft.mods.ModCompatibilityResolver
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.storage.instance.LocalInstanceManager
import io.ezz.launcher.ui.instance.content.model.ContentLoadState
import io.ezz.launcher.ui.instance.content.model.InstanceContentState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Authoritative, centralized content hydration coordinator for Minecraft instances.
 * Eliminates race conditions between startup, instance opening, and manual refresh.
 *
 * Enforces:
 * 1. Single Source of Truth for both Initial Boot Hydration and Manual Refresh.
 * 2. Epoch-based concurrency guarding: rapid instance switching cleanly discards stale results.
 * 3. Distinct Three-State UI progression: Loading -> Empty -> Loaded with Content.
 */
class InstanceContentHydrator(
    private val instanceManager: LocalInstanceManager,
    private val getInstanceDir: (String) -> File,
    private val getInstance: (String) -> Instance?,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher? = null,
    private val onContentUpdated: ((InstanceContentState) -> Unit)? = null
) {
    private val _contentState = MutableStateFlow<InstanceContentState?>(null)
    val contentState: StateFlow<InstanceContentState?> = _contentState.asStateFlow()

    private val cache = ConcurrentHashMap<String, InstanceContentState>()
    private val currentEpoch = AtomicLong(0L)
    private var activeJob: Job? = null

    /**
     * Authoritatively loads or refreshes all content categories for an instance.
     *
     * @param instanceId Target instance identifier
     * @param forceRefresh If true, bypasses cache and re-scans filesystem directly.
     */
    fun hydrateInstance(instanceId: String, forceRefresh: Boolean = false): Job {
        val epoch = currentEpoch.incrementAndGet()
        activeJob?.cancel()

        println("[InstanceContentHydrator] Starting hydration for instance $instanceId (forceRefresh=$forceRefresh, epoch=$epoch)")

        val cached = cache[instanceId]
        val now = System.currentTimeMillis()

        // If we have valid cached content and this is not a forced refresh, render immediately
        if (cached != null && !forceRefresh) {
            println("[InstanceContentHydrator] Serving valid cached state for $instanceId (mods=${cached.mods.size}, rp=${cached.resourcePacks.size}, shaders=${cached.shaders.size})")
            _contentState.value = cached
            onContentUpdated?.invoke(cached)
            // If cached less than 1.5 seconds ago, avoid redundant immediate rescan
            if (now - cached.lastHydratedAt < 1500L) {
                return Job().apply { complete() }
            }
        } else {
            // Distinct Loading state so UI shows skeletons/spinners instead of "Empty"
            val initial = InstanceContentState.initial(instanceId)
            _contentState.value = initial
            onContentUpdated?.invoke(initial)
        }

        val job = scope.launch(dispatcher) {
            try {
                if (currentEpoch.get() != epoch) return@launch

                // 1. Resolve & guarantee instance filesystem paths
                val baseDir = getInstanceDir(instanceId)
                val mcDir = File(baseDir, ".minecraft")
                if (!mcDir.exists()) mcDir.mkdirs()

                // Guarantee standard content subdirectories exist
                val modsDir = File(mcDir, "mods")
                val rpDir = File(mcDir, "resourcepacks")
                val spDir = File(mcDir, "shaderpacks")
                val savesDir = File(mcDir, "saves")
                val ssDir = File(mcDir, "screenshots")
                val logsDir = File(mcDir, "logs")

                if (!modsDir.exists()) modsDir.mkdirs()
                if (!rpDir.exists()) rpDir.mkdirs()
                if (!spDir.exists()) spDir.mkdirs()
                if (!savesDir.exists()) savesDir.mkdirs()
                if (!ssDir.exists()) ssDir.mkdirs()
                if (!logsDir.exists()) logsDir.mkdirs()

                if (currentEpoch.get() != epoch) return@launch

                // 2. Parallel asynchronous scanning of all content categories
                val statsDeferred = async {
                    try {
                        ContentLoadState.Success(instanceManager.getInstanceStatistics(instanceId))
                    } catch (t: Throwable) {
                        ContentLoadState.Error("Failed to calculate statistics", t)
                    }
                }

                val modsDeferred = async {
                    try {
                        ContentLoadState.Success(instanceManager.getMods(instanceId))
                    } catch (t: Throwable) {
                        ContentLoadState.Error("Failed to scan mods", t)
                    }
                }

                val rpDeferred = async {
                    try {
                        ContentLoadState.Success(instanceManager.getResourcePacks(instanceId))
                    } catch (t: Throwable) {
                        ContentLoadState.Error("Failed to scan resource packs", t)
                    }
                }

                val spDeferred = async {
                    try {
                        ContentLoadState.Success(instanceManager.getShaderPacks(instanceId))
                    } catch (t: Throwable) {
                        ContentLoadState.Error("Failed to scan shader packs", t)
                    }
                }

                val worldsDeferred = async {
                    try {
                        ContentLoadState.Success(instanceManager.getWorlds(instanceId))
                    } catch (t: Throwable) {
                        ContentLoadState.Error("Failed to scan worlds", t)
                    }
                }

                val ssDeferred = async {
                    try {
                        ContentLoadState.Success(instanceManager.getScreenshots(instanceId))
                    } catch (t: Throwable) {
                        ContentLoadState.Error("Failed to scan screenshots", t)
                    }
                }

                val logsDeferred = async {
                    try {
                        ContentLoadState.Success(instanceManager.getLogs(instanceId))
                    } catch (t: Throwable) {
                        ContentLoadState.Error("Failed to scan logs", t)
                    }
                }

                val statsState = statsDeferred.await()
                val modsState = modsDeferred.await()
                val rpState = rpDeferred.await()
                val spState = spDeferred.await()
                val worldsState = worldsDeferred.await()
                val ssState = ssDeferred.await()
                val logsState = logsDeferred.await()

                if (currentEpoch.get() != epoch) return@launch

                // 3. Resolve mod compatibility & missing dependencies
                val instance = getInstance(instanceId)
                val installedMods = (modsState as? ContentLoadState.Success)?.data ?: emptyList()
                val (missingDeps, conflicts) = if (instance != null && installedMods.isNotEmpty()) {
                    try {
                        val report = ModCompatibilityResolver.validateLaunchCompatibility(
                            minecraftVersion = instance.minecraftVersion,
                            loader = instance.loaderType.name,
                            installedMods = installedMods
                        )
                        Pair(report.missingDependencies, report.explicitConflicts)
                    } catch (e: Throwable) {
                        Pair(emptyList(), emptyList())
                    }
                } else {
                    Pair(emptyList(), emptyList())
                }

                if (currentEpoch.get() != epoch) return@launch

                val resolvedState = InstanceContentState(
                    instanceId = instanceId,
                    isHydrating = false,
                    modsState = modsState,
                    resourcePacksState = rpState,
                    shadersState = spState,
                    worldsState = worldsState,
                    screenshotsState = ssState,
                    logsState = logsState,
                    statisticsState = statsState,
                    missingDependencies = missingDeps,
                    compatibilityConflicts = conflicts,
                    lastHydratedAt = System.currentTimeMillis()
                )

                // Atomic cache and state update
                cache[instanceId] = resolvedState
                _contentState.value = resolvedState

                println("[InstanceContentHydrator] Hydration completed successfully for $instanceId -> mods: ${resolvedState.mods.size}, rp: ${resolvedState.resourcePacks.size}, shaders: ${resolvedState.shaders.size}, worlds: ${resolvedState.worlds.size}, screenshots: ${resolvedState.screenshots.size}, logs: ${resolvedState.logs.size}")

                dispatchUpdate(resolvedState)
            } catch (c: kotlinx.coroutines.CancellationException) {
                // Expected when user quickly navigates to another instance
            } catch (e: Throwable) {
                if (currentEpoch.get() == epoch) {
                    println("[InstanceContentHydrator] Hydration failed for $instanceId: ${e.message}")
                    val errState = InstanceContentState(
                        instanceId = instanceId,
                        isHydrating = false,
                        modsState = ContentLoadState.Error("Scan failed: ${e.message}", e),
                        resourcePacksState = ContentLoadState.Error("Scan failed: ${e.message}", e),
                        shadersState = ContentLoadState.Error("Scan failed: ${e.message}", e),
                        worldsState = ContentLoadState.Error("Scan failed: ${e.message}", e),
                        screenshotsState = ContentLoadState.Error("Scan failed: ${e.message}", e),
                        logsState = ContentLoadState.Error("Scan failed: ${e.message}", e),
                        statisticsState = ContentLoadState.Error("Scan failed: ${e.message}", e),
                        lastHydratedAt = System.currentTimeMillis()
                    )
                    _contentState.value = errState
                    dispatchUpdate(errState)
                }
            }
        }

        activeJob = job
        return job
    }

    private suspend fun dispatchUpdate(state: InstanceContentState) {
        if (mainDispatcher != null) {
            withContext(mainDispatcher) {
                onContentUpdated?.invoke(state)
            }
        } else {
            onContentUpdated?.invoke(state)
        }
    }

    /**
     * Invalidate cached content for an instance, forcing subsequent requests to re-scan.
     */
    fun invalidate(instanceId: String) {
        cache.remove(instanceId)
    }

    /**
     * Clears all cached content states.
     */
    fun clearAll() {
        cache.clear()
        _contentState.value = null
    }
}
