package io.ezz.launcher.core.minecraft.mods

import io.ezz.launcher.core.model.modrinth.ModrinthDependency
import io.ezz.launcher.core.model.modrinth.ModrinthProjectHit
import io.ezz.launcher.core.model.modrinth.ModrinthVersion
import io.ezz.launcher.core.model.modrinth.ModrinthVersionFile
import io.ezz.launcher.core.model.modrinth.ModrinthVersionType
import io.ezz.launcher.core.model.modrinth.ResolvedModDependency
import io.ezz.launcher.core.model.modrinth.ModInstallWizardState
import io.ezz.launcher.core.network.modrinth.ModrinthService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModInstallFlowTest {

    private fun createVersion(
        id: String,
        versionNumber: String,
        gameVersions: List<String>,
        loaders: List<String>,
        versionType: String = "release",
        dependencies: List<ModrinthDependency> = emptyList()
    ): ModrinthVersion {
        return ModrinthVersion(
            id = id,
            projectId = "test-mod",
            name = "Test Mod $versionNumber",
            versionNumber = versionNumber,
            gameVersions = gameVersions,
            loaders = loaders,
            versionType = versionType,
            dependencies = dependencies,
            files = listOf(
                ModrinthVersionFile(
                    url = "https://cdn.modrinth.com/data/test/$id/mod-$versionNumber.jar",
                    filename = "mod-$versionNumber.jar",
                    primary = true,
                    size = 2048L
                )
            )
        )
    }

    @Test
    fun `test compareGameVersions handles multi-digit semver accurately`() {
        // 1.21.11 is newer than 1.21.2 (1.21.11 comes before 1.21.2 in descending sort, so compare returns negative)
        assertTrue(ModrinthService.compareGameVersions("1.21.11", "1.21.2") < 0)
        // 1.21.2 comes after 1.21.11 in descending sort, so compare returns positive
        assertTrue(ModrinthService.compareGameVersions("1.21.2", "1.21.11") > 0)
        // 1.21.1 == 1.21.1
        assertTrue(ModrinthService.compareGameVersions("1.21.1", "1.21.1") == 0)
        // 1.20.4 is older than 1.21.0
        assertTrue(ModrinthService.compareGameVersions("1.20.4", "1.21.0") > 0)
        // 1.21.10 is newer than 1.21.1
        assertTrue(ModrinthService.compareGameVersions("1.21.10", "1.21.1") < 0)
    }

    @Test
    fun `test sorting game versions with compareGameVersions`() {
        val versions = listOf("1.20.1", "1.21.11", "1.20.4", "1.21.2", "1.21.1")
        val sorted = versions.sortedWith { v1, v2 -> ModrinthService.compareGameVersions(v1, v2) }
        assertEquals(listOf("1.21.11", "1.21.2", "1.21.1", "1.20.4", "1.20.1"), sorted)
    }

    @Test
    fun `test ModInstallWizardState data integrity`() {
        val hit = ModrinthProjectHit(
            projectId = "fabric-api",
            slug = "fabric-api",
            title = "Fabric API",
            description = "Core library",
            author = "modmuss50",
            clientSide = "required",
            serverSide = "required",
            projectType = "mod"
        )
        val version = createVersion("v-fab", "0.100.0", listOf("1.21.1"), listOf("fabric"))

        val dep = ModrinthDependency(
            versionId = "v-dep",
            projectId = "dep-1",
            fileName = "dep-1.jar",
            dependencyType = "required"
        )

        val resolvedDep = ResolvedModDependency(
            dependency = dep,
            project = hit,
            version = version,
            isRequired = true,
            isAlreadyInstalled = false,
            selectedToInstall = true
        )

        val state = ModInstallWizardState(
            project = hit,
            targetInstance = null,
            selectedGameVersion = "1.21.1",
            selectedLoader = "fabric",
            selectedVersion = version,
            installedVersionString = "0.99.0",
            isUpdateAvailable = true,
            resolvedDependencies = listOf(resolvedDep)
        )

        assertTrue(state.isUpdateAvailable)
        assertEquals("0.99.0", state.installedVersionString)
        assertEquals("0.100.0", state.selectedVersion?.versionNumber)
        assertEquals(1, state.resolvedDependencies.size)
        assertTrue(state.resolvedDependencies[0].isRequired)
        assertTrue(state.resolvedDependencies[0].selectedToInstall)
    }

    @Test
    fun `test ModrinthVersionType fromApiValue mapping`() {
        assertEquals(ModrinthVersionType.RELEASE, ModrinthVersionType.fromApiValue("release"))
        assertEquals(ModrinthVersionType.BETA, ModrinthVersionType.fromApiValue("beta"))
        assertEquals(ModrinthVersionType.ALPHA, ModrinthVersionType.fromApiValue("alpha"))
        assertEquals(ModrinthVersionType.RELEASE, ModrinthVersionType.fromApiValue("unknown"))
    }
}
