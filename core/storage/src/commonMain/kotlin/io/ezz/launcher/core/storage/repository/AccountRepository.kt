package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.model.account.Account
import kotlinx.coroutines.flow.StateFlow

interface AccountRepository {
    val accounts: StateFlow<List<Account>>
    val selectedAccount: StateFlow<Account?>
    suspend fun loadAll(): List<Account>
    suspend fun saveAccount(account: Account, source: String? = null)
    suspend fun removeAccount(id: String)
    suspend fun selectAccount(id: String?)
    suspend fun getAccount(id: String): Account?
}
