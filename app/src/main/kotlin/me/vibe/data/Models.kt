package me.vibe.data

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
    /** YouTube video id. Null — the overwhelming majority — means a local MediaStore file. */
    val streamKey: String? = null,
    /** Remote thumbnail, for tracks that have no album art row to point at. */
    val artUrl: String? = null,
) {
    val isRemote: Boolean get() = streamKey != null

    /**
     * A remote track is addressed by a placeholder, never by its real stream URL.
     *
     * Extracted URLs expire in about six hours and the queue is persisted across restarts, so a
     * real URL in here would routinely be dead by the time it was reached. `RemoteResolver` swaps
     * this for a live one at the moment the loader opens the stream.
     */
    val uri: Uri get() =
        if (streamKey != null) "$REMOTE_SCHEME://yt/$streamKey".toUri()
        else ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

    /** Legacy album-art endpoint. Works on every API level we support and needs no permission. */
    val artUri: Uri get() = artUrl?.toUri()
        ?: ContentUris.withAppendedId("content://media/external/audio/albumart".toUri(), albumId)
}

const val REMOTE_SCHEME = "vibe"

/**
 * A stable local id for a track that has no MediaStore row.
 *
 * MediaStore `_ID` is always positive, so the negative half of the space is free and a remote
 * track can never be mistaken for a local one. Stability matters more than collision-freedom here:
 * favourites and play counts are persisted against this number, so a sequential registry — which
 * would be collision-proof — would orphan every one of them on the next launch.
 *
 * ponytail: hash, not a table. ~65k distinct remote tracks before a 50% chance of one collision.
 * If that ever matters, swap in a persisted videoId -> id map behind this same function.
 */
fun remoteId(videoId: String): Long = -((videoId.hashCode().toLong() and 0x7FFFFFFFL) + 1)

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
