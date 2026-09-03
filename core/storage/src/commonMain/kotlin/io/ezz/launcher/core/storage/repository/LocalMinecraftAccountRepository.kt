package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.model.account.Account
import io.ezz.launcher.core.model.account.AccountType
import io.ezz.launcher.core.model.account.MicrosoftAccount
import io.ezz.launcher.core.model.account.OfflineAccount
import io.ezz.launcher.core.storage.path.PathProvider
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
import okio.FileSystem
import okio.Path

@Serializable
private data class AccountMetadataRecord(
    val id: String,
    val username: String,
    val uuid: String,
    val type: AccountType,
    val avatarUrl: String? = null,
    val skinUrl: String? = null,
    val skinModel: String? = null,
    val skinHash: String? = null,
    val createdAt: Long = 0L,
    val lastUsedAt: Long? = null
)

@Serializable
private data class AccountsStoragePayload(
    val selectedAccountId: String? = null,
    val accounts: List<AccountMetadataRecord> = emptyList()
)

/**
 * Robust Local-First repository for Minecraft Accounts.
 * - Non-sensitive account metadata is persisted safely on disk in accounts.json.
 * - Sensitive Microsoft OAuth tokens are encrypted and managed in SecureVault.
 * - Accounts are completely local, survive restarts, launch exits, and are never wiped.
 */
class LocalMinecraftAccountRepository(
    private val pathProvider: PathProvider,
    private val secureVault: SecureVault,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
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

    private val accountsFile: Path get() = pathProvider.rootDirectory.resolve("accounts.json")

    init {
        val initialPayload = readPayloadFromDisk()
        val initialAccounts = initialPayload.accounts.map { record ->
            when (record.type) {
                AccountType.MICROSOFT -> {
                    MicrosoftAccount(
                        id = record.id,
                        username = record.username,
                        uuid = record.uuid,
                        msaRefreshToken = "",
                        mcAccessToken = "",
                        expiresAt = 0L,
                        avatarUrl = record.avatarUrl,
                        skinUrl = record.skinUrl,
                        skinModel = record.skinModel,
                        skinHash = record.skinHash,
                        createdAt = record.createdAt,
                        lastUsedAt = record.lastUsedAt
                    )
                }
                AccountType.OFFLINE -> {
                    OfflineAccount(
                        id = record.id,
                        username = record.username,
                        uuid = record.uuid,
                        createdAt = record.createdAt,
                        lastUsedAt = record.lastUsedAt,
                        avatarUrl = record.avatarUrl,
                        skinUrl = record.skinUrl,
                        skinModel = record.skinModel,
                        skinHash = record.skinHash
                    )
                }
            }
        }
        _accounts.value = initialAccounts
        val selected = initialAccounts.find { it.id == initialPayload.selectedAccountId } ?: initialAccounts.firstOrNull()
        _selectedAccount.value = selected
    }

    companion object {
        @Volatile
        var isStartupPhase: Boolean = false
    }

    private fun readPayloadFromDisk(): AccountsStoragePayload {
        return try {
            if (fileSystem.exists(accountsFile)) {
                val content = fileSystem.read(accountsFile) { readUtf8() }
                json.decodeFromString<AccountsStoragePayload>(content)
            } else {
                AccountsStoragePayload()
            }
        } catch (e: Exception) {
            println("Warning: failed to read accounts.json: ${e.message}")
            AccountsStoragePayload()
        }
    }

    private fun savePayloadToDisk(payload: AccountsStoragePayload) {
        try {
            val parent = accountsFile.parent
            if (parent != null && !fileSystem.exists(parent)) {
                fileSystem.createDirectories(parent)
            }
            fileSystem.write(accountsFile) {
                writeUtf8(json.encodeToString(payload))
            }
        } catch (e: Exception) {
            println("Error saving accounts to disk: ${e.message}")
        }
    }

    private suspend fun readLocalAccounts(): List<Account> {
        // Only perform legacy migration if accounts.json DOES NOT exist yet on disk
        if (!fileSystem.exists(accountsFile)) {
            try {
                val legacyRaw = secureVault.getString("local_saved_accounts")
                if (!legacyRaw.isNullOrBlank()) {
                    val legacyRecords = json.decodeFromString<List<AccountMetadataRecord>>(legacyRaw)
                    if (legacyRecords.isNotEmpty()) {
                        val legacySelected = secureVault.getString("selected_account_id")
                        savePayloadToDisk(AccountsStoragePayload(selectedAccountId = legacySelected, accounts = legacyRecords))
                        println("[ACCOUNT_MIGRATION] Migrated ${legacyRecords.size} legacy account(s) to accounts.json")
                    }
                }
                // Once checked or migrated, clear the legacy key so deleted accounts are never resurrected
                secureVault.remove("local_saved_accounts")
            } catch (e: Exception) {
                // Ignore legacy parse error
            }
        }

        val payload = readPayloadFromDisk()
        val records = payload.accounts

        return records.map { record ->
            when (record.type) {
                AccountType.MICROSOFT -> {
                    val msaRefresh = secureVault.getString("msa_refresh_${record.id}") ?: ""
                    val mcAccess = secureVault.getString("mc_access_${record.id}") ?: ""
                    val expiresAt = secureVault.getString("mc_expires_${record.id}")?.toLongOrNull() ?: 0L
                    MicrosoftAccount(
                        id = record.id,
                        username = record.username,
                        uuid = record.uuid,
                        avatarUrl = record.avatarUrl,
                        skinUrl = record.skinUrl,
                        skinModel = record.skinModel,
                        skinHash = record.skinHash,
                        msaRefreshToken = msaRefresh,
                        mcAccessToken = mcAccess,
                        expiresAt = expiresAt,
                        createdAt = record.createdAt,
                        lastUsedAt = record.lastUsedAt
                    )
                }
                AccountType.OFFLINE -> {
                    OfflineAccount(
                        id = record.id,
                        username = record.username,
                        uuid = record.uuid,
                        createdAt = record.createdAt,
                        lastUsedAt = record.lastUsedAt,
                        avatarUrl = record.avatarUrl,
                        skinUrl = record.skinUrl,
                        skinModel = record.skinModel,
                        skinHash = record.skinHash
                    )
                }
            }
        }
    }

    private fun persistAccountsState(list: List<Account>, selectedId: String?) {
        val records = list.map { account ->
            AccountMetadataRecord(
                id = account.id,
                username = account.username,
                uuid = account.uuid,
                type = account.type,
                avatarUrl = account.avatarUrl,
                skinUrl = account.skinUrl,
                skinModel = account.skinModel,
                skinHash = account.skinHash,
                createdAt = account.createdAt,
                lastUsedAt = account.lastUsedAt
            )
        }
        savePayloadToDisk(AccountsStoragePayload(selectedAccountId = selectedId, accounts = records))
    }

    override suspend fun loadAll(): List<Account> = withContext(dispatcher) {
        mutex.withLock {
            val loaded = readLocalAccounts()
            _accounts.value = loaded

            val payload = readPayloadFromDisk()
            val selectedId = payload.selectedAccountId ?: secureVault.getString("selected_account_id")
            _selectedAccount.value = loaded.find { it.id == selectedId } ?: loaded.firstOrNull()

            println("[ACCOUNT_LOAD] Loaded ${loaded.size} local account(s). Selected: ${_selectedAccount.value?.username ?: "None"}")
            loaded
        }
    }

    override suspend fun getAccount(id: String): Account? = withContext(dispatcher) {
        _accounts.value.find { it.id == id } ?: readLocalAccounts().find { it.id == id }
    }

    override suspend fun saveAccount(account: Account, source: String?): Unit = withContext(dispatcher) {
        mutex.withLock {
            // Read current persistent list from disk
            val diskAccounts = readLocalAccounts()

            // Locate existing account by stable identity:
            // 1. Direct ID match
            // 2. For Microsoft: matching Minecraft profile UUID (case-insensitive) or MSAL account ID
            // 3. For Offline: matching offline UUID or case-insensitive username
            val existingIndex = diskAccounts.indexOfFirst { existing ->
                existing.id == account.id ||
                (account is MicrosoftAccount && existing is MicrosoftAccount &&
                    (existing.uuid.equals(account.uuid, ignoreCase = true) ||
                     (account.msalAccountId != null && existing.msalAccountId == account.msalAccountId))) ||
                (account is OfflineAccount && existing is OfflineAccount &&
                    (existing.uuid == account.uuid || existing.username.equals(account.username, ignoreCase = true)))
            }

            val targetAccount: Account
            val isUpdate = existingIndex != -1

            if (isUpdate) {
                val existing = diskAccounts[existingIndex]
                targetAccount = when (account) {
                    is MicrosoftAccount -> account.copy(
                        id = existing.id,
                        createdAt = if (existing.createdAt > 0L) existing.createdAt else account.createdAt
                    )
                    is OfflineAccount -> account.copy(
                        id = existing.id,
                        createdAt = if (existing.createdAt > 0L) existing.createdAt else account.createdAt
                    )
                }
                println("[ACCOUNT_UPDATE] source=${source ?: "Internal"}, account='${targetAccount.username}' (${targetAccount.type}, id=${targetAccount.id})")
            } else {
                targetAccount = account
                if (isStartupPhase) {
                    System.err.println("[ACCOUNT_CREATE_ERROR] ERROR: ACCOUNT CREATED DURING STARTUP! Source: ${source ?: "Unknown"}, Account: '${account.username}'")
                }
                println("[ACCOUNT_CREATE] source=${source ?: "Internal"}, account='${targetAccount.username}' (${targetAccount.type}, id=${targetAccount.id})")
            }

            if (targetAccount is MicrosoftAccount) {
                if (targetAccount.msaRefreshToken.isNotBlank()) {
                    secureVault.putString("msa_refresh_${targetAccount.id}", targetAccount.msaRefreshToken)
                }
                if (targetAccount.mcAccessToken.isNotBlank()) {
                    secureVault.putString("mc_access_${targetAccount.id}", targetAccount.mcAccessToken)
                }
                if (targetAccount.expiresAt > 0L) {
                    secureVault.putString("mc_expires_${targetAccount.id}", targetAccount.expiresAt.toString())
                }
            }

            val currentList = if (isUpdate) {
                diskAccounts.mapIndexed { idx, item -> if (idx == existingIndex) targetAccount else item }
            } else {
                diskAccounts + targetAccount
            }
            _accounts.value = currentList

            val currentSelected = _selectedAccount.value
            val targetSelectedId = if (currentSelected == null || currentSelected.id == targetAccount.id) {
                _selectedAccount.value = targetAccount
                targetAccount.id
            } else {
                currentSelected.id
            }

            persistAccountsState(currentList, targetSelectedId)
            secureVault.putString("selected_account_id", targetSelectedId)
        }
    }

    override suspend fun selectAccount(id: String?): Unit = withContext(dispatcher) {
        mutex.withLock {
            if (id == null) {
                _selectedAccount.value = null
                secureVault.remove("selected_account_id")
                persistAccountsState(_accounts.value, null)
                println("[ACCOUNT_SELECT] Account deselected.")
                return@withLock
            }

            val currentList = if (_accounts.value.isNotEmpty()) _accounts.value else readLocalAccounts()
            val account = currentList.find { it.id == id } ?: throw IllegalArgumentException("Account not found: $id")
            val updatedAccount = when (account) {
                is MicrosoftAccount -> account.copy(lastUsedAt = System.currentTimeMillis())
                is OfflineAccount -> account.copy(lastUsedAt = System.currentTimeMillis())
            }

            val updatedList = currentList.map { if (it.id == id) updatedAccount else it }
            _accounts.value = updatedList
            _selectedAccount.value = updatedAccount

            secureVault.putString("selected_account_id", id)
            persistAccountsState(updatedList, id)

            println("[ACCOUNT_SELECT] Active account set to '${updatedAccount.username}' (${updatedAccount.type})")
        }
    }

    override suspend fun removeAccount(id: String): Unit = withContext(dispatcher) {
        mutex.withLock {
            val diskAccounts = readLocalAccounts()
            val target = diskAccounts.find { it.id == id }
            val updated = diskAccounts.filter { it.id != id }
            _accounts.value = updated

            val newSelected = if (_selectedAccount.value?.id == id) {
                updated.firstOrNull()
            } else {
                _selectedAccount.value
            }
            _selectedAccount.value = newSelected

            persistAccountsState(updated, newSelected?.id)

            if (newSelected != null) {
                secureVault.putString("selected_account_id", newSelected.id)
            } else {
                secureVault.remove("selected_account_id")
            }

            secureVault.remove("msa_refresh_$id")
            secureVault.remove("mc_access_$id")
            secureVault.remove("mc_expires_$id")
            secureVault.remove("local_saved_accounts")

            println("[ACCOUNT_DELETE] Removed account '${target?.username ?: id}' (${target?.type ?: "Unknown"}). Remaining: ${updated.size}")
        }
    }
}
