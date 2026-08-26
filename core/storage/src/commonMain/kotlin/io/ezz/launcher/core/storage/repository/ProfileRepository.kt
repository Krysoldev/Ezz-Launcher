package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.storage.supabase.SupabaseClient
import io.ezz.launcher.core.storage.supabase.SupabaseProfileDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class EzzProfile(
    val id: String,
    val email: String? = null,
    val displayName: String = "Ezz Player",
    val avatarUrl: String? = null
)

interface ProfileRepository {
    val currentProfile: StateFlow<EzzProfile?>
    suspend fun loadProfile(): EzzProfile?
    suspend fun updateProfile(displayName: String, avatarUrl: String? = null): EzzProfile
}

class SupabaseProfileRepository(
    private val supabaseClient: SupabaseClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ProfileRepository {

    private val _currentProfile = MutableStateFlow<EzzProfile?>(null)
    override val currentProfile: StateFlow<EzzProfile?> = _currentProfile.asStateFlow()

    private val effectiveUserId: String
        get() = supabaseClient.currentUserId ?: "00000000-0000-0000-0000-000000000000"

    override suspend fun loadProfile(): EzzProfile? = withContext(dispatcher) {
        val dtos: List<SupabaseProfileDto> = try {
            supabaseClient.select(
                table = "profiles",
                params = mapOf("id" to "eq.$effectiveUserId", "select" to "*")
            )
        } catch (e: Exception) {
            emptyList()
        }

        val profile = dtos.firstOrNull()?.let {
            EzzProfile(
                id = it.id,
                email = it.email,
                displayName = it.displayName,
                avatarUrl = it.avatarUrl
            )
        } ?: EzzProfile(id = effectiveUserId, displayName = "Ezz Player")

        _currentProfile.value = profile
        profile
    }

    override suspend fun updateProfile(displayName: String, avatarUrl: String?): EzzProfile = withContext(dispatcher) {
        val dto = SupabaseProfileDto(
            id = effectiveUserId,
            displayName = displayName,
            avatarUrl = avatarUrl
        )

        val updated: List<SupabaseProfileDto> = try {
            supabaseClient.update(
                table = "profiles",
                filterParams = mapOf("id" to "eq.$effectiveUserId"),
                bodyData = dto
            )
        } catch (e: Exception) {
            supabaseClient.insert("profiles", dto)
        }

        val profile = (updated.firstOrNull() ?: dto).let {
            EzzProfile(
                id = it.id,
                email = it.email,
                displayName = it.displayName,
                avatarUrl = it.avatarUrl
            )
        }
        _currentProfile.value = profile
        profile
    }
}
