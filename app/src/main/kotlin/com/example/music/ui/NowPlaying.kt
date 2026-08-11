package com.example.music.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.example.music.data.Song
import com.example.music.data.formatDuration
import com.example.music.playback.PlayerUiState
import kotlin.math.roundToInt

/**
 * The bar above the tab bar. A rounded floating card rather than a full-width strip, so the list
 * visibly continues underneath it and it reads as a control rather than a second toolbar.
 */
@Composable
fun MiniPlayer(state: PlayerUiState, onExpand: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit) {
    val song = state.current ?: return
    Column(
        Modifier
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface2)
            .clickable(onClick = onExpand),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(song.artUri, song.title, Modifier.size(42.dp), corner = 8)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    song.title, style = MaterialTheme.typography.titleMedium, color = TextHi,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song.artist, style = MaterialTheme.typography.bodySmall, color = TextLo,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
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
        LinearProgressIndicator(
            progress = { if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = LocalAccent.current,
            trackColor = Hairline,
        )
    }
}

/**
 * Full-screen now playing.
 *
 * It is a sibling of the Scaffold rather than a child, so it covers the tab bar the way a real
 * player screen does. Three ways out, because one is never enough: the chevron, the system back
 * gesture, and dragging it down — the last being what people actually reach for.
 */
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
    onMenu: (Song) -> Unit,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
) {
    val song = state.current ?: return
    var showQueue by remember { mutableStateOf(false) }
    val accent = LocalArtAccent.current

    // Drag-to-dismiss: the screen follows the finger and springs back if the throw was too small.
    var dragY by remember { mutableFloatStateOf(0f) }
    val offsetY by animateFloatAsState(dragY, label = "dismissDrag")

    Column(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, offsetY.roundToInt()) }
            // Opaque base first: the gradient's top stop is translucent, and without this the
            // library screen shows straight through the player.
            .background(Ink)
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.30f), Ink, Ink)))
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { if (dragY > 220f) onCollapse() else dragY = 0f },
                    onDragCancel = { dragY = 0f },
                    onVerticalDrag = { _, amount -> dragY = (dragY + amount).coerceAtLeast(0f) },
                )
            }
            .padding(horizontal = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onCollapse) {
                Icon(Icons.Default.KeyboardArrowDown, "Close player", Modifier.size(30.dp), tint = TextHi)
            }
            Text(
                "NOW PLAYING",
                style = MaterialTheme.typography.labelLarge,
                color = TextLo,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton({ onMenu(song) }) {
                Icon(Icons.Default.MoreVert, "Track options", tint = TextHi)
            }
        }

        if (showQueue) {
            QueueList(state, Modifier.weight(1f), onQueueSelect, onQueueRemove, onQueueMove)
        } else {
            ArtworkPager(state, Modifier.weight(1f), onQueueSelect)
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 26.dp, bottom = 4.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    song.title, style = MaterialTheme.typography.headlineSmall, color = TextHi,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song.artist, style = MaterialTheme.typography.bodyMedium, color = TextLo,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onFavorite) {
                Icon(
                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) accent else TextLo,
                )
            }
        }

        // The thumb follows the finger while held, so it never fights the position poll.
        var scrubbing by remember { mutableStateOf<Float?>(null) }
        val progress = scrubbing
            ?: if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f

        SeekBar(
            progress = progress.coerceIn(0f, 1f),
            accent = accent,
            onScrub = { scrubbing = it },
            onCommit = {
                onSeek((it * state.durationMs).toLong())
                scrubbing = null
            },
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(formatDuration((progress * state.durationMs).toLong()), style = Numeric, color = TextLo)
            Spacer(Modifier.weight(1f))
            Text(formatDuration(state.durationMs), style = Numeric, color = TextLo)
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onShuffle) {
                Icon(Icons.Default.Shuffle, "Shuffle", tint = if (state.shuffle) accent else TextLo)
            }
            IconButton(onPrevious) {
                Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(38.dp), tint = TextHi)
            }
            Box(
                Modifier.size(70.dp).clip(CircleShape).background(accent).clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (state.isPlaying) "Pause" else "Play",
                    Modifier.size(34.dp),
                    tint = OnAccent,
                )
            }
            IconButton(onNext) {
                Icon(Icons.Default.SkipNext, "Next", Modifier.size(38.dp), tint = TextHi)
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

        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpeedPill(speed, onSpeedChange)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { showQueue = !showQueue }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, null, Modifier.size(17.dp), tint = TextLo)
                Text(
                    if (showQueue) "  Hide queue" else "  Queue · ${state.queue.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextLo,
                )
            }
        }
    }
}

/**
 * Swipe the artwork left or right to move through the queue.
 *
 * A pager rather than a swipe threshold, so the art actually tracks the finger and the fling
 * physics are the platform's. The two effects below keep it in step with the player in both
 * directions — the page follows a track change from the notification or a headset button, and
 * settling on a new page tells the player to go there. Each guards on inequality, or they would
 * chase each other forever.
 */
@Composable
private fun ArtworkPager(state: PlayerUiState, modifier: Modifier, onQueueSelect: (Int) -> Unit) {
    if (state.queue.isEmpty()) return
    val pager = rememberPagerState(
        initialPage = state.index.coerceIn(0, state.queue.lastIndex),
        pageCount = { state.queue.size },
    )

    LaunchedEffect(state.index) {
        if (state.index in state.queue.indices && pager.currentPage != state.index) {
            pager.animateScrollToPage(state.index)
        }
    }
    LaunchedEffect(pager.settledPage) {
        if (pager.settledPage != state.index && pager.settledPage in state.queue.indices) {
            onQueueSelect(pager.settledPage)
        }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        HorizontalPager(
            state = pager,
            pageSpacing = 16.dp,
            contentPadding = PaddingValues(horizontal = 26.dp),
        ) { page ->
            val item = state.queue[page]
            Artwork(
                item.artUri, item.title,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shadow(24.dp, RoundedCornerShape(18.dp), clip = false),
                corner = 18,
            )
        }
    }
}

/**
 * Playback speed, as a pill that cycles rather than a slider or a menu.
 *
 * The useful speeds are a short list and people nudge between neighbours, so tapping through them
 * beats opening something. It shows the current rate at all times, and the accent only appears
 * when the speed is not 1x — so an accidental change is visible rather than silent.
 */
/**
 * A thin line and a small thumb. The whole row is the touch target even though the bar is 4dp,
 * so it is easy to hit without being visually heavy, and it can be tapped as well as dragged.
 */
@Composable
private fun SeekBar(
    progress: Float,
    accent: Color,
    onScrub: (Float) -> Unit,
    onCommit: (Float) -> Unit,
) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures { onCommit((it.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { onCommit(lastScrub) },
                    onDragCancel = { onCommit(lastScrub) },
                ) { change, _ ->
                    lastScrub = (change.position.x / size.width).coerceIn(0f, 1f)
                    onScrub(lastScrub)
                    change.consume()
                }
            },
    ) {
        val track = 4.dp.toPx()
        val y = size.height / 2f
        val played = size.width * progress
        drawLine(Hairline, Offset(0f, y), Offset(size.width, y), track, StrokeCap.Round)
        if (played > 0f) {
            drawLine(accent, Offset(0f, y), Offset(played, y), track, StrokeCap.Round)
        }
        drawCircle(accent, radius = 7.dp.toPx(), center = Offset(played, y))
    }
}

/** Last position reported by a drag, so releasing can commit it. */
private var lastScrub = 0f

@Composable
private fun SpeedPill(speed: Float, onSpeedChange: (Float) -> Unit) {
    val steps = remember { listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f) }
    val altered = kotlin.math.abs(speed - 1f) > 0.01f
    Text(
        text = if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x",
        style = MaterialTheme.typography.labelLarge,
        color = if (altered) OnAccent else TextLo,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (altered) LocalAccent.current else Surface2)
            .clickable {
                val next = steps[(steps.indexOfFirst { it >= speed - 0.01f }
                    .takeIf { it >= 0 } ?: 2).plus(1) % steps.size]
                onSpeedChange(next)
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
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
                    Icon(Icons.Default.Close, "Remove from queue", tint = TextLo)
                }
            }
        }
    }
}
