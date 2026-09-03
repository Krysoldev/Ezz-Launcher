package io.ezz.launcher.core.minecraft.mods

import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.model.modrinth.ModInstallProgress
import io.ezz.launcher.core.model.modrinth.ModInstallWizardState
import io.ezz.launcher.core.model.modrinth.ModrinthDependency
import io.ezz.launcher.core.model.modrinth.ModrinthProjectHit
import io.ezz.launcher.core.model.modrinth.ModrinthVersion
import io.ezz.launcher.core.model.modrinth.ModrinthVersionFile
import io.ezz.launcher.core.model.modrinth.ModrinthVersionType
import io.ezz.launcher.core.model.modrinth.ResolvedModDependency
import io.ezz.launcher.core.network.modrinth.ModrinthService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModInstallSystemTest {

    @Test
    fun testVersionComparisonAlgorithm() {
        val comparator = Comparator<String> { v1, v2 -> ModrinthService.compareGameVersions(v1, v2) }

        val versions = listOf(
            "1.20.1",
            "1.21.1",
            "1.21.11",
            "1.21.2",
            "1.19.4",
            "1.21",
            "1.20"
        )

        val sorted = versions.sortedWith(comparator)

        // Expected sorted order (newest first):
        // 1.21.11, 1.21.2, 1.21.1, 1.21, 1.20.1, 1.20, 1.19.4
        assertEquals("1.21.11", sorted[0])
        assertEquals("1.21.2", sorted[1])
        assertEquals("1.21.1", sorted[2])
        assertEquals("1.21", sorted[3])
        assertEquals("1.20.1", sorted[4])
        assertEquals("1.20", sorted[5])
        assertEquals("1.19.4", sorted[6])
    }

    @Test
    fun testVersionTypeParsing() {
        assertEquals(ModrinthVersionType.RELEASE, ModrinthVersionType.fromApiValue("release"))
        assertEquals(ModrinthVersionType.BETA, ModrinthVersionType.fromApiValue("beta"))
        assertEquals(ModrinthVersionType.ALPHA, ModrinthVersionType.fromApiValue("alpha"))
        assertEquals(ModrinthVersionType.RELEASE, ModrinthVersionType.fromApiValue("unknown_type"))
    }

    @Test
    fun testModInstallWizardState() {
        val sampleHit = ModrinthProjectHit(
            projectId = "sodium-mc",
            title = "Sodium",
            description = "A modern rendering engine for Minecraft",
            iconUrl = "https://example.com/sodium.png",
            author = "jellysquid",
            downloads = 1000000,
            follows = 5000,
            categories = listOf("fabric", "quilt"),
            versions = listOf("1.21.1", "1.21.0", "1.20.4")
        )

        val sampleVersion = ModrinthVersion(
            id = "v-sodium-121",
            projectId = "sodium-mc",
            name = "Sodium 0.5.11 for 1.21.1",
            versionNumber = "0.5.11+mc1.21.1",
            gameVersions = listOf("1.21.1"),
            loaders = listOf("fabric", "quilt"),
            versionType = "release",
            files = listOf(
                ModrinthVersionFile(
                    url = "https://cdn.modrinth.com/data/sodium.jar",
                    filename = "sodium-fabric-0.5.11+mc1.21.1.jar",
                    primary = true,
                    size = 1245000
                )
            ),
            dependencies = listOf(
                ModrinthDependency(
                    projectId = "fabric-api",
                    dependencyType = "required"
                )
            )
        )

        val testInstance = Instance(
            id = "inst-121",
            name = "1.21.1 Fabric Client",
            minecraftVersion = "1.21.1",
            loaderType = LoaderType.FABRIC
        )

        val state = ModInstallWizardState(
            project = sampleHit,
            targetInstance = testInstance,
            availableGameVersions = listOf("1.21.1", "1.21.0", "1.20.4"),
            selectedGameVersion = "1.21.1",
            availableLoadersForVersion = listOf("fabric", "quilt"),
            selectedLoader = "fabric",
            compatibleVersions = listOf(sampleVersion),
            selectedVersion = sampleVersion,
            isAlreadyInstalled = false
        )

        assertEquals("1.21.1", state.selectedGameVersion)
        assertEquals("fabric", state.selectedLoader)
        assertEquals("0.5.11+mc1.21.1", state.selectedVersion?.versionNumber)
        assertFalse(state.isAlreadyInstalled)
    }

    @Test
    fun testModInstallProgressLifecycle() {
        val downloading = ModInstallProgress(
            stage = "DOWNLOADING",
            progress = 0.45f,
            currentFileName = "sodium-fabric-0.5.11+mc1.21.1.jar",
            isComplete = false
        )
        assertEquals("DOWNLOADING", downloading.stage)
        assertEquals(0.45f, downloading.progress)
        assertFalse(downloading.isComplete)

        val completed = ModInstallProgress(
            stage = "VERIFIED",
            progress = 1.0f,
            currentFileName = "sodium-fabric-0.5.11+mc1.21.1.jar",
            isComplete = true
        )
        assertTrue(completed.isComplete)
        assertEquals(1.0f, completed.progress)
    }

    @Test
    fun testResolvedModDependencySelection() {
        val requiredDep = ResolvedModDependency(
            dependency = ModrinthDependency(projectId = "fabric-api", dependencyType = "required"),
            project = null,
            version = null,
            isRequired = true,
            isAlreadyInstalled = false,
            selectedToInstall = true
        )
        assertTrue(requiredDep.isRequired)
        assertTrue(requiredDep.selectedToInstall)

        val alreadyInstalledDep = ResolvedModDependency(
            dependency = ModrinthDependency(projectId = "fabric-api", dependencyType = "required"),
            project = null,
            version = null,
            isRequired = true,
            isAlreadyInstalled = true,
            selectedToInstall = false
        )
        assertTrue(alreadyInstalledDep.isAlreadyInstalled)
        assertFalse(alreadyInstalledDep.selectedToInstall)
    }
}
