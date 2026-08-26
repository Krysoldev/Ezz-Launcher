package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.model.account.Account
import io.ezz.launcher.core.model.account.AccountType
import io.ezz.launcher.core.model.account.MicrosoftAccount
import io.ezz.launcher.core.model.account.OfflineAccount
import io.ezz.launcher.core.storage.supabase.SupabaseClient
import io.ezz.launcher.core.storage.supabase.SupabaseMinecraftAccountDto
import io.ezz.launcher.core.storage.vault.SecureVault
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class SupabaseAccountRepository(
    private val supabaseClient: SupabaseClient,
    private val secureVault: SecureVault,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : AccountRepository {

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    override val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _selectedAccount = MutableStateFlow<Account?>(null)
    override val selectedAccount: StateFlow<Account?> = _selectedAccount.asStateFlow()

    private val effectiveUserId: String
        get() = supabaseClient.currentUserId ?: "00000000-0000-0000-0000-000000000000"

    override suspend fun loadAll(): List<Account> = withContext(dispatcher) {
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

        _accounts.value = loaded
        val selectedDto = dtos.find { it.isSelected }
        _selectedAccount.value = loaded.find { it.id == selectedDto?.id } ?: loaded.firstOrNull()
        loaded
    }

    override suspend fun getAccount(id: String): Account? = withContext(dispatcher) {
        val dtos: List<SupabaseMinecraftAccountDto> = supabaseClient.select(
            table = "minecraft_accounts",
            params = mapOf("id" to "eq.$id", "select" to "*")
        )
        val dto = dtos.firstOrNull() ?: return@withContext null
        if (dto.type.equals("MICROSOFT", ignoreCase = true)) {
            val msaRefresh = secureVault.getString("msa_refresh_${dto.id}") ?: ""
            val mcAccess = secureVault.getString("mc_access_${dto.id}") ?: ""
            val expiresAt = secureVault.getString("mc_expires_${dto.id}")?.toLongOrNull() ?: 0L
            dto.toAccount(msaRefreshToken = msaRefresh, mcAccessToken = mcAccess, expiresAt = expiresAt)
        } else {
            dto.toAccount()
        }
    }

    override suspend fun saveAccount(account: Account): Unit = withContext(dispatcher) {
        if (account is MicrosoftAccount) {
            secureVault.putString("msa_refresh_${account.id}", account.msaRefreshToken)
            secureVault.putString("mc_access_${account.id}", account.mcAccessToken)
            secureVault.putString("mc_expires_${account.id}", account.expiresAt.toString())
        }

        val dto = SupabaseMinecraftAccountDto(
            id = account.id,
            userId = effectiveUserId,
            username = account.username,
            uuid = account.uuid,
            type = account.type.name,
            avatarUrl = account.avatarUrl,
            isSelected = (_selectedAccount.value == null || _selectedAccount.value?.id == account.id)
        )

        // Check if existing record
        val existing = getAccount(account.id)
        if (existing == null) {
            supabaseClient.insert<SupabaseMinecraftAccountDto, SupabaseMinecraftAccountDto>(
                table = "minecraft_accounts",
                bodyData = dto
            )
        } else {
            supabaseClient.update<SupabaseMinecraftAccountDto, SupabaseMinecraftAccountDto>(
                table = "minecraft_accounts",
                filterParams = mapOf("id" to "eq.${account.id}"),
                bodyData = dto
            )
        }

        loadAll()
    }

    override suspend fun removeAccount(id: String): Unit = withContext(dispatcher) {
        secureVault.remove("msa_refresh_$id")
        secureVault.remove("mc_access_$id")
        secureVault.remove("mc_expires_$id")

        supabaseClient.delete(
            table = "minecraft_accounts",
            filterParams = mapOf("id" to "eq.$id")
        )

        loadAll()
    }

    override suspend fun selectAccount(id: String?): Unit = withContext(dispatcher) {
        if (id == null) {
            _selectedAccount.value = null
            return@withContext
        }

        // Clear all previous selections in Supabase
        supabaseClient.update<Map<String, Boolean>, SupabaseMinecraftAccountDto>(
            table = "minecraft_accounts",
            filterParams = mapOf("user_id" to "eq.$effectiveUserId"),
            bodyData = mapOf("is_selected" to false)
        )

        // Mark chosen account as selected
        supabaseClient.update<Map<String, Boolean>, SupabaseMinecraftAccountDto>(
            table = "minecraft_accounts",
            filterParams = mapOf("id" to "eq.$id"),
            bodyData = mapOf("is_selected" to true)
        )

        loadAll()
    }
}
