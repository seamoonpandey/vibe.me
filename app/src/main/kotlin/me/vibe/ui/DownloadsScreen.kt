package me.vibe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.vibe.data.Song
import me.vibe.data.formatDuration
import me.vibe.data.remote.DownloadJob
import me.vibe.data.remote.DownloadState
import com.adamglin.phosphoricons.RegularGroup as Ph
import com.adamglin.phosphoricons.regular.*

/**
 * Everything a download can be doing, and everything it has already done.
 *
 * Two sections rather than one list because the two halves answer different questions: the top is
 * "is it working", the bottom is "what do I have". A finished transfer moves out of the first and
 * into the second by itself — the file appearing in `Music/vibe.me` is what puts it there, so the
 * page cannot disagree with the library about whether a track was saved.
 */
@Composable
fun DownloadsScreen(
    jobs: Map<String, DownloadJob>,
    downloaded: List<Song>,
    contentPadding: PaddingValues,
    currentSongId: Long?,
    playbackActive: Boolean,
    onPlay: (List<Song>, Int) -> Unit,
    onRetry: (Song) -> Unit,
    onCancel: (String) -> Unit,
    onSongMenu: (Song) -> Unit,
) {
    // Done jobs are represented by the file itself, below. Keeping them here too would list the
    // same track twice the moment MediaStore indexes it.
    val active = remember(jobs) { jobs.values.filter { it.state !is DownloadState.Done } }
    val listPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())

    if (active.isEmpty() && downloaded.isEmpty()) {
        EmptyState(
            headline = "Nothing downloaded yet",
            detail = "Search YouTube on Explore and tap the download arrow on a track. " +
                "It will land here, then in your library.",
            modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding()),
        )
        return
    }

    val positions = remember(downloaded) { downloaded.withIndex().associate { (i, s) -> s.id to i } }

    LazyColumn(
        Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding()),
        contentPadding = listPadding,
    ) {
        if (active.isNotEmpty()) {
            item(key = "active_header", contentType = "header") {
                SectionHeader("Downloading", active.size)
            }
            items(active, key = { it.song.streamKey ?: it.song.path }, contentType = { "job" }) { job ->
                DownloadRow(job, onRetry, onCancel)
            }
        }

        if (downloaded.isNotEmpty()) {
            item(key = "files_header", contentType = "header") {
                SectionHeader(
                    "Downloaded",
                    downloaded.size,
                )
            }
            item(key = "files_summary", contentType = "summary") {
                Text(
                    "${downloaded.size} tracks · ${formatSize(downloaded.sumOf { it.sizeBytes })} · " +
                        formatDuration(downloaded.sumOf { it.durationMs }),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLo,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
            items(downloaded, key = { it.id }, contentType = { "file" }) { song ->
                SongRow(
                    song = song,
                    playing = song.id == currentSongId,
                    animating = playbackActive,
                    onClick = { onPlay(downloaded, positions[song.id] ?: 0) },
                    onMenu = { onSongMenu(song) },
                )
            }
        }
    }
}

/**
 * One transfer in progress.
 *
 * The bar is only drawn once there is something to fill it with. A queued track gets an
 * indeterminate bar instead of a ring stuck at zero, which says "waiting" rather than "stalled".
 */
@Composable
private fun DownloadRow(
    job: DownloadJob,
    onRetry: (Song) -> Unit,
    onCancel: (String) -> Unit,
) {
    val song = job.song
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song.artUrl, song.title, Modifier.size(48.dp), corner = 6)

        Column(
            Modifier.weight(1f).padding(start = 12.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextHi,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                statusLabel(job.state),
                style = MaterialTheme.typography.bodySmall,
                color = if (job.state is DownloadState.Failed) accent else TextLo,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            when (val state = job.state) {
                is DownloadState.Running -> RoundedProgress(
                    progress = state.fraction,
                    color = accent,
                )
                DownloadState.Queued -> RoundedProgress(progress = null, color = accent)
                else -> Unit
            }
        }

        when (job.state) {
            is DownloadState.Failed -> IconButton({ onRetry(song) }, Modifier.size(44.dp)) {
                Icon(Ph.ArrowsClockwise, "Retry download", Modifier.size(19.dp), tint = TextLo)
            }
            is DownloadState.Running, DownloadState.Queued ->
                IconButton({ onCancel(song.streamKey.orEmpty()) }, Modifier.size(44.dp)) {
                    Icon(Ph.X, "Cancel download", Modifier.size(19.dp), tint = TextLo)
                }
            else -> Box(Modifier.size(44.dp))
        }
    }
}

/**
 * The receipt for a finished download.
 *
 * A dialog rather than a message that slides past, because the outcome is worth acknowledging: the
 * user asked for a track to be kept, and whether it was kept should stay on screen until they have
 * read it rather than expiring on a timer while they are still looking at the row they tapped.
 *
 * One dialog covers however many transfers finished while it was open, so a batch reports itself
 * once instead of stacking a box per track.
 */
@Composable
fun DownloadNoticeDialog(notices: List<DownloadJob>, onDone: () -> Unit) {
    if (notices.isEmpty()) return

    val saved = notices.filter { it.state is DownloadState.Done }
    val failed = notices.filter { it.state !is DownloadState.Done }

    AlertDialog(
        onDismissRequest = onDone,
        containerColor = Surface1,
        title = {
            Text(
                noticeTitle(saved.size, failed.size),
                color = TextHi,
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                saved.forEach { job ->
                    Text(
                        "“${job.song.title}” — ${job.song.artist}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextHi,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                failed.forEach { job ->
                    Text(
                        "“${job.song.title}”",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextHi,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (saved.isEmpty()) Modifier else Modifier.padding(top = 4.dp),
                    )
                    Text(
                        (job.state as DownloadState.Failed).reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLo,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDone) { Text("Done") }
        },
    )
}

private fun noticeTitle(saved: Int, failed: Int): String = when {
    failed == 0 && saved == 1 -> "Saved to your library"
    failed == 0 -> "$saved tracks saved"
    saved == 0 && failed == 1 -> "Couldn't save that track"
    saved == 0 -> "Couldn't save $failed tracks"
    else -> "Downloads finished"
}

/** Thin and rounded, so a progress bar reads as part of the row rather than a hard rule across it. */
@Composable
private fun RoundedProgress(progress: Float?, color: androidx.compose.ui.graphics.Color) {
    val shape = RoundedCornerShape(50)
    if (progress == null) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp).height(3.dp).clip(shape),
            color = color,
            trackColor = Surface2,
            strokeCap = StrokeCap.Round,
        )
    } else {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp).height(3.dp).clip(shape),
            color = color,
            trackColor = Surface2,
            strokeCap = StrokeCap.Round,
        )
    }
}

private fun statusLabel(state: DownloadState): String = when (state) {
    DownloadState.Queued -> "Waiting"
    is DownloadState.Running ->
        if (state.fraction > 0f) "${(state.fraction * 100).toInt()}% · Downloading" else "Connecting"
    is DownloadState.Failed -> state.reason
    DownloadState.Done -> "Done"
}

/** Sizes people read at a glance: one decimal, and the unit that fits. */
private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000L -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
