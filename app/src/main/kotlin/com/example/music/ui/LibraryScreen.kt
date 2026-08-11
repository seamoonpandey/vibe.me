package com.example.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.music.data.Album
import com.example.music.data.Artist
import com.example.music.data.GroupKey
import com.example.music.data.Song
import com.example.music.data.SortKey
import com.example.music.data.SortSpec
import com.example.music.data.Tab as LibTab
import com.example.music.data.defaultDescending
import com.example.music.data.groupSongs

@Composable
fun LibraryScreen(
    state: LibraryState,
    sortByTab: Map<String, SortSpec>,
    playCounts: Map<Long, Int>,
    currentSongId: Long?,
    contentPadding: PaddingValues,
    sortSheetOpen: Boolean,
    onSortSheetOpen: (Boolean) -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenArtist: (Artist) -> Unit,
    onSortChange: (String, SortSpec) -> Unit,
    onSongMenu: (Song) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
) {
    var tab by remember { mutableStateOf(LibTab.SONGS) }
    val spec = sortByTab[tab.name] ?: SortSpec()

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        // Pills, not an underlined tab row: they read as filters over one library rather than
        // three separate places, which is what they actually are.
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibTab.entries.forEach { entry ->
                val selected = tab == entry
                Text(
                    entry.name.lowercase().replaceFirstChar(Char::uppercase),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) OnAccent else TextLo,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) LocalAccent.current else Surface2)
                        .clickable { tab = entry }
                        .padding(horizontal = 15.dp, vertical = 7.dp),
                )
            }
        }

        val count = when (tab) {
            LibTab.SONGS -> state.songs.size
            LibTab.ALBUMS -> state.albums.size
            LibTab.ARTISTS -> state.artists.size
        }
        SortBar(
            spec = spec,
            count = count,
            showGroup = tab == LibTab.SONGS,
            onShuffle = { onShuffleAll(state.songs) },
            onClick = { onSortSheetOpen(true) },
        )

        val listPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())
        when {
            state.songs.isEmpty() -> EmptyState(
                "No audio on this device",
                "Add music to your phone's storage and it will appear here.",
            )
            tab == LibTab.SONGS ->
                SongsTab(state.songs, spec, playCounts, currentSongId, listPadding, onPlaySongs, onSongMenu)
            tab == LibTab.ALBUMS -> AlbumsTab(state.albums, listPadding, onOpenAlbum)
            else -> ArtistsTab(state.artists, listPadding, onOpenArtist)
        }
    }

    if (sortSheetOpen) {
        SortSheet(
            spec = spec,
            groupingAllowed = tab == LibTab.SONGS,
            onDismiss = { onSortSheetOpen(false) },
            onChange = { onSortChange(tab.name, it) },
        )
    }
}

/**
 * The sort control is a bar, not an icon. Sorting is the only real structure an untagged library
 * has, so the current order is stated in words and is always one tap from being changed.
 */
@Composable
private fun SortBar(
    spec: SortSpec,
    count: Int,
    showGroup: Boolean,
    onShuffle: () -> Unit,
    onClick: () -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The order, stated plainly, and the one action worth putting beside it. The sort
            // glyph, the direction arrow and the track count were three more things to read
            // before you got to the music.
            Row(
                Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    spec.key.label() + if (showGroup && spec.group != GroupKey.NONE) {
                        " · ${spec.group.label().lowercase()}"
                    } else "",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextHi,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    if (spec.descending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    if (spec.descending) "Descending" else "Ascending",
                    Modifier.size(14.dp).padding(start = 2.dp),
                    tint = TextLo,
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(LocalAccent.current)
                    .clickable(onClick = onShuffle)
                    .padding(10.dp),
            ) {
                Icon(Icons.Default.Shuffle, "Shuffle everything", Modifier.size(18.dp), tint = OnAccent)
            }
        }
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
    onMenu: (Song) -> Unit,
) {
    // Grouping already sorts, so the flat list is exactly the play order the user sees.
    // Only key on play counts when they can actually affect the order. Otherwise finishing a
    // track re-sorted and re-grouped all 227 rows for nothing.
    val countsKey = if (spec.key == SortKey.PLAY_COUNT) playCounts else emptyMap()
    val groups = remember(songs, spec, countsKey) { groupSongs(songs, spec, countsKey) }
    val flat = remember(groups) { groups.flatMap { it.second } }
    val positions = remember(flat) { flat.withIndex().associate { (i, s) -> s.id to i } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        groups.forEach { (label, items) ->
            if (label.isNotEmpty()) {
                item(key = "h_$label", contentType = "header") { SectionHeader(label, items.size) }
            }
            items(items, key = { it.id }, contentType = { "song" }) { song ->
                SongRow(
                    modifier = Modifier.animateItem(),
                    song = song,
                    playing = song.id == currentSongId,
                    onClick = { onPlay(flat, positions[song.id] ?: 0) },
                    onMenu = { onMenu(song) },
                )
            }
        }
    }
}

@Composable
private fun AlbumsTab(albums: List<Album>, contentPadding: PaddingValues, onOpen: (Album) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(158.dp),
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
    ) {
        items(albums, key = { it.id }, contentType = { "album" }) { album ->
            GridCard(
                title = album.title,
                subtitle = "${album.songs.size} tracks",
                art = album.songs.first().artUri,
                seed = album.title,
                onClick = { onOpen(album) },
            )
        }
    }
}

@Composable
private fun ArtistsTab(artists: List<Artist>, contentPadding: PaddingValues, onOpen: (Artist) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp),
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
    ) {
        items(artists, key = { it.name }, contentType = { "artist" }) { artist ->
            GridCard(
                title = artist.name,
                subtitle = "${artist.songs.size} tracks",
                art = artist.songs.first().artUri,
                seed = artist.name,
                onClick = { onOpen(artist) },
                round = true,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(
    spec: SortSpec,
    groupingAllowed: Boolean,
    onDismiss: () -> Unit,
    onChange: (SortSpec) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface1) {
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Sort by", style = MaterialTheme.typography.labelLarge, color = TextLo)
            Chips(SortKey.entries.map { it to it.label() }, spec.key) {
                // Tapping the active key flips direction — the shortcut every list UI uses.
                onChange(
                    if (it == spec.key) spec.copy(descending = !spec.descending)
                    else spec.copy(key = it, descending = defaultDescending(it)),
                )
            }
            Text(
                if (spec.descending) "Largest or latest first" else "Smallest or earliest first",
                style = MaterialTheme.typography.bodySmall,
                color = TextLo,
            )

            if (groupingAllowed) {
                Text(
                    "Group into sections",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextLo,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Chips(GroupKey.entries.map { it to it.label() }, spec.group) {
                    onChange(spec.copy(group = it))
                }
            }
        }
    }
}

@Composable
private fun <T> Chips(options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onPick(value) },
                label = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Surface2,
                    labelColor = TextLo,
                    selectedContainerColor = LocalAccent.current,
                    selectedLabelColor = OnAccent,
                ),
                border = null,
            )
        }
    }
}

fun SortKey.label() = when (this) {
    SortKey.TITLE -> "Title"
    SortKey.ARTIST -> "Artist"
    SortKey.ALBUM -> "Album"
    SortKey.DURATION -> "Length"
    SortKey.YEAR -> "Year"
    SortKey.TRACK -> "Track number"
    SortKey.DATE_ADDED -> "Recently added"
    SortKey.PLAY_COUNT -> "Most played"
}

fun GroupKey.label() = when (this) {
    GroupKey.NONE -> "None"
    GroupKey.ALBUM -> "Album"
    GroupKey.ARTIST -> "Artist"
    GroupKey.FOLDER -> "Folder"
    GroupKey.YEAR -> "Year"
    GroupKey.LETTER -> "First letter"
}
