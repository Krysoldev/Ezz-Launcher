package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.model.account.OfflineAccount
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.storage.path.DefaultPathProvider
import io.ezz.launcher.core.storage.vault.EncryptedFileVault
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AccountSwitchingInstancePersistenceTest {

    private lateinit var tempDir: File
    private lateinit var pathProvider: DefaultPathProvider
    private lateinit var secureVault: EncryptedFileVault
    private lateinit var instanceRepository: LocalInstanceRepository
    private lateinit var accountRepository: LocalMinecraftAccountRepository

    @BeforeTest
    fun setUp() {
        tempDir = File.createTempFile("ezz_arch_test", "").apply {
            delete()
            mkdirs()
        }
        pathProvider = DefaultPathProvider(tempDir.absolutePath.toPath())
        pathProvider.initializeDirectories()
        secureVault = EncryptedFileVault(pathProvider.rootDirectory.resolve("vault.dat"))

        instanceRepository = LocalInstanceRepository(pathProvider)
        accountRepository = LocalMinecraftAccountRepository(pathProvider, secureVault)
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testInstancesRemainVisibleWhenSwitchingAccounts() = runBlocking {
        // 1. Create Minecraft account: KrysolDev
        val krysolAccount = OfflineAccount(
            id = "acc-krysol",
            username = "KrysolDev",
            uuid = "uuid-krysol"
        )
        accountRepository.saveAccount(krysolAccount)
        accountRepository.selectAccount(krysolAccount.id)

        assertEquals("KrysolDev", accountRepository.selectedAccount.value?.username)

        // 2. Create Instances: Survival, SMP, PvP
        val survival = instanceRepository.createInstance(
            name = "Survival",
            minecraftVersion = "1.21.4",
            loaderType = LoaderType.FABRIC
        )
        val smp = instanceRepository.createInstance(
            name = "SMP",
            minecraftVersion = "1.20.4",
            loaderType = LoaderType.VANILLA
        )
        val pvp = instanceRepository.createInstance(
            name = "PvP",
            minecraftVersion = "1.8.9",
            loaderType = LoaderType.OPTIFINE
        )

        assertNotNull(survival.id)
        assertNotNull(smp.id)
        assertNotNull(pvp.id)
        assertEquals(3, instanceRepository.instances.value.size)
        assertEquals(3, instanceRepository.loadAll().size)

        // 3. Create second Minecraft account: UnknownPixel_ and switch to it
        val unknownAccount = OfflineAccount(
            id = "acc-unknown",
            username = "UnknownPixel_",
            uuid = "uuid-unknown"
        )
        accountRepository.saveAccount(unknownAccount)
        accountRepository.selectAccount(unknownAccount.id)

        // Verify account switched
        assertEquals("UnknownPixel_", accountRepository.selectedAccount.value?.username)

        // 4. CRITICAL CHECK: Instances MUST STILL BE VISIBLE and unchanged
        val instancesAfterSwitch = instanceRepository.instances.value
        assertEquals(3, instancesAfterSwitch.size)
        assertEquals(listOf("Survival", "SMP", "PvP"), instancesAfterSwitch.map { it.name })

        // 5. Switch back to KrysolDev
        accountRepository.selectAccount(krysolAccount.id)
        assertEquals("KrysolDev", accountRepository.selectedAccount.value?.username)

        val instancesAfterSwitchBack = instanceRepository.instances.value
        assertEquals(3, instancesAfterSwitchBack.size)
        assertEquals(listOf("Survival", "SMP", "PvP"), instancesAfterSwitchBack.map { it.name })

        // 6. Simulate Launcher Restart (re-instantiating repositories from disk)
        val freshInstanceRepo = LocalInstanceRepository(pathProvider)
        val freshAccountRepo = LocalMinecraftAccountRepository(pathProvider, secureVault)

        freshInstanceRepo.loadAll()
        freshAccountRepo.loadAll()

        assertEquals("KrysolDev", freshAccountRepo.selectedAccount.value?.username)
        val restartedInstances = freshInstanceRepo.instances.value
        assertEquals(3, restartedInstances.size)
        assertEquals(listOf("Survival", "SMP", "PvP"), restartedInstances.map { it.name })
    }
}
