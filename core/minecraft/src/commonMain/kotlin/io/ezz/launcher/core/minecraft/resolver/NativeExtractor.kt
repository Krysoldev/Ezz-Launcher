package io.ezz.launcher.core.minecraft.resolver

import io.ezz.launcher.core.storage.path.PathProvider
import okio.FileSystem
import okio.Path
import java.util.zip.ZipFile

object NativeExtractor {
    fun extractNatives(
        nativeJars: List<Path>,
        instanceNativesDir: Path,
        fileSystem: FileSystem = FileSystem.SYSTEM
    ) {
        if (!fileSystem.exists(instanceNativesDir)) {
            fileSystem.createDirectories(instanceNativesDir)
        }

        for (nativeJar in nativeJars) {
            val jarFile = nativeJar.toFile()
            if (!jarFile.exists()) continue

            try {
                ZipFile(jarFile).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory) continue
                        val name = entry.name
                        if (name.startsWith("META-INF/")) continue

                        // Only extract native library extensions
                        if (name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".dylib") || name.endsWith(".jnilib")) {
                            val fileName = name.substringAfterLast('/')
                            val destFile = instanceNativesDir.resolve(fileName).toFile()
                            destFile.parentFile?.mkdirs()
                            zip.getInputStream(entry).use { input ->
                                destFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                println("Warning: failed to extract natives from $nativeJar: ${e.message}")
            }
        }
    }
}
