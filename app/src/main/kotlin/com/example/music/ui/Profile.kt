package com.example.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import coil3.compose.AsyncImage
import com.example.music.Deps
import com.example.music.data.initialsOf

/**
 * The listener, as far as an offline app can know them: a name they typed and a picture they
 * chose. There is no account behind this and nothing leaves the device, so the profile shows
 * things that are actually true — what is in the library and what has been played — rather than
 * inventing the follower counts a streaming service would put here.
 */
@Composable
fun Avatar(name: String, uri: String, size: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size.dp).clip(CircleShape).background(LocalAccent.current),
        contentAlignment = Alignment.Center,
    ) {
        if (uri.isNotBlank()) {
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                imageLoader = Deps.images,
                modifier = Modifier.size(size.dp),
            )
        } else if (name.isNotBlank()) {
            Text(
                initialsOf(name),
                style = MaterialTheme.typography.labelLarge,
                color = OnAccent,
            )
        } else {
            Icon(Icons.Default.Person, "Profile", Modifier.size((size * 0.55).dp), tint = OnAccent)
        }
    }
}

@Composable
fun ProfileHeader(
    name: String,
    avatarUri: String,
    trackCount: Int,
    playedCount: Int,
    favouriteCount: Int,
    playlistCount: Int,
    onEdit: () -> Unit,
    onPickPhoto: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(name, avatarUri, 66, Modifier.clickable(onClick = onPickPhoto))
            Column(Modifier.weight(1f).padding(start = 14.dp).clickable(onClick = onEdit)) {
                Text(
                    name.ifBlank { "Add your name" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (name.isBlank()) TextLo else TextHi,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Offline library · nothing leaves this device",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLo,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Tracks", trackCount, Modifier.weight(1f))
            Stat("Played", playedCount, Modifier.weight(1f))
            Stat("Saved", favouriteCount, Modifier.weight(1f))
            Stat("Lists", playlistCount, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(Surface2)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$value", style = MaterialTheme.typography.headlineSmall, color = TextHi)
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextLo)
    }
}
