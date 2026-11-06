package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.model.ThemeMode

private val DarkScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF071A33),
    primaryContainer = Color(0xFF16263D),
    onPrimaryContainer = Color(0xFFD3E3FC),
    secondary = Color(0xFFA9B4C2),
    onSecondary = Color(0xFF111418),
    secondaryContainer = Color(0xFF242A32),
    onSecondaryContainer = Color(0xFFE3E7EC),
    tertiary = DarkCareColors.warning,
    background = Color(0xFF0E1013),
    onBackground = Color(0xFFE6E8EB),
    surface = Color(0xFF0E1013),
    onSurface = Color(0xFFE6E8EB),
    surfaceVariant = Color(0xFF1C2026),
    onSurfaceVariant = DarkCareColors.textSecondary,
    surfaceContainerLowest = Color(0xFF0B0D0F),
    surfaceContainerLow = Color(0xFF13161A),
    surfaceContainer = Color(0xFF161A1E),
    surfaceContainerHigh = Color(0xFF1C2026),
    surfaceContainerHighest = Color(0xFF23282F),
    inverseSurface = Color(0xFFE6E8EB),
    inverseOnSurface = Color(0xFF16191D),
    outline = DarkCareColors.borderStrong,
    outlineVariant = DarkCareColors.border,
    error = DarkCareColors.critical,
    onError = Color(0xFF2B0A09),
    errorContainer = DarkCareColors.criticalContainer,
    onErrorContainer = Color(0xFFFFDAD7),
    scrim = Color.Black,
)

private val LightScheme = lightColorScheme(
    primary = AccentLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2ECFC),
    onPrimaryContainer = Color(0xFF0B2A5C),
    secondary = Color(0xFF4F5864),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9ECF0),
    onSecondaryContainer = Color(0xFF1B1F24),
    tertiary = LightCareColors.warning,
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF15181C),
    surface = Color(0xFFF5F6F8),
    onSurface = Color(0xFF15181C),
    surfaceVariant = Color(0xFFECEEF1),
    onSurfaceVariant = LightCareColors.textSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFBFC),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF0F2F4),
    surfaceContainerHighest = Color(0xFFE8EBEE),
    inverseSurface = Color(0xFF22262B),
    inverseOnSurface = Color(0xFFF1F3F5),
    outline = LightCareColors.borderStrong,
    outlineVariant = LightCareColors.border,
    error = LightCareColors.critical,
    onError = Color.White,
    errorContainer = LightCareColors.criticalContainer,
    onErrorContainer = Color(0xFF5C1210),
    scrim = Color.Black,
)

/** Tight, consistent radii: tools, not toys. */
val CareShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun CareLinkTheme(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(LocalCareColors provides if (dark) DarkCareColors else LightCareColors) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = CareTypography, shapes = CareShapes, content = content)
    }
}

/** Shorthand for semantic tokens: `Care.colors.ok`. */
object Care {
    val colors: CareColors
        @Composable @ReadOnlyComposable get() = LocalCareColors.current
    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalCareColors.current === DarkCareColors
}
