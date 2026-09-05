package io.ezz.launcher.ui.manager

import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.model.instance.LocalMod
import io.ezz.launcher.core.model.instance.LocalResourcePack
import io.ezz.launcher.core.model.instance.LocalShaderPack
import io.ezz.launcher.core.model.instance.LocalWorld
import io.ezz.launcher.core.model.instance.LocalScreenshot
import io.ezz.launcher.core.model.instance.LogLine
import io.ezz.launcher.core.model.instance.LogSeverityLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstanceManagerSearchTest {

    @Test
    fun testInstalledModsSearchMatching() {
        val mods = listOf(
            LocalMod(
                id = "sodium",
                name = "Sodium",
                version = "0.5.11",
                fileName = "sodium-fabric-0.5.11.jar",
                loader = "FABRIC",
                author = "jellysquid3",
                description = "Modern rendering engine for Minecraft",
                enabled = true
            ),
            LocalMod(
                id = "iris",
                name = "Iris Shaders",
                version = "1.7.0",
                fileName = "iris-mc1.21-1.7.0.jar",
                loader = "FABRIC",
                author = "coderbot",
                description = "A modern shaders mod for Minecraft compatible with Sodium",
                enabled = false
            ),
            LocalMod(
                id = "cloth-config",
                name = "Cloth Config v15",
                version = "15.0.127",
                fileName = "cloth-config-15.0.127-fabric.jar",
                loader = "FABRIC",
                author = "shedaniel",
                description = "Configuration library for Minecraft mods",
                enabled = true
            )
        )

        fun filterMods(query: String, filter: String = "ALL"): List<LocalMod> {
            val q = query.trim()
            return mods.filter { mod ->
                val matchesSearch = q.isBlank() ||
                    mod.name.contains(q, ignoreCase = true) ||
                    mod.id.contains(q, ignoreCase = true) ||
                    mod.fileName.contains(q, ignoreCase = true) ||
                    (mod.author?.contains(q, ignoreCase = true) == true) ||
                    (mod.description?.contains(q, ignoreCase = true) == true) ||
                    mod.loader.contains(q, ignoreCase = true) ||
                    mod.version.contains(q, ignoreCase = true)
                val matchesFilter = when (filter) {
                    "ENABLED" -> mod.enabled
                    "DISABLED" -> !mod.enabled
                    else -> true
                }
                matchesSearch && matchesFilter
            }
        }

        // 1. Whitespace trimmed & case-insensitive partial match
        val sodiumRes = filterMods(" sodium ")
        assertEquals(2, sodiumRes.size, "Both Sodium and Iris (which mentions Sodium in description) should match ' sodium '")
        assertEquals("sodium", sodiumRes[0].id)

        val uppercaseRes = filterMods("SODIUM")
        assertEquals(2, uppercaseRes.size)

        val partialRes = filterMods("sod")
        assertEquals(2, partialRes.size)

        // 2. Search by mod ID
        val idRes = filterMods("cloth-config")
        assertEquals(1, idRes.size)
        assertEquals("Cloth Config v15", idRes[0].name)

        // 3. Search by author
        val authorRes = filterMods("coderbot")
        assertEquals(1, authorRes.size)
        assertEquals("iris", authorRes[0].id)

        // 4. Search by version
        val versionRes = filterMods("0.5.11")
        assertEquals(1, versionRes.size)
        assertEquals("sodium", versionRes[0].id)

        // 5. Search by loader
        val loaderRes = filterMods("FABRIC")
        assertEquals(3, loaderRes.size)

        // 6. Filter interaction: Search + Enabled/Disabled
        val enabledRes = filterMods("sodium", filter = "ENABLED")
        assertEquals(1, enabledRes.size)
        assertEquals("sodium", enabledRes[0].id)

        val disabledRes = filterMods("sodium", filter = "DISABLED")
        assertEquals(1, disabledRes.size)
        assertEquals("iris", disabledRes[0].id)

        // 7. Empty query returns all
        assertEquals(3, filterMods("").size)
        assertEquals(3, filterMods("   ").size)

        // 8. No results
        assertEquals(0, filterMods("optifine").size)
    }

    @Test
    fun testResourcePacksSearchMatching() {
        val packs = listOf(
            LocalResourcePack(
                fileName = "faithful-64x.zip",
                name = "Faithful 64x",
                description = "High resolution textures maintaining the vanilla look",
                enabled = true
            ),
            LocalResourcePack(
                fileName = "bare-bones-1.21.zip",
                name = "Bare Bones",
                description = "Simplistic texture pack inspired by official Minecraft trailers",
                enabled = false
            )
        )

        fun filterPacks(query: String, filter: String = "ALL"): List<LocalResourcePack> {
            val q = query.trim()
            return packs.filter { pack ->
                val matchesSearch = q.isBlank() ||
                    pack.name.contains(q, ignoreCase = true) ||
                    pack.fileName.contains(q, ignoreCase = true) ||
                    (pack.description?.contains(q, ignoreCase = true) == true)
                val matchesFilter = when (filter) {
                    "ENABLED" -> pack.enabled
                    "DISABLED" -> !pack.enabled
                    else -> true
                }
                matchesSearch && matchesFilter
            }
        }

        // Whitespace and case insensitivity
        val res = filterPacks("  FAITHFUL  ")
        assertEquals(1, res.size)
        assertEquals("Faithful 64x", res[0].name)

        // Description search
        val trailerRes = filterPacks("trailer")
        assertEquals(1, trailerRes.size)
        assertEquals("Bare Bones", trailerRes[0].name)

        // Search + Filter interaction
        assertEquals(0, filterPacks("faithful", filter = "DISABLED").size)
        assertEquals(1, filterPacks("faithful", filter = "ENABLED").size)
    }

    @Test
    fun testShadersSearchMatching() {
        val shaders = listOf(
            LocalShaderPack(
                fileName = "ComplementaryReimagined_r5.1.1.zip",
                name = "Complementary Reimagined",
                description = "High visual quality with unmatched performance",
                enabled = true
            ),
            LocalShaderPack(
                fileName = "BSL_v8.2.09.zip",
                name = "BSL Shaders",
                description = "Warm lighting and distinct water effects",
                enabled = false
            )
        )

        fun filterShaders(query: String, filter: String = "ALL"): List<LocalShaderPack> {
            val q = query.trim()
            return shaders.filter { shader ->
                val matchesSearch = q.isBlank() ||
                    shader.name.contains(q, ignoreCase = true) ||
                    shader.fileName.contains(q, ignoreCase = true) ||
                    (shader.description?.contains(q, ignoreCase = true) == true)
                val matchesFilter = when (filter) {
                    "ENABLED" -> shader.enabled
                    "DISABLED" -> !shader.enabled
                    else -> true
                }
                matchesSearch && matchesFilter
            }
        }

        assertEquals(1, filterShaders(" bsl ").size)
        assertEquals(1, filterShaders("COMPLEMENTARY").size)
        assertEquals(1, filterShaders("performance").size)
        assertEquals(0, filterShaders("bsl", filter = "ENABLED").size)
        assertEquals(1, filterShaders("bsl", filter = "DISABLED").size)
    }

    @Test
    fun testWorldsSearchMatching() {
        val worlds = listOf(
            LocalWorld(
                folderName = "survival_2026",
                name = "Hardcore Survival Realm",
                gameType = "Survival",
                version = "1.21.1",
                isHardcore = true
            ),
            LocalWorld(
                folderName = "redstone_lab",
                name = "Creative Redstone Testing",
                gameType = "Creative",
                version = "1.21.1"
            )
        )

        fun filterWorlds(query: String): List<LocalWorld> {
            val q = query.trim()
            return worlds.filter { world ->
                q.isBlank() ||
                    world.name.contains(q, ignoreCase = true) ||
                    world.folderName.contains(q, ignoreCase = true) ||
                    world.gameType.contains(q, ignoreCase = true) ||
                    (world.version?.contains(q, ignoreCase = true) == true)
            }
        }

        assertEquals(1, filterWorlds(" hardcore ").size)
        assertEquals("Hardcore Survival Realm", filterWorlds("hardcore")[0].name)
        assertEquals(1, filterWorlds("redstone_lab").size)
        assertEquals(1, filterWorlds("creative").size)
        assertEquals(2, filterWorlds("1.21.1").size)
        assertEquals(2, filterWorlds("").size)
    }

    @Test
    fun testScreenshotsSearchMatching() {
        val screenshots = listOf(
            LocalScreenshot(
                fileName = "2026-09-04_12.30.00.png",
                filePath = "C:/screenshots/2026-09-04_12.30.00.png"
            ),
            LocalScreenshot(
                fileName = "nether_hub_build.png",
                filePath = "C:/screenshots/nether_hub_build.png"
            )
        )

        fun filterScreenshots(query: String): List<LocalScreenshot> {
            val q = query.trim()
            return if (q.isBlank()) screenshots else screenshots.filter { it.fileName.contains(q, ignoreCase = true) }
        }

        assertEquals(1, filterScreenshots(" nether ").size)
        assertEquals("nether_hub_build.png", filterScreenshots("nether")[0].fileName)
        assertEquals(1, filterScreenshots("2026-09").size)
        assertEquals(2, filterScreenshots("").size)
        assertEquals(0, filterScreenshots("end_portal").size)
    }

    @Test
    fun testLogsSearchMatching() {
        val lines = listOf(
            LogLine(1, "[12:00:00] [main/INFO]: Loading Minecraft 1.21.1 with Fabric Loader", LogSeverityLevel.INFO),
            LogLine(2, "[12:00:01] [main/WARN]: Missing optional dependency 'modmenu'", LogSeverityLevel.WARN),
            LogLine(3, "[12:00:02] [Render thread/ERROR]: Failed to compile shader program", LogSeverityLevel.ERROR)
        )

        fun filterLogs(query: String, level: String = "ALL"): List<LogLine> {
            val q = query.trim()
            return lines.filter { line ->
                val matchesSearch = q.isBlank() || line.text.contains(q, ignoreCase = true)
                val matchesLevel = when (level) {
                    "ERROR" -> line.level == LogSeverityLevel.ERROR
                    "WARN" -> line.level == LogSeverityLevel.WARN
                    "INFO" -> line.level == LogSeverityLevel.INFO
                    "DEBUG" -> line.level == LogSeverityLevel.DEBUG
                    else -> true
                }
                matchesSearch && matchesLevel
            }
        }

        assertEquals(1, filterLogs(" shader ").size)
        assertEquals(1, filterLogs("MODMENU").size)
        assertEquals(1, filterLogs("", level = "ERROR").size)
        assertEquals(1, filterLogs("minecraft", level = "INFO").size)
        assertEquals(0, filterLogs("minecraft", level = "ERROR").size)
    }

    @Test
    fun testSearchSequenceRaceConditionContract() {
        // Contract test for latest-search-wins:
        var currentSeq = 0L
        var latestActiveQuery = ""

        fun onStartSearch(query: String): Long {
            val reqId = ++currentSeq
            latestActiveQuery = query
            return reqId
        }

        fun onSearchFinished(reqId: Long, queryResult: String): String? {
            // Drop out-of-order responses from stale jobs
            if (reqId != currentSeq) {
                return null
            }
            return queryResult
        }

        // Simulating search flow: "sodium" -> "iris" -> "fabric"
        val req1 = onStartSearch("sodium")
        val req2 = onStartSearch("iris")
        val req3 = onStartSearch("fabric")

        // Suppose req1 finishes AFTER req3
        val res1 = onSearchFinished(req1, "Results for sodium")
        assertEquals(null, res1, "Stale sodium results must be dropped")

        // Suppose req2 finishes AFTER req3
        val res2 = onSearchFinished(req2, "Results for iris")
        assertEquals(null, res2, "Stale iris results must be dropped")

        // Latest req3 finishes
        val res3 = onSearchFinished(req3, "Results for fabric")
        assertEquals("Results for fabric", res3, "Latest fabric results must be accepted")
        assertEquals("fabric", latestActiveQuery)
    }
}
