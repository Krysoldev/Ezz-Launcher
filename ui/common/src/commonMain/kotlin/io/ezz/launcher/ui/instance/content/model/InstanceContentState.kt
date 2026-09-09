package io.ezz.launcher.ui.instance.content.model

import io.ezz.launcher.core.model.instance.InstanceLogEntry
import io.ezz.launcher.core.model.instance.InstanceStatistics
import io.ezz.launcher.core.model.instance.LocalMod
import io.ezz.launcher.core.model.instance.LocalResourcePack
import io.ezz.launcher.core.model.instance.LocalScreenshot
import io.ezz.launcher.core.model.instance.LocalShaderPack
import io.ezz.launcher.core.model.instance.LocalWorld
import io.ezz.launcher.core.model.modrinth.ModConflict

sealed interface ContentLoadState<out T> {
    object Loading : ContentLoadState<Nothing>
    data class Success<T>(val data: T) : ContentLoadState<T>
    data class Error(val message: String, val throwable: Throwable? = null) : ContentLoadState<Nothing>

    val isLoading: Boolean get() = this is Loading
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
}

data class InstanceContentState(
    val instanceId: String,
    val isHydrating: Boolean = false,
    val modsState: ContentLoadState<List<LocalMod>> = ContentLoadState.Loading,
    val resourcePacksState: ContentLoadState<List<LocalResourcePack>> = ContentLoadState.Loading,
    val shadersState: ContentLoadState<List<LocalShaderPack>> = ContentLoadState.Loading,
    val worldsState: ContentLoadState<List<LocalWorld>> = ContentLoadState.Loading,
    val screenshotsState: ContentLoadState<List<LocalScreenshot>> = ContentLoadState.Loading,
    val logsState: ContentLoadState<List<InstanceLogEntry>> = ContentLoadState.Loading,
    val statisticsState: ContentLoadState<InstanceStatistics> = ContentLoadState.Loading,
    val missingDependencies: List<String> = emptyList(),
    val compatibilityConflicts: List<ModConflict> = emptyList(),
    val lastHydratedAt: Long = 0L
) {
    val mods: List<LocalMod>
        get() = (modsState as? ContentLoadState.Success)?.data ?: emptyList()

    val resourcePacks: List<LocalResourcePack>
        get() = (resourcePacksState as? ContentLoadState.Success)?.data ?: emptyList()

    val shaders: List<LocalShaderPack>
        get() = (shadersState as? ContentLoadState.Success)?.data ?: emptyList()

    val worlds: List<LocalWorld>
        get() = (worldsState as? ContentLoadState.Success)?.data ?: emptyList()

    val screenshots: List<LocalScreenshot>
        get() = (screenshotsState as? ContentLoadState.Success)?.data ?: emptyList()

    val logs: List<InstanceLogEntry>
        get() = (logsState as? ContentLoadState.Success)?.data ?: emptyList()

    val statistics: InstanceStatistics?
        get() = (statisticsState as? ContentLoadState.Success)?.data

    companion object {
        fun initial(instanceId: String): InstanceContentState = InstanceContentState(
            instanceId = instanceId,
            isHydrating = true
        )
    }
}
