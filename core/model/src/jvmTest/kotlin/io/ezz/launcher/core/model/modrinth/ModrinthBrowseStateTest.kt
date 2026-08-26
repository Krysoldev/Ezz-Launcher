package io.ezz.launcher.core.model.modrinth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModrinthBrowseStateTest {

    @Test
    fun testPaginationCalculation() {
        val hits = (1..20).map { i ->
            ModrinthProjectHit(
                projectId = "mod-$i",
                projectType = "mod",
                title = "Test Mod $i"
            )
        }

        val state = ModrinthBrowseState(
            contentType = ModrinthContentType.MOD,
            items = hits,
            page = 1,
            pageSize = 20,
            totalHits = 45,
            totalPages = 3,
            isLoading = false
        )

        assertFalse(state.hasPrevPage, "Page 1 should not have prev page")
        assertTrue(state.hasNextPage, "Page 1 with totalPages 3 should have next page")

        val page2 = state.copy(page = 2)
        assertTrue(page2.hasPrevPage, "Page 2 should have prev page")
        assertTrue(page2.hasNextPage, "Page 2 with totalPages 3 should have next page")

        val page3 = state.copy(page = 3)
        assertTrue(page3.hasPrevPage, "Page 3 should have prev page")
        assertFalse(page3.hasNextPage, "Page 3 with totalPages 3 should not have next page")
    }

    @Test
    fun testCategoryIsolation() {
        val modHit = ModrinthProjectHit(projectId = "sodium", projectType = "mod", title = "Sodium")
        val packHit = ModrinthProjectHit(projectId = "faithful", projectType = "resourcepack", title = "Faithful")
        val shaderHit = ModrinthProjectHit(projectId = "bsl", projectType = "shader", title = "BSL Shaders")

        val mixedHits = listOf(modHit, packHit, shaderHit)

        val modsOnly = mixedHits.filter { it.projectType == ModrinthContentType.MOD.apiValue }
        val packsOnly = mixedHits.filter { it.projectType == ModrinthContentType.RESOURCE_PACK.apiValue }
        val shadersOnly = mixedHits.filter { it.projectType == ModrinthContentType.SHADER.apiValue }

        assertEquals(1, modsOnly.size)
        assertEquals("sodium", modsOnly.first().projectId)

        assertEquals(1, packsOnly.size)
        assertEquals("faithful", packsOnly.first().projectId)

        assertEquals(1, shadersOnly.size)
        assertEquals("bsl", shadersOnly.first().projectId)
    }
}
