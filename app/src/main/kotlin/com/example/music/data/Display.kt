package com.example.music.data

/**
 * Turning download filenames into something readable.
 *
 * Most local libraries are not neatly tagged albums — they are files pulled off the internet whose
 * "title" is the original filename and whose artist is missing entirely. MediaStore reports that
 * faithfully, which leaves a library of `Sia_-_Snowman(128k)` by `<unknown>`. Everything below
 * exists to make that browsable, and it runs once per track at query time, not during composition.
 */

/**
 * Multi-word video junk. These are unambiguous, so they can be removed even unbracketed.
 * Longest first, or "official video" would eat the tail of "official music video".
 */
private val PHRASE_NOISE = listOf(
    "official music video", "official lyric video", "official lyrical video",
    "official video", "official audio", "lyric video", "lyrical video",
    "music video", "full video", "full song",
)

/**
 * Single generic words. Only junk *inside brackets* — bare, they are ordinary names, and stripping
 * them turns "Official Secrets" into "Secrets" and the band "Audioslave" into a stump.
 */
private val WORD_NOISE = listOf("official", "lyrics", "lyric", "audio", "remastered", "hq", "hd", "4k", "mv")

private val NOISE = PHRASE_NOISE + WORD_NOISE

private val BITRATE = Regex("""[(\[]\s*\d{2,4}\s*k(bps)?\s*[)\]]""", RegexOption.IGNORE_CASE)
private val BRACKETED = Regex("""[(\[]([^)\]]*)[)\]]""")
private val YEAR_TAIL = Regex("""\s*[(\[]\s*(19|20)\d{2}\s*[)\]]\s*$""")
private val MULTISPACE = Regex("""\s{2,}""")
private val ARTIST_SPLIT = Regex("""\s+[-–—]\s+""")
private val EDGE_JUNK = Regex("""^[\s\-–—_,.|·]+|[\s\-–—_,.|·]+$""")

/**
 * A bracketed group is noise only when it is *entirely* noise — `(Official Video)` goes,
 * `(feat. Mina Niraula)` and `(Live at Wembley)` stay, because those name the recording.
 */
private fun stripBracketedNoise(s: String) = BRACKETED.replace(s) { m ->
    val inner = m.groupValues[1].trim().lowercase().trim(' ', '-', '_', ',', '.')
    if (inner.isEmpty() || NOISE.any { inner == it }) "" else m.value
}

// Compiled once. Building these per call meant ten regex compilations per track, and on a
// 227-track library that alone cost seconds of the library read.
private val PHRASE_NOISE_RX = PHRASE_NOISE.map {
    Regex("""(?<![\p{L}\d])${Regex.escape(it)}(?![\p{L}\d])""", RegexOption.IGNORE_CASE)
}

private fun stripBareNoise(s: String): String {
    var out = s
    for (rx in PHRASE_NOISE_RX) out = rx.replace(out, " ")
    return out
}

/**
 * Filenames cannot contain some punctuation, so downloaders replace an apostrophe with the same
 * underscore they use for spaces. Once underscores become spaces, "Don't" has already become
 * "Don t". Rejoin the handful of English contraction tails, which is the whole of the damage.
 */
// Case is the discriminator: a lost apostrophe leaves a lowercase tail ("Don t"), whereas a real
// one-letter word is capitalised ("Vitamin D", "Blues Brothers 2 B").
private val CONTRACTION = Regex("""(?<=\p{L})\s+(t|s|ll|re|ve|m|d)(?![\p{L}\d])""")

private fun repairContractions(s: String) = CONTRACTION.replace(s) { "'" + it.groupValues[1] }

/** True once the string has at least one letter or digit; punctuation alone is not a name. */
private fun String.hasSubstance() = any(Char::isLetterOrDigit)

/**
 * Generic single words are junk at the end of a title ("... Somewhere Only We Know Lyrics") and
 * meaningful anywhere else ("Official Secrets"), so position decides. Repeats, because these
 * filenames often stack two ("... Lyrics HD").
 */
private tailrec fun stripTrailingWordNoise(s: String): String {
    val trimmed = s.trimEnd(' ', '-', '_', ',', '.', '|')
    val last = trimmed.substringAfterLast(' ', "")
    if (last.isEmpty() || WORD_NOISE.none { it.equals(last, true) }) return trimmed
    val head = trimmed.substringBeforeLast(' ', "")
    // Never strip away the whole name: "Audio" alone stays "Audio".
    return if (head.hasSubstance()) stripTrailingWordNoise(head) else trimmed
}

private fun tidyString(raw: String, dropTrailingWords: Boolean = false): String =
    raw.replace('_', ' ')
        .let { BITRATE.replace(it, "") }
        .let(::stripBracketedNoise)
        .let(::stripBareNoise)
        .let { MULTISPACE.replace(it, " ") }
        .let { EDGE_JUNK.replace(it, "") }
        .let { YEAR_TAIL.replace(it, "") }
        .let { if (dropTrailingWords) stripTrailingWordNoise(it) else it }
        .let(::repairContractions)
        .trim()

private fun String?.isUnknown() =
    this == null || isBlank() || equals("unknown", true) || this == "<unknown>"

/**
 * Cleaned title and artist for one track.
 *
 * When the file carries no artist tag but its name reads `Artist - Title`, the left side is the
 * artist. Only the first separator counts: `Keane - Somewhere Only We Know` splits once, and a
 * title that merely contains a dash later keeps it.
 */
fun tidyNames(rawTitle: String, rawArtist: String?): Pair<String, String> {
    val cleanedTitle = tidyString(rawTitle, dropTrailingWords = true).takeIf { it.hasSubstance() }
        ?: rawTitle.trim().takeIf { it.hasSubstance() }
        ?: "Untitled"
    val cleanedArtist = if (rawArtist.isUnknown()) "" else tidyString(rawArtist!!)

    if (cleanedArtist.isNotBlank()) return cleanedTitle to cleanedArtist

    val split = ARTIST_SPLIT.find(cleanedTitle)
    if (split != null) {
        val left = EDGE_JUNK.replace(cleanedTitle.substring(0, split.range.first), "").trim()
        val right = EDGE_JUNK.replace(cleanedTitle.substring(split.range.last + 1), "").trim()
        // A leading fragment that is really the whole name ("- Snowman") is not an artist.
        if (left.isNotEmpty() && right.isNotEmpty() && left.length <= 40) {
            return right to left
        }
    }
    return cleanedTitle to "Unknown artist"
}

/** Album names are only worth showing when they say something; "download" does not. */
fun tidyAlbum(rawAlbum: String?): String {
    val cleaned = if (rawAlbum.isUnknown()) "" else tidyString(rawAlbum!!)
    return when {
        cleaned.isBlank() -> "No album"
        cleaned.equals("download", true) || cleaned.equals("downloads", true) -> "No album"
        cleaned.equals("music", true) || cleaned.equals("audio", true) -> "No album"
        else -> cleaned
    }
}

/** Up to two initials for the generated cover tile. */
fun initialsOf(text: String): String {
    val words = text.split(' ', '-', '_').filter { it.isNotBlank() && it.first().isLetterOrDigit() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> "${words[0].first()}${words[1].first()}".uppercase()
    }
}
