package io.ezz.launcher.core.runtime.skin

import io.ezz.launcher.core.model.account.MicrosoftAccount
import io.ezz.launcher.core.model.account.OfflineAccount
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.model.skin.SkinModelType
import io.ezz.launcher.core.model.skin.VaultSkin
import io.ezz.launcher.core.storage.path.DefaultPathProvider
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OfflineSkinInjectorTest {

    private lateinit var tempDir: File
    private lateinit var pathProvider: DefaultPathProvider
    private val fileSystem = FileSystem.SYSTEM

    @BeforeTest
    fun setUp() {
        tempDir = File.createTempFile("ezz_skin_inject_test", "").apply {
            delete()
            mkdirs()
        }
        pathProvider = DefaultPathProvider(tempDir.absolutePath.toPath())
        pathProvider.initializeDirectories(fileSystem)
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testApplyVaultSkinToOfflineInstance_IsolatedNamespace() {
        val instance = Instance(
            id = "test-instance-1",
            name = "Survival 1.21.4",
            minecraftVersion = "1.21.4",
            loaderType = LoaderType.VANILLA
        )

        val account = OfflineAccount(
            id = "offline-acc-1",
            username = "UnknownPixel_",
            uuid = "offline-uuid-1234"
        )

        val skin = VaultSkin(
            id = "skin-1",
            name = "PvP Skin",
            fileName = "skin-1.png",
            modelType = SkinModelType.STEVE
        )

        val dummyBytes = "REAL_MINECRAFT_PNG_SKIN_BYTES".toByteArray()

        val result = OfflineSkinInjector.applyVaultSkin(
            instance = instance,
            account = account,
            skin = skin,
            skinBytes = dummyBytes,
            pathProvider = pathProvider,
            fileSystem = fileSystem
        )

        assertTrue(result.applied)
        assertNotNull(result.overrideJarPath)
        assertTrue(fileSystem.exists(result.overrideJarPath!!))

        val gameDir = pathProvider.getInstanceGameDirectory(instance.id)

        // Verify active skin metadata and raw png
        assertTrue(fileSystem.exists(gameDir.resolve(".ezz").resolve("active_skin.json")))
        assertTrue(fileSystem.exists(gameDir.resolve(".ezz").resolve("active_skin.png")))

        // Verify Override JAR was created with isolated ezz namespace
        val jarFile = result.overrideJarPath!!.toFile()
        assertTrue(jarFile.exists() && jarFile.length() > 0)
        val jar = ZipFile(jarFile)
        assertTrue(jar.getEntry("pack.mcmeta") != null)
        assertTrue(jar.getEntry("assets/ezz/textures/skin.png") != null, "Skin must be in isolated ezz namespace")
        // Verify it NEVER overwrites vanilla steve.png globally
        assertTrue(jar.getEntry("assets/minecraft/textures/entity/player/wide/steve.png") == null, "Must NOT pollute vanilla textures")
        jar.close()
    }

    @Test
    fun testBypassOnlineAccount() {
        val instance = Instance(
            id = "test-instance-2",
            name = "Fabric 1.20.1",
            minecraftVersion = "1.20.1",
            loaderType = LoaderType.FABRIC
        )

        val account = MicrosoftAccount(
            id = "msa-1",
            username = "OfficialPlayer",
            uuid = "msa-uuid-5678",
            msaRefreshToken = "dummy_refresh_token",
            mcAccessToken = "valid_access_token",
            expiresAt = System.currentTimeMillis() + 3600000L
        )

        val skin = VaultSkin(
            id = "skin-1",
            name = "Hero Skin",
            fileName = "skin-1.png",
            modelType = SkinModelType.STEVE
        )

        val result = OfflineSkinInjector.applyVaultSkin(
            instance = instance,
            account = account,
            skin = skin,
            skinBytes = "PNG_BYTES".toByteArray(),
            pathProvider = pathProvider,
            fileSystem = fileSystem
        )

        assertFalse(result.applied, "Should not apply Vault skin to online account")
    }

    @Test
    fun testResolvePackFormatAcrossVersions() {
        assertEquals(46, OfflineSkinInjector.resolvePackFormat("1.21.4"))
        assertEquals(42, OfflineSkinInjector.resolvePackFormat("1.21.3"))
        assertEquals(34, OfflineSkinInjector.resolvePackFormat("1.21.1"))
        assertEquals(18, OfflineSkinInjector.resolvePackFormat("1.20.2"))
        assertEquals(15, OfflineSkinInjector.resolvePackFormat("1.20.1"))
        assertEquals(7, OfflineSkinInjector.resolvePackFormat("1.16.5"))
        assertEquals(3, OfflineSkinInjector.resolvePackFormat("1.12.2"))
        assertEquals(1, OfflineSkinInjector.resolvePackFormat("1.8.9"))
    }

    @Test
    fun testFabricModJarCreationAndOnlineCleanup() {
        val fabricInstance = Instance(
            id = "test-fabric-inst",
            name = "Fabric 1.21.1",
            minecraftVersion = "1.21.1",
            loaderType = LoaderType.FABRIC
        )

        val offlineAccount = OfflineAccount(
            id = "offline-fabric-acc",
            username = "KrysolDev",
            uuid = "offline-uuid-999"
        )

        val skin = VaultSkin(
            id = "skin-fabric",
            name = "My Skin (1)",
            fileName = "my_skin.png",
            modelType = SkinModelType.STEVE
        )

        val skinBytes = "REAL_FABRIC_SKIN_BYTES".toByteArray()

        val result = OfflineSkinInjector.applyVaultSkin(
            instance = fabricInstance,
            account = offlineAccount,
            skin = skin,
            skinBytes = skinBytes,
            pathProvider = pathProvider,
            fileSystem = fileSystem
        )

        assertTrue(result.applied)
        assertNotNull(result.fabricModJarPath)
        assertTrue(fileSystem.exists(result.fabricModJarPath!!))

        val modZip = ZipFile(result.fabricModJarPath!!.toFile())
        assertTrue(modZip.getEntry("fabric.mod.json") != null)
        assertTrue(modZip.getEntry("assets/ezz/textures/skin.png") != null, "Skin must be in isolated ezz namespace")
        assertTrue(modZip.getEntry("assets/minecraft/textures/entity/player/wide/steve.png") == null, "Must NOT pollute vanilla textures")
        modZip.close()

        val gameDir = pathProvider.getInstanceGameDirectory(fabricInstance.id)
        assertTrue(fileSystem.exists(gameDir.resolve(".ezz").resolve("active_skin.json")))
        assertTrue(fileSystem.exists(gameDir.resolve(".ezz").resolve("active_skin.png")))

        // Test Online account cleanup
        val onlineAccount = MicrosoftAccount(
            id = "online-acc",
            username = "OfficialDev",
            uuid = "online-uuid-1",
            msaRefreshToken = "rt",
            mcAccessToken = "at",
            expiresAt = System.currentTimeMillis() + 3600000L
        )

        val onlineResult = OfflineSkinInjector.applyVaultSkin(
            instance = fabricInstance,
            account = onlineAccount,
            skin = skin,
            skinBytes = skinBytes,
            pathProvider = pathProvider,
            fileSystem = fileSystem
        )

        assertFalse(onlineResult.applied)
        assertFalse(fileSystem.exists(result.fabricModJarPath!!), "Fabric mod jar should be removed for online accounts")
    }
}
