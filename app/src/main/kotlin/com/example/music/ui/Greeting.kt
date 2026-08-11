package com.example.music.ui

/**
 * The line at the top of the library.
 *
 * Pure functions taking the hour rather than reading the clock, so the boundaries can actually be
 * tested — an off-by-one at midnight is the kind of thing nobody notices until the one person
 * using the app at 11pm is told good morning.
 */

fun greetingFor(hour: Int, name: String): String {
    val part = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Still up"
    }
    return if (name.isBlank()) part else "$part, $name"
}

private val PROMPTS = listOf(
    "What do you want to listen to?",
    "What's the mood today?",
    "Put something on.",
    "Your library is waiting.",
    "Pick something you love.",
)

/**
 * Chosen from the hour so it is stable while the app is open and changes across a day, rather
 * than shuffling on every recomposition.
 */
fun promptFor(hour: Int, dayOfYear: Int): String =
    PROMPTS[((hour + dayOfYear * 7) % PROMPTS.size + PROMPTS.size) % PROMPTS.size]
