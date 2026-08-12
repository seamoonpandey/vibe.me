package me.vibe.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.vibe.data.Playlist
import me.vibe.data.SmartList

@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    favoriteCount: Int,
    contentPadding: PaddingValues,
    creating: Boolean,
    onCreatingChange: (Boolean) -> Unit,
    onOpen: (Playlist) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSmart: (SmartList) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var renaming by remember { mutableStateOf<Playlist?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        items(SmartList.entries.toList(), key = { "smart_${it.name}" }) { kind ->
            PlaylistRow(
                name = kind.label,
                subtitle = kind.blurb,
                icon = when (kind) {
                    SmartList.RECENTLY_ADDED -> Icons.Default.NewReleases
                    SmartList.MOST_PLAYED -> Icons.Default.TrendingUp
                    SmartList.RECENTLY_PLAYED -> Icons.Default.History
                },
                onClick = { onOpenSmart(kind) },
            )
        }
        item(key = "favorites") {
            PlaylistRow(
                name = "Favorites",
                subtitle = "$favoriteCount tracks",
                icon = Icons.Default.Favorite,
                onClick = onOpenFavorites,
            )
        }
        items(playlists, key = { it.id }) { playlist ->
            PlaylistRow(
                name = playlist.name,
                subtitle = "${playlist.songIds.size} tracks",
                onClick = { onOpen(playlist) },
                onRename = { renaming = playlist },
                onDelete = { onDelete(playlist.id) },
            )
        }
    }

    if (creating) {
        NameDialog("New playlist", "", { onCreatingChange(false) }) {
            onCreate(it); onCreatingChange(false)
        }
    }
    renaming?.let { target ->
        NameDialog("Rename", target.name, { renaming = null }) {
            onRename(target.id, it); renaming = null
        }
    }
}

@Composable
private fun PlaylistRow(
    name: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(40.dp).padding(end = 8.dp), tint = LocalAccent.current)
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = TextHi, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextLo)
        }
        onRename?.let { IconButton(it) { Icon(Icons.Default.Edit, "Rename", tint = TextLo) } }
        onDelete?.let { IconButton(it) { Icon(Icons.Default.Delete, "Delete", tint = TextLo) } }
    }
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        title = { Text(title, color = TextHi) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Name") },
            )
        },
        confirmButton = {
            // A nameless playlist is unfindable later, so an empty name is simply not accepted.
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
    onCreate: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameDialog("New playlist", "", onDismiss) { onCreate(it); onDismiss() }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        title = { Text("Add to playlist", color = TextHi) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                playlists.forEach { p ->
                    Text(
                        p.name,
                        color = TextHi,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(p.id); onDismiss() }
                            .padding(vertical = 10.dp),
                    )
                }
                if (playlists.isEmpty()) Text("No playlists yet", color = TextLo)
            }
        },
        confirmButton = { TextButton({ creating = true }) { Text("New") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
