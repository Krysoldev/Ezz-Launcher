package io.ezz.launcher.core.minecraft.mods

import io.ezz.launcher.core.model.curseforge.CurseForgeFile
import io.ezz.launcher.core.model.curseforge.CurseForgeMod
import io.ezz.launcher.core.model.instance.InstallationPlan
import io.ezz.launcher.core.model.instance.LocalMod
import io.ezz.launcher.core.model.instance.PlanActionType
import io.ezz.launcher.core.model.instance.PlanItem
import io.ezz.launcher.core.model.instance.ResolvedEnvironment
import io.ezz.launcher.core.model.modrinth.ModrinthProjectHit
import io.ezz.launcher.core.model.modrinth.ModrinthVersion
import java.io.File

/**
 * Universal candidate version representation for dependency graph solving.
 */
data class CandidateVersion(
    val id: String,
    val versionNumber: String,
    val versionType: String = "release", // "release", "beta", "alpha"
    val gameVersions: List<String>,
    val loaders: List<String>,
    val dependencies: List<CandidateDependency> = emptyList(),
    val files: List<CandidateFile> = emptyList(),
    val displayName: String? = null,
    val raw: Any? = null
)

data class CandidateDependency(
    val idOrSlug: String,
    val versionId: String? = null,
    val fileName: String? = null,
    val dependencyType: String, // "required", "optional", "incompatible", "embedded"
    val versionConstraint: String? = null
)

data class CandidateFile(
    val filename: String,
    val url: String,
    val sizeBytes: Long = 0L,
    val sha1: String? = null,
    val sha512: String? = null,
    val isPrimary: Boolean = true
)

sealed class SolverResult {
    data class Success(
        val plan: InstallationPlan,
        val selectedCandidate: CandidateVersion,
        val selectionReason: String,
        val resolvedDependencies: List<CandidateVersion> = emptyList()
    ) : SolverResult()

    data class Failure(
        val reason: String,
        val candidateRejections: Map<String, String>,
        val primaryConflict: String? = null
    ) : SolverResult()
}

/**
 * The single authoritative, metadata-driven global dependency graph solver for Ezz Launcher.
 *
 * Enforces:
 * 1. ZERO random or silent mod deletions. Existing installed mods are user data and ALWAYS defaulted to KEEP.
 * 2. Automatic compatible version selection: Evaluates candidates in priority order (Exact MC -> Loader ->
 *    Dependencies -> Bidirectional Conflicts -> Minimal Changes -> Release stability).
 * 3. Bidirectional conflict checking: Candidate -> Installed AND Installed -> Candidate.
 * 4. Dependency chain solving: Complete dependency tree solved before any file is touched.
 * 5. Deterministic, immutable InstallationPlan with pre- and post-condition file sets.
 */
object GlobalModDependencySolver {

    fun isEnvironmentOrLoaderDep(depId: String): Boolean {
        val id = depId.lowercase().trim()
        return id == "minecraft" || id == "java" ||
               id == "fabricloader" || id == "fabric-loader" || id == "fabric" ||
               id == "forge" || id == "neoforge" || id == "quilt_loader" || id == "quilt" ||
               id == "mixinextras"
    }

    fun buildModAliases(vararg inputs: String?): Set<String> {
        val aliases = mutableSetOf<String>()
        for (input in inputs) {
            if (!input.isNullOrBlank()) {
                val clean = input.trim().lowercase()
                aliases.add(clean)
                aliases.add(clean.replace(" ", "-"))
                aliases.add(clean.replace(" ", "_"))
                aliases.add(clean.replace("-", "_"))
                aliases.add(clean.replace("_", "-"))
                aliases.add(clean.filter { it.isLetterOrDigit() })
            }
        }
        return aliases
    }

    /**
     * Solves the global dependency graph for a requested mod installation against the authoritative instance environment.
     */
    fun solve(
        environment: ResolvedEnvironment,
        installedMods: List<LocalMod>,
        targetModId: String,
        targetModName: String,
        targetModSlug: String,
        candidates: List<CandidateVersion>,
        dependencyResolver: ((CandidateDependency) -> CandidateVersion?)? = null
    ): SolverResult {
        val targetMc = environment.normalizedMinecraftVersion
        val targetLoader = environment.normalizedLoader
        val targetAliases = buildModAliases(targetModId, targetModSlug, targetModName)

        println("[SOLVER] ======================================================")
        println("[SOLVER] EVENT: DEPENDENCY_GRAPH_CREATED for '$targetModName' (id=$targetModId, slug=$targetModSlug)")
        println("[SOLVER] Environment: Minecraft $targetMc, Loader: ${environment.loader.uppercase()}")
        println("[SOLVER] Installed mods: ${installedMods.size} total (${installedMods.count { it.enabled }} active)")
        println("[SOLVER] Candidates available to evaluate: ${candidates.size}")

        val activeInstalled = installedMods.filter { it.enabled }
        val initialModFiles = environment.modsDirectory.listFiles { f ->
            f.isFile && (f.name.endsWith(".jar", ignoreCase = true) || f.name.endsWith(".jar.disabled", ignoreCase = true))
        }?.map { it.name }?.toSet() ?: emptySet()

        // 1. Filter candidates matching Minecraft Version & Loader
        val environmentMatching = candidates.filter { cand ->
            val mcMatch = cand.gameVersions.isEmpty() || cand.gameVersions.any { gv ->
                gv.equals(targetMc, ignoreCase = true) ||
                (targetMc.startsWith("1.21") && gv.startsWith("1.21") && !gv.contains("w") && gv.take(4) == targetMc.take(4)) ||
                SemverRangeEvaluator.satisfies(targetMc, gv)
            }

            val loaderMatch = cand.loaders.isEmpty() || cand.loaders.any { l ->
                val normL = l.trim().lowercase()
                normL == targetLoader ||
                (targetLoader == "fabric" && normL == "quilt") ||
                (targetLoader == "quilt" && normL == "fabric") ||
                (targetLoader == "neoforge" && normL == "forge")
            }

            mcMatch && loaderMatch
        }

        if (environmentMatching.isEmpty()) {
            val msg = "No version of '$targetModName' found supporting Minecraft $targetMc and ${environment.loader.uppercase()}."
            println("[SOLVER] CANDIDATES_REJECTED: $msg")
            return SolverResult.Failure(
                reason = msg,
                candidateRejections = candidates.associate { it.versionNumber to "Incompatible Minecraft version or loader" }
            )
        }

        // 2. Sort candidates by Release Type (Release > Beta > Alpha) and then semantic version descending
        val sortedCandidates = environmentMatching.sortedWith { v1, v2 ->
            val type1 = when (v1.versionType.lowercase()) { "release" -> 1; "beta" -> 2; else -> 3 }
            val type2 = when (v2.versionType.lowercase()) { "release" -> 1; "beta" -> 2; else -> 3 }
            if (type1 != type2) {
                type1.compareTo(type2)
            } else {
                SemverRangeEvaluator.compareDescending(v1.versionNumber, v2.versionNumber)
            }
        }

        val rejections = mutableMapOf<String, String>()

        // 3. Evaluate each candidate against existing installed mods and dependency constraints
        for (candidate in sortedCandidates) {
            val candVer = SemverRangeEvaluator.extractModVersion(candidate.versionNumber)
            println("[SOLVER] Evaluating candidate: ${candidate.versionNumber} (type=${candidate.versionType})...")

            // A. Check Candidate -> Installed Incompatibilities
            var conflictReason: String? = null
            for (dep in candidate.dependencies) {
                if (dep.dependencyType.equals("incompatible", ignoreCase = true)) {
                    val depAliases = buildModAliases(dep.idOrSlug, dep.fileName)
                    val conflictingInstalled = activeInstalled.firstOrNull { inst ->
                        val instAliases = buildModAliases(inst.id, inst.name, inst.fileName)
                        instAliases.any { depAliases.contains(it) }
                    }
                    if (conflictingInstalled != null) {
                        conflictReason = "Candidate ${candidate.versionNumber} explicitly declares incompatibility with installed mod '${conflictingInstalled.name}'"
                        break
                    }
                }
            }
            if (conflictReason != null) {
                println("[SOLVER] CANDIDATE_REJECTED: ${candidate.versionNumber} -> $conflictReason")
                rejections[candidate.versionNumber] = conflictReason
                continue
            }

            // B. Check Installed -> Candidate Incompatibilities (conflicts & breaks)
            for (instMod in activeInstalled) {
                val instAliases = buildModAliases(instMod.id, instMod.name)
                // Check conflicts
                for ((confKey, confConstraint) in instMod.conflicts) {
                    val confAliases = buildModAliases(confKey)
                    if (targetAliases.any { confAliases.contains(it) }) {
                        if (confConstraint == "*" || confConstraint.isBlank() || SemverRangeEvaluator.satisfies(candVer, confConstraint)) {
                            conflictReason = "Installed mod '${instMod.name}' explicitly conflicts with candidate ${candidate.versionNumber} ($confConstraint)"
                            break
                        }
                    }
                }
                if (conflictReason != null) break

                // Check breaks
                for ((breakKey, breakConstraint) in instMod.breaks) {
                    val breakAliases = buildModAliases(breakKey)
                    if (targetAliases.any { breakAliases.contains(it) }) {
                        if (breakConstraint == "*" || breakConstraint.isBlank() || SemverRangeEvaluator.satisfies(candVer, breakConstraint)) {
                            conflictReason = "Installed mod '${instMod.name}' breaks on candidate ${candidate.versionNumber} ($breakConstraint)"
                            break
                        }
                    }
                }
                if (conflictReason != null) break
            }
            if (conflictReason != null) {
                println("[SOLVER] CANDIDATE_REJECTED: ${candidate.versionNumber} -> $conflictReason")
                rejections[candidate.versionNumber] = conflictReason
                continue
            }

            // C. Check Required Dependencies against installed mods & dependency resolver
            val hasFabricApi = activeInstalled.any { m ->
                val aliases = buildModAliases(m.id, m.name)
                aliases.contains("fabric-api") || aliases.contains("fabric_api") || aliases.contains("fabricapi")
            }

            var depFailure: String? = null
            val resolvedDepsForCandidate = mutableListOf<CandidateVersion>()
            val pendingDeps = ArrayDeque<CandidateDependency>(candidate.dependencies.filter { it.dependencyType.equals("required", ignoreCase = true) })
            val visitedDepIds = mutableSetOf<String>()

            while (pendingDeps.isNotEmpty()) {
                val dep = pendingDeps.removeFirst()
                val depId = dep.idOrSlug.lowercase().trim()
                if (isEnvironmentOrLoaderDep(depId)) {
                    if (depId == "minecraft" && dep.versionConstraint != null && dep.versionConstraint != "*") {
                        if (!SemverRangeEvaluator.satisfies(targetMc, dep.versionConstraint)) {
                            depFailure = "Requires Minecraft ${dep.versionConstraint} (Instance is $targetMc)"
                            break
                        }
                    }
                    continue
                }
                if (depId.startsWith("fabric-") && hasFabricApi) continue // fabric-api provides all fabric-* modules

                val depAliases = buildModAliases(dep.idOrSlug, dep.fileName)
                if (visitedDepIds.any { depAliases.contains(it) }) continue
                visitedDepIds.addAll(depAliases)

                val matchingInstalled = activeInstalled.firstOrNull { inst ->
                    val instAliases = buildModAliases(inst.id, inst.name, inst.fileName)
                    instAliases.any { depAliases.contains(it) }
                }

                if (matchingInstalled != null) {
                    // Dependency already installed: verify constraint
                    val instVer = SemverRangeEvaluator.extractModVersion(matchingInstalled.version)
                    if (dep.versionConstraint != null && dep.versionConstraint != "*" && dep.versionConstraint.isNotBlank()) {
                        if (!SemverRangeEvaluator.satisfies(instVer, dep.versionConstraint)) {
                            depFailure = "Candidate requires '${dep.idOrSlug}' (${dep.versionConstraint}), but installed version is ${matchingInstalled.version}"
                            break
                        }
                    }
                    // Existing dependency satisfies requirement: KEEP IT!
                } else {
                    // Dependency is missing: attempt to resolve candidate dependency if resolver is provided
                    if (dependencyResolver != null) {
                        val resolvedDep = dependencyResolver(dep)
                        if (resolvedDep != null) {
                            if (resolvedDepsForCandidate.none { it.id == resolvedDep.id }) {
                                resolvedDepsForCandidate.add(resolvedDep)
                                // Enqueue transitive dependencies of this dependency
                                for (transitiveDep in resolvedDep.dependencies) {
                                    if (transitiveDep.dependencyType.equals("required", ignoreCase = true)) {
                                        pendingDeps.addLast(transitiveDep)
                                    }
                                }
                            }
                        } else {
                            depFailure = "Missing required dependency '${dep.idOrSlug}' with no compatible version found"
                            break
                        }
                    }
                }
            }

            if (depFailure != null) {
                println("[SOLVER] CANDIDATE_REJECTED: ${candidate.versionNumber} -> $depFailure")
                rejections[candidate.versionNumber] = depFailure
                continue
            }

            // D. Candidate Accepted! Construct deterministic InstallationPlan with pure mod preservation
            println("[SOLVER] CANDIDATE_ACCEPTED: ${candidate.versionNumber} satisfies all constraints!")

            val planItems = mutableListOf<PlanItem>()

            // 1. Mark ALL existing installed mods as KEEP
            for (mod in installedMods) {
                planItems.add(
                    PlanItem(
                        action = PlanActionType.KEEP,
                        modId = mod.id,
                        modName = mod.name,
                        currentVersion = mod.version,
                        targetVersion = mod.version,
                        fileName = mod.fileName,
                        reason = "Existing installed mod preserved"
                    )
                )
            }

            // 2. Mark target candidate as INSTALL
            val primaryFile = candidate.files.firstOrNull { it.isPrimary } ?: candidate.files.firstOrNull()
                ?: throw IllegalStateException("Candidate ${candidate.versionNumber} has no downloadable files")

            planItems.add(
                PlanItem(
                    action = PlanActionType.INSTALL,
                    modId = targetModId,
                    modName = targetModName,
                    targetVersion = candidate.versionNumber,
                    fileName = primaryFile.filename,
                    downloadUrl = primaryFile.url,
                    fileSize = primaryFile.sizeBytes,
                    isPrimary = true,
                    reason = "User requested mod installation"
                )
            )

            // 3. Mark newly resolved required dependencies as INSTALL
            for (depVer in resolvedDepsForCandidate) {
                val depPrimary = depVer.files.firstOrNull { it.isPrimary } ?: depVer.files.firstOrNull() ?: continue
                planItems.add(
                    PlanItem(
                        action = PlanActionType.INSTALL,
                        modId = depVer.id,
                        modName = depVer.displayName ?: depVer.id,
                        targetVersion = depVer.versionNumber,
                        fileName = depPrimary.filename,
                        downloadUrl = depPrimary.url,
                        fileSize = depPrimary.sizeBytes,
                        isPrimary = false,
                        reason = "Required dependency for $targetModName"
                    )
                )
            }

            val newlyAddedFileNames = planItems.filter { it.action == PlanActionType.INSTALL }.map { it.fileName }.toSet()
            val expectedFinalModFiles = initialModFiles + newlyAddedFileNames

            val selectionReason = when {
                candidate.versionNumber == sortedCandidates.first().versionNumber ->
                    "Latest compatible release for Minecraft $targetMc (${environment.loader.uppercase()})."
                else ->
                    "Automatically selected version ${candidate.versionNumber} as the best compatible release for your current mod setup."
            }

            val plan = InstallationPlan(
                environment = environment,
                targetModId = targetModId,
                targetModName = targetModName,
                selectedVersionNumber = candidate.versionNumber,
                items = planItems,
                initialModFileNames = initialModFiles,
                expectedFinalModFileNames = expectedFinalModFiles
            )

            println("[SOLVER] SOLUTION_FOUND: ${candidate.versionNumber}")
            println("[SOLVER] INSTALL_PLAN_CREATED: KEEP=${plan.itemsToKeep.size}, INSTALL=${plan.itemsToInstall.size}, REMOVE=${plan.itemsToRemove.size}")
            println("[SOLVER] ======================================================")

            return SolverResult.Success(
                plan = plan,
                selectedCandidate = candidate,
                selectionReason = selectionReason,
                resolvedDependencies = resolvedDepsForCandidate
            )
        }

        // All candidates rejected
        val summaryReason = rejections.values.firstOrNull() ?: "No release of '$targetModName' satisfies current instance mod constraints."
        println("[SOLVER] RESOLUTION_FAILED: $summaryReason")
        println("[SOLVER] ======================================================")
        return SolverResult.Failure(
            reason = summaryReason,
            candidateRejections = rejections,
            primaryConflict = rejections.values.firstOrNull()
        )
    }

    /**
     * Converts a ModrinthVersion into a universal CandidateVersion.
     */
    fun fromModrinth(
        version: ModrinthVersion,
        projectHit: ModrinthProjectHit? = null
    ): CandidateVersion {
        val deps = version.dependencies.map { dep ->
            CandidateDependency(
                idOrSlug = dep.projectId ?: dep.versionId ?: "",
                versionId = dep.versionId,
                fileName = dep.fileName,
                dependencyType = dep.dependencyType
            )
        }

        val files = version.files.map { f ->
            CandidateFile(
                filename = f.filename,
                url = f.url,
                sizeBytes = f.size,
                sha1 = f.hashes["sha1"],
                sha512 = f.hashes["sha512"],
                isPrimary = f.primary
            )
        }

        return CandidateVersion(
            id = version.id,
            versionNumber = version.versionNumber,
            versionType = version.versionType,
            gameVersions = version.gameVersions,
            loaders = version.loaders,
            dependencies = deps,
            files = files,
            displayName = version.name.ifBlank { version.versionNumber },
            raw = version
        )
    }

    /**
     * Converts a CurseForgeFile into a universal CandidateVersion.
     */
    fun fromCurseForge(
        file: CurseForgeFile,
        mod: CurseForgeMod? = null
    ): CandidateVersion {
        val loaders = file.gameVersions.mapNotNull { gv ->
            when (gv.lowercase()) {
                "fabric" -> "fabric"
                "forge" -> "forge"
                "neoforge" -> "neoforge"
                "quilt" -> "quilt"
                else -> null
            }
        }.ifEmpty {
            listOf("fabric")
        }

        val gameVersions = file.gameVersions.filter { gv ->
            gv.matches(Regex("""^1\.\d+(\.\d+)?$"""))
        }

        val deps = file.dependencies.map { d ->
            val relType = when (d.relationType) {
                3, 6 -> "required"
                2 -> "optional"
                5 -> "incompatible"
                else -> "embedded"
            }
            CandidateDependency(
                idOrSlug = d.modId.toString(),
                dependencyType = relType
            )
        }

        val primaryFile = CandidateFile(
            filename = file.fileName,
            url = file.downloadUrl ?: "",
            sizeBytes = file.fileLength,
            isPrimary = true
        )

        return CandidateVersion(
            id = file.id.toString(),
            versionNumber = file.displayName.ifBlank { file.fileName },
            versionType = file.releaseTypeEnum.name.lowercase(),
            gameVersions = gameVersions,
            loaders = loaders,
            dependencies = deps,
            files = listOf(primaryFile),
            displayName = file.displayName,
            raw = file
        )
    }
}
