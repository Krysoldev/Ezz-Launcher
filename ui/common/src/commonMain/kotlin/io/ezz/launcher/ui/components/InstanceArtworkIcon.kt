package io.ezz.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.ui.image.ImageDecoder
import java.io.File

/**
 * High-quality Minecraft instance visual icon.
 * - If a custom instance icon exists (locally stored in instance directory), renders it sharp with rounded corners.
 * - Otherwise renders a crisp isometric Minecraft 3D block tailored to the modloader (Fabric, OptiFine, Vanilla).
 * - Displays a subtle version tag badge in the bottom-right corner.
 */
@Composable
fun InstanceArtworkIcon(
    instance: Instance,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
    customFile: File? = null,
    showBadge: Boolean = false
) {
    val cornerRadius = when {
        size >= 64.dp -> 12.dp
        size >= 40.dp -> 8.dp
        else -> 6.dp
    }

    // Resolve local custom icon file
    val iconFile = remember(instance.id, instance.customIconPath, customFile) {
        if (customFile != null && customFile.exists() && customFile.length() > 0L) {
            customFile
        } else {
            val path = instance.customIconPath
            val primaryFile = if (!path.isNullOrBlank()) {
                val f = File(path)
                if (f.exists() && f.length() > 0L) f else null
            } else null

            primaryFile ?: run {
                // Fallback: Check standard instance directory locations
                val userHome = System.getProperty("user.home") ?: "."
                val possibleRoots = listOf(
                    File(userHome, ".ezz/instances/${instance.id}"),
                    File(userHome, "AppData/Roaming/.ezz/instances/${instance.id}")
                )

                possibleRoots.flatMap { root ->
                    listOf(
                        File(root, "icon.png"),
                        File(root, "pack.png"),
                        File(root, "icon.webp"),
                        File(root, "icon.jpg"),
                        File(root, ".minecraft/icon.png"),
                        File(root, ".minecraft/pack.png")
                    )
                }.firstOrNull { it.exists() && it.length() > 0L }
            }
        }
    }

    // Decode custom bitmap if present
    val customBitmap = remember(iconFile?.absolutePath, iconFile?.lastModified()) {
        ImageDecoder.decodeFile(iconFile)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1C1C22), Color(0xFF0F0F13))
                )
            )
            .border(1.dp, Color(0xFF2B2B33), RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        if (customBitmap != null) {
            Image(
                bitmap = customBitmap,
                contentDescription = instance.name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius)),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High
            )
        } else {
            // Isometric Minecraft 3D block rendering
            IsometricBlockCanvas(
                loaderType = instance.loaderType,
                modifier = Modifier.size(size * 0.72f)
            )
        }
    }
}

@Composable
private fun IsometricBlockCanvas(
    loaderType: LoaderType,
    modifier: Modifier = Modifier
) {
    val (topColor, leftColor, rightColor) = when (loaderType) {
        LoaderType.FABRIC -> Triple(Color(0xFF5CA346), Color(0xFF866043), Color(0xFF6B4C35)) // Grass Block
        LoaderType.OPTIFINE -> Triple(Color(0xFF4DD0E1), Color(0xFF0097A7), Color(0xFF00838F)) // Diamond Block
        LoaderType.VANILLA -> Triple(Color(0xFF7CB342), Color(0xFF8D6E63), Color(0xFF6D4C41)) // Classic Grass
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = this.size.width
        val h = this.size.height

        val cx = w / 2f
        val cy = h / 2f
        val radius = minOf(w, h) * 0.46f

        val topVertex = Offset(cx, cy - radius)
        val rightTop = Offset(cx + radius * 0.866f, cy - radius * 0.5f)
        val rightBottom = Offset(cx + radius * 0.866f, cy + radius * 0.5f)
        val bottomVertex = Offset(cx, cy + radius)
        val leftBottom = Offset(cx - radius * 0.866f, cy + radius * 0.5f)
        val leftTop = Offset(cx - radius * 0.866f, cy - radius * 0.5f)
        val center = Offset(cx, cy)

        // 1. Top Face (Grass / Diamond Top)
        val topPath = Path().apply {
            moveTo(topVertex.x, topVertex.y)
            lineTo(rightTop.x, rightTop.y)
            lineTo(center.x, center.y)
            lineTo(leftTop.x, leftTop.y)
            close()
        }
        drawPath(topPath, color = topColor)

        // 2. Left Face (Dirt Side)
        val leftPath = Path().apply {
            moveTo(leftTop.x, leftTop.y)
            lineTo(center.x, center.y)
            lineTo(bottomVertex.x, bottomVertex.y)
            lineTo(leftBottom.x, leftBottom.y)
            close()
        }
        drawPath(leftPath, color = leftColor)

        // 3. Right Face (Dirt Side Shaded)
        val rightPath = Path().apply {
            moveTo(center.x, center.y)
            lineTo(rightTop.x, rightTop.y)
            lineTo(rightBottom.x, rightBottom.y)
            lineTo(bottomVertex.x, bottomVertex.y)
            close()
        }
        drawPath(rightPath, color = rightColor)

        // 4. Subtle Grass Overhang on Left Face
        if (loaderType != LoaderType.OPTIFINE) {
            val leftGrassPath = Path().apply {
                moveTo(leftTop.x, leftTop.y)
                lineTo(center.x, center.y)
                lineTo(center.x, center.y + radius * 0.28f)
                lineTo(cx - radius * 0.433f, cy + radius * 0.1f)
                lineTo(leftTop.x, leftTop.y + radius * 0.32f)
                close()
            }
            drawPath(leftGrassPath, color = topColor.copy(alpha = 0.95f))

            // Grass Overhang on Right Face
            val rightGrassPath = Path().apply {
                moveTo(center.x, center.y)
                lineTo(rightTop.x, rightTop.y)
                lineTo(rightTop.x, rightTop.y + radius * 0.28f)
                lineTo(cx + radius * 0.433f, cy + radius * 0.05f)
                lineTo(center.x, center.y + radius * 0.28f)
                close()
            }
            drawPath(rightGrassPath, color = topColor.copy(alpha = 0.85f))
        }

        // 5. Crisp Block Contours
        val outlinePath = Path().apply {
            moveTo(topVertex.x, topVertex.y)
            lineTo(rightTop.x, rightTop.y)
            lineTo(rightBottom.x, rightBottom.y)
            lineTo(bottomVertex.x, bottomVertex.y)
            lineTo(leftBottom.x, leftBottom.y)
            lineTo(leftTop.x, leftTop.y)
            close()
        }
        drawPath(outlinePath, color = Color(0xFF101010), style = Stroke(width = 1.5f))

        // Center Y-Lines
        drawLine(Color(0xFF101010), center, topVertex, strokeWidth = 1.2f)
        drawLine(Color(0xFF101010), center, bottomVertex, strokeWidth = 1.2f)
        drawLine(Color(0xFF101010), center, leftTop, strokeWidth = 1.2f)
        drawLine(Color(0xFF101010), center, rightTop, strokeWidth = 1.2f)
    }
}

/**
 * Bulletproof, single-line instance metadata row.
 * Uses non-breaking spaces (\u00A0) and softWrap=false to completely prevent RAM/version vertical fragmentation.
 */
@Composable
fun InstanceMetadataRow(
    instance: Instance,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    modCount: Int? = null
) {
    val javaReq = io.ezz.launcher.core.minecraft.version.JavaCompatibility.getRequiredJavaMajorVersion(instance.minecraftVersion)
    val ramText = "${instance.maxMemoryMb / 1024}\u00A0GB\u00A0RAM"
    val mcText = "Minecraft\u00A0${instance.minecraftVersion}"
    val loaderText = instance.loaderType.name
    val javaText = "Java\u00A0$javaReq"

    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = mcText,
            color = Color(0xFFA1A1AA),
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false
        )
        Text(text = "•", color = Color(0xFF52525B), fontSize = fontSize, maxLines = 1, softWrap = false)
        Text(
            text = loaderText,
            color = Color.White,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
        )
        Text(text = "•", color = Color(0xFF52525B), fontSize = fontSize, maxLines = 1, softWrap = false)
        Text(
            text = javaText,
            color = Color(0xFFA1A1AA),
            fontSize = fontSize,
            maxLines = 1,
            softWrap = false
        )
        Text(text = "•", color = Color(0xFF52525B), fontSize = fontSize, maxLines = 1, softWrap = false)
        Text(
            text = ramText,
            color = Color(0xFF94A3B8),
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false
        )
        if (modCount != null && modCount > 0) {
            Text(text = "•", color = Color(0xFF52525B), fontSize = fontSize, maxLines = 1, softWrap = false)
            Text(
                text = "$modCount\u00A0${if (modCount == 1) "Mod" else "Mods"}",
                color = Color.White,
                fontSize = fontSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
