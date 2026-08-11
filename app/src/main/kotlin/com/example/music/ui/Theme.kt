package com.example.music.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette as ArtPalette

/**
 * One set of tokens, swappable at runtime.
 *
 * These colours used to be top-level constants, which is why they read as plain names at every
 * call site. Keeping that spelling matters — there are hundreds of uses — so each name is now a
 * composable getter over [LocalPalette]. No call site changed; only where the value comes from.
 */
data class Palette(
    val ink: Color,
    val surface1: Color,
    val surface2: Color,
    val hairline: Color,
    val textHi: Color,
    val textLo: Color,
    val accent: Color,
    val onAccent: Color,
    val onCover: Color,
    val dark: Boolean,
)

enum class ThemeChoice(val label: String) {
    ROSE("Rose"),
    PURPLE("Purple"),
    OCEAN("Ocean"),
    FOREST("Forest"),
    AMBER("Amber"),
    MIDNIGHT("Midnight"),
    CARBON("Carbon"),
}

fun paletteFor(choice: ThemeChoice): Palette = when (choice) {
    ThemeChoice.ROSE -> Palette(
        ink = Color(0xFFFDF7F4), surface1 = Color(0xFFF8EBE6), surface2 = Color(0xFFF1DBD5),
        hairline = Color(0xFFE7D2CC), textHi = Color(0xFF2B2126), textLo = Color(0xFF8A737A),
        accent = Color(0xFFC4506F), onAccent = Color(0xFFFFFBFA),
        onCover = Color(0xFF3A2A30), dark = false,
    )
    ThemeChoice.PURPLE -> Palette(
        ink = Color(0xFFFAF6FD), surface1 = Color(0xFFF0E9F8), surface2 = Color(0xFFE5DAF2),
        hairline = Color(0xFFD8C9EA), textHi = Color(0xFF261C33), textLo = Color(0xFF7A6B92),
        accent = Color(0xFF7B4FCF), onAccent = Color(0xFFFCFAFF),
        onCover = Color(0xFF2E2340), dark = false,
    )
    ThemeChoice.OCEAN -> Palette(
        ink = Color(0xFFF3F8FB), surface1 = Color(0xFFE5EFF6), surface2 = Color(0xFFD4E5F0),
        hairline = Color(0xFFC0D8E7), textHi = Color(0xFF10262F), textLo = Color(0xFF5F7D8C),
        accent = Color(0xFF0E7C93), onAccent = Color(0xFFF7FCFE),
        onCover = Color(0xFF16323D), dark = false,
    )
    ThemeChoice.FOREST -> Palette(
        ink = Color(0xFFF5F8F3), surface1 = Color(0xFFE7EFE4), surface2 = Color(0xFFD8E5D3),
        hairline = Color(0xFFC5D8BF), textHi = Color(0xFF1A2618), textLo = Color(0xFF677F61),
        accent = Color(0xFF3E7D3A), onAccent = Color(0xFFF9FDF8),
        onCover = Color(0xFF23331F), dark = false,
    )
    ThemeChoice.AMBER -> Palette(
        ink = Color(0xFFFDF8F0), surface1 = Color(0xFFF7ECDB), surface2 = Color(0xFFF0DFC4),
        hairline = Color(0xFFE2CDAB), textHi = Color(0xFF2B2113), textLo = Color(0xFF85724F),
        accent = Color(0xFFB4741A), onAccent = Color(0xFFFFFCF6),
        onCover = Color(0xFF3A2D18), dark = false,
    )
    ThemeChoice.MIDNIGHT -> Palette(
        ink = Color(0xFF0B0D12), surface1 = Color(0xFF141821), surface2 = Color(0xFF1E2430),
        hairline = Color(0xFF272E3C), textHi = Color(0xFFECEEF3), textLo = Color(0xFF8B93A7),
        accent = Color(0xFF7C6BFF), onAccent = Color(0xFF0B0D12),
        onCover = Color(0xFFF0F1F5), dark = true,
    )
    // Spotify's own register: near-black, one green, grey secondary text.
    ThemeChoice.CARBON -> Palette(
        ink = Color(0xFF0C0C0C), surface1 = Color(0xFF181818), surface2 = Color(0xFF242424),
        hairline = Color(0xFF2E2E2E), textHi = Color(0xFFF5F5F5), textLo = Color(0xFFA7A7A7),
        accent = Color(0xFF1DB954), onAccent = Color(0xFF0A0A0A),
        onCover = Color(0xFFF5F5F5), dark = true,
    )
}

val LocalPalette = staticCompositionLocalOf { paletteFor(ThemeChoice.ROSE) }

/** The colour pulled from the current artwork. Scoped to the player; see [MusicTheme]. */
val LocalArtAccent = staticCompositionLocalOf { paletteFor(ThemeChoice.ROSE).accent }

private val LocalAccentInternal = staticCompositionLocalOf { paletteFor(ThemeChoice.ROSE).accent }

val LocalAccent: ProvidableCompositionLocal<Color> get() = LocalAccentInternal

// The names the rest of the app already uses, now sourced from the active palette.
val Ink: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.ink
val Surface1: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.surface1
val Surface2: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.surface2
val Hairline: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.hairline
val TextHi: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.textHi
val TextLo: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.textLo
val DefaultAccent: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.accent
val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.onAccent
val OnCover: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.onCover

/** A record is black whatever the page behind it is. */
val VinylBody = Color(0xFF17131A)
val VinylGroove = Color(0x1FFFFFFF)

private fun schemeFor(p: Palette) = if (p.dark) {
    darkColorScheme(
        background = p.ink, onBackground = p.textHi,
        surface = p.surface1, onSurface = p.textHi,
        surfaceVariant = p.surface2, onSurfaceVariant = p.textLo,
        surfaceContainer = p.surface1, surfaceContainerHigh = p.surface1,
        surfaceContainerHighest = p.surface2, surfaceContainerLow = p.surface1,
        outline = p.hairline, outlineVariant = p.hairline,
        primary = p.accent, onPrimary = p.onAccent,
        primaryContainer = p.surface2, onPrimaryContainer = p.textHi,
        secondary = p.accent, onSecondary = p.onAccent,
        secondaryContainer = p.surface2, onSecondaryContainer = p.textHi,
        tertiary = p.accent, onTertiary = p.onAccent,
        tertiaryContainer = p.surface2, onTertiaryContainer = p.textHi,
        surfaceTint = p.accent, inverseSurface = p.textHi, inverseOnSurface = p.ink,
        inversePrimary = p.accent,
    )
} else {
    lightColorScheme(
        background = p.ink, onBackground = p.textHi,
        surface = p.surface1, onSurface = p.textHi,
        surfaceVariant = p.surface2, onSurfaceVariant = p.textLo,
        surfaceContainer = p.surface1, surfaceContainerHigh = p.surface1,
        surfaceContainerHighest = p.surface2, surfaceContainerLow = p.surface1,
        outline = p.hairline, outlineVariant = p.hairline,
        primary = p.accent, onPrimary = p.onAccent,
        primaryContainer = p.surface2, onPrimaryContainer = p.textHi,
        secondary = p.accent, onSecondary = p.onAccent,
        secondaryContainer = p.surface2, onSecondaryContainer = p.textHi,
        tertiary = p.accent, onTertiary = p.onAccent,
        tertiaryContainer = p.surface2, onTertiaryContainer = p.textHi,
        surfaceTint = p.accent, inverseSurface = p.textHi, inverseOnSurface = p.ink,
        inversePrimary = p.accent,
    )
}

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
fun MusicTheme(
    choice: ThemeChoice = ThemeChoice.ROSE,
    artAccent: Color? = null,
    content: @Composable () -> Unit,
) {
    val palette = paletteFor(choice)
    val animatedArt by animateColorAsState(artAccent ?: palette.accent, tween(700), label = "art")
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalAccentInternal provides palette.accent,
        LocalArtAccent provides animatedArt,
    ) {
        MaterialTheme(colorScheme = schemeFor(palette), typography = typography, content = content)
    }
}

private fun Color.luminance() = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/**
 * Force an artwork colour to stay legible against the current page.
 *
 * The failure mode flips with the ground: on cream a pale swatch disappears, on near-black a dark
 * one does. Nudge it until it carries rather than discarding it, so the accent still tracks the
 * album under either theme.
 */
fun Color.fitTo(dark: Boolean): Color {
    var c = this
    var guard = 0
    if (dark) {
        while (c.luminance() < 0.20f && guard++ < 14) {
            c = Color(
                red = (c.red * 1.18f + 0.02f).coerceAtMost(1f),
                green = (c.green * 1.18f + 0.02f).coerceAtMost(1f),
                blue = (c.blue * 1.18f + 0.02f).coerceAtMost(1f),
                alpha = 1f,
            )
        }
    } else {
        while (c.luminance() > 0.42f && guard++ < 14) {
            c = Color(red = c.red * 0.86f, green = c.green * 0.86f, blue = c.blue * 0.86f, alpha = 1f)
        }
    }
    return c
}

/** Pick the most usable swatch from album art. Callers fit it to the active ground. */
fun accentFrom(bitmap: Bitmap): Color? {
    val palette = ArtPalette.from(bitmap).clearFilters().maximumColorCount(16).generate()
    val candidate = palette.vibrantSwatch
        ?: palette.darkVibrantSwatch
        ?: palette.mutedSwatch
        ?: palette.dominantSwatch
        ?: return null
    return Color(candidate.rgb)
}

/**
 * Deterministic cover colours for tracks with no artwork — which here is nearly all of them.
 * Same name always yields the same pair, so a track keeps its identity between sessions and the
 * grid reads as a set of distinct things rather than a wall of identical blank squares.
 */
fun coverColors(seed: String, dark: Boolean = false): Pair<Color, Color> {
    var h = 0
    for (ch in seed.lowercase()) h = h * 31 + ch.code
    val hue = ((h % 360) + 360) % 360
    return if (dark) {
        Color.hsl(hue.toFloat(), 0.40f, 0.32f) to Color.hsl(((hue + 28) % 360).toFloat(), 0.46f, 0.20f)
    } else {
        Color.hsl(hue.toFloat(), 0.44f, 0.82f) to Color.hsl(((hue + 28) % 360).toFloat(), 0.40f, 0.66f)
    }
}
