package me.vibe.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import me.vibe.Deps
import me.vibe.data.Song
import me.vibe.data.formatDuration
import me.vibe.data.initialsOf

/**
 * Artwork, or a generated stand-in. Most files here have no embedded art, so the fallback is not
 * an edge case — it is the common case, and it has to look deliberate.
 *
 * The cover is painted underneath and the image composited over it, rather than swapped in through
 * SubcomposeAsyncImage. Subcomposition costs a separate measure pass per row, which is the wrong
 * price to pay on every item of a long scrolling list for a fallback that is usually what shows.
 */
@Composable
fun Artwork(
    model: Any?,
    seed: String,
    modifier: Modifier = Modifier,
    corner: Int = 8,
) {
    val shape = RoundedCornerShape(corner.dp)
    val dark = LocalPalette.current.dark
    val (top, bottom) = remember(seed, dark) { coverColors(seed, dark) }
    val initials = remember(seed) { initialsOf(seed) }

    Box(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(top, bottom))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = OnCover.copy(alpha = 0.72f),
            fontSize = 15.sp,
            style = MaterialTheme.typography.labelLarge,
        )
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            imageLoader = Deps.images,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Three bars, moving. The point is that "this is the track playing right now" should be legible
 * from across the list without reading anything — a static glyph and a tinted title both need a
 * second look, and motion does not. Each bar runs on its own period so they never line up into a
 * pulse, and the whole thing stops when playback does, which is information in itself.
 *
 * Drawn in one Canvas off a single infinite transition: three animated floats and no recomposition,
 * since the values are read in the draw scope.
 */
@Composable
fun EqualizerBars(color: Color, modifier: Modifier = Modifier, animating: Boolean = true) {
    // Paused draws a plain shape and starts no transition at all. Leaving one running to hold a
    // constant value is an animation frame a second, forever, for something that is not moving.
    if (!animating) {
        Canvas(modifier) { bars(color) { 0.34f } }
        return
    }
    val transition = rememberInfiniteTransition(label = "eq")
    val heights = listOf(620, 900, 740).mapIndexed { i, period ->
        transition.animateFloat(
            initialValue = if (i % 2 == 0) 0.28f else 0.85f,
            targetValue = if (i % 2 == 0) 1f else 0.22f,
            animationSpec = infiniteRepeatable(
                tween(period, easing = LinearEasing),
                RepeatMode.Reverse,
            ),
            label = "bar$i",
        )
    }
    Canvas(modifier) { bars(color) { i -> heights[i].value } }
}

private inline fun DrawScope.bars(color: Color, height: (Int) -> Float) {
    val gap = size.width * 0.22f
    val bar = (size.width - gap * 2f) / 3f
    repeat(3) { i ->
        val h = size.height * height(i)
        drawRoundRect(
            color = color,
            topLeft = Offset((bar + gap) * i, size.height - h),
            size = Size(bar, h),
            cornerRadius = CornerRadius(bar / 2f),
        )
    }
}

@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
    index: Int? = null,
    onMenu: (() -> Unit)? = null,
    animating: Boolean = true,
) {
    val accent = LocalAccent.current
    val titleColor by animateColorAsState(if (playing) accent else TextHi, tween(220), label = "rowTitle")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
                if (playing) {
                    EqualizerBars(accent, Modifier.size(14.dp), animating)
                } else {
                    Text("$index", style = Numeric, color = TextLo)
                }
            }
        } else {
            Box(contentAlignment = Alignment.Center) {
                Artwork(song.artUri, song.title, Modifier.size(48.dp), corner = 6)
                // The list has no track numbers, so the marker goes on the artwork — scrimmed,
                // because it has to stay readable over whatever the cover happens to be.
                androidx.compose.animation.AnimatedVisibility(playing, enter = fadeIn(), exit = fadeOut()) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        EqualizerBars(Color.White, Modifier.size(16.dp), animating)
                    }
                }
            }
        }

        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = TextLo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // No duration column. It is the same width on every row and tells you nothing while
        // browsing; the player and the track sheet both show it when it matters.
        if (onMenu != null) {
            IconButton(onMenu, Modifier.size(40.dp)) {
                Icon(Icons.Default.MoreVert, "More options", Modifier.size(19.dp), tint = TextLo.copy(alpha = 0.6f))
            }
        } else {
            Box(Modifier.width(12.dp))
        }
    }
}

@Composable
fun GridCard(
    title: String,
    subtitle: String,
    art: Any?,
    seed: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    round: Boolean = false,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Artwork(art, seed, Modifier.fillMaxWidth().aspectRatio(1f), corner = if (round) 500 else 10)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = TextHi,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (round) TextAlign.Center else TextAlign.Start,
                modifier = if (round) Modifier.fillMaxWidth() else Modifier,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextLo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (round) TextAlign.Center else TextAlign.Start,
                modifier = if (round) Modifier.fillMaxWidth() else Modifier,
            )
        }
    }
}

/** Sticky section heading for a grouped list. */
@Composable
fun SectionHeader(text: String, @Suppress("UNUSED_PARAMETER") count: Int) {
    Row(
        Modifier.fillMaxWidth().background(Ink).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.headlineSmall,
            color = TextHi,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/**
 * Empty states name the next action rather than apologising. A library with no audio on the device
 * is a different situation from a search that matched nothing, so they say different things.
 */
@Composable
fun EmptyState(headline: String, detail: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(headline, style = MaterialTheme.typography.headlineSmall, color = TextHi, textAlign = TextAlign.Center)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = TextLo, textAlign = TextAlign.Center)
        }
    }
}
