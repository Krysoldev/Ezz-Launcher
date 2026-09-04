package io.ezz.launcher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
    SUCCESS,
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
    val state = EzzTheme.state
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (state.enableAnimations && isPressed) 0.98f else if (state.enableAnimations && isHovered) 1.01f else 1.0f,
        animationSpec = tween(durationMillis = 120)
    )

    val (containerColor, contentColor, borderStroke) = when (variant) {
        EzzButtonVariant.PRIMARY -> Triple(
            if (isHovered) Color(0xFF7C3AED) else Color(0xFF8B5CF6),
            Color.White,
            BorderStroke(1.dp, if (isHovered) Color(0xFFA78BFA) else Color(0xFF8B5CF6))
        )
        EzzButtonVariant.SECONDARY -> Triple(
            if (isHovered) Color(0xFF181D2A) else Color(0xFF12151E),
            if (isHovered) Color.White else Color(0xFFE2E8F0),
            BorderStroke(1.dp, if (isHovered) Color(0xFF2F374E) else Color(0xFF1E2332))
        )
        EzzButtonVariant.OUTLINE -> Triple(
            if (isHovered) Color(0xFF141722) else Color.Transparent,
            if (isHovered) Color.White else Color(0xFFCBD5E1),
            BorderStroke(1.dp, if (isHovered) Color(0xFF2F374E) else Color(0xFF1E2332))
        )
        EzzButtonVariant.DANGER -> Triple(
            if (isHovered) Color(0xFF381419) else Color(0xFF261215),
            if (isHovered) Color.White else Color(0xFFFCA5A5),
            BorderStroke(1.dp, if (isHovered) Color(0xFFEF4444) else Color(0xFF5A1E24))
        )
        EzzButtonVariant.SUCCESS -> Triple(
            if (isHovered) Color(0xFF143324) else Color(0xFF0E2218),
            if (isHovered) Color.White else Color(0xFF6EE7B7),
            BorderStroke(1.dp, if (isHovered) Color(0xFF10B981) else Color(0xFF1B4D36))
        )
        EzzButtonVariant.GHOST -> Triple(
            if (isHovered) Color(0xFF141722) else Color.Transparent,
            if (isHovered) Color.White else Color(0xFF94A3B8),
            if (isHovered) BorderStroke(1.dp, Color(0xFF1E2332)) else null
        )
    }

    val (height, fontSize, iconSize, paddingValues, cornerRadius) = when (size) {
        EzzButtonSize.SMALL -> Tuple5(32.dp, 12.sp, 15.dp, PaddingValues(horizontal = 12.dp, vertical = 6.dp), 6.dp)
        EzzButtonSize.MEDIUM -> Tuple5(40.dp, 13.sp, 17.dp, PaddingValues(horizontal = 16.dp, vertical = 8.dp), 8.dp)
        EzzButtonSize.LARGE -> Tuple5(48.dp, 15.sp, 20.dp, PaddingValues(horizontal = 22.dp, vertical = 12.dp), 8.dp)
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
        color = if (enabled) containerColor else containerColor.copy(alpha = 0.35f),
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.35f),
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
                fontWeight = FontWeight.Bold,
                color = contentColor,
                letterSpacing = 0.3.sp
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
    variant: EzzButtonVariant = EzzButtonVariant.SECONDARY,
    size: EzzButtonSize = EzzButtonSize.MEDIUM,
    enabled: Boolean = true
) {
    val (dimension, iconDim) = when (size) {
        EzzButtonSize.SMALL -> Pair(30.dp, 15.dp)
        EzzButtonSize.MEDIUM -> Pair(36.dp, 18.dp)
        EzzButtonSize.LARGE -> Pair(42.dp, 20.dp)
    }

    val (bg, fg) = when (variant) {
        EzzButtonVariant.PRIMARY -> Pair(Color(0xFF8B5CF6), Color.White)
        EzzButtonVariant.SECONDARY -> Pair(Color(0xFF12151E), Color(0xFFE2E8F0))
        EzzButtonVariant.OUTLINE -> Pair(Color.Transparent, Color(0xFFCBD5E1))
        EzzButtonVariant.DANGER -> Pair(Color(0xFF261215), Color(0xFFFCA5A5))
        EzzButtonVariant.SUCCESS -> Pair(Color(0xFF0E2218), Color(0xFF6EE7B7))
        EzzButtonVariant.GHOST -> Pair(Color.Transparent, Color(0xFF94A3B8))
    }

    EzzIconButton(
        icon = icon,
        onClick = onClick,
        modifier = modifier,
        contentDescription = contentDescription,
        tint = fg,
        backgroundColor = bg,
        size = dimension,
        iconSize = iconDim,
        enabled = enabled
    )
}

@Composable
fun EzzIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = Color(0xFF94A3B8),
    backgroundColor: Color = Color.Transparent,
    size: Dp = 34.dp,
    iconSize: Dp = 16.dp,
    enabled: Boolean = true
) {
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
        color = if (isHovered) Color(0xFF181C28) else backgroundColor,
        border = BorderStroke(1.dp, if (isHovered) Color(0xFF323A4E) else Color(0xFF222735))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = if (isHovered) Color.White else tint
            )
        }
    }
}

private data class Tuple5<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
