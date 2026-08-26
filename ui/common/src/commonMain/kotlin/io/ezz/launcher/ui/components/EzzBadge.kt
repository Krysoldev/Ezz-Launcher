package io.ezz.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
    variant: EzzBadgeVariant = EzzBadgeVariant.NEUTRAL,
    dotColor: Color? = null
) {
    val (bg, textColor, borderColor, defaultDot) = when (variant) {
        EzzBadgeVariant.PRIMARY -> Tuple4(Color(0xFF1E1E1E), Color(0xFFFFFFFF), Color(0xFF383838), null)
        EzzBadgeVariant.SUCCESS -> Tuple4(Color(0xFF141A16), Color(0xFFE0E0E0), Color(0xFF243428), Color(0xFF10B981))
        EzzBadgeVariant.WARNING -> Tuple4(Color(0xFF1C1914), Color(0xFFE0E0E0), Color(0xFF382E20), Color(0xFFF59E0B))
        EzzBadgeVariant.DANGER -> Tuple4(Color(0xFF1F1414), Color(0xFFE0E0E0), Color(0xFF382020), Color(0xFFEF4444))
        EzzBadgeVariant.INFO -> Tuple4(Color(0xFF161616), Color(0xFFB8B8B8), Color(0xFF2E2E2E), null)
        EzzBadgeVariant.NEUTRAL -> Tuple4(Color(0xFF141414), Color(0xFFA0A0A0), Color(0xFF242424), null)
    }

    val finalDot = dotColor ?: defaultDot

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (finalDot != null) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(finalDot)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }

            Text(
                text = text,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp
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
        LoaderType.VANILLA -> Tuple4("VANILLA", Color(0xFF181818), Color(0xFFD4D4D4), Color(0xFF2D2D2D))
        LoaderType.FABRIC -> Tuple4("FABRIC", Color(0xFF1A1A1A), Color(0xFFFFFFFF), Color(0xFF383838))
        LoaderType.OPTIFINE -> Tuple4("OPTIFINE", Color(0xFF1A1A1A), Color(0xFFFFFFFF), Color(0xFF383838))
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
