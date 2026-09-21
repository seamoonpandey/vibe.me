package me.vibe.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.vibe.Deps
import me.vibe.data.Album
import me.vibe.data.Artist
import me.vibe.data.Song
import me.vibe.data.SortSpec
import me.vibe.data.UserState
import me.vibe.data.remote.YouTube
import me.vibe.data.toAlbums
import me.vibe.data.toArtists
import me.vibe.playback.DownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

data class LibraryState(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val loaded: Boolean = false,
)

data class ExploreState(
    val query: String = "",
    val results: List<Song> = emptyList(),
    val loading: Boolean = false,
    /** Already a sentence fit to show the user; the mapping happens where the exception is. */
    val error: String? = null,
)

class MusicViewModel : ViewModel() {

    private val library = Deps.library
    private val userData = Deps.userData
    val player = Deps.player

    val libraryState: StateFlow<LibraryState> =
        combine(library.songs, library.loaded) { songs, loaded ->
            // Albums and artists are views over the same list, recomputed only when it changes.
            LibraryState(songs, songs.toAlbums(), songs.toArtists(), loaded)
        }
            // Grouping the whole library is real work; keep it off the frame.
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, LibraryState())

    // Read straight off the warmed source rather than re-derived here: the splash lifts on
    // UserData.loaded, which is set after this value, so the first frame the user sees already
    // carries the saved theme.
    val user: StateFlow<UserState> = userData.user

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

    // --- Explore ---

    private val query = MutableStateFlow("")

    /**
     * What the text field shows, undebounced.
     *
     * It must not read [explore], which is 400ms behind by design: a controlled field whose value
     * lags the keystroke snaps back to the stale text on the next recomposition, and the character
     * you typed disappears. The field tracks this; only the search waits.
     */
    val queryText: StateFlow<String> = query.asStateFlow()

    /**
     * Search results, debounced.
     *
     * `flatMapLatest` is what makes typing cheap: each new keystroke cancels the request in flight,
     * so a fast typist causes one search rather than one per letter, and a slow reply for "sno"
     * can never land on top of the results for "snowman".
     */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val explore: StateFlow<ExploreState> = query
        .debounce(400)
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(ExploreState(query = q))
            else flow {
                emit(ExploreState(query = q, loading = true))
                emit(
                    try {
                        Deps.startRemote()
                        ExploreState(query = q, results = YouTube.search(q))
                    } catch (t: Throwable) {
                        ExploreState(query = q, error = messageFor(t))
                    },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExploreState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun retrySearch() {
        val q = query.value
        query.value = ""
        query.value = q
    }

    fun download(song: Song) = DownloadService.enqueue(Deps.app, song)

    /** Stops a queued or in-flight transfer; the Downloads page is the only caller. */
    fun cancelDownload(videoId: String) = DownloadService.cancel(videoId)

    /**
     * Every failure gets its own sentence. An empty list is not an acceptable rendering of "you are
     * offline" or "YouTube changed shape" — the user can act on one of those and not the other.
     */
    private fun messageFor(t: Throwable): String = when {
        t is ReCaptchaException ->
            "YouTube is asking this device to prove it is a browser. Try again in a few minutes."
        t is UnknownHostException || t is java.net.ConnectException ->
            "No connection."
        t is SocketTimeoutException ->
            "YouTube did not answer in time. Try again."
        t is ExtractionException ->
            "Search failed. YouTube has probably changed and vibe.me needs an update."
        else -> t.message ?: "Something went wrong."
    }

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
        val position = player.positionMs()
        viewModelScope.launch {
            userData.saveQueue(s.queue.map { it.id }, s.index, position, s.shuffle, s.repeatMode)
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
