package com.example.music.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.music.data.Song
import com.example.music.data.formatDuration
import java.text.DateFormat
import java.util.Date

/** Hand the file to whatever else the user has installed. */
fun shareSong(context: Context, song: Song) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = song.mime.ifBlank { "audio/*" }
        putExtra(Intent.EXTRA_STREAM, song.uri)
        putExtra(Intent.EXTRA_TITLE, song.title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share ${song.title}"))
}

/**
 * Setting a ringtone needs the special "modify system settings" grant, which is a separate screen
 * rather than a runtime prompt. Send the user there once; afterwards it just works.
 */
fun setAsRingtone(context: Context, song: Song) {
    if (Build.VERSION.SDK_INT >= 23 && !Settings.System.canWrite(context)) {
        Toast.makeText(context, "Allow Music to change system settings, then try again", Toast.LENGTH_LONG).show()
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${context.packageName}".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "This device has no settings screen for that", Toast.LENGTH_SHORT).show()
        }
        return
    }
    runCatching {
        RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, song.uri)
    }.onSuccess {
        Toast.makeText(context, "Ringtone set to ${song.title}", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, "Could not set that track as the ringtone", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Deleting media the app did not create requires the user's explicit consent on API 30+, returned
 * as an IntentSender the caller launches. Null means the delete already went through.
 */
fun deleteSong(context: Context, song: Song): IntentSender? {
    if (Build.VERSION.SDK_INT >= 30) {
        return MediaStore.createDeleteRequest(context.contentResolver, listOf(song.uri)).intentSender
    }
    return try {
        context.contentResolver.delete(song.uri, null, null)
        null
    } catch (e: SecurityException) {
        Toast.makeText(context, "Could not delete that file", Toast.LENGTH_SHORT).show()
        null
    }
}

private fun String.toUri(): Uri = Uri.parse(this)

@Composable
fun TrackInfoDialog(song: Song, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        title = { Text("Track details", color = TextHi, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                InfoLine("Title", song.title)
                InfoLine("Artist", song.artist)
                InfoLine("Album", song.album)
                InfoLine("Length", formatDuration(song.durationMs))
                if (song.year > 0) InfoLine("Year", song.year.toString())
                InfoLine("Format", song.mime.substringAfter('/').uppercase().ifBlank { "Unknown" })
                InfoLine("Size", "%.1f MB".format(song.sizeBytes / 1_048_576f))
                InfoLine("Bitrate", bitrateOf(song))
                InfoLine("Added", DateFormat.getDateInstance().format(Date(song.dateAdded * 1000)))
                InfoLine("Folder", song.folder)
                InfoLine("Path", song.path)
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
    )
}

/** Average bitrate, which is all we can know without decoding the file. */
private fun bitrateOf(song: Song): String {
    if (song.durationMs <= 0 || song.sizeBytes <= 0) return "Unknown"
    val kbps = (song.sizeBytes * 8.0) / song.durationMs
    return "${kbps.toInt()} kbps"
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = TextLo,
            modifier = Modifier.weight(0.34f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = TextHi,
            modifier = Modifier.weight(0.66f),
        )
    }
}
