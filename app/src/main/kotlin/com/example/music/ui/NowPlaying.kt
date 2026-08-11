package com.example.music.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.example.music.data.Song
import com.example.music.data.formatDuration
import com.example.music.playback.PlayerUiState

@Composable
fun MiniPlayer(state: PlayerUiState, onExpand: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit) {
    val song = state.current ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .background(Surface1)
            .clickable(onClick = onExpand),
    ) {
        LinearProgressIndicator(
            progress = { if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = LocalAccent.current,
            trackColor = Surface2,
        )
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(song.artUri, song.title, Modifier.size(44.dp), corner = 6)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(song.title, style = MaterialTheme.typography.titleMedium, color = TextHi,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = MaterialTheme.typography.bodySmall, color = TextLo,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onPlayPause) {
                Icon(
                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (state.isPlaying) "Pause" else "Play",
                    tint = TextHi,
                )
            }
            IconButton(onNext) { Icon(Icons.Default.SkipNext, "Next", tint = TextHi) }
        }
    }
}

@Composable
fun NowPlayingScreen(
    state: PlayerUiState,
    isFavorite: Boolean,
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onFavorite: () -> Unit,
    onQueueSelect: (Int) -> Unit,
    onQueueRemove: (Int) -> Unit,
    onQueueMove: (Int, Int) -> Unit,
    onEditTags: (Song) -> Unit,
) {
    val song = state.current ?: return
    var showQueue by remember { mutableStateOf(false) }
    val accent = LocalAccent.current

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.45f), Ink, Ink)))
            .systemBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onCollapse) {
                Icon(Icons.Default.ExpandMore, "Collapse", tint = TextHi)
            }
            Spacer(Modifier.weight(1f))
            IconButton({ onEditTags(song) }) { Icon(Icons.Default.Tune, "Edit tags", tint = TextHi) }
        }

        if (showQueue) {
            QueueList(
                state = state,
                modifier = Modifier.weight(1f),
                onSelect = onQueueSelect,
                onRemove = onQueueRemove,
                onMove = onQueueMove,
            )
        } else {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Artwork(song.artUri, song.title, Modifier.fillMaxWidth().aspectRatio(1f), corner = 14)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.headlineMedium, color = TextHi,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${song.artist} · ${song.album}", style = MaterialTheme.typography.bodyMedium,
                    color = TextLo, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onFavorite) {
                Icon(
                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    "Favorite",
                    tint = if (isFavorite) accent else TextLo,
                )
            }
        }

        // Dragging must not fight the 500ms position poll, so the thumb follows local state while held.
        var scrubbing by remember { mutableStateOf<Float?>(null) }
        val progress = scrubbing ?: if (state.durationMs > 0) {
            state.positionMs.toFloat() / state.durationMs
        } else 0f

        Slider(
            value = progress.coerceIn(0f, 1f),
            onValueChange = { scrubbing = it },
            onValueChangeFinished = {
                scrubbing?.let { onSeek((it * state.durationMs).toLong()) }
                scrubbing = null
            },
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatDuration((progress * state.durationMs).toLong()),
                style = MaterialTheme.typography.bodySmall, color = TextLo)
            Spacer(Modifier.weight(1f))
            Text(formatDuration(state.durationMs),
                style = MaterialTheme.typography.bodySmall, color = TextLo)
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onShuffle) {
                Icon(Icons.Default.Shuffle, "Shuffle",
                    tint = if (state.shuffle) accent else TextLo)
            }
            IconButton(onPrevious) {
                Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(36.dp), tint = TextHi)
            }
            Box(
                Modifier.size(68.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(accent).clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (state.isPlaying) "Pause" else "Play",
                    Modifier.size(34.dp),
                    tint = Color.Black,
                )
            }
            IconButton(onNext) {
                Icon(Icons.Default.SkipNext, "Next", Modifier.size(36.dp), tint = TextHi)
            }
            IconButton(onRepeat) {
                Icon(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne
                    else Icons.Default.Repeat,
                    "Repeat",
                    tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) TextLo else accent,
                )
            }
        }

        Text(
            if (showQueue) "Hide queue" else "Queue · ${state.queue.size}",
            style = MaterialTheme.typography.labelLarge,
            color = TextLo,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showQueue = !showQueue }
                .padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun QueueList(
    state: PlayerUiState,
    modifier: Modifier,
    onSelect: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    LazyColumn(modifier.fillMaxHeight()) {
        itemsIndexed(state.queue, key = { i, s -> "$i-${s.id}" }) { i, song ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SongRow(
                        song = song,
                        index = i + 1,
                        playing = i == state.index,
                        onClick = { onSelect(i) },
                    )
                }
                // ponytail: arrow reordering, not drag. Two taps beats pulling in a drag library
                // for a list most people touch rarely; swap for a reorderable modifier if it grates.
                IconButton({ onMove(i, i - 1) }, enabled = i > 0) {
                    Icon(Icons.Default.KeyboardArrowUp, "Move up", tint = TextLo)
                }
                IconButton({ onMove(i, i + 1) }, enabled = i < state.queue.lastIndex) {
                    Icon(Icons.Default.KeyboardArrowDown, "Move down", tint = TextLo)
                }
                IconButton({ onRemove(i) }) {
                    Icon(Icons.Default.Close, "Remove", tint = TextLo)
                }
            }
        }
    }
}

@Composable
fun ExpandingPlayer(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
    ) { content() }
}
