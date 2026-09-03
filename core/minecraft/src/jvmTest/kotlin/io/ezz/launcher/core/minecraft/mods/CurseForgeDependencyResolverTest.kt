package io.ezz.launcher.core.minecraft.mods

import io.ezz.launcher.core.model.curseforge.CurseForgeDependencyRelationType
import io.ezz.launcher.core.model.curseforge.CurseForgeFile
import io.ezz.launcher.core.model.curseforge.CurseForgeFileDependency
import io.ezz.launcher.core.model.curseforge.CurseForgeMod
import io.ezz.launcher.core.model.instance.LocalMod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CurseForgeDependencyResolverTest {

    private fun createCurseForgeMod(
        id: Long,
        name: String,
        slug: String
    ): CurseForgeMod {
        return CurseForgeMod(
            id = id,
            name = name,
            slug = slug
        )
    }

    private fun createCurseForgeFile(
        id: Long,
        modId: Long,
        fileName: String,
        displayName: String = fileName,
        gameVersions: List<String> = listOf("1.21.11", "Fabric"),
        releaseType: Int = 1, // 1 = Release, 2 = Beta, 3 = Alpha
        dependencies: List<CurseForgeFileDependency> = emptyList()
    ): CurseForgeFile {
        return CurseForgeFile(
            id = id,
            modId = modId,
            fileName = fileName,
            displayName = displayName,
            gameVersions = gameVersions,
            releaseType = releaseType,
            dependencies = dependencies
        )
    }

    @Test
    fun `test Case 1 - Installed Sodium 0_8_14 allows installing Iris Shaders 1_10_7`() {
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

        val irisMod = createCurseForgeMod(455508L, "Iris Shaders", "iris")
        val candidateFiles = listOf(
            createCurseForgeFile(
                id = 5501L,
                modId = 455508L,
                fileName = "iris-fabric-1.10.7+mc1.21.11.jar",
                displayName = "1.10.7+mc1.21.11",
                gameVersions = listOf("1.21.11", "Fabric"),
                releaseType = 1,
                dependencies = listOf(
                    CurseForgeFileDependency(modId = 394535L, relationType = 3) // Required: Sodium
                )
            ),
            createCurseForgeFile(
                id = 5500L,
                modId = 455508L,
                fileName = "iris-fabric-1.10.5+mc1.21.11.jar",
                displayName = "1.10.5+mc1.21.11",
                gameVersions = listOf("1.21.11", "Fabric"),
                releaseType = 1
            )
        )

        val result = CurseForgeDependencyResolver.resolveCompatibility(
            minecraftVersion = "1.21.11",
            loader = "fabric",
            installedMods = installedSodium,
            mod = irisMod,
            candidateFiles = candidateFiles
        )

        assertTrue(result.hasCompatibleVersion, "Iris Shaders MUST be compatible when Sodium 0.8.14 is installed")
        assertTrue(result.isLatestCompatible)
        assertEquals(5501L, result.recommendedFile?.id)
        assertNull(result.primaryConflictText)
    }

    @Test
    fun `test Case 2 - Reverse Direction - Installed Iris 1_10_7 allows installing Sodium 0_8_14`() {
        val installedIris = listOf(
            LocalMod(
                id = "iris",
                name = "Iris Shaders",
                version = "1.10.7+mc1.21.11",
                fileName = "iris-fabric-1.10.7+mc1.21.11.jar",
                enabled = true,
                dependencies = mapOf("sodium" to ">=0.6.0")
            )
        )

        val sodiumMod = createCurseForgeMod(394535L, "Sodium", "sodium")
        val candidateFiles = listOf(
            createCurseForgeFile(
                id = 6002L,
                modId = 394535L,
                fileName = "sodium-fabric-0.8.14+mc1.21.11.jar",
                displayName = "0.8.14+mc1.21.11",
                gameVersions = listOf("1.21.11", "Fabric"),
                releaseType = 1
            ),
            createCurseForgeFile(
                id = 6001L,
                modId = 394535L,
                fileName = "sodium-fabric-0.8.13+mc1.21.11.jar",
                displayName = "0.8.13+mc1.21.11",
                gameVersions = listOf("1.21.11", "Fabric"),
                releaseType = 1
            )
        )

        val result = CurseForgeDependencyResolver.resolveCompatibility(
            minecraftVersion = "1.21.11",
            loader = "fabric",
            installedMods = installedIris,
            mod = sodiumMod,
            candidateFiles = candidateFiles
        )

        assertTrue(result.hasCompatibleVersion, "Sodium 0.8.14 MUST be compatible when Iris is installed")
        assertTrue(result.isLatestCompatible)
        assertEquals(6002L, result.recommendedFile?.id)
    }

    @Test
    fun `test Case 3 - Fabric 1_21_11 + Iris installed allows installing Sodium Extra`() {
        val installedIris = listOf(
            LocalMod(
                id = "iris",
                name = "Iris Shaders",
                version = "1.10.7+mc1.21.11",
                fileName = "iris-1.10.7+mc1.21.11.jar",
                enabled = true,
                dependencies = mapOf("sodium" to ">=0.6.0"),
                breaks = mapOf("sodium" to ">=0.8.14")
            )
        )

        val sodiumExtraMod = createCurseForgeMod(306612L, "Sodium Extra", "sodium-extra")
        val candidateFiles = listOf(
            createCurseForgeFile(
                id = 5001L,
                modId = 306612L,
                fileName = "sodium-extra-fabric-mc1.21.11-0.9.3.jar",
                displayName = "mc1.21.11-0.9.3+fabric",
                gameVersions = listOf("1.21.11", "Fabric"),
                releaseType = 1,
                dependencies = listOf(
                    CurseForgeFileDependency(modId = 394535L, relationType = 3) // Required: Sodium
                )
            ),
            createCurseForgeFile(
                id = 5000L,
                modId = 306612L,
                fileName = "sodium-extra-fabric-mc1.21.11-0.9.2.jar",
                displayName = "mc1.21.11-0.9.2+fabric",
                gameVersions = listOf("1.21.11", "Fabric"),
                releaseType = 1
            )
        )

        val result = CurseForgeDependencyResolver.resolveCompatibility(
            minecraftVersion = "1.21.11",
            loader = "fabric",
            installedMods = installedIris,
            mod = sodiumExtraMod,
            candidateFiles = candidateFiles
        )

        assertTrue(result.hasCompatibleVersion, "Sodium Extra must be compatible with Iris installed")
        assertTrue(result.isLatestCompatible)
        assertEquals(5001L, result.recommendedFile?.id)
        assertNull(result.primaryConflictText)
    }

    @Test
    fun `test Case 4 - Forge instance rejects Fabric-only mod`() {
        val fabricMod = createCurseForgeMod(111L, "Fabric Mod", "fabric-mod")
        val files = listOf(
            createCurseForgeFile(
                id = 7001L,
                modId = 111L,
                fileName = "fabricmod-1.0.jar",
                gameVersions = listOf("1.21.11", "Fabric")
            )
        )

        val result = CurseForgeDependencyResolver.resolveCompatibility(
            minecraftVersion = "1.21.11",
            loader = "forge",
            installedMods = emptyList(),
            mod = fabricMod,
            candidateFiles = files
        )

        assertFalse(result.hasCompatibleVersion)
        assertNull(result.recommendedFile)
        assertNotNull(result.selectionReason)
        assertTrue(result.selectionReason!!.contains("FORGE"))
    }

    @Test
    fun `test Case 5 - Optional and embedded dependencies do not fail compatibility`() {
        val mod = createCurseForgeMod(222L, "Complex Mod", "complex-mod")
        val files = listOf(
            createCurseForgeFile(
                id = 8001L,
                modId = 222L,
                fileName = "complex-1.0.jar",
                gameVersions = listOf("1.21.11", "Fabric"),
                dependencies = listOf(
                    CurseForgeFileDependency(modId = 9999L, relationType = 2), // Optional
                    CurseForgeFileDependency(modId = 8888L, relationType = 1), // Embedded
                    CurseForgeFileDependency(modId = 7777L, relationType = 4)  // Tool
                )
            )
        )

        val result = CurseForgeDependencyResolver.resolveCompatibility(
            minecraftVersion = "1.21.11",
            loader = "fabric",
            installedMods = emptyList(),
            mod = mod,
            candidateFiles = files
        )

        assertTrue(result.hasCompatibleVersion, "Optional and embedded dependencies must not block compatibility")
        assertEquals(8001L, result.recommendedFile?.id)
    }

    @Test
    fun `test Case 6 - Installed incompatible dependency rejects candidate with source`() {
        val sodiumMod = createCurseForgeMod(394535L, "Sodium", "sodium")
        val files = listOf(
            createCurseForgeFile(
                id = 9001L,
                modId = 394535L,
                fileName = "sodium-0.6.0.jar",
                gameVersions = listOf("1.21.11", "Fabric"),
                dependencies = listOf(
                    CurseForgeFileDependency(modId = 99999L, relationType = 5) // Incompatible: 99999
                )
            )
        )

        val installedWithId = listOf(
            LocalMod(
                id = "99999",
                name = "IncompatibleMod",
                version = "1.0",
                fileName = "mod-99999.jar",
                enabled = true
            )
        )

        val result = CurseForgeDependencyResolver.resolveCompatibility(
            minecraftVersion = "1.21.11",
            loader = "fabric",
            installedMods = installedWithId,
            mod = sodiumMod,
            candidateFiles = files
        )

        assertFalse(result.hasCompatibleVersion)
        assertNotNull(result.primaryConflictText)
        assertTrue(result.primaryConflictText!!.contains("IncompatibleMod", ignoreCase = true))
    }
}
