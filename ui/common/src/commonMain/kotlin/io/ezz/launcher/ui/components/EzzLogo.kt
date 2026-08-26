package io.ezz.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.ezz.launcher.ui.theme.EzzTheme

@Composable
fun EzzLogo(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    showGlow: Boolean = true,
    shapeRadius: Dp = 10.dp
) {
    val colors = EzzTheme.colors

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (showGlow) {
                    Modifier.shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(shapeRadius),
                        ambientColor = colors.primary.copy(alpha = 0.4f),
                        spotColor = colors.primary.copy(alpha = 0.5f)
                    )
                } else Modifier
            )
            .clip(RoundedCornerShape(shapeRadius))
            .background(colors.surfaceVariant)
            .border(1.5.dp, colors.primary.copy(alpha = 0.6f), RoundedCornerShape(shapeRadius)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource("logo.png"),
            contentDescription = "Ezz Launcher HD Mascot",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size)
        )
    }
}
