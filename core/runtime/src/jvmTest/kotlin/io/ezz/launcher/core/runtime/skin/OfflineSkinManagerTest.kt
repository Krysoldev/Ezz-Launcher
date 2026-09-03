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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineSkinManagerTest {

    private lateinit var tempDir: File
    private lateinit var pathProvider: DefaultPathProvider
    private val fileSystem = FileSystem.SYSTEM

    @BeforeTest
    fun setUp() {
        tempDir = File.createTempFile("ezz_skin_mgr_test", "").apply {
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
    fun testSyncOfflineSkin_ProfileKeyedCacheWithoutModInjection() {
        val instance = Instance(
            id = "test-instance-fabric",
            name = "Fabric 1.21.11",
            minecraftVersion = "1.21.11",
            loaderType = LoaderType.FABRIC
        )

        val account = OfflineAccount(
            id = "offline-acc-1",
            username = "UnknownPixel_",
            uuid = "offline-uuid-1234"
        )

        val skin = VaultSkin(
            id = "skin-1",
            name = "My Custom Skin",
            fileName = "my_skin.png",
            fileHash = "abc123hash",
            modelType = SkinModelType.STEVE
        )

        val dummyBytes = "RAW_MINECRAFT_PNG_SKIN_BYTES".toByteArray()

        OfflineSkinManager.syncOfflineSkin(
            instance = instance,
            account = account,
            skin = skin,
            skinBytes = dummyBytes,
            pathProvider = pathProvider,
            fileSystem = fileSystem
        )

        val profileCacheDir = pathProvider.cacheDirectory.resolve("profiles").resolve(account.uuid)

        // Verify profile-keyed cache created
        assertTrue(fileSystem.exists(profileCacheDir.resolve("profile.json")))
        assertTrue(fileSystem.exists(profileCacheDir.resolve("skins").resolve("abc123hash.png")))

        // Verify NO Ezz mod was installed into mods/
        val gameDir = pathProvider.getInstanceGameDirectory(instance.id)
        val modJar = gameDir.resolve("mods").resolve("ezz_vault_skin.jar")
        assertFalse(fileSystem.exists(modJar), "Must NOT install Ezz skin mod into mods/")
    }

    @Test
    fun testCleanupLegacySkinMod_PreservesUserMods() {
        val gameDir = tempDir.absolutePath.toPath().resolve("test_game_dir")
        val modsDir = gameDir.resolve("mods")
        val ezzDir = gameDir.resolve(".ezz")

        fileSystem.createDirectories(modsDir)
        fileSystem.createDirectories(ezzDir)

        val legacyMod = modsDir.resolve("ezz_vault_skin.jar")
        val legacyOverride = ezzDir.resolve("vault_skin_override.jar")
        val userMod = modsDir.resolve("sodium-fabric-0.5.8.jar")

        fileSystem.write(legacyMod) { writeUtf8("legacy_ezz_mod") }
        fileSystem.write(legacyOverride) { writeUtf8("legacy_override") }
        fileSystem.write(userMod) { writeUtf8("user_sodium_mod") }

        assertTrue(fileSystem.exists(legacyMod))
        assertTrue(fileSystem.exists(legacyOverride))
        assertTrue(fileSystem.exists(userMod))

        OfflineSkinManager.cleanupLegacySkinMod(gameDir, fileSystem)

        assertFalse(fileSystem.exists(legacyMod), "Legacy Ezz mod must be cleaned up")
        assertFalse(fileSystem.exists(legacyOverride), "Legacy override jar must be cleaned up")
        assertTrue(fileSystem.exists(userMod), "User mods must remain untouched")
    }
}
