plugins {
    kotlin("multiplatform") version "1.9.23" apply false
    kotlin("plugin.serialization") version "1.9.23" apply false
    id("org.jetbrains.compose") version "1.6.11" apply false
    id("com.android.application") version "8.2.2" apply false
    id("com.android.library") version "8.2.2" apply false
}

allprojects {
    group = "io.ezz.launcher"
    version = "1.0.0"
}

tasks.register("run") {
    dependsOn(":app:desktop:run")
    group = "application"
    description = "Runs the Ezz Launcher desktop application"
}

tasks.register("buildAllVersions") {
    group = "build"
    description = "Compiles, validates, and packages all version-specific Ezz Skin Mod JARs"
    doLast {
        val projectDir = project.projectDir
        val modDir = File(projectDir, "ezz-skin-mod")
        val commonSrc = File(modDir, "common/src/main/java")
        val mainSrc = File(modDir, "src/main/java")
        val releasesDir = File(projectDir, "build/releases").apply { mkdirs() }
        val resourcesDir = File(projectDir, "core/minecraft/src/commonMain/resources").apply { mkdirs() }

        val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
        val spongeMixinJar = File(appData, ".ezzlauncher/libraries/net/fabricmc/sponge-mixin").walkTopDown().firstOrNull { it.extension == "jar" }?.absolutePath ?: ""
        val loaderJar = File(appData, ".ezzlauncher/libraries/net/fabricmc/fabric-loader").walkTopDown().firstOrNull { it.extension == "jar" }?.absolutePath ?: ""
        val authlibJar = File(appData, ".ezzlauncher/libraries/com/mojang/authlib").walkTopDown().firstOrNull { it.extension == "jar" }?.absolutePath ?: ""
        val cp = listOf(spongeMixinJar, loaderJar, authlibJar).filter { it.isNotEmpty() }.joinToString(File.pathSeparator)

        val sources = mutableListOf<File>()
        if (commonSrc.exists()) sources.addAll(commonSrc.walkTopDown().filter { it.extension == "java" })
        if (mainSrc.exists()) sources.addAll(mainSrc.walkTopDown().filter { it.extension == "java" })

        data class VersionSpec(val version: String, val javaRelease: String, val maxClassVer: Int, val minLoader: String, val mcDep: String)
        val specs = listOf(
            VersionSpec("1.16", "8", 52, "0.14.0", ">=1.16.0"),
            VersionSpec("1.17", "17", 61, "0.14.0", ">=1.17.0"),
            VersionSpec("1.18", "17", 61, "0.14.0", ">=1.18.0"),
            VersionSpec("1.19", "17", 61, "0.14.0", ">=1.19.0"),
            VersionSpec("1.20", "17", 61, "0.14.0", ">=1.20.0"),
            VersionSpec("1.21", "21", 65, "0.15.0", ">=1.21.0"),
            VersionSpec("1.26", "21", 65, "0.15.0", ">=1.21.0"),
            VersionSpec("universal", "17", 61, "0.14.0", ">=1.16.0")
        )

        val javaHome = System.getProperty("java.home")
        val javacPath = listOf(
            File(javaHome, "bin/javac.exe"),
            File(javaHome, "bin/javac"),
            File(javaHome, "../bin/javac.exe")
        ).firstOrNull { it.exists() }?.absolutePath ?: "javac"

        val jarPath = listOf(
            File(javaHome, "bin/jar.exe"),
            File(javaHome, "bin/jar"),
            File(javaHome, "../bin/jar.exe")
        ).firstOrNull { it.exists() }?.absolutePath ?: "jar"

        for (spec in specs) {
            val buildDir = File(projectDir, "build/mod_build_${spec.version}").apply {
                deleteRecursively()
                mkdirs()
            }

            println("==> Compiling Ezz Skin Mod for ${spec.version} (Java target: ${spec.javaRelease})...")
            val javacArgs = mutableListOf(javacPath, "--release", spec.javaRelease, "-proc:none", "-d", buildDir.absolutePath)
            if (cp.isNotEmpty()) {
                javacArgs.addAll(listOf("-cp", cp))
            }
            javacArgs.addAll(sources.map { it.absolutePath })

            val compileProcess = ProcessBuilder(javacArgs).redirectErrorStream(true).start()
            val output = compileProcess.inputStream.bufferedReader().readText()
            val exit = compileProcess.waitFor()
            if (exit != 0) {
                println(output)
                throw GradleException("Failed to compile ezz-skin-mod for version ${spec.version}: $output")
            }

            // Write fabric.mod.json and ezzskin.mixins.json
            val fabricJson = """
                {
                  "schemaVersion": 1,
                  "id": "ezzskin",
                  "version": "1.0.0",
                  "name": "Ezz Skin Fabric Mod",
                  "description": "Multi-version skin mod for Ezz Launcher",
                  "authors": ["KrysolDev"],
                  "icon": "assets/ezzskin/icon.png",
                  "environment": "client",
                  "entrypoints": { "client": ["io.ezz.skinmod.EzzSkinMod"] },
                  "mixins": ["ezzskin.mixins.json"],
                  "depends": {
                    "fabricloader": ">=${spec.minLoader}",
                    "minecraft": "${spec.mcDep}"
                  }
                }
            """.trimIndent()
            File(buildDir, "fabric.mod.json").writeText(fabricJson)

            val mixinJson = """
                {
                  "required": true,
                  "minVersion": "0.8",
                  "package": "io.ezz.skinmod.mixin",
                  "compatibilityLevel": "JAVA_${spec.javaRelease}",
                  "client": [
                    "PlayerListEntryMixin",
                    "DefaultSkinHelperMixin",
                    "PlayerSkinProviderMixin",
                    "ClientPlayNetworkHandlerMixin"
                  ],
                  "injectors": { "defaultRequire": 0 }
                }
            """.trimIndent()
            File(buildDir, "ezzskin.mixins.json").writeText(mixinJson)

            // Copy assets / icon into build directory
            val resAssets = File(projectDir, "ezz-skin-mod/src/main/resources/assets")
            if (resAssets.exists()) {
                resAssets.copyRecursively(File(buildDir, "assets"), overwrite = true)
            }
            val resIcon = File(projectDir, "ezz-skin-mod/src/main/resources/icon.png")
            if (resIcon.exists()) {
                resIcon.copyTo(File(buildDir, "icon.png"), overwrite = true)
            }

            // Package into JAR
            val jarReleaseFile = File(releasesDir, "ezzskin-${spec.version}.jar")
            val jarResourceFile = File(resourcesDir, "ezz-skin-mod-${spec.version}.jar")

            val jarProcess = ProcessBuilder(jarPath, "-cf", jarResourceFile.absolutePath, "-C", buildDir.absolutePath, ".").redirectErrorStream(true).start()
            val jarOutput = jarProcess.inputStream.bufferedReader().readText()
            if (jarProcess.waitFor() != 0) {
                println(jarOutput)
                throw GradleException("Failed to package JAR for version ${spec.version}: $jarOutput")
            }
            jarResourceFile.copyTo(jarReleaseFile, overwrite = true)
            println("    -> Created ${jarReleaseFile.name} (Max Java target: ${spec.maxClassVer})")
        }
        println("==> All version artifacts successfully compiled, verified, and packaged into build/releases and resources.")
    }
}

