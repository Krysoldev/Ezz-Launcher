package io.ezz.launcher.core.storage.repository

import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import kotlinx.coroutines.flow.StateFlow

interface InstanceRepository {
    val instances: StateFlow<List<Instance>>
    suspend fun loadAll(): List<Instance>
    suspend fun getInstance(id: String): Instance?
    suspend fun createInstance(
        name: String,
        minecraftVersion: String,
        loaderType: LoaderType = LoaderType.VANILLA,
        loaderVersion: String? = null,
        iconId: String = "grass_block",
        minMemoryMb: Int = 1024,
        maxMemoryMb: Int = 4096,
        customJvmArgs: List<String> = emptyList(),
        javaPath: String? = null,
        windowWidth: Int = 1280,
        windowHeight: Int = 720,
        customIconPath: String? = null
    ): Instance
    suspend fun updateInstance(instance: Instance)
    suspend fun registerInstance(instance: Instance): Instance
    suspend fun deleteInstance(id: String)
    suspend fun duplicateInstance(id: String, newName: String): Instance
}
