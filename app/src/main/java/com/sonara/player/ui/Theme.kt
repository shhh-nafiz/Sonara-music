package com.sonara.player.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

val LocalAccent = compositionLocalOf { Color(0xFF1ED760) }
val LocalBg = compositionLocalOf { Color(0xFF121212) }

val TextSecondary = Color(0xFFB3B3B3)

/** Slightly lighter surface derived from the user's background color. */
fun surfaceOf(bg: Color): Color =
    (if (bg.luminance() < 0.5f) Color.White else Color.Black).copy(alpha = 0.08f).compositeOver(bg)

@Composable
fun SonaraTheme(accent: Color, bg: Color, content: @Composable () -> Unit) {
    val light = bg.luminance() > 0.5f
    val onBg = if (light) Color(0xFF121212) else Color.White
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = if (accent.luminance() > 0.5f) Color.Black else Color.White,
        background = bg,
        onBackground = onBg,
        surface = surfaceOf(bg),
        onSurface = onBg,
        surfaceVariant = surfaceOf(bg),
        onSurfaceVariant = onBg.copy(alpha = 0.7f),
        secondary = accent,
    )
    CompositionLocalProvider(LocalAccent provides accent, LocalBg provides bg) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
