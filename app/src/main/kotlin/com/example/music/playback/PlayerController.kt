package com.example.music.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.music.data.Song
import com.example.music.data.moved
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PlayerUiState(
    val current: Song? = null,
    val queue: List<Song> = emptyList(),
    val index: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
)

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

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()

        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            publish()
            item?.mediaId?.toLongOrNull()?.let { id -> songsById[id]?.let(onSongStarted) }
        }
    }

    fun connect() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            controller = future.get().apply { addListener(listener) }
            publish()
        }, MoreExecutors.directExecutor())

        // Position is not an event, so it has to be polled. 500ms is under the eye's threshold for
        // a progress bar and costs nothing measurable.
        scope.launch {
            while (true) {
                delay(500)
                if (_state.value.isPlaying) publish()
            }
        }
    }

    fun setLibrary(songs: List<Song>) {
        songsById = songs.associateBy { it.id }
        publish()
    }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    private fun publish() {
        val c = controller ?: return
        val queue = (0 until c.mediaItemCount).mapNotNull { i ->
            songsById[c.getMediaItemAt(i).mediaId.toLongOrNull() ?: -1L]
        }
        _state.value = PlayerUiState(
            current = queue.getOrNull(c.currentMediaItemIndex),
            queue = queue,
            index = c.currentMediaItemIndex,
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.coerceAtLeast(0),
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
        )
    }

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

    fun seekTo(ms: Long) = controller?.seekTo(ms)
    fun seekToIndex(i: Int) = controller?.seekTo(i, 0)

    fun toggleShuffle() = controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }

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
