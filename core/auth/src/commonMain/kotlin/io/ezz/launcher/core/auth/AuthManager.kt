package io.ezz.launcher.core.auth

import io.ezz.launcher.core.auth.microsoft.MicrosoftAuthService
import io.ezz.launcher.core.auth.microsoft.MicrosoftLoginProgress
import io.ezz.launcher.core.auth.offline.OfflineAuthService
import io.ezz.launcher.core.model.account.Account
import io.ezz.launcher.core.model.account.AccountType
import io.ezz.launcher.core.model.account.MicrosoftAccount
import io.ezz.launcher.core.model.account.OfflineAccount
import io.ezz.launcher.core.storage.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class AuthManager(
    private val accountRepository: AccountRepository,
    private val microsoftAuthService: MicrosoftAuthService
) {
    suspend fun createOfflineAccount(username: String): OfflineAccount {
        val account = OfflineAuthService.createAccount(username)
        accountRepository.saveAccount(account)
        accountRepository.selectAccount(account.id)
        return account
    }

    fun startMicrosoftLogin(): Flow<MicrosoftLoginProgress> = flow {
        microsoftAuthService.startLoginFlow().collect { progress ->
            if (progress is MicrosoftLoginProgress.Success) {
                accountRepository.saveAccount(progress.account)
                accountRepository.selectAccount(progress.account.id)
            }
            emit(progress)
        }
    }

    suspend fun getValidSession(account: Account): Account {
        return when (account) {
            is OfflineAccount -> account
            is MicrosoftAccount -> {
                // If token is expiring in less than 5 minutes, refresh it
                val timeRemaining = account.expiresAt - System.currentTimeMillis()
                if (timeRemaining < 5 * 60 * 1000L) {
                    try {
                        val refreshed = microsoftAuthService.refreshToken(account)
                        accountRepository.saveAccount(refreshed)
                        refreshed
                    } catch (e: Exception) {
                        account
                    }
                } else {
                    account
                }
            }
        }
    }
}
