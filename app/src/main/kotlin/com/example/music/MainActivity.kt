package com.example.music

import android.Manifest
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.example.music.data.Album
import com.example.music.data.Artist
import com.example.music.data.SmartList
import com.example.music.data.Song
import com.example.music.data.smartListSongs
import com.example.music.data.Tags
import com.example.music.ui.AddToPlaylistDialog
import com.example.music.ui.DefaultAccent
import com.example.music.ui.DetailScreen
import com.example.music.ui.Ink
import com.example.music.ui.OnAccent
import com.example.music.ui.LocalAccent
import com.example.music.ui.LibraryScreen
import com.example.music.ui.MiniPlayer
import com.example.music.ui.MusicTheme
import com.example.music.ui.MusicViewModel
import com.example.music.ui.NowPlayingScreen
import com.example.music.ui.PlaylistsScreen
import com.example.music.ui.SearchScreen
import com.example.music.ui.SettingsScreen
import com.example.music.ui.SongMenuSheet
import com.example.music.ui.Surface1
import com.example.music.ui.TagEditorDialog
import com.example.music.ui.TextHi
import com.example.music.ui.TextLo
import com.example.music.ui.TrackInfoDialog
import com.example.music.ui.deleteSong
import com.example.music.ui.setAsRingtone
import com.example.music.ui.shareSong
import com.example.music.ui.accentFrom
import kotlinx.coroutines.launch

private fun audioPermission() =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

/** Screen graph. Eight destinations do not need a navigation library. */
private sealed interface Screen {
    data object Library : Screen
    data object Playlists : Screen
    data object Settings : Screen
    data object Search : Screen
    data class AlbumDetail(val album: Album) : Screen
    data class ArtistDetail(val artist: Artist) : Screen
    data class PlaylistDetail(val id: Long) : Screen
    data object Favorites : Screen
    data class Smart(val kind: SmartList) : Screen
}

private val Screen.isTopLevel: Boolean
    get() = this is Screen.Library || this is Screen.Playlists || this is Screen.Settings

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must precede super/setContentView so the system hands the splash over cleanly.
        val splash = installSplashScreen()

        // Begin the library read here rather than from a LaunchedEffect. The splash's condition is
        // first evaluated before Compose has run, so a read kicked off during composition has not
        // started yet and the splash lifts immediately - which is the empty list all over again.
        val granted = checkSelfPermission(audioPermission()) == PackageManager.PERMISSION_GRANTED
        if (granted) Deps.library.start()

        // Hold until the library is actually in hand, so nothing loads on the main screen. Bounded
        // two ways, because a splash that never lifts is worse than what it replaced: only when a
        // read is genuinely under way, and never past a few seconds.
        val startedAt = System.currentTimeMillis()
        splash.setKeepOnScreenCondition {
            granted && !Deps.library.loaded.value && System.currentTimeMillis() - startedAt < 8000
        }
        super.onCreate(savedInstanceState)
        // Cream ground needs dark system-bar icons; the default assumes a dark app.
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
            ),
        )
        startService(Intent(this, com.example.music.playback.PlaybackService::class.java))
        setContent { Root() }
    }

    override fun onStop() {
        super.onStop()
        // Cheapest moment to checkpoint the queue; the process may not get another one.
        val s = Deps.player.state.value
        if (s.queue.isNotEmpty()) {
            Deps.scope.launch {
                Deps.userData.saveQueue(s.queue.map { it.id }, s.index, s.positionMs, s.shuffle, s.repeatMode)
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
    val permission = audioPermission()

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

    var accent by remember { mutableStateOf(DefaultAccent) }
    val currentArt = playback.current?.artUri
    LaunchedEffect(currentArt) {
        accent = currentArt?.let { uri ->
            val result = Deps.images.execute(
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var menuFor by remember { mutableStateOf<Song?>(null) }
    var infoFor by remember { mutableStateOf<Song?>(null) }
    var sortSheet by remember { mutableStateOf(false) }
    var creatingPlaylist by remember { mutableStateOf(false) }
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

    val title = when (val s = screen) {
        Screen.Library -> "Library"
        Screen.Playlists -> "Playlists"
        Screen.Settings -> "Settings"
        Screen.Favorites -> "Favorites"
        is Screen.Smart -> s.kind.label
        Screen.Search -> "Search"
        is Screen.AlbumDetail -> s.album.title
        is Screen.ArtistDetail -> s.artist.name
        is Screen.PlaylistDetail -> user.playlists.firstOrNull { it.id == s.id }?.name ?: "Playlist"
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = Ink,
        // The top bar owns the status-bar inset. Hand-rolling this header is what previously left
        // the sort and search controls underneath the status bar, where taps never reached them.
        topBar = {
            if (screen != Screen.Search) {
                TopAppBar(
                    title = {
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextHi,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        if (!screen.isTopLevel) {
                            IconButton({ screen = backTargetFor(screen) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextHi)
                            }
                        }
                    },
                    actions = {
                        when (screen) {
                            Screen.Library -> IconButton({ screen = Screen.Search }) {
                                Icon(Icons.Default.Search, "Search", tint = TextHi)
                            }
                            Screen.Playlists -> IconButton({ creatingPlaylist = true }) {
                                Icon(Icons.Default.Add, "New playlist", tint = TextHi)
                            }
                            else -> Unit
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink),
                )
            }
        },
        bottomBar = {
            // Opaque: the mini player is an inset card, and without this the list scrolls
            // visibly through the margin around it.
            Column(Modifier.background(Ink)) {
                MiniPlayer(
                    state = playback,
                    onExpand = { expanded = true },
                    onPlayPause = vm.player::playPause,
                    onNext = { vm.player.next() },
                )
                NavigationBar(containerColor = Surface1, tonalElevation = 0.dp) {
                    NavItem(screen is Screen.Library, Icons.Default.LibraryMusic, "Library") {
                        screen = Screen.Library
                    }
                    NavItem(
                        screen is Screen.Playlists || screen is Screen.PlaylistDetail ||
                            screen is Screen.Favorites || screen is Screen.Smart,
                        Icons.AutoMirrored.Filled.QueueMusic, "Playlists",
                    ) { screen = Screen.Playlists }
                    NavItem(screen is Screen.Settings, Icons.Default.Settings, "Settings") {
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
                    contentPadding = padding,
                    sortSheetOpen = sortSheet,
                    onSortSheetOpen = { sortSheet = it },
                    onPlaySongs = ::play,
                    onOpenAlbum = { screen = Screen.AlbumDetail(it) },
                    onOpenArtist = { screen = Screen.ArtistDetail(it) },
                    onSortChange = vm::setSort,
                    onSongMenu = { menuFor = it },
                    onShuffleAll = { all -> if (all.isNotEmpty()) play(all.shuffled(), 0) },
                )

                Screen.Playlists -> PlaylistsScreen(
                    playlists = user.playlists,
                    favoriteCount = user.favorites.size,
                    contentPadding = padding,
                    creating = creatingPlaylist,
                    onCreatingChange = { creatingPlaylist = it },
                    onOpen = { screen = Screen.PlaylistDetail(it.id) },
                    onOpenFavorites = { screen = Screen.Favorites },
                    onOpenSmart = { screen = Screen.Smart(it) },
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
                    onSongMenu = { menuFor = it },
                )

                is Screen.AlbumDetail -> DetailScreen(
                    subtitle = "${s.album.songs.size} tracks",
                    songs = s.album.songs,
                    contentPadding = padding,
                    currentSongId = playback.current?.id,
                    onPlay = { i -> play(s.album.songs, i) },
                    onShuffle = { play(s.album.songs.shuffled(), 0) },
                    onSongMenu = { song, _ -> menuFor = song },
                )

                is Screen.ArtistDetail -> DetailScreen(
                    subtitle = "${s.artist.songs.size} tracks",
                    songs = s.artist.songs,
                    contentPadding = padding,
                    currentSongId = playback.current?.id,
                    roundArt = true,
                    onPlay = { i -> play(s.artist.songs, i) },
                    onShuffle = { play(s.artist.songs.shuffled(), 0) },
                    onSongMenu = { song, _ -> menuFor = song },
                )

                is Screen.PlaylistDetail -> {
                    val playlist = user.playlists.firstOrNull { it.id == s.id }
                    val songs = playlist?.songIds?.mapNotNull(songsById::get).orEmpty()
                    DetailScreen(
                        subtitle = "Playlist",
                        songs = songs,
                        contentPadding = padding,
                        currentSongId = playback.current?.id,
                        emptyMessage = "Nothing here yet. Add tracks from the library.",
                        onPlay = { i -> play(songs, i) },
                        onShuffle = { play(songs.shuffled(), 0) },
                        onSongMenu = { _, i -> vm.removeFromPlaylist(s.id, i) },
                    )
                }

                is Screen.Smart -> {
                    val songs = remember(library.songs, user.playCounts, user.lastPlayed, s.kind) {
                        smartListSongs(s.kind, library.songs, user.playCounts, user.lastPlayed)
                    }
                    DetailScreen(
                        subtitle = s.kind.blurb,
                        songs = songs,
                        contentPadding = padding,
                        currentSongId = playback.current?.id,
                        emptyMessage = "Play a few tracks and they will show up here.",
                        onPlay = { i -> play(songs, i) },
                        onShuffle = { play(songs.shuffled(), 0) },
                        onSongMenu = { song, _ -> menuFor = song },
                    )
                }

                Screen.Favorites -> {
                    val songs = user.favorites.mapNotNull(songsById::get)
                    DetailScreen(
                        subtitle = "Playlist",
                        songs = songs,
                        contentPadding = padding,
                        currentSongId = playback.current?.id,
                        emptyMessage = "Tap the heart on any track to save it here.",
                        onPlay = { i -> play(songs, i) },
                        onShuffle = { play(songs.shuffled(), 0) },
                        onSongMenu = { song, _ -> vm.toggleFavorite(song.id) },
                    )
                }
            }

        }
    }

    // Rendered outside the Scaffold so it covers the tab bar; a child could never do that.
    AnimatedVisibility(
        visible = expanded,
        enter = slideInVertically(animationSpec = tween(300)) { it },
        exit = slideOutVertically(animationSpec = tween(260)) { it },
    ) {
        NowPlayingScreen(
            state = playback,
            isFavorite = playback.current?.id in user.favorites,
            onCollapse = { expanded = false },
            onPlayPause = vm.player::playPause,
            onNext = { vm.player.next() },
            onPrevious = { vm.player.previous() },
            onSeek = { vm.player.seekTo(it) },
            onShuffle = vm::toggleShuffle,
            onRepeat = vm::cycleRepeat,
            onFavorite = { playback.current?.let { vm.toggleFavorite(it.id) } },
            onQueueSelect = { vm.player.seekToIndex(it) },
            onQueueRemove = { vm.player.removeFromQueue(it) },
            onQueueMove = { from, to -> vm.player.moveInQueue(from, to) },
            onMenu = { menuFor = it },
            speed = user.playbackSpeed,
            onSpeedChange = { v -> vm.updateSettings { it.copy(playbackSpeed = v) } },
        )
    }
    }

    // The back gesture should close the player before it leaves the app.
    BackHandler(enabled = expanded) { expanded = false }

    menuFor?.let { song ->
        SongMenuSheet(
            song = song,
            isFavorite = song.id in user.favorites,
            onDismiss = { menuFor = null },
            onPlayNext = { vm.player.playNext(listOf(song)) },
            onAddToQueue = { vm.player.addToQueue(listOf(song)) },
            onAddToPlaylist = { addTo = listOf(song.id) },
            onToggleFavorite = { vm.toggleFavorite(song.id) },
            onGoToAlbum = {
                library.albums.firstOrNull { it.id == song.albumId }?.let { screen = Screen.AlbumDetail(it) }
            },
            onGoToArtist = {
                library.artists.firstOrNull { it.name.equals(song.artist, true) }
                    ?.let { screen = Screen.ArtistDetail(it) }
            },
            onEditTags = { editing = song },
            onShare = { shareSong(context, song) },
            onRingtone = { setAsRingtone(context, song) },
            onInfo = { infoFor = song },
            onDelete = {
                deleteSong(context, song)?.let {
                    writeRequest.launch(IntentSenderRequest.Builder(it).build())
                }
            },
        )
    }

    infoFor?.let { song -> TrackInfoDialog(song) { infoFor = null } }

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

private fun backTargetFor(screen: Screen): Screen = when (screen) {
    is Screen.PlaylistDetail, Screen.Favorites, is Screen.Smart -> Screen.Playlists
    else -> Screen.Library
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, label, Modifier.size(21.dp)) },
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = LocalAccent.current,
            selectedTextColor = TextHi,
            // No pill: the accent icon is the selected state, which is quieter and denser.
            indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unselectedIconColor = TextLo,
            unselectedTextColor = TextLo,
        ),
    )
}
