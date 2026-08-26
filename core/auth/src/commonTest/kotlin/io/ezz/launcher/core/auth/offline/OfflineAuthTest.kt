package io.ezz.launcher.core.auth.offline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OfflineAuthTest {

    @Test
    fun testOfflineUuidGenerationIsDeterministic() {
        val account1 = OfflineAuthService.createAccount("Alex")
        val account2 = OfflineAuthService.createAccount("Alex")

        assertEquals(account1.uuid, account2.uuid)
        assertEquals("Alex", account1.username)
    }

    @Test
    fun testInvalidUsernamesRejected() {
        assertFailsWith<IllegalArgumentException> {
            OfflineAuthService.createAccount("")
        }
        assertFailsWith<IllegalArgumentException> {
            OfflineAuthService.createAccount("ab") // Too short
        }
        assertFailsWith<IllegalArgumentException> {
            OfflineAuthService.createAccount("invalid-name!") // Invalid characters
        }
    }
}
