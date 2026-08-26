package io.ezz.launcher.ui.vault

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.ezz.launcher.core.model.skin.SkinModelType
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Vault V3 — Studio-Grade 3D Minecraft Player Model Renderer.
 *
 * Key Architecture:
 * 1. Pixel-Perfect Nearest-Neighbor texture sampling from canonical 64x64 PNG.
 * 2. Body-Centered Orbit Camera (Target = Chest Pivot at Y = -16).
 * 3. Smooth exponential damping for manual drag, scroll zoom, and reset animation.
 * 4. Delta-time auto-rotation (12s period) with automatic drag-pause & 1.5s resumption.
 * 5. Full dual-layer geometry: Head, Torso, Arms, Legs + Hat, Jacket, Sleeves, Pants.
 * 6. Verified Steve (4px) and Alex (3px) Minecraft Java Edition UV mapping.
 * 7. Soft ground contact shadow aligned at player feet (Y = 0).
 */
@Composable
fun MinecraftPlayerModel3DView(
    skinBytes: ByteArray?,
    modelType: SkinModelType = SkinModelType.STEVE,
    autoRotate: Boolean = true,
    resetTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    // Camera Target Angles (Updated by user interaction or auto-rotation)
    var targetYaw by remember { mutableFloatStateOf(-20f) }
    var targetPitch by remember { mutableFloatStateOf(8f) }
    var targetZoom by remember { mutableFloatStateOf(1.0f) }

    // Interpolated Damped Camera Angles (Rendered every frame)
    var currentYaw by remember { mutableFloatStateOf(-20f) }
    var currentPitch by remember { mutableFloatStateOf(8f) }
    var currentZoom by remember { mutableFloatStateOf(1.0f) }

    var isDragging by remember { mutableStateOf(false) }
    var lastDragEndTime by remember { mutableLongStateOf(0L) }

    // Handle Reset View Trigger (Smooth transition)
    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            targetYaw = -20f
            targetPitch = 8f
            targetZoom = 1.0f
        }
    }

    // Parse skin texture bitmap directly from canonical PNG bytes (No downscaling)
    val skinImage = remember(skinBytes) {
        if (skinBytes != null && skinBytes.isNotEmpty()) {
            try {
                val img = ImageIO.read(ByteArrayInputStream(skinBytes))
                if (img != null) {
                    println("[VaultRenderer] Skin loaded: ${img.width}x${img.height} (Filter: Nearest-Neighbor, Model: $modelType)")
                    img
                } else {
                    generateDefaultSteveSkin()
                }
            } catch (e: Exception) {
                println("[VaultRenderer] Error decoding skin: ${e.message}")
                generateDefaultSteveSkin()
            }
        } else {
            generateDefaultSteveSkin()
        }
    }

    // Subtle Harmonic Idle Animation (Breathing & Limb swing)
    val infiniteTransition = rememberInfiniteTransition(label = "idle_anim")
    val animTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "animTime"
    )

    // Frame-rate Independent Delta-Time Animation Loop for Orbit Damping & Auto-Rotation
    LaunchedEffect(autoRotate) {
        var lastTimeNanos = 0L
        while (true) {
            withFrameNanos { timeNanos ->
                if (lastTimeNanos != 0L) {
                    val deltaSeconds = ((timeNanos - lastTimeNanos) / 1_000_000_000.0).toFloat().coerceIn(0.001f, 0.1f)
                    val now = System.currentTimeMillis()

                    // Auto-Rotate at constant speed (~30 deg/sec = 1 full 360 rotation every 12 seconds)
                    if (autoRotate && !isDragging && (now - lastDragEndTime > 1500L)) {
                        targetYaw = (targetYaw + 30f * deltaSeconds) % 360f
                    }

                    // Exponential smooth damping (Spring factor: 14.0)
                    val dampFactor = (1.0 - exp(-14.0 * deltaSeconds)).toFloat()
                    currentYaw += (targetYaw - currentYaw) * dampFactor
                    currentPitch += (targetPitch - currentPitch) * dampFactor
                    currentZoom += (targetZoom - currentZoom) * dampFactor
                }
                lastTimeNanos = timeNanos
            }
        }
    }

    Box(
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
            // Mouse Drag Interaction for Orbit Camera
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                    },
                    onDragEnd = {
                        isDragging = false
                        lastDragEndTime = System.currentTimeMillis()
                    },
                    onDragCancel = {
                        isDragging = false
                        lastDragEndTime = System.currentTimeMillis()
                    }
                ) { change, dragAmount ->
                    change.consume()
                    isDragging = true
                    lastDragEndTime = System.currentTimeMillis()

                    // Natural horizontal orbit (Drag Left -> character turns left)
                    targetYaw = (targetYaw - dragAmount.x * 0.45f) % 360f

                    // Bounded vertical orbit (Clamped between -35 deg and +35 deg to prevent camera flip)
                    targetPitch = (targetPitch + dragAmount.y * 0.35f).coerceIn(-35f, 35f)
                }
            }
            // Mouse Wheel Scroll for Smooth Zoom
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (delta != 0f) {
                                lastDragEndTime = System.currentTimeMillis()
                                targetZoom = (targetZoom - delta * 0.08f).coerceIn(0.70f, 1.80f)
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height
            if (canvasW <= 0f || canvasH <= 0f) return@Canvas

            // Scale to fill ~74% of canvas height (Character is 32 Minecraft units tall)
            val baseScale = ((canvasH * 0.74f) / 32f) * currentZoom

            // Orbit center pivot: Center of player torso/chest (X = 0, Y = -16, Z = 0)
            val centerX = canvasW / 2f
            val centerY = canvasH * 0.50f

            val yawRad = (currentYaw * PI / 180.0).toFloat()
            val pitchRad = (currentPitch * PI / 180.0).toFloat()

            // 1. Draw Soft Ground Contact Shadow at Feet Plane (Y = 0)
            val feetScreenPos = projectVertex(
                v = Vec3(0f, 0f, 0f),
                pivotY = -16f,
                yawRad = yawRad,
                pitchRad = pitchRad,
                centerX = centerX,
                centerY = centerY,
                scale = baseScale
            )
            val shadowW = 15f * baseScale
            val shadowH = 5f * baseScale * (1f - (currentPitch / 90f).coerceIn(-0.5f, 0.5f))
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xB3000000), Color(0x40000000), Color.Transparent),
                    center = Offset(feetScreenPos.screenX, feetScreenPos.screenY),
                    radius = shadowW
                ),
                topLeft = Offset(feetScreenPos.screenX - shadowW, feetScreenPos.screenY - shadowH / 2f),
                size = androidx.compose.ui.geometry.Size(shadowW * 2f, shadowH)
            )

            val armW = if (modelType == SkinModelType.ALEX) 3 else 4

            // Build all textured 3D quads
            val quads = mutableListOf<RenderQuad>()

            // Subtle harmonic breathing offset & arm resting swing
            val breathOffset = sin(animTime.toDouble()).toFloat() * 0.20f
            val armSwing = sin(animTime.toDouble()).toFloat() * 1.8f

            // ==========================================
            // 1. HEAD (8x8x8) at [X: -4..4, Y: -32..-24, Z: -4..4]
            // ==========================================
            buildBoxFaces(
                quads = quads, skin = skinImage,
                minX = -4f, maxX = 4f,
                minY = -32f - breathOffset, maxY = -24f - breathOffset,
                minZ = -4f, maxZ = 4f,
                uvTop = UvRect(8, 0, 8, 8),
                uvBottom = UvRect(16, 0, 8, 8),
                uvRight = UvRect(0, 8, 8, 8),
                uvFront = UvRect(8, 8, 8, 8),
                uvLeft = UvRect(16, 8, 8, 8),
                uvBack = UvRect(24, 8, 8, 8),
                isOverlay = false
            )
            // Head Hat Overlay (+0.5 expand)
            buildBoxFaces(
                quads = quads, skin = skinImage,
                minX = -4.5f, maxX = 4.5f,
                minY = -32.5f - breathOffset, maxY = -23.5f - breathOffset,
                minZ = -4.5f, maxZ = 4.5f,
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
            buildBoxFaces(
                quads = quads, skin = skinImage,
                minX = -4f, maxX = 4f,
                minY = -24f - breathOffset, maxY = -12f,
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
            buildBoxFaces(
                quads = quads, skin = skinImage,
                minX = -4.35f, maxX = 4.35f,
                minY = -24.35f - breathOffset, maxY = -11.65f,
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
                buildBoxFaces(
                    quads = quads, skin = skinImage,
                    minX = rArmMinX, maxX = rArmMaxX,
                    minY = -24f - breathOffset, maxY = -12f - breathOffset,
                    minZ = -2f, maxZ = 2f,
                    uvTop = UvRect(44, 16, 3, 4),
                    uvBottom = UvRect(47, 16, 3, 4),
                    uvRight = UvRect(40, 20, 4, 12),
                    uvFront = UvRect(44, 20, 3, 12),
                    uvLeft = UvRect(47, 20, 4, 12),
                    uvBack = UvRect(51, 20, 3, 12),
                    isOverlay = false,
                    pitchOffset = armSwing
                )
                // Alex Right Arm Sleeve
                buildBoxFaces(
                    quads = quads, skin = skinImage,
                    minX = rArmMinX - 0.35f, maxX = rArmMaxX + 0.35f,
                    minY = -24.35f - breathOffset, maxY = -11.65f - breathOffset,
                    minZ = -2.35f, maxZ = 2.35f,
                    uvTop = UvRect(44, 32, 3, 4),
                    uvBottom = UvRect(47, 32, 3, 4),
                    uvRight = UvRect(40, 36, 4, 12),
                    uvFront = UvRect(44, 36, 3, 12),
                    uvLeft = UvRect(47, 36, 4, 12),
                    uvBack = UvRect(51, 36, 3, 12),
                    isOverlay = true,
                    pitchOffset = armSwing
                )
            } else {
                buildBoxFaces(
                    quads = quads, skin = skinImage,
                    minX = rArmMinX, maxX = rArmMaxX,
                    minY = -24f - breathOffset, maxY = -12f - breathOffset,
                    minZ = -2f, maxZ = 2f,
                    uvTop = UvRect(44, 16, 4, 4),
                    uvBottom = UvRect(48, 16, 4, 4),
                    uvRight = UvRect(40, 20, 4, 12),
                    uvFront = UvRect(44, 20, 4, 12),
                    uvLeft = UvRect(48, 20, 4, 12),
                    uvBack = UvRect(52, 20, 4, 12),
                    isOverlay = false,
                    pitchOffset = armSwing
                )
                // Steve Right Arm Sleeve
                buildBoxFaces(
                    quads = quads, skin = skinImage,
                    minX = rArmMinX - 0.35f, maxX = rArmMaxX + 0.35f,
                    minY = -24.35f - breathOffset, maxY = -11.65f - breathOffset,
                    minZ = -2.35f, maxZ = 2.35f,
                    uvTop = UvRect(44, 32, 4, 4),
                    uvBottom = UvRect(48, 32, 4, 4),
                    uvRight = UvRect(40, 36, 4, 12),
                    uvFront = UvRect(44, 36, 4, 12),
                    uvLeft = UvRect(48, 36, 4, 12),
                    uvBack = UvRect(52, 36, 4, 12),
                    isOverlay = true,
                    pitchOffset = armSwing
                )
            }

            // ==========================================
            // 4. LEFT ARM (Steve 4x12x4, Alex 3x12x4)
            // ==========================================
            val lArmMinX = 4f
            val lArmMaxX = 4f + armW
            if (modelType == SkinModelType.ALEX) {
                buildBoxFaces(
                    quads = quads, skin = skinImage,
                    minX = lArmMinX, maxX = lArmMaxX,
                    minY = -24f - breathOffset, maxY = -12f - breathOffset,
                    minZ = -2f, maxZ = 2f,
                    uvTop = UvRect(36, 48, 3, 4),
                    uvBottom = UvRect(39, 48, 3, 4),
                    uvRight = UvRect(32, 52, 4, 12),
                    uvFront = UvRect(36, 52, 3, 12),
                    uvLeft = UvRect(39, 52, 4, 12),
                    uvBack = UvRect(43, 52, 3, 12),
                    isOverlay = false,
                    pitchOffset = -armSwing
                )
                // Alex Left Arm Sleeve
                buildBoxFaces(
                    quads = quads, skin = skinImage,
                    minX = lArmMinX - 0.35f, maxX = lArmMaxX + 0.35f,
                    minY = -24.35f - breathOffset, maxY = -11.65f - breathOffset,
                    minZ = -2.35f, maxZ = 2.35f,
                    uvTop = UvRect(52, 48, 3, 4),
                    uvBottom = UvRect(55, 48, 3, 4),
                    uvRight = UvRect(48, 52, 4, 12),
                    uvFront = UvRect(52, 52, 3, 12),
                    uvLeft = UvRect(55, 52, 4, 12),
                    uvBack = UvRect(59, 52, 3, 12),
                    isOverlay = true,
                    pitchOffset = -armSwing
                )
            } else {
                buildBoxFaces(
                    quads = quads, skin = skinImage,
                    minX = lArmMinX, maxX = lArmMaxX,
                    minY = -24f - breathOffset, maxY = -12f - breathOffset,
                    minZ = -2f, maxZ = 2f,
                    uvTop = UvRect(36, 48, 4, 4),
                    uvBottom = UvRect(40, 48, 4, 4),
                    uvRight = UvRect(32, 52, 4, 12),
                    uvFront = UvRect(36, 52, 4, 12),
                    uvLeft = UvRect(40, 52, 4, 12),
                    uvBack = UvRect(44, 52, 4, 12),
                    isOverlay = false,
                    pitchOffset = -armSwing
                )
                // Steve Left Arm Sleeve
                buildBoxFaces(
                    quads = quads, skin = skinImage,
                    minX = lArmMinX - 0.35f, maxX = lArmMaxX + 0.35f,
                    minY = -24.35f - breathOffset, maxY = -11.65f - breathOffset,
                    minZ = -2.35f, maxZ = 2.35f,
                    uvTop = UvRect(52, 48, 4, 4),
                    uvBottom = UvRect(56, 48, 4, 4),
                    uvRight = UvRect(48, 52, 4, 12),
                    uvFront = UvRect(52, 52, 4, 12),
                    uvLeft = UvRect(56, 52, 4, 12),
                    uvBack = UvRect(60, 52, 4, 12),
                    isOverlay = true,
                    pitchOffset = -armSwing
                )
            }

            // ==========================================
            // 5. RIGHT LEG (4x12x4) at [X: -4..0, Y: -12..0, Z: -2..2]
            // ==========================================
            buildBoxFaces(
                quads = quads, skin = skinImage,
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
            buildBoxFaces(
                quads = quads, skin = skinImage,
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
            buildBoxFaces(
                quads = quads, skin = skinImage,
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
            buildBoxFaces(
                quads = quads, skin = skinImage,
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

            // ==========================================
            // 3D CAMERA PROJECTION & DEPTH SORTING
            // ==========================================
            val projectedQuads = quads.mapNotNull { quad ->
                val v0 = projectVertex(quad.v0, -16f, yawRad, pitchRad, centerX, centerY, baseScale)
                val v1 = projectVertex(quad.v1, -16f, yawRad, pitchRad, centerX, centerY, baseScale)
                val v2 = projectVertex(quad.v2, -16f, yawRad, pitchRad, centerX, centerY, baseScale)
                val v3 = projectVertex(quad.v3, -16f, yawRad, pitchRad, centerX, centerY, baseScale)

                // Backface Culling (Signed 2D cross product of winding order)
                val cross = (v1.screenX - v0.screenX) * (v2.screenY - v0.screenY) - (v1.screenY - v0.screenY) * (v2.screenX - v0.screenX)
                if (cross >= 0f) {
                    val avgZ = (v0.z + v1.z + v2.z + v3.z) / 4f
                    ProjectedQuad(
                        p0 = Offset(v0.screenX, v0.screenY),
                        p1 = Offset(v1.screenX, v1.screenY),
                        p2 = Offset(v2.screenX, v2.screenY),
                        p3 = Offset(v3.screenX, v3.screenY),
                        avgZ = avgZ + (if (quad.isOverlay) 0.12f else 0f),
                        color = quad.color
                    )
                } else {
                    null
                }
            }

            // Depth sorting for Painter's Algorithm (Lowest Z / farthest rendered first)
            val sortedQuads = projectedQuads.sortedBy { it.avgZ }

            // Render all nearest-neighbor pixel quads
            val path = Path()
            for (quad in sortedQuads) {
                path.reset()
                path.moveTo(quad.p0.x, quad.p0.y)
                path.lineTo(quad.p1.x, quad.p1.y)
                path.lineTo(quad.p2.x, quad.p2.y)
                path.lineTo(quad.p3.x, quad.p3.y)
                path.close()
                drawPath(path = path, color = quad.color)
            }
        }
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
private data class ProjectedVertex(val screenX: Float, val screenY: Float, val z: Float)
private data class RenderQuad(
    val v0: Vec3,
    val v1: Vec3,
    val v2: Vec3,
    val v3: Vec3,
    val color: Color,
    val isOverlay: Boolean
)
private data class ProjectedQuad(
    val p0: Offset,
    val p1: Offset,
    val p2: Offset,
    val p3: Offset,
    val avgZ: Float,
    val color: Color
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
    scale: Float
): ProjectedVertex {
    // 1. Shift vertex relative to chest center pivot
    val x0 = v.x
    val y0 = v.y - pivotY
    val z0 = v.z

    // 2. Yaw rotation around Y axis
    val cosYaw = cos(yawRad)
    val sinYaw = sin(yawRad)
    val x1 = x0 * cosYaw - z0 * sinYaw
    val z1 = x0 * sinYaw + z0 * cosYaw
    val y1 = y0

    // 3. Pitch rotation around X axis
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

    return ProjectedVertex(screenX, screenY, z2)
}

/**
 * Builds the 6 textured faces of a 3D box with exact pixel-level nearest-neighbor sampling.
 */
private fun buildBoxFaces(
    quads: MutableList<RenderQuad>,
    skin: BufferedImage,
    minX: Float, maxX: Float,
    minY: Float, maxY: Float,
    minZ: Float, maxZ: Float,
    uvTop: UvRect,
    uvBottom: UvRect,
    uvRight: UvRect,
    uvFront: UvRect,
    uvLeft: UvRect,
    uvBack: UvRect,
    isOverlay: Boolean,
    pitchOffset: Float = 0f
) {
    val rad = (pitchOffset * PI / 180.0).toFloat()
    val cosP = cos(rad)
    val sinP = sin(rad)
    fun rot(v: Vec3): Vec3 {
        if (pitchOffset == 0f) return v
        val dy = v.y - minY
        val dz = v.z
        val newY = minY + dy * cosP - dz * sinP
        val newZ = dy * sinP + dz * cosP
        return Vec3(v.x, newY, newZ)
    }

    val stepX = (maxX - minX) / uvFront.w
    val stepY = (maxY - minY) / uvFront.h
    val stepZ = (maxZ - minZ) / uvTop.h

    // 1. TOP FACE (minY) -> Light: 1.08f
    for (ix in 0 until uvTop.w) {
        for (iz in 0 until uvTop.h) {
            val color = getPixelColor(skin, uvTop.x + ix, uvTop.y + iz, 1.08f)
            if (color.alpha > 0.05f) {
                val x0 = minX + ix * stepX
                val x1 = x0 + stepX
                val z0 = minZ + iz * stepZ
                val z1 = z0 + stepZ
                quads.add(
                    RenderQuad(
                        v0 = rot(Vec3(x0, minY, z0)),
                        v1 = rot(Vec3(x1, minY, z0)),
                        v2 = rot(Vec3(x1, minY, z1)),
                        v3 = rot(Vec3(x0, minY, z1)),
                        color = color,
                        isOverlay = isOverlay
                    )
                )
            }
        }
    }

    // 2. BOTTOM FACE (maxY) -> Light: 0.58f
    for (ix in 0 until uvBottom.w) {
        for (iz in 0 until uvBottom.h) {
            val color = getPixelColor(skin, uvBottom.x + ix, uvBottom.y + iz, 0.58f)
            if (color.alpha > 0.05f) {
                val x0 = minX + ix * stepX
                val x1 = x0 + stepX
                val z0 = minZ + iz * stepZ
                val z1 = z0 + stepZ
                quads.add(
                    RenderQuad(
                        v0 = rot(Vec3(x0, maxY, z1)),
                        v1 = rot(Vec3(x1, maxY, z1)),
                        v2 = rot(Vec3(x1, maxY, z0)),
                        v3 = rot(Vec3(x0, maxY, z0)),
                        color = color,
                        isOverlay = isOverlay
                    )
                )
            }
        }
    }

    // 3. FRONT FACE (minZ) -> Light: 1.00f
    for (ix in 0 until uvFront.w) {
        for (iy in 0 until uvFront.h) {
            val color = getPixelColor(skin, uvFront.x + ix, uvFront.y + iy, 1.00f)
            if (color.alpha > 0.05f) {
                val x0 = minX + ix * stepX
                val x1 = x0 + stepX
                val y0 = minY + iy * stepY
                val y1 = y0 + stepY
                quads.add(
                    RenderQuad(
                        v0 = rot(Vec3(x0, y0, minZ)),
                        v1 = rot(Vec3(x1, y0, minZ)),
                        v2 = rot(Vec3(x1, y1, minZ)),
                        v3 = rot(Vec3(x0, y1, minZ)),
                        color = color,
                        isOverlay = isOverlay
                    )
                )
            }
        }
    }

    // 4. BACK FACE (maxZ) -> Light: 0.72f
    for (ix in 0 until uvBack.w) {
        for (iy in 0 until uvBack.h) {
            val color = getPixelColor(skin, uvBack.x + ix, uvBack.y + iy, 0.72f)
            if (color.alpha > 0.05f) {
                val x0 = minX + (uvBack.w - 1 - ix) * stepX
                val x1 = x0 + stepX
                val y0 = minY + iy * stepY
                val y1 = y0 + stepY
                quads.add(
                    RenderQuad(
                        v0 = rot(Vec3(x1, y0, maxZ)),
                        v1 = rot(Vec3(x0, y0, maxZ)),
                        v2 = rot(Vec3(x0, y1, maxZ)),
                        v3 = rot(Vec3(x1, y1, maxZ)),
                        color = color,
                        isOverlay = isOverlay
                    )
                )
            }
        }
    }

    // 5. RIGHT FACE (minX) -> Light: 0.85f
    for (iz in 0 until uvRight.w) {
        for (iy in 0 until uvRight.h) {
            val color = getPixelColor(skin, uvRight.x + iz, uvRight.y + iy, 0.85f)
            if (color.alpha > 0.05f) {
                val z0 = minZ + iz * stepZ
                val z1 = z0 + stepZ
                val y0 = minY + iy * stepY
                val y1 = y0 + stepY
                quads.add(
                    RenderQuad(
                        v0 = rot(Vec3(minX, y0, z1)),
                        v1 = rot(Vec3(minX, y0, z0)),
                        v2 = rot(Vec3(minX, y1, z0)),
                        v3 = rot(Vec3(minX, y1, z1)),
                        color = color,
                        isOverlay = isOverlay
                    )
                )
            }
        }
    }

    // 6. LEFT FACE (maxX) -> Light: 0.85f
    for (iz in 0 until uvLeft.w) {
        for (iy in 0 until uvLeft.h) {
            val color = getPixelColor(skin, uvLeft.x + iz, uvLeft.y + iy, 0.85f)
            if (color.alpha > 0.05f) {
                val z0 = minZ + (uvLeft.w - 1 - iz) * stepZ
                val z1 = z0 + stepZ
                val y0 = minY + iy * stepY
                val y1 = y0 + stepY
                quads.add(
                    RenderQuad(
                        v0 = rot(Vec3(maxX, y0, z0)),
                        v1 = rot(Vec3(maxX, y0, z1)),
                        v2 = rot(Vec3(maxX, y1, z1)),
                        v3 = rot(Vec3(maxX, y1, z0)),
                        color = color,
                        isOverlay = isOverlay
                    )
                )
            }
        }
    }
}

/**
 * Extracts a pixel from the canonical 64x64 skin PNG with exact sRGB color fidelity.
 */
private fun getPixelColor(skin: BufferedImage, u: Int, v: Int, lightIntensity: Float): Color {
    if (u < 0 || u >= skin.width || v < 0 || v >= skin.height) {
        return Color.Transparent
    }
    val argb = skin.getRGB(u, v)
    val a = ((argb ushr 24) and 0xFF) / 255f
    if (a < 0.05f) return Color.Transparent

    val r = (((argb ushr 16) and 0xFF) / 255f * lightIntensity).coerceIn(0f, 1f)
    val g = (((argb ushr 8) and 0xFF) / 255f * lightIntensity).coerceIn(0f, 1f)
    val b = ((argb and 0xFF) / 255f * lightIntensity).coerceIn(0f, 1f)

    return Color(red = r, green = g, blue = b, alpha = a)
}

fun generateDefaultSteveSkin(): BufferedImage {
    val img = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()

    // Base skin tones
    g.color = java.awt.Color(0x9E, 0x6E, 0x48) // Head & skin
    g.fillRect(0, 0, 64, 64)

    // Cyan shirt (torso & sleeves)
    g.color = java.awt.Color(0x00, 0x8C, 0x8C)
    g.fillRect(16, 16, 24, 16) // Torso
    g.fillRect(40, 16, 16, 16) // Right arm
    g.fillRect(32, 48, 16, 16) // Left arm

    // Blue pants (legs)
    g.color = java.awt.Color(0x2B, 0x2D, 0x7E)
    g.fillRect(0, 16, 16, 16)  // Right leg
    g.fillRect(16, 48, 16, 16) // Left leg

    // Face features
    g.color = java.awt.Color(0x4A, 0x32, 0x1E) // Hair & beard
    g.fillRect(8, 8, 8, 2)
    g.fillRect(10, 13, 4, 1)

    // Eyes
    g.color = java.awt.Color.WHITE
    g.fillRect(9, 11, 2, 1)
    g.fillRect(13, 11, 2, 1)
    g.color = java.awt.Color(0x3B, 0x47, 0xA8) // Indigo eyes
    g.fillRect(10, 11, 1, 1)
    g.fillRect(13, 11, 1, 1)

    g.dispose()
    return img
}
