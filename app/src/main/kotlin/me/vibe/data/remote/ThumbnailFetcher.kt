package me.vibe.data.remote

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.buffer
import okio.source
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Loads `http(s)` images for Coil.
 *
 * Coil 3 ships no network fetcher of its own — that lives in `coil-network-okhttp`, which would add
 * OkHttp to an APK whose only other network code is forty lines of `HttpURLConnection`. Search
 * thumbnails are the only remote images this app will ever load, so they get the same treatment.
 *
 * ponytail: no caching beyond Coil's own memory and disk caches, which already sit in front of this.
 */
class ThumbnailFetcher(
    private val data: Uri,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val connection = (URL(data.toString()).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = true
        }
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            throw IOException("Thumbnail request returned HTTP $code")
        }
        // Read to memory rather than handing Coil a live stream: the connection has to be closed,
        // and thumbnails are small enough that streaming buys nothing.
        val bytes = connection.inputStream.use { it.readBytes() }
        connection.disconnect()

        SourceFetchResult(
            source = ImageSource(
                source = bytes.inputStream().source().buffer(),
                fileSystem = FileSystem.SYSTEM,
            ),
            mimeType = connection.contentType,
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (data.scheme == "http" || data.scheme == "https") ThumbnailFetcher(data, options)
            else null
    }
}
