package com.example.music.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import kotlinx.serialization.Serializable

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val track: Int,
    val year: Int,
    val dateAdded: Long,
    val folder: String,
    val path: String,
    val sizeBytes: Long = 0,
    val mime: String = "",
) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

    /** Legacy album-art endpoint. Works on every API level we support and needs no permission. */
    val artUri: Uri get() = ContentUris.withAppendedId("content://media/external/audio/albumart".toUri(), albumId)
}

private fun String.toUri(): Uri = Uri.parse(this)

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val year: Int,
    val songs: List<Song>,
)

data class Artist(
    val name: String,
    val albumCount: Int,
    val songs: List<Song>,
)

@Serializable
data class Playlist(
    val id: Long,
    val name: String,
    val songIds: List<Long> = emptyList(),
)

enum class SortKey { TITLE, ARTIST, ALBUM, DURATION, YEAR, TRACK, DATE_ADDED, PLAY_COUNT }

enum class GroupKey { NONE, ALBUM, ARTIST, FOLDER, YEAR, LETTER }

@Serializable
data class SortSpec(
    val key: SortKey = SortKey.TITLE,
    val descending: Boolean = false,
    val group: GroupKey = GroupKey.NONE,
)

enum class Tab { SONGS, ALBUMS, ARTISTS }

/** Lists the app derives rather than the user curating them. */
enum class SmartList(val label: String, val blurb: String) {
    RECENTLY_ADDED("Recently added", "Newest files on this device"),
    MOST_PLAYED("Most played", "What you come back to"),
    RECENTLY_PLAYED("Recently played", "Where you left off"),
}

/** Capped so these stay a shortlist worth scanning, not a second copy of the library. */
const val SMART_LIST_LIMIT = 100

fun smartListSongs(
    kind: SmartList,
    songs: List<Song>,
    playCounts: Map<Long, Int>,
    lastPlayed: Map<Long, Long>,
): List<Song> = when (kind) {
    SmartList.RECENTLY_ADDED -> songs.sortedByDescending { it.dateAdded }
    SmartList.MOST_PLAYED -> songs.filter { (playCounts[it.id] ?: 0) > 0 }
        .sortedByDescending { playCounts[it.id] ?: 0 }
    SmartList.RECENTLY_PLAYED -> songs.filter { lastPlayed.containsKey(it.id) }
        .sortedByDescending { lastPlayed[it.id] ?: 0L }
}.take(SMART_LIST_LIMIT)
