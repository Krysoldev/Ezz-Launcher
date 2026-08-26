package io.ezz.launcher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object EzzColors {
    val Background = Color(0xFF0B0F19)
    val Surface = Color(0xFF131B2E)
    val SurfaceVariant = Color(0xFF1E293B)
    val SurfaceLight = Color(0xFF283548)
    
    val Primary = Color(0xFF00E5FF)          // Electric Cyan
    val PrimaryHover = Color(0xFF38EFFF)
    val PrimaryGlow = Color(0x3300E5FF)
    
    val Secondary = Color(0xFF6366F1)        // Indigo
    val Accent = Color(0xFF10B981)           // Emerald Green (Success / Ready)
    val AccentGlow = Color(0x3310B981)
    
    val Warning = Color(0xFFF59E0B)          // Amber
    val Danger = Color(0xFFEF4444)           // Crimson
    val DangerGlow = Color(0x33EF4444)
    
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)
    
    val Border = Color(0xFF334155)
    val BorderLight = Color(0xFF475569)
}

private val DarkColorScheme = darkColorScheme(
    primary = EzzColors.Primary,
    onPrimary = Color(0xFF0B0F19),
    primaryContainer = EzzColors.SurfaceVariant,
    onPrimaryContainer = EzzColors.TextPrimary,
    secondary = EzzColors.Secondary,
    onSecondary = Color.White,
    background = EzzColors.Background,
    onBackground = EzzColors.TextPrimary,
    surface = EzzColors.Surface,
    onSurface = EzzColors.TextPrimary,
    surfaceVariant = EzzColors.SurfaceVariant,
    onSurfaceVariant = EzzColors.TextSecondary,
    error = EzzColors.Danger,
    onError = Color.White
)

@Composable
fun EzzTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
