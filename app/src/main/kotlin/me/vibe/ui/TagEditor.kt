package me.vibe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.vibe.data.Song
import me.vibe.data.Tags

@Composable
fun TagEditorDialog(
    song: Song,
    load: suspend (Song) -> Tags,
    onDismiss: () -> Unit,
    onSave: (Tags) -> Unit,
) {
    var tags by remember { mutableStateOf<Tags?>(null) }
    LaunchedEffect(song.id) { tags = load(song) }

    val current = tags
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        title = { Text("Edit tags", color = TextHi) },
        text = {
            if (current == null) {
                Text("Reading file…", color = TextLo)
            } else {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Field("Title", current.title) { tags = current.copy(title = it) }
                    Field("Artist", current.artist) { tags = current.copy(artist = it) }
                    Field("Album", current.album) { tags = current.copy(album = it) }
                    Field("Album artist", current.albumArtist) { tags = current.copy(albumArtist = it) }
                    Field("Genre", current.genre) { tags = current.copy(genre = it) }
                    Field("Year", current.year) { tags = current.copy(year = it) }
                    Field("Track", current.track) { tags = current.copy(track = it) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { current?.let(onSave) }, enabled = current != null) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
