package me.vibe.data.remote

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import me.vibe.data.DOWNLOAD_FOLDER
import me.vibe.data.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface DownloadState {
    data object Queued : DownloadState
    data class Running(val fraction: Float) : DownloadState
    data class Failed(val reason: String) : DownloadState
    data object Done : DownloadState
}

/**
 * One download, with the track it was asked for.
 *
 * The song is remembered alongside the state because the Downloads page has to name a transfer
 * that is not in the library and may never be — a progress bar under a video id is not a thing
 * anyone can read.
 */
data class DownloadJob(val song: Song, val state: DownloadState)

/**
 * Turns a search result into a real file in `Music/vibe.me`.
 *
 * Nothing here tells the library about the new file. [me.vibe.data.MediaStoreLibrary] already
 * watches MediaStore through a ContentObserver, so a completed download shows up on Home by itself.
 */
class Downloads(private val context: Context) {

    private val _jobs = MutableStateFlow<Map<String, DownloadJob>>(emptyMap())
    val jobs: StateFlow<Map<String, DownloadJob>> = _jobs

    fun jobOf(videoId: String): DownloadJob? = _jobs.value[videoId]

    fun markQueued(song: Song) = set(song, DownloadState.Queued)

    /**
     * Records a failure that happened before [download] could report one of its own.
     *
     * Without this a throw on the way into a transfer — the extractor failing to come up, say —
     * leaves a job queued forever, which the user reads as "it hung" rather than "it failed".
     */
    fun fail(song: Song, reason: String) = set(song, DownloadState.Failed(reason))

    /** Drops a job from the list — only ever a failure or a cancelled transfer. */
    fun remove(videoId: String) {
        _jobs.value = _jobs.value - videoId
    }

    private fun set(song: Song, state: DownloadState) {
        val id = song.streamKey ?: return
        _jobs.value = _jobs.value + (id to DownloadJob(song, state))
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
        set(song, DownloadState.Running(0f))

        val picked = YouTube.stream(videoId)
        val temp = File(context.cacheDir, "dl-$videoId.${picked.suffix}")

        try {
            fetch(picked.url, temp) { set(song, DownloadState.Running(it)) }
            // Best-effort: a tagging failure must not lose an otherwise good download.
            runCatching { tag(temp, song) }
            val name = "${sanitize(song.artist)} - ${sanitize(song.title)}.${picked.suffix}"
            publish(temp, name, picked.mime, song)
            set(song, DownloadState.Done)
            name
        } catch (c: CancellationException) {
            // The user cancelled it; a cancelled transfer is not a failure to report.
            remove(videoId)
            throw c
        } catch (t: Throwable) {
            set(song, DownloadState.Failed(t.message ?: "Download failed"))
            throw t
        } finally {
            temp.delete()
        }
    }

    /**
     * Streams the body to [target] in chunked range requests.
     *
     * YouTube throttles a single GET to ~50–100 KB/s after the first burst. Splitting the download
     * into 10 MB chunks — each a fresh connection that gets its own burst — bypasses the throttle
     * and matches NewPipe's download speed.
     *
     * The loop is driven by how many bytes actually arrived, never by a declared length. Sizes are
     * only used to draw the progress bar: a host that answers HEAD with `Content-Length: 0` (Google
     * Video routinely does) must still produce a complete file, and the previous length-driven loop
     * ran past the end of every short file and died on the resulting HTTP 416.
     */
    private suspend fun fetch(url: String, target: File, onProgress: (Float) -> Unit) {
        val total = probeLength(url)
        var written = 0L

        target.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()

                val rangeEnd = if (total > 0L) minOf(written + CHUNK_BYTES - 1, total - 1)
                               else written + CHUNK_BYTES - 1
                val wanted = rangeEnd - written + 1

                val conn = connection(url).apply {
                    setRequestProperty("Range", "bytes=$written-$rangeEnd")
                }
                var chunk = 0L
                var whole = false
                try {
                    val code = conn.responseCode
                    // 416 means the range started past the end, which can only happen once the
                    // file is already complete.
                    if (code == 416 && written > 0L) break
                    if (code != 200 && code != 206) throw IOException("Stream returned HTTP $code")
                    // 200 is the server ignoring the Range header and sending everything at once,
                    // which is only usable for the first chunk. Mid-download it would splice a
                    // second copy of the file's start onto the end of the first.
                    if (code == 200 && written > 0L) throw IOException("Stream restarted mid-download")
                    whole = code == 200
                    conn.inputStream.use { input ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            chunk += n
                            if (total > 0L) {
                                onProgress(((written + chunk).toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                } finally {
                    conn.disconnect()
                }

                written += chunk
                if (whole) break
                // A short chunk is the end of the body, whatever the headers claimed.
                if (chunk < wanted) break
                if (total > 0L && written >= total) break
            }
        }
        if (written == 0L) throw IOException("Stream was empty")
    }

    /**
     * The file size, or 0 when the host will not say.
     *
     * HEAD first because it is one round trip and no bytes, then a one-byte ranged GET, whose
     * `Content-Range` carries the total even when HEAD is refused or answers zero.
     */
    private fun probeLength(url: String): Long {
        val head = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            applyHeaders()
        }
        try {
            if (head.responseCode in 200..299 && head.contentLengthLong > 0L) {
                return head.contentLengthLong
            }
        } catch (_: Exception) {
            // Fall through to the ranged probe.
        } finally {
            head.disconnect()
        }

        val probe = connection(url).apply { setRequestProperty("Range", "bytes=0-0") }
        return try {
            probe.responseCode
            totalFrom(probe.getHeaderField("Content-Range"))
        } catch (_: Exception) {
            0L
        } finally {
            probe.disconnect()
        }
    }

    /** `Content-Range: bytes 0-0/4194304` -> 4194304. */
    private fun totalFrom(header: String?): Long =
        header?.substringAfterLast('/')?.trim()?.toLongOrNull()?.takeIf { it > 0L } ?: 0L

    private fun connection(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 30_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        applyHeaders()
    }

    /** A UA is what keeps Google Video from treating the transfer as a bot and cutting it short. */
    private fun HttpURLConnection.applyHeaders() {
        setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:129.0) Gecko/20100101 Firefox/129.0",
        )
    }

    /**
     * Writes the tags the file will be read by forever.
     *
     * This is the whole reason downloads are worth doing inside the app rather than through a
     * browser: the names already went through the same repair the library uses, so the file lands
     * correct instead of landing as `Sia_-_Snowman(128k)` for `Display.kt` to patch up at every
     * read.
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

    /**
     * The type to file the download under.
     *
     * MediaStore's audio table knows mp4, ogg, mp3, flac, wav and a handful more, but not
     * `audio/webm`, and it rejects an insert it cannot type rather than storing it untyped — which
     * is how a fully-fetched Opus download ends up saving nothing at all. Opus in WebM is indexed
     * as ogg, the nearest type the table has. The file keeps its real extension, so media3 still
     * sniffs the container correctly on playback.
     */
    private fun storeMime(mime: String): String =
        if (mime.equals("audio/webm", ignoreCase = true)) "audio/ogg" else mime

    /** Into `Music/vibe.me`, by whichever route the API level allows. */
    private fun publish(source: File, displayName: String, mime: String, song: Song) {
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val pending = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.MIME_TYPE, storeMime(mime))
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$DOWNLOAD_FOLDER")
                // Hidden from every other app until the bytes are all there, so nothing indexes a
                // half-written file as music.
                put(MediaStore.Audio.Media.IS_PENDING, 1)
                // The library query filters on IS_MUSIC, so a row that arrives without it is a
                // file that exists and never appears. MediaStore would fill these in on its next
                // scan; setting them now means the download is on Home the moment it clears the
                // pending flag instead of whenever that scan happens to run.
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                put(MediaStore.Audio.Media.TITLE, song.title)
                put(MediaStore.Audio.Media.ARTIST, song.artist)
                if (song.album.isNotBlank()) put(MediaStore.Audio.Media.ALBUM, song.album)
            }
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, pending)
                ?: throw IOException("MediaStore refused the insert")
            try {
                resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                    ?: throw IOException("MediaStore gave no output stream")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                DOWNLOAD_FOLDER,
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

    private companion object {
        const val CHUNK_BYTES = 10L * 1024 * 1024
    }
}
