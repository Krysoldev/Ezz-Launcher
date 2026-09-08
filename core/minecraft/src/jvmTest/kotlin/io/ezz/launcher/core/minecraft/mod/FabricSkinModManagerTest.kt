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
import kotlin.test.assertNull
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

    @Test
    fun testAllSupportedVersionsResolutionAndBytecodeCompatibility() {
        val testMatrix = listOf(
            // 1.16.x family (Java 8 target)
            "1.16" to 8,
            "1.16.1" to 8,
            "1.16.2" to 8,
            "1.16.3" to 8,
            "1.16.4" to 8,
            "1.16.5" to 8,

            // 1.17.x family (Java 16/17 target)
            "1.17" to 17,
            "1.17.1" to 17,

            // 1.18.x family (Java 17 target)
            "1.18" to 17,
            "1.18.1" to 17,
            "1.18.2" to 17,

            // 1.19.x family (Java 17 target)
            "1.19" to 17,
            "1.19.1" to 17,
            "1.19.2" to 17,
            "1.19.3" to 17,
            "1.19.4" to 17,

            // 1.20.x family (Java 17/21 target)
            "1.20" to 17,
            "1.20.1" to 17,
            "1.20.2" to 17,
            "1.20.3" to 17,
            "1.20.4" to 17,
            "1.20.5" to 21,
            "1.20.6" to 21,

            // 1.21.x family (Java 21 target)
            "1.21" to 21,
            "1.21.1" to 21,
            "1.21.2" to 21,
            "1.21.3" to 21,
            "1.21.4" to 21,
            "1.21.11" to 21,

            // 1.26.x / 26.x family (Java 21/26 target)
            "1.26" to 21,
            "1.26.1" to 21,
            "1.26.2" to 21,
            "26.1" to 21,
            "26.2" to 21,
            "26.3" to 21
        )

        val account = OfflineAccount(
            id = "acc-krysol",
            username = "KrysolDev",
            uuid = "11111111-2222-3333-4444-555555555555"
        )
        val skin = VaultSkin(
            id = "skin-matrix-1",
            name = "Matrix Test Skin",
            fileName = "matrix.png",
            fileHash = "matrix_hash_xyz",
            modelType = SkinModelType.STEVE
        )
        val skinBytes = "VALID_PNG_RAW_BYTES".toByteArray()

        for ((mcVersion, javaRuntimeVer) in testMatrix) {
            val entry = FabricSkinModManager.resolveModEntry(mcVersion)
            assertNotNull(entry, "ModVersionEntry must resolve for Minecraft $mcVersion")

            val instance = Instance(
                id = "inst-test-${mcVersion.replace('.', '-')}",
                name = "Test $mcVersion",
                minecraftVersion = mcVersion,
                loaderType = LoaderType.FABRIC,
                ezzSkinEnabled = true
            )

            val prepResult = FabricSkinModManager.prepareInstanceSkinMod(
                instance = instance,
                account = account,
                skin = skin,
                skinBytes = skinBytes,
                pathProvider = pathProvider,
                fileSystem = fileSystem
            )
            assertTrue(prepResult.isSuccess, "Preparation must succeed for $mcVersion")

            val gameDir = pathProvider.getInstanceGameDirectory(instance.id)
            val stagedJar = gameDir.resolve("mods").resolve(entry.jarName)
            assertTrue(fileSystem.exists(stagedJar), "Staged JAR ${entry.jarName} must exist for $mcVersion")

            val jarBytes = fileSystem.read(stagedJar) { readByteArray() }
            val compatResult = ModBytecodeValidator.validateJarBytes(
                modName = entry.jarName,
                jarBytes = jarBytes,
                javaMajorVersion = javaRuntimeVer
            )

            assertTrue(
                compatResult is ModCompatibilityResult.Compatible,
                "JAR ${entry.jarName} for Minecraft $mcVersion must be compatible with Java $javaRuntimeVer (was $compatResult)"
            )
        }
    }

    @Test
    fun testSkinModelSwitchingClassicVsSlim() {
        val instance = Instance(
            id = "inst-model-switch",
            name = "Model Switch Instance",
            minecraftVersion = "1.20.1",
            loaderType = LoaderType.FABRIC,
            ezzSkinEnabled = true
        )
        val account = OfflineAccount(
            id = "acc-krysol",
            username = "KrysolDev",
            uuid = "11111111-2222-3333-4444-555555555555"
        )

        // 1. Classic (STEVE)
        val steveSkin = VaultSkin(
            id = "skin-steve",
            name = "Steve Skin",
            fileName = "steve.png",
            fileHash = "steve_hash",
            modelType = SkinModelType.STEVE
        )
        FabricSkinModManager.prepareInstanceSkinMod(
            instance = instance,
            account = account,
            skin = steveSkin,
            skinBytes = "STEVE_BYTES".toByteArray(),
            pathProvider = pathProvider,
            fileSystem = fileSystem
        )

        val gameDir = pathProvider.getInstanceGameDirectory(instance.id)
        val configFile = gameDir.resolve("config").resolve("ezz-skin-config.json")
        var configJson = fileSystem.read(configFile) { readUtf8() }
        assertTrue(configJson.contains("\"model\": \"STEVE\""), "Config must specify STEVE model")

        // 2. Slim (ALEX)
        val alexSkin = VaultSkin(
            id = "skin-alex",
            name = "Alex Skin",
            fileName = "alex.png",
            fileHash = "alex_hash",
            modelType = SkinModelType.ALEX
        )
        FabricSkinModManager.prepareInstanceSkinMod(
            instance = instance,
            account = account,
            skin = alexSkin,
            skinBytes = "ALEX_BYTES".toByteArray(),
            pathProvider = pathProvider,
            fileSystem = fileSystem
        )

        configJson = fileSystem.read(configFile) { readUtf8() }
        assertTrue(configJson.contains("\"model\": \"ALEX\""), "Config must specify ALEX model after switch")
    }

    @Test
    fun testMultiplayerIsolationRule_PreservesRemotePlayers() {
        val localUuid = java.util.UUID.fromString("11111111-2222-3333-4444-555555555555")
        val localUsername = "KrysolDev"

        val remoteUuid = java.util.UUID.fromString("99999999-8888-7777-6666-555555555555")
        val remoteUsername = "RemoteFriend"

        // Set up test config in temp directory
        val runDir = tempDir.resolve("multiplayer_test")
        runDir.mkdirs()
        val configDir = runDir.resolve("config").apply { mkdirs() }
        val ezzSkinDir = configDir.resolve("ezz-skin").apply { mkdirs() }
        val skinFile = ezzSkinDir.resolve("skin.png")
        skinFile.writeBytes("DUMMY_SKIN_PNG".toByteArray())

        val configFile = configDir.resolve("ezz-skin-config.json")
        configFile.writeText(
            """
            {
                "enabled": true,
                "username": "$localUsername",
                "uuid": "$localUuid",
                "accountId": "acc-1",
                "skinId": "skin-1",
                "skinHash": "hash1",
                "model": "STEVE",
                "skinFile": "config/ezz-skin/skin.png"
            }
            """.trimIndent()
        )

        // Extract the packaged 1.21 JAR to test directly with dynamic ClassLoader
        val entry = FabricSkinModManager.resolveModEntry("1.21.4")
        assertNotNull(entry, "Entry for 1.21.4 must exist")
        val jarBytes = FabricSkinModManager.getModJarBytes(entry)
        val jarFile = tempDir.resolve("ezz-skin-mod-1.21.jar")
        jarFile.writeBytes(jarBytes)

        val classLoader = java.net.URLClassLoader(arrayOf(jarFile.toURI().toURL()), javaClass.classLoader)
        try {
            val commonClass = classLoader.loadClass("io.ezz.skinmod.common.EzzSkinModCommon")
            val providerClass = classLoader.loadClass("io.ezz.skinmod.common.EzzSkinTextureProvider")

            // Initialize EzzSkinModCommon with this config
            commonClass.getMethod("init", java.io.File::class.java).invoke(null, runDir)
            providerClass.getMethod("initLocalPlayerIdentity").invoke(null)

            val isLocalPlayerMethod = providerClass.getMethod("isLocalPlayer", Any::class.java)
            val getCustomSkinTextureMethod = providerClass.getMethod("getCustomSkinTexture", Any::class.java)
            val getCustomModelMethod = providerClass.getMethod("getCustomModel", Any::class.java)

            // 1. Local player verification
            val isLocalUuid = isLocalPlayerMethod.invoke(null, localUuid) as Boolean
            val isLocalName = isLocalPlayerMethod.invoke(null, localUsername) as Boolean
            assertTrue(isLocalUuid, "Local UUID must be recognized as local player")
            assertTrue(isLocalName, "Local username must be recognized as local player")

            // 2. Remote player isolation verification (Multiplayer & SkinsRestorer rule)
            val isRemoteUuid = isLocalPlayerMethod.invoke(null, remoteUuid) as Boolean
            val isRemoteName = isLocalPlayerMethod.invoke(null, remoteUsername) as Boolean
            assertTrue(!isRemoteUuid, "Remote player UUID must NOT be recognized as local player")
            assertTrue(!isRemoteName, "Remote player username must NOT be recognized as local player")

            // Remote players must return null, guaranteeing no global texture overwrite!
            assertNull(
                getCustomSkinTextureMethod.invoke(null, remoteUuid),
                "Remote player skin texture must be null to preserve SkinsRestorer/server skin"
            )
            assertNull(
                getCustomModelMethod.invoke(null, remoteUuid),
                "Remote player model must be null to preserve server model"
            )
        } finally {
            classLoader.close()
        }
    }
}
