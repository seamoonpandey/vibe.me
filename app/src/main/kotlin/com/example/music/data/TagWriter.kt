package com.example.music.data

import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

data class Tags(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val genre: String = "",
    val year: String = "",
    val track: String = "",
)

/**
 * Tag editing under scoped storage. We cannot hand jaudiotagger the real file — on API 29+ the
 * app has no filesystem write access to media it did not create. So: copy out to cache, edit the
 * copy, then stream it back through the ContentResolver once the user has granted a write request.
 */
class TagWriter(private val context: Context) {

    suspend fun read(song: Song): Tags = withContext(Dispatchers.IO) {
        val work = copyToCache(song)
        try {
            val tag = AudioFileIO.read(work).tag ?: return@withContext Tags()
            Tags(
                title = tag.getFirst(FieldKey.TITLE).orEmpty(),
                artist = tag.getFirst(FieldKey.ARTIST).orEmpty(),
                album = tag.getFirst(FieldKey.ALBUM).orEmpty(),
                albumArtist = tag.getFirst(FieldKey.ALBUM_ARTIST).orEmpty(),
                genre = tag.getFirst(FieldKey.GENRE).orEmpty(),
                year = tag.getFirst(FieldKey.YEAR).orEmpty(),
                track = tag.getFirst(FieldKey.TRACK).orEmpty(),
            )
        } catch (e: Exception) {
            // Unsupported container or damaged header — fall back to what MediaStore already knows.
            Tags(title = song.title, artist = song.artist, album = song.album)
        } finally {
            work.delete()
        }
    }

    /**
     * Returns null on success, or an [IntentSender] the caller must launch to obtain write consent
     * before retrying. Android only tells us consent is needed by refusing the write.
     */
    suspend fun write(song: Song, tags: Tags): IntentSender? = withContext(Dispatchers.IO) {
        val work = copyToCache(song)
        try {
            val audio = AudioFileIO.read(work)
            val tag = audio.tagOrCreateAndSetDefault
            fun set(key: FieldKey, value: String) {
                if (value.isBlank()) tag.deleteField(key) else tag.setField(key, value)
            }
            set(FieldKey.TITLE, tags.title)
            set(FieldKey.ARTIST, tags.artist)
            set(FieldKey.ALBUM, tags.album)
            set(FieldKey.ALBUM_ARTIST, tags.albumArtist)
            set(FieldKey.GENRE, tags.genre)
            set(FieldKey.YEAR, tags.year)
            set(FieldKey.TRACK, tags.track)
            AudioFileIO.write(audio)

            try {
                context.contentResolver.openOutputStream(song.uri, "wt")?.use { out ->
                    work.inputStream().use { it.copyTo(out) }
                } ?: error("no output stream for ${song.uri}")
                android.media.MediaScannerConnection.scanFile(context, arrayOf(song.path), null, null)
                null
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= 30) {
                    MediaStore.createWriteRequest(context.contentResolver, listOf(song.uri))
                        .intentSender
                } else throw e
            }
        } finally {
            work.delete()
        }
    }

    private fun copyToCache(song: Song): File {
        // jaudiotagger dispatches on the extension, so the temp copy has to keep it.
        val ext = song.path.substringAfterLast('.', "mp3")
        val out = File.createTempFile("tag", ".$ext", context.cacheDir)
        context.contentResolver.openInputStream(song.uri)?.use { input ->
            out.outputStream().use { input.copyTo(it) }
        } ?: error("cannot read ${song.uri}")
        return out
    }
}
