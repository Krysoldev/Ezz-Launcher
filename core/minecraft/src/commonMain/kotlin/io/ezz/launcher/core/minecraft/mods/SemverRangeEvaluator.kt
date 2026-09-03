package io.ezz.launcher.core.minecraft.mods

/**
 * Robust semantic version and range constraint evaluator for Minecraft mods.
 * Handles Fabric, Forge, Modrinth, and CurseForge version constraints:
 * - Exact: "=1.2.3", "1.2.3"
 * - Comparisons: ">=1.2.3", ">1.2.3", "<=1.2.3", "<1.2.3"
 * - Ranges: ">=0.8.0 <=0.8.13", ">=1.20 <1.21"
 * - Tildes / Carets: "~1.2.3", "^1.2.3"
 * - Wildcards: "*", "1.21.x", "1.21.*"
 * - Build tags & Prefixes: "0.8.13+mc1.21.11", "mc1.21.11-0.9.3+fabric", "1.10.7+1.21.11", "v0.9.3"
 */
object SemverRangeEvaluator {

    data class ParsedVersion(
        val raw: String,
        val numbers: List<Int>,
        val preRelease: String? = null,
        val buildMetadata: String? = null
    ) : Comparable<ParsedVersion> {
        override fun compareTo(other: ParsedVersion): Int {
            val maxLen = maxOf(numbers.size, other.numbers.size)
            for (i in 0 until maxLen) {
                val n1 = numbers.getOrElse(i) { 0 }
                val n2 = other.numbers.getOrElse(i) { 0 }
                if (n1 != n2) return n1.compareTo(n2)
            }
            // Pre-release versions have lower precedence than normal versions
            if (preRelease != null && other.preRelease == null) return -1
            if (preRelease == null && other.preRelease != null) return 1
            if (preRelease != null && other.preRelease != null && preRelease != other.preRelease) {
                return preRelease.compareTo(other.preRelease)
            }
            return 0
        }
    }

    /**
     * Extracts the clean semantic mod version from composite version tags
     * (e.g., "mc1.21.11-0.9.3+fabric" -> "0.9.3", "v1.10.7" -> "1.10.7", "1.10.7+mc1.21.11" -> "1.10.7").
     */
    fun extractModVersion(raw: String): String {
        var s = raw.trim()
        if (s.isBlank()) return "0.0.0"

        // 1. Remove build metadata after '+'
        val withoutBuild = s.substringBefore('+')

        // 2. Remove common Minecraft version prefixes like "mc1.21.11-" or "1.21.11-0.9.3"
        val mcPrefixRegex = Regex("""^(?:mc)?1\.\d+(?:\.\d+)?[-_]""", RegexOption.IGNORE_CASE)
        var cleaned = withoutBuild.replace(mcPrefixRegex, "")

        // 3. Remove mod/loader name prefixes like "sodium-extra-" or "fabric-"
        val modNamePrefixRegex = Regex("""^[a-zA-Z\-_]+[-_](\d+(?:\.\d+)*.*)$""")
        val modMatch = modNamePrefixRegex.find(cleaned)
        if (modMatch != null) {
            cleaned = modMatch.groupValues[1]
        }

        // 4. Strip leading 'v' or 'V' if followed by digit
        if (cleaned.startsWith("v", ignoreCase = true) && cleaned.length > 1 && cleaned[1].isDigit()) {
            cleaned = cleaned.substring(1)
        }

        // 5. Fallback: if empty or doesn't start with a digit, try finding a version sequence
        if (cleaned.isEmpty() || !cleaned[0].isDigit()) {
            val digitSeqRegex = Regex("""\b\d+(?:\.\d+)+[a-zA-Z0-9.\-_]*\b""")
            val seqMatch = digitSeqRegex.find(withoutBuild)
            if (seqMatch != null) {
                cleaned = seqMatch.value
            } else {
                cleaned = withoutBuild
            }
        }

        return cleaned
    }

    fun parseVersion(versionStr: String): ParsedVersion {
        val clean = versionStr.trim()
        val modVer = extractModVersion(clean)

        val withoutBuild = modVer.substringBefore('+')
        val buildMeta = if (clean.contains('+')) clean.substringAfter('+') else null

        val withoutPre = withoutBuild.substringBefore('-')
        val pre = if (withoutBuild.contains('-')) withoutBuild.substringAfter('-') else null

        // Extract integer numbers separated by dots
        val nums = withoutPre.split('.')
            .mapNotNull { part ->
                // Take leading digits if attached to letters
                val digits = part.takeWhile { it.isDigit() }
                digits.toIntOrNull()
            }

        return ParsedVersion(
            raw = clean,
            numbers = if (nums.isEmpty()) listOf(0) else nums,
            preRelease = pre,
            buildMetadata = buildMeta
        )
    }

    /**
     * Compares two semantic version strings.
     * Returns > 0 if v1 > v2, < 0 if v1 < v2, 0 if equal.
     */
    fun compareSemVer(v1: String, v2: String): Int {
        return parseVersion(v1).compareTo(parseVersion(v2))
    }

    /**
     * Checks whether a version satisfies a given constraint expression.
     */
    fun satisfies(versionStr: String, constraintStr: String): Boolean {
        val constraint = constraintStr.trim()
        if (constraint.isBlank() || constraint == "*") return true

        // Handle OR expressions: "||" or "|"
        if (constraint.contains("||")) {
            return constraint.split("||").any { satisfies(versionStr, it) }
        }

        // Handle space/comma separated AND constraints: ">=0.8.0 <=0.8.13" or ">=1.20, <1.21"
        val clauses = constraint.split(Regex("[,\\s]+")).filter { it.isNotBlank() }
        if (clauses.size > 1) {
            return clauses.all { satisfySingleClause(versionStr, it) }
        }

        return satisfySingleClause(versionStr, constraint)
    }

    private fun satisfySingleClause(versionStr: String, clause: String): Boolean {
        val token = clause.trim()
        if (token.isBlank() || token == "*") return true

        val parsedVer = parseVersion(versionStr)

        // Wildcards: "1.21.x", "1.21.*", "0.8.x"
        if (token.endsWith(".x", ignoreCase = true) || token.endsWith(".*")) {
            val prefix = token.dropLast(2)
            val prefixParsed = parseVersion(prefix)
            for (i in 0 until prefixParsed.numbers.size) {
                if (parsedVer.numbers.getOrElse(i) { 0 } != prefixParsed.numbers[i]) {
                    return false
                }
            }
            return true
        }

        // Caret: ^1.2.3 (compatible with 1.x.x, or 0.x if 0.x.x)
        if (token.startsWith("^")) {
            val target = parseVersion(token.drop(1))
            if (parsedVer < target) return false
            val major = target.numbers.getOrElse(0) { 0 }
            val minor = target.numbers.getOrElse(1) { 0 }
            return if (major > 0) {
                parsedVer.numbers.getOrElse(0) { 0 } == major
            } else if (minor > 0) {
                parsedVer.numbers.getOrElse(0) { 0 } == 0 && parsedVer.numbers.getOrElse(1) { 0 } == minor
            } else {
                parsedVer == target
            }
        }

        // Tilde: ~1.2.3 (approximately equivalent to 1.2.x)
        if (token.startsWith("~")) {
            val target = parseVersion(token.drop(1))
            if (parsedVer < target) return false
            val major = target.numbers.getOrElse(0) { 0 }
            val minor = target.numbers.getOrElse(1) { 0 }
            return parsedVer.numbers.getOrElse(0) { 0 } == major &&
                   parsedVer.numbers.getOrElse(1) { 0 } == minor
        }

        // Greater than or equal: >=
        if (token.startsWith(">=")) {
            val target = parseVersion(token.drop(2))
            return parsedVer >= target
        }

        // Greater than: >
        if (token.startsWith(">")) {
            val target = parseVersion(token.drop(1))
            return parsedVer > target
        }

        // Less than or equal: <=
        if (token.startsWith("<=")) {
            val target = parseVersion(token.drop(2))
            return parsedVer <= target
        }

        // Less than: <
        if (token.startsWith("<")) {
            val target = parseVersion(token.drop(1))
            return parsedVer < target
        }

        // Exact equal: =1.2.3
        if (token.startsWith("=")) {
            val target = parseVersion(token.drop(1))
            return parsedVer.compareTo(target) == 0
        }

        // Exact match fallback
        val target = parseVersion(token)
        return parsedVer.compareTo(target) == 0
    }

    /**
     * Checks whether candidateVersion conflicts with a 'breaks' rule constraint.
     */
    fun isBreaksConstraintMatched(candidateVersion: String, breaksConstraint: String): Boolean {
        return satisfies(candidateVersion, breaksConstraint)
    }

    /**
     * Checks if a candidate version satisfies all constraints in a list (constraint intersection).
     */
    fun satisfiesAll(versionStr: String, constraints: List<String>): Boolean {
        if (constraints.isEmpty()) return true
        return constraints.all { satisfies(versionStr, it) }
    }

    /**
     * Compare two versions descending (newest first).
     */
    fun compareDescending(v1: String, v2: String): Int {
        return parseVersion(v2).compareTo(parseVersion(v1))
    }
}
