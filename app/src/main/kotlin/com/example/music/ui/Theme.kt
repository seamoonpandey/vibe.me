package com.example.music.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette

/**
 * Near-black base with a single accent pulled from the current album art. Structure borrows from
 * the obvious reference; the colour does not — it changes with the music, which is what keeps the
 * app from looking like every other Material template.
 */

val Ink = Color(0xFF080809)
val Surface1 = Color(0xFF131316)
val Surface2 = Color(0xFF1D1D21)
val TextHi = Color(0xFFF2F2F3)
val TextLo = Color(0xFF9C9CA5)
val DefaultAccent = Color(0xFF6F5BFF)

val LocalAccent = staticCompositionLocalOf { DefaultAccent }

private val scheme = darkColorScheme(
    background = Ink,
    onBackground = TextHi,
    surface = Surface1,
    onSurface = TextHi,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextLo,
    primary = DefaultAccent,
    onPrimary = Color.White,
)

private val display = TextStyle(fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)

private val typography = Typography(
    displaySmall = display.copy(fontSize = 34.sp, lineHeight = 38.sp),
    headlineMedium = display.copy(fontSize = 26.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.3.sp),
)

@Composable
fun MusicTheme(accent: Color = DefaultAccent, content: @Composable () -> Unit) {
    val animated by animateColorAsState(accent, tween(600), label = "accent")
    CompositionLocalProvider(LocalAccent provides animated) {
        MaterialTheme(
            colorScheme = scheme.copy(primary = animated),
            typography = typography,
            content = content,
        )
    }
}

/**
 * Pick the most usable swatch from album art. Vibrant first, then muted, then dominant — and any
 * result too dark to read against the near-black background is rejected rather than shipped.
 */
fun accentFrom(bitmap: Bitmap): Color? {
    val palette = Palette.from(bitmap).clearFilters().maximumColorCount(16).generate()
    val candidate = palette.vibrantSwatch
        ?: palette.lightVibrantSwatch
        ?: palette.mutedSwatch
        ?: palette.dominantSwatch
        ?: return null
    val color = Color(candidate.rgb)
    return if (color.luminance() < 0.06f) null else color
}

private fun Color.luminance() = 0.2126f * red + 0.7152f * green + 0.0722f * blue
