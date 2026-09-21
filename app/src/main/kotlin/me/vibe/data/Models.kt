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

    /**
     * The cover for this track, whichever of the three sources has one.
     *
     * Remote tracks carry the URL they were found at. Files this app downloaded are addressed
     * per-file, and everything else goes through the album-art endpoint, which is the one that
     * works on every API level without a permission.
     *
     * The per-file branch is not a preference, it is a fix. Android derives an album from the
     * folder, so every download landing in `Music/vibe.me` shares one album id and therefore one
     * `albumart/<albumId>` URI. Media3 keys its artwork cache by that URI, so the lockscreen and the
     * media notification showed whichever downloaded track MediaStore indexed last — for every
     * track that played. `media/<id>/albumart` is scoped to the file, and the file is where the
     * cover we embedded actually lives.
     */
    val artUri: Uri get() = artUriFor(id, albumId, artUrl, folder).toUri()
}

/**
 * Which cover a track should show, as a plain string so the choice can be tested on the JVM.
 *
 * Building the [Uri] here instead would make this untestable — `android.net.Uri` is a stub outside a
 * device — and this decision is exactly the kind that regresses quietly.
 */
internal fun artUriFor(id: Long, albumId: Long, artUrl: String?, folder: String): String = when {
    artUrl != null -> artUrl
    // Per-file, because everything the app downloads shares one folder and therefore one album id,
    // and therefore one album-art URI — which is the whole bug. See [Song.artUri].
    folder == DOWNLOAD_FOLDER -> "content://media/external/audio/media/$id/albumart"
    else -> "content://media/external/audio/albumart/$albumId"
}

/** Where every in-app download lands, and therefore how a downloaded track is recognised again. */
const val DOWNLOAD_FOLDER = "vibe.me"

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
