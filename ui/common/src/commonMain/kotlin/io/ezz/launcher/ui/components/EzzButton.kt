package io.ezz.launcher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ezz.launcher.ui.theme.EzzTheme

enum class EzzButtonVariant {
    PRIMARY,
    SECONDARY,
    OUTLINE,
    DANGER,
    GHOST
}

enum class EzzButtonSize {
    SMALL,
    MEDIUM,
    LARGE
}

@Composable
fun EzzButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: EzzButtonVariant = EzzButtonVariant.PRIMARY,
    size: EzzButtonSize = EzzButtonSize.MEDIUM,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    fullWidth: Boolean = false
) {
    val colors = EzzTheme.colors
    val state = EzzTheme.state
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (state.enableAnimations && isPressed) 0.96f else if (state.enableAnimations && isHovered) 1.02f else 1.0f
    )

    val (containerColor, contentColor, borderStroke) = when (variant) {
        EzzButtonVariant.PRIMARY -> Triple(
            if (isHovered) colors.primaryHover else colors.primary,
            if (colors.isDark) Color(0xFF0B0F19) else Color.White,
            null
        )
        EzzButtonVariant.SECONDARY -> Triple(
            if (isHovered) colors.surfaceLight else colors.surfaceVariant,
            colors.textPrimary,
            BorderStroke(1.dp, if (isHovered) colors.primary.copy(alpha = 0.5f) else colors.border)
        )
        EzzButtonVariant.OUTLINE -> Triple(
            if (isHovered) colors.primaryGlow else Color.Transparent,
            if (isHovered) colors.primary else colors.textPrimary,
            BorderStroke(1.dp, if (isHovered) colors.primary else colors.border)
        )
        EzzButtonVariant.DANGER -> Triple(
            if (isHovered) colors.danger.copy(alpha = 0.85f) else colors.danger,
            Color.White,
            null
        )
        EzzButtonVariant.GHOST -> Triple(
            if (isHovered) colors.surfaceVariant.copy(alpha = 0.5f) else Color.Transparent,
            if (isHovered) colors.primary else colors.textSecondary,
            null
        )
    }

    val (height, fontSize, iconSize, paddingValues, cornerRadius) = when (size) {
        EzzButtonSize.SMALL -> Tuple5(32.dp, 12.sp, 16.dp, PaddingValues(horizontal = 12.dp, vertical = 6.dp), 8.dp)
        EzzButtonSize.MEDIUM -> Tuple5(42.dp, 14.sp, 18.dp, PaddingValues(horizontal = 18.dp, vertical = 10.dp), 10.dp)
        EzzButtonSize.LARGE -> Tuple5(52.dp, 16.sp, 22.dp, PaddingValues(horizontal = 24.dp, vertical = 14.dp), 12.dp)
    }

    Surface(
        modifier = (if (fullWidth) modifier.fillMaxWidth() else modifier)
            .scale(scale)
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !isLoading,
                onClick = onClick
            ),
        shape = RoundedCornerShape(cornerRadius),
        color = if (enabled) containerColor else containerColor.copy(alpha = 0.4f),
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.4f),
        border = borderStroke
    ) {
        Row(
            modifier = (if (fullWidth) Modifier.fillMaxWidth() else Modifier).padding(paddingValues),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(iconSize),
                    color = contentColor,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = contentColor
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Text(
                text = text,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )

            if (trailingIcon != null && !isLoading) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = contentColor
                )
            }
        }
    }
}

@Composable
fun EzzIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = EzzTheme.colors.textSecondary,
    backgroundColor: Color = Color.Transparent,
    size: Dp = 36.dp,
    iconSize: Dp = 18.dp,
    enabled: Boolean = true
) {
    val colors = EzzTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Surface(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        shape = RoundedCornerShape(8.dp),
        color = if (isHovered) colors.surfaceVariant else backgroundColor,
        border = if (isHovered) BorderStroke(1.dp, colors.border) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = if (isHovered) colors.primary else tint
            )
        }
    }
}

private data class Tuple5<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
