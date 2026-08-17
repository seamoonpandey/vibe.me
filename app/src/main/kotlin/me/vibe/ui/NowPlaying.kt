package me.vibe.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import kotlinx.coroutines.flow.StateFlow
import me.vibe.data.Song
import me.vibe.data.formatDuration
import me.vibe.playback.PlayerUiState
import me.vibe.playback.Progress
import kotlin.math.roundToInt

/**
 * The playback position, advanced by the frame clock between anchors.
 *
 * The player hands out an anchor — a position and the moment it was true — instead of a number
 * re-read on a timer. Interpolating that against the frame clock gives a value good to the
 * millisecond and moving at the refresh rate, where the old half-second poll both stepped visibly
 * and could be up to half a second stale under the finger. Read the result inside a draw lambda and
 * nothing recomposes at all.
 */
@Composable
fun rememberPosition(progress: Progress): MutableLongState {
    val pos = remember { mutableLongStateOf(progress.at(SystemClock.elapsedRealtime())) }
    LaunchedEffect(progress) {
        pos.longValue = progress.at(SystemClock.elapsedRealtime())
        while (progress.playing) {
            withFrameMillis { pos.longValue = progress.at(SystemClock.elapsedRealtime()) }
        }
    }
    return pos
}

/**
 * Play and pause, crossfading and turning into one another rather than swapping between frames.
 * It is the control people press most, so it is the one worth animating.
 */
@Composable
private fun PlayGlyph(playing: Boolean, tint: Color, size: Dp = 24.dp) {
    AnimatedContent(
        targetState = playing,
        transitionSpec = {
            (fadeIn(tween(120)) + scaleIn(tween(160), initialScale = 0.72f))
                .togetherWith(fadeOut(tween(90)) + scaleOut(tween(160), targetScale = 0.72f))
        },
        label = "playPause",
    ) { isPlaying ->
        Icon(
            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            if (isPlaying) "Pause" else "Play",
            Modifier.size(size),
            tint = tint,
        )
    }
}

/**
 * The bar above the tab bar. A rounded floating card rather than a full-width strip, so the list
 * visibly continues underneath it and it reads as a control rather than a second toolbar.
 */
@Composable
fun MiniPlayer(
    state: PlayerUiState,
    progressFlow: StateFlow<Progress>,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val song = state.current ?: return
    // Collected here rather than passed in, so a new position anchor never reaches the scaffold.
    val progress by progressFlow.collectAsStateWithLifecycle()
    val position = rememberPosition(progress)
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
            IconButton(onPlayPause) { PlayGlyph(state.isPlaying, TextHi) }
            IconButton(onNext) { Icon(Icons.Default.SkipNext, "Next", tint = TextHi) }
        }
        // The lambda is read in the draw pass, so this line advances every frame without any of
        // the surrounding UI recomposing.
        LinearProgressIndicator(
            progress = {
                val d = progress.durationMs
                if (d > 0) position.longValue.toFloat() / d else 0f
            },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = LocalAccent.current,
            trackColor = Hairline,
            drawStopIndicator = {},
            gapSize = 0.dp,
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
    progressFlow: StateFlow<Progress>,
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
            IconButton(onRepeat) {
                Icon(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne
                    else Icons.Default.Repeat,
                    "Repeat",
                    tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) TextLo else accent,
                )
            }
        }

        // The thumb follows the finger while held, so it never fights the interpolated position.
        // -1 rather than null: this is read once per frame in a draw lambda, and a boxed Float
        // there is an allocation per frame for nothing.
        var scrubbing by remember { mutableFloatStateOf(-1f) }
        val progress by progressFlow.collectAsStateWithLifecycle()
        val position = rememberPosition(progress)
        val duration = progress.durationMs

        // Deferred, so the bar redraws each frame without recomposing this screen.
        val fraction = {
            val s = scrubbing
            if (s >= 0f) s
            else if (duration > 0) (position.longValue.toFloat() / duration).coerceIn(0f, 1f)
            else 0f
        }

        SeekBar(
            fraction = fraction,
            accent = accent,
            scrubbing = scrubbing >= 0f,
            onScrub = { scrubbing = it },
            onCommit = {
                onSeek((it * duration).toLong())
                scrubbing = -1f
            },
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            ElapsedLabel { (fraction() * duration).toLong() }
            Spacer(Modifier.weight(1f))
            Text(formatDuration(duration), style = Numeric, color = TextLo)
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
            // Presses on the main transport button are felt, not just registered: it gives under
            // the finger and springs back. No ripple — on a solid accent disc it only muddies it.
            val interactions = remember { MutableInteractionSource() }
            val pressed by interactions.collectIsPressedAsState()
            val press by animateFloatAsState(
                if (pressed) 0.90f else 1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
                label = "press",
            )
            Box(
                Modifier
                    .size(70.dp)
                    .graphicsLayer { scaleX = press; scaleY = press }
                    .clip(CircleShape)
                    .background(accent)
                    .clickable(interactions, indication = null, onClick = onPlayPause),
                contentAlignment = Alignment.Center,
            ) {
                PlayGlyph(state.isPlaying, OnAccent, 34.dp)
            }
            IconButton(onNext) {
                Icon(Icons.Default.SkipNext, "Next", Modifier.size(38.dp), tint = TextHi)
            }
            IconButton(onFavorite) {
                Icon(
                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) accent else TextLo,
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
            SpinningCover(
                song = state.queue[page],
                // Only the page you are actually on turns; neighbours sit still.
                spinning = state.isPlaying && page == pager.currentPage,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
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
 *
 * Precision comes from three things the old version got wrong. The drag now starts from the press,
 * so the thumb goes exactly where the finger landed instead of jumping by the touch slop on first
 * movement. Both gestures live in one `awaitPointerEventScope`, so a tap and a drag can no longer
 * be recognised by two competing detectors and commit different values. And the position under the
 * finger is measured against the drawn track — inset by the thumb radius at each end, since the
 * thumb cannot centre itself outside the bar — so the number committed is the one shown.
 */
@Composable
private fun SeekBar(
    fraction: () -> Float,
    accent: Color,
    scrubbing: Boolean,
    onScrub: (Float) -> Unit,
    onCommit: (Float) -> Unit,
) {
    // Read palette colours here: a Canvas draw lambda is not a composable scope.
    val track = Hairline
    val thumbRadius by animateFloatAsState(if (scrubbing) 10f else 7f, label = "thumb")

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(Unit) {
                val inset = 10.dp.toPx()
                fun at(x: Float) =
                    ((x - inset) / (size.width - inset * 2f).coerceAtLeast(1f)).coerceIn(0f, 1f)

                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // The press itself is a scrub. Everything downstream reads one value, so
                        // a tap and a drag that ends where it started commit the same place.
                        var value = at(down.position.x)
                        onScrub(value)
                        down.consume()
                        var pointer = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointer } ?: break
                            if (!change.pressed) break
                            if (change.position != change.previousPosition) {
                                value = at(change.position.x)
                                onScrub(value)
                            }
                            change.consume()
                            pointer = change.id
                        }
                        onCommit(value)
                    }
                }
            },
    ) {
        val thickness = 4.dp.toPx()
        val inset = 10.dp.toPx()
        val y = size.height / 2f
        val span = size.width - inset * 2f
        val played = inset + span * fraction().coerceIn(0f, 1f)
        drawLine(track, Offset(inset, y), Offset(inset + span, y), thickness, StrokeCap.Round)
        drawLine(accent, Offset(inset, y), Offset(played, y), thickness, StrokeCap.Round)
        drawCircle(accent, radius = thumbRadius.dp.toPx(), center = Offset(played, y))
    }
}

/**
 * The elapsed time. Fed a lambda rather than a value, so the per-frame position only forces a
 * recomposition when the second it rounds to actually changes — once a second, on one Text.
 */
@Composable
private fun ElapsedLabel(millis: () -> Long) {
    // rememberUpdatedState, not a bare capture: the lambda closes over the current duration, and a
    // derived state remembered once would keep reading the duration of the previous track.
    val latest by rememberUpdatedState(millis)
    val seconds by remember { derivedStateOf { latest() / 1000 } }
    Text(formatDuration(seconds * 1000), style = Numeric, color = TextLo)
}

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

/**
 * A record, turning while the music plays.
 *
 * The point of the detail is legibility of motion. A smooth disc looks identical at every angle,
 * so spinning it changes nothing you can see; grooves, a label edge and an off-centre highlight
 * give the eye features to track. The sheen deliberately does NOT rotate — the grooves passing
 * underneath a fixed glint is what sells it as turning rather than just being round.
 *
 * The angle is read inside graphicsLayer rather than passed to rotate(), so advancing it each
 * frame invalidates only the draw phase instead of recomposing the artwork sixty times a second.
 */
@Composable
private fun SpinningCover(song: Song, spinning: Boolean, modifier: Modifier = Modifier) {
    val angle = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(spinning) {
        var last = 0L
        while (spinning) {
            withFrameNanos { now ->
                // Real elapsed time, so speed does not depend on frame rate and pausing leaves
                // the record exactly where it stopped. ~12s a turn: visible, not frantic.
                if (last != 0L) {
                    angle.floatValue = (angle.floatValue + (now - last) / 1_000_000_000f * 30f) % 360f
                }
                last = now
            }
        }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        // Everything that turns, in one layer.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = angle.floatValue },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                drawCircle(VinylBody, radius = r)

                // Grooves. Spacing widens slightly outward, as on a real pressing.
                var groove = r * 0.99f
                var step = r * 0.012f
                while (groove > r * 0.46f) {
                    drawCircle(
                        color = VinylGroove,
                        radius = groove,
                        style = Stroke(width = size.minDimension * 0.0016f),
                    )
                    groove -= step
                    step *= 1.012f
                }

                // One faint seam from label to rim. Without a feature at a known angle the
                // grooves alone can still read as a static texture.
                drawLine(
                    color = VinylGroove,
                    start = Offset(center.x, center.y - r * 0.47f),
                    end = Offset(center.x, center.y - r * 0.99f),
                    strokeWidth = size.minDimension * 0.004f,
                )
            }

            // The label: artwork, sized and placed like a real one.
            Artwork(
                song.artUri, song.title,
                Modifier.fillMaxSize(0.46f).shadow(0.dp, CircleShape, clip = false),
                corner = 500,
            )
            Canvas(Modifier.fillMaxSize(0.46f)) {
                drawCircle(VinylGroove, radius = size.minDimension / 2f, style = Stroke(width = 3f))
            }
            // Spindle hole.
            Box(Modifier.fillMaxSize(0.055f).clip(CircleShape).background(Ink))
        }

        // Fixed glint. Stays put while the grooves turn under it.
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.16f),
                    0.45f to Color.Transparent,
                    0.78f to Color.Transparent,
                    1f to Color.White.copy(alpha = 0.07f),
                ),
                radius = size.minDimension / 2f,
            )
        }
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
                    Icon(Icons.Default.Close, "Remove from queue", tint = TextLo)
                }
            }
        }
    }
}
