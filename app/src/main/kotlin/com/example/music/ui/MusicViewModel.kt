package com.example.music.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.music.Deps
import com.example.music.data.Album
import com.example.music.data.Artist
import com.example.music.data.Song
import com.example.music.data.SortSpec
import com.example.music.data.UserState
import com.example.music.data.toAlbums
import com.example.music.data.toArtists
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryState(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val loaded: Boolean = false,
)

class MusicViewModel : ViewModel() {

    private val library = Deps.library
    private val userData = Deps.userData
    val player = Deps.player

    val libraryState: StateFlow<LibraryState> =
        combine(library.songs, library.loaded) { songs, loaded ->
            // Albums and artists are views over the same list, recomputed only when it changes.
            LibraryState(songs, songs.toAlbums(), songs.toArtists(), loaded)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, LibraryState())

    val user: StateFlow<UserState> =
        userData.state.stateIn(viewModelScope, SharingStarted.Eagerly, UserState())

    val playerState = player.state

    private var queueRestored = false

    init {
        player.connect()
        viewModelScope.launch {
            libraryState.map { it.songs }.collect { songs ->
                player.setLibrary(songs)
                if (songs.isNotEmpty()) restoreQueueOnce(songs)
            }
        }
    }

    fun onPermissionGranted() = library.start()

    /** Bring back the previous session's queue, paused, so the app opens where it was left. */
    private fun restoreQueueOnce(songs: List<Song>) {
        if (queueRestored) return
        queueRestored = true
        viewModelScope.launch {
            // Not user.value: that StateFlow still holds its placeholder until DataStore's first
            // read lands, and the library usually wins that race. first() waits for the real one.
            val saved = userData.state.first()
            if (saved.queue.isEmpty()) return@launch
            val byId = songs.associateBy { it.id }
            val queue = saved.queue.mapNotNull(byId::get)
            if (queue.isEmpty()) return@launch
            player.play(
                songs = queue,
                startIndex = saved.queueIndex.coerceIn(0, queue.lastIndex),
                positionMs = saved.queuePositionMs,
                autoPlay = false,
            )
            player.setModes(saved.shuffle, saved.repeatMode)
        }
    }

    fun saveQueue() {
        val s = playerState.value
        if (s.queue.isEmpty()) return
        viewModelScope.launch {
            userData.saveQueue(s.queue.map { it.id }, s.index, s.positionMs, s.shuffle, s.repeatMode)
        }
    }

    /**
     * Mode changes checkpoint immediately. Relying on onStop would lose them whenever the process
     * is killed outright rather than backgrounded.
     */
    fun toggleShuffle() {
        player.toggleShuffle()
        saveQueue()
    }

    fun cycleRepeat() {
        player.cycleRepeat()
        saveQueue()
    }

    // --- user data passthroughs; the UI has no business knowing about DataStore ---

    fun setSort(tab: String, spec: SortSpec) = viewModelScope.launch { userData.setSort(tab, spec) }
    fun toggleFavorite(id: Long) = viewModelScope.launch { userData.toggleFavorite(id) }
    fun createPlaylist(name: String) = viewModelScope.launch { userData.createPlaylist(name) }
    fun renamePlaylist(id: Long, name: String) = viewModelScope.launch { userData.renamePlaylist(id, name) }
    fun deletePlaylist(id: Long) = viewModelScope.launch { userData.deletePlaylist(id) }
    fun addToPlaylist(id: Long, songIds: List<Long>) = viewModelScope.launch { userData.addToPlaylist(id, songIds) }
    fun removeFromPlaylist(id: Long, index: Int) = viewModelScope.launch { userData.removeFromPlaylist(id, index) }
    fun movePlaylistItem(id: Long, from: Int, to: Int) = viewModelScope.launch { userData.movePlaylistItem(id, from, to) }
    fun updateSettings(block: (UserState) -> UserState) = viewModelScope.launch { userData.edit(block) }

    override fun onCleared() {
        saveQueue()
        player.release()
        super.onCleared()
    }
}
