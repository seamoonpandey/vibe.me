package com.example.music.data

/**
 * Pure sort/group logic. Kept free of Android types so it can be tested on the JVM — this is the
 * only part of the library layer with enough branching to get quietly wrong.
 */

fun sortSongs(songs: List<Song>, spec: SortSpec, playCounts: Map<Long, Int> = emptyMap()): List<Song> {
    val base: Comparator<Song> = when (spec.key) {
        SortKey.TITLE -> compareBy { it.title.lowercase() }
        SortKey.ARTIST -> compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.track })
        SortKey.ALBUM -> compareBy({ it.album.lowercase() }, { it.track })
        SortKey.DURATION -> compareBy { it.durationMs }
        SortKey.YEAR -> compareBy { it.year }
        SortKey.TRACK -> compareBy { it.track }
        SortKey.DATE_ADDED -> compareBy { it.dateAdded }
        SortKey.PLAY_COUNT -> compareBy { playCounts[it.id] ?: 0 }
    }
    // Tie-break on title so equal keys keep a stable, predictable order instead of MediaStore's.
    val full = base.thenBy { it.title.lowercase() }
    return songs.sortedWith(if (spec.descending) full.reversed() else full)
}

fun groupLabel(song: Song, key: GroupKey): String = when (key) {
    GroupKey.NONE -> ""
    GroupKey.ALBUM -> song.album
    GroupKey.ARTIST -> song.artist
    GroupKey.FOLDER -> song.folder
    GroupKey.YEAR -> if (song.year > 0) song.year.toString() else "Unknown year"
    GroupKey.LETTER -> song.title.firstOrNull()
        ?.takeIf { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"
}

/**
 * Sorts, then splits into labelled runs. Returns a single unlabelled group when grouping is off so
 * callers have one shape to render.
 */
fun groupSongs(
    songs: List<Song>,
    spec: SortSpec,
    playCounts: Map<Long, Int> = emptyMap(),
): List<Pair<String, List<Song>>> {
    val sorted = sortSongs(songs, spec, playCounts)
    if (spec.group == GroupKey.NONE) return listOf("" to sorted)
    return sorted.groupBy { groupLabel(it, spec.group) }
        .toList()
        .sortedBy { it.first.lowercase() }
}

fun filterSongs(songs: List<Song>, query: String): List<Song> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    return songs.filter {
        it.title.lowercase().contains(q) ||
            it.artist.lowercase().contains(q) ||
            it.album.lowercase().contains(q)
    }
}

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
