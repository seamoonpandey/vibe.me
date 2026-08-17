package me.vibe.playback

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import me.vibe.data.Song
import me.vibe.data.moved
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class PlayerUiState(
    val current: Song? = null,
    val queue: List<Song> = emptyList(),
    val index: Int = 0,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
)

/**
 * Playback position as an anchor plus the clock, rather than a number that has to be re-read.
 *
 * Polling the controller twice a second put a new [PlayerUiState] into the tree 2× a second, which
 * recomposed the whole scaffold for a value only the seek bar cares about — and still drew the
 * thumb in 500ms steps, which is exactly the imprecision you could see. An anchor changes only when
 * playback genuinely jumps, so the UI can interpolate against the frame clock and be right to the
 * millisecond in between.
 */
data class Progress(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val anchorRealtimeMs: Long = 0,
    val playing: Boolean = false,
    val speed: Float = 1f,
) {
    fun at(realtimeMs: Long): Long {
        if (!playing) return positionMs
        val elapsed = ((realtimeMs - anchorRealtimeMs) * speed).toLong()
        val p = positionMs + elapsed
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

/**
 * The UI's only view of playback. Connects a MediaController once and republishes it as state, so
 * no composable ever touches ExoPlayer and playback keeps running when the UI goes away.
 */
class PlayerController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onSongStarted: (Song) -> Unit,
) {
    private var controller: MediaController? = null
    private var songsById = emptyMap<Long, Song>()

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress

    // The queue only changes when the timeline does. Position updates arrive twice a second, and
    // rebuilding 200-odd items each time was pure waste.
    private var cachedQueue = emptyList<Song>()
    private var queueDirty = true

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            queueDirty = true
            publish()
        }

        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            publish()
            item?.mediaId?.toLongOrNull()?.let { id -> songsById[id]?.let(onSongStarted) }
        }
    }

    private var connecting = false

    // The anchor is re-taken on every player event, which covers every way the position can jump.
    // Between those it only has to survive drift between the audio clock and elapsedRealtime, which
    // over a track is well under a frame — so this beat is insurance, not the mechanism, and it runs
    // at a fifth of a hertz instead of two. Only while somebody is watching: collectAsStateWithLifecycle
    // drops its subscription in the background, so this stops dead rather than waking a dozing CPU.
    init {
        scope.launch {
            combine(_progress.subscriptionCount, _progress) { watchers, p -> watchers > 0 && p.playing }
                .distinctUntilChanged()
                .collectLatest { ticking ->
                    // Re-anchor first, then wait. Coming back from the background otherwise leaves
                    // an anchor taken minutes ago, and interpolating from that puts the thumb at
                    // the end of the track for as long as the delay lasts.
                    while (ticking) {
                        publishProgress()
                        delay(5000)
                    }
                }
        }
    }

    fun connect() {
        // The controller is a process-wide singleton but callers are not; a second connect would
        // leak a controller and double every listener callback.
        if (connecting || controller != null) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            controller = future.get().apply { addListener(listener) }
            publish()
        }, MoreExecutors.directExecutor())
    }

    fun setLibrary(songs: List<Song>) {
        songsById = songs.associateBy { it.id }
        queueDirty = true
        publish()
    }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        // Without this a second connect() — finishing the activity and opening it again inside one
        // process — would return early on the guard and leave the app with no player at all.
        connecting = false
    }

    private fun publish() {
        val c = controller ?: return
        if (queueDirty || cachedQueue.size != c.mediaItemCount) {
            cachedQueue = (0 until c.mediaItemCount).mapNotNull { i ->
                songsById[c.getMediaItemAt(i).mediaId.toLongOrNull() ?: -1L]
            }
            queueDirty = false
        }
        val queue = cachedQueue
        _state.value = PlayerUiState(
            current = queue.getOrNull(c.currentMediaItemIndex),
            queue = queue,
            index = c.currentMediaItemIndex,
            isPlaying = c.isPlaying,
            durationMs = c.duration.coerceAtLeast(0),
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
        )
        publishProgress()
    }

    private fun publishProgress() {
        val c = controller ?: return
        _progress.value = Progress(
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.coerceAtLeast(0),
            anchorRealtimeMs = SystemClock.elapsedRealtime(),
            playing = c.isPlaying,
            speed = c.playbackParameters.speed,
        )
    }

    /** The live position, for checkpointing. Reading the controller beats trusting an interpolation. */
    fun positionMs(): Long = controller?.currentPosition?.coerceAtLeast(0) ?: _progress.value.positionMs

    // --- commands ---

    fun play(songs: List<Song>, startIndex: Int = 0, positionMs: Long = 0, autoPlay: Boolean = true) {
        val c = controller ?: return
        c.setMediaItems(songs.map(::toItem), startIndex.coerceIn(0, maxOf(songs.lastIndex, 0)), positionMs)
        c.prepare()
        c.playWhenReady = autoPlay
    }

    fun playPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = controller?.seekToNextMediaItem()
    fun previous() {
        val c = controller ?: return
        // Match every other player: restart the track unless we are near its start.
        if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    // Re-anchor on the spot rather than waiting for the event to come back over the binder — a seek
    // that takes a frame or two to show up under the thumb is the whole "imprecise" feeling.
    fun seekTo(ms: Long) {
        controller?.seekTo(ms.coerceAtLeast(0))
        publishProgress()
    }

    fun seekToIndex(i: Int) {
        controller?.seekTo(i, 0)
        publishProgress()
    }

    fun toggleShuffle() = controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    /** Reapply the modes the user last chose, on restore. */
    fun setModes(shuffle: Boolean, repeatMode: Int) = controller?.let {
        it.shuffleModeEnabled = shuffle
        it.repeatMode = repeatMode
    }

    fun cycleRepeat() = controller?.let {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun addToQueue(songs: List<Song>) = controller?.addMediaItems(songs.map(::toItem))

    fun playNext(songs: List<Song>) {
        val c = controller ?: return
        c.addMediaItems(c.currentMediaItemIndex + 1, songs.map(::toItem))
    }

    fun removeFromQueue(index: Int) = controller?.removeMediaItem(index)

    fun moveInQueue(from: Int, to: Int) {
        controller?.moveMediaItem(from, to)
        // Keep the visible list in step immediately; the player event follows.
        cachedQueue = cachedQueue.moved(from, to)
        _state.value = _state.value.let { it.copy(queue = it.queue.moved(from, to)) }
    }

    fun stop() = controller?.stop()

    private fun toItem(song: Song) = MediaItem.Builder()
        .setMediaId(song.id.toString())
        .setUri(song.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setArtworkUri(song.artUri)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build(),
        )
        .build()
}
