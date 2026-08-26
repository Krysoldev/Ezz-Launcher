package io.ezz.launcher.core.network.downloader

import io.ezz.launcher.core.model.download.DownloadProgress
import io.ezz.launcher.core.model.download.DownloadResult
import io.ezz.launcher.core.model.download.DownloadTask
import io.ezz.launcher.core.network.checksum.ChecksumVerifier
import io.ktor.client.HttpClient
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class DownloadManager(
    private val httpClient: HttpClient,
    private val maxConcurrency: Int = 12,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun downloadAll(
        tasks: List<DownloadTask>,
        onProgress: (DownloadProgress) -> Unit = {}
    ): DownloadResult = withContext(dispatcher) {
        if (tasks.isEmpty()) return@withContext DownloadResult.Success

        val totalTasks = tasks.size
        val totalBytesExpected = tasks.sumOf { it.expectedSize }
        val completedTasks = AtomicInteger(0)
        val downloadedBytes = AtomicLong(0)
        val semaphore = Semaphore(maxConcurrency)

        try {
            coroutineScope {
                val deferreds = tasks.map { task ->
                    async {
                        semaphore.withPermit {
                            downloadSingleTaskWithRetry(
                                task = task,
                                maxRetries = 3,
                                onByteChunk = { bytes ->
                                    val currentTotal = downloadedBytes.addAndGet(bytes)
                                    onProgress(
                                        DownloadProgress(
                                            currentTaskIndex = completedTasks.get(),
                                            totalTasks = totalTasks,
                                            bytesDownloaded = currentTotal,
                                            totalBytes = if (totalBytesExpected > 0) totalBytesExpected else currentTotal,
                                            currentItemName = task.description.ifBlank { File(task.destinationPath).name }
                                        )
                                    )
                                }
                            )
                            val done = completedTasks.incrementAndGet()
                            onProgress(
                                DownloadProgress(
                                    currentTaskIndex = done,
                                    totalTasks = totalTasks,
                                    bytesDownloaded = downloadedBytes.get(),
                                    totalBytes = if (totalBytesExpected > 0) totalBytesExpected else downloadedBytes.get(),
                                    currentItemName = task.description.ifBlank { File(task.destinationPath).name }
                                )
                            )
                        }
                    }
                }
                deferreds.awaitAll()
            }
            DownloadResult.Success
        } catch (e: CancellationException) {
            DownloadResult.Cancelled
        } catch (e: Exception) {
            DownloadResult.Failure(e.message ?: "Download failed", e)
        }
    }

    private suspend fun downloadSingleTaskWithRetry(
        task: DownloadTask,
        maxRetries: Int,
        onByteChunk: (Long) -> Unit
    ) {
        val destFile = File(task.destinationPath)

        // Check if file already exists with valid SHA-1
        if (destFile.exists() && destFile.length() > 0) {
            if (task.expectedSha1 != null && ChecksumVerifier.verifySha1(destFile.absolutePath, task.expectedSha1)) {
                if (task.expectedSize > 0) {
                    onByteChunk(task.expectedSize)
                }
                return
            } else if (task.expectedSha1 == null && task.expectedSize > 0 && destFile.length() == task.expectedSize) {
                onByteChunk(task.expectedSize)
                return
            }
        }

        destFile.parentFile?.mkdirs()
        val tempFile = File("${task.destinationPath}.tmp.${System.nanoTime()}")

        var attempt = 0
        var lastException: Exception? = null

        while (attempt < maxRetries) {
            attempt++
            try {
                if (tempFile.exists()) tempFile.delete()

                val statement = httpClient.get(task.url)
                val channel: ByteReadChannel = statement.bodyAsChannel()

                FileOutputStream(tempFile).use { fos ->
                    val buffer = ByteArray(8192)
                    while (!channel.isClosedForRead) {
                        val packet = channel.readRemaining(8192)
                        while (!packet.isEmpty) {
                            val bytes = packet.readBytes()
                            fos.write(bytes)
                            onByteChunk(bytes.size.toLong())
                        }
                    }
                }

                // Verify SHA-1 if provided
                if (task.expectedSha1 != null && !ChecksumVerifier.verifySha1(tempFile.absolutePath, task.expectedSha1)) {
                    throw IllegalStateException("Checksum verification failed for ${task.url}. Expected: ${task.expectedSha1}, Actual: ${ChecksumVerifier.computeSha1(tempFile.absolutePath)}")
                }

                // Atomic move to destination
                if (destFile.exists()) destFile.delete()
                if (!tempFile.renameTo(destFile)) {
                    tempFile.copyTo(destFile, overwrite = true)
                    tempFile.delete()
                }

                return
            } catch (e: CancellationException) {
                if (tempFile.exists()) tempFile.delete()
                throw e
            } catch (e: Exception) {
                lastException = e
                if (tempFile.exists()) tempFile.delete()
                if (attempt >= maxRetries) {
                    throw IllegalStateException("Failed to download ${task.url} after $maxRetries attempts: ${e.message}", e)
                }
            }
        }

        if (tempFile.exists()) tempFile.delete()
        throw lastException ?: IllegalStateException("Failed to download ${task.url}")
    }
}
