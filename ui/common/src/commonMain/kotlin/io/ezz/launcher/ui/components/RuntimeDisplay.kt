package io.ezz.launcher.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.core.model.runtime.formatRuntime
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Isolated Real-Time Game Runtime Component.
 * - Runs its own high-frequency (50ms) update loop without triggering parent screen recomposition.
 * - Computes real elapsed time: (currentTime - startedAt) / 1000L.
 * - Formats as HH:MM:SS with fixed-width tabular numerals to prevent layout shifting.
 * - Features a smooth pulsing live status indicator dot.
 */
@Composable
fun RuntimeDisplay(
    startedAt: Long,
    modifier: Modifier = Modifier,
    showPrefix: Boolean = false,
    prefixText: String = "RUNTIME",
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    textColor: Color = Color.White,
    dotSize: Dp = 7.dp,
    showDot: Boolean = true,
    dotColor: Color = Color(0xFF10B981)
) {
    var currentTime by remember(startedAt) { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(startedAt) {
        while (isActive) {
            currentTime = System.currentTimeMillis()
            delay(50L) // 50ms smooth evaluation (20 FPS)
        }
    }

    val elapsedSeconds = ((currentTime - startedAt) / 1000L).coerceAtLeast(0L)
    val formattedTime = remember(elapsedSeconds) { formatRuntime(elapsedSeconds) }

    val infiniteTransition = rememberInfiniteTransition()
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (showDot) {
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = dotAlpha))
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        if (showPrefix) {
            Text(
                text = prefixText,
                color = dotColor,
                fontSize = fontSize,
                fontWeight = fontWeight,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        // Animated tabular number display with zero layout shift
        AnimatedContent(
            targetState = formattedTime,
            transitionSpec = {
                fadeIn(animationSpec = tween(100)) togetherWith fadeOut(animationSpec = tween(100))
            }
        ) { targetText ->
            Text(
                text = targetText,
                color = textColor,
                fontSize = fontSize,
                fontWeight = fontWeight,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

/**
 * Compact Runtime Badge for Instance Cards in Grid and List views.
 */
@Composable
fun CompactRuntimeBadge(
    startedAt: Long,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF141414))
            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        RuntimeDisplay(
            startedAt = startedAt,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textColor = Color.White,
            dotSize = 6.dp
        )
    }
}

/**
 * Hero Large Runtime Action Button Display.
 */
@Composable
fun HeroRuntimeActionDisplay(
    startedAt: Long,
    modifier: Modifier = Modifier
) {
    RuntimeDisplay(
        startedAt = startedAt,
        modifier = modifier,
        fontSize = 16.sp,
        fontWeight = FontWeight.Black,
        textColor = Color.White,
        dotSize = 8.dp
    )
}
