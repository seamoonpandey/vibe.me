package com.example.music.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.music.data.Album
import com.example.music.data.Artist
import com.example.music.data.GroupKey
import com.example.music.data.Song
import com.example.music.data.SortKey
import com.example.music.data.SortSpec
import com.example.music.data.Tab
import com.example.music.data.groupSongs

@Composable
fun LibraryScreen(
    state: LibraryState,
    sortByTab: Map<String, SortSpec>,
    playCounts: Map<Long, Int>,
    currentSongId: Long?,
    contentPadding: PaddingValues,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenArtist: (Artist) -> Unit,
    onSortChange: (String, SortSpec) -> Unit,
    onSearch: () -> Unit,
) {
    var tab by remember { mutableStateOf(Tab.SONGS) }
    var sortSheet by remember { mutableStateOf(false) }
    val spec = sortByTab[tab.name] ?: SortSpec()

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle("Library", Modifier.weight(1f))
            IconButton(onSearch) { Icon(Icons.Default.Search, "Search", tint = TextHi) }
            IconButton({ sortSheet = true }) {
                Icon(Icons.AutoMirrored.Filled.Sort, "Sort", tint = TextHi)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Tab.entries.forEach { entry ->
                FilterChip(
                    selected = tab == entry,
                    onClick = { tab = entry },
                    label = { Text(entry.name.lowercase().replaceFirstChar(Char::uppercase)) },
                )
            }
        }

        when (tab) {
            Tab.SONGS -> SongsTab(state.songs, spec, playCounts, currentSongId, contentPadding, onPlaySongs)
            Tab.ALBUMS -> AlbumsTab(state.albums, contentPadding, onOpenAlbum)
            Tab.ARTISTS -> ArtistsTab(state.artists, contentPadding, onOpenArtist)
        }
    }

    if (sortSheet) {
        SortSheet(
            spec = spec,
            onDismiss = { sortSheet = false },
            onChange = { onSortChange(tab.name, it) },
        )
    }
}

@Composable
private fun SongsTab(
    songs: List<Song>,
    spec: SortSpec,
    playCounts: Map<Long, Int>,
    currentSongId: Long?,
    contentPadding: PaddingValues,
    onPlay: (List<Song>, Int) -> Unit,
) {
    // Grouping already sorts, so the flat list below is the exact play order the user sees.
    val groups = remember(songs, spec, playCounts) { groupSongs(songs, spec, playCounts) }
    val flat = remember(groups) { groups.flatMap { it.second } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        groups.forEach { (label, items) ->
            if (label.isNotEmpty()) {
                item(key = "h_$label", contentType = "header") { SectionHeader(label) }
            }
            items(items, key = { it.id }, contentType = { "song" }) { song ->
                SongRow(
                    song = song,
                    highlighted = song.id == currentSongId,
                    onClick = { onPlay(flat, flat.indexOf(song)) },
                )
            }
        }
    }
}

@Composable
private fun AlbumsTab(albums: List<Album>, contentPadding: PaddingValues, onOpen: (Album) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
    ) {
        items(albums, key = { it.id }, contentType = { "album" }) { album ->
            GridCard(
                title = album.title,
                subtitle = album.artist,
                art = album.songs.first().artUri,
                onClick = { onOpen(album) },
            )
        }
    }
}

@Composable
private fun ArtistsTab(artists: List<Artist>, contentPadding: PaddingValues, onOpen: (Artist) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
    ) {
        items(artists, key = { it.name }, contentType = { "artist" }) { artist ->
            GridCard(
                title = artist.name,
                subtitle = "${artist.songs.size} songs · ${artist.albumCount} albums",
                art = artist.songs.first().artUri,
                onClick = { onOpen(artist) },
                round = true,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(spec: SortSpec, onDismiss: () -> Unit, onChange: (SortSpec) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface1) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sort by", style = MaterialTheme.typography.titleMedium, color = TextHi)
            ChipFlow(SortKey.entries.map { it to it.label() }, spec.key) {
                // Tapping the active key flips direction — the standard shortcut.
                onChange(if (it == spec.key) spec.copy(descending = !spec.descending) else spec.copy(key = it))
            }
            Text(
                if (spec.descending) "Descending" else "Ascending",
                style = MaterialTheme.typography.bodySmall,
                color = TextLo,
            )
            Text("Group by", style = MaterialTheme.typography.titleMedium, color = TextHi)
            ChipFlow(GroupKey.entries.map { it to it.label() }, spec.group) {
                onChange(spec.copy(group = it))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChipFlow(options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onPick(value) },
                label = { Text(label) },
            )
        }
    }
}

private fun SortKey.label() = when (this) {
    SortKey.TITLE -> "Title"
    SortKey.ARTIST -> "Artist"
    SortKey.ALBUM -> "Album"
    SortKey.DURATION -> "Duration"
    SortKey.YEAR -> "Year"
    SortKey.TRACK -> "Track"
    SortKey.DATE_ADDED -> "Date added"
    SortKey.PLAY_COUNT -> "Play count"
}

private fun GroupKey.label() = when (this) {
    GroupKey.NONE -> "None"
    GroupKey.ALBUM -> "Album"
    GroupKey.ARTIST -> "Artist"
    GroupKey.FOLDER -> "Folder"
    GroupKey.YEAR -> "Year"
    GroupKey.LETTER -> "A–Z"
}
