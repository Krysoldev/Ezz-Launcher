package io.ezz.launcher.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.ezz.launcher.ui.image.ModrinthImageLoader
import io.ezz.launcher.ui.theme.EzzTheme

/**
 * Reusable asynchronous image loader for Modrinth project icons and preview covers.
 * - Checks persistent local disk cache and memory cache.
 * - Shows a sleek dark shimmer skeleton during initial load.
 * - Displays a high-contrast fallback icon if the image fails or is unavailable.
 */
@Composable
fun ModrinthAsyncImage(
    url: String?,
    imageLoader: ModrinthImageLoader,
    modifier: Modifier = Modifier,
    placeholderIcon: ImageVector = Icons.Default.Extension,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    var bitmap by remember(url) { mutableStateOf(imageLoader.getImageBitmap(url)) }
    var isLoading by remember(url) { mutableStateOf(bitmap == null && !url.isNullOrBlank()) }

    LaunchedEffect(url) {
        if (!url.isNullOrBlank() && bitmap == null) {
            isLoading = true
            val loaded = imageLoader.loadBitmap(url)
            bitmap = loaded
            isLoading = false
        } else {
            isLoading = false
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF141414))
            .border(1.dp, Color(0xFF242424), shape),
        contentAlignment = Alignment.Center
    ) {
        val currentBitmap = bitmap

        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                filterQuality = FilterQuality.Medium
            )
        } else if (isLoading) {
            val transition = rememberInfiniteTransition(label = "ImageShimmer")
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 0.7f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "ShimmerAlpha"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF222222).copy(alpha = alpha))
            )
        } else {
            // Fallback icon
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = placeholderIcon,
                    contentDescription = contentDescription,
                    tint = Color(0xFF666666),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
