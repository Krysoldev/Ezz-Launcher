package io.ezz.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

@Composable
fun EzzTheme(
    themeState: ThemeState = remember { ThemeState() },
    content: @Composable () -> Unit
) {
    val colors = themeState.currentColors

    val materialColorScheme = if (colors.isDark) {
        darkColorScheme(
            primary = colors.primary,
            onPrimary = Color(0xFF0B0F19),
            primaryContainer = colors.surfaceVariant,
            onPrimaryContainer = colors.textPrimary,
            secondary = colors.secondary,
            onSecondary = Color.White,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariant = colors.textSecondary,
            error = colors.danger,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = Color.White,
            primaryContainer = colors.surfaceVariant,
            onPrimaryContainer = colors.textPrimary,
            secondary = colors.secondary,
            onSecondary = Color.White,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariant = colors.textSecondary,
            error = colors.danger,
            onError = Color.White
        )
    }

    CompositionLocalProvider(
        LocalThemeState provides themeState,
        LocalEzzColors provides colors
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            content = content
        )
    }
}
