package com.example.music

import com.example.music.data.GroupKey
import com.example.music.data.Song
import com.example.music.data.SortKey
import com.example.music.data.SortSpec
import com.example.music.data.filterSongs
import com.example.music.data.formatDuration
import com.example.music.data.groupSongs
import com.example.music.data.moved
import com.example.music.data.sortSongs
import org.junit.Assert.assertEquals
import org.junit.Test

private fun song(
    id: Long,
    title: String,
    artist: String = "A",
    album: String = "Alb",
    duration: Long = 1000,
    year: Int = 2000,
    folder: String = "Music",
) = Song(
    id = id, title = title, artist = artist, album = album, albumId = 1,
    durationMs = duration, track = 1, year = year, dateAdded = id,
    folder = folder, path = "/music/$title.mp3",
)

class SortingTest {

    private val songs = listOf(
        song(1, "beta", artist = "Zed", duration = 3000, year = 1999),
        song(2, "Alpha", artist = "Ann", duration = 1000, year = 2010),
        song(3, "gamma", artist = "ann", duration = 2000, year = 2010),
    )

    @Test
    fun `title sort is case insensitive`() {
        val out = sortSongs(songs, SortSpec(SortKey.TITLE))
        assertEquals(listOf("Alpha", "beta", "gamma"), out.map { it.title })
    }

    @Test
    fun `descending reverses the order`() {
        val out = sortSongs(songs, SortSpec(SortKey.TITLE, descending = true))
        assertEquals(listOf("gamma", "beta", "Alpha"), out.map { it.title })
    }

    @Test
    fun `equal keys fall back to title so order is stable`() {
        // Two songs share year 2010; the tie-break must be title, not input order.
        val out = sortSongs(songs, SortSpec(SortKey.YEAR))
        assertEquals(listOf("beta", "Alpha", "gamma"), out.map { it.title })
    }

    @Test
    fun `play count sort uses the supplied counts`() {
        val counts = mapOf(1L to 5, 2L to 1, 3L to 9)
        val out = sortSongs(songs, SortSpec(SortKey.PLAY_COUNT, descending = true), counts)
        assertEquals(listOf(3L, 1L, 2L), out.map { it.id })
    }

    @Test
    fun `no grouping yields one unlabelled group`() {
        val groups = groupSongs(songs, SortSpec(SortKey.TITLE, group = GroupKey.NONE))
        assertEquals(1, groups.size)
        assertEquals("", groups[0].first)
        assertEquals(3, groups[0].second.size)
    }

    @Test
    fun `letter grouping buckets by first letter`() {
        val groups = groupSongs(songs, SortSpec(SortKey.TITLE, group = GroupKey.LETTER))
        assertEquals(listOf("A", "B", "G"), groups.map { it.first })
    }

    @Test
    fun `non-letter titles land in the hash bucket`() {
        val groups = groupSongs(listOf(song(9, "123")), SortSpec(group = GroupKey.LETTER))
        assertEquals("#", groups.single().first)
    }

    @Test
    fun `grouping keeps every song exactly once`() {
        val groups = groupSongs(songs, SortSpec(SortKey.TITLE, group = GroupKey.ARTIST))
        assertEquals(songs.size, groups.sumOf { it.second.size })
    }

    @Test
    fun `search matches title artist and album, and blank returns nothing`() {
        assertEquals(listOf(1L), filterSongs(songs, "zed").map { it.id })
        assertEquals(listOf(2L, 3L), filterSongs(songs, "ann").map { it.id })
        assertEquals(emptyList<Song>(), filterSongs(songs, "   "))
    }

    @Test
    fun `duration formatting crosses the hour boundary`() {
        assertEquals("0:09", formatDuration(9_000))
        assertEquals("3:07", formatDuration(187_000))
        assertEquals("1:00:00", formatDuration(3_600_000))
    }
}

class ReorderTest {

    private val list = listOf("a", "b", "c", "d")

    @Test
    fun `moving forward and backward both work`() {
        assertEquals(listOf("b", "c", "a", "d"), list.moved(0, 2))
        assertEquals(listOf("a", "d", "b", "c"), list.moved(3, 1))
    }

    @Test
    fun `out of range and no-op moves leave the list untouched`() {
        assertEquals(list, list.moved(0, 0))
        assertEquals(list, list.moved(-1, 2))
        assertEquals(list, list.moved(1, 99))
    }
}
