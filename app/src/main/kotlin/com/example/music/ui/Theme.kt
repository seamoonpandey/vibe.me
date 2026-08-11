package com.example.music.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
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
 * Warm paper, not a storefront.
 *
 * The library is 200-odd untagged files, so the job is legibility at density. A cream-blush ground
 * keeps long scrolling sessions soft, one accent is lifted from whatever is playing, and every
 * numeral is monospaced so durations form real columns down the page.
 */

val Ink = Color(0xFFFDF7F4)          // page
val Surface1 = Color(0xFFF8EBE6)     // raised: bars, sheets, mini player
val Surface2 = Color(0xFFF1DBD5)     // chips, wells
val Hairline = Color(0xFFE7D2CC)
val TextHi = Color(0xFF2B2126)       // warm near-black
val TextLo = Color(0xFF8A737A)
val DefaultAccent = Color(0xFFC4506F) // dusty rose
val OnAccent = Color(0xFFFFFBFA)

val LocalAccent = staticCompositionLocalOf { DefaultAccent }

/**
 * The colour pulled from the current artwork. Scoped to the player on purpose: when this drove the
 * whole app, one brown album cover turned every chip, tab and icon olive.
 */
val LocalArtAccent = staticCompositionLocalOf { DefaultAccent }

private val scheme = lightColorScheme(
    background = Ink,
    onBackground = TextHi,
    surface = Surface1,
    onSurface = TextHi,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextLo,
    surfaceContainerHigh = Surface1,
    surfaceContainerLow = Surface1,
    outline = Hairline,
    outlineVariant = Hairline,
    primary = DefaultAccent,
    onPrimary = OnAccent,
)

private val display = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-0.4).sp,
)

/** Durations, counts, years. Tabular by nature, so they get the tabular face. */
val Numeric = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.sp,
)

private val typography = Typography(
    displaySmall = display.copy(fontSize = 32.sp, lineHeight = 36.sp),
    headlineMedium = display.copy(fontSize = 24.sp, lineHeight = 28.sp),
    headlineSmall = display.copy(fontSize = 19.sp, lineHeight = 23.sp, letterSpacing = (-0.5).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 18.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 17.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 15.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.1.sp),
)

@Composable
fun MusicTheme(artAccent: Color = DefaultAccent, content: @Composable () -> Unit) {
    val animated by animateColorAsState(artAccent, tween(700), label = "artAccent")
    CompositionLocalProvider(
        LocalAccent provides DefaultAccent,
        LocalArtAccent provides animated,
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

private fun Color.luminance() = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/**
 * Force a colour to stay legible as text and fills on the cream ground.
 *
 * On a light page the failure mode is the opposite of a dark one: pale artwork yields a pastel
 * accent that vanishes. Anything too light is darkened until it carries, rather than discarded,
 * so the accent still tracks the album.
 */
private fun Color.fitToLightGround(): Color {
    var c = this
    var guard = 0
    while (c.luminance() > 0.42f && guard++ < 12) {
        c = Color(red = c.red * 0.86f, green = c.green * 0.86f, blue = c.blue * 0.86f, alpha = 1f)
    }
    return c
}

/**
 * Pick the most usable swatch from album art, then make it work on cream. Vibrant reads best;
 * muted and dominant are fallbacks for artwork with no strong hue.
 */
fun accentFrom(bitmap: Bitmap): Color? {
    val palette = Palette.from(bitmap).clearFilters().maximumColorCount(16).generate()
    val candidate = palette.vibrantSwatch
        ?: palette.darkVibrantSwatch
        ?: palette.mutedSwatch
        ?: palette.dominantSwatch
        ?: return null
    return Color(candidate.rgb).fitToLightGround()
}

/**
 * Deterministic cover colours for tracks with no artwork — which here is nearly all of them.
 * Same name always yields the same pair, so a track keeps its identity between sessions and the
 * grid reads as a set of distinct things rather than a wall of identical blank squares. Kept in
 * the blush register so the covers sit inside the palette instead of fighting it.
 */
fun coverColors(seed: String): Pair<Color, Color> {
    var h = 0
    for (ch in seed.lowercase()) h = h * 31 + ch.code
    val hue = ((h % 360) + 360) % 360
    return Color.hsl(hue.toFloat(), 0.44f, 0.82f) to
        Color.hsl(((hue + 28) % 360).toFloat(), 0.40f, 0.66f)
}

/** Ink for text drawn on top of a generated cover tile. */
val OnCover = Color(0xFF3A2A30)
