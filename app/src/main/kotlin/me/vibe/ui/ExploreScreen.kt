package me.vibe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.vibe.data.Song
import me.vibe.data.remote.DownloadState

/**
 * Search YouTube, play a result, keep a result.
 *
 * The rows are the same [SongRow] the library uses, on purpose: a track should not look like a
 * different kind of thing depending on whether you happen to own it yet.
 */
@Composable
fun ExploreScreen(
    /** Live, so the field keeps up with the keyboard. */
    query: String,
    /** Debounced, so results only chase a query the user has stopped typing. */
    state: ExploreState,
    downloads: Map<String, DownloadState>,
    ownedKeys: Set<String>,
    contentPadding: PaddingValues,
    currentSongId: Long?,
    playbackActive: Boolean,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onDownload: (Song) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text("Search YouTube") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )

        // A one-pixel bar rather than a spinner over the list: results stay readable while the next
        // search runs, which matters because the query changes on every keystroke. Also covers the
        // debounce gap, when the typed query and the shown results disagree.
        if (state.loading || (query.isNotBlank() && query != state.query)) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        when {
            // Blankness is judged on the live query so clearing the field empties the screen at
            // once, rather than leaving the last results up for the length of the debounce.
            query.isBlank() -> EmptyState(
                headline = "Find something",
                detail = "Search YouTube, play it straight away, and keep the ones you want.",
                modifier = Modifier.fillMaxSize(),
            )

            state.error != null -> Message(state.error, onRetry)

            !state.loading && state.query.isNotBlank() && state.results.isEmpty() -> EmptyState(
                headline = "Nothing found",
                detail = "No music matched that.",
                modifier = Modifier.fillMaxSize(),
            )

            else -> LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            ) {
                items(state.results, key = { it.id }, contentType = { "remote" }) { song ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SongRow(
                            song = song,
                            playing = song.id == currentSongId,
                            animating = playbackActive,
                            modifier = Modifier.weight(1f),
                            onClick = { onPlay(state.results, state.results.indexOf(song)) },
                        )
                        DownloadButton(
                            state = downloads[song.streamKey],
                            owned = song.streamKey in ownedKeys,
                            onClick = { onDownload(song) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One control, four meanings. Kept as an icon rather than a label so the row stays the same shape
 * whatever the download is doing.
 */
@Composable
private fun DownloadButton(state: DownloadState?, owned: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        when {
            owned || state is DownloadState.Done ->
                Icon(Icons.Default.Check, "Already saved", tint = accent, modifier = Modifier.size(20.dp))

            state is DownloadState.Queued ->
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = TextLo)

            state is DownloadState.Running ->
                // Determinate once a length is known, so the ring means something.
                CircularProgressIndicator(
                    progress = { state.fraction },
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = accent,
                )

            state is DownloadState.Failed -> IconButton(onClick) {
                Icon(Icons.Default.Refresh, "Retry download", tint = TextLo, modifier = Modifier.size(20.dp))
            }

            else -> IconButton(onClick) {
                Icon(Icons.Default.Download, "Download", tint = TextLo, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun Message(text: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text,
                color = TextLo,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onRetry) { Text("Try again") }
        }
    }
}
