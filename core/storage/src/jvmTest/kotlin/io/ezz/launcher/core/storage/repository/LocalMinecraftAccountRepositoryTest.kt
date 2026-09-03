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

    @Test
    fun testOfflineAccountPersistenceAndAttributes() = runBlocking {
        val offlineAcc = io.ezz.launcher.core.model.account.OfflineAccount(
            id = "offline-acc-1",
            username = "KrysolDev",
            uuid = "12345678-1234-1234-1234-123456789abc",
            skinUrl = "https://storage.ezzlauncher.com/skins/krysol_custom.png",
            skinModel = "STEVE",
            skinHash = "sha256_krysol_custom"
        )

        repository.saveAccount(offlineAcc)
        repository.selectAccount(offlineAcc.id)

        // Restart simulation
        val freshRepo = LocalMinecraftAccountRepository(pathProvider, secureVault)
        val loaded = freshRepo.loadAll()

        assertEquals(1, loaded.size)
        val loadedAccount = loaded.first() as io.ezz.launcher.core.model.account.OfflineAccount
        assertEquals("KrysolDev", loadedAccount.username)
        assertEquals("12345678-1234-1234-1234-123456789abc", loadedAccount.uuid)
        assertEquals(io.ezz.launcher.core.model.account.AccountType.OFFLINE, loadedAccount.type)
        assertEquals("https://storage.ezzlauncher.com/skins/krysol_custom.png", loadedAccount.skinUrl)
        assertEquals("STEVE", loadedAccount.skinModel)
    }

    @Test
    fun testStartupWithNoAccountsNeverCreatesAccount() = runBlocking {
        // Startup on fresh repository with zero accounts
        val freshRepo = LocalMinecraftAccountRepository(pathProvider, secureVault)
        val loaded = freshRepo.loadAll()

        assertEquals(0, loaded.size)
        assertEquals(0, freshRepo.accounts.value.size)
        assertNull(freshRepo.selectedAccount.value)

        // Simulate 5 consecutive restarts
        for (i in 1..5) {
            val restartedRepo = LocalMinecraftAccountRepository(pathProvider, secureVault)
            val accounts = restartedRepo.loadAll()
            assertEquals(0, accounts.size, "Accounts must remain 0 after restart #$i")
            assertNull(restartedRepo.selectedAccount.value)
        }
    }

    @Test
    fun testDuplicateOfflineAccountUpdatesExistingRecord() = runBlocking {
        val acc1 = OfflineAccount(
            id = "offline_123",
            username = "TestPlayer",
            uuid = "uuid-test-player",
            createdAt = 1000L
        )
        repository.saveAccount(acc1, source = "AddOfflineAccount")
        assertEquals(1, repository.accounts.value.size)

        // Attempt to save another offline account with same username but different generated ID
        val acc2 = OfflineAccount(
            id = "offline_456",
            username = "TestPlayer",
            uuid = "uuid-test-player",
            avatarUrl = "https://minotar.net/avatar/TestPlayer/128.png",
            createdAt = 2000L
        )
        repository.saveAccount(acc2, source = "AddOfflineAccount")

        // Must NOT create a duplicate: should update existing record while preserving original id and createdAt
        assertEquals(1, repository.accounts.value.size)
        val updated = repository.accounts.value.first()
        assertEquals("offline_123", updated.id)
        assertEquals("TestPlayer", updated.username)
        assertEquals(1000L, updated.createdAt)
        assertEquals("https://minotar.net/avatar/TestPlayer/128.png", updated.avatarUrl)
    }

    @Test
    fun testDuplicateMicrosoftAccountUpdatesExistingRecord() = runBlocking {
        val msAcc1 = MicrosoftAccount(
            id = "ms_profile_uuid_1",
            username = "KrysolDev",
            uuid = "uuid-krysol-official",
            msaRefreshToken = "initial_refresh_token",
            mcAccessToken = "initial_access_token",
            expiresAt = 5000000L,
            createdAt = 1000L
        )
        repository.saveAccount(msAcc1, source = "MicrosoftLoginSuccess")
        assertEquals(1, repository.accounts.value.size)

        // User re-authenticates with Microsoft; new tokens and new random ID assigned
        val msAcc2 = MicrosoftAccount(
            id = "random_new_id_999",
            username = "KrysolDev",
            uuid = "uuid-krysol-official",
            msaRefreshToken = "updated_refresh_token",
            mcAccessToken = "updated_access_token",
            expiresAt = 9000000L,
            createdAt = 2000L
        )
        repository.saveAccount(msAcc2, source = "MicrosoftLoginSuccess")

        // Must update existing record and not duplicate
        assertEquals(1, repository.accounts.value.size)
        val updated = repository.accounts.value.first() as MicrosoftAccount
        assertEquals("ms_profile_uuid_1", updated.id)
        assertEquals("KrysolDev", updated.username)
        assertEquals("uuid-krysol-official", updated.uuid)
        assertEquals(1000L, updated.createdAt)
        assertEquals("updated_refresh_token", updated.msaRefreshToken)
        assertEquals("updated_access_token", updated.mcAccessToken)
        assertEquals(9000000L, updated.expiresAt)
    }

    @Test
    fun testMultipleRestartsMaintainExactAccountCount() = runBlocking {
        val acc1 = OfflineAccount(id = "acc1", username = "KrysolDev", uuid = "u1")
        val acc2 = OfflineAccount(id = "acc2", username = "UnknownPixel_", uuid = "u2")
        repository.saveAccount(acc1)
        repository.saveAccount(acc2)
        repository.selectAccount(acc1.id)

        assertEquals(2, repository.accounts.value.size)
        assertEquals("KrysolDev", repository.selectedAccount.value?.username)

        // Simulate 10 restarts
        for (i in 1..10) {
            val restarted = LocalMinecraftAccountRepository(pathProvider, secureVault)
            val accounts = restarted.loadAll()
            assertEquals(2, accounts.size, "Restart #$i must have exactly 2 accounts")
            assertEquals("KrysolDev", restarted.selectedAccount.value?.username, "Restart #$i must preserve selected account")
        }
    }

    @Test
    fun testDeletedAccountsNeverResurrectOnRestart() = runBlocking {
        val acc = OfflineAccount(id = "acc-temp", username = "TempUser", uuid = "u-temp")
        repository.saveAccount(acc)
        assertEquals(1, repository.accounts.value.size)

        repository.removeAccount(acc.id)
        assertEquals(0, repository.accounts.value.size)

        // Restart
        val restarted = LocalMinecraftAccountRepository(pathProvider, secureVault)
        val accounts = restarted.loadAll()
        assertEquals(0, accounts.size)
        assertNull(restarted.selectedAccount.value)
    }
}
