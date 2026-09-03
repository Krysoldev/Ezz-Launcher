package io.ezz.launcher.core.model.modrinth

import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.model.instance.LocalMod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModInstallModelsTest {

    @Test
    fun testResolvedModDependency_RequiredAutoSelectsIfNotInstalled() {
        val dep = ModrinthDependency(
            projectId = "P7dR8mSH", // Fabric API
            dependencyType = "required"
        )
        val project = ModrinthProjectHit(
            projectId = "P7dR8mSH",
            slug = "fabric-api",
            title = "Fabric API"
        )
        val version = ModrinthVersion(
            id = "ver-fapi-1",
            projectId = "P7dR8mSH",
            name = "Fabric API 0.100.0+1.21.1",
            versionNumber = "0.100.0+1.21.1",
            gameVersions = listOf("1.21.1"),
            loaders = listOf("fabric")
        )

        val resolvedNotInstalled = ResolvedModDependency(
            dependency = dep,
            project = project,
            version = version,
            isRequired = true,
            isAlreadyInstalled = false
        )

        assertTrue(resolvedNotInstalled.isRequired)
        assertFalse(resolvedNotInstalled.isAlreadyInstalled)
        assertTrue(resolvedNotInstalled.selectedToInstall, "Required dependency not installed must be selected to install")

        val resolvedInstalled = ResolvedModDependency(
            dependency = dep,
            project = project,
            version = version,
            isRequired = true,
            isAlreadyInstalled = true
        )

        assertFalse(resolvedInstalled.selectedToInstall, "Required dependency already installed should not be selected to install")
    }

    @Test
    fun testResolvedModDependency_OptionalDoesNotAutoSelect() {
        val dep = ModrinthDependency(
            projectId = "iris",
            dependencyType = "optional"
        )
        val project = ModrinthProjectHit(
            projectId = "iris",
            slug = "iris",
            title = "Iris Shaders"
        )
        val version = ModrinthVersion(
            id = "ver-iris-1",
            projectId = "iris",
            name = "Iris 1.7.0",
            versionNumber = "1.7.0",
            gameVersions = listOf("1.21.1"),
            loaders = listOf("fabric")
        )

        val resolvedOptional = ResolvedModDependency(
            dependency = dep,
            project = project,
            version = version,
            isRequired = false,
            isAlreadyInstalled = false
        )

        assertFalse(resolvedOptional.isRequired)
        assertFalse(resolvedOptional.selectedToInstall, "Optional dependency must NOT be silently auto-selected")
    }

    @Test
    fun testVersionSorting_NaturalSemverOrder() {
        val versions = listOf(
            "1.16.5",
            "1.21.1",
            "1.21.11",
            "1.21.10",
            "1.20.1",
            "1.21.2",
            "1.20.4"
        )

        fun compareGameVersions(v1: String, v2: String): Int {
            val parts1 = v1.split('.').mapNotNull { it.toIntOrNull() }
            val parts2 = v2.split('.').mapNotNull { it.toIntOrNull() }
            if (parts1.isNotEmpty() && parts2.isNotEmpty() && parts1.size == v1.split('.').size && parts2.size == v2.split('.').size) {
                val maxLen = maxOf(parts1.size, parts2.size)
                for (i in 0 until maxLen) {
                    val p1 = parts1.getOrElse(i) { 0 }
                    val p2 = parts2.getOrElse(i) { 0 }
                    if (p1 != p2) return p2.compareTo(p1) // descending (newer first)
                }
                return 0
            }
            return v2.compareTo(v1)
        }

        val sorted = versions.sortedWith { v1, v2 -> compareGameVersions(v1, v2) }

        val expected = listOf(
            "1.21.11",
            "1.21.10",
            "1.21.2",
            "1.21.1",
            "1.20.4",
            "1.20.1",
            "1.16.5"
        )

        assertEquals(expected, sorted, "Versions must be naturally sorted with exact sub-versions newest first")
    }

    @Test
    fun testModInstallWizardState_DefaultConstruction() {
        val project = ModrinthProjectHit(
            projectId = "AANobbMI",
            slug = "sodium",
            title = "Sodium",
            description = "Modern rendering engine for Minecraft"
        )
        val instance = Instance(
            id = "inst-test",
            name = "Fabric 1.21.11",
            minecraftVersion = "1.21.11",
            loaderType = LoaderType.FABRIC
        )

        val wizardState = ModInstallWizardState(
            project = project,
            targetInstance = instance,
            selectedGameVersion = instance.minecraftVersion,
            selectedLoader = instance.loaderType.name.lowercase()
        )

        assertEquals("Sodium", wizardState.project.title)
        assertEquals("1.21.11", wizardState.selectedGameVersion)
        assertEquals("fabric", wizardState.selectedLoader)
        assertFalse(wizardState.isAlreadyInstalled)
        assertFalse(wizardState.isLoadingVersions)
    }
}
