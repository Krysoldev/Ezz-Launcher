package io.ezz.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.instance.Instance
import io.ezz.launcher.core.model.instance.LoaderType

/**
 * High-quality Minecraft instance visual icon.
 * Renders a crisp isometric Minecraft block.
 */
@Composable
fun InstanceArtworkIcon(
    instance: Instance,
    size: Dp = 64.dp,
    modifier: Modifier = Modifier
) {
    val cornerRadius = (size.value * 0.18f).dp

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF222222), Color(0xFF141414))
                )
            )
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        // Isometric Minecraft 3D block rendering
        IsometricBlockCanvas(
            loaderType = instance.loaderType,
            modifier = Modifier.size(size * 0.68f)
        )

        // Version badge overlay in bottom-right
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .clip(RoundedCornerShape(topStart = 4.dp))
                .background(Color(0xFF0A0A0A).copy(alpha = 0.9f))
                .border(0.5.dp, Color(0xFF2A2A2A), RoundedCornerShape(topStart = 4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            val shortVer = instance.minecraftVersion
            Text(
                text = shortVer,
                color = Color(0xFFCCCCCC),
                fontSize = if (size >= 64.dp) 8.sp else 7.sp,
                fontWeight = FontWeight.Bold
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

        // Top Face
        val topPath = Path().apply {
            moveTo(topVertex.x, topVertex.y)
            lineTo(rightTop.x, rightTop.y)
            lineTo(center.x, center.y)
            lineTo(leftTop.x, leftTop.y)
            close()
        }
        drawPath(topPath, color = topColor)

        // Left Face
        val leftPath = Path().apply {
            moveTo(leftTop.x, leftTop.y)
            lineTo(center.x, center.y)
            lineTo(bottomVertex.x, bottomVertex.y)
            lineTo(leftBottom.x, leftBottom.y)
            close()
        }
        drawPath(leftPath, color = leftColor)

        // Right Face
        val rightPath = Path().apply {
            moveTo(center.x, center.y)
            lineTo(rightTop.x, rightTop.y)
            lineTo(rightBottom.x, rightBottom.y)
            lineTo(bottomVertex.x, bottomVertex.y)
            close()
        }
        drawPath(rightPath, color = rightColor)

        // Subtle outline
        val outlinePath = Path().apply {
            moveTo(topVertex.x, topVertex.y)
            lineTo(rightTop.x, rightTop.y)
            lineTo(rightBottom.x, rightBottom.y)
            lineTo(bottomVertex.x, bottomVertex.y)
            lineTo(leftBottom.x, leftBottom.y)
            lineTo(leftTop.x, leftTop.y)
            close()
        }
        drawPath(outlinePath, color = Color(0xFF000000).copy(alpha = 0.35f), style = Stroke(width = 1.5f))
    }
}
