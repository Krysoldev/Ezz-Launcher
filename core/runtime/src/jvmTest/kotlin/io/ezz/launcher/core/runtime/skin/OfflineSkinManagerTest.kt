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
    fun testSyncOfflineSkin_MetadataOnlyAndNoModInjected() {
        val instance = Instance(
            id = "test-instance-1",
            name = "Vanilla 1.21.4",
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

        OfflineSkinManager.syncOfflineSkin(
            instance = instance,
            account = account,
            skin = skin,
            pathProvider = pathProvider,
            fileSystem = fileSystem
        )

        val gameDir = pathProvider.getInstanceGameDirectory(instance.id)

        // Verify active skin metadata in .ezz/ for launcher tracking
        assertTrue(fileSystem.exists(gameDir.resolve(".ezz").resolve("active_skin.json")))

        // Verify NO Ezz mod or override jar was installed
        val modsDir = gameDir.resolve("mods")
        assertFalse(fileSystem.exists(modsDir.resolve("ezz_vault_skin.jar")), "Must NOT install Ezz skin mod into mods/")
        assertFalse(fileSystem.exists(gameDir.resolve(".ezz").resolve("vault_skin_override.jar")), "Must NOT install classpath override JAR")
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
