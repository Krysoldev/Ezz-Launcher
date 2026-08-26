package io.ezz.launcher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ThemePreset(val displayName: String) {
    EZZ_BLACK_WHITE("Ezz Black & White (Default)"),
    OBSIDIAN("Obsidian Minimal"),
    FROST_MONO("Frost Monochrome")
}

data class EzzColorScheme(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceLight: Color,
    val cardBackground: Color,
    val elevatedCard: Color,
    val inputBackground: Color,
    val primary: Color,
    val primaryHover: Color,
    val primaryGlow: Color,
    val secondary: Color,
    val accent: Color,
    val accentHover: Color,
    val accentGlow: Color,
    val warning: Color,
    val danger: Color,
    val dangerGlow: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textDisabled: Color,
    val border: Color,
    val borderLight: Color,
    val borderStrong: Color
)

object EzzThemePresets {
    // Official High-Contrast Ezz Black & White Gaming Theme
    val BlackAndWhite = EzzColorScheme(
        isDark = true,
        background = Color(0xFF050505),
        surface = Color(0xFF0A0A0A),
        surfaceVariant = Color(0xFF101010),
        surfaceLight = Color(0xFF141414),
        cardBackground = Color(0xFF151515),
        elevatedCard = Color(0xFF1A1A1A),
        inputBackground = Color(0xFF171717),
        primary = Color(0xFFFFFFFF),       // High contrast Pure White
        primaryHover = Color(0xFFE5E5E5),  // Off-white Hover
        primaryGlow = Color(0x33FFFFFF),
        secondary = Color(0xFFD4D4D4),
        accent = Color(0xFF10B981),        // Micro Status Green (Online)
        accentHover = Color(0xFF34D399),
        accentGlow = Color(0x2210B981),
        warning = Color(0xFFF59E0B),       // Micro Status Amber
        danger = Color(0xFFEF4444),        // Micro Status Red
        dangerGlow = Color(0x22EF4444),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFB8B8B8),
        textMuted = Color(0xFF777777),
        textDisabled = Color(0xFF505050),
        border = Color(0xFF242424),
        borderLight = Color(0xFF2D2D2D),
        borderStrong = Color(0xFF303030)
    )

    val Obsidian = EzzColorScheme(
        isDark = true,
        background = Color(0xFF07080A),
        surface = Color(0xFF0D0F13),
        surfaceVariant = Color(0xFF12151B),
        surfaceLight = Color(0xFF181C24),
        cardBackground = Color(0xFF141820),
        elevatedCard = Color(0xFF1A1F2A),
        inputBackground = Color(0xFF161A22),
        primary = Color(0xFFFFFFFF),
        primaryHover = Color(0xFFE2E8F0),
        primaryGlow = Color(0x22FFFFFF),
        secondary = Color(0xFFCBD5E1),
        accent = Color(0xFF10B981),
        accentHover = Color(0xFF34D399),
        accentGlow = Color(0x2210B981),
        warning = Color(0xFFF59E0B),
        danger = Color(0xFFEF4444),
        dangerGlow = Color(0x22EF4444),
        textPrimary = Color(0xFFF8FAFC),
        textSecondary = Color(0xFF94A3B8),
        textMuted = Color(0xFF64748B),
        textDisabled = Color(0xFF475569),
        border = Color(0xFF262D3D),
        borderLight = Color(0xFF333E54),
        borderStrong = Color(0xFF404D68)
    )

    val FrostMono = EzzColorScheme(
        isDark = false,
        background = Color(0xFFF5F5F5),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFEBEBEB),
        surfaceLight = Color(0xFFE0E0E0),
        cardBackground = Color(0xFFFFFFFF),
        elevatedCard = Color(0xFFF9F9F9),
        inputBackground = Color(0xFFEEEEEE),
        primary = Color(0xFF050505),
        primaryHover = Color(0xFF262626),
        primaryGlow = Color(0x22000000),
        secondary = Color(0xFF404040),
        accent = Color(0xFF059669),
        accentHover = Color(0xFF10B981),
        accentGlow = Color(0x22059669),
        warning = Color(0xFFD97706),
        danger = Color(0xFFDC2626),
        dangerGlow = Color(0x22DC2626),
        textPrimary = Color(0xFF0A0A0A),
        textSecondary = Color(0xFF525252),
        textMuted = Color(0xFF737373),
        textDisabled = Color(0xFFA3A3A3),
        border = Color(0xFFE5E5E5),
        borderLight = Color(0xFFD4D4D4),
        borderStrong = Color(0xFFA3A3A3)
    )
}

class ThemeState {
    var preset by mutableStateOf(ThemePreset.EZZ_BLACK_WHITE)
    var cardCornerRadius by mutableStateOf(8.dp)
    var enableAnimations by mutableStateOf(true)
    var isCompactDensity by mutableStateOf(true)

    val currentColors: EzzColorScheme
        get() {
            return when (preset) {
                ThemePreset.EZZ_BLACK_WHITE -> EzzThemePresets.BlackAndWhite
                ThemePreset.OBSIDIAN -> EzzThemePresets.Obsidian
                ThemePreset.FROST_MONO -> EzzThemePresets.FrostMono
            }
        }
}

val LocalThemeState = staticCompositionLocalOf { ThemeState() }
val LocalEzzColors = staticCompositionLocalOf { EzzThemePresets.BlackAndWhite }

object EzzTheme {
    val colors: EzzColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalEzzColors.current

    val state: ThemeState
        @Composable
        @ReadOnlyComposable
        get() = LocalThemeState.current
}
