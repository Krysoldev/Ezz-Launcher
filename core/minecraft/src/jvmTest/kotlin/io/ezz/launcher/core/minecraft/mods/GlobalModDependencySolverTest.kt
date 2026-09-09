package io.ezz.launcher.core.minecraft.mods

import io.ezz.launcher.core.model.instance.LocalMod
import io.ezz.launcher.core.model.instance.ResolvedEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GlobalModDependencySolverTest {

    private val testEnv = ResolvedEnvironment(
        instanceId = "test-instance-121",
        minecraftVersion = "1.21.1",
        loader = "fabric",
        loaderVersion = "0.16.0",
        minecraftDirectoryPath = System.getProperty("java.io.tmpdir")
    )

    private fun createInstalledMod(
        id: String,
        name: String,
        version: String,
        fileName: String = "$id-$version.jar",
        breaks: Map<String, String> = emptyMap(),
        conflicts: Map<String, String> = emptyMap(),
        dependencies: Map<String, String> = emptyMap()
    ): LocalMod {
        return LocalMod(
            id = id,
            name = name,
            version = version,
            fileName = fileName,
            enabled = true,
            loader = "fabric",
            breaks = breaks,
            conflicts = conflicts,
            dependencies = dependencies
        )
    }

    private fun createCandidate(
        id: String,
        versionNumber: String,
        mcVersions: List<String> = listOf("1.21.1"),
        loaders: List<String> = listOf("fabric"),
        versionType: String = "release",
        dependencies: List<CandidateDependency> = emptyList()
    ): CandidateVersion {
        return CandidateVersion(
            id = id,
            versionNumber = versionNumber,
            gameVersions = mcVersions,
            loaders = loaders,
            versionType = versionType,
            files = listOf(
                CandidateFile(
                    filename = "$id-$versionNumber.jar",
                    url = "https://cdn.example.com/$id-$versionNumber.jar",
                    sizeBytes = 1024L,
                    isPrimary = true
                )
            ),
            dependencies = dependencies
        )
    }

    @Test
    fun `test environment enforces non-blank validation`() {
        assertFailsWith<IllegalArgumentException> {
            ResolvedEnvironment(
                instanceId = "inst",
                minecraftVersion = "",
                loader = "fabric",
                minecraftDirectoryPath = "C:/test"
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ResolvedEnvironment(
                instanceId = "inst",
                minecraftVersion = "1.21.1",
                loader = "   ",
                minecraftDirectoryPath = "C:/test"
            )
        }
    }

    @Test
    fun `test existing mods are unconditionally preserved with KEEP action`() {
        val installedSodium = createInstalledMod("sodium", "Sodium", "0.5.8")
        val installedIris = createInstalledMod("iris", "Iris", "1.7.0")
        val installedFabApi = createInstalledMod("fabric-api", "Fabric API", "0.98.0")
        val installedMods = listOf(installedSodium, installedIris, installedFabApi)

        val lithiumCandidates = listOf(
            createCandidate("lithium-v1", "0.12.0", versionType = "release")
        )

        val result = GlobalModDependencySolver.solve(
            environment = testEnv,
            installedMods = installedMods,
            targetModId = "lithium",
            targetModName = "Lithium",
            targetModSlug = "lithium",
            candidates = lithiumCandidates
        )

        assertTrue(result is SolverResult.Success, "Solver should succeed")
        val plan = (result as SolverResult.Success).plan

        // Verify KEEP items: Sodium, Iris, Fabric API MUST all be KEEP
        val keepItems = plan.itemsToKeep
        val keepModIds = keepItems.map { it.modId }.toSet()
        assertTrue(keepModIds.contains("sodium"))
        assertTrue(keepModIds.contains("iris"))
        assertTrue(keepModIds.contains("fabric-api"))

        // Verify no REMOVE items
        assertEquals(0, plan.itemsToRemove.size)
        assertEquals(0, plan.itemsToReplace.size)

        // Verify target mod is INSTALL
        val installItems = plan.itemsToInstall
        assertEquals(1, installItems.size)
        assertEquals("lithium", installItems.first().modId)
        assertEquals("0.12.0", installItems.first().targetVersion)
    }

    @Test
    fun `test bidirectional conflict resolution selects alternative compatible candidate`() {
        // Installed mod declares break against candidate v2, but allows candidate v1
        val installedMod = createInstalledMod(
            id = "continuity",
            name = "Continuity",
            version = "3.0.0",
            breaks = mapOf("custom-lib" to ">=2.0.0") // Breaks candidate v2.0.0+
        )

        val candidateV2 = createCandidate("cl-2", "2.0.0", versionType = "release")
        val candidateV1 = createCandidate("cl-1", "1.5.0", versionType = "release")

        val result = GlobalModDependencySolver.solve(
            environment = testEnv,
            installedMods = listOf(installedMod),
            targetModId = "custom-lib",
            targetModName = "Custom Lib",
            targetModSlug = "custom-lib",
            candidates = listOf(candidateV2, candidateV1)
        )

        assertTrue(result is SolverResult.Success, "Solver should succeed by selecting non-conflicting candidate")
        val plan = (result as SolverResult.Success).plan
        // Solver must skip candidateV2 due to bidirectional break and select candidateV1
        assertEquals("1.5.0", plan.selectedVersionNumber)
        assertEquals(0, plan.itemsToRemove.size)
    }

    @Test
    fun `test multi-level dependency tree resolution`() {
        val depC = createCandidate("mod-c", "1.0.0")
        val depB = createCandidate(
            id = "mod-b",
            versionNumber = "1.0.0",
            dependencies = listOf(CandidateDependency("mod-c", dependencyType = "required"))
        )
        val modA = createCandidate(
            id = "mod-a",
            versionNumber = "1.0.0",
            dependencies = listOf(CandidateDependency("mod-b", dependencyType = "required"))
        )

        val resolver: (CandidateDependency) -> CandidateVersion? = { dep ->
            when (dep.idOrSlug) {
                "mod-b" -> depB
                "mod-c" -> depC
                else -> null
            }
        }

        val result = GlobalModDependencySolver.solve(
            environment = testEnv,
            installedMods = emptyList(),
            targetModId = "mod-a",
            targetModName = "Mod A",
            targetModSlug = "mod-a",
            candidates = listOf(modA),
            dependencyResolver = resolver
        )

        assertTrue(result is SolverResult.Success, "Dependency solver should solve tree successfully")
        val plan = (result as SolverResult.Success).plan
        val installedModIds = plan.itemsToInstall.map { it.modId }.toSet()
        assertTrue(installedModIds.contains("mod-a"))
        assertTrue(installedModIds.contains("mod-b"))
        assertTrue(installedModIds.contains("mod-c"))
        assertEquals(3, plan.itemsToInstall.size)
    }

    @Test
    fun `test existing dependency satisfied does not re-install dependency`() {
        val installedFabApi = createInstalledMod("fabric-api", "Fabric API", "0.98.0")
        val modWithDep = createCandidate(
            id = "my-mod",
            versionNumber = "1.0.0",
            dependencies = listOf(CandidateDependency("fabric-api", dependencyType = "required", versionConstraint = ">=0.90.0"))
        )

        val result = GlobalModDependencySolver.solve(
            environment = testEnv,
            installedMods = listOf(installedFabApi),
            targetModId = "my-mod",
            targetModName = "My Mod",
            targetModSlug = "my-mod",
            candidates = listOf(modWithDep)
        )

        assertTrue(result is SolverResult.Success, "Solver should succeed")
        val plan = (result as SolverResult.Success).plan
        // Fabric API should remain KEEP, only my-mod should be INSTALL
        val installIds = plan.itemsToInstall.map { it.modId }
        assertEquals(listOf("my-mod"), installIds)
        assertTrue(plan.itemsToKeep.any { it.modId == "fabric-api" })
    }

    @Test
    fun `test iris sodium sodium-extra continuity co-existence`() {
        // Reproduce exact scenario: Minecraft 1.21.1 Fabric
        val installedSodium = createInstalledMod("sodium", "Sodium", "0.5.8")
        val installedIris = createInstalledMod("iris", "Iris", "1.7.0", dependencies = mapOf("sodium" to ">=0.5.0"))
        val installedSodiumExtra = createInstalledMod("sodium-extra", "Sodium Extra", "0.5.4", dependencies = mapOf("sodium" to "*"))

        val continuityCandidate = createCandidate(
            id = "continuity",
            versionNumber = "3.0.0",
            dependencies = listOf(CandidateDependency("fabric-api", dependencyType = "required"))
        )

        val fabApiCandidate = createCandidate("fabric-api", "0.100.0")

        val result = GlobalModDependencySolver.solve(
            environment = testEnv,
            installedMods = listOf(installedSodium, installedIris, installedSodiumExtra),
            targetModId = "continuity",
            targetModName = "Continuity",
            targetModSlug = "continuity",
            candidates = listOf(continuityCandidate),
            dependencyResolver = { dep -> if (dep.idOrSlug == "fabric-api") fabApiCandidate else null }
        )

        assertTrue(result is SolverResult.Success, "Continuity installation should succeed")
        val plan = (result as SolverResult.Success).plan

        // Existing mods Sodium, Iris, Sodium Extra MUST be kept
        val keepIds = plan.itemsToKeep.map { it.modId }.toSet()
        assertTrue(keepIds.contains("sodium"))
        assertTrue(keepIds.contains("iris"))
        assertTrue(keepIds.contains("sodium-extra"))

        // Target and required dependencies installed
        val installIds = plan.itemsToInstall.map { it.modId }.toSet()
        assertTrue(installIds.contains("continuity"))
        assertTrue(installIds.contains("fabric-api"))

        // Absolute zero unexpected removals
        assertEquals(0, plan.itemsToRemove.size)
    }
}
