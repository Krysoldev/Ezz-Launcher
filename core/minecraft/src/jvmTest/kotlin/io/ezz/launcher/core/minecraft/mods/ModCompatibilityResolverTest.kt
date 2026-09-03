package io.ezz.launcher.core.minecraft.mods

import io.ezz.launcher.core.model.instance.LocalMod
import io.ezz.launcher.core.model.modrinth.ModrinthDependency
import io.ezz.launcher.core.model.modrinth.ModrinthProjectHit
import io.ezz.launcher.core.model.modrinth.ModrinthVersion
import io.ezz.launcher.core.model.modrinth.ModrinthVersionFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModCompatibilityResolverTest {

    private fun createVersion(
        id: String,
        versionNumber: String,
        gameVersions: List<String> = listOf("1.21.11"),
        loaders: List<String> = listOf("fabric"),
        versionType: String = "release",
        dependencies: List<ModrinthDependency> = emptyList()
    ): ModrinthVersion {
        return ModrinthVersion(
            id = id,
            projectId = "test-project",
            name = "Test $versionNumber",
            versionNumber = versionNumber,
            gameVersions = gameVersions,
            loaders = loaders,
            versionType = versionType,
            dependencies = dependencies,
            files = listOf(
                ModrinthVersionFile(
                    url = "https://cdn.modrinth.com/data/test/$id/test-$versionNumber.jar",
                    filename = "test-$versionNumber.jar",
                    primary = true,
                    size = 1024L
                )
            )
        )
    }

    private val sodiumProject = ModrinthProjectHit(
        projectId = "AANobbMI",
        slug = "sodium",
        title = "Sodium",
        description = "Modern rendering engine for Minecraft",
        author = "CaffeineMC",
        clientSide = "required",
        serverSide = "unsupported",
        projectType = "mod",
        downloads = 10000000,
        follows = 50000
    )

    private val irisProject = ModrinthProjectHit(
        projectId = "YL57xq9U",
        slug = "iris",
        title = "Iris Shaders",
        description = "A modern shaders mod for Minecraft",
        author = "IrisMC",
        clientSide = "required",
        serverSide = "unsupported",
        projectType = "mod",
        downloads = 8000000,
        follows = 40000
    )

    private val sodiumExtraProject = ModrinthProjectHit(
        projectId = "PtjYWJkn",
        slug = "sodium-extra",
        title = "Sodium Extra",
        description = "Features that shouldn't be in Sodium",
        author = "FlashyReese",
        clientSide = "required",
        serverSide = "unsupported",
        projectType = "mod"
    )

    private val genericProject = ModrinthProjectHit(
        projectId = "generic-mod-id",
        slug = "generic-mod",
        title = "Generic Mod",
        description = "A standard mod",
        author = "TestAuthor",
        clientSide = "optional",
        serverSide = "optional",
        projectType = "mod"
    )

    @Test
    fun `test User Case 1 - Installed Sodium 0_8_14 selects compatible Iris Shaders 1_10_8`() {
        val installedSodium = listOf(
            LocalMod(
                id = "sodium",
                name = "Sodium",
                version = "0.8.14+mc1.21.11",
                fileName = "sodium-fabric-0.8.14+mc1.21.11.jar",
                enabled = true,
                breaks = mapOf("iris" to "<=1.10.7")
            )
        )

        val irisCandidates = listOf(
            createVersion("iris_1_10_8", "1.10.8+mc1.21.11", gameVersions = listOf("1.21.11"), loaders = listOf("fabric")),
            createVersion("iris_1_10_7", "1.10.7+mc1.21.11", gameVersions = listOf("1.21.11"), loaders = listOf("fabric")),
            createVersion("iris_1_10_5", "1.10.5+mc1.21.11", gameVersions = listOf("1.21.11"), loaders = listOf("fabric"))
        )

        val result = ModCompatibilityResolver.resolve(
            minecraftVersion = "1.21.11",
            loader = "fabric",
            installedMods = installedSodium,
            project = irisProject,
            candidateVersions = irisCandidates
        )

        assertTrue(result.hasCompatibleVersion, "Iris Shaders 1.10.8 MUST be compatible when Sodium 0.8.14 is installed")
        assertTrue(result.isLatestCompatible)
        assertEquals("iris_1_10_8", result.recommendedVersion?.id)
        assertEquals("1.10.8+mc1.21.11", result.recommendedVersion?.versionNumber)
        assertNull(result.coUpgradeOption, "Co-upgrade recommendations must be disabled")
    }

    @Test
    fun `test User Case 2 - Reverse Direction - Installed Iris 1_10_7 backtracks to Sodium 0_8_13`() {
        val installedIris = listOf(
            LocalMod(
                id = "iris",
                name = "Iris Shaders",
                version = "1.10.7+mc1.21.11",
                fileName = "iris-fabric-1.10.7+mc1.21.11.jar",
                enabled = true,
                dependencies = mapOf("sodium" to ">=0.8.13 <0.8.14")
            )
        )

        val sodiumCandidates = listOf(
            createVersion("s_0_8_14", "0.8.14+mc1.21.11", gameVersions = listOf("1.21.11"), loaders = listOf("fabric")),
            createVersion("s_0_8_13", "0.8.13+mc1.21.11", gameVersions = listOf("1.21.11"), loaders = listOf("fabric"))
        )

        val result = ModCompatibilityResolver.resolve(
            minecraftVersion = "1.21.11",
            loader = "fabric",
            installedMods = installedIris,
            project = sodiumProject,
            candidateVersions = sodiumCandidates
        )

        assertTrue(result.hasCompatibleVersion, "Sodium 0.8.13 MUST be selected when Iris 1.10.7 requires <0.8.14")
        assertEquals("s_0_8_13", result.recommendedVersion?.id)
        assertEquals("0.8.13+mc1.21.11", result.recommendedVersion?.versionNumber)
    }

    @Test
    fun `test resolve picks latest compatible version when no conflicts exist`() {
        val candidates = listOf(
            createVersion("v3", "3.0.0"),
            createVersion("v2", "2.0.0"),
            createVersion("v1", "1.0.0")
        )

        val result = ModCompatibilityResolver.resolve(
            minecraftVersion = "1.21.11",
            loader = "fabric",
            installedMods = emptyList(),
            project = genericProject,
            candidateVersions = candidates
        )

        assertTrue(result.hasCompatibleVersion)
        assertTrue(result.isLatestCompatible)
        assertEquals("v3", result.recommendedVersion?.id)
        assertEquals("3.0.0", result.recommendedVersion?.versionNumber)
    }

    @Test
    fun `test resolve filters out candidates with mismatched loader or mc version`() {
        val candidates = listOf(
            createVersion("v_wrong_mc", "3.0.0", gameVersions = listOf("1.20.4")),
            createVersion("v_wrong_loader", "2.5.0", loaders = listOf("forge")),
            createVersion("v_ok", "2.0.0", gameVersions = listOf("1.21.11"), loaders = listOf("fabric"))
        )

        val result = ModCompatibilityResolver.resolve(
            minecraftVersion = "1.21.11",
            loader = "fabric",
            installedMods = emptyList(),
            project = genericProject,
            candidateVersions = candidates
        )

        assertTrue(result.hasCompatibleVersion)
        assertEquals("v_ok", result.recommendedVersion?.id)
        assertFalse(result.candidateEvaluations["v_wrong_mc"]!!.isCompatible)
        assertFalse(result.candidateEvaluations["v_wrong_loader"]!!.isCompatible)
        assertTrue(result.candidateEvaluations["v_ok"]!!.isCompatible)
    }

    @Test
    fun `test Sodium Extra is NOT falsely matched against Iris Sodium dependency`() {
        val installedMods = listOf(
            LocalMod(
                id = "iris",
                name = "Iris Shaders",
                version = "1.10.7+mc1.21.11",
                fileName = "iris-1.10.7+mc1.21.11.jar",
                enabled = true,
                dependencies = mapOf("sodium" to ">=0.6.0")
            )
        )

        val sodiumExtraCandidates = listOf(
            createVersion("se_0_9_3", "mc1.21.11-0.9.3+fabric", gameVersions = listOf("1.21.11"), loaders = listOf("fabric")),
            createVersion("se_0_9_2", "mc1.21.11-0.9.2+fabric", gameVersions = listOf("1.21.11"), loaders = listOf("fabric"))
        )

        val result = ModCompatibilityResolver.resolve(
            minecraftVersion = "1.21.11",
            loader = "fabric",
            installedMods = installedMods,
            project = sodiumExtraProject,
            candidateVersions = sodiumExtraCandidates
        )

        assertTrue(result.hasCompatibleVersion, "Sodium Extra must be compatible with Iris installed")
        assertTrue(result.isLatestCompatible)
        assertEquals("se_0_9_3", result.recommendedVersion?.id)
        assertNull(result.primaryConflict)
    }

    @Test
    fun `test Explicit incompatible dependency blocks installation with clear reason`() {
        val installedOptifine = listOf(
            LocalMod(
                id = "optifine",
                name = "OptiFine",
                version = "HD_U_I7",
                fileName = "OptiFine_HD_U_I7.jar",
                enabled = true
            )
        )

        val sodiumCandidates = listOf(
            createVersion(
                id = "s_0_8_14",
                versionNumber = "0.8.14",
                dependencies = listOf(
                    ModrinthDependency(
                        projectId = "optifine",
                        dependencyType = "incompatible"
                    )
                )
            )
        )

        val result = ModCompatibilityResolver.resolve(
            minecraftVersion = "1.21.11",
            loader = "fabric",
            installedMods = installedOptifine,
            project = sodiumProject,
            candidateVersions = sodiumCandidates
        )

        assertFalse(result.hasCompatibleVersion)
        assertNull(result.recommendedVersion)
        assertNotNull(result.primaryConflict)
        assertTrue(result.primaryConflict!!.reason.contains("OptiFine"))
    }

    @Test
    fun `test validateLaunchCompatibility blocks when Sodium 0_8_14 breaks on Iris 1_10_7`() {
        val mods = listOf(
            LocalMod(
                id = "iris",
                name = "Iris",
                version = "1.10.7+mc1.21.11",
                fileName = "iris-fabric-1.10.7+mc1.21.11.jar",
                enabled = true
            ),
            LocalMod(
                id = "sodium",
                name = "Sodium",
                version = "0.8.14+mc1.21.11",
                fileName = "sodium-fabric-0.8.14+mc1.21.11.jar",
                enabled = true,
                breaks = mapOf("iris" to "<=1.10.7")
            )
        )

        val report = ModCompatibilityResolver.validateLaunchCompatibility(
            minecraftVersion = "1.21.11",
            loader = "Fabric",
            installedMods = mods
        )

        assertFalse(report.isReadyToLaunch, "Launch must be blocked because Sodium 0.8.14 breaks on Iris <=1.10.7")
        assertEquals(1, report.explicitConflicts.size)
        assertTrue(report.explicitConflicts.first().reason.contains("breaks on 'Iris'"))
    }

    @Test
    fun `test validateLaunchCompatibility allows launch with Iris 1_10_7 and compatible Sodium 0_8_13`() {
        val mods = listOf(
            LocalMod(
                id = "iris",
                name = "Iris",
                version = "1.10.7+mc1.21.11",
                fileName = "iris-fabric-1.10.7+mc1.21.11.jar",
                enabled = true,
                dependencies = mapOf("sodium" to ">=0.8.13 <0.9")
            ),
            LocalMod(
                id = "sodium",
                name = "Sodium",
                version = "0.8.13+mc1.21.11",
                fileName = "sodium-fabric-0.8.13+mc1.21.11.jar",
                enabled = true
            ),
            LocalMod(
                id = "sodium-extra",
                name = "Sodium Extra",
                version = "0.9.3",
                fileName = "sodium-extra-fabric-0.9.3+mc1.21.11.jar",
                enabled = true,
                dependencies = mapOf("sodium" to ">=0.8.13")
            )
        )

        val report = ModCompatibilityResolver.validateLaunchCompatibility(
            minecraftVersion = "1.21.11",
            loader = "Fabric",
            installedMods = mods
        )

        assertTrue(report.isReadyToLaunch, "Launch must be allowed for Iris 1.10.7 and Sodium 0.8.13")
        assertTrue(report.explicitConflicts.isEmpty(), "There should be no explicit hard conflicts")
        assertTrue(report.missingDependencies.isEmpty(), "All required dependencies are present")
        assertTrue(report.formattedReport.contains("READY TO LAUNCH"))
    }

    @Test
    fun `test validateLaunchCompatibility blocks duplicate mod IDs`() {
        val mods = listOf(
            LocalMod(
                id = "sodium",
                name = "Sodium",
                version = "0.8.13+mc1.21.11",
                fileName = "sodium-fabric-0.8.13.jar",
                enabled = true
            ),
            LocalMod(
                id = "sodium",
                name = "Sodium",
                version = "0.8.14+mc1.21.11",
                fileName = "sodium-fabric-0.8.14.jar",
                enabled = true
            )
        )

        val report = ModCompatibilityResolver.validateLaunchCompatibility(
            minecraftVersion = "1.21.11",
            loader = "Fabric",
            installedMods = mods
        )

        assertFalse(report.isReadyToLaunch, "Launch must be blocked when duplicate mod IDs exist")
        assertTrue(report.explicitConflicts.any { it.reason.contains("Duplicate mod detected") })
    }

    @Test
    fun `test resolve backtracks and selects Sodium 0_8_13 when Iris 1_10_7 is installed`() {
        val installed = listOf(
            LocalMod(
                id = "iris",
                name = "Iris",
                version = "1.10.7+mc1.21.11",
                fileName = "iris-fabric-1.10.7+mc1.21.11.jar",
                enabled = true,
                dependencies = mapOf("sodium" to ">=0.8.13 <0.8.14") // requires 0.8.13
            )
        )

        val project = ModrinthProjectHit(
            projectId = "sodium",
            slug = "sodium",
            title = "Sodium",
            description = "Sodium rendering engine"
        )

        val candidate0814 = ModrinthVersion(
            id = "ver-sodium-0.8.14",
            projectId = "sodium",
            name = "Sodium 0.8.14",
            versionNumber = "0.8.14+mc1.21.11",
            gameVersions = listOf("1.21.11"),
            loaders = listOf("fabric"),
            versionType = "release"
        )

        val candidate0813 = ModrinthVersion(
            id = "ver-sodium-0.8.13",
            projectId = "sodium",
            name = "Sodium 0.8.13",
            versionNumber = "0.8.13+mc1.21.11",
            gameVersions = listOf("1.21.11"),
            loaders = listOf("fabric"),
            versionType = "release"
        )

        val result = ModCompatibilityResolver.resolve(
            minecraftVersion = "1.21.11",
            loader = "Fabric",
            installedMods = installed,
            project = project,
            candidateVersions = listOf(candidate0814, candidate0813)
        )

        assertTrue(result.hasCompatibleVersion)
        assertNotNull(result.recommendedVersion)
        assertEquals("0.8.13+mc1.21.11", result.recommendedVersion!!.versionNumber, "Resolver must backtrack and select 0.8.13 because 0.8.14 fails Iris dependency requirement")
    }

    @Test
    fun `test validateLaunchCompatibility blocks explicit mutual incompatibility with source`() {
        val mods = listOf(
            LocalMod(
                id = "sodium",
                name = "Sodium",
                version = "0.8.14",
                fileName = "sodium-0.8.14.jar",
                enabled = true,
                conflicts = mapOf("optifine" to "*")
            ),
            LocalMod(
                id = "optifine",
                name = "OptiFine",
                version = "HD_U_I7",
                fileName = "OptiFine_HD_U_I7.jar",
                enabled = true
            )
        )

        val report = ModCompatibilityResolver.validateLaunchCompatibility(
            minecraftVersion = "1.21.11",
            loader = "Fabric",
            installedMods = mods
        )

        assertFalse(report.isReadyToLaunch)
        assertEquals(1, report.explicitConflicts.size)
        assertTrue(report.explicitConflicts.first().reason.contains("Source: Fabric Loader metadata"))
    }

    @Test
    fun `test semver extraction and comparisons`() {
        assertEquals("0.9.3", SemverRangeEvaluator.extractModVersion("mc1.21.11-0.9.3+fabric"))
        assertEquals("0.9.3", SemverRangeEvaluator.extractModVersion("1.21.11-0.9.3"))
        assertEquals("1.10.7", SemverRangeEvaluator.extractModVersion("1.10.7+mc1.21.11"))
        assertEquals("0.9.3", SemverRangeEvaluator.extractModVersion("v0.9.3"))
        assertEquals("0.9.3", SemverRangeEvaluator.extractModVersion("sodium-extra-0.9.3+1.21.11"))

        assertTrue(ModCompatibilityResolver.compareSemVer("1.21.11", "1.21.2") > 0)
        assertTrue(ModCompatibilityResolver.compareSemVer("0.8.14", "0.8.13") > 0)
        assertTrue(ModCompatibilityResolver.compareSemVer("1.10.7", "1.10.7") == 0)
        assertTrue(ModCompatibilityResolver.compareSemVer("0.5.8", "0.6.0") < 0)
    }

    @Test
    fun `test validateLaunchCompatibility blocks when required dependency is missing`() {
        val mods = listOf(
            LocalMod(
                id = "iris",
                name = "Iris",
                version = "1.10.7+mc1.21.11",
                fileName = "iris-fabric-1.10.7+mc1.21.11.jar",
                enabled = true,
                dependencies = mapOf("sodium" to ">=0.8.13 <0.9")
            )
        )

        val report = ModCompatibilityResolver.validateLaunchCompatibility(
            minecraftVersion = "1.21.11",
            loader = "Fabric",
            installedMods = mods
        )

        assertFalse(report.isReadyToLaunch, "Launch must be blocked when a required dependency is missing")
        assertEquals(1, report.missingDependencies.size)
        assertTrue(report.missingDependencies.first().contains("requires 'sodium'"))
        assertTrue(report.formattedReport.contains("Required:                       FAIL"))
        assertTrue(report.formattedReport.contains("MISSING:"))
    }

    @Test
    fun `test SemverRangeEvaluator satisfiesAll calculates constraint intersection correctly`() {
        val irisConstraint = ">=0.8.0 <0.9"
        val sodiumExtraConstraint = ">=0.8.13"
        val constraints = listOf(irisConstraint, sodiumExtraConstraint)

        assertTrue(SemverRangeEvaluator.satisfiesAll("0.8.14", constraints))
        assertTrue(SemverRangeEvaluator.satisfiesAll("0.8.13", constraints))
        assertFalse(SemverRangeEvaluator.satisfiesAll("0.8.12", constraints), "0.8.12 does not satisfy sodium extra >=0.8.13")
        assertFalse(SemverRangeEvaluator.satisfiesAll("0.9.0", constraints), "0.9.0 does not satisfy iris <0.9")
    }

    @Test
    fun `test semver range evaluator satisfies and breaks matching`() {
        assertTrue(SemverRangeEvaluator.satisfies("0.8.13", ">=0.8.0 <0.8.14"))
        assertFalse(SemverRangeEvaluator.satisfies("0.8.14", ">=0.8.0 <0.8.14"))
        assertTrue(SemverRangeEvaluator.satisfies("1.21.1", "~1.21.0"))
        assertTrue(SemverRangeEvaluator.satisfies("1.2.3", "^1.2.0"))
        assertTrue(SemverRangeEvaluator.satisfies("mc1.21.11-0.9.3+fabric", ">=0.9.0"))
        assertTrue(SemverRangeEvaluator.isBreaksConstraintMatched("0.8.14", ">=0.8.14"))
        assertFalse(SemverRangeEvaluator.isBreaksConstraintMatched("0.8.13", ">=0.8.14"))
    }
}
