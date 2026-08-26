package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.storage.path.PathProvider
import io.ezz.launcher.core.storage.supabase.SupabaseClient
import io.ezz.launcher.core.storage.supabase.SupabaseInstanceDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import java.util.UUID

class SupabaseInstanceRepository(
    private val supabaseClient: SupabaseClient,
    private val pathProvider: PathProvider,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : InstanceRepository {

    private val _instances = MutableStateFlow<List<Instance>>(emptyList())
    override val instances: StateFlow<List<Instance>> = _instances.asStateFlow()

    private val effectiveUserId: String
        get() = supabaseClient.currentUserId ?: "00000000-0000-0000-0000-000000000000"

    override suspend fun loadAll(): List<Instance> = withContext(dispatcher) {
        val dtos: List<SupabaseInstanceDto> = supabaseClient.select(
            table = "instances",
            params = mapOf("select" to "*", "order" to "created_at.desc")
        )
        val loaded = dtos.map { it.toInstance() }
        _instances.value = loaded
        loaded
    }

    override suspend fun getInstance(id: String): Instance? = withContext(dispatcher) {
        val dtos: List<SupabaseInstanceDto> = supabaseClient.select(
            table = "instances",
            params = mapOf("id" to "eq.$id", "select" to "*")
        )
        dtos.firstOrNull()?.toInstance()
    }

    override suspend fun createInstance(
        name: String,
        minecraftVersion: String,
        loaderType: LoaderType,
        loaderVersion: String?,
        iconId: String,
        minMemoryMb: Int,
        maxMemoryMb: Int,
        customJvmArgs: List<String>
    ): Instance = withContext(dispatcher) {
        val newId = UUID.randomUUID().toString()
        val dto = SupabaseInstanceDto(
            id = newId,
            userId = effectiveUserId,
            name = name,
            minecraftVersion = minecraftVersion,
            loaderType = loaderType.name,
            loaderVersion = loaderVersion,
            iconId = iconId,
            minMemoryMb = minMemoryMb,
            maxMemoryMb = maxMemoryMb,
            customJvmArgs = customJvmArgs
        )

        // Authoritative write to Supabase PostgreSQL
        val returned: List<SupabaseInstanceDto> = supabaseClient.insert("instances", dto)
        val created = (returned.firstOrNull() ?: dto).toInstance()

        // Prepare local Minecraft runtime directories
        val instanceDir = pathProvider.getInstanceDirectory(created.id)
        fileSystem.createDirectories(instanceDir)
        val gameDir = pathProvider.getInstanceGameDirectory(created.id)
        fileSystem.createDirectories(gameDir)
        fileSystem.createDirectories(gameDir.resolve("mods"))
        fileSystem.createDirectories(gameDir.resolve("config"))
        fileSystem.createDirectories(gameDir.resolve("resourcepacks"))
        fileSystem.createDirectories(gameDir.resolve("shaderpacks"))
        fileSystem.createDirectories(gameDir.resolve("saves"))
        fileSystem.createDirectories(gameDir.resolve("logs"))
        fileSystem.createDirectories(pathProvider.getInstanceNativesDirectory(created.id))

        loadAll()
        created
    }

    override suspend fun updateInstance(instance: Instance): Unit = withContext(dispatcher) {
        val dto = SupabaseInstanceDto.fromInstance(instance, effectiveUserId)
        supabaseClient.update<SupabaseInstanceDto, SupabaseInstanceDto>(
            table = "instances",
            filterParams = mapOf("id" to "eq.${instance.id}"),
            bodyData = dto
        )
        loadAll()
    }

    override suspend fun deleteInstance(id: String): Unit = withContext(dispatcher) {
        // Authoritative deletion in Supabase PostgreSQL
        supabaseClient.delete(
            table = "instances",
            filterParams = mapOf("id" to "eq.$id")
        )

        // Clean up local Minecraft runtime files
        val instanceDir = pathProvider.getInstanceDirectory(id)
        if (fileSystem.exists(instanceDir)) {
            fileSystem.deleteRecursively(instanceDir)
        }

        loadAll()
    }

    override suspend fun duplicateInstance(id: String, newName: String): Instance = withContext(dispatcher) {
        val original = getInstance(id) ?: throw IllegalArgumentException("Instance $id not found in Supabase")
        val newId = UUID.randomUUID().toString()
        val duplicated = original.copy(
            id = newId,
            name = newName,
            createdAt = 0L,
            lastPlayedAt = null,
            totalPlayTimeSeconds = 0L
        )

        val dto = SupabaseInstanceDto.fromInstance(duplicated, effectiveUserId)
        val returned: List<SupabaseInstanceDto> = supabaseClient.insert("instances", dto)
        val created = (returned.firstOrNull() ?: dto).toInstance()

        // Copy local .minecraft game files (mods, config, saves, resourcepacks)
        val srcGameDir = pathProvider.getInstanceGameDirectory(id)
        val destGameDir = pathProvider.getInstanceGameDirectory(newId)
        copyDirectory(srcGameDir, destGameDir)

        loadAll()
        created
    }

    private fun copyDirectory(source: Path, target: Path) {
        if (!fileSystem.exists(source)) return
        fileSystem.createDirectories(target)
        for (child in fileSystem.list(source)) {
            val destChild = target.resolve(child.name)
            val metadata = fileSystem.metadataOrNull(child)
            if (metadata?.isDirectory == true) {
                copyDirectory(child, destChild)
            } else {
                fileSystem.copy(child, destChild)
            }
        }
    }
}
