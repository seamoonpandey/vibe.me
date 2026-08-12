package me.vibe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.vibe.data.Song
import me.vibe.data.formatDuration

/**
 * One screen for albums, artists, playlists and favorites. All four are a header plus an ordered
 * list; three near-identical files would have been three places to fix the same bug. The title
 * lives in the app bar, so the header carries artwork and actions only.
 */
@Composable
fun DetailScreen(
    subtitle: String,
    songs: List<Song>,
    contentPadding: PaddingValues,
    currentSongId: Long?,
    roundArt: Boolean = false,
    emptyMessage: String? = null,
    onPlay: (Int) -> Unit,
    onShuffle: () -> Unit,
    onSongMenu: ((Song, Int) -> Unit)? = null,
) {
    if (songs.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(contentPadding)) {
            EmptyState("Empty", emptyMessage ?: "There is nothing to show here yet.")
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "header", contentType = "header") {
            DetailHeader(subtitle, songs, roundArt, onPlay, onShuffle)
        }
        itemsIndexed(songs, key = { i, s -> "$i-${s.id}" }, contentType = { _, _ -> "song" }) { i, song ->
            SongRow(
                song = song,
                index = i + 1,
                playing = song.id == currentSongId,
                onClick = { onPlay(i) },
                onMenu = onSongMenu?.let { menu -> { menu(song, i) } },
            )
        }
    }
}

@Composable
private fun DetailHeader(
    subtitle: String,
    songs: List<Song>,
    roundArt: Boolean,
    onPlay: (Int) -> Unit,
    onShuffle: () -> Unit,
) {
    val accent = LocalAccent.current
    val total = songs.sumOf { it.durationMs }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f), Ink)))
            .padding(top = 8.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Artwork(
            songs.first().artUri,
            songs.first().album.ifBlank { songs.first().title },
            Modifier.fillMaxWidth(0.48f).aspectRatio(1f),
            corner = if (roundArt) 500 else 12,
        )
        Text(
            "$subtitle · ${formatDuration(total)}",
            style = MaterialTheme.typography.bodySmall,
            color = TextLo,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { onPlay(0) },
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = OnAccent),
            ) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(19.dp))
                Text("Play", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(onClick = onShuffle) {
                Icon(Icons.Default.Shuffle, null, Modifier.size(17.dp), tint = TextHi)
                Text("Shuffle", Modifier.padding(start = 6.dp), color = TextHi, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
