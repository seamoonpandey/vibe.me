package me.vibe.data

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
    // Fold on case so inconsistent tag casing does not split one album or artist into two
    // sections; the heading shows the spelling used by the most tracks in the group.
    return sorted.groupBy { groupLabel(it, spec.group).lowercase() }
        .toList()
        .sortedBy { it.first }
        .map { (_, items) ->
            val label = items.groupingBy { groupLabel(it, spec.group) }.eachCount().maxBy { it.value }.key
            label to items
        }
}

/**
 * Some keys only make sense one way round. Nobody asks for "recently added" and wants the oldest
 * file on the device; picking the key should pick the obvious direction with it.
 */
fun defaultDescending(key: SortKey) = when (key) {
    SortKey.DATE_ADDED, SortKey.PLAY_COUNT, SortKey.YEAR -> true
    else -> false
}

/**
 * Fuzzy match of one query against one field.
 *
 * Returns null when the query does not match at all, otherwise a score where higher is better.
 * Three tiers, because they mean genuinely different things to someone typing:
 *  - the field starts with the query, or a word in it does  -> best
 *  - the query appears somewhere as a run of characters     -> good
 *  - the query's letters appear in order but spread out     -> last resort, and the more spread
 *    out they are the worse it scores, so "sn" prefers "Snowman" over "SomethiNg"
 */
fun fuzzyScore(text: String, query: String): Int? {
    if (query.isEmpty()) return null
    val t = text.lowercase()
    val q = query.lowercase()

    if (t.startsWith(q)) return 1000 - t.length
    if (t.split(' ', '-', '(', '[', '.', ',').any { it.startsWith(q) }) return 900 - t.length

    val direct = t.indexOf(q)
    if (direct >= 0) return 800 - direct - t.length / 10

    // Subsequence: walk the query through the text, tracking how far apart the hits land.
    var ti = 0
    var spread = 0
    var lastHit = -1
    for (ch in q) {
        val found = t.indexOf(ch, ti)
        if (found < 0) return null
        if (lastHit >= 0) spread += found - lastHit - 1
        lastHit = found
        ti = found + 1
    }
    return (500 - spread * 4 - t.length / 10).coerceAtLeast(1)
}

/** Best score across the fields worth searching, weighted so a title hit outranks an album hit. */
fun songScore(song: Song, query: String): Int? {
    val title = fuzzyScore(song.title, query)?.plus(50)
    val artist = fuzzyScore(song.artist, query)?.plus(20)
    val album = fuzzyScore(song.album, query)
    return listOfNotNull(title, artist, album).maxOrNull()
}

fun filterSongs(songs: List<Song>, query: String): List<Song> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    return songs.mapNotNull { song -> songScore(song, q)?.let { song to it } }
        .sortedWith(compareByDescending<Pair<Song, Int>> { it.second }.thenBy { it.first.title.lowercase() })
        .map { it.first }
}

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
