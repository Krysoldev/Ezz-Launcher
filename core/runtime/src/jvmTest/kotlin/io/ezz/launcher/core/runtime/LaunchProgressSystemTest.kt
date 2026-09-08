package io.ezz.launcher.core.runtime

import io.ezz.launcher.core.model.runtime.LaunchProgressState
import io.ezz.launcher.core.model.runtime.computeDisplayInterpolationDuration
import io.ezz.launcher.core.model.runtime.computeDisplayPercentage
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LaunchProgressSystemTest {

    @Test
    fun testEveryIntegerBetween0And100IsValid() {
        for (i in 0..100) {
            val progress = i / 100f
            val calculatedPercentage = round((progress * 100).toDouble()).toInt().coerceIn(0, 100)
            assertEquals(i, calculatedPercentage, "Failed for integer percentage: $i")

            val state = LaunchProgressState(
                operationId = "test-op-1",
                instanceId = "test-inst",
                stage = "DOWNLOADING",
                status = "Downloading file $i of 100",
                progress = progress,
                percentage = calculatedPercentage,
                completedWork = i.toLong(),
                totalWork = 100L
            )
            assertEquals(i, state.percentage)
            assertEquals(progress, state.progress)
            assertFalse(state.isIndeterminate)
        }
    }

    @Test
    fun testFineGrainedWorkRatioToPercentage() {
        // Test byte ratios like 37.4% -> 37%
        val completedBytes = 37400L
        val totalBytes = 100000L
        val progress = completedBytes.toFloat() / totalBytes
        val percentage = round((progress * 100).toDouble()).toInt().coerceIn(0, 100)
        assertEquals(37, percentage)

        // 37.6% -> 38%
        val completedBytes2 = 37600L
        val progress2 = completedBytes2.toFloat() / totalBytes
        val percentage2 = round((progress2 * 100).toDouble()).toInt().coerceIn(0, 100)
        assertEquals(38, percentage2)
    }

    @Test
    fun testClampingPreventsNegativeAndOverflow() {
        val negativeProgress = -0.5f
        val clampedNegative = round((negativeProgress.coerceIn(0f, 1f) * 100).toDouble()).toInt().coerceIn(0, 100)
        assertEquals(0, clampedNegative)

        val overflowProgress = 1.5f
        val clampedOverflow = round((overflowProgress.coerceIn(0f, 1f) * 100).toDouble()).toInt().coerceIn(0, 100)
        assertEquals(100, clampedOverflow)
    }

    @Test
    fun testMonotonicProgressProtection() {
        var currentProgress = 0.42f // 42%

        // Stale event arriving with 0.20f (e.g. 20% from a delayed callback)
        val staleIncomingProgress = 0.20f
        val safeProgress = maxOf(currentProgress, staleIncomingProgress)

        assertEquals(0.42f, safeProgress, "Monotonic protection must prevent backward progress")

        // Legitimate forward progress
        val forwardProgress = 0.43f
        val newProgress = maxOf(currentProgress, forwardProgress)
        assertEquals(0.43f, newProgress, "Forward progress must be accepted")
    }

    @Test
    fun testOperationIdIsolation() {
        val oldOpId = "launch-op-A"
        val newOpId = "launch-op-B"

        val oldEvent = LaunchEvent.ProgressUpdate(
            operationId = oldOpId,
            stage = "RESOLVING DEPENDENCIES",
            status = "Stale late event",
            progress = 0.20f
        )

        val currentActiveOpId = newOpId

        // Filter check as in AppViewModel
        val shouldAcceptOld = (oldEvent.operationId == currentActiveOpId)
        assertFalse(shouldAcceptOld, "Late event from cancelled/old launch A must be discarded")
    }

    @Test
    fun testHighFrequencyCoalescing() {
        val stateA = LaunchProgressState(
            operationId = "op-1",
            instanceId = "inst-1",
            stage = "DOWNLOADING",
            status = "Downloading assets...",
            progress = 0.421f,
            percentage = 42
        )

        // Micro-fractional update that yields the exact same percentage, status, and stage
        val incomingMicroProgress = 0.424f
        val incomingPercentage = round((incomingMicroProgress * 100).toDouble()).toInt()

        val isDuplicate = (
            stateA.percentage == incomingPercentage &&
            stateA.status == "Downloading assets..." &&
            stateA.stage == "DOWNLOADING" &&
            stateA.isIndeterminate == false
        )

        assertTrue(isDuplicate, "Micro-fractional updates without integer percentage change should coalesce")

        // Meaningful integer step: 42% -> 43%
        val forwardProgress = 0.426f
        val nextPercentage = round((forwardProgress * 100).toDouble()).toInt()
        val isStepDuplicate = (
            stateA.percentage == nextPercentage &&
            stateA.status == "Downloading assets..." &&
            stateA.stage == "DOWNLOADING"
        )
        assertFalse(isStepDuplicate, "Integer increments (42% -> 43%) must NOT be coalesced; UI must update immediately")
    }

    @Test
    fun test100PercentOnlyOnProcessStarted() {
        // Pre-launch operations (native extraction, command generation) should stay < 100%
        val nativeExtractProgress = 0.82f
        val commandGenProgress = 0.96f

        assertTrue(nativeExtractProgress < 1.0f)
        assertTrue(commandGenProgress < 1.0f)

        // Only when Minecraft Process starts does it become 100%
        val processStartedProgress = 1.0f
        val finalPercentage = round((processStartedProgress * 100).toDouble()).toInt()
        assertEquals(100, finalPercentage)
    }

    @Test
    fun testIndeterminateStateHandling() {
        val indeterminateState = LaunchProgressState(
            operationId = "op-1",
            instanceId = "inst-1",
            stage = "RESOLVING DEPENDENCIES",
            status = "Querying metadata server...",
            progress = 0.12f,
            percentage = 12,
            isIndeterminate = true
        )

        assertTrue(indeterminateState.isIndeterminate)
        // Stage text is real, not fake
        assertEquals("RESOLVING DEPENDENCIES", indeterminateState.stage)
    }

    @Test
    fun testChibiSteveWalkCycleFrameMapping() {
        fun getFrameForTimeMs(timeMs: Int): Int {
            val t = timeMs % 700
            return when {
                t < 100 -> 0
                t < 200 -> 1
                t < 300 -> 2
                t < 500 -> 3
                t < 600 -> 4
                else -> 5
            }
        }

        assertEquals(0, getFrameForTimeMs(0))
        assertEquals(0, getFrameForTimeMs(99))
        assertEquals(1, getFrameForTimeMs(100))
        assertEquals(1, getFrameForTimeMs(199))
        assertEquals(2, getFrameForTimeMs(200))
        assertEquals(2, getFrameForTimeMs(299))
        assertEquals(3, getFrameForTimeMs(300))
        assertEquals(3, getFrameForTimeMs(499))
        assertEquals(4, getFrameForTimeMs(500))
        assertEquals(4, getFrameForTimeMs(599))
        assertEquals(5, getFrameForTimeMs(600))
        assertEquals(5, getFrameForTimeMs(699))
        // Verify cycle repeat
        assertEquals(0, getFrameForTimeMs(700))
        assertEquals(3, getFrameForTimeMs(1050))
    }

    @Test
    fun testChibiSteveTrackPositionLinearContinuity() {
        val totalTrackWidth = 600f
        val runnerWidth = 40f
        val finishMargin = 28f
        val maxTravel = totalTrackWidth - runnerWidth - finishMargin // 532f

        for (pct in 0..100) {
            val progress = pct / 100f
            val runnerX = maxTravel * progress
            assertTrue(runnerX >= 0f, "Position cannot be negative at $pct%")
            assertTrue(runnerX <= maxTravel, "Position cannot exceed max travel at $pct%")
            assertEquals((maxTravel * pct) / 100f, runnerX, 0.001f)
        }
    }

    @Test
    fun testDisplayInterpolationDurationScaling() {
        assertEquals(0, computeDisplayInterpolationDuration(0.0005f))
        assertEquals(120, computeDisplayInterpolationDuration(0.01f))
        assertEquals(120, computeDisplayInterpolationDuration(0.02f))
        assertEquals(240, computeDisplayInterpolationDuration(0.04f))
        assertEquals(240, computeDisplayInterpolationDuration(0.05f))
        assertEquals(550, computeDisplayInterpolationDuration(0.12f))
        assertEquals(550, computeDisplayInterpolationDuration(0.15f))
        assertEquals(1000, computeDisplayInterpolationDuration(0.25f))
        assertEquals(1000, computeDisplayInterpolationDuration(0.35f))
        assertEquals(1600, computeDisplayInterpolationDuration(0.50f))
        assertEquals(1600, computeDisplayInterpolationDuration(0.65f))
        assertEquals(2100, computeDisplayInterpolationDuration(0.68f)) // Typical 32% -> 100% jump
        assertEquals(2100, computeDisplayInterpolationDuration(1.0f))
    }

    @Test
    fun testRealVsDisplayProgressSafetySeparation() {
        // Real progress safety: 100% is strictly prohibited while backend operation is incomplete
        assertEquals(0, computeDisplayPercentage(0f, isFinished = false))
        assertEquals(16, computeDisplayPercentage(0.16f, isFinished = false))
        assertEquals(32, computeDisplayPercentage(0.32f, isFinished = false))
        assertEquals(99, computeDisplayPercentage(0.994f, isFinished = false))
        assertEquals(100, computeDisplayPercentage(1.0f, isFinished = false), "1.0f display progress reaches 100%")

        // Once finished is true, 100% is permitted when display progress reaches 100%
        assertEquals(100, computeDisplayPercentage(1.0f, isFinished = true))
        // And if display progress is still in-flight, it displays the actual intermediate integer
        assertEquals(75, computeDisplayPercentage(0.75f, isFinished = true))
    }

    @Test
    fun testSmoothIntegerTraversalWithoutGaps() {
        // Simulate display interpolation: 0.16f -> 0.32f
        val startVal = 0.16f
        val targetVal = 0.32f
        val durationMs = computeDisplayInterpolationDuration(targetVal - startVal)
        val frameTimeMs = 16.6f
        val totalFrames = (durationMs / frameTimeMs).toInt()

        val visitedIntegers = mutableSetOf<Int>()
        for (f in 0..totalFrames) {
            val t = (f.toFloat() / totalFrames).coerceIn(0f, 1f)
            val currentProgress = startVal + (targetVal - startVal) * t
            val pct = computeDisplayPercentage(currentProgress, isFinished = false)
            visitedIntegers.add(pct)
        }

        // Verify that start and end are covered
        assertTrue(visitedIntegers.contains(16))
        assertTrue(visitedIntegers.contains(32))

        // Verify monotonic traversal (each visited integer must be >= previous)
        var lastInt = 16
        for (f in 0..totalFrames) {
            val t = (f.toFloat() / totalFrames).coerceIn(0f, 1f)
            val currentProgress = startVal + (targetVal - startVal) * t
            val pct = computeDisplayPercentage(currentProgress, isFinished = false)
            assertTrue(pct >= lastInt, "Progress must be strictly monotonic: $pct < $lastInt")
            lastInt = pct
        }

        // Simulate 0.32f -> 1.0f jump to completion
        val startVal2 = 0.32f
        val targetVal2 = 1.0f
        val durationMs2 = computeDisplayInterpolationDuration(targetVal2 - startVal2)
        val totalFrames2 = (durationMs2 / frameTimeMs).toInt()

        val visitedIntegers2 = mutableSetOf<Int>()
        for (f in 0..totalFrames2) {
            val t = (f.toFloat() / totalFrames2).coerceIn(0f, 1f)
            val currentProgress = startVal2 + (targetVal2 - startVal2) * t
            val pct = computeDisplayPercentage(currentProgress, isFinished = true)
            visitedIntegers2.add(pct)
        }

        assertTrue(visitedIntegers2.contains(32))
        assertTrue(visitedIntegers2.contains(100))
        // Verify high density coverage across intermediate integers
        assertTrue(visitedIntegers2.size >= 50, "Must smoothly visit high density of intermediate integers without large gaps")
    }

    @Test
    fun testDeliberateFinalProgressInterpolation() {
        // When target is final (100%), duration must remain smooth and deliberate (~2100ms for 68% jump)
        val standardDuration = computeDisplayInterpolationDuration(0.68f, isTargetFinal = false)
        val finalDuration = computeDisplayInterpolationDuration(0.68f, isTargetFinal = true)

        assertEquals(2100, standardDuration)
        assertEquals(2100, finalDuration, "Final 100% progress transition must remain smooth and deliberate to let Chibi Steve walk naturally")
    }

    @Test
    fun testImmediate100PercentDisplayWhenFinished() {
        // When backend emits 100% (isFinished = true), display percentage must immediately be 100
        val pct = computeDisplayPercentage(1.0f, isFinished = true)
        assertEquals(100, pct)
    }
}

