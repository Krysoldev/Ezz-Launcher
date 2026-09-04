package io.ezz.launcher.core.runtime.discord

import io.ezz.launcher.core.model.account.AccountType
import io.ezz.launcher.core.model.account.MicrosoftAccount
import io.ezz.launcher.core.model.account.OfflineAccount
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DiscordRpcLifecycleTest {

    @Test
    fun testFullRpcLifecycleWithLiveDiscord() = runBlocking {
        val service = DiscordRpcService()

        val krysolAccount = MicrosoftAccount(
            id = "ad17221c781d4ec5aca6f5069fbced7b",
            username = "KrysolDev",
            uuid = "ad17221c781d4ec5aca6f5069fbced7b",
            msaRefreshToken = "",
            mcAccessToken = "",
            expiresAt = 0L,
            avatarUrl = null
        )

        val nyxAccount = OfflineAccount(
            id = "offline_712d0e27-85de-3732-8672-485e9c21361e",
            username = "NyxKrishna",
            uuid = "712d0e27-85de-3732-8672-485e9c21361e",
            avatarUrl = null
        )

        // 1. Initialize launcher presence on startup
        service.initialize(account = krysolAccount, enabled = true)
        delay(1000L) // Wait for auto-connect and send

        // Verify avatar resolution
        val krysolAvatar = service.resolveAccountAvatarUrl(krysolAccount)
        assertEquals("https://minotar.net/helm/ad17221c781d4ec5aca6f5069fbced7b/128.png", krysolAvatar)

        // 2. Start Minecraft 1.21.11
        service.setMinecraftPresence(
            playerUsername = krysolAccount.username,
            minecraftVersion = "1.21.11",
            instanceName = "Default",
            playerUuid = krysolAccount.uuid,
            avatarUrl = krysolAvatar,
            startedAtMs = System.currentTimeMillis(),
            processId = 99999L
        )
        delay(500L)

        // 3. Minecraft exits -> Launcher presence restored
        service.onMinecraftExited(processId = 99999L)
        delay(500L)

        // 4. Switch active account to NyxKrishna
        service.setLauncherPresence(account = nyxAccount)
        delay(500L)
        val nyxAvatar = service.resolveAccountAvatarUrl(nyxAccount)
        assertEquals("https://minotar.net/helm/NyxKrishna/128.png", nyxAvatar)

        // 5. Toggle Discord RPC OFF
        service.setEnabled(false)
        delay(500L)
        assertEquals(false, service.isEnabled)

        // 6. Toggle Discord RPC ON
        service.setEnabled(true)
        delay(500L)
        assertEquals(true, service.isEnabled)

        // Cleanup
        service.clearActivity(disconnect = true)
        delay(500L)
    }
}
