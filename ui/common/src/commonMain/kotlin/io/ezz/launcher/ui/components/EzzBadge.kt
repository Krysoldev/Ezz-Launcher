package io.ezz.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.instance.LoaderType
import io.ezz.launcher.ui.theme.EzzTheme

enum class EzzBadgeVariant {
    PRIMARY,
    SUCCESS,
    WARNING,
    DANGER,
    INFO,
    NEUTRAL
}

@Composable
fun EzzBadge(
    text: String,
    modifier: Modifier = Modifier,
    variant: EzzBadgeVariant = EzzBadgeVariant.PRIMARY,
    dotColor: Color? = null
) {
    val colors = EzzTheme.colors

    val (bg, textColor, borderColor) = when (variant) {
        EzzBadgeVariant.PRIMARY -> Triple(colors.primary.copy(alpha = 0.15f), colors.primary, colors.primary.copy(alpha = 0.4f))
        EzzBadgeVariant.SUCCESS -> Triple(colors.accent.copy(alpha = 0.15f), colors.accent, colors.accent.copy(alpha = 0.4f))
        EzzBadgeVariant.WARNING -> Triple(colors.warning.copy(alpha = 0.15f), colors.warning, colors.warning.copy(alpha = 0.4f))
        EzzBadgeVariant.DANGER -> Triple(colors.danger.copy(alpha = 0.15f), colors.danger, colors.danger.copy(alpha = 0.4f))
        EzzBadgeVariant.INFO -> Triple(colors.secondary.copy(alpha = 0.15f), colors.secondary, colors.secondary.copy(alpha = 0.4f))
        EzzBadgeVariant.NEUTRAL -> Triple(colors.surfaceVariant, colors.textSecondary, colors.border)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Text(
                text = text,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun EzzLoaderBadge(
    loaderType: LoaderType,
    modifier: Modifier = Modifier
) {
    val (label, bg, fg, border) = when (loaderType) {
        LoaderType.VANILLA -> Tuple4("VANILLA", Color(0xFF6B7280).copy(alpha = 0.2f), Color(0xFF9CA3AF), Color(0xFF6B7280))
        LoaderType.FABRIC -> Tuple4("FABRIC", Color(0xFF00E5FF).copy(alpha = 0.2f), Color(0xFF00E5FF), Color(0xFF00E5FF).copy(alpha = 0.6f))
        LoaderType.OPTIFINE -> Tuple4("OPTIFINE", Color(0xFFF59E0B).copy(alpha = 0.2f), Color(0xFFFBBF24), Color(0xFFF59E0B).copy(alpha = 0.6f))
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp
        )
    }
}

private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
