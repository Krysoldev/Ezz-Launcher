package io.ezz.launcher.core.runtime.diagnostics

enum class BottleneckType(val title: String) {
    GPU_BOUND("GPU / Rendering Capacity Bound"),
    CPU_RENDER_THREAD("CPU / Game Render Thread Bound"),
    ENTITY_BOUND("Entity Heavy Scenario Bound"),
    RAM_PRESSURE("Memory / Heap Pressure Bound"),
    BALANCED("Balanced / High Throughput")
}

data class BenchmarkResult(
    val averageFps: Double,
    val onePercentLowFps: Double,
    val pointOnePercentLowFps: Double,
    val averageFrameTimeMs: Double,
    val worstFrameTimeMs: Double,
    val memoryUsedMb: Int,
    val memoryAllocatedMb: Int,
    val gpuUtilizationPct: Int?,
    val cpuRenderThreadPct: Int?,
    val bottleneck: BottleneckType,
    val recommendation: String
)

object PerformanceBenchmarkAnalyzer {

    fun calculateMetrics(
        frameTimesMs: List<Double>,
        memoryUsedMb: Int,
        memoryAllocatedMb: Int,
        gpuPct: Int? = null,
        cpuThreadPct: Int? = null,
        entityCount: Int = 0
    ): BenchmarkResult {
        if (frameTimesMs.isEmpty()) {
            return BenchmarkResult(
                averageFps = 0.0,
                onePercentLowFps = 0.0,
                pointOnePercentLowFps = 0.0,
                averageFrameTimeMs = 0.0,
                worstFrameTimeMs = 0.0,
                memoryUsedMb = memoryUsedMb,
                memoryAllocatedMb = memoryAllocatedMb,
                gpuUtilizationPct = gpuPct,
                cpuRenderThreadPct = cpuThreadPct,
                bottleneck = BottleneckType.BALANCED,
                recommendation = "No frame data collected."
            )
        }

        val avgTimeMs = frameTimesMs.average()
        val avgFps = if (avgTimeMs > 0.0) 1000.0 / avgTimeMs else 0.0
        val sortedTimes = frameTimesMs.sorted()

        // 1% lowest FPS corresponds to the 99th percentile frame time
        val p99Index = ((sortedTimes.size * 0.99).toInt()).coerceIn(0, sortedTimes.size - 1)
        val p99TimeMs = sortedTimes[p99Index]
        val onePercentLow = if (p99TimeMs > 0.0) 1000.0 / p99TimeMs else 0.0

        // 0.1% lowest FPS corresponds to the 99.9th percentile frame time
        val p999Index = ((sortedTimes.size * 0.999).toInt()).coerceIn(0, sortedTimes.size - 1)
        val p999TimeMs = sortedTimes[p999Index]
        val pointOnePercentLow = if (p999TimeMs > 0.0) 1000.0 / p999TimeMs else 0.0

        val worstTimeMs = sortedTimes.last()

        // Intelligent Bottleneck Classification
        val bottleneck: BottleneckType
        val recommendation: String

        when {
            gpuPct != null && gpuPct >= 95 -> {
                bottleneck = BottleneckType.GPU_BOUND
                recommendation = "GPU utilization is at 95%+. If shaders are active, disable them or lower render resolution to increase FPS."
            }
            entityCount > 150 || (cpuThreadPct != null && cpuThreadPct >= 90) -> {
                bottleneck = if (entityCount > 150) BottleneckType.ENTITY_BOUND else BottleneckType.CPU_RENDER_THREAD
                recommendation = "CPU render thread / entity bottleneck. Reduce Entity Distance to 50%-75% and Simulation Distance to 5 chunks in Performance Mode."
            }
            memoryUsedMb >= (memoryAllocatedMb * 0.9) -> {
                bottleneck = BottleneckType.RAM_PRESSURE
                recommendation = "Heap memory is under pressure. For an 8 GB system, 4096 MB is safe; avoid high-res 128x/256x resource packs."
            }
            else -> {
                bottleneck = BottleneckType.BALANCED
                recommendation = "Hardware performance is balanced with consistent frame pacing."
            }
        }

        return BenchmarkResult(
            averageFps = (avgFps * 10).toInt() / 10.0,
            onePercentLowFps = (onePercentLow * 10).toInt() / 10.0,
            pointOnePercentLowFps = (pointOnePercentLow * 10).toInt() / 10.0,
            averageFrameTimeMs = ((avgTimeMs * 100).toInt()) / 100.0,
            worstFrameTimeMs = ((worstTimeMs * 100).toInt()) / 100.0,
            memoryUsedMb = memoryUsedMb,
            memoryAllocatedMb = memoryAllocatedMb,
            gpuUtilizationPct = gpuPct,
            cpuRenderThreadPct = cpuThreadPct,
            bottleneck = bottleneck,
            recommendation = recommendation
        )
    }
}
