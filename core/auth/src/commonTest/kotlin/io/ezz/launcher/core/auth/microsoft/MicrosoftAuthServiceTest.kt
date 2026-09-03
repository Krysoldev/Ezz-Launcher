package io.ezz.launcher.core.auth.microsoft

import io.ezz.launcher.core.model.account.AccountType
import io.ezz.launcher.core.model.account.MicrosoftAccount
import io.ezz.launcher.core.model.account.OfflineAccount
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MicrosoftAuthServiceTest {

    @Test
    fun testBrokerIntegration() {
        val broker = com.microsoft.aad.msal4jbrokers.Broker.Builder().supportWindows(true).build()
        assertNotNull(broker, "Broker should instantiate")

        val uri = java.net.URI("ms-appx-web://Microsoft.AAD.BrokerPlugin/074d6e3a-87dc-4d22-a3d7-0bde23144b0c")
        val params = com.microsoft.aad.msal4j.InteractiveRequestParameters.builder(uri)
            .scopes(setOf("XboxLive.signin"))
            .windowHandle(12345L)
            .build()
        assertNotNull(params)

        val pca = com.microsoft.aad.msal4j.PublicClientApplication.builder("074d6e3a-87dc-4d22-a3d7-0bde23144b0c")
            .authority("https://login.microsoftonline.com/consumers/")
            .broker(broker)
            .build()
        assertNotNull(pca)

        val future = broker.acquireToken(pca, params)
        assertNotNull(future, "broker.acquireToken should return a non-null future without throwing redirect URI error")
    }

    @Test
    fun testSilentLoginWithValidToken() = runBlocking {
        val service = MicrosoftAuthService(HttpClient())
        val validAccount = MicrosoftAccount(
            id = "test-ms-id",
            username = "TestPlayer",
            uuid = "12345678-1234-1234-1234-123456789012",
            msaRefreshToken = "",
            mcAccessToken = "valid_cached_token",
            expiresAt = System.currentTimeMillis() + (60 * 60 * 1000L), // 1 hour remaining
            msalAccountId = "msal-home-account-id"
        )

        val result = service.silentLogin(validAccount)
        assertTrue(result.isSuccess, "Valid account with non-expired token should return success silently")
        assertEquals("TestPlayer", result.getOrThrow().username)
        assertEquals("valid_cached_token", result.getOrThrow().mcAccessToken)
    }

    @Test
    fun testOfflineAccountPreservation() {
        val offline = OfflineAccount(
            id = "offline-id-1",
            username = "OfflineSteve",
            uuid = "deterministic-uuid-steve"
        )

        assertEquals(AccountType.OFFLINE, offline.type)
        assertEquals("OfflineSteve", offline.username)
        assertFalse(offline.id.isBlank())
    }

    @Test
    fun testErrorMappingJavaNotFound() {
        val exception = MinecraftJavaNotFoundException("Microsoft account connected, but Minecraft Java Edition was not found on this account.")
        assertEquals("Microsoft account connected, but Minecraft Java Edition was not found on this account.", exception.message)
    }

    @Test
    fun testWindowsHwndResolverHandlesZero() {
        val normalized = WindowsHwndResolver.normalizeAndValidate(0L, "test")
        assertEquals(null, normalized, "Zero HWND should not be normalized as valid")
    }

    @Test
    fun testMicrosoftAuthStateProgression() {
        val connecting = MicrosoftAuthState.ConnectingToMicrosoft
        val waiting = MicrosoftAuthState.WaitingForMicrosoft
        val signingIn = MicrosoftAuthState.SigningIn
        val completing = MicrosoftAuthState.CompletingMinecraftAuth("Completing Minecraft authentication...")
        val cancelled = MicrosoftAuthState.Cancelled

        assertNotNull(connecting)
        assertNotNull(waiting)
        assertNotNull(signingIn)
        assertEquals("Completing Minecraft authentication...", completing.message)
        assertEquals(MicrosoftAuthState.Cancelled, cancelled)
    }
}
