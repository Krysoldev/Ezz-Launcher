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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Size
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
import kotlin.math.sin

/**
 * High-performance, Studio-grade 3D Minecraft Player Model Renderer.
 * - Dual-layer UV texture projection (Head, Torso, Arms, Legs + Hat, Jacket, Sleeves, Pants overlays).
 * - Steve (4px) & Alex (3px) arm geometry.
 * - Studio lighting, radial vignette, and soft ground contact shadow.
 * - 360° interactive camera with Yaw, Pitch, Zoom, and drag-aware auto-rotation.
 * - Subtle harmonic idle breathing & limb resting angles.
 */
@Composable
fun MinecraftPlayerModel3DView(
    skinBytes: ByteArray?,
    modelType: SkinModelType = SkinModelType.STEVE,
    autoRotate: Boolean = true,
    yawState: Float = -25f,
    pitchState: Float = 8f,
    zoomState: Float = 1.0f,
    onYawChange: ((Float) -> Unit)? = null,
    onPitchChange: ((Float) -> Unit)? = null,
    onZoomChange: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var yaw by remember(yawState) { mutableFloatStateOf(yawState) }
    var pitch by remember(pitchState) { mutableFloatStateOf(pitchState) }
    var zoom by remember(zoomState) { mutableFloatStateOf(zoomState) }
    var lastInteractionTime by remember { mutableLongStateOf(0L) }

    // Parse skin texture bitmap
    val skinImage = remember(skinBytes) {
        if (skinBytes != null && skinBytes.isNotEmpty()) {
            try {
                ImageIO.read(ByteArrayInputStream(skinBytes))
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    val fallbackSteve = remember { generateDefaultSteveSkin() }
    val effectiveSkin = skinImage ?: fallbackSteve

    // Idle breathing & limb swing animation
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

    // Drag-aware Auto-rotate tick
    LaunchedEffect(autoRotate) {
        while (autoRotate) {
            withFrameNanos {
                val now = System.currentTimeMillis()
                // Pause rotation for 2.5s after user finishes dragging
                if (now - lastInteractionTime > 2500L) {
                    yaw = (yaw + 0.28f) % 360f
                    onYawChange?.invoke(yaw)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1E1E1E),
                        Color(0xFF111111),
                        Color(0xFF070707)
                    )
                )
            )
            .border(1.dp, Color(0xFF222222), RoundedCornerShape(10.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { lastInteractionTime = System.currentTimeMillis() },
                    onDragEnd = { lastInteractionTime = System.currentTimeMillis() },
                    onDragCancel = { lastInteractionTime = System.currentTimeMillis() }
                ) { change, dragAmount ->
                    change.consume()
                    lastInteractionTime = System.currentTimeMillis()
                    yaw = (yaw - dragAmount.x * 0.55f) % 360f
                    pitch = (pitch + dragAmount.y * 0.45f).coerceIn(-65f, 65f)
                    onYawChange?.invoke(yaw)
                    onPitchChange?.invoke(pitch)
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (delta != 0f) {
                                lastInteractionTime = System.currentTimeMillis()
                                zoom = (zoom - delta * 0.08f).coerceIn(0.6f, 2.0f)
                                onZoomChange?.invoke(zoom)
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

            // Hero scale: Fill ~75% of stage height nicely (character is 32 units tall)
            val baseScale = ((canvasH * 0.76f) / 32f) * zoom
            val centerX = canvasW / 2f
            val centerY = canvasH * 0.86f // Position feet close to base

            val yawRad = (yaw * PI / 180.0).toFloat()
            val pitchRad = (pitch * PI / 180.0).toFloat()

            // 1. Draw subtle soft elliptical ground contact shadow beneath player
            val shadowRadiusX = 14f * baseScale
            val shadowRadiusY = 4.5f * baseScale
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x99000000), Color(0x40000000), Color.Transparent),
                    center = Offset(centerX, centerY - 2f * baseScale),
                    radius = shadowRadiusX
                ),
                topLeft = Offset(centerX - shadowRadiusX, centerY - 2f * baseScale - shadowRadiusY),
                size = Size(shadowRadiusX * 2f, shadowRadiusY * 2f)
            )

            val armW = if (modelType == SkinModelType.ALEX) 3 else 4

            // Build all textured 3D quads for the player model
            val quads = mutableListOf<RenderQuad>()

            // Idle animation angles
            val breathOffset = sin(animTime.toDouble()).toFloat() * 0.25f
            val armSwing = sin(animTime.toDouble()).toFloat() * 2.2f

            // 1. HEAD (8x8x8)
            buildCubeQuads(
                quads = quads,
                skin = effectiveSkin,
                minX = -4f, maxX = 4f,
                minY = -32f - breathOffset, maxY = -24f - breathOffset,
                minZ = -4f, maxZ = 4f,
                uvX = 0, uvY = 0,
                texW = 8, texH = 8, texD = 8,
                isOverlay = false
            )
            // Head Hat Overlay (expanded by 0.5)
            buildCubeQuads(
                quads = quads,
                skin = effectiveSkin,
                minX = -4.5f, maxX = 4.5f,
                minY = -32.5f - breathOffset, maxY = -23.5f - breathOffset,
                minZ = -4.5f, maxZ = 4.5f,
                uvX = 32, uvY = 0,
                texW = 8, texH = 8, texD = 8,
                isOverlay = true
            )

            // 2. TORSO / BODY (8x12x4)
            buildCubeQuads(
                quads = quads,
                skin = effectiveSkin,
                minX = -4f, maxX = 4f,
                minY = -24f - breathOffset, maxY = -12f,
                minZ = -2f, maxZ = 2f,
                uvX = 16, uvY = 16,
                texW = 8, texH = 12, texD = 4,
                isOverlay = false
            )
            // Torso Jacket Overlay (expanded by 0.35)
            buildCubeQuads(
                quads = quads,
                skin = effectiveSkin,
                minX = -4.35f, maxX = 4.35f,
                minY = -24.35f - breathOffset, maxY = -11.65f,
                minZ = -2.35f, maxZ = 2.35f,
                uvX = 16, uvY = 32,
                texW = 8, texH = 12, texD = 4,
                isOverlay = true
            )

            // 3. RIGHT ARM
            val rightArmMinX = -4f - armW
            val rightArmMaxX = -4f
            buildCubeQuads(
                quads = quads,
                skin = effectiveSkin,
                minX = rightArmMinX, maxX = rightArmMaxX,
                minY = -24f - breathOffset, maxY = -12f - breathOffset,
                minZ = -2f, maxZ = 2f,
                uvX = 40, uvY = 16,
                texW = armW, texH = 12, texD = 4,
                isOverlay = false,
                pitchOffset = armSwing
            )
            // Right Arm Sleeve Overlay
            buildCubeQuads(
                quads = quads,
                skin = effectiveSkin,
                minX = rightArmMinX - 0.35f, maxX = rightArmMaxX + 0.35f,
                minY = -24.35f - breathOffset, maxY = -11.65f - breathOffset,
                minZ = -2.35f, maxZ = 2.35f,
                uvX = 40, uvY = 32,
                texW = armW, texH = 12, texD = 4,
                isOverlay = true,
                pitchOffset = armSwing
            )

            // 4. LEFT ARM
            val leftArmMinX = 4f
            val leftArmMaxX = 4f + armW
            buildCubeQuads(
                quads = quads,
                skin = effectiveSkin,
                minX = leftArmMinX, maxX = leftArmMaxX,
                minY = -24f - breathOffset, maxY = -12f - breathOffset,
                minZ = -2f, maxZ = 2f,
                uvX = 32, uvY = 48,
                texW = armW, texH = 12, texD = 4,
                isOverlay = false,
                pitchOffset = -armSwing
            )
            // Left Arm Sleeve Overlay
            buildCubeQuads(
                quads = quads,
                skin = effectiveSkin,
                minX = leftArmMinX - 0.35f, maxX = leftArmMaxX + 0.35f,
                minY = -24.35f - breathOffset, maxY = -11.65f - breathOffset,
                minZ = -2.35f, maxZ = 2.35f,
                uvX = 48, uvY = 48,
                texW = armW, texH = 12, texD = 4,
                isOverlay = true,
                pitchOffset = -armSwing
            )

            // 5. RIGHT LEG (4x12x4)
            buildCubeQuads(
                quads = quads,
                skin = effectiveSkin,
                minX = -4f, maxX = 0f,
                minY = -12f, maxY = 0f,
                minZ = -2f, maxZ = 2f,
                uvX = 0, uvY = 16,
                texW = 4, texH = 12, texD = 4,
                isOverlay = false
            )
            // Right Leg Pants Overlay
            buildCubeQuads(
                quads = quads,
                skin = effectiveSkin,
                minX = -4.35f, maxX = 0.35f,
                minY = -12.35f, maxY = 0.35f,
                minZ = -2.35f, maxZ = 2.35f,
                uvX = 0, uvY = 32,
                texW = 4, texH = 12, texD = 4,
                isOverlay = true
            )

            // 6. LEFT LEG (4x12x4)
            buildCubeQuads(
                quads = quads,
                skin = effectiveSkin,
                minX = 0f, maxX = 4f,
                minY = -12f, maxY = 0f,
                minZ = -2f, maxZ = 2f,
                uvX = 16, uvY = 48,
                texW = 4, texH = 12, texD = 4,
                isOverlay = false
            )
            // Left Leg Pants Overlay
            buildCubeQuads(
                quads = quads,
                skin = effectiveSkin,
                minX = -0.35f, maxX = 4.35f,
                minY = -12.35f, maxY = 0.35f,
                minZ = -2.35f, maxZ = 2.35f,
                uvX = 0, uvY = 48,
                texW = 4, texH = 12, texD = 4,
                isOverlay = true
            )

            // Transform all quads to 3D camera space
            val transformedQuads = quads.mapNotNull { quad ->
                val v0 = project3D(quad.v0, yawRad, pitchRad, centerX, centerY, baseScale)
                val v1 = project3D(quad.v1, yawRad, pitchRad, centerX, centerY, baseScale)
                val v2 = project3D(quad.v2, yawRad, pitchRad, centerX, centerY, baseScale)
                val v3 = project3D(quad.v3, yawRad, pitchRad, centerX, centerY, baseScale)

                // Backface culling: compute 2D signed cross product
                val cross = (v1.screenX - v0.screenX) * (v2.screenY - v0.screenY) - (v1.screenY - v0.screenY) * (v2.screenX - v0.screenX)
                if (cross >= 0f) {
                    // Average depth Z for Painter's Algorithm depth-sorting
                    val avgZ = (v0.z + v1.z + v2.z + v3.z) / 4f
                    ProjectedQuad(
                        p0 = Offset(v0.screenX, v0.screenY),
                        p1 = Offset(v1.screenX, v1.screenY),
                        p2 = Offset(v2.screenX, v2.screenY),
                        p3 = Offset(v3.screenX, v3.screenY),
                        avgZ = avgZ + (if (quad.isOverlay) 0.1f else 0f),
                        color = quad.color
                    )
                } else {
                    null
                }
            }

            // Sort back-to-front (lowest Z drawn first)
            val sortedQuads = transformedQuads.sortedBy { it.avgZ }

            // Render all quads to Compose canvas
            for (quad in sortedQuads) {
                val path = Path().apply {
                    moveTo(quad.p0.x, quad.p0.y)
                    lineTo(quad.p1.x, quad.p1.y)
                    lineTo(quad.p2.x, quad.p2.y)
                    lineTo(quad.p3.x, quad.p3.y)
                    close()
                }
                drawPath(path = path, color = quad.color)
            }
        }
    }
}

/**
 * High-performance 2-Layer Avatar Head Thumbnail for Skin Collection Cards.
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
            // Base layer: UV (8+x, 8+y)
            val baseArgb = skin.getRGB(8 + x, 8 + y)
            val baseA = (baseArgb ushr 24) and 0xFF
            val baseR = (baseArgb ushr 16) and 0xFF
            val baseG = (baseArgb ushr 8) and 0xFF
            val baseB = baseArgb and 0xFF

            // Overlay/Hat layer: UV (40+x, 8+y)
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

private fun project3D(
    v: Vec3,
    yawRad: Float,
    pitchRad: Float,
    centerX: Float,
    centerY: Float,
    scale: Float
): ProjectedVertex {
    // 1. Yaw rotation around Y axis
    val cosYaw = cos(yawRad)
    val sinYaw = sin(yawRad)
    val x1 = v.x * cosYaw - v.z * sinYaw
    val z1 = v.x * sinYaw + v.z * cosYaw
    val y1 = v.y

    // 2. Pitch rotation around X axis
    val cosPitch = cos(pitchRad)
    val sinPitch = sin(pitchRad)
    val y2 = y1 * cosPitch - z1 * sinPitch
    val z2 = y1 * sinPitch + z1 * cosPitch
    val x2 = x1

    // 3. Perspective Camera
    val cameraDistance = 90f
    val fovFactor = cameraDistance / (cameraDistance + z2).coerceAtLeast(10f)

    val screenX = centerX + (x2 * scale * fovFactor)
    val screenY = centerY + (y2 * scale * fovFactor)

    return ProjectedVertex(screenX, screenY, z2)
}

private fun buildCubeQuads(
    quads: MutableList<RenderQuad>,
    skin: BufferedImage,
    minX: Float, maxX: Float,
    minY: Float, maxY: Float,
    minZ: Float, maxZ: Float,
    uvX: Int, uvY: Int,
    texW: Int, texH: Int, texD: Int,
    isOverlay: Boolean,
    pitchOffset: Float = 0f
) {
    val stepX = (maxX - minX) / texW
    val stepY = (maxY - minY) / texH
    val stepZ = (maxZ - minZ) / texD

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

    // TOP (+Y is downwards, so Top is at minY) -> Light: 1.15f
    val topUvX = uvX + texD
    val topUvY = uvY
    for (ix in 0 until texW) {
        for (iz in 0 until texD) {
            val color = getPixelColor(skin, topUvX + ix, topUvY + iz, 1.15f)
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

    // BOTTOM (maxY) -> Light: 0.55f
    val botUvX = uvX + texD + texW
    val botUvY = uvY
    for (ix in 0 until texW) {
        for (iz in 0 until texD) {
            val color = getPixelColor(skin, botUvX + ix, botUvY + iz, 0.55f)
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

    // FRONT (minZ) -> Light: 1.0f
    val frontUvX = uvX + texD
    val frontUvY = uvY + texD
    for (ix in 0 until texW) {
        for (iy in 0 until texH) {
            val color = getPixelColor(skin, frontUvX + ix, frontUvY + iy, 1.0f)
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

    // BACK (maxZ) -> Light: 0.65f
    val backUvX = uvX + texD + texW + texD
    val backUvY = uvY + texD
    for (ix in 0 until texW) {
        for (iy in 0 until texH) {
            val color = getPixelColor(skin, backUvX + ix, backUvY + iy, 0.65f)
            if (color.alpha > 0.05f) {
                val x0 = minX + (texW - 1 - ix) * stepX
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

    // RIGHT (minX) -> Light: 0.75f
    val rightUvX = uvX
    val rightUvY = uvY + texD
    for (iz in 0 until texD) {
        for (iy in 0 until texH) {
            val color = getPixelColor(skin, rightUvX + iz, rightUvY + iy, 0.75f)
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

    // LEFT (maxX) -> Light: 0.88f
    val leftUvX = uvX + texD + texW
    val leftUvY = uvY + texD
    for (iz in 0 until texD) {
        for (iy in 0 until texH) {
            val color = getPixelColor(skin, leftUvX + iz, leftUvY + iy, 0.88f)
            if (color.alpha > 0.05f) {
                val z0 = minZ + (texD - 1 - iz) * stepZ
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
