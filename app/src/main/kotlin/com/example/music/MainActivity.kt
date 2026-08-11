package com.example.music

import android.Manifest
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.example.music.data.Album
import com.example.music.data.Artist
import com.example.music.data.Playlist
import com.example.music.data.Song
import com.example.music.data.Tags
import com.example.music.ui.AddToPlaylistDialog
import com.example.music.ui.DefaultAccent
import com.example.music.ui.DetailScreen
import com.example.music.ui.ExpandingPlayer
import com.example.music.ui.Ink
import com.example.music.ui.LibraryScreen
import com.example.music.ui.MiniPlayer
import com.example.music.ui.MusicTheme
import com.example.music.ui.MusicViewModel
import com.example.music.ui.NowPlayingScreen
import com.example.music.ui.PlaylistsScreen
import com.example.music.ui.SearchScreen
import com.example.music.ui.SettingsScreen
import com.example.music.ui.Surface1
import com.example.music.ui.TagEditorDialog
import com.example.music.ui.TextHi
import com.example.music.ui.TextLo
import com.example.music.ui.accentFrom
import kotlinx.coroutines.launch

/** Screen graph. Seven destinations do not need a navigation library. */
private sealed interface Screen {
    data object Library : Screen
    data object Playlists : Screen
    data object Settings : Screen
    data object Search : Screen
    data class AlbumDetail(val album: Album) : Screen
    data class ArtistDetail(val artist: Artist) : Screen
    data class PlaylistDetail(val id: Long) : Screen
    data object Favorites : Screen
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Keep the service alive across the activity's own lifecycle so playback never blips.
        startService(Intent(this, com.example.music.playback.PlaybackService::class.java))
        setContent { Root() }
    }

    override fun onStop() {
        super.onStop()
        // Cheapest moment to checkpoint the queue; the process may not get another one.
        (Deps.player.state.value.queue.isNotEmpty()).let {
            if (it) Deps.scope.launch {
                val s = Deps.player.state.value
                Deps.userData.saveQueue(s.queue.map { song -> song.id }, s.index, s.positionMs)
            }
        }
    }
}

@Composable
private fun Root() {
    val vm: MusicViewModel = viewModel()
    val library by vm.libraryState.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    val playback by vm.playerState.collectAsStateWithLifecycle()

    var granted by remember { mutableStateOf(false) }
    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val context = LocalContext.current
    val audioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { ok ->
        granted = ok
        if (ok) vm.onPermissionGranted()
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        val already = context.checkSelfPermission(permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (already) {
            granted = true
            vm.onPermissionGranted()
        } else {
            audioLauncher.launch(permission)
        }
        // Without this the media notification silently never appears on Android 13+.
        if (Build.VERSION.SDK_INT >= 33) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Accent follows the artwork of whatever is playing.
    var accent by remember { mutableStateOf(DefaultAccent) }
    val currentArt = playback.current?.artUri
    LaunchedEffect(currentArt) {
        accent = currentArt?.let { uri ->
            val result = ImageLoader(context).execute(
                ImageRequest.Builder(context).data(uri).allowHardware(false).build(),
            )
            result.image?.toBitmap()?.let(::accentFrom)
        } ?: DefaultAccent
    }

    MusicTheme(accent) {
        if (!granted) {
            PermissionGate { audioLauncher.launch(permission) }
        } else {
            AppScaffold(vm, library, user, playback)
        }
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Music needs access to the audio on this device.", color = TextHi)
            Button(onClick = onRequest, modifier = Modifier.padding(top = 16.dp)) {
                Text("Grant access")
            }
        }
    }
}

@Composable
private fun AppScaffold(
    vm: MusicViewModel,
    library: com.example.music.ui.LibraryState,
    user: com.example.music.data.UserState,
    playback: com.example.music.playback.PlayerUiState,
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Library) }
    var expanded by remember { mutableStateOf(false) }
    var addTo by remember { mutableStateOf<List<Long>?>(null) }
    var editing by remember { mutableStateOf<Song?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val writeRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { }

    val songsById = remember(library.songs) { library.songs.associateBy { it.id } }

    fun play(songs: List<Song>, index: Int) {
        vm.player.play(songs, index)
        vm.saveQueue()
    }

    Scaffold(
        containerColor = Ink,
        bottomBar = {
            Column {
                MiniPlayer(
                    state = playback,
                    onExpand = { expanded = true },
                    onPlayPause = vm.player::playPause,
                    onNext = { vm.player.next() },
                )
                NavigationBar(containerColor = Surface1) {
                    NavBarItem(screen is Screen.Library || screen is Screen.AlbumDetail ||
                        screen is Screen.ArtistDetail, Icons.Default.LibraryMusic, "Library") {
                        screen = Screen.Library
                    }
                    NavBarItem(screen is Screen.Playlists || screen is Screen.PlaylistDetail ||
                        screen is Screen.Favorites, Icons.AutoMirrored.Filled.QueueMusic, "Playlists") {
                        screen = Screen.Playlists
                    }
                    NavBarItem(screen is Screen.Settings, Icons.Default.Settings, "Settings") {
                        screen = Screen.Settings
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            when (val s = screen) {
                Screen.Library -> LibraryScreen(
                    state = library,
                    sortByTab = user.sortBy,
                    playCounts = user.playCounts,
                    currentSongId = playback.current?.id,
                    contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
                    onPlaySongs = ::play,
                    onOpenAlbum = { screen = Screen.AlbumDetail(it) },
                    onOpenArtist = { screen = Screen.ArtistDetail(it) },
                    onSortChange = vm::setSort,
                    onSearch = { screen = Screen.Search },
                )

                Screen.Playlists -> PlaylistsScreen(
                    playlists = user.playlists,
                    favoriteCount = user.favorites.size,
                    contentPadding = padding,
                    onOpen = { screen = Screen.PlaylistDetail(it.id) },
                    onOpenFavorites = { screen = Screen.Favorites },
                    onCreate = vm::createPlaylist,
                    onRename = vm::renamePlaylist,
                    onDelete = vm::deletePlaylist,
                )

                Screen.Settings -> SettingsScreen(user, padding, vm::updateSettings)

                Screen.Search -> SearchScreen(
                    songs = library.songs,
                    contentPadding = padding,
                    currentSongId = playback.current?.id,
                    onBack = { screen = Screen.Library },
                    onPlay = ::play,
                )

                is Screen.AlbumDetail -> DetailScreen(
                    title = s.album.title,
                    subtitle = s.album.artist,
                    songs = s.album.songs,
                    contentPadding = padding,
                    currentSongId = playback.current?.id,
                    onBack = { screen = Screen.Library },
                    onPlay = { i -> play(s.album.songs, i) },
                    onShuffle = { play(s.album.songs.shuffled(), 0) },
                    onSongMenu = { song, _ -> addTo = listOf(song.id) },
                )

                is Screen.ArtistDetail -> DetailScreen(
                    title = s.artist.name,
                    subtitle = "${s.artist.albumCount} albums",
                    songs = s.artist.songs,
                    contentPadding = padding,
                    currentSongId = playback.current?.id,
                    roundArt = true,
                    numbered = false,
                    onBack = { screen = Screen.Library },
                    onPlay = { i -> play(s.artist.songs, i) },
                    onShuffle = { play(s.artist.songs.shuffled(), 0) },
                    onSongMenu = { song, _ -> addTo = listOf(song.id) },
                )

                is Screen.PlaylistDetail -> {
                    val playlist = user.playlists.firstOrNull { it.id == s.id }
                    val songs = playlist?.songIds?.mapNotNull(songsById::get).orEmpty()
                    DetailScreen(
                        title = playlist?.name ?: "Playlist",
                        subtitle = "Playlist",
                        songs = songs,
                        contentPadding = padding,
                        currentSongId = playback.current?.id,
                        onBack = { screen = Screen.Playlists },
                        onPlay = { i -> play(songs, i) },
                        onShuffle = { play(songs.shuffled(), 0) },
                        onSongMenu = { _, i -> vm.removeFromPlaylist(s.id, i) },
                    )
                }

                Screen.Favorites -> {
                    val songs = user.favorites.mapNotNull(songsById::get)
                    DetailScreen(
                        title = "Favorites",
                        subtitle = "Playlist",
                        songs = songs,
                        contentPadding = padding,
                        currentSongId = playback.current?.id,
                        onBack = { screen = Screen.Playlists },
                        onPlay = { i -> play(songs, i) },
                        onShuffle = { play(songs.shuffled(), 0) },
                        onSongMenu = { song, _ -> vm.toggleFavorite(song.id) },
                    )
                }
            }

            ExpandingPlayer(expanded) {
                NowPlayingScreen(
                    state = playback,
                    isFavorite = playback.current?.id in user.favorites,
                    onCollapse = { expanded = false },
                    onPlayPause = vm.player::playPause,
                    onNext = { vm.player.next() },
                    onPrevious = { vm.player.previous() },
                    onSeek = { vm.player.seekTo(it) },
                    onShuffle = { vm.player.toggleShuffle() },
                    onRepeat = { vm.player.cycleRepeat() },
                    onFavorite = { playback.current?.let { vm.toggleFavorite(it.id) } },
                    onQueueSelect = { vm.player.seekToIndex(it) },
                    onQueueRemove = { vm.player.removeFromQueue(it) },
                    onQueueMove = { from, to -> vm.player.moveInQueue(from, to) },
                    onEditTags = { editing = it },
                )
            }
        }
    }

    addTo?.let { ids ->
        AddToPlaylistDialog(
            playlists = user.playlists,
            onDismiss = { addTo = null },
            onPick = { vm.addToPlaylist(it, ids) },
            onCreate = { name -> vm.createPlaylist(name) },
        )
    }

    editing?.let { song ->
        TagEditorDialog(
            song = song,
            load = { Deps.tagWriter.read(it) },
            onDismiss = { editing = null },
            onSave = { tags: Tags ->
                editing = null
                scope.launch {
                    // Android only reveals that consent is required by refusing the write once.
                    Deps.tagWriter.write(song, tags)?.let { sender: IntentSender ->
                        writeRequest.launch(IntentSenderRequest.Builder(sender).build())
                    }
                }
            },
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavBarItem(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, label) },
        label = { Text(label) },
        colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
            selectedIconColor = Color.Black,
            selectedTextColor = TextHi,
            unselectedIconColor = TextLo,
            unselectedTextColor = TextLo,
        ),
    )
}
