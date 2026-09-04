package io.ezz.launcher.core.auth.admin

import io.ezz.launcher.core.model.account.MicrosoftAccount
import io.ezz.launcher.core.model.account.OfflineAccount
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminAuthorizationServiceTest {

    private val krysolDevAccount = MicrosoftAccount(
        id = "ad17221c781d4ec5aca6f5069fbced7b",
        username = "KrysolDev",
        uuid = "ad17221c781d4ec5aca6f5069fbced7b",
        msaRefreshToken = "",
        mcAccessToken = "",
        expiresAt = 0L
    )

    private val otherMicrosoftAccount = MicrosoftAccount(
        id = "11111111-2222-3333-4444-555555555555",
        username = "SteveGamer",
        uuid = "11111111-2222-3333-4444-555555555555",
        msaRefreshToken = "",
        mcAccessToken = "",
        expiresAt = 0L
    )

    private val offlineSpoofAccount = OfflineAccount(
        id = "offline-krysol",
        username = "KrysolDev",
        uuid = "ad17221c781d4ec5aca6f5069fbced7b"
    )

    private val regularOfflineAccount = OfflineAccount(
        id = "offline-alex",
        username = "Alex",
        uuid = "00000000-0000-0000-0000-000000000000"
    )

    @Test
    fun testNullAccount_returnsNormalUser() = runBlocking {
        val service = AdminAuthorizationService()
        val status = service.verifyAdminStatus(null)

        assertTrue(status is AdminStatus.NormalUser)
        assertFalse(status.isAuthorizedAdmin)
    }

    @Test
    fun testOfflineAccountSpoofingKrysolDev_isDeniedAdmin() = runBlocking {
        val service = AdminAuthorizationService()
        val status = service.verifyAdminStatus(offlineSpoofAccount)

        assertTrue(status is AdminStatus.NormalUser)
        assertFalse(status.isAuthorizedAdmin)
        assertFalse(status.microsoftConnected)
        assertEquals("KrysolDev", status.minecraftUsername)
    }

    @Test
    fun testRegularOfflineAccount_isDeniedAdmin() = runBlocking {
        val service = AdminAuthorizationService()
        val status = service.verifyAdminStatus(regularOfflineAccount)

        assertTrue(status is AdminStatus.NormalUser)
        assertFalse(status.isAuthorizedAdmin)
        assertFalse(status.microsoftConnected)
    }

    @Test
    fun testNormalMicrosoftAccount_isDeniedAdmin() = runBlocking {
        val service = AdminAuthorizationService()
        val status = service.verifyAdminStatus(otherMicrosoftAccount)

        assertTrue(status is AdminStatus.NormalUser)
        assertFalse(status.isAuthorizedAdmin)
        assertTrue(status.microsoftConnected)
        assertEquals("SteveGamer", status.minecraftUsername)
    }

    @Test
    fun testKrysolDevMicrosoftAccount_isAuthorizedAdmin() = runBlocking {
        val service = AdminAuthorizationService()
        val status = service.verifyAdminStatus(krysolDevAccount)

        assertTrue(status is AdminStatus.VerifiedAdmin)
        assertTrue(status.isAuthorizedAdmin)
        assertEquals("KrysolDev", status.minecraftUsername)
        assertEquals("ad17221c781d4ec5aca6f5069fbced7b", status.minecraftUuid)
    }

    @Test
    fun testAccountSwitchingSequence_preservesStrictAccessControl() = runBlocking {
        val service = AdminAuthorizationService()

        // 1. Login as KrysolDev -> ADMIN VERIFIED
        val step1 = service.verifyAdminStatus(krysolDevAccount)
        assertTrue(step1 is AdminStatus.VerifiedAdmin)
        assertTrue(service.adminStatus.value.isAuthorizedAdmin)

        // 2. Logout (null) -> NOT AUTHORIZED
        val step2 = service.verifyAdminStatus(null)
        assertTrue(step2 is AdminStatus.NormalUser)
        assertFalse(service.adminStatus.value.isAuthorizedAdmin)

        // 3. Login as another Microsoft account -> NOT AUTHORIZED
        val step3 = service.verifyAdminStatus(otherMicrosoftAccount)
        assertTrue(step3 is AdminStatus.NormalUser)
        assertFalse(service.adminStatus.value.isAuthorizedAdmin)

        // 4. Logout (null) -> NOT AUTHORIZED
        val step4 = service.verifyAdminStatus(null)
        assertTrue(step4 is AdminStatus.NormalUser)
        assertFalse(service.adminStatus.value.isAuthorizedAdmin)

        // 5. Login again as KrysolDev -> ADMIN VERIFIED again
        val step5 = service.verifyAdminStatus(krysolDevAccount)
        assertTrue(step5 is AdminStatus.VerifiedAdmin)
        assertTrue(service.adminStatus.value.isAuthorizedAdmin)
    }
}
