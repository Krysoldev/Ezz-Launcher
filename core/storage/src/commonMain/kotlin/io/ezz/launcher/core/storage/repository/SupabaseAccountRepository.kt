package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.model.account.Account
import io.ezz.launcher.core.model.account.MicrosoftAccount
import io.ezz.launcher.core.storage.supabase.SupabaseClient
import io.ezz.launcher.core.storage.supabase.SupabaseMinecraftAccountDto
import io.ezz.launcher.core.storage.vault.SecureVault
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SupabaseAccountRepository(
    private val supabaseClient: SupabaseClient,
    private val secureVault: SecureVault,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : AccountRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = true
    }

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    override val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _selectedAccount = MutableStateFlow<Account?>(null)
    override val selectedAccount: StateFlow<Account?> = _selectedAccount.asStateFlow()

    private val effectiveUserId: String
        get() = supabaseClient.currentUserId ?: "00000000-0000-0000-0000-000000000000"

    private suspend fun readLocalAccounts(): List<Account> {
        return try {
            val raw = secureVault.getString("local_saved_accounts") ?: return emptyList()
            val dtos = json.decodeFromString<List<SupabaseMinecraftAccountDto>>(raw)
            dtos.map { dto ->
                if (dto.type.equals("MICROSOFT", ignoreCase = true)) {
                    val msaRefresh = secureVault.getString("msa_refresh_${dto.id}") ?: ""
                    val mcAccess = secureVault.getString("mc_access_${dto.id}") ?: ""
                    val expiresAt = secureVault.getString("mc_expires_${dto.id}")?.toLongOrNull() ?: 0L
                    dto.toAccount(msaRefreshToken = msaRefresh, mcAccessToken = mcAccess, expiresAt = expiresAt)
                } else {
                    dto.toAccount()
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun saveLocalAccounts(list: List<Account>) {
        try {
            val selectedId = _selectedAccount.value?.id
            val dtos = list.map { account ->
                SupabaseMinecraftAccountDto(
                    id = account.id,
                    userId = effectiveUserId,
                    username = account.username,
                    uuid = account.uuid,
                    type = account.type.name,
                    avatarUrl = account.avatarUrl,
                    isSelected = (account.id == selectedId)
                )
            }
            secureVault.putString("local_saved_accounts", json.encodeToString(dtos))
        } catch (e: Exception) {
            println("Warning: failed to save local accounts: ${e.message}")
        }
    }

    override suspend fun loadAll(): List<Account> = withContext(dispatcher) {
        try {
            if (supabaseClient.config.isConfigured && supabaseClient.isConnected.value == true) {
                val dtos: List<SupabaseMinecraftAccountDto> = supabaseClient.select(
                    table = "minecraft_accounts",
                    params = mapOf("select" to "*", "order" to "created_at.desc")
                )

                val loaded = dtos.map { dto ->
                    if (dto.type.equals("MICROSOFT", ignoreCase = true)) {
                        val msaRefresh = secureVault.getString("msa_refresh_${dto.id}") ?: ""
                        val mcAccess = secureVault.getString("mc_access_${dto.id}") ?: ""
                        val expiresAt = secureVault.getString("mc_expires_${dto.id}")?.toLongOrNull() ?: 0L
                        dto.toAccount(msaRefreshToken = msaRefresh, mcAccessToken = mcAccess, expiresAt = expiresAt)
                    } else {
                        dto.toAccount()
                    }
                }

                saveLocalAccounts(loaded)
                _accounts.value = loaded
                val selectedDto = dtos.find { it.isSelected }
                _selectedAccount.value = loaded.find { it.id == selectedDto?.id } ?: loaded.firstOrNull()
                return@withContext loaded
            }
        } catch (e: Exception) {
            println("Notice: Supabase accounts loadAll fallback: ${e.message}")
        }

        val local = readLocalAccounts()
        _accounts.value = local
        val savedSelectedId = secureVault.getString("selected_account_id")
        _selectedAccount.value = local.find { it.id == savedSelectedId } ?: local.firstOrNull()
        local
    }

    override suspend fun getAccount(id: String): Account? = withContext(dispatcher) {
        _accounts.value.find { it.id == id } ?: readLocalAccounts().find { it.id == id }
    }

    override suspend fun saveAccount(account: Account): Unit = withContext(dispatcher) {
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

        try {
            if (supabaseClient.config.isConfigured) {
                val dto = SupabaseMinecraftAccountDto(
                    id = account.id,
                    userId = effectiveUserId,
                    username = account.username,
                    uuid = account.uuid,
                    type = account.type.name,
                    avatarUrl = account.avatarUrl,
                    isSelected = (_selectedAccount.value?.id == account.id)
                )
                supabaseClient.insert<SupabaseMinecraftAccountDto, SupabaseMinecraftAccountDto>(
                    table = "minecraft_accounts",
                    bodyData = dto
                )
            }
        } catch (e: Exception) {
            println("Notice: Supabase account sync deferred: ${e.message}")
        }
    }

    override suspend fun selectAccount(id: String?): Unit = withContext(dispatcher) {
        if (id == null) {
            _selectedAccount.value = null
            secureVault.remove("selected_account_id")
            return@withContext
        }
        val account = getAccount(id) ?: throw IllegalArgumentException("Account not found: $id")
        _selectedAccount.value = account
        secureVault.putString("selected_account_id", account.id)
        saveLocalAccounts(_accounts.value)

        try {
            if (supabaseClient.config.isConfigured) {
                supabaseClient.update<Map<String, Boolean>, SupabaseMinecraftAccountDto>(
                    table = "minecraft_accounts",
                    filterParams = mapOf("user_id" to "eq.$effectiveUserId"),
                    bodyData = mapOf("is_selected" to false)
                )
                supabaseClient.update<Map<String, Boolean>, SupabaseMinecraftAccountDto>(
                    table = "minecraft_accounts",
                    filterParams = mapOf("id" to "eq.$id"),
                    bodyData = mapOf("is_selected" to true)
                )
            }
        } catch (e: Exception) {
            // local state is already authoritative
        }
    }

    override suspend fun removeAccount(id: String): Unit = withContext(dispatcher) {
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

        try {
            if (supabaseClient.config.isConfigured) {
                supabaseClient.delete(
                    table = "minecraft_accounts",
                    filterParams = mapOf("id" to "eq.$id")
                )
            }
        } catch (e: Exception) {
            // deferred
        }
    }
}
