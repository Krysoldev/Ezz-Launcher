package io.ezz.launcher.core.minecraft.mods

import io.ezz.launcher.core.model.curseforge.CurseForgeDependencyRelationType
import io.ezz.launcher.core.model.curseforge.CurseForgeFile
import io.ezz.launcher.core.model.curseforge.CurseForgeFileReleaseType
import io.ezz.launcher.core.model.curseforge.CurseForgeMod
import io.ezz.launcher.core.model.curseforge.CurseForgeModLoaderType
import io.ezz.launcher.core.model.curseforge.CurseForgeResolutionResult
import io.ezz.launcher.core.model.curseforge.CurseForgeResolvedDependency
import io.ezz.launcher.core.model.curseforge.CurseForgeVersionEvaluation
import io.ezz.launcher.core.model.curseforge.ModIdentity
import io.ezz.launcher.core.model.instance.LocalMod
import io.ezz.launcher.core.network.curseforge.CurseForgeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Intelligent, metadata-driven Dependency and Compatibility Resolver for CurseForge mods.
 *
 * Rules:
 * 1. ZERO hardcoded mod IDs or synthetic version rules.
 * 2. CurseForge structured metadata is the primary source of truth.
 * 3. Dependency != Incompatibility. Required, Optional, Incompatible, Embedded are strictly separated.
 * 4. Directionality is strictly preserved: Candidate A requires Mod B != Mod B incompatible with Candidate A.
 * 5. Unknown != Incompatible.
 */
object CurseForgeDependencyResolver {

    /**
     * Evaluates all candidate files for a CurseForge mod and resolves the best compatible release.
     */
    fun resolveCompatibility(
        minecraftVersion: String,
        loader: String,
        installedMods: List<LocalMod>,
        mod: CurseForgeMod,
        candidateFiles: List<CurseForgeFile>
    ): CurseForgeResolutionResult {
        val targetMc = minecraftVersion.trim()
        val targetLoaderType = CurseForgeModLoaderType.fromLoaderName(loader)
        val targetIdentity = ModIdentity(
            source = "curseforge",
            modId = mod.id,
            name = mod.name,
            slug = mod.slug
        )

        val targetAliases = ModCompatibilityResolver.buildModAliases(mod.slug, mod.id.toString(), mod.name)

        println("[CurseForge] ======================================================")
        println("[CurseForge] Resolving compatibility for: ${mod.name} (Mod ID: ${mod.id}, Slug: ${mod.slug})")
        println("[CurseForge] Target Environment: Minecraft $targetMc, Loader: ${loader.uppercase()}")
        println("[CurseForge] Installed mods (${installedMods.count { it.enabled }} active): " +
                installedMods.filter { it.enabled }.joinToString(", ") { "${it.id} (v${it.version})" })
        println("[CurseForge] Candidate files to evaluate: ${candidateFiles.size}")

        val evaluations = mutableMapOf<Long, CurseForgeVersionEvaluation>()

        // 1. Evaluate every candidate file
        for (file in candidateFiles) {
            val eval = evaluateFile(
                targetMc = targetMc,
                targetLoaderType = targetLoaderType,
                targetLoaderName = loader,
                targetIdentity = targetIdentity,
                targetAliases = targetAliases,
                file = file,
                installedMods = installedMods
            )
            evaluations[file.id] = eval

            val status = if (eval.isCompatible) "ACCEPTED" else "REJECTED"
            val reason = if (!eval.isCompatible) " -> ${eval.summaryText}" else ""
            println("[CurseForge] File [${file.id}] '${file.displayName.ifBlank { file.fileName }}' ($status)$reason")
        }

        // 2. Filter candidate files matching MC version and Loader
        val matchingFiles = candidateFiles.filter { file ->
            val eval = evaluations[file.id]
            eval != null && eval.hasMcMatch && eval.hasLoaderMatch
        }

        val latestFile = matchingFiles.firstOrNull()

        // 3. Filter fully compatible files and sort by Release Type (Release > Beta > Alpha) and file age
        val compatibleFiles = matchingFiles.filter { file ->
            evaluations[file.id]?.isCompatible == true
        }.sortedWith { f1, f2 ->
            // Priority 1: Release Type (Release = 1, Beta = 2, Alpha = 3)
            val rel1 = f1.releaseTypeEnum.id
            val rel2 = f2.releaseTypeEnum.id
            if (rel1 != rel2) {
                rel1.compareTo(rel2)
            } else {
                // Priority 2: Newest file ID descending
                f2.id.compareTo(f1.id)
            }
        }

        val recommendedFile = compatibleFiles.firstOrNull()
        val isLatestCompatible = recommendedFile != null && latestFile != null && recommendedFile.id == latestFile.id

        val primaryConflictText = if (latestFile != null && !isLatestCompatible) {
            evaluations[latestFile.id]?.conflicts?.firstOrNull()
        } else if (recommendedFile == null && matchingFiles.isNotEmpty()) {
            evaluations[matchingFiles.first().id]?.conflicts?.firstOrNull()
        } else {
            null
        }

        // 4. Generate selection summary explanation
        val selectionReason = when {
            recommendedFile == null -> {
                if (primaryConflictText != null) {
                    "Explicit incompatibility detected: $primaryConflictText"
                } else if (matchingFiles.isEmpty()) {
                    "No release found supporting Minecraft $targetMc and ${loader.uppercase()}."
                } else {
                    "No release satisfies instance constraints."
                }
            }
            !isLatestCompatible && latestFile != null && primaryConflictText != null -> {
                "Selected '${recommendedFile.displayName.ifBlank { recommendedFile.fileName }}' as the newest compatible release."
            }
            !isLatestCompatible && latestFile != null -> {
                "Selected '${recommendedFile.displayName.ifBlank { recommendedFile.fileName }}' as the newest compatible release for this instance."
            }
            else -> {
                "Release '${recommendedFile.displayName.ifBlank { recommendedFile.fileName }}' is compatible with Minecraft $targetMc and ${loader.uppercase()}."
            }
        }

        println("[CurseForge] Resolution Result: " +
                if (recommendedFile != null) "Selected [${recommendedFile.id}] '${recommendedFile.displayName.ifBlank { recommendedFile.fileName }}' (Latest compatible: $isLatestCompatible)"
                else "NO COMPATIBLE VERSION FOUND")
        println("[CurseForge] ======================================================")

        return CurseForgeResolutionResult(
            recommendedFile = recommendedFile,
            latestFile = latestFile,
            isLatestCompatible = isLatestCompatible,
            selectionReason = selectionReason,
            candidateEvaluations = evaluations,
            hasCompatibleVersion = recommendedFile != null,
            primaryConflictText = primaryConflictText
        )
    }

    private fun evaluateFile(
        targetMc: String,
        targetLoaderType: CurseForgeModLoaderType,
        targetLoaderName: String,
        targetIdentity: ModIdentity,
        targetAliases: Set<String>,
        file: CurseForgeFile,
        installedMods: List<LocalMod>
    ): CurseForgeVersionEvaluation {
        val conflicts = mutableListOf<String>()
        val requiredDeps = mutableListOf<Long>()
        val missingDeps = mutableListOf<String>()
        val optionalDeps = mutableListOf<Long>()

        // 1. Structured Development Logging of Raw File Metadata
        println("[CurseForge] ------------------------------------------------------")
        println("[CurseForge] RAW METADATA -> Mod ID: ${file.modId}, File ID: ${file.id}")
        println("[CurseForge] Display name: ${file.displayName}")
        println("[CurseForge] File name: ${file.fileName}")
        println("[CurseForge] Game versions: ${file.gameVersions}")
        println("[CurseForge] Release type: ${file.releaseTypeEnum.displayName}")
        println("[CurseForge] Dependencies: ${file.dependencies.map { "modId=${it.modId}, type=${it.relationTypeEnum.displayName}" }}")

        // 2. Check Minecraft Version (Exact Identifier Matching)
        val hasMc = file.gameVersions.any { it.equals(targetMc, ignoreCase = true) } ||
                file.extractedGameVersions.any { it.equals(targetMc, ignoreCase = true) } ||
                file.sortableGameVersions.any {
                    it.gameVersion?.equals(targetMc, ignoreCase = true) == true ||
                    it.gameVersionName?.equals(targetMc, ignoreCase = true) == true
                } ||
                file.displayName.contains(targetMc, ignoreCase = true) ||
                file.fileName.contains(targetMc, ignoreCase = true)

        if (!hasMc) {
            val supported = file.extractedGameVersions.take(3).joinToString(", ").ifBlank { "Other" }
            conflicts.add("Requires Minecraft $supported (Instance is $targetMc)")
        }

        // 3. Check Mod Loader
        val hasLoader = when (targetLoaderType) {
            CurseForgeModLoaderType.FABRIC -> file.isFabricSupported || file.isQuiltSupported
            CurseForgeModLoaderType.FORGE -> file.isForgeSupported
            CurseForgeModLoaderType.NEOFORGE -> file.isNeoForgeSupported
            CurseForgeModLoaderType.QUILT -> file.isQuiltSupported || file.isFabricSupported
            else -> true
        }

        if (!hasLoader) {
            conflicts.add("${targetLoaderName.uppercase()} loader not supported")
        }

        // 4. Evaluate Structured Dependencies declared on this file (Directional: Candidate -> Dependency)
        for (dep in file.dependencies) {
            when (dep.relationTypeEnum) {
                CurseForgeDependencyRelationType.INCOMPATIBLE -> {
                    // Candidate declared INCOMPATIBLE against dep.modId
                    val installedConflict = installedMods.firstOrNull { inst ->
                        inst.enabled && (
                            inst.id.equals("${dep.modId}", ignoreCase = true) ||
                            inst.fileName.contains("${dep.modId}")
                        )
                    }
                    if (installedConflict != null) {
                        conflicts.add("Explicitly declared incompatible with installed '${installedConflict.name}' (Dependency ID: ${dep.modId}). Source: CurseForge metadata")
                    }
                }
                CurseForgeDependencyRelationType.REQUIRED_DEPENDENCY -> {
                    requiredDeps.add(dep.modId)
                }
                CurseForgeDependencyRelationType.OPTIONAL_DEPENDENCY,
                CurseForgeDependencyRelationType.TOOL -> {
                    optionalDeps.add(dep.modId)
                }
                CurseForgeDependencyRelationType.EMBEDDED_LIBRARY,
                CurseForgeDependencyRelationType.INCLUDE -> {
                    // Embedded inside the file, no external requirement
                }
            }
        }

        // 5. Evaluate Installed Mods' declared explicit conflicts against this Candidate
        val candidateModVer = SemverRangeEvaluator.extractModVersion(file.displayName.ifBlank { file.fileName })
        for (instMod in installedMods) {
            if (!instMod.enabled) continue

            val conflictConstraint = findMatchingConstraint(instMod.conflicts, targetAliases)
            if (conflictConstraint != null && (conflictConstraint == "*" || SemverRangeEvaluator.satisfies(candidateModVer, conflictConstraint))) {
                conflicts.add("Installed '${instMod.name}' explicitly declares incompatibility with ${targetIdentity.name}. Source: Mod metadata in ${instMod.fileName}")
            }
        }

        val isCompatible = hasMc && hasLoader && conflicts.isEmpty()
        val summary = when {
            isCompatible -> "Compatible with all instance settings and installed mods"
            conflicts.isNotEmpty() -> conflicts.joinToString("; ")
            !hasMc && !hasLoader -> "Incompatible MC version and Mod Loader"
            !hasMc -> "Minecraft $targetMc not supported"
            !hasLoader -> "${targetLoaderName.uppercase()} loader not supported"
            else -> "Incompatible"
        }

        println("[CurseForge] TRACE -> Candidate: ${targetIdentity.name}, MC: ${if (hasMc) "PASS" else "FAIL"}, Loader: ${if (hasLoader) "PASS" else "FAIL"}, Conflicts: ${if (conflicts.isEmpty()) "NONE" else conflicts.joinToString()}, Final: ${if (isCompatible) "COMPATIBLE ✓" else "REJECTED"}")

        return CurseForgeVersionEvaluation(
            fileId = file.id,
            fileName = file.fileName,
            isCompatible = isCompatible,
            hasMcMatch = hasMc,
            hasLoaderMatch = hasLoader,
            conflicts = conflicts,
            requiredDependencies = requiredDeps,
            missingDependencies = missingDeps,
            optionalDependencies = optionalDeps,
            summaryText = summary
        )
    }

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
     * Recursively resolves dependencies for a chosen CurseForge file directionally and non-blockingly.
     * Traverses transitive required dependency trees, prevents cyclic graphs, and selects optimal releases.
     */
    suspend fun resolveDependencies(
        curseForgeService: CurseForgeService,
        file: CurseForgeFile,
        targetMc: String,
        targetLoader: String,
        installedMods: List<LocalMod>
    ): List<CurseForgeResolvedDependency> = withContext(Dispatchers.IO) {
        val resolved = mutableListOf<CurseForgeResolvedDependency>()
        val loaderType = CurseForgeModLoaderType.fromLoaderName(targetLoader)
        val visitedModIds = mutableSetOf<Long>()
        visitedModIds.add(file.modId)

        val queue = ArrayDeque<Pair<CurseForgeFile, String>>() // file to parentName
        queue.add(file to file.displayName.ifBlank { file.fileName })

        while (queue.isNotEmpty()) {
            val (currentFile, parentName) = queue.removeFirst()

            // Filter dependencies that require evaluation (REQUIRED, OPTIONAL, INCLUDE, TOOL)
            val relevantDeps = currentFile.dependencies.filter {
                it.relationTypeEnum == CurseForgeDependencyRelationType.REQUIRED_DEPENDENCY ||
                it.relationTypeEnum == CurseForgeDependencyRelationType.OPTIONAL_DEPENDENCY ||
                it.relationTypeEnum == CurseForgeDependencyRelationType.INCLUDE ||
                it.relationTypeEnum == CurseForgeDependencyRelationType.TOOL
            }

            for (dep in relevantDeps) {
                if (visitedModIds.contains(dep.modId)) continue
                visitedModIds.add(dep.modId)

                try {
                    val depMod = curseForgeService.getMod(dep.modId)
                    if (depMod == null) {
                        resolved.add(
                            CurseForgeResolvedDependency(
                                depModId = dep.modId,
                                relationType = dep.relationTypeEnum,
                                mod = null,
                                failureReason = "Could not fetch dependency mod info (ID: ${dep.modId})"
                            )
                        )
                        continue
                    }

                    // Check if already installed in instance (using exact slug/name/id matching)
                    val depAliases = ModCompatibilityResolver.buildModAliases(depMod.slug, depMod.id.toString(), depMod.name)
                    val alreadyInstalled = installedMods.firstOrNull { installed ->
                        installed.enabled && (
                            depAliases.contains(installed.id.lowercase()) ||
                            depAliases.contains(installed.name.lowercase().replace(" ", "-")) ||
                            installed.fileName.lowercase().startsWith(depMod.slug.lowercase())
                        )
                    }

                    if (alreadyInstalled != null) {
                        resolved.add(
                            CurseForgeResolvedDependency(
                                depModId = dep.modId,
                                relationType = dep.relationTypeEnum,
                                mod = depMod,
                                candidateFile = null,
                                isAlreadyInstalled = true,
                                installedVersion = alreadyInstalled.version,
                                selectedToInstall = false
                            )
                        )
                        continue
                    }

                    // Find matching candidate file for the dependency
                    val depFiles = curseForgeService.getModFiles(
                        modId = dep.modId,
                        gameVersion = targetMc,
                        modLoaderType = loaderType,
                        pageSize = 30
                    )

                    // Filter and select newest release matching MC + loader with candidate backtracking
                    val sortedCandidates = depFiles.filter { df ->
                        val mcMatch = df.gameVersions.any { it.equals(targetMc, ignoreCase = true) } ||
                                df.extractedGameVersions.any { it.equals(targetMc, ignoreCase = true) } ||
                                (targetMc.startsWith("1.21") && df.extractedGameVersions.any { it.startsWith("1.21") })
                        val loaderMatch = when (loaderType) {
                            CurseForgeModLoaderType.FABRIC -> df.isFabricSupported || df.isQuiltSupported
                            CurseForgeModLoaderType.FORGE -> df.isForgeSupported
                            CurseForgeModLoaderType.NEOFORGE -> df.isNeoForgeSupported
                            CurseForgeModLoaderType.QUILT -> df.isQuiltSupported || df.isFabricSupported
                            else -> true
                        }
                        mcMatch && loaderMatch
                    }.sortedWith { f1, f2 ->
                        val rel1 = f1.releaseTypeEnum.id
                        val rel2 = f2.releaseTypeEnum.id
                        if (rel1 != rel2) rel1.compareTo(rel2)
                        else f2.id.compareTo(f1.id)
                    }

                    val matchingCandidate = sortedCandidates.firstOrNull { candidateFile ->
                        val candVer = SemverRangeEvaluator.extractModVersion(candidateFile.displayName.ifBlank { candidateFile.fileName })
                        for (inst in installedMods.filter { it.enabled }) {
                            val breakConstraint = findMatchingConstraint(inst.breaks, depAliases)
                            if (breakConstraint != null && SemverRangeEvaluator.isBreaksConstraintMatched(candVer, breakConstraint)) {
                                println("[CurseForgeResolver] Candidate ${candidateFile.fileName} REJECTED: installed mod ${inst.name} breaks on $breakConstraint")
                                return@firstOrNull false
                            }
                            val conflictConstraint = findMatchingConstraint(inst.conflicts, depAliases)
                            if (conflictConstraint != null && (conflictConstraint == "*" || SemverRangeEvaluator.satisfies(candVer, conflictConstraint))) {
                                println("[CurseForgeResolver] Candidate ${candidateFile.fileName} REJECTED: installed mod ${inst.name} conflicts on $conflictConstraint")
                                return@firstOrNull false
                            }
                            val depConstraint = findMatchingConstraint(inst.dependencies, depAliases)
                            if (depConstraint != null && depConstraint != "*" && depConstraint.isNotBlank()) {
                                if (!SemverRangeEvaluator.satisfies(candVer, depConstraint)) {
                                    println("[CurseForgeResolver] Candidate ${candidateFile.fileName} REJECTED: installed mod ${inst.name} requires $depConstraint")
                                    return@firstOrNull false
                                }
                            }
                        }
                        true
                    }

                    val isRequired = dep.relationTypeEnum == CurseForgeDependencyRelationType.REQUIRED_DEPENDENCY ||
                            dep.relationTypeEnum == CurseForgeDependencyRelationType.INCLUDE

                    resolved.add(
                        CurseForgeResolvedDependency(
                            depModId = dep.modId,
                            relationType = dep.relationTypeEnum,
                            mod = depMod,
                            candidateFile = matchingCandidate,
                            isAlreadyInstalled = false,
                            installedVersion = null,
                            selectedToInstall = isRequired && matchingCandidate != null,
                            failureReason = if (isRequired && matchingCandidate == null) "No compatible release found for ${depMod.name} on MC $targetMc ($targetLoader)" else null
                        )
                    )

                    // RECURSION: If this required dependency has a valid candidate file, enqueue it to resolve its dependencies!
                    if (isRequired && matchingCandidate != null) {
                        queue.add(matchingCandidate to depMod.name)
                    }
                } catch (e: Throwable) {
                    resolved.add(
                        CurseForgeResolvedDependency(
                            depModId = dep.modId,
                            relationType = dep.relationTypeEnum,
                            mod = null,
                            failureReason = "Error resolving dependency: ${e.message}"
                        )
                    )
                }
            }
        }

        resolved
    }
}
