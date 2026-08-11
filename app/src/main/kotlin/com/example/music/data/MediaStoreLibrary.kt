package com.example.music.data

import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The library. MediaStore is already a complete, OS-maintained index of every audio file on the
 * device, so we never scan storage ourselves — one query, one pass, done. Albums and artists are
 * derived in memory rather than queried separately; a second and third ContentResolver round trip
 * would cost more than grouping a list we already hold.
 */
class MediaStoreLibrary(private val context: Context, private val scope: CoroutineScope) {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    private val observer = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) {
            scope.launch { load() }
        }
    }

    fun start() {
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer,
        )
        scope.launch { load() }
    }

    suspend fun load() = withContext(Dispatchers.IO) {
        _songs.value = query()
        _loaded.value = true
    }

    private fun query(): List<Song> {
        val cols = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
        )
        if (Build.VERSION.SDK_INT >= 29) cols += MediaStore.Audio.Media.RELATIVE_PATH

        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            cols.toTypedArray(),
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0",
            null,
            null,
        ) ?: return emptyList()

        return cursor.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val track = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val year = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val added = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val data = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val size = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val mime = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val rel = if (Build.VERSION.SDK_INT >= 29) {
                c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            } else -1

            val out = ArrayList<Song>(c.count)
            while (c.moveToNext()) {
                val path = c.getString(data) ?: ""
                val folder = when {
                    rel >= 0 -> c.getString(rel)?.trimEnd('/')?.substringAfterLast('/').orEmpty()
                    else -> File(path).parentFile?.name.orEmpty()
                }
                // Clean once, here, rather than on every recomposition of every row.
                val (cleanTitle, cleanArtist) = tidyNames(c.getString(title) ?: "", c.getString(artist))
                out += Song(
                    id = c.getLong(id),
                    title = cleanTitle,
                    artist = cleanArtist,
                    album = tidyAlbum(c.getString(album)),
                    albumId = c.getLong(albumId),
                    durationMs = c.getLong(duration),
                    // MediaStore encodes disc number as the thousands digit: 1004 = disc 1, track 4.
                    track = c.getInt(track).let { if (it > 1000) it % 1000 else it },
                    year = c.getInt(year),
                    dateAdded = c.getLong(added),
                    folder = folder.ifEmpty { "Internal storage" },
                    path = path,
                    sizeBytes = c.getLong(size),
                    mime = c.getString(mime).orEmpty(),
                )
            }
            out
        }
    }

    private fun String?.orUnknown() =
        if (this == null || this == MediaStore.UNKNOWN_STRING) "Unknown" else this
}

fun List<Song>.toAlbums(): List<Album> =
    groupBy { it.albumId }.map { (id, songs) ->
        val sorted = songs.sortedWith(compareBy({ it.track }, { it.title }))
        Album(
            id = id,
            title = sorted.first().album,
            artist = sorted.map { it.artist }.distinct().singleOrNull() ?: "Various Artists",
            year = sorted.maxOf { it.year },
            songs = sorted,
        )
    }.sortedBy { it.title.lowercase() }

fun List<Song>.toArtists(): List<Artist> =
    // Tags are hand-typed, so "Kestrel" and "kestrel" are one artist. Fold on case, then display
    // whichever spelling appears on the most tracks.
    groupBy { it.artist.lowercase() }.map { (_, songs) ->
        Artist(
            name = songs.groupingBy { it.artist }.eachCount().maxBy { it.value }.key,
            albumCount = songs.distinctBy { it.albumId }.size,
            songs = songs.sortedWith(compareBy({ it.album }, { it.track }, { it.title })),
        )
    }.sortedBy { it.name.lowercase() }
