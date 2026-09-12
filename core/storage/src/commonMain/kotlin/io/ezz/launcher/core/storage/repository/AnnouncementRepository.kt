package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.storage.supabase.SupabaseAnnouncementDto
import io.ezz.launcher.core.storage.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface AnnouncementRepository {
    val announcements: StateFlow<List<SupabaseAnnouncementDto>>
    suspend fun getActiveAnnouncements(forceRefresh: Boolean = false): List<SupabaseAnnouncementDto>
    suspend fun getAllAnnouncements(forceRefresh: Boolean = false): List<SupabaseAnnouncementDto>
    suspend fun saveAnnouncement(adminUsername: String, announcement: SupabaseAnnouncementDto): Result<SupabaseAnnouncementDto>
    suspend fun deleteAnnouncement(adminUsername: String, id: String): Result<Unit>
}

class SupabaseAnnouncementRepository(
    private val supabaseClient: SupabaseClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : AnnouncementRepository {

    private val _announcements = MutableStateFlow<List<SupabaseAnnouncementDto>>(emptyList())
    override val announcements: StateFlow<List<SupabaseAnnouncementDto>> = _announcements.asStateFlow()

    private suspend fun isAdmin(adminUsername: String): Boolean {
        return try {
            val response = supabaseClient.rpc(
                functionName = "is_admin_user",
                params = buildJsonObject {
                    put("lookup_username", adminUsername)
                }
            )
            response.trim().equals("true", ignoreCase = true)
        } catch (e: Throwable) {
            false
        }
    }

    override suspend fun getActiveAnnouncements(forceRefresh: Boolean): List<SupabaseAnnouncementDto> = withContext(dispatcher) {
        if (!forceRefresh && _announcements.value.isNotEmpty()) {
            return@withContext _announcements.value
        }

        try {
            val list: List<SupabaseAnnouncementDto> = supabaseClient.select(
                table = "launcher_announcements",
                params = mapOf(
                    "is_active" to "eq.true",
                    "order" to "priority.desc,published_at.desc",
                    "select" to "*"
                )
            )
            _announcements.value = list
            list
        } catch (e: Throwable) {
            _announcements.value
        }
    }

    override suspend fun getAllAnnouncements(forceRefresh: Boolean): List<SupabaseAnnouncementDto> = withContext(dispatcher) {
        try {
            supabaseClient.select(
                table = "launcher_announcements",
                params = mapOf(
                    "order" to "priority.desc,created_at.desc",
                    "select" to "*"
                )
            )
        } catch (e: Throwable) {
            emptyList()
        }
    }

    override suspend fun saveAnnouncement(
        adminUsername: String,
        announcement: SupabaseAnnouncementDto
    ): Result<SupabaseAnnouncementDto> = withContext(dispatcher) {
        try {
            if (!isAdmin(adminUsername)) {
                return@withContext Result.failure(SecurityException("403 Forbidden: '$adminUsername' is not an authorized administrator"))
            }
            val result = if (announcement.id.isBlank()) {
                val created: List<SupabaseAnnouncementDto> = supabaseClient.insert(
                    table = "launcher_announcements",
                    bodyData = announcement
                )
                created.firstOrNull() ?: announcement
            } else {
                val updated: List<SupabaseAnnouncementDto> = supabaseClient.update(
                    table = "launcher_announcements",
                    filterParams = mapOf("id" to "eq.${announcement.id}"),
                    bodyData = announcement
                )
                updated.firstOrNull() ?: announcement
            }
            getActiveAnnouncements(forceRefresh = true)
            Result.success(result)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    override suspend fun deleteAnnouncement(adminUsername: String, id: String): Result<Unit> = withContext(dispatcher) {
        try {
            if (!isAdmin(adminUsername)) {
                return@withContext Result.failure(SecurityException("403 Forbidden: '$adminUsername' is not an authorized administrator"))
            }
            supabaseClient.delete(
                table = "launcher_announcements",
                filterParams = mapOf("id" to "eq.$id")
            )
            getActiveAnnouncements(forceRefresh = true)
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}
