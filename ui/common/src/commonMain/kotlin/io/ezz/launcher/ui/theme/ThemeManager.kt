package io.ezz.launcher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ThemePreset(val displayName: String) {
    EZZ_DARK("Ezz Dark Red (Default)"),
    OBSIDIAN("Obsidian Slate"),
    MIDNIGHT_NEON("Midnight Neon"),
    AMETHYST_GLOW("Amethyst Glow"),
    CYBER_GOLD("Cyber Gold"),
    FROST_LIGHT("Frost Light")
}

data class EzzColorScheme(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceLight: Color,
    val cardBackground: Color,
    val inputBackground: Color,
    val primary: Color,
    val primaryHover: Color,
    val primaryGlow: Color,
    val secondary: Color,
    val accent: Color,
    val accentGlow: Color,
    val warning: Color,
    val danger: Color,
    val dangerGlow: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val border: Color,
    val borderLight: Color
)

object EzzThemePresets {
    // Primary Ezz Signature Dark Gaming Theme
    val EzzDark = EzzColorScheme(
        isDark = true,
        background = Color(0xFF070809),
        surface = Color(0xFF0D0F10),
        surfaceVariant = Color(0xFF121416),
        surfaceLight = Color(0xFF181B1E),
        cardBackground = Color(0xFF151719),
        inputBackground = Color(0xFF191B1D),
        primary = Color(0xFFEF4444),       // Ezz Signature Red
        primaryHover = Color(0xFFDC2626),  // Deep Red Hover
        primaryGlow = Color(0x33EF4444),
        secondary = Color(0xFFE11D48),
        accent = Color(0xFF10B981),        // Success / Online Green
        accentGlow = Color(0x3310B981),
        warning = Color(0xFFF59E0B),
        danger = Color(0xFFEF4444),
        dangerGlow = Color(0x33EF4444),
        textPrimary = Color(0xFFF5F5F5),
        textSecondary = Color(0xFFA0A3A6),
        textMuted = Color(0xFF6E7276),
        border = Color(0xFF24272A),
        borderLight = Color(0xFF32363A)
    )

    val Obsidian = EzzColorScheme(
        isDark = true,
        background = Color(0xFF0B0F19),
        surface = Color(0xFF131B2E),
        surfaceVariant = Color(0xFF1E293B),
        surfaceLight = Color(0xFF283548),
        cardBackground = Color(0xFF162032),
        inputBackground = Color(0xFF1E293B),
        primary = Color(0xFF00E5FF),
        primaryHover = Color(0xFF38EFFF),
        primaryGlow = Color(0x3300E5FF),
        secondary = Color(0xFF6366F1),
        accent = Color(0xFF10B981),
        accentGlow = Color(0x3310B981),
        warning = Color(0xFFF59E0B),
        danger = Color(0xFFEF4444),
        dangerGlow = Color(0x33EF4444),
        textPrimary = Color(0xFFF8FAFC),
        textSecondary = Color(0xFF94A3B8),
        textMuted = Color(0xFF64748B),
        border = Color(0xFF334155),
        borderLight = Color(0xFF475569)
    )

    val MidnightNeon = EzzColorScheme(
        isDark = true,
        background = Color(0xFF05070B),
        surface = Color(0xFF0D1117),
        surfaceVariant = Color(0xFF161B22),
        surfaceLight = Color(0xFF21262D),
        cardBackground = Color(0xFF0E141E),
        inputBackground = Color(0xFF161B22),
        primary = Color(0xFF10B981),
        primaryHover = Color(0xFF34D399),
        primaryGlow = Color(0x3310B981),
        secondary = Color(0xFF06B6D4),
        accent = Color(0xFF00E5FF),
        accentGlow = Color(0x3300E5FF),
        warning = Color(0xFFFBBF24),
        danger = Color(0xFFF43F5E),
        dangerGlow = Color(0x33F43F5E),
        textPrimary = Color(0xFFF0F6FC),
        textSecondary = Color(0xFF8B949E),
        textMuted = Color(0xFF6E7681),
        border = Color(0xFF30363D),
        borderLight = Color(0xFF484F58)
    )

    val AmethystGlow = EzzColorScheme(
        isDark = true,
        background = Color(0xFF0E0B1A),
        surface = Color(0xFF18132B),
        surfaceVariant = Color(0xFF241C3E),
        surfaceLight = Color(0xFF322854),
        cardBackground = Color(0xFF1E1736),
        inputBackground = Color(0xFF241C3E),
        primary = Color(0xFFA855F7),
        primaryHover = Color(0xFFC084FC),
        primaryGlow = Color(0x33A855F7),
        secondary = Color(0xFFEC4899),
        accent = Color(0xFF8B5CF6),
        accentGlow = Color(0x338B5CF6),
        warning = Color(0xFFF59E0B),
        danger = Color(0xFFEF4444),
        dangerGlow = Color(0x33EF4444),
        textPrimary = Color(0xFFFAF5FF),
        textSecondary = Color(0xFFD8B4FE),
        textMuted = Color(0xFF9333EA),
        border = Color(0xFF4C1D95),
        borderLight = Color(0xFF6D28D9)
    )

    val CyberGold = EzzColorScheme(
        isDark = true,
        background = Color(0xFF0F0E0D),
        surface = Color(0xFF1C1917),
        surfaceVariant = Color(0xFF292524),
        surfaceLight = Color(0xFF44403C),
        cardBackground = Color(0xFF23201D),
        inputBackground = Color(0xFF292524),
        primary = Color(0xFFF59E0B),
        primaryHover = Color(0xFFFBBF24),
        primaryGlow = Color(0x33F59E0B),
        secondary = Color(0xFFD97706),
        accent = Color(0xFF10B981),
        accentGlow = Color(0x3310B981),
        warning = Color(0xFFF59E0B),
        danger = Color(0xFFEF4444),
        dangerGlow = Color(0x33EF4444),
        textPrimary = Color(0xFFFFFBEB),
        textSecondary = Color(0xFFD6D3D1),
        textMuted = Color(0xFF78716C),
        border = Color(0xFF44403C),
        borderLight = Color(0xFF57534E)
    )

    val FrostLight = EzzColorScheme(
        isDark = false,
        background = Color(0xFFF8FAFC),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFF1F5F9),
        surfaceLight = Color(0xFFE2E8F0),
        cardBackground = Color(0xFFFFFFFF),
        inputBackground = Color(0xFFF1F5F9),
        primary = Color(0xFF0284C7),
        primaryHover = Color(0xFF0369A1),
        primaryGlow = Color(0x330284C7),
        secondary = Color(0xFF4F46E5),
        accent = Color(0xFF059669),
        accentGlow = Color(0x33059669),
        warning = Color(0xFFD97706),
        danger = Color(0xFFDC2626),
        dangerGlow = Color(0x33DC2626),
        textPrimary = Color(0xFF0F172A),
        textSecondary = Color(0xFF475569),
        textMuted = Color(0xFF94A3B8),
        border = Color(0xFFE2E8F0),
        borderLight = Color(0xFFCBD5E1)
    )
}

class ThemeState {
    var preset by mutableStateOf(ThemePreset.EZZ_DARK)
    var customPrimaryColor by mutableStateOf<Color?>(null)
    var cardCornerRadius by mutableStateOf(10.dp)
    var enableAnimations by mutableStateOf(true)
    var isCompactDensity by mutableStateOf(true)

    val currentColors: EzzColorScheme
        get() {
            val base = when (preset) {
                ThemePreset.EZZ_DARK -> EzzThemePresets.EzzDark
                ThemePreset.OBSIDIAN -> EzzThemePresets.Obsidian
                ThemePreset.MIDNIGHT_NEON -> EzzThemePresets.MidnightNeon
                ThemePreset.AMETHYST_GLOW -> EzzThemePresets.AmethystGlow
                ThemePreset.CYBER_GOLD -> EzzThemePresets.CyberGold
                ThemePreset.FROST_LIGHT -> EzzThemePresets.FrostLight
            }
            return if (customPrimaryColor != null) {
                val custom = customPrimaryColor!!
                base.copy(
                    primary = custom,
                    primaryHover = custom.copy(alpha = 0.85f),
                    primaryGlow = custom.copy(alpha = 0.25f)
                )
            } else {
                base
            }
        }
}

val LocalThemeState = staticCompositionLocalOf { ThemeState() }
val LocalEzzColors = staticCompositionLocalOf { EzzThemePresets.EzzDark }

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
