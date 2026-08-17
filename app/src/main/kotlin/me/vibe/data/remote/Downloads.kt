package me.vibe.data.remote

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import me.vibe.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed interface DownloadState {
    data object Queued : DownloadState
    data class Running(val fraction: Float) : DownloadState
    data class Failed(val reason: String) : DownloadState
    data object Done : DownloadState
}

/**
 * Turns a search result into a real file in `Music/vibe.me`.
 *
 * Nothing here tells the library about the new file. [me.vibe.data.MediaStoreLibrary] already
 * watches MediaStore through a ContentObserver, so a completed download shows up on Home by itself.
 */
class Downloads(private val context: Context) {

    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states

    fun stateOf(videoId: String): DownloadState? = _states.value[videoId]

    fun markQueued(videoId: String) = set(videoId, DownloadState.Queued)

    private fun set(videoId: String, state: DownloadState) {
        _states.value = _states.value + (videoId to state)
    }

    /**
     * Download, tag, publish. Returns the display name written, or throws.
     *
     * The file is assembled in `cacheDir` first and only copied into MediaStore once it is complete
     * and tagged. Two reasons, both load-bearing: jaudiotagger needs a real seekable [File] and
     * cannot work against a MediaStore output stream, and a partial file that never reaches
     * MediaStore is a partial file the library never indexes as music.
     */
    suspend fun download(song: Song): String = withContext(Dispatchers.IO) {
        val videoId = requireNotNull(song.streamKey) { "not a remote track" }
        set(videoId, DownloadState.Running(0f))

        val picked = YouTube.stream(videoId)
        val temp = File(context.cacheDir, "dl-$videoId.${picked.suffix}")

        try {
            fetch(picked.url, temp) { set(videoId, DownloadState.Running(it)) }
            runCatching { tag(temp, song) }
            val name = "${sanitize(song.artist)} - ${sanitize(song.title)}.${picked.suffix}"
            publish(temp, name, picked.mime)
            set(videoId, DownloadState.Done)
            name
        } catch (t: Throwable) {
            set(videoId, DownloadState.Failed(t.message ?: "Download failed"))
            throw t
        } finally {
            temp.delete()
        }
    }

    /** Streams the body to [target], reporting progress when the server declares a length. */
    private fun fetch(url: String, target: File, onProgress: (Float) -> Unit) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw java.io.IOException("Stream returned HTTP ${connection.responseCode}")
            }
            val total = connection.contentLengthLong
            var written = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        // An unknown length is reported as indeterminate rather than as a lie.
                        if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (written == 0L) throw java.io.IOException("Stream was empty")
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Writes the tags the file will be read by forever.
     *
     * This is the whole reason downloads are worth doing inside the app rather than through a
     * browser: the names already went through the same repair the library uses, so the file lands
     * correct instead of landing as `Sia_-_Snowman(128k)` for `Display.kt` to patch up at every
     * read. Best-effort — a tagging failure must not lose an otherwise good download.
     */
    private fun tag(file: File, song: Song) {
        val audio = AudioFileIO.read(file)
        val tag = audio.tagOrCreateAndSetDefault
        tag.setField(FieldKey.TITLE, song.title)
        tag.setField(FieldKey.ARTIST, song.artist)
        if (song.album.isNotBlank()) tag.setField(FieldKey.ALBUM, song.album)
        song.artUrl?.let { url ->
            runCatching {
                val bytes = URL(url).openStream().use { it.readBytes() }
                tag.setField(
                    ArtworkFactory.getNew().apply {
                        binaryData = bytes
                        mimeType = "image/jpeg"
                    },
                )
            }
        }
        AudioFileIO.write(audio)
    }

    /** Into `Music/vibe.me`, by whichever route the API level allows. */
    private fun publish(source: File, displayName: String, mime: String) {
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val pending = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.MIME_TYPE, mime)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/vibe.me")
                // Hidden from every other app until the bytes are all there, so nothing indexes a
                // half-written file as music.
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, pending)
                ?: throw java.io.IOException("MediaStore refused the insert")
            try {
                resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                    ?: throw java.io.IOException("MediaStore gave no output stream")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "vibe.me",
            ).apply { mkdirs() }
            val target = File(dir, displayName)
            source.copyTo(target, overwrite = true)
            // Pre-Q there is no IS_PENDING, so the scanner is told only once the copy is complete.
            android.media.MediaScannerConnection.scanFile(
                context, arrayOf(target.absolutePath), null, null,
            )
        }
    }

    /** Characters that are legal in a title and illegal in a filename. */
    private fun sanitize(name: String): String =
        name.replace(Regex("[/\\\\:*?\"<>|]"), "-").trim().take(80).ifBlank { "Unknown" }
}
