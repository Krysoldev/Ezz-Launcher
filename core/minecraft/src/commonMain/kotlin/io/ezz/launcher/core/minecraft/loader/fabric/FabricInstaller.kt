package io.ezz.launcher.core.minecraft.loader.fabric

import io.ezz.launcher.core.model.minecraft.VersionInfo
import io.ezz.launcher.core.storage.path.PathProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem

@Serializable
data class FabricLoaderSummary(
    val separator: String = ".",
    val build: Int = 0,
    val maven: String,
    val version: String,
    val stable: Boolean = false
)

@Serializable
data class FabricLoaderVersionEntry(
    val loader: FabricLoaderSummary
)

class FabricMetaClient(
    private val httpClient: HttpClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun getLoaderVersionsForGame(gameVersion: String): List<String> = withContext(dispatcher) {
        try {
            val responseText = httpClient.get("https://meta.fabricmc.net/v2/versions/loader/$gameVersion").bodyAsText()
            val entries = json.decodeFromString<List<FabricLoaderVersionEntry>>(responseText)
            entries.map { it.loader.version }
        } catch (e: Exception) {
            // Fallback to general loader versions
            val responseText = httpClient.get("https://meta.fabricmc.net/v2/versions/loader").bodyAsText()
            val entries = json.decodeFromString<List<FabricLoaderSummary>>(responseText)
            entries.map { it.version }
        }
    }

    suspend fun fetchProfileJson(gameVersion: String, loaderVersion: String): VersionInfo = withContext(dispatcher) {
        val url = "https://meta.fabricmc.net/v2/versions/loader/$gameVersion/$loaderVersion/profile/json"
        val responseText = httpClient.get(url).bodyAsText()
        json.decodeFromString<VersionInfo>(responseText)
    }
}

class FabricInstaller(
    private val fabricMetaClient: FabricMetaClient,
    private val pathProvider: PathProvider,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun install(gameVersion: String, loaderVersion: String): VersionInfo = withContext(dispatcher) {
        val versionId = "fabric-loader-$loaderVersion-$gameVersion"
        val versionDir = pathProvider.versionsDirectory.resolve(versionId)
        val versionFile = versionDir.resolve("$versionId.json")

        val profile = fabricMetaClient.fetchProfileJson(gameVersion, loaderVersion)

        fileSystem.createDirectories(versionDir)
        fileSystem.write(versionFile) {
            writeUtf8(json.encodeToString(profile))
        }

        profile
    }
}
