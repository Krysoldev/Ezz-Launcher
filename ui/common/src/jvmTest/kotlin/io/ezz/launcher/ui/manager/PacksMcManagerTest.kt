package io.ezz.launcher.ui.manager

import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.InstanceContentType
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.model.packsmc.PacksMcAuthor
import io.ezz.launcher.core.model.packsmc.PacksMcBrowseState
import io.ezz.launcher.core.model.packsmc.PacksMcLicense
import io.ezz.launcher.core.model.packsmc.PacksMcPack
import io.ezz.launcher.core.model.packsmc.PacksMcStatus
import io.ezz.launcher.core.model.packsmc.PacksMcVersionBuild
import io.ezz.launcher.core.model.packsmc.ResourcePackProvider
import io.ezz.launcher.core.storage.instance.InstanceContentDestinationResolver
import io.ezz.launcher.core.storage.path.DefaultPathProvider
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PacksMcManagerTest {

    @Test
    fun testResourcePackProvidersEnum() {
        val providers = ResourcePackProvider.values()
        assertEquals(2, providers.size)
        assertEquals("Modrinth", ResourcePackProvider.MODRINTH.title)
        assertEquals("PacksMC", ResourcePackProvider.PACKSMC.title)
    }

    @Test
    fun testPacksMcBrowseStateDefaults() {
        val state = PacksMcBrowseState()
        assertEquals("", state.searchQuery)
        assertEquals("recent", state.selectedSort)
        assertEquals(null, state.selectedResolution)
        assertFalse(state.filterMinecraftVersion)
        assertTrue(state.items.isEmpty())
        assertFalse(state.isRateLimited)
        assertEquals(PacksMcStatus.READY, state.status)
    }

    @Test
    fun testPacksMcClientSideVersionFiltering() {
        val packs = listOf(
            PacksMcPack(
                id = "pack1",
                slug = "bare-bones",
                name = "Bare Bones",
                mcVersions = listOf("1.21.4", "1.21.1"),
                versions = listOf(PacksMcVersionBuild(mcVersion = "1.21.4"))
            ),
            PacksMcPack(
                id = "pack2",
                slug = "legacy-pvp",
                name = "Legacy PvP",
                mcVersions = listOf("1.8.9"),
                versions = listOf(PacksMcVersionBuild(mcVersion = "1.8.9"))
            ),
            PacksMcPack(
                id = "pack3",
                slug = "universal-pack",
                name = "Universal Textures",
                mcVersions = emptyList(),
                versions = emptyList()
            )
        )

        val targetVersion = "1.21.4"

        // Filter for 1.21.4
        val filtered = packs.filter { pack ->
            (pack.mcVersions.isEmpty() && pack.versions.isEmpty()) ||
            pack.mcVersions.any { it.equals(targetVersion, ignoreCase = true) } ||
            pack.versions.any { it.mcVersion.equals(targetVersion, ignoreCase = true) }
        }

        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.slug == "bare-bones" })
        assertTrue(filtered.any { it.slug == "universal-pack" })
        assertFalse(filtered.any { it.slug == "legacy-pvp" })
    }

    @Test
    fun testPacksMcResolutionFiltering() {
        val packs = listOf(
            PacksMcPack(id = "1", slug = "p1", name = "16x Pack", resolution = "16x"),
            PacksMcPack(id = "2", slug = "p2", name = "32x Pack", resolution = "32x"),
            PacksMcPack(id = "3", slug = "p3", name = "64x Pack", resolution = "64x"),
            PacksMcPack(id = "4", slug = "p4", name = "No Res Pack", resolution = null)
        )

        val res32 = packs.filter { it.resolution?.equals("32x", ignoreCase = true) == true }
        assertEquals(1, res32.size)
        assertEquals("p2", res32[0].slug)
    }

    @Test
    fun testResourcePackDestinationPathResolverUsesCorrectFolder() {
        val dummyRoot = "C:/ezz_test_dir".toPath()
        val pathProvider = DefaultPathProvider(dummyRoot)
        val resolver = InstanceContentDestinationResolver(pathProvider)

        val instance = Instance(
            id = "test-instance-123",
            name = "Test Instance",
            minecraftVersion = "1.21.4",
            loaderType = LoaderType.FABRIC
        )

        val destination = resolver.resolveContentDirectory(instance.id, InstanceContentType.RESOURCE_PACK)
        val pathStr = destination.absolutePath.replace("\\", "/")

        assertTrue(pathStr.endsWith(".minecraft/resourcepacks"), "Resource packs destination must be .minecraft/resourcepacks but was $pathStr")
        assertFalse(pathStr.contains("mods/"), "Resource pack must NEVER be installed into mods/")
        assertFalse(pathStr.contains("shaderpacks/"), "Resource pack must NEVER be installed into shaderpacks/")
    }
}
