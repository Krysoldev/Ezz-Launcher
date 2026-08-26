package io.ezz.launcher.core.runtime.skin

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
    fun testApplyVaultSkinToOfflineInstance() {
        val instance = Instance(
            id = "test-instance-1",
            name = "Survival 1.21.4",
            minecraftVersion = "1.21.4",
            loaderType = LoaderType.VANILLA
        )

        val account = OfflineAccount(
            id = "offline-acc-1",
            username = "TestPlayer",
            uuid = "offline-uuid-1234"
        )

        val skin = VaultSkin(
            id = "skin-1",
            name = "Hero Skin",
            fileName = "skin-1.png",
            modelType = SkinModelType.STEVE
        )

        val dummyBytes = "PNG_TEXTURE_DATA".toByteArray()

        val applied = OfflineSkinInjector.applyVaultSkin(
            instance = instance,
            account = account,
            skin = skin,
            skinBytes = dummyBytes,
            pathProvider = pathProvider,
            fileSystem = fileSystem
        )

        assertTrue(applied)

        // Verify resource pack was created
        val gameDir = pathProvider.getInstanceGameDirectory(instance.id)
        val skinPackDir = gameDir.resolve("resourcepacks").resolve("EzzVaultSkin")
        assertTrue(fileSystem.exists(skinPackDir.resolve("pack.mcmeta")))
        assertTrue(fileSystem.exists(skinPackDir.resolve("assets").resolve("minecraft").resolve("textures").resolve("entity").resolve("player").resolve("wide").resolve("steve.png")))

        // Verify options.txt contains EzzVaultSkin
        val optionsFile = gameDir.resolve("options.txt")
        assertTrue(fileSystem.exists(optionsFile))
        val optionsContent = fileSystem.read(optionsFile) { readUtf8() }
        assertTrue(optionsContent.contains("file/EzzVaultSkin"))
    }
}
