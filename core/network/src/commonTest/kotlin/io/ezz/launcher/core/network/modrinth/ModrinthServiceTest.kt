package io.ezz.launcher.core.network.modrinth

import kotlin.test.Test
import kotlin.test.assertEquals

class ModrinthServiceTest {

    private val service = ModrinthService()

    @Test
    fun testVersionComparatorOrdersExactVersionsCorrectly() {
        val rawVersions = listOf(
            "1.8.9",
            "1.20.1",
            "1.21.11",
            "1.21.2",
            "1.21.10",
            "1.21.1",
            "1.21",
            "1.20.4",
            "1.16.5",
            "1.12.2",
            "1.19.4"
        )

        val sorted = rawVersions.sortedWith { a, b -> ModrinthService.compareGameVersions(a, b) }

        val expected = listOf(
            "1.21.11",
            "1.21.10",
            "1.21.2",
            "1.21.1",
            "1.21",
            "1.20.4",
            "1.20.1",
            "1.19.4",
            "1.16.5",
            "1.12.2",
            "1.8.9"
        )

        assertEquals(expected, sorted)
    }

    @Test
    fun testVersionComparatorHandlesSubMinorVersions() {
        val versions = listOf("1.20", "1.20.4", "1.20.1", "1.20.6")
        val sorted = versions.sortedWith { a, b -> ModrinthService.compareGameVersions(a, b) }

        val expected = listOf("1.20.6", "1.20.4", "1.20.1", "1.20")
        assertEquals(expected, sorted)
    }
}
