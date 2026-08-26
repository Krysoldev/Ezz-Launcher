package io.ezz.launcher.core.minecraft.resolver

import io.ezz.launcher.core.model.minecraft.OsRule
import io.ezz.launcher.core.model.minecraft.Rule
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuleEvaluatorTest {

    @Test
    fun testEmptyRulesAllowedByDefault() {
        assertTrue(RuleEvaluator.isAllowed(null))
        assertTrue(RuleEvaluator.isAllowed(emptyList()))
    }

    @Test
    fun testOsMatchingRule() {
        val windowsOnlyRule = listOf(
            Rule(action = "allow", os = OsRule(name = "windows"))
        )
        assertTrue(RuleEvaluator.isAllowed(windowsOnlyRule, currentOs = OperatingSystem.WINDOWS))
        assertFalse(RuleEvaluator.isAllowed(windowsOnlyRule, currentOs = OperatingSystem.LINUX))
        assertFalse(RuleEvaluator.isAllowed(windowsOnlyRule, currentOs = OperatingSystem.OSX))
    }

    @Test
    fun testDisallowRule() {
        val rules = listOf(
            Rule(action = "allow"),
            Rule(action = "disallow", os = OsRule(name = "osx"))
        )
        assertTrue(RuleEvaluator.isAllowed(rules, currentOs = OperatingSystem.WINDOWS))
        assertTrue(RuleEvaluator.isAllowed(rules, currentOs = OperatingSystem.LINUX))
        assertFalse(RuleEvaluator.isAllowed(rules, currentOs = OperatingSystem.OSX))
    }

    @Test
    fun testArchMatchingRule() {
        val arm64Rule = listOf(
            Rule(action = "allow", os = OsRule(arch = "arm64"))
        )
        assertTrue(RuleEvaluator.isAllowed(arm64Rule, currentArch = "aarch64"))
        assertTrue(RuleEvaluator.isAllowed(arm64Rule, currentArch = "arm64"))
        assertFalse(RuleEvaluator.isAllowed(arm64Rule, currentArch = "x86_64"))
    }
}
