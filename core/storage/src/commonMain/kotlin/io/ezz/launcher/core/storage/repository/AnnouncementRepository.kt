package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.storage.supabase.SupabaseAnnouncementDto
import io.ezz.launcher.core.storage.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

interface AnnouncementRepository {
    val announcements: StateFlow<List<SupabaseAnnouncementDto>>
    suspend fun getActiveAnnouncements(forceRefresh: Boolean = false): List<SupabaseAnnouncementDto>
}

class SupabaseAnnouncementRepository(
    private val supabaseClient: SupabaseClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : AnnouncementRepository {

    private val _announcements = MutableStateFlow<List<SupabaseAnnouncementDto>>(emptyList())
    override val announcements: StateFlow<List<SupabaseAnnouncementDto>> = _announcements.asStateFlow()

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
}
