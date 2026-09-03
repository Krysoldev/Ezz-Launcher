package io.ezz.launcher.core.minecraft.mods

import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.model.instance.ModMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PerformanceModAdvisorTest {

    @Test
    fun testDetectInstalledPerformanceModsOnFabric() {
        val instance = Instance(
            id = "inst-fabric",
            name = "Fabric Optimized",
            minecraftVersion = "1.21.1",
            loaderType = LoaderType.FABRIC
        )

        val installedMods = listOf(
            ModMetadata(id = "sodium", instanceId = "inst-fabric", name = "Sodium", version = "0.5.11", fileName = "sodium-fabric-0.5.11.jar", enabled = true),
            ModMetadata(id = "ferritecore", instanceId = "inst-fabric", name = "FerriteCore", version = "6.0.1", fileName = "ferritecore-6.0.1-fabric.jar", enabled = true),
            ModMetadata(id = "appleskin", instanceId = "inst-fabric", name = "AppleSkin", version = "2.5.1", fileName = "appleskin-fabric-2.5.1.jar", enabled = true)
        )

        val recommendations = PerformanceModAdvisor.evaluatePerformanceMods(instance, installedMods)

        val sodiumRec = recommendations.first { it.id == "sodium" }
        assertTrue(sodiumRec.isInstalled, "Sodium should be detected as installed")
        assertTrue(sodiumRec.isCompatible, "Sodium is compatible with Fabric")

        val ferriteRec = recommendations.first { it.id == "ferritecore" }
        assertTrue(ferriteRec.isInstalled, "FerriteCore should be detected as installed")

        val lithiumRec = recommendations.first { it.id == "lithium" }
        assertFalse(lithiumRec.isInstalled, "Lithium is not installed")
        assertTrue(lithiumRec.isCompatible, "Lithium should be recommended for Fabric")
    }

    @Test
    fun testVanillaInstanceShowsFabricModsAsIncompatible() {
        val instance = Instance(
            id = "inst-vanilla",
            name = "Pure Vanilla",
            minecraftVersion = "1.21.1",
            loaderType = LoaderType.VANILLA
        )

        val recommendations = PerformanceModAdvisor.evaluatePerformanceMods(instance, emptyList())
        for (rec in recommendations) {
            assertFalse(rec.isCompatible, "${rec.name} should not be compatible with Vanilla loader")
        }
    }
}
