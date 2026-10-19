package com.loudwhisper.rangedtimer.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val TIME = DateTimeFormatter.ofPattern("HH:mm")

private fun outcomeText(o: Outcome) = when (o) {
    Outcome.COMPLETED -> "Completed"
    Outcome.ENDED_EARLY -> "Ended early"
    Outcome.INTERRUPTED -> "Interrupted"
}

/** Quotes a field when needed, and stops a spreadsheet from treating text as a formula. */
private fun field(text: String): String {
    val safe = if (text.isNotEmpty() && text[0] in "=+-@\t\r") "'$text" else text
    return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + safe.replace("\"", "\"\"") + "\""
    } else {
        safe
    }
}

private fun minutes(seconds: Long) = String.format(Locale.ROOT, "%.1f", seconds / 60.0)

/** A spreadsheet-friendly table: one row per session, oldest first. */
fun csvReport(sessions: List<Session>, zone: ZoneId): String = buildString {
    append("Date,Start time,Label,Minutes sat,Minutes picked,Outcome\n")
    sessions.sortedBy { it.startEpochMillis }.forEach { s ->
        val t = Instant.ofEpochMilli(s.startEpochMillis).atZone(zone)
        append(t.format(DATE)).append(',')
        append(t.format(TIME)).append(',')
        append(field(s.label)).append(',')
        append(minutes(s.actualSeconds)).append(',')
        append(minutes(s.pickedSeconds)).append(',')
        append(outcomeText(s.outcome)).append('\n')
    }
}
