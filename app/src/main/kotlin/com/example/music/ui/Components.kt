package com.example.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.example.music.data.Song
import com.example.music.data.formatDuration

@Composable
fun Artwork(model: Any?, modifier: Modifier = Modifier, corner: Int = 6) {
    val shape = RoundedCornerShape(corner.dp)
    SubcomposeAsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(shape).background(Surface2),
        error = { ArtworkFallback() },
        loading = { Box(Modifier.fillMaxSize().background(Surface2)) },
    )
}

@Composable
private fun ArtworkFallback() {
    Box(Modifier.fillMaxSize().background(Surface2), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            tint = TextLo,
            modifier = Modifier.fillMaxSize(0.4f),
        )
    }
}

@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    index: Int? = null,
    onMenu: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Text(
                text = "$index",
                style = MaterialTheme.typography.bodySmall,
                color = if (highlighted) LocalAccent.current else TextLo,
                modifier = Modifier.size(28.dp).padding(end = 8.dp),
            )
        } else {
            Artwork(song.artUri, Modifier.size(48.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (highlighted) LocalAccent.current else TextHi,
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
        if (onMenu != null) {
            IconButton(onClick = onMenu) {
                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = TextLo)
            }
        }
    }
}

@Composable
fun GridCard(
    title: String,
    subtitle: String,
    art: Any?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    round: Boolean = false,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Artwork(art, Modifier.fillMaxWidth().aspectRatio(1f), corner = if (round) 500 else 8)
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = TextHi,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = TextLo,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = LocalAccent.current,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.displaySmall,
        color = TextHi,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}
