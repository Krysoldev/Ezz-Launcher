package io.ezz.launcher.core.minecraft.version

import io.ezz.launcher.core.model.minecraft.VersionSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MinecraftVersionCatalogTest {

    @Test
    fun testSemanticSortingOfVersions() {
        val versions = listOf(
            VersionSummary("1.8.9", "release", "", "", "2015-12-03"),
            VersionSummary("1.21.10", "release", "", "", "2025-01-01"),
            VersionSummary("1.21.9", "release", "", "", "2024-12-20"),
            VersionSummary("1.21.4", "release", "", "", "2024-12-03"),
            VersionSummary("1.20.1", "release", "", "", "2023-06-12"),
            VersionSummary("1.16.5", "release", "", "", "2021-01-15"),
            VersionSummary("1.7.10", "release", "", "", "2014-06-26")
        )

        val newestFirst = MinecraftVersionComparator.sort(versions, VersionSortOrder.NEWEST_FIRST)
        assertEquals("1.21.10", newestFirst[0].id)
        assertEquals("1.21.9", newestFirst[1].id)
        assertEquals("1.21.4", newestFirst[2].id)
        assertEquals("1.20.1", newestFirst[3].id)
        assertEquals("1.16.5", newestFirst[4].id)
        assertEquals("1.8.9", newestFirst[5].id)
        assertEquals("1.7.10", newestFirst[6].id)

        val oldestFirst = MinecraftVersionComparator.sort(versions, VersionSortOrder.OLDEST_FIRST)
        assertEquals("1.7.10", oldestFirst[0].id)
        assertEquals("1.8.9", oldestFirst[1].id)
        assertEquals("1.21.10", oldestFirst.last().id)
    }

    @Test
    fun testSemanticStringComparison() {
        assertTrue(MinecraftVersionComparator.compareSemanticStrings("1.21.10", "1.21.9") > 0)
        assertTrue(MinecraftVersionComparator.compareSemanticStrings("1.21.1", "1.21") > 0)
        assertTrue(MinecraftVersionComparator.compareSemanticStrings("1.20.1", "1.19.4") > 0)
        assertTrue(MinecraftVersionComparator.compareSemanticStrings("1.8.9", "1.16.5") < 0)
    }

    @Test
    fun testJavaRequirementsResolution() {
        assertEquals(21, JavaCompatibility.getRequiredJavaMajorVersion("1.21.4"))
        assertEquals(21, JavaCompatibility.getRequiredJavaMajorVersion("1.21.1"))
        assertEquals(21, JavaCompatibility.getRequiredJavaMajorVersion("1.20.6"))
        assertEquals(21, JavaCompatibility.getRequiredJavaMajorVersion("1.20.5"))
        assertEquals(17, JavaCompatibility.getRequiredJavaMajorVersion("1.20.4"))
        assertEquals(17, JavaCompatibility.getRequiredJavaMajorVersion("1.20.1"))
        assertEquals(17, JavaCompatibility.getRequiredJavaMajorVersion("1.19.4"))
        assertEquals(17, JavaCompatibility.getRequiredJavaMajorVersion("1.18.2"))
        assertEquals(17, JavaCompatibility.getRequiredJavaMajorVersion("1.17.1"))
        assertEquals(8, JavaCompatibility.getRequiredJavaMajorVersion("1.16.5"))
        assertEquals(8, JavaCompatibility.getRequiredJavaMajorVersion("1.12.2"))
        assertEquals(8, JavaCompatibility.getRequiredJavaMajorVersion("1.8.9"))
        assertEquals(8, JavaCompatibility.getRequiredJavaMajorVersion("1.7.10"))
    }

    @Test
    fun testJavaRuntimeCompatibilityValidation() {
        // Java 21 satisfies Java 21, 17, 16
        assertTrue(JavaCompatibility.isJavaVersionCompatible(installedMajorVersion = 21, requiredMajorVersion = 21))
        assertTrue(JavaCompatibility.isJavaVersionCompatible(installedMajorVersion = 21, requiredMajorVersion = 17))
        
        // Java 17 satisfies Java 17, 16, but not 21
        assertTrue(JavaCompatibility.isJavaVersionCompatible(installedMajorVersion = 17, requiredMajorVersion = 17))
        assertTrue(!JavaCompatibility.isJavaVersionCompatible(installedMajorVersion = 17, requiredMajorVersion = 21))

        // Java 8 satisfies Java 8
        assertTrue(JavaCompatibility.isJavaVersionCompatible(installedMajorVersion = 8, requiredMajorVersion = 8))
        assertTrue(!JavaCompatibility.isJavaVersionCompatible(installedMajorVersion = 8, requiredMajorVersion = 17))
        assertTrue(!JavaCompatibility.isJavaVersionCompatible(installedMajorVersion = 8, requiredMajorVersion = 21))
    }
}
