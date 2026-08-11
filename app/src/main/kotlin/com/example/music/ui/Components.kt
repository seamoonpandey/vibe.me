package com.example.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.GraphicEq
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
import com.example.music.Deps
import com.example.music.data.Song
import com.example.music.data.formatDuration
import com.example.music.data.initialsOf

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

@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
    index: Int? = null,
    onMenu: (() -> Unit)? = null,
) {
    val accent = LocalAccent.current
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
                    Icon(Icons.Default.GraphicEq, "Now playing", Modifier.size(16.dp), tint = accent)
                } else {
                    Text("$index", style = Numeric, color = TextLo)
                }
            }
        } else {
            Artwork(song.artUri, song.title, Modifier.size(48.dp), corner = 6)
        }

        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (playing) accent else TextHi,
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
