package me.vibe.playback

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import me.vibe.data.REMOTE_SCHEME
import me.vibe.data.remote.YouTube
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * Swaps a `vibe://yt/<videoId>` placeholder for a live stream URL at the moment the loader opens it.
 *
 * This is what lets a remote track sit in a saved queue. Extracted URLs expire in about six hours
 * and the queue outlives that routinely — a restored queue from yesterday would be entirely dead
 * links. Resolving here instead means the queue stores something permanent and the network call
 * happens exactly when the bytes are actually wanted.
 */
@UnstableApi
object RemoteResolver : ResolvingDataSource.Resolver {

    /** Under the six-hour expiry with room to spare, so a seek does not re-extract. */
    private const val TTL_MS = 5 * 60 * 60 * 1000L

    private class Entry(val url: String, val atElapsed: Long)

    private val cache = mutableMapOf<String, Entry>()

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        if (dataSpec.uri.scheme != REMOTE_SCHEME) return dataSpec
        val videoId = dataSpec.uri.lastPathSegment
            ?: throw IOException("Malformed remote uri: ${dataSpec.uri}")
        return dataSpec.withUri(Uri.parse(urlFor(videoId)))
    }

    private fun urlFor(videoId: String): String {
        synchronized(cache) {
            cache[videoId]?.takeIf { SystemClock.elapsedRealtime() - it.atElapsed < TTL_MS }
                ?.let { return it.url }
        }
        // Blocking is correct here: resolveDataSpec runs on Media3's loader thread, never the main
        // thread, and the loader has nothing to do until there is a URL to open.
        val url = runBlocking { YouTube.stream(videoId) }.url
        synchronized(cache) { cache[videoId] = Entry(url, SystemClock.elapsedRealtime()) }
        return url
    }

    /** Dropped when a stream 403s, so the retry re-extracts rather than replaying a dead URL. */
    fun invalidate(videoId: String) {
        synchronized(cache) { cache.remove(videoId) }
    }
}
