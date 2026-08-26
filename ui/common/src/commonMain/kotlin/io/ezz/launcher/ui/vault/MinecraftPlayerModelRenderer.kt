package io.ezz.launcher.ui.vault

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.ezz.launcher.core.model.skin.SkinModelType
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Vault V5 — True Solid-Cuboid 3D Minecraft Player Model Engine with Pixel-Perfect Z-Buffer Rasterizer.
 *
 * Core Technical Architecture:
 * 1. 100% Solid Minecraft Cuboid Geometry (1 cuboid per body part, 12 triangles per box).
 * 2. True Hardware-Precision Z-Buffer: Eliminates all disappearing faces, holes, and transparency artifacts during 360° rotation.
 * 3. Nearest-Neighbor Texture Sampling: Maps directly from canonical 64x64 PNG with Clamp-To-Edge UV boundaries.
 * 4. Zero Grid/Seam Artifacts: Single continuous rasterization per face eliminates all subpixel lines and checkerboard borders.
 * 5. PlayerRoot Y-Axis Character Rotation: Rotates around waist/chest pivot (Y = -16) while camera stays stable.
 * 6. Alpha Cutout: Opaque pixels render completely solid; transparent pixels (alpha < 128) are discarded cleanly.
 * 7. Verified Java Edition 1.8+ 64x64 Skin UV Layout for Steve (4px) and Alex (3px).
 */
@Composable
fun MinecraftPlayerModel3DView(
    skinBytes: ByteArray?,
    modelType: SkinModelType = SkinModelType.STEVE,
    resetTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    // Camera Target Angles (Updated by user manual drag)
    var targetYaw by remember { mutableFloatStateOf(-20f) }
    var targetPitch by remember { mutableFloatStateOf(6f) }
    var targetZoom by remember { mutableFloatStateOf(1.0f) }

    // Interpolated Damped Camera Angles (Smooth manual transition)
    var currentYaw by remember { mutableFloatStateOf(-20f) }
    var currentPitch by remember { mutableFloatStateOf(6f) }
    var currentZoom by remember { mutableFloatStateOf(1.0f) }

    // Smooth Reset View Handler
    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            targetYaw = -20f
            targetPitch = 6f
            targetZoom = 1.0f
        }
    }

    // Decode original 64x64 PNG losslessly
    val skinImage = remember(skinBytes) {
        if (skinBytes != null && skinBytes.isNotEmpty()) {
            try {
                val img = ImageIO.read(ByteArrayInputStream(skinBytes))
                if (img != null) {
                    img
                } else {
                    generateDefaultSteveSkin()
                }
            } catch (e: Exception) {
                generateDefaultSteveSkin()
            }
        } else {
            generateDefaultSteveSkin()
        }
    }

    // Frame-rate Independent Delta-Time Animation Loop for Smooth Damping (NO auto-rotation)
    LaunchedEffect(Unit) {
        var lastTimeNanos = 0L
        while (true) {
            withFrameNanos { timeNanos ->
                if (lastTimeNanos != 0L) {
                    val deltaSeconds = ((timeNanos - lastTimeNanos) / 1_000_000_000.0).toFloat().coerceIn(0.001f, 0.1f)

                    // Exponential smooth damping (Spring factor: 20.0 for crisp, responsive manual tracking)
                    val dampFactor = (1.0 - exp(-20.0 * deltaSeconds)).toFloat()
                    currentYaw += (targetYaw - currentYaw) * dampFactor
                    currentPitch += (targetPitch - currentPitch) * dampFactor
                    currentZoom += (targetZoom - currentZoom) * dampFactor
                }
                lastTimeNanos = timeNanos
            }
        }
    }

    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1C1C1C),
                        Color(0xFF101010),
                        Color(0xFF060606)
                    )
                )
            )
            .border(1.dp, Color(0xFF222222), RoundedCornerShape(10.dp))
            // Mouse Drag: Drag RIGHT -> Rotate RIGHT, Drag LEFT -> Rotate LEFT
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()

                    // Mouse drag RIGHT (dragAmount.x > 0) -> Player rotates RIGHT (targetYaw increases)
                    // Mouse drag LEFT (dragAmount.x < 0) -> Player rotates LEFT (targetYaw decreases)
                    targetYaw = (targetYaw + dragAmount.x * 0.45f) % 360f

                    // Vertical drag: Subtle camera pitch adjustment (Clamped [-28°, +28°] to prevent flipping)
                    targetPitch = (targetPitch + dragAmount.y * 0.30f).coerceIn(-28f, 28f)
                }
            }
            // Mouse Wheel Scroll: Smooth Zoom
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (delta != 0f) {
                                targetZoom = (targetZoom - delta * 0.08f).coerceIn(0.70f, 1.80f)
                            }
                        }
                    }
                }
            }
    ) {
        val widthPx = with(density) { maxWidth.toPx().toInt() }.coerceAtLeast(100)
        val heightPx = with(density) { maxHeight.toPx().toInt() }.coerceAtLeast(100)

        // Render current 3D frame using our high-performance Z-buffer rasterizer
        val renderedFrame = remember(widthPx, heightPx, currentYaw, currentPitch, currentZoom, modelType, skinImage) {
            renderPlayerModelFrame(
                width = widthPx,
                height = heightPx,
                yaw = currentYaw,
                pitch = currentPitch,
                zoom = currentZoom,
                modelType = modelType,
                skin = skinImage
            )
        }

        if (renderedFrame != null) {
            Image(
                bitmap = renderedFrame,
                contentDescription = "Minecraft Player 3D",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Renders a complete 3D frame using a high-precision software Z-buffer.
 */
private fun renderPlayerModelFrame(
    width: Int,
    height: Int,
    yaw: Float,
    pitch: Float,
    zoom: Float,
    modelType: SkinModelType,
    skin: BufferedImage
): ImageBitmap? {
    if (width <= 0 || height <= 0) return null

    val rasterizer = ZBufferRasterizer(width, height)
    rasterizer.clear()

    // Character is 32 units tall -> Scale to fill ~74% of viewport
    val baseScale = ((height * 0.74f) / 32f) * zoom

    // Pivot Center: Player chest/torso (X = 0, Y = -16, Z = 0)
    val centerX = width / 2f
    val centerY = height * 0.50f

    val yawRad = (yaw * PI / 180.0).toFloat()
    val pitchRad = (pitch * PI / 180.0).toFloat()

    // 1. Draw Soft Ground Contact Shadow at Feet Plane (Y = 0)
    rasterizer.drawGroundShadow(
        pivotY = -16f,
        yawRad = yawRad,
        pitchRad = pitchRad,
        centerX = centerX,
        centerY = centerY,
        scale = baseScale,
        pitchDeg = pitch
    )

    val armW = if (modelType == SkinModelType.ALEX) 3f else 4f

    // List of 3D triangles to render
    val triangles = mutableListOf<ModelTriangle3D>()

    // ==========================================
    // 1. HEAD (8x8x8) at [X: -4..4, Y: -32..-24, Z: -4..4]
    // ==========================================
    addCuboidTriangles(
        triangles = triangles,
        minX = -4f, maxX = 4f,
        minY = -32f, maxY = -24f,
        minZ = -4f, maxZ = 4f,
        uvTop = UvRect(8, 0, 8, 8),
        uvBottom = UvRect(16, 0, 8, 8),
        uvRight = UvRect(0, 8, 8, 8),
        uvFront = UvRect(8, 8, 8, 8),
        uvLeft = UvRect(16, 8, 8, 8),
        uvBack = UvRect(24, 8, 8, 8),
        isOverlay = false
    )
    // Head Hat Overlay (+0.45 expand)
    addCuboidTriangles(
        triangles = triangles,
        minX = -4.45f, maxX = 4.45f,
        minY = -32.45f, maxY = -23.55f,
        minZ = -4.45f, maxZ = 4.45f,
        uvTop = UvRect(40, 0, 8, 8),
        uvBottom = UvRect(48, 0, 8, 8),
        uvRight = UvRect(32, 8, 8, 8),
        uvFront = UvRect(40, 8, 8, 8),
        uvLeft = UvRect(48, 8, 8, 8),
        uvBack = UvRect(56, 8, 8, 8),
        isOverlay = true
    )

    // ==========================================
    // 2. TORSO / BODY (8x12x4) at [X: -4..4, Y: -24..-12, Z: -2..2]
    // ==========================================
    addCuboidTriangles(
        triangles = triangles,
        minX = -4f, maxX = 4f,
        minY = -24f, maxY = -12f,
        minZ = -2f, maxZ = 2f,
        uvTop = UvRect(20, 16, 8, 4),
        uvBottom = UvRect(28, 16, 8, 4),
        uvRight = UvRect(16, 20, 4, 12),
        uvFront = UvRect(20, 20, 8, 12),
        uvLeft = UvRect(28, 20, 4, 12),
        uvBack = UvRect(32, 20, 8, 12),
        isOverlay = false
    )
    // Torso Jacket Overlay (+0.35 expand)
    addCuboidTriangles(
        triangles = triangles,
        minX = -4.35f, maxX = 4.35f,
        minY = -24.35f, maxY = -11.65f,
        minZ = -2.35f, maxZ = 2.35f,
        uvTop = UvRect(20, 32, 8, 4),
        uvBottom = UvRect(28, 32, 8, 4),
        uvRight = UvRect(16, 36, 4, 12),
        uvFront = UvRect(20, 36, 8, 12),
        uvLeft = UvRect(28, 36, 4, 12),
        uvBack = UvRect(32, 36, 8, 12),
        isOverlay = true
    )

    // ==========================================
    // 3. RIGHT ARM (Steve 4x12x4, Alex 3x12x4)
    // ==========================================
    val rArmMinX = -4f - armW
    val rArmMaxX = -4f
    if (modelType == SkinModelType.ALEX) {
        addCuboidTriangles(
            triangles = triangles,
            minX = rArmMinX, maxX = rArmMaxX,
            minY = -24f, maxY = -12f,
            minZ = -2f, maxZ = 2f,
            uvTop = UvRect(44, 16, 3, 4),
            uvBottom = UvRect(47, 16, 3, 4),
            uvRight = UvRect(40, 20, 4, 12),
            uvFront = UvRect(44, 20, 3, 12),
            uvLeft = UvRect(47, 20, 4, 12),
            uvBack = UvRect(51, 20, 3, 12),
            isOverlay = false
        )
        // Alex Right Arm Sleeve
        addCuboidTriangles(
            triangles = triangles,
            minX = rArmMinX - 0.35f, maxX = rArmMaxX + 0.35f,
            minY = -24.35f, maxY = -11.65f,
            minZ = -2.35f, maxZ = 2.35f,
            uvTop = UvRect(44, 32, 3, 4),
            uvBottom = UvRect(47, 32, 3, 4),
            uvRight = UvRect(40, 36, 4, 12),
            uvFront = UvRect(44, 36, 3, 12),
            uvLeft = UvRect(47, 36, 4, 12),
            uvBack = UvRect(51, 36, 3, 12),
            isOverlay = true
        )
    } else {
        addCuboidTriangles(
            triangles = triangles,
            minX = rArmMinX, maxX = rArmMaxX,
            minY = -24f, maxY = -12f,
            minZ = -2f, maxZ = 2f,
            uvTop = UvRect(44, 16, 4, 4),
            uvBottom = UvRect(48, 16, 4, 4),
            uvRight = UvRect(40, 20, 4, 12),
            uvFront = UvRect(44, 20, 4, 12),
            uvLeft = UvRect(48, 20, 4, 12),
            uvBack = UvRect(52, 20, 4, 12),
            isOverlay = false
        )
        // Steve Right Arm Sleeve
        addCuboidTriangles(
            triangles = triangles,
            minX = rArmMinX - 0.35f, maxX = rArmMaxX + 0.35f,
            minY = -24.35f, maxY = -11.65f,
            minZ = -2.35f, maxZ = 2.35f,
            uvTop = UvRect(44, 32, 4, 4),
            uvBottom = UvRect(48, 32, 4, 4),
            uvRight = UvRect(40, 36, 4, 12),
            uvFront = UvRect(44, 36, 4, 12),
            uvLeft = UvRect(48, 36, 4, 12),
            uvBack = UvRect(52, 36, 4, 12),
            isOverlay = true
        )
    }

    // ==========================================
    // 4. LEFT ARM (Steve 4x12x4, Alex 3x12x4)
    // ==========================================
    val lArmMinX = 4f
    val lArmMaxX = 4f + armW
    if (modelType == SkinModelType.ALEX) {
        addCuboidTriangles(
            triangles = triangles,
            minX = lArmMinX, maxX = lArmMaxX,
            minY = -24f, maxY = -12f,
            minZ = -2f, maxZ = 2f,
            uvTop = UvRect(36, 48, 3, 4),
            uvBottom = UvRect(39, 48, 3, 4),
            uvRight = UvRect(32, 52, 4, 12),
            uvFront = UvRect(36, 52, 3, 12),
            uvLeft = UvRect(39, 52, 4, 12),
            uvBack = UvRect(43, 52, 3, 12),
            isOverlay = false
        )
        // Alex Left Arm Sleeve
        addCuboidTriangles(
            triangles = triangles,
            minX = lArmMinX - 0.35f, maxX = lArmMaxX + 0.35f,
            minY = -24.35f, maxY = -11.65f,
            minZ = -2.35f, maxZ = 2.35f,
            uvTop = UvRect(52, 48, 3, 4),
            uvBottom = UvRect(55, 48, 3, 4),
            uvRight = UvRect(48, 52, 4, 12),
            uvFront = UvRect(52, 52, 3, 12),
            uvLeft = UvRect(55, 52, 4, 12),
            uvBack = UvRect(59, 52, 3, 12),
            isOverlay = true
        )
    } else {
        addCuboidTriangles(
            triangles = triangles,
            minX = lArmMinX, maxX = lArmMaxX,
            minY = -24f, maxY = -12f,
            minZ = -2f, maxZ = 2f,
            uvTop = UvRect(36, 48, 4, 4),
            uvBottom = UvRect(40, 48, 4, 4),
            uvRight = UvRect(32, 52, 4, 12),
            uvFront = UvRect(36, 52, 4, 12),
            uvLeft = UvRect(40, 52, 4, 12),
            uvBack = UvRect(44, 52, 4, 12),
            isOverlay = false
        )
        // Steve Left Arm Sleeve
        addCuboidTriangles(
            triangles = triangles,
            minX = lArmMinX - 0.35f, maxX = lArmMaxX + 0.35f,
            minY = -24.35f, maxY = -11.65f,
            minZ = -2.35f, maxZ = 2.35f,
            uvTop = UvRect(52, 48, 4, 4),
            uvBottom = UvRect(56, 48, 4, 4),
            uvRight = UvRect(48, 52, 4, 12),
            uvFront = UvRect(52, 52, 4, 12),
            uvLeft = UvRect(56, 52, 4, 12),
            uvBack = UvRect(60, 52, 4, 12),
            isOverlay = true
        )
    }

    // ==========================================
    // 5. RIGHT LEG (4x12x4) at [X: -4..0, Y: -12..0, Z: -2..2]
    // ==========================================
    addCuboidTriangles(
        triangles = triangles,
        minX = -4f, maxX = 0f,
        minY = -12f, maxY = 0f,
        minZ = -2f, maxZ = 2f,
        uvTop = UvRect(4, 16, 4, 4),
        uvBottom = UvRect(8, 16, 4, 4),
        uvRight = UvRect(0, 20, 4, 12),
        uvFront = UvRect(4, 20, 4, 12),
        uvLeft = UvRect(8, 20, 4, 12),
        uvBack = UvRect(12, 20, 4, 12),
        isOverlay = false
    )
    // Right Leg Pants Overlay (+0.35 expand)
    addCuboidTriangles(
        triangles = triangles,
        minX = -4.35f, maxX = 0.35f,
        minY = -12.35f, maxY = 0.35f,
        minZ = -2.35f, maxZ = 2.35f,
        uvTop = UvRect(4, 32, 4, 4),
        uvBottom = UvRect(8, 32, 4, 4),
        uvRight = UvRect(0, 36, 4, 12),
        uvFront = UvRect(4, 36, 4, 12),
        uvLeft = UvRect(8, 36, 4, 12),
        uvBack = UvRect(12, 36, 4, 12),
        isOverlay = true
    )

    // ==========================================
    // 6. LEFT LEG (4x12x4) at [X: 0..4, Y: -12..0, Z: -2..2]
    // ==========================================
    addCuboidTriangles(
        triangles = triangles,
        minX = 0f, maxX = 4f,
        minY = -12f, maxY = 0f,
        minZ = -2f, maxZ = 2f,
        uvTop = UvRect(20, 48, 4, 4),
        uvBottom = UvRect(24, 48, 4, 4),
        uvRight = UvRect(16, 52, 4, 12),
        uvFront = UvRect(20, 52, 4, 12),
        uvLeft = UvRect(24, 52, 4, 12),
        uvBack = UvRect(28, 52, 4, 12),
        isOverlay = false
    )
    // Left Leg Pants Overlay (+0.35 expand)
    addCuboidTriangles(
        triangles = triangles,
        minX = -0.35f, maxX = 4.35f,
        minY = -12.35f, maxY = 0.35f,
        minZ = -2.35f, maxZ = 2.35f,
        uvTop = UvRect(4, 48, 4, 4),
        uvBottom = UvRect(8, 48, 4, 4),
        uvRight = UvRect(0, 52, 4, 12),
        uvFront = UvRect(4, 52, 4, 12),
        uvLeft = UvRect(8, 52, 4, 12),
        uvBack = UvRect(12, 52, 4, 12),
        isOverlay = true
    )

    // Render all triangles with Z-buffering
    for (tri in triangles) {
        val s0 = projectVertex(tri.v0, -16f, yawRad, pitchRad, centerX, centerY, baseScale, tri.u0, tri.vTex0, tri.light)
        val s1 = projectVertex(tri.v1, -16f, yawRad, pitchRad, centerX, centerY, baseScale, tri.u1, tri.vTex1, tri.light)
        val s2 = projectVertex(tri.v2, -16f, yawRad, pitchRad, centerX, centerY, baseScale, tri.u2, tri.vTex2, tri.light)

        rasterizer.drawTriangle(s0, s1, s2, skin, tri.isOverlay)
    }

    return rasterizer.finish()
}

/**
 * Pixel-Perfect Software Z-Buffer Rasterizer.
 */
private class ZBufferRasterizer(val width: Int, val height: Int) {
    val depthBuffer = FloatArray(width * height)
    val colorBuffer = IntArray(width * height)
    val outBitmap = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

    fun clear() {
        depthBuffer.fill(Float.POSITIVE_INFINITY)
        colorBuffer.fill(0)
    }

    fun drawGroundShadow(
        pivotY: Float,
        yawRad: Float,
        pitchRad: Float,
        centerX: Float,
        centerY: Float,
        scale: Float,
        pitchDeg: Float
    ) {
        val feet = projectVertex(Vec3(0f, 0f, 0f), pivotY, yawRad, pitchRad, centerX, centerY, scale, 0f, 0f, 1f)
        val rx = 14f * scale
        val ry = 5.2f * scale * (1f - (pitchDeg / 90f).coerceIn(-0.4f, 0.4f))

        val minX = max(0, (feet.screenX - rx).toInt())
        val maxX = min(width - 1, (feet.screenX + rx).toInt())
        val minY = max(0, (feet.screenY - ry).toInt())
        val maxY = min(height - 1, (feet.screenY + ry).toInt())

        val rxSq = rx * rx
        val rySq = ry * ry

        for (py in minY..maxY) {
            val dy = py - feet.screenY
            val dySq = dy * dy
            var rowIdx = py * width + minX
            for (px in minX..maxX) {
                val dx = px - feet.screenX
                val distNorm = (dx * dx) / rxSq + dySq / rySq
                if (distNorm <= 1f) {
                    val alphaF = (1f - distNorm) * 0.45f
                    val alphaInt = (alphaF * 255).toInt().coerceIn(0, 255)
                    colorBuffer[rowIdx] = (alphaInt shl 24)
                }
                rowIdx++
            }
        }
    }

    fun drawTriangle(
        v0: ScreenVertex,
        v1: ScreenVertex,
        v2: ScreenVertex,
        skin: BufferedImage,
        isOverlay: Boolean
    ) {
        // Backface culling: Counter-clockwise winding cross product
        val cross = (v1.screenX - v0.screenX) * (v2.screenY - v0.screenY) - (v1.screenY - v0.screenY) * (v2.screenX - v0.screenX)
        if (cross <= 0.0001f) return // Discard back-facing triangles

        val minX = max(0, min(v0.screenX, min(v1.screenX, v2.screenX)).toInt())
        val maxX = min(width - 1, max(v0.screenX, max(v1.screenX, v2.screenX)).toInt())
        val minY = max(0, min(v0.screenY, min(v1.screenY, v2.screenY)).toInt())
        val maxY = min(height - 1, max(v0.screenY, max(v1.screenY, v2.screenY)).toInt())
        if (minX > maxX || minY > maxY) return

        val area = cross
        val invArea = 1.0f / area

        val zBias = if (isOverlay) -0.15f else 0.0f

        for (py in minY..maxY) {
            val pY = py + 0.5f
            var rowIdx = py * width + minX

            for (px in minX..maxX) {
                val pX = px + 0.5f

                // Barycentric weights
                val w0 = (v2.screenX - v1.screenX) * (pY - v1.screenY) - (v2.screenY - v1.screenY) * (pX - v1.screenX)
                val w1 = (v0.screenX - v2.screenX) * (pY - v2.screenY) - (v0.screenY - v2.screenY) * (pX - v2.screenX)
                val w2 = (v1.screenX - v0.screenX) * (pY - v0.screenY) - (v1.screenY - v0.screenY) * (pX - v0.screenX)

                if (w0 >= 0f && w1 >= 0f && w2 >= 0f) {
                    val b0 = w0 * invArea
                    val b1 = w1 * invArea
                    val b2 = w2 * invArea

                    // Depth test
                    val z = b0 * v0.z + b1 * v1.z + b2 * v2.z + zBias

                    if (z < depthBuffer[rowIdx]) {
                        // Nearest-neighbor texel UV lookup (Clamp-To-Edge)
                        val u = (b0 * v0.u + b1 * v1.u + b2 * v2.u).toInt().coerceIn(0, 63)
                        val v = (b0 * v0.v + b1 * v1.v + b2 * v2.v).toInt().coerceIn(0, 63)

                        val argb = skin.getRGB(u, v)
                        val alpha = (argb ushr 24) and 0xFF

                        // Alpha cutout (transparent discarded, opaque drawn solid)
                        if (alpha >= 128) {
                            depthBuffer[rowIdx] = z

                            // Apply directional light multiplier
                            val light = b0 * v0.light + b1 * v1.light + b2 * v2.light
                            val r = (((argb ushr 16) and 0xFF) * light).toInt().coerceIn(0, 255)
                            val g = (((argb ushr 8) and 0xFF) * light).toInt().coerceIn(0, 255)
                            val b = ((argb and 0xFF) * light).toInt().coerceIn(0, 255)

                            colorBuffer[rowIdx] = (255 shl 24) or (r shl 16) or (g shl 8) or b
                        }
                    }
                }
                rowIdx++
            }
        }
    }

    fun finish(): ImageBitmap {
        outBitmap.setRGB(0, 0, width, height, colorBuffer, 0, width)
        return outBitmap.toComposeImageBitmap()
    }
}

/**
 * 2-Layer Avatar Head Thumbnail for Skin Collection Cards.
 * Preserves exact sRGB colors and nearest-neighbor scaling.
 */
@Composable
fun SkinAvatarHeadThumbnail(
    skinBytes: ByteArray?,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(skinBytes) {
        try {
            val img = if (skinBytes != null && skinBytes.isNotEmpty()) {
                ImageIO.read(ByteArrayInputStream(skinBytes))
            } else {
                generateDefaultSteveSkin()
            }
            createHeadBitmap(img)
        } catch (e: Exception) {
            createHeadBitmap(generateDefaultSteveSkin())
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF141414))
            .border(1.dp, Color(0xFF282828), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Skin Head",
                modifier = Modifier.size(size - 4.dp)
            )
        }
    }
}

private fun createHeadBitmap(skin: BufferedImage): ImageBitmap? {
    val headSize = 8
    val scale = 8
    val outSize = headSize * scale

    val outImage = BufferedImage(outSize, outSize, BufferedImage.TYPE_INT_ARGB)

    for (y in 0 until headSize) {
        for (x in 0 until headSize) {
            val baseArgb = skin.getRGB(8 + x, 8 + y)
            val baseA = (baseArgb ushr 24) and 0xFF
            val baseR = (baseArgb ushr 16) and 0xFF
            val baseG = (baseArgb ushr 8) and 0xFF
            val baseB = baseArgb and 0xFF

            val hatArgb = if (skin.width >= 64 && skin.height >= 16) skin.getRGB(40 + x, 8 + y) else 0
            val hatA = (hatArgb ushr 24) and 0xFF

            val finalR: Int
            val finalG: Int
            val finalB: Int
            val finalA: Int

            if (hatA > 20) {
                val alphaF = hatA / 255f
                finalR = (((hatArgb ushr 16) and 0xFF) * alphaF + baseR * (1f - alphaF)).toInt().coerceIn(0, 255)
                finalG = (((hatArgb ushr 8) and 0xFF) * alphaF + baseG * (1f - alphaF)).toInt().coerceIn(0, 255)
                finalB = ((hatArgb and 0xFF) * alphaF + baseB * (1f - alphaF)).toInt().coerceIn(0, 255)
                finalA = 255
            } else {
                finalR = baseR
                finalG = baseG
                finalB = baseB
                finalA = if (baseA > 20) 255 else 0
            }

            val argb = (finalA shl 24) or (finalR shl 16) or (finalG shl 8) or finalB

            for (dy in 0 until scale) {
                for (dx in 0 until scale) {
                    outImage.setRGB(x * scale + dx, y * scale + dy, argb)
                }
            }
        }
    }

    return outImage.toComposeImageBitmap()
}

private data class UvRect(val x: Int, val y: Int, val w: Int, val h: Int)
private data class Vec3(val x: Float, val y: Float, val z: Float)
private data class ScreenVertex(val screenX: Float, val screenY: Float, val z: Float, val u: Float, val v: Float, val light: Float)

private data class ModelTriangle3D(
    val v0: Vec3, val u0: Float, val vTex0: Float,
    val v1: Vec3, val u1: Float, val vTex1: Float,
    val v2: Vec3, val u2: Float, val vTex2: Float,
    val light: Float,
    val isOverlay: Boolean
)

/**
 * Body-Centered Perspective Camera Projection.
 * Pivot is centered at player chest (pivotY = -16).
 */
private fun projectVertex(
    v: Vec3,
    pivotY: Float,
    yawRad: Float,
    pitchRad: Float,
    centerX: Float,
    centerY: Float,
    scale: Float,
    u: Float,
    vTex: Float,
    light: Float
): ScreenVertex {
    // 1. Shift vertex relative to chest center pivot
    val x0 = v.x
    val y0 = v.y - pivotY
    val z0 = v.z

    // 2. Yaw rotation around Y axis (PlayerRoot horizontal rotation)
    val cosYaw = cos(yawRad)
    val sinYaw = sin(yawRad)
    val x1 = x0 * cosYaw - z0 * sinYaw
    val z1 = x0 * sinYaw + z0 * cosYaw
    val y1 = y0

    // 3. Pitch tilt around X axis
    val cosPitch = cos(pitchRad)
    val sinPitch = sin(pitchRad)
    val y2 = y1 * cosPitch - z1 * sinPitch
    val z2 = y1 * sinPitch + z1 * cosPitch
    val x2 = x1

    // 4. Perspective Camera
    val cameraDist = 85f
    val fovFactor = cameraDist / (cameraDist + z2).coerceAtLeast(10f)

    val screenX = centerX + (x2 * scale * fovFactor)
    val screenY = centerY + (y2 * scale * fovFactor)

    return ScreenVertex(screenX, screenY, z2, u, vTex, light)
}

/**
 * Adds the 12 triangles (6 cuboid faces x 2 triangles) of a Minecraft solid box.
 * Winding is strictly counter-clockwise for outward-pointing normals.
 */
private fun addCuboidTriangles(
    triangles: MutableList<ModelTriangle3D>,
    minX: Float, maxX: Float,
    minY: Float, maxY: Float,
    minZ: Float, maxZ: Float,
    uvTop: UvRect,
    uvBottom: UvRect,
    uvRight: UvRect,
    uvFront: UvRect,
    uvLeft: UvRect,
    uvBack: UvRect,
    isOverlay: Boolean
) {
    fun addQuad(
        v0: Vec3, v1: Vec3, v2: Vec3, v3: Vec3,
        uv: UvRect,
        light: Float
    ) {
        val uL = uv.x.toFloat()
        val uR = (uv.x + uv.w).toFloat()
        val vT = uv.y.toFloat()
        val vB = (uv.y + uv.h).toFloat()

        // Triangle 1: (v0, v1, v2)
        triangles.add(
            ModelTriangle3D(
                v0 = v0, u0 = uL, vTex0 = vT,
                v1 = v1, u1 = uR, vTex1 = vT,
                v2 = v2, u2 = uR, vTex2 = vB,
                light = light,
                isOverlay = isOverlay
            )
        )
        // Triangle 2: (v0, v2, v3)
        triangles.add(
            ModelTriangle3D(
                v0 = v0, u0 = uL, vTex0 = vT,
                v1 = v2, u1 = uR, vTex1 = vB,
                v2 = v3, u2 = uL, vTex2 = vB,
                light = light,
                isOverlay = isOverlay
            )
        )
    }

    // 1. TOP FACE (y = minY, looking down) -> Counter-clockwise: (minX, maxZ) -> (maxX, maxZ) -> (maxX, minZ) -> (minX, minZ)
    addQuad(
        v0 = Vec3(minX, minY, maxZ),
        v1 = Vec3(maxX, minY, maxZ),
        v2 = Vec3(maxX, minY, minZ),
        v3 = Vec3(minX, minY, minZ),
        uv = uvTop,
        light = 1.08f
    )

    // 2. BOTTOM FACE (y = maxY, looking up) -> Counter-clockwise: (minX, minZ) -> (maxX, minZ) -> (maxX, maxZ) -> (minX, maxZ)
    addQuad(
        v0 = Vec3(minX, maxY, minZ),
        v1 = Vec3(maxX, maxY, minZ),
        v2 = Vec3(maxX, maxY, maxZ),
        v3 = Vec3(minX, maxY, maxZ),
        uv = uvBottom,
        light = 0.58f
    )

    // 3. FRONT FACE (z = minZ, looking towards +Z) -> Counter-clockwise: (minX, minY) -> (maxX, minY) -> (maxX, maxY) -> (minX, maxY)
    addQuad(
        v0 = Vec3(minX, minY, minZ),
        v1 = Vec3(maxX, minY, minZ),
        v2 = Vec3(maxX, maxY, minZ),
        v3 = Vec3(minX, maxY, minZ),
        uv = uvFront,
        light = 1.00f
    )

    // 4. BACK FACE (z = maxZ, looking towards -Z) -> Counter-clockwise: (maxX, minY) -> (minX, minY) -> (minX, maxY) -> (maxX, maxY)
    addQuad(
        v0 = Vec3(maxX, minY, maxZ),
        v1 = Vec3(minX, minY, maxZ),
        v2 = Vec3(minX, maxY, maxZ),
        v3 = Vec3(maxX, maxY, maxZ),
        uv = uvBack,
        light = 0.72f
    )

    // 5. RIGHT FACE (x = minX, looking towards +X) -> Counter-clockwise: (minX, maxZ) -> (minX, minZ) -> (minX, minZ) -> (minX, maxZ)
    addQuad(
        v0 = Vec3(minX, minY, maxZ),
        v1 = Vec3(minX, minY, minZ),
        v2 = Vec3(minX, maxY, minZ),
        v3 = Vec3(minX, maxY, maxZ),
        uv = uvRight,
        light = 0.85f
    )

    // 6. LEFT FACE (x = maxX, looking towards -X) -> Counter-clockwise: (maxX, minZ) -> (maxX, maxZ) -> (maxX, maxZ) -> (maxX, minZ)
    addQuad(
        v0 = Vec3(maxX, minY, minZ),
        v1 = Vec3(maxX, minY, maxZ),
        v2 = Vec3(maxX, maxY, maxZ),
        v3 = Vec3(maxX, maxY, minZ),
        uv = uvLeft,
        light = 0.85f
    )
}

fun generateDefaultSteveSkin(): BufferedImage {
    return io.ezz.launcher.core.minecraft.skin.DefaultMinecraftSkin.getSteveSkinBufferedImage()
}
