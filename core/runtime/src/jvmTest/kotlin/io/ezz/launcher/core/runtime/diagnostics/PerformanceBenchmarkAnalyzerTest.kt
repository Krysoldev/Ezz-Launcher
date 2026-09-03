package io.ezz.launcher.core.runtime.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerformanceBenchmarkAnalyzerTest {

    @Test
    fun testCalculateMetricsBalanced() {
        // 200 frames @ 5.0ms avg (200 FPS), with some variation
        val frameTimes = (1..200).map { 5.0 + (it % 5) * 0.2 }
        val result = PerformanceBenchmarkAnalyzer.calculateMetrics(
            frameTimesMs = frameTimes,
            memoryUsedMb = 2048,
            memoryAllocatedMb = 4096,
            gpuPct = 70,
            cpuThreadPct = 60,
            entityCount = 30
        )

        assertTrue(result.averageFps > 170.0, "Average FPS should be around 180-200")
        assertTrue(result.onePercentLowFps > 160.0, "1% Low FPS should be calculated correctly")
        assertTrue(result.pointOnePercentLowFps > 150.0, "0.1% Low FPS should be calculated correctly")
        assertEquals(BottleneckType.BALANCED, result.bottleneck)
    }

    @Test
    fun testCalculateMetricsGpuBound() {
        val frameTimes = listOf(10.0, 10.5, 9.8, 10.2)
        val result = PerformanceBenchmarkAnalyzer.calculateMetrics(
            frameTimesMs = frameTimes,
            memoryUsedMb = 2048,
            memoryAllocatedMb = 4096,
            gpuPct = 98,
            cpuThreadPct = 40
        )

        assertEquals(BottleneckType.GPU_BOUND, result.bottleneck)
        assertTrue(result.recommendation.contains("GPU", ignoreCase = true))
    }

    @Test
    fun testCalculateMetricsEntityBound() {
        val frameTimes = listOf(8.0, 12.0, 15.0, 9.0)
        val result = PerformanceBenchmarkAnalyzer.calculateMetrics(
            frameTimesMs = frameTimes,
            memoryUsedMb = 2500,
            memoryAllocatedMb = 4096,
            gpuPct = 50,
            cpuThreadPct = 95,
            entityCount = 200
        )

        assertEquals(BottleneckType.ENTITY_BOUND, result.bottleneck)
        assertTrue(result.recommendation.contains("Entity", ignoreCase = true))
    }

    @Test
    fun testCalculateMetricsRamPressure() {
        val frameTimes = listOf(6.0, 6.2, 5.9)
        val result = PerformanceBenchmarkAnalyzer.calculateMetrics(
            frameTimesMs = frameTimes,
            memoryUsedMb = 3900,
            memoryAllocatedMb = 4096,
            gpuPct = 60
        )

        assertEquals(BottleneckType.RAM_PRESSURE, result.bottleneck)
        assertTrue(result.recommendation.contains("pressure", ignoreCase = true))
    }
}
