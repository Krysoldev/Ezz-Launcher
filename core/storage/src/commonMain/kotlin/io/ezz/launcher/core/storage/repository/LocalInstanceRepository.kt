package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.core.storage.path.PathProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import java.util.UUID

/**
 * Local-First, authoritative repository for Minecraft Instances.
 * Instances are stored purely on the local machine and are NEVER tied to
 * or filtered by Minecraft accounts or Supabase user IDs.
 */
class LocalInstanceRepository(
    private val pathProvider: PathProvider,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : InstanceRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = true
    }

    private val mutex = Mutex()
    private val _instances = MutableStateFlow<List<Instance>>(emptyList())
    override val instances: StateFlow<List<Instance>> = _instances.asStateFlow()

    private val instancesFile: Path get() = pathProvider.rootDirectory.resolve("instances.json")
    private val legacyCacheFile: Path get() = pathProvider.rootDirectory.resolve("local_instances.json")

    init {
        // Read initial state from disk synchronously or on start
        val initial = readFromDisk()
        _instances.value = initial
    }

    private fun readFromDisk(): List<Instance> {
        return try {
            val target = if (fileSystem.exists(instancesFile)) {
                instancesFile
            } else if (fileSystem.exists(legacyCacheFile)) {
                legacyCacheFile
            } else {
                null
            }

            if (target != null) {
                val content = fileSystem.read(target) { readUtf8() }
                val parsed = json.decodeFromString<List<Instance>>(content)
                // Migrate to instances.json if we read from legacy file
                if (target == legacyCacheFile && !fileSystem.exists(instancesFile)) {
                    saveToDisk(parsed)
                }
                parsed
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            println("Warning: failed to read local instances from disk: ${e.message}")
            emptyList()
        }
    }

    private fun saveToDisk(list: List<Instance>) {
        try {
            val parent = instancesFile.parent
            if (parent != null && !fileSystem.exists(parent)) {
                fileSystem.createDirectories(parent)
            }
            fileSystem.write(instancesFile) {
                writeUtf8(json.encodeToString(list))
            }
        } catch (e: Exception) {
            println("Error saving local instances to disk: ${e.message}")
        }
    }

    override suspend fun loadAll(): List<Instance> = withContext(dispatcher) {
        mutex.withLock {
            val list = readFromDisk()
            _instances.value = list
            list
        }
    }

    override suspend fun getInstance(id: String): Instance? = withContext(dispatcher) {
        _instances.value.find { it.id == id } ?: readFromDisk().find { it.id == id }
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
        mutex.withLock {
            val id = UUID.randomUUID().toString()
            val newInstance = Instance(
                id = id,
                name = name.trim().ifBlank { "Minecraft $minecraftVersion" },
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

            // Create instance directory structure on disk
            try {
                val instDir = pathProvider.getInstanceDirectory(id)
                fileSystem.createDirectories(instDir)
                fileSystem.createDirectories(instDir.resolve(".minecraft"))
                fileSystem.createDirectories(instDir.resolve(".minecraft").resolve("mods"))
                fileSystem.createDirectories(instDir.resolve(".minecraft").resolve("resourcepacks"))
                fileSystem.createDirectories(instDir.resolve(".minecraft").resolve("shaderpacks"))
            } catch (e: Exception) {
                println("Note creating instance directory structure: ${e.message}")
            }

            val updated = _instances.value + newInstance
            _instances.value = updated
            saveToDisk(updated)
            newInstance
        }
    }

    override suspend fun updateInstance(instance: Instance): Unit = withContext(dispatcher) {
        mutex.withLock {
            val current = _instances.value
            val updated = current.map { if (it.id == instance.id) instance else it }
            _instances.value = updated
            saveToDisk(updated)
        }
    }

    override suspend fun registerInstance(instance: Instance): Instance = withContext(dispatcher) {
        mutex.withLock {
            val instDir = pathProvider.getInstanceDirectory(instance.id)
            try {
                fileSystem.createDirectories(instDir)
                fileSystem.createDirectories(instDir.resolve(".minecraft"))
            } catch (_: Exception) {}

            val updated = _instances.value.filter { it.id != instance.id } + instance
            _instances.value = updated
            saveToDisk(updated)
            instance
        }
    }

    override suspend fun deleteInstance(id: String): Unit = withContext(dispatcher) {
        mutex.withLock {
            val current = _instances.value
            val updated = current.filter { it.id != id }
            _instances.value = updated
            saveToDisk(updated)

            // Delete instance directory from local filesystem
            try {
                val instDir = pathProvider.getInstanceDirectory(id)
                if (fileSystem.exists(instDir)) {
                    fileSystem.deleteRecursively(instDir)
                }
            } catch (e: Exception) {
                println("Warning: failed to delete instance directory on disk: ${e.message}")
            }
        }
    }

    override suspend fun duplicateInstance(id: String, newName: String): Instance = withContext(dispatcher) {
        val original = getInstance(id) ?: throw IllegalArgumentException("Instance not found: $id")
        createInstance(
            name = newName,
            minecraftVersion = original.minecraftVersion,
            loaderType = original.loaderType,
            loaderVersion = original.loaderVersion,
            iconId = original.iconId,
            minMemoryMb = original.minMemoryMb,
            maxMemoryMb = original.maxMemoryMb,
            customJvmArgs = original.customJvmArgs
        )
    }
}
