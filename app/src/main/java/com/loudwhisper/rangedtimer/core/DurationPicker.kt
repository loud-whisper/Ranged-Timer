package com.loudwhisper.rangedtimer.core

import kotlin.random.Random

const val MAX_MINUTES = 1000

/** Picks a session length in seconds, uniformly between the two bounds, both included. */
fun pickSeconds(shortestMinutes: Int, longestMinutes: Int, random: Random = Random.Default): Long {
    val lo = shortestMinutes * 60L
    val hi = longestMinutes * 60L
    return random.nextLong(lo, hi + 1)
}

/** Returns null when the range is usable, otherwise a message for the user. */
fun validateRange(shortest: Int, longest: Int): String? = when {
    shortest < 0 || longest < 0 -> "Minutes cannot be negative."
    shortest > MAX_MINUTES || longest > MAX_MINUTES -> "The most allowed is $MAX_MINUTES minutes."
    shortest > longest -> "Shortest must not be more than Longest."
    else -> null
}
