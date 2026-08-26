package io.ezz.launcher.core.minecraft.version

import io.ezz.launcher.core.model.minecraft.VersionInfo

/**
 * Validates and resolves Java version requirements for Minecraft Java Edition.
 */
object JavaCompatibility {

    /**
     * Resolves the required Java major version for a given Minecraft version.
     * Checks explicit VersionInfo metadata first, then falls back to official Mojang version heuristics.
     */
    fun getRequiredJavaMajorVersion(versionId: String, versionInfo: VersionInfo? = null): Int {
        // 1. If versionInfo has explicit javaVersion, use it
        versionInfo?.javaVersion?.majorVersion?.let { major ->
            if (major > 0) return major
        }

        // 2. Heuristic based on Minecraft version history
        return when {
            // Minecraft 1.20.5+ requires Java 21
            isVersionGreaterOrEqual(versionId, 1, 20, 5) -> 21
            // Minecraft 1.18 - 1.20.4 requires Java 17
            isVersionGreaterOrEqual(versionId, 1, 18, 0) -> 17
            // Minecraft 1.17.x requires Java 16 / 17
            isVersionGreaterOrEqual(versionId, 1, 17, 0) -> 17
            // Minecraft 1.0 - 1.16.5 and Beta/Alpha requires Java 8
            else -> 8
        }
    }

    /**
     * Returns a human-friendly description of Java requirements.
     */
    fun getJavaRequirementDescription(majorVersion: Int): String {
        return when (majorVersion) {
            21 -> "Java 21 (Modern)"
            17 -> "Java 17 (LTS)"
            16 -> "Java 16"
            8 -> "Java 8 (Legacy)"
            else -> "Java $majorVersion"
        }
    }

    /**
     * Checks if an installed Java runtime major version satisfies the requirement.
     */
    fun isJavaVersionCompatible(installedMajorVersion: Int, requiredMajorVersion: Int): Boolean {
        return when (requiredMajorVersion) {
            21 -> installedMajorVersion >= 21
            17 -> installedMajorVersion in 17..20 || installedMajorVersion >= 21 // Java 21 can often run Java 17 MC
            16 -> installedMajorVersion in 16..20 || installedMajorVersion >= 17
            8 -> installedMajorVersion == 8 || installedMajorVersion <= 17 // Java 8 is best for legacy MC, but Java 11/17 with flags might run some 1.16.5
            else -> installedMajorVersion >= requiredMajorVersion
        }
    }

    private fun isVersionGreaterOrEqual(versionId: String, targetMajor: Int, targetMinor: Int, targetPatch: Int): Boolean {
        val numbers = versionId.replace(Regex("[^0-9.]"), "")
            .split(".")
            .mapNotNull { it.toIntOrNull() }

        if (numbers.isEmpty()) return false

        val major = numbers.getOrElse(0) { 0 }
        val minor = numbers.getOrElse(1) { 0 }
        val patch = numbers.getOrElse(2) { 0 }

        if (major != targetMajor) return major > targetMajor
        if (minor != targetMinor) return minor > targetMinor
        return patch >= targetPatch
    }
}
