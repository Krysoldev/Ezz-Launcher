package io.ezz.launcher.core.network.curseforge

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CurseForgeServiceTest {

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

        val sorted = rawVersions.sortedWith { a, b -> CurseForgeService.compareGameVersions(a, b) }

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
    fun testServiceHandlesMissingApiKeyWithoutCrashing() = runTest {
        val service = CurseForgeService(apiKeyProvider = { "" })
        assertFalse(service.isConfigured())
        assertEquals(CurseForgeStatus.UNAVAILABLE, service.status.value)

        val searchResult = service.searchMods(query = "sodium")
        assertNotNull(searchResult)
        assertTrue(searchResult.data.isEmpty())

        val mod = service.getMod(12345L)
        assertEquals(null, mod)

        val files = service.getModFiles(12345L)
        assertTrue(files.isEmpty())
    }

    @Test
    fun testServiceStatusReportsConfiguredCorrectly() {
        val service = CurseForgeService(apiKeyProvider = { "test_dummy_key" })
        assertTrue(service.isConfigured())
        assertEquals(CurseForgeStatus.AVAILABLE, service.status.value)
    }
}
