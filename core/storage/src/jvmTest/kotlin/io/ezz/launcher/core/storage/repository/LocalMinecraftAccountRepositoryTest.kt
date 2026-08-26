package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.model.account.MicrosoftAccount
import io.ezz.launcher.core.model.account.OfflineAccount
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalMinecraftAccountRepositoryTest {

    private lateinit var tempDir: File
    private lateinit var pathProvider: DefaultPathProvider
    private lateinit var secureVault: EncryptedFileVault
    private lateinit var repository: LocalMinecraftAccountRepository

    @BeforeTest
    fun setUp() {
        tempDir = File.createTempFile("ezz_acc_test", "").apply {
            delete()
            mkdirs()
        }
        pathProvider = DefaultPathProvider(tempDir.absolutePath.toPath())
        pathProvider.initializeDirectories()
        secureVault = EncryptedFileVault(pathProvider.rootDirectory.resolve("vault.dat"))
        repository = LocalMinecraftAccountRepository(pathProvider, secureVault)
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testAddMultipleAccountsAndPersistAcrossRestart() = runBlocking {
        // 1. Add Offline Account
        val offlineAcc = OfflineAccount(
            id = "acc-offline-1",
            username = "UnknownPixel_",
            uuid = "uuid-unknown"
        )
        repository.saveAccount(offlineAcc)

        // 2. Add Microsoft Account
        val msAcc = MicrosoftAccount(
            id = "acc-ms-1",
            username = "KrysolDev",
            uuid = "uuid-krysol",
            msaRefreshToken = "refresh_token_sample",
            mcAccessToken = "access_token_sample",
            expiresAt = System.currentTimeMillis() + 3600000L,
            skinUrl = "https://textures.minecraft.net/texture/sample"
        )
        repository.saveAccount(msAcc)

        // 3. Add third account
        val steve = OfflineAccount(
            id = "acc-offline-2",
            username = "Steve",
            uuid = "uuid-steve"
        )
        repository.saveAccount(steve)

        assertEquals(3, repository.accounts.value.size)

        // 4. Select KrysolDev
        repository.selectAccount(msAcc.id)
        assertEquals("KrysolDev", repository.selectedAccount.value?.username)

        // 5. Select UnknownPixel_ -> KrysolDev must remain intact
        repository.selectAccount(offlineAcc.id)
        assertEquals("UnknownPixel_", repository.selectedAccount.value?.username)
        assertEquals(3, repository.accounts.value.size)
        assertTrue(repository.accounts.value.any { it.username == "KrysolDev" })
        assertTrue(repository.accounts.value.any { it.username == "UnknownPixel_" })
        assertTrue(repository.accounts.value.any { it.username == "Steve" })

        // 6. Simulate Complete Launcher Restart
        val freshRepo = LocalMinecraftAccountRepository(pathProvider, secureVault)
        val loaded = freshRepo.loadAll()

        assertEquals(3, loaded.size)
        assertEquals("UnknownPixel_", freshRepo.selectedAccount.value?.username)

        // Verify Microsoft tokens persisted in secure vault
        val loadedMsAcc = freshRepo.getAccount(msAcc.id) as? MicrosoftAccount
        assertNotNull(loadedMsAcc)
        assertEquals("KrysolDev", loadedMsAcc.username)
        assertEquals("refresh_token_sample", loadedMsAcc.msaRefreshToken)
        assertEquals("access_token_sample", loadedMsAcc.mcAccessToken)
        assertEquals("https://textures.minecraft.net/texture/sample", loadedMsAcc.skinUrl)
    }

    @Test
    fun testAccountLaunchCycleDoesNotWipeAccounts() = runBlocking {
        val acc1 = OfflineAccount(id = "1", username = "Player1", uuid = "u1")
        val acc2 = OfflineAccount(id = "2", username = "Player2", uuid = "u2")

        repository.saveAccount(acc1)
        repository.saveAccount(acc2)
        assertEquals(2, repository.accounts.value.size)

        // Simulate re-saving active account on launch/token refresh
        val updatedAcc1 = acc1.copy(lastUsedAt = System.currentTimeMillis())
        repository.saveAccount(updatedAcc1)

        // Both accounts MUST remain
        val accounts = repository.loadAll()
        assertEquals(2, accounts.size)
        assertTrue(accounts.any { it.username == "Player1" })
        assertTrue(accounts.any { it.username == "Player2" })
    }

    @Test
    fun testRemoveAccountSelectsNext() = runBlocking {
        val acc1 = OfflineAccount(id = "1", username = "Player1", uuid = "u1")
        val acc2 = OfflineAccount(id = "2", username = "Player2", uuid = "u2")

        repository.saveAccount(acc1)
        repository.saveAccount(acc2)
        repository.selectAccount(acc1.id)

        repository.removeAccount(acc1.id)
        assertEquals(1, repository.accounts.value.size)
        assertEquals("Player2", repository.selectedAccount.value?.username)

        repository.removeAccount(acc2.id)
        assertEquals(0, repository.accounts.value.size)
        assertNull(repository.selectedAccount.value)
    }
}
