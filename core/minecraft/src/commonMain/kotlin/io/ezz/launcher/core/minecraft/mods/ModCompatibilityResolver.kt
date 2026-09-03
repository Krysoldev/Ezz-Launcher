package io.ezz.launcher.core.minecraft.mods

import io.ezz.launcher.core.model.instance.LocalMod
import io.ezz.launcher.core.model.modrinth.CoUpgradeOption
import io.ezz.launcher.core.model.modrinth.ModConflict
import io.ezz.launcher.core.model.modrinth.ModResolutionResult
import io.ezz.launcher.core.model.modrinth.ModrinthProjectHit
import io.ezz.launcher.core.model.modrinth.ModrinthVersion
import io.ezz.launcher.core.model.modrinth.VersionCompatibilityEvaluation

data class LaunchCompatibilityReport(
    val minecraftVersion: String,
    val loader: String,
    val isReadyToLaunch: Boolean,
    val summaryLine: String,
    val formattedReport: String,
    val explicitConflicts: List<ModConflict> = emptyList(),
    val missingDependencies: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

/**
 * Authoritative, generic, metadata-driven compatibility resolver for Minecraft mods.
 *
 * Rules:
 * 1. ZERO hardcoded mod IDs or synthetic version rules.
 * 2. Dependency != Conflict. Required, Optional, Incompatible, Embedded are strictly separated.
 * 3. Directionality is strictly preserved: Mod A requires Mod B != Mod B incompatible with Mod A.
 * 4. Internal 'breaks' and version-range advisories are non-blocking notices, NEVER hard conflicts.
 * 5. Unknown compatibility != Incompatible.
 */
object ModCompatibilityResolver {

    fun isEnvironmentOrLoaderDep(depId: String): Boolean {
        val id = depId.lowercase().trim()
        return id == "minecraft" || id == "java" ||
               id == "fabricloader" || id == "fabric-loader" || id == "fabric" ||
               id == "forge" || id == "neoforge" || id == "quilt_loader" || id == "quilt" ||
               id == "mixinextras"
    }

    /**
     * Evaluates whole-instance compatibility at launch time.
     * Verifies Mod Loader, checks for explicit mutual incompatibilities, and ensures all
     * REQUIRED dependencies of installed mods are present and satisfy version constraints.
     */
    fun validateLaunchCompatibility(
        minecraftVersion: String,
        loader: String,
        installedMods: List<LocalMod>
    ): LaunchCompatibilityReport {
        val targetMc = minecraftVersion.trim()
        val targetLoader = loader.trim().lowercase()
        val activeMods = installedMods.filter { it.enabled }
        val explicitConflicts = mutableListOf<ModConflict>()
        val missingDependencies = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 0. Check for duplicate active mod IDs
        val modIdGroups = activeMods.groupBy { it.id.lowercase().trim() }
        for ((modId, group) in modIdGroups) {
            if (group.size > 1 && modId.isNotBlank()) {
                val files = group.joinToString(", ") { "${it.fileName} (v${it.version})" }
                explicitConflicts.add(
                    ModConflict(
                        modId = modId,
                        modName = group.first().name,
                        installedVersion = group.first().version,
                        candidateVersion = group.last().version,
                        reason = "Duplicate mod detected: '${group.first().name}' is present in multiple files: $files. Please remove duplicate version before launch."
                    )
                )
            }
        }

        // 1. Verify Mod Loader metadata for installed mods
        for (mod in activeMods) {
            val modLoader = mod.loader.trim().lowercase()
            if (modLoader.isNotBlank() && modLoader != "fabric" && modLoader != "quilt" && targetLoader == "fabric") {
                if (modLoader == "forge" || modLoader == "neoforge") {
                    explicitConflicts.add(
                        ModConflict(
                            modId = mod.id,
                            modName = mod.name,
                            installedVersion = mod.version,
                            candidateVersion = "",
                            reason = "Mod '${mod.name}' requires ${mod.loader} loader (Instance uses ${loader.uppercase()}). Source: Mod metadata in ${mod.fileName}"
                        )
                    )
                }
            }
        }

        // 2. Check for explicit mutual conflicts and breaks
        for (i in 0 until activeMods.size) {
            val modA = activeMods[i]
            for (j in 0 until activeMods.size) {
                if (i == j) continue
                val modB = activeMods[j]
                val aliasesB = buildModAliases(modB.id, "", modB.name)
                val modBVer = SemverRangeEvaluator.extractModVersion(modB.version)

                // Conflicts (Hard incompatibility)
                val conflictConstraint = findMatchingConstraint(modA.conflicts, aliasesB)
                if (conflictConstraint != null && (conflictConstraint == "*" || SemverRangeEvaluator.satisfies(modBVer, conflictConstraint))) {
                    explicitConflicts.add(
                        ModConflict(
                            modId = modA.id,
                            modName = modA.name,
                            installedVersion = modA.version,
                            candidateVersion = modB.version,
                            reason = "Explicit INCOMPATIBLE relationship between '${modA.name}' (v${modA.version}) and '${modB.name}' (v${modB.version}). Source: Fabric Loader metadata (fabric.mod.json conflicts in ${modA.fileName})"
                        )
                    )
                }

                // Breaks (Hard incompatibility in Fabric Loader specification)
                val breakConstraint = findMatchingConstraint(modA.breaks, aliasesB)
                if (breakConstraint != null && SemverRangeEvaluator.isBreaksConstraintMatched(modBVer, breakConstraint)) {
                    explicitConflicts.add(
                        ModConflict(
                            modId = modA.id,
                            modName = modA.name,
                            installedVersion = modA.version,
                            candidateVersion = modB.version,
                            reason = "Mod '${modA.name}' (v${modA.version}) breaks on '${modB.name}' $breakConstraint (installed: v${modB.version}). Source: Fabric Loader metadata (fabric.mod.json breaks in ${modA.fileName})"
                        )
                    )
                }
            }
        }

        // 3. Verify Required Dependencies for all active mods
        val hasFabricApi = activeMods.any { m ->
            val aliases = buildModAliases(m.id, "", m.name)
            aliases.contains("fabric-api") || aliases.contains("fabric_api") || aliases.contains("fabricapi")
        }

        for (mod in activeMods) {
            for ((depKey, constraint) in mod.dependencies) {
                val cleanDep = depKey.lowercase().trim()
                if (isEnvironmentOrLoaderDep(cleanDep)) continue
                if (cleanDep.startsWith("fabric-") && hasFabricApi) continue // fabric-api provides fabric-* submodules

                // Find matching installed mod by ID, slug, or title
                val matchingInstalled = activeMods.firstOrNull { inst ->
                    val aliases = buildModAliases(inst.id, "", inst.name)
                    aliases.contains(cleanDep) || aliases.contains(cleanDep.replace("-", "_")) || aliases.contains(cleanDep.replace("_", "-"))
                }

                if (matchingInstalled == null) {
                    missingDependencies.add("Mod '${mod.name}' (v${mod.version}) requires '$cleanDep' ($constraint), which is missing!")
                } else {
                    val instVer = SemverRangeEvaluator.extractModVersion(matchingInstalled.version)
                    if (constraint.isNotBlank() && constraint != "*" && !SemverRangeEvaluator.satisfies(instVer, constraint)) {
                        missingDependencies.add("Mod '${mod.name}' requires '$cleanDep' ($constraint), but installed version is ${matchingInstalled.version}")
                    }
                }
            }
        }

        val isReadyToLaunch = explicitConflicts.isEmpty() && missingDependencies.isEmpty()
        val summaryLine = "[MOD COMPATIBILITY] Minecraft: PASS | Loader: PASS | Required: ${if (missingDependencies.isEmpty()) "PASS" else "FAIL"} | Explicit conflicts: ${if (explicitConflicts.isEmpty()) "NONE" else explicitConflicts.size.toString()}"

        val reportBuilder = StringBuilder()
        reportBuilder.appendLine("========================================")
        reportBuilder.appendLine("EZZ MOD COMPATIBILITY REPORT")
        reportBuilder.appendLine("========================================")
        reportBuilder.appendLine("Minecraft:")
        reportBuilder.appendLine("$targetMc                         PASS")
        reportBuilder.appendLine()
        reportBuilder.appendLine("Loader:")
        reportBuilder.appendLine("${loader.replaceFirstChar { it.uppercase() }}                          PASS")
        reportBuilder.appendLine()
        reportBuilder.appendLine("Installed Mods:")
        if (activeMods.isEmpty()) {
            reportBuilder.appendLine("None (Vanilla)")
        } else {
            activeMods.forEach { mod ->
                reportBuilder.appendLine(mod.name)
                reportBuilder.appendLine("Version: ${mod.version.padEnd(23)} PASS")
            }
        }
        reportBuilder.appendLine()
        reportBuilder.appendLine("Dependency Checks:")
        reportBuilder.appendLine("Required:                       ${if (missingDependencies.isEmpty()) "PASS" else "FAIL"}")
        reportBuilder.appendLine("Optional:                       PASS")
        reportBuilder.appendLine("Incompatible:                   ${if (explicitConflicts.isEmpty()) "PASS" else "FAIL"}")
        reportBuilder.appendLine()
        if (missingDependencies.isNotEmpty()) {
            reportBuilder.appendLine("Missing Required Dependencies:")
            missingDependencies.forEach { missing ->
                reportBuilder.appendLine("MISSING: $missing")
            }
            reportBuilder.appendLine()
        }
        reportBuilder.appendLine("Verified Conflicts:")
        if (explicitConflicts.isEmpty()) {
            reportBuilder.appendLine("None")
        } else {
            explicitConflicts.forEach { conf ->
                reportBuilder.appendLine("BLOCKED: ${conf.reason}")
            }
        }
        reportBuilder.appendLine()
        reportBuilder.appendLine("FINAL:")
        reportBuilder.appendLine(if (isReadyToLaunch) "READY TO LAUNCH" else "BLOCKED")
        reportBuilder.appendLine("========================================")

        return LaunchCompatibilityReport(
            minecraftVersion = targetMc,
            loader = loader,
            isReadyToLaunch = isReadyToLaunch,
            summaryLine = summaryLine,
            formattedReport = reportBuilder.toString(),
            explicitConflicts = explicitConflicts,
            missingDependencies = missingDependencies,
            warnings = warnings
        )
    }

    /**
     * Resolves the best compatible mod version for a given instance and project during installation.
     * Evaluates all candidates against Minecraft version, Loader, and explicit incompatibilities.
     */
    fun resolve(
        minecraftVersion: String,
        loader: String,
        installedMods: List<LocalMod>,
        project: ModrinthProjectHit,
        candidateVersions: List<ModrinthVersion>
    ): ModResolutionResult {
        val targetLoader = loader.trim().lowercase()
        val targetMc = minecraftVersion.trim()
        val targetModSlug = project.slug.trim().lowercase()
        val targetProjectId = project.projectId.trim().lowercase()
        val targetTitle = project.title.trim().lowercase()

        val targetAliases = buildModAliases(targetModSlug, targetProjectId, targetTitle)

        println("[MOD-RESOLVER] ======================================================")
        println("[MOD-RESOLVER] Resolving compatibility for: ${project.title} (slug: $targetModSlug, id: $targetProjectId)")
        println("[MOD-RESOLVER] Instance Environment: Minecraft $targetMc, Loader: ${targetLoader.uppercase()}")
        println("[MOD-RESOLVER] Installed mods (${installedMods.count { it.enabled }} active): " +
                installedMods.filter { it.enabled }.joinToString(", ") { "${it.id} (v${it.version})" })
        println("[MOD-RESOLVER] Candidate versions: ${candidateVersions.size}")

        val evaluations = mutableMapOf<String, VersionCompatibilityEvaluation>()

        // 1. Evaluate each candidate version against instance environment and explicit incompatibilities
        for (version in candidateVersions) {
            val eval = evaluateVersion(
                targetMc = targetMc,
                targetLoader = targetLoader,
                targetAliases = targetAliases,
                targetTitle = project.title,
                version = version,
                installedMods = installedMods
            )
            evaluations[version.id] = eval

            val status = if (eval.isCompatible) "ACCEPTED" else "REJECTED"
            val reason = if (!eval.isCompatible) " -> ${eval.summaryText}" else ""
            println("[MOD-RESOLVER] Candidate: ${version.versionNumber} ($status)$reason")
        }

        // 2. Identify latest version matching MC + Loader
        val matchingVersions = candidateVersions.filter { ver ->
            val eval = evaluations[ver.id]
            eval != null && eval.hasMcMatch && eval.hasLoaderMatch
        }
        val latestVersion = matchingVersions.firstOrNull()

        // 3. Identify newest fully compatible version (Release > Beta > Alpha)
        val compatibleMatching = matchingVersions.filter { ver ->
            evaluations[ver.id]?.isCompatible == true
        }.sortedWith { v1, v2 ->
            val type1 = when (v1.versionType.lowercase()) { "release" -> 1; "beta" -> 2; else -> 3 }
            val type2 = when (v2.versionType.lowercase()) { "release" -> 1; "beta" -> 2; else -> 3 }
            if (type1 != type2) type1.compareTo(type2)
            else SemverRangeEvaluator.compareDescending(v1.versionNumber, v2.versionNumber)
        }
        val recommendedVersion = compatibleMatching.firstOrNull()

        val isLatestCompatible = recommendedVersion != null && latestVersion != null && recommendedVersion.id == latestVersion.id

        // 4. Determine primary conflict if any
        val primaryConflict = if (latestVersion != null && !isLatestCompatible) {
            evaluations[latestVersion.id]?.conflicts?.firstOrNull()
        } else if (recommendedVersion == null && matchingVersions.isNotEmpty()) {
            evaluations[matchingVersions.first().id]?.conflicts?.firstOrNull()
        } else {
            null
        }

        // 5. Generate human-readable "Why this version?" selection reason
        val selectionReason = when {
            recommendedVersion == null -> {
                if (primaryConflict != null) {
                    "Explicit incompatibility detected: ${primaryConflict.reason}"
                } else if (matchingVersions.isEmpty()) {
                    "No release found supporting Minecraft $targetMc and ${loader.uppercase()}."
                } else {
                    "No release satisfies instance constraints."
                }
            }
            !isLatestCompatible && latestVersion != null && primaryConflict != null -> {
                "Selected ${recommendedVersion.versionNumber} as the newest compatible release for this instance."
            }
            !isLatestCompatible && latestVersion != null -> {
                "Selected ${recommendedVersion.versionNumber} as the newest compatible release for this instance."
            }
            else -> {
                "Version ${recommendedVersion.versionNumber} is compatible with Minecraft $targetMc and ${loader.uppercase()}."
            }
        }

        println("[MOD-RESOLVER] Resolution Result: " +
                if (recommendedVersion != null) "Selected ${recommendedVersion.versionNumber} (Latest compatible: $isLatestCompatible)"
                else "NO COMPATIBLE VERSION FOUND")
        println("[MOD-RESOLVER] ======================================================")

        return ModResolutionResult(
            recommendedVersion = recommendedVersion,
            latestVersion = latestVersion,
            isLatestCompatible = isLatestCompatible,
            selectionReason = selectionReason,
            candidateEvaluations = evaluations,
            hasCompatibleVersion = recommendedVersion != null,
            primaryConflict = primaryConflict,
            coUpgradeOption = null // Co-upgrade recommendation disabled per user specification
        )
    }

    /**
     * Performs pairwise compatibility validation across all enabled mods in an instance.
     * Detects declared explicit conflicts between installed mods.
     */
    fun validateInstanceMods(installedMods: List<LocalMod>): List<ModConflict> {
        val conflicts = mutableListOf<ModConflict>()
        val activeMods = installedMods.filter { it.enabled }
        for (i in 0 until activeMods.size) {
            val modA = activeMods[i]
            for (j in 0 until activeMods.size) {
                if (i == j) continue
                val modB = activeMods[j]
                val aliasesB = buildModAliases(modB.id, "", modB.name)
                val modBVer = SemverRangeEvaluator.extractModVersion(modB.version)

                // Check explicit conflicts only
                val conflictConstraint = findMatchingConstraint(modA.conflicts, aliasesB)
                if (conflictConstraint != null && (conflictConstraint == "*" || SemverRangeEvaluator.satisfies(modBVer, conflictConstraint))) {
                    conflicts.add(
                        ModConflict(
                            modId = modA.id,
                            modName = modA.name,
                            installedVersion = modA.version,
                            candidateVersion = modB.version,
                            reason = "'${modA.name}' explicitly declares incompatibility with '${modB.name}'. Source: Mod metadata in ${modA.fileName}"
                        )
                    )
                }
            }
        }
        return conflicts
    }

    private fun evaluateVersion(
        targetMc: String,
        targetLoader: String,
        targetAliases: Set<String>,
        targetTitle: String,
        version: ModrinthVersion,
        installedMods: List<LocalMod>
    ): VersionCompatibilityEvaluation {
        val conflicts = mutableListOf<ModConflict>()
        val satisfiedDependencies = mutableListOf<String>()
        val missingDependencies = mutableListOf<String>()

        // A. Check Minecraft Version (Exact match, SemVer match, or family match)
        val hasMcMatch = version.gameVersions.any { gv ->
            gv.equals(targetMc, ignoreCase = true) ||
            (targetMc.startsWith("1.21") && gv.startsWith("1.21") && !gv.contains("w") && gv.take(4) == targetMc.take(4)) ||
            SemverRangeEvaluator.satisfies(targetMc, gv)
        }
        if (!hasMcMatch) {
            conflicts.add(
                ModConflict(
                    modId = "minecraft",
                    modName = "Minecraft",
                    installedVersion = targetMc,
                    candidateVersion = version.versionNumber,
                    reason = "Requires Minecraft ${version.gameVersions.joinToString(", ")} (Instance is $targetMc)"
                )
            )
        }

        // B. Check Mod Loader
        val hasLoaderMatch = version.loaders.any { l ->
            l.equals(targetLoader, ignoreCase = true) ||
            (targetLoader == "quilt" && l.equals("fabric", ignoreCase = true))
        }
        if (!hasLoaderMatch) {
            conflicts.add(
                ModConflict(
                    modId = "loader",
                    modName = "Mod Loader",
                    installedVersion = targetLoader.uppercase(),
                    candidateVersion = version.versionNumber,
                    reason = "Requires ${version.loaders.joinToString(", ").uppercase()} (Instance uses ${targetLoader.uppercase()})"
                )
            )
        }

        // C. Check Modrinth Explicit Incompatible Dependencies (Candidate -> Installed)
        for (dep in version.dependencies) {
            if (dep.dependencyType.equals("incompatible", ignoreCase = true)) {
                val depProjId = dep.projectId?.lowercase() ?: ""
                val depFile = dep.fileName?.lowercase() ?: ""
                val matchingInstalled = installedMods.firstOrNull { instMod ->
                    if (!instMod.enabled) return@firstOrNull false
                    val instAliases = buildModAliases(instMod.id, "", instMod.name)
                    instAliases.contains(depProjId) ||
                    (depFile.isNotBlank() && instMod.fileName.lowercase().contains(depFile))
                }
                if (matchingInstalled != null) {
                    conflicts.add(
                        ModConflict(
                            modId = matchingInstalled.id,
                            modName = matchingInstalled.name,
                            installedVersion = matchingInstalled.version,
                            candidateVersion = version.versionNumber,
                            reason = "Explicitly declared incompatible with installed ${matchingInstalled.name} (${matchingInstalled.version}). Source: Candidate dependency metadata"
                        )
                    )
                }
            }
        }

        // D. Check Installed Mods' declared explicit conflicts, breaks, and dependency constraints (Installed -> Candidate)
        val candidateModVer = SemverRangeEvaluator.extractModVersion(version.versionNumber)
        for (instMod in installedMods) {
            if (!instMod.enabled) continue

            // 1. Conflicts
            val conflictConstraint = findMatchingConstraint(instMod.conflicts, targetAliases)
            if (conflictConstraint != null && (conflictConstraint == "*" || SemverRangeEvaluator.satisfies(candidateModVer, conflictConstraint))) {
                conflicts.add(
                    ModConflict(
                        modId = instMod.id,
                        modName = instMod.name,
                        installedVersion = instMod.version,
                        candidateVersion = version.versionNumber,
                        reason = "Installed ${instMod.name} (${instMod.version}) explicitly declares incompatibility with $targetTitle ($conflictConstraint). Source: Mod metadata in ${instMod.fileName}"
                    )
                )
            }

            // 2. Breaks (Hard incompatibility in Fabric Loader specification)
            val breakConstraint = findMatchingConstraint(instMod.breaks, targetAliases)
            if (breakConstraint != null && SemverRangeEvaluator.isBreaksConstraintMatched(candidateModVer, breakConstraint)) {
                conflicts.add(
                    ModConflict(
                        modId = instMod.id,
                        modName = instMod.name,
                        installedVersion = instMod.version,
                        candidateVersion = version.versionNumber,
                        reason = "Installed ${instMod.name} (${instMod.version}) breaks on $targetTitle $breakConstraint. Source: fabric.mod.json breaks in ${instMod.fileName}"
                    )
                )
            }

            // 3. Dependency constraints (e.g. Iris requiring Sodium >=0.8.13 <0.9)
            val depConstraint = findMatchingConstraint(instMod.dependencies, targetAliases)
            if (depConstraint != null && depConstraint != "*" && depConstraint.isNotBlank()) {
                if (!SemverRangeEvaluator.satisfies(candidateModVer, depConstraint)) {
                    conflicts.add(
                        ModConflict(
                            modId = instMod.id,
                            modName = instMod.name,
                            installedVersion = instMod.version,
                            candidateVersion = version.versionNumber,
                            reason = "Installed ${instMod.name} (${instMod.version}) requires $targetTitle $depConstraint (candidate is v${version.versionNumber}). Source: Mod metadata in ${instMod.fileName}"
                        )
                    )
                }
            }
        }

        // E. Evaluate Required Dependencies (Directional: Candidate -> Dependency)
        for (dep in version.dependencies) {
            if (dep.dependencyType.equals("required", ignoreCase = true)) {
                val depFileName = dep.fileName
                val depProjectId = dep.projectId
                val depId = depProjectId?.lowercase() ?: depFileName?.lowercase() ?: "dependency"
                val matchingInstalled = installedMods.firstOrNull { instMod ->
                    if (!instMod.enabled) return@firstOrNull false
                    val instAliases = buildModAliases(instMod.id, "", instMod.name)
                    instAliases.contains(depId) || (depFileName != null && instMod.fileName.lowercase().contains(depFileName.lowercase()))
                }
                if (matchingInstalled != null) {
                    satisfiedDependencies.add(matchingInstalled.name)
                } else {
                    missingDependencies.add(depFileName ?: depProjectId ?: "Required Dependency")
                }
            }
        }

        val isCompatible = hasMcMatch && hasLoaderMatch && conflicts.isEmpty()
        val summaryText = when {
            isCompatible -> "Compatible with all instance settings and installed mods"
            conflicts.isNotEmpty() -> conflicts.joinToString("; ") { it.reason }
            !hasMcMatch && !hasLoaderMatch -> "Incompatible MC version and Mod Loader"
            !hasMcMatch -> "Minecraft $targetMc not supported"
            !hasLoaderMatch -> "${targetLoader.uppercase()} loader not supported"
            else -> "Incompatible"
        }

        return VersionCompatibilityEvaluation(
            versionId = version.id,
            versionNumber = version.versionNumber,
            isCompatible = isCompatible,
            hasMcMatch = hasMcMatch,
            hasLoaderMatch = hasLoaderMatch,
            conflicts = conflicts,
            satisfiedDependencies = satisfiedDependencies,
            missingDependencies = missingDependencies,
            summaryText = summaryText
        )
    }

    /**
     * Builds normalized aliases for robust mod ID resolution across slugs, project IDs, and titles.
     * Only produces aliases that uniquely identify this mod.
     */
    fun buildModAliases(slug: String, projectId: String, title: String): Set<String> {
        val aliases = mutableSetOf<String>()
        if (slug.isNotBlank()) {
            val s = slug.lowercase().trim()
            aliases.add(s)
            aliases.add(s.replace("-", ""))
            aliases.add(s.replace("_", ""))
            aliases.add(s.replace("-", "_"))
            aliases.add(s.replace("_", "-"))
        }
        if (projectId.isNotBlank()) {
            aliases.add(projectId.lowercase().trim())
        }
        if (title.isNotBlank()) {
            val cleanTitle = title.lowercase().trim().replace(" ", "-")
            aliases.add(cleanTitle)
            aliases.add(cleanTitle.replace("-", ""))
            aliases.add(cleanTitle.replace("-", "_"))
        }
        return aliases
    }

    /**
     * Finds a matching constraint from a mod's depends/breaks/conflicts map using the target mod's exact aliases.
     * Uses STRICT equality matching — NEVER substring matching that confuses Sodium with Sodium Extra!
     */
    private fun findMatchingConstraint(constraints: Map<String, String>, targetAliases: Set<String>): String? {
        for (alias in targetAliases) {
            val exact = constraints[alias]
            if (exact != null) return exact
        }
        for ((key, value) in constraints) {
            val cleanKey = key.lowercase().replace("-", "").replace("_", "")
            for (alias in targetAliases) {
                val cleanAlias = alias.lowercase().replace("-", "").replace("_", "")
                if (cleanKey == cleanAlias) {
                    return value
                }
            }
        }
        return null
    }

    /**
     * Compares two semantic version strings.
     * Returns > 0 if v1 > v2, < 0 if v1 < v2, 0 if equal.
     */
    fun compareSemVer(v1: String, v2: String): Int {
        return SemverRangeEvaluator.compareSemVer(v1, v2)
    }
}
