package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * CareLink design tokens. Neutral graphite surfaces, one restrained accent and
 * semantic status colours that keep their meaning in both themes.
 */
@Immutable
data class CareColors(
    val ok: Color,
    val okContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val critical: Color,
    val criticalContainer: Color,
    val paused: Color,
    val pausedContainer: Color,
    val onPaused: Color,
    val neutral: Color,
    val border: Color,
    val borderStrong: Color,
    val track: Color,
    val textSecondary: Color,
    val textMuted: Color,
)

internal val DarkCareColors = CareColors(
    ok = Color(0xFF3DBE8B),
    okContainer = Color(0xFF12291F),
    warning = Color(0xFFE9AB3F),
    warningContainer = Color(0xFF2E2311),
    critical = Color(0xFFF26B67),
    criticalContainer = Color(0xFF331816),
    paused = Color(0xFFF0954A),
    pausedContainer = Color(0xFF34210F),
    onPaused = Color(0xFF1E1206),
    neutral = Color(0xFF8B949E),
    border = Color(0xFF262B32),
    borderStrong = Color(0xFF353C45),
    track = Color(0xFF262B32),
    textSecondary = Color(0xFFA2ABB5),
    textMuted = Color(0xFF6E7781),
)

internal val LightCareColors = CareColors(
    ok = Color(0xFF16805A),
    okContainer = Color(0xFFE3F4EC),
    warning = Color(0xFFA9670B),
    warningContainer = Color(0xFFFDF1DC),
    critical = Color(0xFFC93A35),
    criticalContainer = Color(0xFFFCE9E8),
    paused = Color(0xFFC0620F),
    pausedContainer = Color(0xFFFDEBDB),
    onPaused = Color.White,
    neutral = Color(0xFF6A737D),
    border = Color(0xFFE1E4E8),
    borderStrong = Color(0xFFCDD2D8),
    track = Color(0xFFE6E9ED),
    textSecondary = Color(0xFF57606A),
    textMuted = Color(0xFF8C959F),
)

val LocalCareColors = staticCompositionLocalOf { DarkCareColors }

// Brand accent: calm clinical blue.
internal val AccentDark = Color(0xFF7AAEF7)
internal val AccentLight = Color(0xFF1F63D1)
