package io.ezz.launcher.core.minecraft.resolver

import io.ezz.launcher.core.model.download.DownloadTask
import io.ezz.launcher.core.model.minecraft.Library
import io.ezz.launcher.core.model.minecraft.VersionInfo
import io.ezz.launcher.core.storage.path.PathProvider
import okio.Path

data class ResolvedLibrary(
    val localPath: Path,
    val isNative: Boolean,
    val downloadTask: DownloadTask?
)

class LibraryResolver(
    private val pathProvider: PathProvider
) {
    fun resolveLibraries(
        versionInfo: VersionInfo,
        currentOs: OperatingSystem = OperatingSystem.current,
        currentArch: String = System.getProperty("os.arch") ?: "x86_64"
    ): List<ResolvedLibrary> {
        val resolved = mutableListOf<ResolvedLibrary>()

        for (library in versionInfo.libraries) {
            if (!RuleEvaluator.isAllowed(library.rules, currentOs, currentArch)) {
                continue
            }

            // 1. Check standard artifact
            val artifact = library.downloads?.artifact
            if (artifact != null) {
                val relPath = artifact.path ?: mavenCoordinateToPath(library.name)
                val localPath = pathProvider.librariesDirectory.resolve(relPath)
                val task = DownloadTask(
                    url = artifact.url,
                    destinationPath = localPath.toString(),
                    expectedSha1 = artifact.sha1,
                    expectedSize = artifact.size,
                    description = "Library: ${library.name}"
                )
                resolved.add(ResolvedLibrary(localPath, isNative = false, downloadTask = task))
            } else if (library.url != null || library.name.isNotBlank()) {
                // Custom maven repository URL (e.g., Fabric libraries)
                val relPath = mavenCoordinateToPath(library.name)
                val localPath = pathProvider.librariesDirectory.resolve(relPath)
                val repoUrl = (library.url ?: "https://libraries.minecraft.net/").trimEnd('/')
                val fullUrl = "$repoUrl/$relPath"
                val task = DownloadTask(
                    url = fullUrl,
                    destinationPath = localPath.toString(),
                    expectedSha1 = null,
                    expectedSize = 0L,
                    description = "Library: ${library.name}"
                )
                resolved.add(ResolvedLibrary(localPath, isNative = false, downloadTask = task))
            }

            // 2. Check native classifiers
            val natives = library.natives
            if (natives != null) {
                val nativeKey = natives[currentOs.standardName]?.replace("\${arch}", getArchBits(currentArch))
                if (nativeKey != null) {
                    val classifierArtifact = library.downloads?.classifiers?.get(nativeKey)
                    if (classifierArtifact != null) {
                        val relPath = classifierArtifact.path ?: mavenCoordinateToPath(library.name, nativeKey)
                        val localPath = pathProvider.librariesDirectory.resolve(relPath)
                        val task = DownloadTask(
                            url = classifierArtifact.url,
                            destinationPath = localPath.toString(),
                            expectedSha1 = classifierArtifact.sha1,
                            expectedSize = classifierArtifact.size,
                            description = "Native: ${library.name} ($nativeKey)"
                        )
                        resolved.add(ResolvedLibrary(localPath, isNative = true, downloadTask = task))
                    }
                }
            }
        }

        return resolved
    }

    private fun getArchBits(arch: String): String {
        val lower = arch.lowercase()
        return if (lower.contains("64")) "64" else "32"
    }

    fun mavenCoordinateToPath(coordinate: String, classifier: String? = null): String {
        val parts = coordinate.split(":")
        if (parts.size < 3) return coordinate.replace(":", "/") + ".jar"

        val group = parts[0].replace(".", "/")
        val name = parts[1]
        val version = parts[2]
        val ext = if (parts.size >= 4 && !parts[3].startsWith("@")) parts[3] else "jar"

        return if (classifier != null) {
            "$group/$name/$version/$name-$version-$classifier.$ext"
        } else {
            "$group/$name/$version/$name-$version.$ext"
        }
    }
}
