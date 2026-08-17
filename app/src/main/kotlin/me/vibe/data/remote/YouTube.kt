package me.vibe.data.remote

import me.vibe.data.Song
import me.vibe.data.remoteId
import me.vibe.data.tidyNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** What a picked audio stream is: a direct URL plus the two facts the downloader needs about it. */
data class PickedStream(
    val url: String,
    val suffix: String,
    val mime: String,
    val bitrate: Int,
)

/**
 * The only thing in this app that talks to a network.
 *
 * Every call is blocking underneath — NewPipeExtractor is a synchronous library — so everything
 * here hops to [Dispatchers.IO] and nothing on the main thread ever touches it directly.
 */
object YouTube {

    /** Audio-only formats we are willing to keep. Everything else is video or a container we would
     *  have to unwrap, and unwrapping is what the "no transcoding" non-goal rules out. */
    private val AUDIO_FORMATS = setOf(
        MediaFormat.M4A, MediaFormat.WEBMA, MediaFormat.WEBMA_OPUS, MediaFormat.OPUS,
    )

    @Volatile private var started = false

    fun init(poTokens: org.schabi.newpipe.extractor.services.youtube.PoTokenProvider?) {
        if (started) return
        started = true
        NewPipe.init(HttpDownloader)
        // Without a provider the extractor still tries, but most streaming URLs come back 403.
        YoutubeStreamExtractor.setPoTokenProvider(poTokens)
    }

    /**
     * Search results as ordinary [Song] rows.
     *
     * `music_songs` narrows YouTube's own search to its music vertical, which drops the vlogs,
     * reaction videos and hour-long mixes that a bare query returns and that nobody wants in a
     * music player.
     */
    suspend fun search(query: String): List<Song> = withContext(Dispatchers.IO) {
        val service = ServiceList.YouTube
        val handler = service.searchQHFactory.fromQuery(query, listOf("music_songs"), "")
        SearchInfo.getInfo(service, handler).relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull(::toSong)
    }

    /** A direct, short-lived stream URL for one video. Resolved late, never stored. */
    suspend fun stream(videoId: String): PickedStream = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(ServiceList.YouTube, watchUrl(videoId))
        pickAudio(info.audioStreams) ?: throw NoAudioStream(videoId)
    }

    fun watchUrl(videoId: String) = "https://www.youtube.com/watch?v=$videoId"

    /**
     * Highest-bitrate progressive audio stream, or null.
     *
     * Progressive only: a DASH or HLS stream is a manifest rather than a file, and both the
     * downloader and the plain [androidx.media3.datasource.DefaultDataSource] path want something
     * they can open and read to the end.
     */
    fun pickAudio(streams: List<AudioStream>): PickedStream? = streams
        .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        .filter { it.format in AUDIO_FORMATS }
        .filter { it.isUrl && !it.content.isNullOrBlank() }
        .maxByOrNull { it.averageBitrate }
        ?.let {
            PickedStream(
                url = it.content,
                suffix = it.format?.suffix ?: "m4a",
                mime = it.format?.mimeType ?: "audio/mp4",
                bitrate = it.averageBitrate,
            )
        }

    /**
     * One search result as a [Song].
     *
     * The title goes through [tidyNames], the same repair the local library already runs. YouTube
     * titles are the exact shape it was written for — `Sia - Snowman (Official Video)` — so a track
     * reads identically in search, in the queue, and again after it has been downloaded. The
     * uploader is only consulted when the title itself carries no artist, because a channel is as
     * often a label or an aggregator as it is the artist.
     */
    private fun toSong(item: StreamInfoItem): Song? {
        val videoId = videoIdOf(item.url) ?: return null
        // Live streams have no duration and no downloadable file.
        if (item.duration <= 0) return null

        val (title, artistFromTitle) = tidyNames(item.name.orEmpty(), null)
        val artist = if (artistFromTitle != "Unknown artist") artistFromTitle
        else tidyNames(item.name.orEmpty(), channelArtist(item.uploaderName)).second

        return Song(
            id = remoteId(videoId),
            title = title,
            artist = artist,
            album = "",
            albumId = 0,
            durationMs = item.duration * 1000L,
            track = 0,
            year = 0,
            dateAdded = 0,
            folder = "YouTube",
            path = watchUrl(videoId),
            streamKey = videoId,
            // Largest thumbnail: these are cover art in a list, not favicons.
            artUrl = item.thumbnails.maxByOrNull { it.height }?.url,
        )
    }

    /** `Sia - Topic` and `SiaVEVO` are both just Sia wearing a channel's clothes. */
    private fun channelArtist(uploader: String?): String? = uploader
        ?.removeSuffix(" - Topic")
        ?.removeSuffix("VEVO")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    /** Accepts both `watch?v=` and `youtu.be/` shapes; the extractor has returned each over time. */
    fun videoIdOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        Regex("[?&]v=([A-Za-z0-9_-]{11})").find(url)?.let { return it.groupValues[1] }
        Regex("youtu\\.be/([A-Za-z0-9_-]{11})").find(url)?.let { return it.groupValues[1] }
        return null
    }
}

class NoAudioStream(videoId: String) :
    java.io.IOException("No progressive audio stream for $videoId")
