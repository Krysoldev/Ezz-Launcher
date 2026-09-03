package io.ezz.launcher.core.model.instance

import kotlinx.serialization.Serializable

@Serializable
enum class LoaderType {
    VANILLA,
    FABRIC,
    OPTIFINE
}

@Serializable
enum class PerformanceProfile(val displayName: String, val description: String) {
    DEFAULT("Default", "Preserves your custom Minecraft settings without automatic modifications."),
    BALANCED("Balanced", "Smooth 60-144 FPS with high visual quality, 12 chunk render distance, and rich particles."),
    PERFORMANCE("Performance", "Optimized 144-240+ FPS with 10 chunk render distance, fast graphics, and minimal stutter."),
    MAX_FPS("Max FPS", "High framerate (250-400+ FPS) with uncapped FPS, minimal particles, and fast lighting."),
    EXTREME_FPS("Extreme FPS", "Maximum real-time FPS targeting 500+ FPS. Visual quality reduced for maximum throughput.")
}

@Serializable
enum class GpuPreference(val displayName: String) {
    AUTO("Auto (OS Default)"),
    HIGH_PERFORMANCE("High Performance (Dedicated GPU)"),
    POWER_SAVING("Power Saving (Integrated GPU)")
}

@Serializable
enum class ProcessPriority(val displayName: String) {
    NORMAL("Normal"),
    ABOVE_NORMAL("Above Normal")
}

@Serializable
enum class GarbageCollectorType(val displayName: String, val jvmFlag: String) {
    AUTO("Auto (G1GC Balanced)", "-XX:+UseG1GC"),
    G1GC("G1GC (Low Latency / Balanced)", "-XX:+UseG1GC"),
    ZGC("ZGC (Ultra Low Latency, Java 17+)", "-XX:+UseZGC"),
    SHENANDOAH("Shenandoah GC", "-XX:+UseShenandoahGC")
}

@Serializable
enum class FpsMode(val displayName: String, val description: String) {
    DEFAULT("Default", "Preserves Minecraft's existing in-game FPS and VSync settings."),
    UNLIMITED("Unlimited", "Unlocks unconstrained FPS (Max FPS = Unlimited, VSync = OFF)."),
    DISPLAY_LIMIT("Display Limit", "Limits Max FPS to your monitor's native refresh rate."),
    CUSTOM("Custom Limit", "Limits Max FPS to a custom user-defined framerate limit.")
}

/**
 * Snapshot of known-working performance & runtime settings for safe rollback.
 */
@Serializable
data class InstancePerformanceSnapshot(
    val timestamp: Long,
    val javaPath: String?,
    val minMemoryMb: Int,
    val maxMemoryMb: Int,
    val customJvmArgs: List<String>,
    val windowWidth: Int,
    val windowHeight: Int,
    val performanceProfile: PerformanceProfile,
    val gpuPreference: GpuPreference,
    val processPriority: ProcessPriority,
    val gcType: GarbageCollectorType,
    val fpsMode: FpsMode = FpsMode.UNLIMITED,
    val customFpsLimit: Int = 260,
    val ezzSkinEnabled: Boolean = true
)

@Serializable
data class Instance(
    val id: String,
    val name: String,
    val minecraftVersion: String,
    val loaderType: LoaderType = LoaderType.VANILLA,
    val loaderVersion: String? = null,
    val iconId: String = "grass_block",
    val javaPath: String? = null,
    val minMemoryMb: Int = 1024,
    val maxMemoryMb: Int = 4096,
    val customJvmArgs: List<String> = emptyList(),
    val windowWidth: Int = 1280,
    val windowHeight: Int = 720,
    val createdAt: Long = 0L,
    val lastPlayedAt: Long? = null,
    val totalPlayTimeSeconds: Long = 0L,
    val customIconPath: String? = null,
    val performanceProfile: PerformanceProfile = PerformanceProfile.DEFAULT,
    val gpuPreference: GpuPreference = GpuPreference.AUTO,
    val processPriority: ProcessPriority = ProcessPriority.NORMAL,
    val gcType: GarbageCollectorType = GarbageCollectorType.AUTO,
    val enableDiagnostics: Boolean = false,
    val fpsMode: FpsMode = FpsMode.UNLIMITED,
    val customFpsLimit: Int = 260,
    val ezzSkinEnabled: Boolean = true,
    val knownGoodSnapshot: InstancePerformanceSnapshot? = null,
    val lastLaunchPreparationMs: Long? = null
) {
    /**
     * Creates a snapshot of current performance & runtime parameters.
     */
    fun createPerformanceSnapshot(timestamp: Long = System.currentTimeMillis()): InstancePerformanceSnapshot {
        return InstancePerformanceSnapshot(
            timestamp = timestamp,
            javaPath = javaPath,
            minMemoryMb = minMemoryMb,
            maxMemoryMb = maxMemoryMb,
            customJvmArgs = customJvmArgs,
            windowWidth = windowWidth,
            windowHeight = windowHeight,
            performanceProfile = performanceProfile,
            gpuPreference = gpuPreference,
            processPriority = processPriority,
            gcType = gcType,
            fpsMode = fpsMode,
            customFpsLimit = customFpsLimit,
            ezzSkinEnabled = ezzSkinEnabled
        )
    }

    /**
     * Restores performance parameters from a known good snapshot.
     */
    fun rollbackToSnapshot(snapshot: InstancePerformanceSnapshot): Instance {
        return copy(
            javaPath = snapshot.javaPath,
            minMemoryMb = snapshot.minMemoryMb,
            maxMemoryMb = snapshot.maxMemoryMb,
            customJvmArgs = snapshot.customJvmArgs,
            windowWidth = snapshot.windowWidth,
            windowHeight = snapshot.windowHeight,
            performanceProfile = snapshot.performanceProfile,
            gpuPreference = snapshot.gpuPreference,
            processPriority = snapshot.processPriority,
            gcType = snapshot.gcType,
            fpsMode = snapshot.fpsMode,
            customFpsLimit = snapshot.customFpsLimit,
            ezzSkinEnabled = snapshot.ezzSkinEnabled
        )
    }
}

