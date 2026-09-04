package io.ezz.launcher.core.minecraft.launch

import io.ezz.launcher.core.minecraft.resolver.OperatingSystem
import io.ezz.launcher.core.minecraft.resolver.RuleEvaluator
import io.ezz.launcher.core.model.account.Account
import io.ezz.launcher.core.model.account.MicrosoftAccount
import io.ezz.launcher.core.model.account.OfflineAccount
import io.ezz.launcher.core.model.instance.GarbageCollectorType
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.PerformanceProfile
import io.ezz.launcher.core.model.minecraft.Rule
import io.ezz.launcher.core.model.minecraft.VersionInfo
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okio.Path
import java.io.File

object LaunchArgumentBuilder {

    fun buildLaunchCommand(
        instance: Instance,
        account: Account,
        versionInfo: VersionInfo,
        classpathEntries: List<Path>,
        clientJarPath: Path,
        nativesDir: Path,
        assetsDir: Path,
        gameDir: Path,
        javaBinaryPath: String,
        os: OperatingSystem = OperatingSystem.current,
        arch: String = System.getProperty("os.arch") ?: "x86_64",
        defaultWindowWidth: Int = 1280,
        defaultWindowHeight: Int = 720,
        defaultFullscreen: Boolean = false,
        globalJvmArgs: List<String> = emptyList()
    ): List<String> {
        val command = mutableListOf<String>()
        command.add(javaBinaryPath)

        // 1. Memory arguments
        val minMem = instance.minMemoryMb.coerceAtLeast(512)
        val maxMem = instance.maxMemoryMb.coerceAtLeast(minMem)
        command.add("-Xms${minMem}M")
        command.add("-Xmx${maxMem}M")

        // 2. Classpath assembly
        val pathSeparator = if (os == OperatingSystem.WINDOWS) ";" else ":"
        val fullClasspath = (classpathEntries + clientJarPath).joinToString(pathSeparator) { it.toString() }

        val variables = mapOf(
            "auth_player_name" to account.username,
            "version_name" to (versionInfo.id),
            "game_directory" to gameDir.toString(),
            "assets_root" to assetsDir.toString(),
            "assets_index_name" to (versionInfo.assetIndex?.id ?: versionInfo.assets ?: "legacy"),
            "auth_uuid" to account.uuid,
            "auth_access_token" to when (account) {
                is MicrosoftAccount -> account.mcAccessToken
                is OfflineAccount -> "00000000-0000-0000-0000-000000000000"
            },
            "user_type" to when (account) {
                is MicrosoftAccount -> "msa"
                is OfflineAccount -> "legacy"
            },
            "version_type" to versionInfo.type,
            "natives_directory" to nativesDir.toString(),
            "launcher_name" to "EzzLauncher",
            "launcher_version" to "1.0.0",
            "classpath" to fullClasspath,
            "resolution_width" to instance.windowWidth.toString(),
            "resolution_height" to instance.windowHeight.toString()
        )

        // 3. JVM Arguments from Version Metadata
        val jvmArguments = versionInfo.arguments?.jvm
        if (jvmArguments != null && jvmArguments.isNotEmpty()) {
            val jvmArgs = parseArgumentElements(jvmArguments, variables, os, arch)
            command.addAll(jvmArgs)
        } else {
            // Default legacy JVM arguments
            command.add("-Djava.library.path=$nativesDir")
            command.add("-cp")
            command.add(fullClasspath)
        }

        // Ensure classpath is always present
        if (!command.contains("-cp") && !command.contains("-classpath")) {
            command.add("-cp")
            command.add(fullClasspath)
        }

        // 4. Clean, Performance-Focused JVM Flags
        val customArgsJoined = instance.customJvmArgs.joinToString(" ")
        val hasCustomGC = customArgsJoined.contains("-XX:+Use")

        if (!hasCustomGC) {
            when (instance.gcType) {
                GarbageCollectorType.AUTO, GarbageCollectorType.G1GC -> {
                    command.add("-XX:+UseG1GC")
                    command.add("-XX:G1ReservePercent=20")
                    command.add("-XX:MaxGCPauseMillis=50")
                    command.add("-XX:G1HeapRegionSize=32M")
                }
                GarbageCollectorType.ZGC -> {
                    command.add("-XX:+UseZGC")
                    command.add("-XX:+ZGenerational")
                }
                GarbageCollectorType.SHENANDOAH -> {
                    command.add("-XX:+UseShenandoahGC")
                }
            }
        }

        // Performance profile optimizations
        if (instance.performanceProfile == PerformanceProfile.PERFORMANCE ||
            instance.performanceProfile == PerformanceProfile.MAX_FPS ||
            instance.performanceProfile == PerformanceProfile.EXTREME_FPS) {
            if (!command.contains("-XX:+AlwaysPreTouch")) {
                command.add("-XX:+AlwaysPreTouch")
            }
            if (!command.contains("-XX:+ParallelRefProcEnabled")) {
                command.add("-XX:+ParallelRefProcEnabled")
            }
        }

        // Standard modern JVM compatibility & stability flags
        if (!command.contains("-XX:+IgnoreUnrecognizedVMOptions")) {
            command.add("-XX:+IgnoreUnrecognizedVMOptions")
        }
        if (!command.contains("--enable-native-access=ALL-UNNAMED")) {
            command.add("--enable-native-access=ALL-UNNAMED")
        }
        if (!command.contains("-Dsun.stdout.encoding=UTF-8")) {
            command.add("-Dsun.stdout.encoding=UTF-8")
        }
        if (!command.contains("-Dsun.stderr.encoding=UTF-8")) {
            command.add("-Dsun.stderr.encoding=UTF-8")
        }
        if (!command.contains("-Djava.net.preferIPv4Stack=true")) {
            command.add("-Djava.net.preferIPv4Stack=true")
        }

        // Add global JVM arguments from Settings (if not already present)
        for (arg in globalJvmArgs) {
            if (arg.isNotBlank() && !command.contains(arg)) {
                command.add(arg)
            }
        }

        // Add instance custom JVM arguments (excluding empty entries)
        command.addAll(instance.customJvmArgs.filter { it.isNotBlank() })

        // 5. Main Class
        command.add(versionInfo.mainClass)

        // 6. Game Arguments
        val gameArguments = versionInfo.arguments?.game
        val mcArgs = versionInfo.minecraftArguments

        if (gameArguments != null && gameArguments.isNotEmpty()) {
            val gameArgs = parseArgumentElements(gameArguments, variables, os, arch)
            command.addAll(gameArgs)
        } else if (!mcArgs.isNullOrBlank()) {
            val legacyArgs = parseLegacyArguments(mcArgs, variables)
            command.addAll(legacyArgs)
        }

        // Add resolution if specified and not already in game arguments
        val effectiveWidth = if (instance.windowWidth > 0 && instance.windowWidth != 1280) instance.windowWidth else defaultWindowWidth
        val effectiveHeight = if (instance.windowHeight > 0 && instance.windowHeight != 720) instance.windowHeight else defaultWindowHeight

        if (!command.contains("--width") && effectiveWidth > 0 && effectiveHeight > 0) {
            command.add("--width")
            command.add(effectiveWidth.toString())
            command.add("--height")
            command.add(effectiveHeight.toString())
        }

        // Launch in fullscreen if enabled
        if (defaultFullscreen && !command.contains("--fullscreen")) {
            command.add("--fullscreen")
        }

        return command
    }

    private fun parseArgumentElements(
        elements: List<JsonElement>,
        variables: Map<String, String>,
        os: OperatingSystem,
        arch: String
    ): List<String> {
        val result = mutableListOf<String>()

        for (element in elements) {
            when (element) {
                is JsonObject -> {
                    val rulesElement = element["rules"]?.jsonArray
                    val rules = rulesElement?.mapNotNull { ruleJson ->
                        try {
                            kotlinx.serialization.json.Json.decodeFromJsonElement<Rule>(ruleJson)
                        } catch (e: Exception) {
                            null
                        }
                    }

                    if (RuleEvaluator.isAllowed(rules, os, arch)) {
                        val valueElement = element["value"]
                        when (valueElement) {
                            is JsonArray -> {
                                for (subVal in valueElement) {
                                    result.add(substituteVariables(subVal.jsonPrimitive.content, variables))
                                }
                            }
                            is JsonPrimitive -> {
                                result.add(substituteVariables(valueElement.content, variables))
                            }
                            else -> {}
                        }
                    }
                }
                is JsonPrimitive -> {
                    result.add(substituteVariables(element.content, variables))
                }
                else -> {}
            }
        }

        return result
    }

    private fun parseLegacyArguments(template: String, variables: Map<String, String>): List<String> {
        val replaced = substituteVariables(template, variables)
        return replaced.split("\\s+".toRegex()).filter { it.isNotBlank() }
    }

    private fun substituteVariables(template: String, variables: Map<String, String>): String {
        var output = template
        for ((key, value) in variables) {
            output = output.replace("\${$key}", value)
        }
        return output
    }
}
