package com.example.music.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.music.data.Song
import com.example.music.data.formatDuration

/** Everything you can do to one track, in the order you are most likely to want it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongMenuSheet(
    song: Song,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onGoToAlbum: () -> Unit,
    onGoToArtist: () -> Unit,
    onEditTags: () -> Unit,
    onShare: () -> Unit,
    onRingtone: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface1) {
        Column(Modifier.padding(bottom = 28.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(song.artUri, song.title, Modifier.size(46.dp), corner = 6)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextHi,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${song.artist} · ${formatDuration(song.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLo,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(color = Hairline)

            Item(Icons.Default.PlayArrow, "Play next", onPlayNext, onDismiss)
            Item(Icons.AutoMirrored.Filled.QueueMusic, "Add to queue", onAddToQueue, onDismiss)
            Item(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist", onAddToPlaylist, onDismiss)
            Item(
                if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                if (isFavorite) "Remove from favorites" else "Add to favorites",
                onToggleFavorite, onDismiss,
            )
            HorizontalDivider(color = Hairline, modifier = Modifier.padding(vertical = 6.dp))
            Item(Icons.Default.Album, "Go to album", onGoToAlbum, onDismiss)
            Item(Icons.Default.Person, "Go to artist", onGoToArtist, onDismiss)
            Item(Icons.Default.Tune, "Edit track details", onEditTags, onDismiss)
            HorizontalDivider(color = Hairline, modifier = Modifier.padding(vertical = 6.dp))
            Item(Icons.Default.Share, "Share", onShare, onDismiss)
            Item(Icons.Default.Notifications, "Set as ringtone", onRingtone, onDismiss)
            Item(Icons.Default.Info, "Details", onInfo, onDismiss)
            Item(Icons.Default.DeleteOutline, "Delete from device", onDelete, onDismiss)
        }
    }
}

@Composable
private fun Item(icon: ImageVector, label: String, action: () -> Unit, dismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { action(); dismiss() }
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, Modifier.size(19.dp), tint = TextLo)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextHi)
    }
}
