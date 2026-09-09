package io.ezz.launcher.core.model.instance

import kotlinx.serialization.Serializable

/**
 * Authoritative central classification for Minecraft instance content types
 * and their dedicated filesystem directories.
 */
@Serializable
enum class InstanceContentType(
    val directoryName: String,
    val displayName: String
) {
    MOD("mods", "Mod"),
    RESOURCE_PACK("resourcepacks", "Resource Pack"),
    SHADER("shaderpacks", "Shader Pack"),
    WORLD("saves", "World"),
    SCREENSHOT("screenshots", "Screenshot");

    companion object {
        fun fromDirectoryName(name: String): InstanceContentType? {
            val normalized = name.trim().lowercase()
            return entries.firstOrNull { it.directoryName.equals(normalized, ignoreCase = true) }
        }

        fun fromModrinthType(projectType: String): InstanceContentType {
            return when (projectType.trim().lowercase()) {
                "mod" -> MOD
                "resourcepack", "resource_pack", "resource-pack" -> RESOURCE_PACK
                "shader", "shaderpack", "shader_pack", "shader-pack" -> SHADER
                else -> MOD
            }
        }
    }
}
