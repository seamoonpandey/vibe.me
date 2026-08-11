package com.example.music.ui

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.music.data.Song
import com.example.music.data.formatDuration

/**
 * One screen for albums, artists and playlists. All three are a header plus an ordered song list;
 * three near-identical files would have been three places to fix the same bug.
 */
@Composable
fun DetailScreen(
    title: String,
    subtitle: String,
    songs: List<Song>,
    contentPadding: PaddingValues,
    currentSongId: Long?,
    roundArt: Boolean = false,
    numbered: Boolean = true,
    onBack: () -> Unit,
    onPlay: (Int) -> Unit,
    onShuffle: () -> Unit,
    onSongMenu: ((Song, Int) -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            item(key = "header", contentType = "header") {
                DetailHeader(title, subtitle, songs, roundArt, onPlay, onShuffle)
            }
            itemsIndexed(
                songs,
                key = { i, s -> "$i-${s.id}" },
                contentType = { _, _ -> "song" },
            ) { i, song ->
                SongRow(
                    song = song,
                    index = if (numbered) i + 1 else null,
                    highlighted = song.id == currentSongId,
                    onClick = { onPlay(i) },
                    onMenu = onSongMenu?.let { menu -> { menu(song, i) } },
                )
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(top = contentPadding.calculateTopPadding() + 4.dp, start = 4.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextHi)
        }
    }
}

@Composable
private fun DetailHeader(
    title: String,
    subtitle: String,
    songs: List<Song>,
    roundArt: Boolean,
    onPlay: (Int) -> Unit,
    onShuffle: () -> Unit,
) {
    val accent = LocalAccent.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.30f), Ink)))
            .padding(top = 48.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Artwork(
            songs.firstOrNull()?.artUri,
            Modifier.fillMaxWidth(0.55f).aspectRatio(1f),
            corner = if (roundArt) 500 else 10,
        )
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = TextHi,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Text(
            "$subtitle · ${songs.size} songs · ${formatDuration(songs.sumOf { it.durationMs })}",
            style = MaterialTheme.typography.bodySmall,
            color = TextLo,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onPlay(0) },
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
            ) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                Text("Play", Modifier.padding(start = 6.dp))
            }
            OutlinedButton(onClick = onShuffle) {
                Icon(Icons.Default.Shuffle, null, Modifier.size(18.dp), tint = TextHi)
                Text("Shuffle", Modifier.padding(start = 6.dp), color = TextHi)
            }
        }
    }
}
