package me.vibe

import me.vibe.data.remote.YouTube
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod

/**
 * Stream selection, against hand-built lists.
 *
 * Deliberately not a network test: what is worth pinning is the choice made given a set of streams,
 * and a test that asks YouTube for a real set tests YouTube.
 */
class StreamPickTest {

    private fun stream(
        id: String,
        format: MediaFormat?,
        bitrate: Int,
        delivery: DeliveryMethod = DeliveryMethod.PROGRESSIVE_HTTP,
    ): AudioStream = AudioStream.Builder()
        .setId(id)
        .setContent("https://example.invalid/$id", true)
        .setMediaFormat(format)
        .setDeliveryMethod(delivery)
        .setAverageBitrate(bitrate)
        .build()

    @Test
    fun `highest bitrate wins`() {
        val picked = YouTube.pickAudio(
            listOf(
                stream("low", MediaFormat.M4A, 64),
                stream("high", MediaFormat.M4A, 160),
                stream("mid", MediaFormat.WEBMA_OPUS, 128),
            ),
        )
        assertEquals("https://example.invalid/high", picked?.url)
        assertEquals(160, picked?.bitrate)
    }

    /**
     * M4A is preferred on purpose, not on bitrate.
     *
     * MediaStore refuses to index an `audio/webm` row, so the higher-bitrate Opus stream is the one
     * that downloads and then cannot be saved. The best-sounding stream is not the best stream if
     * it never reaches the library.
     */
    @Test
    fun `m4a beats a higher-bitrate opus stream, because it is the one that can be saved`() {
        val picked = YouTube.pickAudio(
            listOf(
                stream("m4a", MediaFormat.M4A, 128),
                stream("opus", MediaFormat.WEBMA_OPUS, 160),
            ),
        )
        assertEquals("https://example.invalid/m4a", picked?.url)
        assertEquals("m4a", picked?.suffix)
    }

    @Test
    fun `opus is still used when the video offers no m4a at all`() {
        val picked = YouTube.pickAudio(
            listOf(
                stream("opus-low", MediaFormat.WEBMA_OPUS, 90),
                stream("opus-high", MediaFormat.WEBMA_OPUS, 160),
            ),
        )
        assertEquals("https://example.invalid/opus-high", picked?.url)
        assertEquals("webm", picked?.suffix)
    }

    @Test
    fun `non-progressive streams are refused, because they are manifests not files`() {
        assertNull(
            YouTube.pickAudio(
                listOf(stream("dash", MediaFormat.M4A, 320, DeliveryMethod.DASH)),
            ),
        )
    }

    @Test
    fun `video containers are refused`() {
        assertNull(YouTube.pickAudio(listOf(stream("mp4", MediaFormat.MPEG_4, 320))))
    }

    @Test
    fun `an empty list returns null rather than throwing`() {
        assertNull(YouTube.pickAudio(emptyList()))
    }

    @Test
    fun `video ids are read out of both url shapes`() {
        assertEquals("dQw4w9WgXcQ", YouTube.videoIdOf("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YouTube.videoIdOf("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(
            "dQw4w9WgXcQ",
            YouTube.videoIdOf("https://www.youtube.com/watch?t=42&v=dQw4w9WgXcQ&feature=x"),
        )
        assertNull(YouTube.videoIdOf("https://www.youtube.com/results?search_query=x"))
        assertNull(YouTube.videoIdOf(null))
    }
}
