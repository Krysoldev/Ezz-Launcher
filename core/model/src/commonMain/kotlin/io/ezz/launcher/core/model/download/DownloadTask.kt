package io.ezz.launcher.core.model.download

import kotlinx.serialization.Serializable

@Serializable
data class DownloadTask(
    val url: String,
    val destinationPath: String,
    val expectedSha1: String? = null,
    val expectedSize: Long = 0L,
    val description: String = ""
)

data class DownloadProgress(
    val currentTaskIndex: Int,
    val totalTasks: Int,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val currentItemName: String,
    val percentage: Float = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
)

sealed interface DownloadResult {
    data object Success : DownloadResult
    data class Failure(val message: String, val cause: Throwable? = null) : DownloadResult
    data object Cancelled : DownloadResult
}
