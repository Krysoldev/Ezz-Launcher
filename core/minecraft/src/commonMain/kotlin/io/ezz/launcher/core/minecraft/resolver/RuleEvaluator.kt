package io.ezz.launcher.core.minecraft.resolver

import io.ezz.launcher.core.model.minecraft.Rule

enum class OperatingSystem(val standardName: String) {
    WINDOWS("windows"),
    OSX("osx"),
    LINUX("linux"),
    UNKNOWN("unknown");

    companion object {
        val current: OperatingSystem by lazy {
            val osName = System.getProperty("os.name")?.lowercase() ?: ""
            when {
                osName.contains("win") -> WINDOWS
                osName.contains("mac") || osName.contains("darwin") -> OSX
                osName.contains("linux") || osName.contains("unix") -> LINUX
                else -> UNKNOWN
            }
        }
    }
}

object RuleEvaluator {
    fun isAllowed(
        rules: List<Rule>?,
        currentOs: OperatingSystem = OperatingSystem.current,
        currentArch: String = System.getProperty("os.arch") ?: "x86_64",
        features: Map<String, Boolean> = emptyMap()
    ): Boolean {
        if (rules.isNullOrEmpty()) return true

        var allowed = false
        for (rule in rules) {
            val applies = matchesRule(rule, currentOs, currentArch, features)
            if (applies) {
                allowed = rule.action.equals("allow", ignoreCase = true)
            }
        }
        return allowed
    }

    private fun matchesRule(
        rule: Rule,
        currentOs: OperatingSystem,
        currentArch: String,
        features: Map<String, Boolean>
    ): Boolean {
        val os = rule.os
        if (os != null) {
            val ruleOsName = os.name?.lowercase()
            if (ruleOsName != null && ruleOsName != currentOs.standardName) {
                return false
            }
            val ruleArch = os.arch?.lowercase()
            if (ruleArch != null) {
                val archNormalized = currentArch.lowercase()
                val matchesArch = if (ruleArch == "x86") {
                    archNormalized == "x86" || archNormalized == "i386" || archNormalized == "i686"
                } else if (ruleArch == "x86_64" || ruleArch == "amd64") {
                    archNormalized == "x86_64" || archNormalized == "amd64"
                } else if (ruleArch.contains("arm")) {
                    archNormalized.contains("arm") || archNormalized.contains("aarch64")
                } else {
                    archNormalized == ruleArch
                }
                if (!matchesArch) return false
            }
        }

        val ruleFeatures = rule.features
        if (ruleFeatures != null) {
            for ((featureKey, requiredValue) in ruleFeatures) {
                val actualValue = features[featureKey] ?: false
                if (actualValue != requiredValue) {
                    return false
                }
            }
        }

        return true
    }
}
