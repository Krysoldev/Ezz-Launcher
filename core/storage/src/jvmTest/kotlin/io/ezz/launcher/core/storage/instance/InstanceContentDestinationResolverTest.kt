package io.ezz.launcher.core.storage.instance

import io.ezz.launcher.core.model.instance.InstanceContentType
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.storage.path.DefaultPathProvider
import io.ezz.launcher.core.storage.repository.LocalInstanceRepository
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InstanceContentDestinationResolverTest {

    private lateinit var tempDir: File
    private lateinit var pathProvider: DefaultPathProvider
    private lateinit var repository: LocalInstanceRepository
    private lateinit var resolver: InstanceContentDestinationResolver

    @BeforeTest
    fun setUp() {
        tempDir = File.createTempFile("ezz_dest_test", "").apply {
            delete()
            mkdirs()
        }
        pathProvider = DefaultPathProvider(tempDir.absolutePath.toPath())
        pathProvider.initializeDirectories()
        repository = LocalInstanceRepository(pathProvider)
        resolver = InstanceContentDestinationResolver(pathProvider)
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testDestinationResolutionForAllWindowsContentDirectories() {
        runBlocking {
            val instance = repository.createInstance(
                name = "Destination Test Instance",
                minecraftVersion = "1.21.1",
                loaderType = LoaderType.FABRIC
            )

            val gameDir = pathProvider.getInstanceGameDirectory(instance.id).toFile().canonicalFile

            // 1. MOD -> mods/
            val modsDir = resolver.resolveContentDirectory(instance.id, InstanceContentType.MOD)
            assertEquals(File(gameDir, "mods").canonicalFile, modsDir)
            assertTrue(modsDir.exists())
            assertTrue(modsDir.isDirectory)

            // 2. RESOURCE_PACK -> resourcepacks/
            val rpDir = resolver.resolveContentDirectory(instance.id, InstanceContentType.RESOURCE_PACK)
            assertEquals(File(gameDir, "resourcepacks").canonicalFile, rpDir)
            assertTrue(rpDir.exists())
            assertTrue(rpDir.isDirectory)

            // 3. SHADER -> shaderpacks/
            val spDir = resolver.resolveContentDirectory(instance.id, InstanceContentType.SHADER)
            assertEquals(File(gameDir, "shaderpacks").canonicalFile, spDir)
            assertTrue(spDir.exists())
            assertTrue(spDir.isDirectory)

            // 4. WORLD -> saves/
            val savesDir = resolver.resolveContentDirectory(instance.id, InstanceContentType.WORLD)
            assertEquals(File(gameDir, "saves").canonicalFile, savesDir)
            assertTrue(savesDir.exists())
            assertTrue(savesDir.isDirectory)

            // 5. SCREENSHOT -> screenshots/
            val ssDir = resolver.resolveContentDirectory(instance.id, InstanceContentType.SCREENSHOT)
            assertEquals(File(gameDir, "screenshots").canonicalFile, ssDir)
            assertTrue(ssDir.exists())
            assertTrue(ssDir.isDirectory)
        }
    }

    @Test
    fun testValidateDestinationRejectsCrossContamination() {
        runBlocking {
            val instance = repository.createInstance(
                name = "Validation Test Instance",
                minecraftVersion = "1.21.1",
                loaderType = LoaderType.FABRIC
            )

            val modsDir = resolver.resolveContentDirectory(instance.id, InstanceContentType.MOD)
            val rpDir = resolver.resolveContentDirectory(instance.id, InstanceContentType.RESOURCE_PACK)
            val spDir = resolver.resolveContentDirectory(instance.id, InstanceContentType.SHADER)

            // Target file placed in resourcepacks should validate for RESOURCE_PACK
            val validRpFile = File(rpDir, "faithful_64x.zip")
            val validatedRp = resolver.validateDestination(instance.id, InstanceContentType.RESOURCE_PACK, validRpFile)
            assertEquals(validRpFile.canonicalFile, validatedRp)

            // Target file in mods/ MUST be rejected if content type is RESOURCE_PACK
            val invalidRpInMods = File(modsDir, "faithful_64x.zip")
            assertFailsWith<IllegalStateException> {
                resolver.validateDestination(instance.id, InstanceContentType.RESOURCE_PACK, invalidRpInMods)
            }

            // Target file in mods/ MUST be rejected if content type is SHADER
            val invalidShaderInMods = File(modsDir, "complimentary_reimagined.zip")
            assertFailsWith<IllegalStateException> {
                resolver.validateDestination(instance.id, InstanceContentType.SHADER, invalidShaderInMods)
            }

            // Target file in shaderpacks/ MUST be rejected if content type is MOD
            val invalidModInShaders = File(spDir, "fabric-api.jar")
            assertFailsWith<IllegalStateException> {
                resolver.validateDestination(instance.id, InstanceContentType.MOD, invalidModInShaders)
            }
        }
    }

    @Test
    fun testAntiPathTraversalRejection() {
        assertFailsWith<IllegalArgumentException> {
            resolver.resolveContentDirectory("", InstanceContentType.MOD)
        }
        assertFailsWith<IllegalArgumentException> {
            resolver.resolveContentDirectory("   ", InstanceContentType.MOD)
        }
        assertFailsWith<IllegalArgumentException> {
            resolver.resolveContentDirectory("../escaped_instance", InstanceContentType.MOD)
        }
        assertFailsWith<IllegalArgumentException> {
            resolver.resolveContentDirectory("sub/folder", InstanceContentType.MOD)
        }
    }

    @Test
    fun testResolveContentPathAndDirectoryName() {
        runBlocking {
            val instance = repository.createInstance(
                name = "Path Test Instance",
                minecraftVersion = "1.21.1",
                loaderType = LoaderType.FABRIC
            )
            val okioPath = resolver.resolveContentPath(instance.id, InstanceContentType.RESOURCE_PACK)
            assertTrue(okioPath.toString().endsWith("resourcepacks"))
            assertEquals(InstanceContentType.MOD, InstanceContentType.fromDirectoryName("mods"))
            assertEquals(InstanceContentType.RESOURCE_PACK, InstanceContentType.fromDirectoryName("resourcepacks"))
            assertEquals(InstanceContentType.SHADER, InstanceContentType.fromDirectoryName("shaderpacks"))
            assertEquals(InstanceContentType.WORLD, InstanceContentType.fromDirectoryName("saves"))
            assertEquals(InstanceContentType.SCREENSHOT, InstanceContentType.fromDirectoryName("screenshots"))
        }
    }

    @Test
    fun testFromModrinthTypeMapping() {
        assertEquals(InstanceContentType.MOD, InstanceContentType.fromModrinthType("mod"))
        assertEquals(InstanceContentType.RESOURCE_PACK, InstanceContentType.fromModrinthType("resourcepack"))
        assertEquals(InstanceContentType.RESOURCE_PACK, InstanceContentType.fromModrinthType("resource_pack"))
        assertEquals(InstanceContentType.SHADER, InstanceContentType.fromModrinthType("shader"))
        assertEquals(InstanceContentType.SHADER, InstanceContentType.fromModrinthType("shaderpack"))
        assertEquals(InstanceContentType.MOD, InstanceContentType.fromModrinthType("unknown_type"))
    }
}
