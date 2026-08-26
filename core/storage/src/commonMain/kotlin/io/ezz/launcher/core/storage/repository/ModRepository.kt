package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.model.instance.ModMetadata
import io.ezz.launcher.core.storage.supabase.SupabaseClient
import io.ezz.launcher.core.storage.supabase.SupabaseInstanceModDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

interface ModRepository {
    suspend fun getModsForInstance(instanceId: String): List<ModMetadata>
    suspend fun registerMod(
        instanceId: String,
        name: String,
        version: String,
        fileName: String,
        fileHash: String? = null,
        loader: String = "FABRIC"
    ): ModMetadata
    suspend fun toggleMod(modId: String, enabled: Boolean)
    suspend fun deleteMod(modId: String)
}

class SupabaseModRepository(
    private val supabaseClient: SupabaseClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ModRepository {

    private val effectiveUserId: String
        get() = supabaseClient.currentUserId ?: "00000000-0000-0000-0000-000000000000"

    override suspend fun getModsForInstance(instanceId: String): List<ModMetadata> = withContext(dispatcher) {
        val dtos: List<SupabaseInstanceModDto> = supabaseClient.select(
            table = "instance_mods",
            params = mapOf("instance_id" to "eq.$instanceId", "select" to "*")
        )
        dtos.map {
            ModMetadata(
                id = it.id,
                instanceId = it.instanceId,
                name = it.name,
                version = it.version,
                fileName = it.fileName,
                fileHash = it.fileHash,
                loader = it.loader,
                enabled = it.enabled
            )
        }
    }

    override suspend fun registerMod(
        instanceId: String,
        name: String,
        version: String,
        fileName: String,
        fileHash: String?,
        loader: String
    ): ModMetadata = withContext(dispatcher) {
        val id = UUID.randomUUID().toString()
        val dto = SupabaseInstanceModDto(
            id = id,
            instanceId = instanceId,
            userId = effectiveUserId,
            name = name,
            version = version,
            fileName = fileName,
            fileHash = fileHash,
            loader = loader,
            enabled = true
        )

        val returned: List<SupabaseInstanceModDto> = supabaseClient.insert("instance_mods", dto)
        val saved = returned.firstOrNull() ?: dto
        ModMetadata(
            id = saved.id,
            instanceId = saved.instanceId,
            name = saved.name,
            version = saved.version,
            fileName = saved.fileName,
            fileHash = saved.fileHash,
            loader = saved.loader,
            enabled = saved.enabled
        )
    }

    override suspend fun toggleMod(modId: String, enabled: Boolean): Unit = withContext(dispatcher) {
        supabaseClient.update<Map<String, Boolean>, SupabaseInstanceModDto>(
            table = "instance_mods",
            filterParams = mapOf("id" to "eq.$modId"),
            bodyData = mapOf("enabled" to enabled)
        )
    }

    override suspend fun deleteMod(modId: String): Unit = withContext(dispatcher) {
        supabaseClient.delete(
            table = "instance_mods",
            filterParams = mapOf("id" to "eq.$modId")
        )
    }
}
