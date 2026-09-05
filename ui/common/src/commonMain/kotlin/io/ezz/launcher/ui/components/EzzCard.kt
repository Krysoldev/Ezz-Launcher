package io.ezz.launcher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.ezz.launcher.ui.theme.EzzTheme

enum class EzzCardVariant {
    SURFACE,
    ELEVATED,
    OUTLINED,
    GLASS
}

@Composable
fun EzzCard(
    modifier: Modifier = Modifier,
    variant: EzzCardVariant = EzzCardVariant.SURFACE,
    cornerRadius: Dp = 8.dp,
    onClick: (() -> Unit)? = null,
    borderColor: Color? = null,
    backgroundColor: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val state = EzzTheme.state
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (state.enableAnimations && onClick != null && isHovered) 1.01f else 1.0f,
        animationSpec = tween(durationMillis = 150)
    )

    val offsetY by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (state.enableAnimations && onClick != null && isHovered) (-2).dp else 0.dp,
        animationSpec = tween(durationMillis = 140)
    )

    val baseBg = backgroundColor ?: when (variant) {
        EzzCardVariant.SURFACE -> Color(0xFF10131A)
        EzzCardVariant.ELEVATED -> Color(0xFF141822)
        EzzCardVariant.OUTLINED -> Color(0xFF0A0C12)
        EzzCardVariant.GLASS -> Color(0xFF10131A)
    }

    val baseBorder = borderColor ?: when {
        isHovered && onClick != null -> Color(0xFF333B50)
        variant == EzzCardVariant.OUTLINED -> Color(0xFF242A3B)
        else -> Color(0xFF1B1F2C)
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = {
                io.ezz.launcher.ui.audio.EzzAudioService.playSelect()
                onClick()
            }
        )
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .offset(y = offsetY)
            .scale(scale)
            .clip(RoundedCornerShape(cornerRadius))
            .then(clickableModifier),
        shape = RoundedCornerShape(cornerRadius),
        color = if (isHovered && onClick != null) Color(0xFF151923) else baseBg,
        border = BorderStroke(1.dp, baseBorder)
    ) {
        Box {
            content()
        }
    }
}
