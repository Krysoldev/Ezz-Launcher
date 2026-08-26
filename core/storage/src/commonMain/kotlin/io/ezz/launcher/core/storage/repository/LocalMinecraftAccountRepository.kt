package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.model.account.Account
import io.ezz.launcher.core.model.account.AccountType
import io.ezz.launcher.core.model.account.MicrosoftAccount
import io.ezz.launcher.core.model.account.OfflineAccount
import io.ezz.launcher.core.storage.vault.SecureVault
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class SavedAccountRecord(
    val id: String,
    val username: String,
    val uuid: String,
    val type: String,
    val avatarUrl: String? = null
)

/**
 * Local-First repository for Minecraft Accounts.
 * Manages accounts strictly on the local machine in encrypted storage.
 * Switching accounts modifies only the active launch account and NEVER touches local instances.
 */
class LocalMinecraftAccountRepository(
    private val secureVault: SecureVault,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : AccountRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = true
    }

    private val mutex = Mutex()
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    override val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _selectedAccount = MutableStateFlow<Account?>(null)
    override val selectedAccount: StateFlow<Account?> = _selectedAccount.asStateFlow()

    private suspend fun readLocalAccounts(): List<Account> {
        return try {
            val raw = secureVault.getString("local_saved_accounts") ?: return emptyList()
            val records = json.decodeFromString<List<SavedAccountRecord>>(raw)
            records.map { record ->
                if (record.type.equals("MICROSOFT", ignoreCase = true)) {
                    val msaRefresh = secureVault.getString("msa_refresh_${record.id}") ?: ""
                    val mcAccess = secureVault.getString("mc_access_${record.id}") ?: ""
                    val expiresAt = secureVault.getString("mc_expires_${record.id}")?.toLongOrNull() ?: 0L
                    MicrosoftAccount(
                        id = record.id,
                        username = record.username,
                        uuid = record.uuid,
                        avatarUrl = record.avatarUrl,
                        msaRefreshToken = msaRefresh,
                        mcAccessToken = mcAccess,
                        expiresAt = expiresAt
                    )
                } else {
                    OfflineAccount(
                        id = record.id,
                        username = record.username,
                        uuid = record.uuid,
                        avatarUrl = record.avatarUrl
                    )
                }
            }
        } catch (e: Exception) {
            println("Warning reading local accounts from vault: ${e.message}")
            emptyList()
        }
    }

    private suspend fun saveLocalAccounts(list: List<Account>) {
        try {
            val records = list.map { account ->
                SavedAccountRecord(
                    id = account.id,
                    username = account.username,
                    uuid = account.uuid,
                    type = account.type.name,
                    avatarUrl = account.avatarUrl
                )
            }
            secureVault.putString("local_saved_accounts", json.encodeToString(records))
        } catch (e: Exception) {
            println("Error saving accounts to vault: ${e.message}")
        }
    }

    override suspend fun loadAll(): List<Account> = withContext(dispatcher) {
        mutex.withLock {
            val loaded = readLocalAccounts()
            _accounts.value = loaded

            val selectedId = secureVault.getString("selected_account_id")
            _selectedAccount.value = loaded.find { it.id == selectedId } ?: loaded.firstOrNull()
            loaded
        }
    }

    override suspend fun getAccount(id: String): Account? = withContext(dispatcher) {
        _accounts.value.find { it.id == id } ?: readLocalAccounts().find { it.id == id }
    }

    override suspend fun saveAccount(account: Account): Unit = withContext(dispatcher) {
        mutex.withLock {
            if (account is MicrosoftAccount) {
                secureVault.putString("msa_refresh_${account.id}", account.msaRefreshToken)
                secureVault.putString("mc_access_${account.id}", account.mcAccessToken)
                secureVault.putString("mc_expires_${account.id}", account.expiresAt.toString())
            }

            val currentList = _accounts.value.filter { it.id != account.id } + account
            _accounts.value = currentList
            if (_selectedAccount.value == null || _selectedAccount.value?.id == account.id) {
                _selectedAccount.value = account
                secureVault.putString("selected_account_id", account.id)
            }
            saveLocalAccounts(currentList)
        }
    }

    override suspend fun selectAccount(id: String?): Unit = withContext(dispatcher) {
        mutex.withLock {
            if (id == null) {
                _selectedAccount.value = null
                secureVault.remove("selected_account_id")
                return@withLock
            }
            val account = getAccount(id) ?: throw IllegalArgumentException("Account not found: $id")
            _selectedAccount.value = account
            secureVault.putString("selected_account_id", account.id)
        }
    }

    override suspend fun removeAccount(id: String): Unit = withContext(dispatcher) {
        mutex.withLock {
            val updated = _accounts.value.filter { it.id != id }
            _accounts.value = updated
            if (_selectedAccount.value?.id == id) {
                _selectedAccount.value = updated.firstOrNull()
                if (_selectedAccount.value != null) {
                    secureVault.putString("selected_account_id", _selectedAccount.value!!.id)
                } else {
                    secureVault.remove("selected_account_id")
                }
            }
            saveLocalAccounts(updated)

            secureVault.remove("msa_refresh_$id")
            secureVault.remove("mc_access_$id")
            secureVault.remove("mc_expires_$id")
        }
    }
}
