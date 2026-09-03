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
    // Official High-Contrast Ezz Desktop Theme (Pure Black, Charcoal & Crisp White)
    val BlackAndWhite = EzzColorScheme(
        isDark = true,
        background = Color(0xFF07080A),
        surface = Color(0xFF0C0E12),
        surfaceVariant = Color(0xFF101318),
        surfaceLight = Color(0xFF141720),
        cardBackground = Color(0xFF101318),
        elevatedCard = Color(0xFF141720),
        inputBackground = Color(0xFF141720),
        primary = Color(0xFFFFFFFF),       // Crisp High-Contrast White CTA
        primaryHover = Color(0xFFE2E8F0),  // Off-white Hover
        primaryGlow = Color(0x1AFFFFFF),
        secondary = Color(0xFF94A3B8),
        accent = Color(0xFF10B981),        // Minimal Emerald Status Green
        accentHover = Color(0xFF34D399),
        accentGlow = Color(0x2210B981),
        warning = Color(0xFFF59E0B),       // Subtle Amber Warning
        danger = Color(0xFFEF4444),        // Subtle Red Danger
        dangerGlow = Color(0x22EF4444),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFF94A3B8),
        textMuted = Color(0xFF64748B),
        textDisabled = Color(0xFF475569),
        border = Color(0xFF1A1D26),
        borderLight = Color(0xFF222735),
        borderStrong = Color(0xFF323A4E)
    )

    val Obsidian = EzzColorScheme(
        isDark = true,
        background = Color(0xFF07080A),
        surface = Color(0xFF0C0E12),
        surfaceVariant = Color(0xFF101318),
        surfaceLight = Color(0xFF141720),
        cardBackground = Color(0xFF101318),
        elevatedCard = Color(0xFF141720),
        inputBackground = Color(0xFF141720),
        primary = Color(0xFFFFFFFF),
        primaryHover = Color(0xFFE2E8F0),
        primaryGlow = Color(0x1AFFFFFF),
        secondary = Color(0xFF94A3B8),
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
        border = Color(0xFF1A1D26),
        borderLight = Color(0xFF222735),
        borderStrong = Color(0xFF323A4E)
    )

    val FrostMono = EzzColorScheme(
        isDark = false,
        background = Color(0xFFF1F5F9),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFE2E8F0),
        surfaceLight = Color(0xFFCBD5E1),
        cardBackground = Color(0xFFFFFFFF),
        elevatedCard = Color(0xFFF8FAFC),
        inputBackground = Color(0xFFF1F5F9),
        primary = Color(0xFF6D28D9),
        primaryHover = Color(0xFF7C3AED),
        primaryGlow = Color(0x226D28D9),
        secondary = Color(0xFF475569),
        accent = Color(0xFF059669),
        accentHover = Color(0xFF10B981),
        accentGlow = Color(0x22059669),
        warning = Color(0xFFD97706),
        danger = Color(0xFFDC2626),
        dangerGlow = Color(0x22DC2626),
        textPrimary = Color(0xFF0F172A),
        textSecondary = Color(0xFF64748B),
        textMuted = Color(0xFF94A3B8),
        textDisabled = Color(0xFFCBD5E1),
        border = Color(0xFFE2E8F0),
        borderLight = Color(0xFFCBD5E1),
        borderStrong = Color(0xFF94A3B8)
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
