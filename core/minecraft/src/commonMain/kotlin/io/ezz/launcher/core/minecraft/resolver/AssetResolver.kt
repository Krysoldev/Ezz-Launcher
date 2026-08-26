package io.ezz.launcher.core.minecraft.resolver

import io.ezz.launcher.core.model.download.DownloadTask
import io.ezz.launcher.core.model.minecraft.AssetIndex
import io.ezz.launcher.core.model.minecraft.VersionInfo
import io.ezz.launcher.core.storage.path.PathProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

class AssetResolver(
    private val httpClient: HttpClient,
    private val pathProvider: PathProvider,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun resolveAssetTasks(versionInfo: VersionInfo): List<DownloadTask> = withContext(dispatcher) {
        val assetIndexRef = versionInfo.assetIndex ?: return@withContext emptyList()
        val indexFile = pathProvider.assetsIndexesDirectory.resolve("${assetIndexRef.id}.json")

        var indexContent: String? = null
        if (fileSystem.exists(indexFile)) {
            try {
                indexContent = fileSystem.read(indexFile) { readUtf8() }
            } catch (e: Exception) {
                indexContent = null
            }
        }

        if (indexContent == null) {
            val responseText = httpClient.get(assetIndexRef.url).bodyAsText()
            val parent = indexFile.parent
            if (parent != null && !fileSystem.exists(parent)) {
                fileSystem.createDirectories(parent)
            }
            fileSystem.write(indexFile) {
                writeUtf8(responseText)
            }
            indexContent = responseText
        }

        val assetIndex = json.decodeFromString<AssetIndex>(indexContent)
        val tasks = mutableListOf<DownloadTask>()

        for ((assetPath, assetObj) in assetIndex.objects) {
            val hash = assetObj.hash
            if (hash.length < 2) continue
            val prefix = hash.substring(0, 2)
            val destination = pathProvider.assetsObjectsDirectory.resolve(prefix).resolve(hash)

            tasks.add(
                DownloadTask(
                    url = "https://resources.download.minecraft.net/$prefix/$hash",
                    destinationPath = destination.toString(),
                    expectedSha1 = hash,
                    expectedSize = assetObj.size,
                    description = "Asset: $assetPath"
                )
            )
        }

        tasks
    }
}
