package io.ezz.launcher.core.auth

import io.ezz.launcher.core.auth.microsoft.MicrosoftAuthService
import io.ezz.launcher.core.auth.microsoft.MicrosoftAuthState
import io.ezz.launcher.core.auth.offline.OfflineAuthService
import io.ezz.launcher.core.model.account.Account
import io.ezz.launcher.core.model.account.MicrosoftAccount
import io.ezz.launcher.core.model.account.OfflineAccount
import io.ezz.launcher.core.storage.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class AuthManager(
    private val accountRepository: AccountRepository,
    val microsoftAuthService: MicrosoftAuthService
) {
    suspend fun createOfflineAccount(username: String): OfflineAccount {
        val account = OfflineAuthService.createAccount(username)
        accountRepository.saveAccount(account, source = "AddOfflineAccount")
        accountRepository.selectAccount(account.id)
        return account
    }

    fun startMicrosoftLogin(windowHandle: Long? = null): Flow<MicrosoftAuthState> = flow {
        microsoftAuthService.login(windowHandle).collect { state ->
            if (state is MicrosoftAuthState.Success) {
                accountRepository.saveAccount(state.account, source = "MicrosoftLoginSuccess")
                accountRepository.selectAccount(state.account.id)
            }
            emit(state)
        }
    }

    suspend fun getValidSession(account: Account): Account {
        return when (account) {
            is OfflineAccount -> account
            is MicrosoftAccount -> {
                val silentResult = microsoftAuthService.silentLogin(account)
                if (silentResult.isSuccess) {
                    val refreshed = silentResult.getOrThrow()
                    accountRepository.saveAccount(refreshed, source = "TokenRefresh")
                    refreshed
                } else {
                    // Check if existing token still has validity remaining
                    val timeRemaining = account.expiresAt - System.currentTimeMillis()
                    if (timeRemaining > 60 * 1000L && account.mcAccessToken.isNotBlank()) {
                        account
                    } else {
                        throw IllegalStateException("Your Microsoft session expired. Please sign in again.")
                    }
                }
            }
        }
    }

    suspend fun removeAccount(accountId: String) {
        val account = accountRepository.getAccount(accountId)
        if (account is MicrosoftAccount) {
            microsoftAuthService.logout(account)
        }
        accountRepository.removeAccount(accountId)
    }
}
