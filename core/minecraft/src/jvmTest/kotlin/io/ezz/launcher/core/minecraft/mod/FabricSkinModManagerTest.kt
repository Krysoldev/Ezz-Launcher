package io.ezz.launcher.core.minecraft.mod

import io.ezz.launcher.core.model.account.OfflineAccount
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.model.skin.SkinModelType
import io.ezz.launcher.core.model.skin.VaultSkin
import io.ezz.launcher.core.storage.path.DefaultPathProvider
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FabricSkinModManagerTest {

    private lateinit var tempDir: File
    private lateinit var pathProvider: DefaultPathProvider
    private val fileSystem = FileSystem.SYSTEM

    @BeforeTest
    fun setUp() {
        tempDir = File.createTempFile("fabric_skin_mod_test", "").apply {
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
    fun testResolveModEntry_VersionMatching() {
        val entry116 = FabricSkinModManager.resolveModEntry("1.16.5")
        assertNotNull(entry116)
        assertEquals("1.16", entry116.versionFamily)
        assertEquals("ezz-skin-mod-1.16.jar", entry116.jarName)

        val entry120 = FabricSkinModManager.resolveModEntry("1.20.1")
        assertNotNull(entry120)
        assertEquals("1.20", entry120.versionFamily)
        assertEquals("ezz-skin-mod-1.20.jar", entry120.jarName)

        val entry121 = FabricSkinModManager.resolveModEntry("1.21.1")
        assertNotNull(entry121)
        assertEquals("1.21", entry121.versionFamily)
        assertEquals("ezz-skin-mod-1.21.jar", entry121.jarName)
    }

    @Test
    fun testPrepareInstanceSkinMod_FabricInstanceWithActiveSkin() {
        val instance = Instance(
            id = "inst-fabric-1",
            name = "Fabric 1.20.1",
            minecraftVersion = "1.20.1",
            loaderType = LoaderType.FABRIC
        )

        val account = OfflineAccount(
            id = "acc-krysol",
            username = "KrysolDev",
            uuid = "offline-uuid-krysol"
        )

        val skin = VaultSkin(
            id = "skin-uuid-1",
            name = "Krysol Skin",
            fileName = "krysol.png",
            fileHash = "hash123krysol",
            modelType = SkinModelType.ALEX
        )

        val skinBytes = "DUMMY_PNG_TEXTURE_BYTES".toByteArray()

        val result = FabricSkinModManager.prepareInstanceSkinMod(
            instance = instance,
            account = account,
            skin = skin,
            skinBytes = skinBytes,
            pathProvider = pathProvider,
            fileSystem = fileSystem
        )

        assertTrue(result.isSuccess)

        val gameDir = pathProvider.getInstanceGameDirectory(instance.id)
        val configFile = gameDir.resolve("config").resolve("ezz-skin-config.json")
        val skinFile = gameDir.resolve("config").resolve("ezz-skin").resolve("skin.png")
        val modJar = gameDir.resolve("mods").resolve("ezz-skin-mod-1.20.jar")

        assertTrue(fileSystem.exists(configFile), "Config file must exist")
        assertTrue(fileSystem.exists(skinFile), "Skin PNG file must exist")
        assertTrue(fileSystem.exists(modJar), "Fabric skin mod JAR must be installed")

        // Verify config content
        val configContent = fileSystem.read(configFile) { readUtf8() }
        assertTrue(configContent.contains("\"enabled\": true"))
        assertTrue(configContent.contains("\"accountId\": \"acc-krysol\""))
        assertTrue(configContent.contains("\"skinHash\": \"hash123krysol\""))
        assertTrue(configContent.contains("\"model\": \"ALEX\""))

        // Verify JAR archive integrity
        val jarBytes = fileSystem.read(modJar) { readByteArray() }
        var hasFabricModJson = false
        var hasMixinsJson = false

        ZipInputStream(jarBytes.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "fabric.mod.json") hasFabricModJson = true
                if (entry.name == "ezzskin.mixins.json") hasMixinsJson = true
                entry = zis.nextEntry
            }
        }

        assertTrue(hasFabricModJson, "JAR must contain fabric.mod.json")
        assertTrue(hasMixinsJson, "JAR must contain ezzskin.mixins.json")
    }

    @Test
    fun testPrepareInstanceSkinMod_AccountWithoutSkinDefaultsToDisabled() {
        val instance = Instance(
            id = "inst-fabric-2",
            name = "Fabric 1.21.1",
            minecraftVersion = "1.21.1",
            loaderType = LoaderType.FABRIC
        )

        val account = OfflineAccount(
            id = "acc-unknown",
            username = "UnknownPixel_",
            uuid = "offline-uuid-unknown"
        )

        val result = FabricSkinModManager.prepareInstanceSkinMod(
            instance = instance,
            account = account,
            skin = null,
            skinBytes = null,
            pathProvider = pathProvider,
            fileSystem = fileSystem
        )

        assertTrue(result.isSuccess)

        val gameDir = pathProvider.getInstanceGameDirectory(instance.id)
        val configFile = gameDir.resolve("config").resolve("ezz-skin-config.json")
        assertTrue(fileSystem.exists(configFile))

        val configContent = fileSystem.read(configFile) { readUtf8() }
        assertTrue(configContent.contains("\"enabled\": false"))
        assertTrue(configContent.contains("\"accountId\": \"acc-unknown\""))
    }

    @Test
    fun testPrepareInstanceSkinMod_EzzSkinDisabledPreservesJarWithoutDeleting() {
        val instance = Instance(
            id = "inst-fabric-disabled-test",
            name = "Fabric 1.21.1",
            minecraftVersion = "1.21.1",
            loaderType = LoaderType.FABRIC,
            ezzSkinEnabled = false
        )

        val account = OfflineAccount(
            id = "acc-krysol",
            username = "KrysolDev",
            uuid = "offline-uuid-krysol"
        )

        val skin = VaultSkin(
            id = "skin-uuid-1",
            name = "Krysol Skin",
            fileName = "krysol.png",
            fileHash = "hash123krysol",
            modelType = SkinModelType.ALEX
        )

        val skinBytes = "DUMMY_PNG_TEXTURE_BYTES".toByteArray()

        val result = FabricSkinModManager.prepareInstanceSkinMod(
            instance = instance,
            account = account,
            skin = skin,
            skinBytes = skinBytes,
            pathProvider = pathProvider,
            fileSystem = fileSystem
        )

        assertTrue(result.isSuccess)

        val gameDir = pathProvider.getInstanceGameDirectory(instance.id)
        val configFile = gameDir.resolve("config").resolve("ezz-skin-config.json")
        val activeModJar = gameDir.resolve("mods").resolve("ezz-skin-mod-1.21.jar")
        val disabledModJar = gameDir.resolve("mods").resolve("ezz-skin-mod-1.21.jar.disabled")
        val backupModJar = gameDir.resolve("config").resolve("ezz-skin").resolve("ezz-skin-mod-1.21.jar")

        assertTrue(fileSystem.exists(configFile), "Config file must exist")
        assertTrue(!fileSystem.exists(activeModJar), "Active mod JAR must NOT be in mods/ when disabled")
        assertTrue(fileSystem.exists(disabledModJar), "Disabled mod JAR must exist in mods/ with .disabled extension")
        assertTrue(fileSystem.exists(backupModJar), "Backup mod JAR must be preserved in config/ezz-skin/")

        val configContent = fileSystem.read(configFile) { readUtf8() }
        assertTrue(configContent.contains("\"enabled\": false"), "Config must indicate mod is disabled")
    }

    @Test
    fun testPrepareInstanceSkinMod_ReenablingEzzSkinRestoresActiveJar() {
        val disabledInstance = Instance(
            id = "inst-fabric-toggle-test",
            name = "Fabric 1.21.1",
            minecraftVersion = "1.21.1",
            loaderType = LoaderType.FABRIC,
            ezzSkinEnabled = false
        )

        val account = OfflineAccount(
            id = "acc-krysol",
            username = "KrysolDev",
            uuid = "offline-uuid-krysol"
        )

        val skin = VaultSkin(
            id = "skin-uuid-1",
            name = "Krysol Skin",
            fileName = "krysol.png",
            fileHash = "hash123krysol",
            modelType = SkinModelType.ALEX
        )

        val skinBytes = "DUMMY_PNG_TEXTURE_BYTES".toByteArray()

        // 1. Prepare as DISABLED
        FabricSkinModManager.prepareInstanceSkinMod(
            instance = disabledInstance,
            account = account,
            skin = skin,
            skinBytes = skinBytes,
            pathProvider = pathProvider,
            fileSystem = fileSystem
        )

        val gameDir = pathProvider.getInstanceGameDirectory(disabledInstance.id)
        val activeModJar = gameDir.resolve("mods").resolve("ezz-skin-mod-1.21.jar")
        val disabledModJar = gameDir.resolve("mods").resolve("ezz-skin-mod-1.21.jar.disabled")

        assertTrue(!fileSystem.exists(activeModJar))
        assertTrue(fileSystem.exists(disabledModJar))

        // 2. Prepare as ENABLED (Toggle ON)
        val enabledInstance = disabledInstance.copy(ezzSkinEnabled = true)
        val enableResult = FabricSkinModManager.prepareInstanceSkinMod(
            instance = enabledInstance,
            account = account,
            skin = skin,
            skinBytes = skinBytes,
            pathProvider = pathProvider,
            fileSystem = fileSystem
        )

        assertTrue(enableResult.isSuccess)
        assertTrue(fileSystem.exists(activeModJar), "Active mod JAR must be restored when Ezz Skin is toggled ON")
        assertTrue(!fileSystem.exists(disabledModJar), "Disabled mod JAR must be cleaned up when active JAR restored")
    }

    @Test
    fun testPrepareInstanceSkinMod_MultiInstanceIndependence() {
        val instA = Instance(
            id = "inst-a-on",
            name = "Instance A",
            minecraftVersion = "1.21.1",
            loaderType = LoaderType.FABRIC,
            ezzSkinEnabled = true
        )
        val instB = Instance(
            id = "inst-b-off",
            name = "Instance B",
            minecraftVersion = "1.21.1",
            loaderType = LoaderType.FABRIC,
            ezzSkinEnabled = false
        )

        val account = OfflineAccount(
            id = "acc-krysol",
            username = "KrysolDev",
            uuid = "offline-uuid-krysol"
        )

        FabricSkinModManager.prepareInstanceSkinMod(
            instance = instA,
            account = account,
            skin = null,
            skinBytes = null,
            pathProvider = pathProvider,
            fileSystem = fileSystem
        )

        FabricSkinModManager.prepareInstanceSkinMod(
            instance = instB,
            account = account,
            skin = null,
            skinBytes = null,
            pathProvider = pathProvider,
            fileSystem = fileSystem
        )

        val gameDirA = pathProvider.getInstanceGameDirectory(instA.id)
        val gameDirB = pathProvider.getInstanceGameDirectory(instB.id)

        val jarA = gameDirA.resolve("mods").resolve("ezz-skin-mod-1.21.jar")
        val jarB = gameDirB.resolve("mods").resolve("ezz-skin-mod-1.21.jar")
        val jarBDisabled = gameDirB.resolve("mods").resolve("ezz-skin-mod-1.21.jar.disabled")

        assertTrue(fileSystem.exists(jarA), "Instance A must have active JAR")
        assertTrue(!fileSystem.exists(jarB), "Instance B must NOT have active JAR")
        assertTrue(fileSystem.exists(jarBDisabled), "Instance B must have .disabled JAR preserved")
    }
}
