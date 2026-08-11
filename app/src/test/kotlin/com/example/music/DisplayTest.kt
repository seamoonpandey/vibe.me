package com.example.music

import com.example.music.data.initialsOf
import com.example.music.data.tidyAlbum
import com.example.music.data.tidyNames
import org.junit.Assert.assertEquals
import org.junit.Test

/** Cases taken verbatim from a real 227-track device library. */
class DisplayTest {

    private fun title(raw: String, artist: String? = "<unknown>") = tidyNames(raw, artist).first
    private fun artist(raw: String, artist: String? = "<unknown>") = tidyNames(raw, artist).second

    @Test
    fun `underscores become spaces and bitrate suffix goes`() {
        assertEquals("Snowman", title("Sia_-_Snowman(128k)"))
        assertEquals("Sia", artist("Sia_-_Snowman(128k)"))
    }

    @Test
    fun `artist is taken from the filename when the tag is missing`() {
        val raw = "Keane_-_Somewhere_Only_We_Know__Lyrics_(128k)"
        assertEquals("Somewhere Only We Know", title(raw))
        assertEquals("Keane", artist(raw))
    }

    @Test
    fun `a real artist tag always wins over the filename`() {
        val (t, a) = tidyNames("Keane_-_Somewhere_Only_We_Know(128k)", "Keane Official")
        assertEquals("Keane - Somewhere Only We Know", t)
        assertEquals("Keane Official", a)
    }

    @Test
    fun `bracketed video noise is dropped but real parentheses survive`() {
        assertEquals("Line Without a Hook", title("Ricky_Montgomery_-_Line_Without_a_Hook__Official_Lyric_Video_(128k)"))
        // "feat." names a performer, so it is content, not noise.
        assertEquals("Aau Wora (feat. Mina Niraula)", title("Aau_Wora_(feat._Mina_Niraula)(128k)", "Bipul Chettri"))
    }

    @Test
    fun `noise words inside real words are left alone`() {
        assertEquals("Audioslave", title("Audioslave", "Audioslave"))
        assertEquals("Official Secrets", title("Official_Secrets", "Someone"))
    }

    @Test
    fun `missing artist falls back to a readable label, not angle brackets`() {
        assertEquals("Unknown artist", artist("Recording 001", "<unknown>"))
        assertEquals("Unknown artist", artist("Recording 001", null))
    }

    @Test
    fun `a title that is only punctuation still yields something`() {
        assertEquals("Untitled", title("___", null))
    }

    @Test
    fun `an overlong left fragment is not treated as an artist`() {
        val raw = "This is a very long sentence indeed that runs on - Song"
        assertEquals("This is a very long sentence indeed that runs on - Song", title(raw))
        assertEquals("Unknown artist", artist(raw))
    }

    @Test
    fun `junk album names read as no album`() {
        assertEquals("No album", tidyAlbum("download"))
        assertEquals("No album", tidyAlbum("<unknown>"))
        assertEquals("No album", tidyAlbum(null))
        assertEquals("Kinnaui Lok Geet", tidyAlbum("Kinnaui_Lok_Geet"))
    }

    @Test
    fun `initials cover one word, two words and empty input`() {
        assertEquals("SN", initialsOf("Snowman"))
        assertEquals("SO", initialsOf("Somewhere Only We Know"))
        assertEquals("?", initialsOf(""))
    }
}

class SmartListTest {
    private fun s(id: Long, added: Long) = com.example.music.data.Song(
        id = id, title = "T$id", artist = "A", album = "Al", albumId = 1,
        durationMs = 1000, track = 1, year = 2000, dateAdded = added,
        folder = "f", path = "/p/$id.mp3",
    )

    private val songs = listOf(s(1, 100), s(2, 300), s(3, 200))

    @Test
    fun `recently added is newest first`() {
        val out = com.example.music.data.smartListSongs(
            com.example.music.data.SmartList.RECENTLY_ADDED, songs, emptyMap(), emptyMap(),
        )
        assertEquals(listOf(2L, 3L, 1L), out.map { it.id })
    }

    @Test
    fun `most played excludes never-played tracks`() {
        val out = com.example.music.data.smartListSongs(
            com.example.music.data.SmartList.MOST_PLAYED, songs, mapOf(1L to 2, 3L to 9), emptyMap(),
        )
        assertEquals(listOf(3L, 1L), out.map { it.id })
    }

    @Test
    fun `recently played is ordered by last play, not by count`() {
        val out = com.example.music.data.smartListSongs(
            com.example.music.data.SmartList.RECENTLY_PLAYED, songs,
            mapOf(1L to 99), mapOf(1L to 10L, 2L to 50L),
        )
        assertEquals(listOf(2L, 1L), out.map { it.id })
    }
}

class ContractionTest {
    @Test
    fun `apostrophes lost to filename encoding are put back`() {
        assertEquals("Don't Blame Me", tidyNames("Don_t_Blame_Me", "Taylor Swift").first)
        assertEquals("It'll Be Okay", tidyNames("It_ll_Be_Okay", "Shawn Mendes").first)
        assertEquals("If I Can't Have You", tidyNames("If_I_Can_t_Have_You", "Shawn Mendes").first)
    }

    @Test
    fun `real single-letter words are not mangled into contractions`() {
        // "A" and "I" are words; only a letter following another word gets an apostrophe.
        assertEquals("Vitamin D", tidyNames("Vitamin D", "Someone").first)
    }

    @Test
    fun `a dash left behind by the artist split is trimmed`() {
        val (title, artist) = tidyNames("Ma_Timro_-_-_Swoopna_Suman", "<unknown>")
        assertEquals("Swoopna Suman", title)
        assertEquals("Ma Timro", artist)
    }
}

class GreetingTest {
    private fun g(h: Int) = com.example.music.ui.greetingFor(h, "moon")

    @Test
    fun `each part of the day gets its own greeting`() {
        assertEquals("Good morning, moon", g(5))
        assertEquals("Good morning, moon", g(11))
        assertEquals("Good afternoon, moon", g(12))
        assertEquals("Good afternoon, moon", g(16))
        assertEquals("Good evening, moon", g(17))
        assertEquals("Good evening, moon", g(21))
    }

    @Test
    fun `the small hours are not morning`() {
        // 22:00 through 04:59 is late, not early. Being told "good morning" at 11pm is the bug.
        assertEquals("Still up, moon", g(22))
        assertEquals("Still up, moon", g(0))
        assertEquals("Still up, moon", g(4))
    }

    @Test
    fun `no name means no dangling comma`() {
        assertEquals("Good morning", com.example.music.ui.greetingFor(9, ""))
        assertEquals("Good morning", com.example.music.ui.greetingFor(9, "   "))
    }

    @Test
    fun `the prompt is stable for an hour and always in range`() {
        assertEquals(com.example.music.ui.promptFor(9, 200), com.example.music.ui.promptFor(9, 200))
        for (h in 0..23) for (d in 1..366) com.example.music.ui.promptFor(h, d)
    }
}
