package io.ezz.launcher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Clean 4-square official Microsoft brand logo.
 */
@Composable
fun MicrosoftLogo(modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val squareSize = (size - 3.dp) / 2
    Column(
        modifier = modifier.size(size),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.size(squareSize).background(Color(0xFFF25022))) // Red
            Box(Modifier.size(squareSize).background(Color(0xFF7FBA00))) // Green
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.size(squareSize).background(Color(0xFF00A4EF))) // Blue
            Box(Modifier.size(squareSize).background(Color(0xFFFFB900))) // Yellow
        }
    }
}

enum class MicrosoftButtonState {
    NORMAL,
    LOADING,
    DISABLED,
    SUCCESS
}

/**
 * Premium Microsoft Sign-In Button adhering to Ezz Launcher's design language.
 */
@Composable
fun MicrosoftSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: MicrosoftButtonState = MicrosoftButtonState.NORMAL,
    size: EzzButtonSize = EzzButtonSize.MEDIUM
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val isClickable = state == MicrosoftButtonState.NORMAL
    val scale by animateFloatAsState(
        targetValue = if (isClickable && isPressed) 0.98f else if (isClickable && isHovered) 1.01f else 1.0f,
        animationSpec = tween(durationMillis = 120)
    )

    val (height, fontSize, hPadding, logoSize) = when (size) {
        EzzButtonSize.SMALL -> Quad(32.dp, 12.sp, 12.dp, 14.dp)
        EzzButtonSize.MEDIUM -> Quad(38.dp, 13.sp, 16.dp, 16.dp)
        EzzButtonSize.LARGE -> Quad(44.dp, 14.sp, 20.dp, 18.dp)
    }

    val backgroundColor = when (state) {
        MicrosoftButtonState.NORMAL -> if (isHovered) Color(0xFFF1F5F9) else Color(0xFFFFFFFF)
        MicrosoftButtonState.LOADING -> Color(0xFFE2E8F0)
        MicrosoftButtonState.DISABLED -> Color(0xFF334155)
        MicrosoftButtonState.SUCCESS -> Color(0xFF10B981)
    }

    val textColor = when (state) {
        MicrosoftButtonState.NORMAL -> Color(0xFF07080A)
        MicrosoftButtonState.LOADING -> Color(0xFF1E293B)
        MicrosoftButtonState.DISABLED -> Color(0xFF64748B)
        MicrosoftButtonState.SUCCESS -> Color.White
    }

    val buttonText = when (state) {
        MicrosoftButtonState.NORMAL -> "Sign in with Microsoft"
        MicrosoftButtonState.LOADING -> "Signing in..."
        MicrosoftButtonState.DISABLED -> "Signing in..."
        MicrosoftButtonState.SUCCESS -> "Signed in"
    }

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(
                BorderStroke(
                    1.dp,
                    if (state == MicrosoftButtonState.NORMAL && isHovered) Color.White else Color.White.copy(alpha = 0.9f)
                ),
                RoundedCornerShape(8.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = isClickable,
                onClick = onClick
            )
            .padding(horizontal = hPadding, vertical = (height - 20.dp) / 2),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            when (state) {
                MicrosoftButtonState.NORMAL, MicrosoftButtonState.DISABLED -> {
                    MicrosoftLogo(size = logoSize)
                    Spacer(modifier = Modifier.width(10.dp))
                }
                MicrosoftButtonState.LOADING -> {
                    CircularProgressIndicator(
                        color = textColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(logoSize)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                MicrosoftButtonState.SUCCESS -> {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(logoSize)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }

            Text(
                text = buttonText,
                color = textColor,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
