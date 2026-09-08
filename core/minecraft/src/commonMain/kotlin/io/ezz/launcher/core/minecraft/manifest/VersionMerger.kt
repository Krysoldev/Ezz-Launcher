package io.ezz.launcher.core.minecraft.manifest

import io.ezz.launcher.core.model.minecraft.Library
import io.ezz.launcher.core.model.minecraft.VersionArguments
import io.ezz.launcher.core.model.minecraft.VersionInfo

object VersionMerger {
    fun merge(child: VersionInfo, parent: VersionInfo): VersionInfo {
        val mergedLibraries = mergeLibraries(child.libraries, parent.libraries)
        val mainClass = child.mainClass.ifBlank { parent.mainClass }
        val assetIndex = child.assetIndex ?: parent.assetIndex
        val assets = child.assets ?: parent.assets
        val downloads = child.downloads ?: parent.downloads
        val javaVersion = child.javaVersion ?: parent.javaVersion

        val childArgs = child.arguments
        val parentArgs = parent.arguments

        val mergedArguments = when {
            childArgs != null && parentArgs != null -> {
                VersionArguments(
                    game = parentArgs.game + childArgs.game,
                    jvm = parentArgs.jvm + childArgs.jvm
                )
            }
            childArgs != null -> childArgs
            parentArgs != null -> parentArgs
            else -> null
        }

        val mergedMinecraftArguments = listOfNotNull(parent.minecraftArguments, child.minecraftArguments)
            .joinToString(" ")
            .ifBlank { null }

        return VersionInfo(
            id = child.id,
            type = child.type,
            mainClass = mainClass,
            assets = assets,
            assetIndex = assetIndex,
            downloads = downloads,
            libraries = mergedLibraries,
            arguments = mergedArguments,
            minecraftArguments = mergedMinecraftArguments,
            javaVersion = javaVersion
        )
    }

    /**
     * Merges loader (child) libraries and vanilla (parent) libraries.
     * Loader libraries take strict precedence over vanilla libraries with the same artifact key.
     * Prevents duplicate/conflicting versions of ASM, Netty, Guava, Gson, Log4j, etc. on the classpath.
     */
    fun mergeLibraries(childLibraries: List<Library>, parentLibraries: List<Library>): List<Library> {
        val result = mutableListOf<Library>()
        val seenArtifactKeys = mutableSetOf<String>()

        // 1. Child (Loader, e.g. Fabric) libraries take priority
        for (lib in childLibraries) {
            val key = getLibraryArtifactKey(lib)
            if (seenArtifactKeys.add(key)) {
                result.add(lib)
            }
        }

        // 2. Parent (Vanilla Minecraft) libraries are included unless overridden by child
        for (lib in parentLibraries) {
            val key = getLibraryArtifactKey(lib)
            if (seenArtifactKeys.add(key)) {
                result.add(lib)
            }
        }

        return result
    }

    /**
     * Extracts the maven artifact key (group:artifact[:classifier]) for deduplication.
     * Examples:
     * - "org.ow2.asm:asm:9.10.1" -> "org.ow2.asm:asm"
     * - "org.ow2.asm:asm:9.6" -> "org.ow2.asm:asm"
     * - "org.lwjgl:lwjgl:3.3.3:natives-windows" -> "org.lwjgl:lwjgl:natives-windows"
     * - "com.google.guava:guava:33.3.1-jre" -> "com.google.guava:guava"
     */
    fun getLibraryArtifactKey(library: Library): String {
        val coordinate = library.name.trim()
        val parts = coordinate.split(":")
        if (parts.size >= 2) {
            val group = parts[0]
            val artifact = parts[1]
            // If classifier exists (4th part and not @ext)
            val classifier = if (parts.size >= 4 && !parts[3].startsWith("@")) parts[3] else null
            return if (classifier != null) {
                "$group:$artifact:$classifier"
            } else {
                "$group:$artifact"
            }
        }
        return coordinate
    }
}
