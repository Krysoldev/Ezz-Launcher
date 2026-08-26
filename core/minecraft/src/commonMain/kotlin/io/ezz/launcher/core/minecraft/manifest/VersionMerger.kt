package io.ezz.launcher.core.minecraft.manifest

import io.ezz.launcher.core.model.minecraft.VersionArguments
import io.ezz.launcher.core.model.minecraft.VersionInfo

object VersionMerger {
    fun merge(child: VersionInfo, parent: VersionInfo): VersionInfo {
        val mergedLibraries = child.libraries + parent.libraries
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
}
