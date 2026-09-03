package io.ezz.launcher.core.minecraft.mods

import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.model.instance.ModMetadata

data class PerformanceModRecommendation(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val isInstalled: Boolean,
    val isCompatible: Boolean,
    val impact: String
)

object PerformanceModAdvisor {

    private val KNOWN_OPTIMIZATION_MODS = listOf(
        OptimizationModDef(
            id = "sodium",
            name = "Sodium",
            description = "State-of-the-art OpenGL rendering engine replacement with massive FPS boosts.",
            category = "Rendering Engine",
            impact = "Extreme (+150-300% FPS)",
            supportedLoaders = listOf(LoaderType.FABRIC)
        ),
        OptimizationModDef(
            id = "lithium",
            name = "Lithium",
            description = "General-purpose physics, mob AI, and chunk-ticking optimization mod.",
            category = "Game Logic & TPS",
            impact = "High (+40% Server/Singleplayer TPS)",
            supportedLoaders = listOf(LoaderType.FABRIC)
        ),
        OptimizationModDef(
            id = "ferritecore",
            name = "FerriteCore",
            description = "Reduces Minecraft RAM usage by up to 50% through model data deduplication.",
            category = "Memory & GC",
            impact = "High (-30-50% RAM Usage)",
            supportedLoaders = listOf(LoaderType.FABRIC)
        ),
        OptimizationModDef(
            id = "immediatelyfast",
            name = "ImmediatelyFast",
            description = "Optimizes immediate-mode rendering of HUD, GUI, and text for silky-smooth menus.",
            category = "UI & HUD Rendering",
            impact = "Medium (+20-40% UI Frametimes)",
            supportedLoaders = listOf(LoaderType.FABRIC)
        ),
        OptimizationModDef(
            id = "entityculling",
            name = "Entity Culling",
            description = "Skips rendering tiles and entities obscured behind walls and terrain.",
            category = "Culling & Occlusion",
            impact = "High (+30-80% FPS in crowded areas)",
            supportedLoaders = listOf(LoaderType.FABRIC)
        ),
        OptimizationModDef(
            id = "modernfix",
            name = "ModernFix",
            description = "All-in-one performance improvements, memory leak fixes, and launch speedups.",
            category = "General Optimization",
            impact = "High (2x Faster Launch)",
            supportedLoaders = listOf(LoaderType.FABRIC)
        ),
        OptimizationModDef(
            id = "moreculling",
            name = "More Culling",
            description = "High-performance block state culling for leaves, beacons, and chests.",
            category = "Block Culling",
            impact = "Medium (+15-30% FPS in forests)",
            supportedLoaders = listOf(LoaderType.FABRIC)
        ),
        OptimizationModDef(
            id = "krypton",
            name = "Krypton",
            description = "Optimizes the Minecraft networking stack and reduces server packet latency.",
            category = "Networking",
            impact = "Medium (Smoother Multiplayer)",
            supportedLoaders = listOf(LoaderType.FABRIC)
        ),
        OptimizationModDef(
            id = "badoptimizations",
            name = "BadOptimizations",
            description = "Micro-optimizations eliminating redundant Minecraft rendering calls.",
            category = "Micro-benchmarks",
            impact = "Medium (+10-25% FPS)",
            supportedLoaders = listOf(LoaderType.FABRIC)
        )
    )

    fun evaluatePerformanceMods(instance: Instance, installedMods: List<ModMetadata>): List<PerformanceModRecommendation> {
        val installedNormalized = installedMods.map { it.id.lowercase().replace("-", "").replace("_", "") }.toSet()

        return KNOWN_OPTIMIZATION_MODS.map { def ->
            val cleanDefId = def.id.lowercase().replace("-", "").replace("_", "")
            val isInstalled = installedNormalized.contains(cleanDefId)
            val isCompatible = def.supportedLoaders.contains(instance.loaderType)

            PerformanceModRecommendation(
                id = def.id,
                name = def.name,
                description = def.description,
                category = def.category,
                isInstalled = isInstalled,
                isCompatible = isCompatible,
                impact = def.impact
            )
        }
    }

    private data class OptimizationModDef(
        val id: String,
        val name: String,
        val description: String,
        val category: String,
        val impact: String,
        val supportedLoaders: List<LoaderType>
    )
}
