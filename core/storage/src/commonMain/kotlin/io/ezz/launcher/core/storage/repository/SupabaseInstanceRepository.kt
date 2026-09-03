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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import java.util.UUID

class SupabaseInstanceRepository(
    private val supabaseClient: SupabaseClient,
    private val pathProvider: PathProvider,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : InstanceRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = true
    }

    private val _instances = MutableStateFlow<List<Instance>>(emptyList())
    override val instances: StateFlow<List<Instance>> = _instances.asStateFlow()

    private val localCacheFile: Path get() = pathProvider.rootDirectory.resolve("local_instances.json")

    private val effectiveUserId: String
        get() = supabaseClient.currentUserId ?: "00000000-0000-0000-0000-000000000000"

    private fun readLocalCache(): List<Instance> {
        return try {
            if (fileSystem.exists(localCacheFile)) {
                val content = fileSystem.read(localCacheFile) { readUtf8() }
                json.decodeFromString<List<Instance>>(content)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveLocalCache(list: List<Instance>) {
        try {
            val parent = localCacheFile.parent
            if (parent != null && !fileSystem.exists(parent)) {
                fileSystem.createDirectories(parent)
            }
            fileSystem.write(localCacheFile) {
                writeUtf8(json.encodeToString(list))
            }
        } catch (e: Exception) {
            println("Warning: failed to write local instances cache: ${e.message}")
        }
    }

    override suspend fun loadAll(): List<Instance> = withContext(dispatcher) {
        try {
            if (supabaseClient.config.isConfigured && supabaseClient.isConnected.value == true) {
                val dtos: List<SupabaseInstanceDto> = supabaseClient.select(
                    table = "instances",
                    params = mapOf("select" to "*", "order" to "created_at.desc")
                )
                val loaded = dtos.map { it.toInstance() }
                saveLocalCache(loaded)
                _instances.value = loaded
                return@withContext loaded
            }
        } catch (e: Exception) {
            println("Supabase loadAll notice (falling back to local cache): ${e.message}")
        }

        // Fallback to local disk cache
        val local = readLocalCache()
        _instances.value = local
        local
    }

    override suspend fun getInstance(id: String): Instance? = withContext(dispatcher) {
        val current = _instances.value.find { it.id == id }
        if (current != null) return@withContext current

        try {
            if (supabaseClient.config.isConfigured && supabaseClient.isConnected.value == true) {
                val dtos: List<SupabaseInstanceDto> = supabaseClient.select(
                    table = "instances",
                    params = mapOf("id" to "eq.$id", "select" to "*")
                )
                val inst = dtos.firstOrNull()?.toInstance()
                if (inst != null) return@withContext inst
            }
        } catch (e: Exception) {
            // fallback
        }
        readLocalCache().find { it.id == id }
    }

    override suspend fun createInstance(
        name: String,
        minecraftVersion: String,
        loaderType: LoaderType,
        loaderVersion: String?,
        iconId: String,
        minMemoryMb: Int,
        maxMemoryMb: Int,
        customJvmArgs: List<String>,
        javaPath: String?,
        windowWidth: Int,
        windowHeight: Int,
        customIconPath: String?
    ): Instance = withContext(dispatcher) {
        val newId = UUID.randomUUID().toString()
        val instance = Instance(
            id = newId,
            name = name,
            minecraftVersion = minecraftVersion,
            loaderType = loaderType,
            loaderVersion = loaderVersion,
            iconId = iconId,
            javaPath = javaPath,
            minMemoryMb = minMemoryMb,
            maxMemoryMb = maxMemoryMb,
            customJvmArgs = customJvmArgs,
            windowWidth = windowWidth,
            windowHeight = windowHeight,
            customIconPath = customIconPath,
            createdAt = System.currentTimeMillis()
        )

        // Prepare local Minecraft runtime directories
        val instanceDir = pathProvider.getInstanceDirectory(instance.id)
        fileSystem.createDirectories(instanceDir)
        val gameDir = pathProvider.getInstanceGameDirectory(instance.id)
        fileSystem.createDirectories(gameDir)
        fileSystem.createDirectories(gameDir.resolve("mods"))
        fileSystem.createDirectories(gameDir.resolve("config"))
        fileSystem.createDirectories(gameDir.resolve("resourcepacks"))
        fileSystem.createDirectories(gameDir.resolve("shaderpacks"))
        fileSystem.createDirectories(gameDir.resolve("saves"))
        fileSystem.createDirectories(gameDir.resolve("logs"))
        fileSystem.createDirectories(pathProvider.getInstanceNativesDirectory(instance.id))

        // Save locally first
        val currentList = readLocalCache().filter { it.id != newId } + instance
        saveLocalCache(currentList)
        _instances.value = currentList

        // Sync to Supabase if connected
        try {
            if (supabaseClient.config.isConfigured) {
                val dto = SupabaseInstanceDto.fromInstance(instance, effectiveUserId)
                supabaseClient.insert<SupabaseInstanceDto, SupabaseInstanceDto>("instances", dto)
            }
        } catch (e: Exception) {
            println("Notice: Supabase cloud sync deferred for new instance: ${e.message}")
        }

        instance
    }

    override suspend fun updateInstance(instance: Instance): Unit = withContext(dispatcher) {
        val currentList = readLocalCache().map { if (it.id == instance.id) instance else it }
        saveLocalCache(currentList)
        _instances.value = currentList

        try {
            if (supabaseClient.config.isConfigured) {
                val dto = SupabaseInstanceDto.fromInstance(instance, effectiveUserId)
                supabaseClient.update<SupabaseInstanceDto, SupabaseInstanceDto>(
                    table = "instances",
                    filterParams = mapOf("id" to "eq.${instance.id}"),
                    bodyData = dto
                )
            }
        } catch (e: Exception) {
            println("Notice: Supabase cloud sync deferred for instance update: ${e.message}")
        }
    }

    override suspend fun registerInstance(instance: Instance): Instance = withContext(dispatcher) {
        val currentList = readLocalCache().filter { it.id != instance.id } + instance
        saveLocalCache(currentList)
        _instances.value = currentList

        // Ensure directory structure
        try {
            val instDir = pathProvider.getInstanceDirectory(instance.id)
            fileSystem.createDirectories(instDir)
            fileSystem.createDirectories(instDir.resolve(".minecraft"))
        } catch (_: Exception) {}

        try {
            if (supabaseClient.config.isConfigured && supabaseClient.isConnected.value == true) {
                val dto = SupabaseInstanceDto(
                    id = instance.id,
                    userId = effectiveUserId,
                    name = instance.name,
                    minecraftVersion = instance.minecraftVersion,
                    loaderType = instance.loaderType.name.lowercase(),
                    loaderVersion = instance.loaderVersion,
                    iconId = instance.iconId,
                    javaPath = instance.javaPath,
                    minMemoryMb = instance.minMemoryMb,
                    maxMemoryMb = instance.maxMemoryMb,
                    customJvmArgs = instance.customJvmArgs,
                    windowWidth = instance.windowWidth,
                    windowHeight = instance.windowHeight,
                    createdAt = instance.createdAt.toString()
                )
                supabaseClient.insert<SupabaseInstanceDto, SupabaseInstanceDto>(
                    table = "instances",
                    bodyData = dto
                )
            }
        } catch (e: Exception) {
            println("Notice: Supabase register instance deferred: ${e.message}")
        }

        instance
    }

    override suspend fun deleteInstance(id: String): Unit = withContext(dispatcher) {
        val currentList = readLocalCache().filter { it.id != id }
        saveLocalCache(currentList)
        _instances.value = currentList

        // Clean up local files
        val instanceDir = pathProvider.getInstanceDirectory(id)
        if (fileSystem.exists(instanceDir)) {
            fileSystem.deleteRecursively(instanceDir)
        }

        try {
            if (supabaseClient.config.isConfigured) {
                supabaseClient.delete(
                    table = "instances",
                    filterParams = mapOf("id" to "eq.$id")
                )
            }
        } catch (e: Exception) {
            println("Notice: Supabase delete deferred: ${e.message}")
        }
    }

    override suspend fun duplicateInstance(id: String, newName: String): Instance = withContext(dispatcher) {
        val original = getInstance(id) ?: throw IllegalArgumentException("Instance $id not found")
        val newId = UUID.randomUUID().toString()
        val duplicated = original.copy(
            id = newId,
            name = newName,
            createdAt = System.currentTimeMillis(),
            lastPlayedAt = null,
            totalPlayTimeSeconds = 0L
        )

        // Copy local files
        val srcGameDir = pathProvider.getInstanceGameDirectory(id)
        val destGameDir = pathProvider.getInstanceGameDirectory(newId)
        copyDirectory(srcGameDir, destGameDir)

        val currentList = readLocalCache().filter { it.id != newId } + duplicated
        saveLocalCache(currentList)
        _instances.value = currentList

        try {
            if (supabaseClient.config.isConfigured) {
                val dto = SupabaseInstanceDto.fromInstance(duplicated, effectiveUserId)
                supabaseClient.insert<SupabaseInstanceDto, SupabaseInstanceDto>("instances", dto)
            }
        } catch (e: Exception) {
            println("Notice: Supabase duplicate sync deferred: ${e.message}")
        }

        duplicated
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
