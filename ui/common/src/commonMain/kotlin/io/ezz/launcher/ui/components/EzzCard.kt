package io.ezz.launcher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
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
    cornerRadius: Dp = EzzTheme.state.cardCornerRadius,
    onClick: (() -> Unit)? = null,
    borderColor: Color? = null,
    backgroundColor: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = EzzTheme.colors
    val state = EzzTheme.state
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (state.enableAnimations && onClick != null && isHovered) 1.01f else 1.0f
    )

    val baseBg = backgroundColor ?: when (variant) {
        EzzCardVariant.SURFACE -> colors.cardBackground
        EzzCardVariant.ELEVATED -> colors.surfaceVariant
        EzzCardVariant.OUTLINED -> colors.surface
        EzzCardVariant.GLASS -> colors.surface.copy(alpha = 0.6f)
    }

    val baseBorder = borderColor ?: when {
        isHovered && onClick != null -> colors.primary.copy(alpha = 0.6f)
        variant == EzzCardVariant.OUTLINED -> colors.border
        else -> colors.border.copy(alpha = 0.6f)
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(cornerRadius))
            .then(clickableModifier),
        shape = RoundedCornerShape(cornerRadius),
        color = if (isHovered && onClick != null) baseBg.copy(alpha = 0.9f) else baseBg,
        border = BorderStroke(1.dp, baseBorder)
    ) {
        Box {
            content()
        }
    }
}
