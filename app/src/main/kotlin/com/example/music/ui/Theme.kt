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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette

/**
 * A catalogue, not a storefront.
 *
 * This library is 200-odd untagged files, so the design job is legibility at density rather than
 * merchandising. Near-black ground, one accent lifted from whatever is playing, and every numeral
 * set in monospace so durations and track numbers form real columns down the page.
 */

val Ink = Color(0xFF0B0D12)
val Surface1 = Color(0xFF141821)
val Surface2 = Color(0xFF1E2430)
val Hairline = Color(0xFF272E3C)
val TextHi = Color(0xFFECEEF3)
val TextLo = Color(0xFF8B93A7)
val DefaultAccent = Color(0xFF7C6BFF)

val LocalAccent = staticCompositionLocalOf { DefaultAccent }

private val scheme = darkColorScheme(
    background = Ink,
    onBackground = TextHi,
    surface = Surface1,
    onSurface = TextHi,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextLo,
    surfaceContainerHigh = Surface2,
    outline = Hairline,
    primary = DefaultAccent,
    onPrimary = Color.White,
)

private val display = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Black,
    letterSpacing = (-1.2).sp,
)

/** Durations, counts, years. Tabular by nature, so they get the tabular face. */
val Numeric = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.sp,
)

private val typography = Typography(
    displaySmall = display.copy(fontSize = 32.sp, lineHeight = 36.sp),
    headlineMedium = display.copy(fontSize = 24.sp, lineHeight = 28.sp),
    headlineSmall = display.copy(fontSize = 19.sp, lineHeight = 23.sp, letterSpacing = (-0.5).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 19.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 17.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.5.sp,
        letterSpacing = 0.9.sp,
    ),
)

@Composable
fun MusicTheme(accent: Color = DefaultAccent, content: @Composable () -> Unit) {
    val animated by animateColorAsState(accent, tween(700), label = "accent")
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
 * result too dark to read against the near-black ground is rejected rather than shipped.
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

/**
 * Deterministic cover colours for tracks with no artwork — which here is nearly all of them.
 * Same name always yields the same pair, so a track keeps its identity between sessions and the
 * grid reads as a set of distinct things rather than a wall of identical grey squares.
 */
fun coverColors(seed: String): Pair<Color, Color> {
    var h = 0
    for (ch in seed.lowercase()) h = h * 31 + ch.code
    val hue = ((h % 360) + 360) % 360
    return Color.hsl(hue.toFloat(), 0.42f, 0.30f) to
        Color.hsl(((hue + 34) % 360).toFloat(), 0.48f, 0.15f)
}
