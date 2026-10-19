package com.loudwhisper.rangedtimer.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

fun minutes(seconds: Long): Long = (seconds / 60.0).roundToLong()

fun plural(n: Int, one: String, many: String = one + "s"): String = "$n " + if (n == 1) one else many

/** "45 min" under an hour, otherwise "1.5 hours". */
fun totalTime(hours: Double): String =
    if (hours < 1.0) "${(hours * 60).roundToLong()} min" else "${oneDecimal(hours)} hours"

fun oneDecimal(x: Double): String = String.format(Locale.getDefault(), "%.1f", x)

fun whole(x: Double): String = x.roundToLong().toString()

private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val dateTimeFormat = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a")
private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

fun timeOf(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(timeFormat)

fun dateTimeOf(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(dateTimeFormat)

fun dateOf(d: java.time.LocalDate): String = d.format(dateFormat)

fun periodName(days: Int): String = when (days) {
    7 -> "1 week"
    21 -> "3 weeks"
    30 -> "1 month"
    60 -> "2 months"
    90 -> "3 months"
    else -> "$days days"
}

private fun previousName(days: Int): String = when (days) {
    7 -> "week"
    30 -> "month"
    else -> periodName(days)
}

/** "Up 6 min/day vs the previous 3 weeks" style sentence. */
fun changeSentence(change: Double, days: Int): String {
    val rounded = oneDecimal(abs(change))
    val previous = previousName(days)
    return when {
        abs(change) < 0.05 -> "Same as the previous $previous"
        change > 0 -> "Up $rounded min/day vs the previous $previous"
        else -> "Down $rounded min/day vs the previous $previous"
    }
}
