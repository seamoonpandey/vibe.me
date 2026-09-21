package me.vibe

import me.vibe.data.DOWNLOAD_FOLDER
import me.vibe.data.artUriFor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where a track's cover comes from.
 *
 * The per-file case is a bug fix wearing a preference's clothes, so it is worth pinning: every
 * download shares one folder, Android derives an album from the folder, and the album-art endpoint
 * is album-scoped. Pointing downloads at it gave all of them one identical URI, which Media3 then
 * cached one bitmap against — so the lockscreen and the notification showed a single arbitrary
 * track's cover for whatever was playing.
 */
class ArtUriTest {

    @Test
    fun `a downloaded track is addressed per file, not per album`() {
        assertEquals(
            "content://media/external/audio/media/42/albumart",
            artUriFor(id = 42, albumId = 999, artUrl = null, folder = DOWNLOAD_FOLDER),
        )
    }

    @Test
    fun `two downloads in the same folder get different covers`() {
        val a = artUriFor(id = 1, albumId = 999, artUrl = null, folder = DOWNLOAD_FOLDER)
        val b = artUriFor(id = 2, albumId = 999, artUrl = null, folder = DOWNLOAD_FOLDER)
        assertEquals(false, a == b)
    }

    @Test
    fun `anything already on the device uses the album-art endpoint`() {
        assertEquals(
            "content://media/external/audio/albumart/77",
            artUriFor(id = 5, albumId = 77, artUrl = null, folder = "Music"),
        )
    }

    @Test
    fun `a remote track uses the thumbnail it was found at`() {
        assertEquals(
            "https://i.ytimg.com/vi/abc/hq720.jpg",
            artUriFor(id = -9, albumId = 0, artUrl = "https://i.ytimg.com/vi/abc/hq720.jpg", folder = "YouTube"),
        )
    }

    @Test
    fun `a downloaded remote track still prefers the remote url while it has one`() {
        // The library read is what clears artUrl; until then the URL is the better cover.
        assertEquals(
            "https://i.ytimg.com/vi/abc/hq720.jpg",
            artUriFor(id = 1, albumId = 999, artUrl = "https://i.ytimg.com/vi/abc/hq720.jpg", folder = DOWNLOAD_FOLDER),
        )
    }
}
