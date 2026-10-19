package com.loudwhisper.rangedtimer.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

const val CYCLE_DAYS = 90

/** Rolling windows shown on the History screen, in days. */
val PERIOD_WINDOWS = listOf(7, 21, 30, 60, 90)

data class PeriodStats(
    val windowDays: Int,
    /** Days used as the divisor: the window, or fewer when history is shorter. */
    val daysCounted: Int,
    val partial: Boolean,
    val avgMinutesPerDay: Double,
    val daysMeditated: Int,
    val avgSessionMinutes: Double?,
    /** Current average minus the previous equal-length window; null when not comparable. */
    val changeVsPrevious: Double?,
)

data class CycleSummary(
    val index: Int,
    val start: LocalDate,
    val end: LocalDate,
    val isCurrent: Boolean,
    val dayOfCycle: Int,
    val avgMinutesPerDay: Double,
    val daysMeditated: Int,
    val avgSessionMinutes: Double?,
    val totalHours: Double,
)

fun Session.day(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(startEpochMillis).atZone(zone).toLocalDate()

/** Seconds sat per local calendar day; a session belongs to the day it started. */
fun dayTotals(sessions: List<Session>, zone: ZoneId): Map<LocalDate, Long> =
    sessions.groupBy { it.day(zone) }.mapValues { (_, list) -> list.sumOf { it.actualSeconds } }

private fun daysInclusive(from: LocalDate, to: LocalDate): Int =
    (ChronoUnit.DAYS.between(from, to) + 1).toInt()

private class RangeSummary(val avgMinutesPerDay: Double, val daysMeditated: Int, val avgSessionMinutes: Double?, val totalSeconds: Long)

private fun summarize(sessions: List<Session>, zone: ZoneId, from: LocalDate, to: LocalDate, divisorDays: Int): RangeSummary {
    val inRange = sessions.filter { it.day(zone) in from..to }
    val total = inRange.sumOf { it.actualSeconds }
    val daysMeditated = inRange.groupBy { it.day(zone) }.count { (_, l) -> l.sumOf { it.actualSeconds } > 0 }
    val avgSession = if (inRange.isEmpty()) null else inRange.sumOf { it.actualSeconds } / 60.0 / inRange.size
    return RangeSummary(total / 60.0 / divisorDays, daysMeditated, avgSession, total)
}

fun periodStats(sessions: List<Session>, today: LocalDate, windowDays: Int, zone: ZoneId): PeriodStats? {
    if (sessions.isEmpty()) return null
    val firstDay = sessions.minOf { it.day(zone) }
    val windowStart = today.minusDays(windowDays - 1L)
    val partial = firstDay.isAfter(windowStart)
    val effectiveStart = if (partial) firstDay else windowStart
    val daysCounted = daysInclusive(effectiveStart, today).coerceAtLeast(1)
    val current = summarize(sessions, zone, effectiveStart, today, daysCounted)

    var change: Double? = null
    if (!partial) {
        val prevEnd = windowStart.minusDays(1)
        val prevStart = windowStart.minusDays(windowDays.toLong())
        if (!firstDay.isAfter(prevEnd)) {
            val prevEffectiveStart = if (firstDay.isAfter(prevStart)) firstDay else prevStart
            val prev = summarize(sessions, zone, prevEffectiveStart, prevEnd, daysInclusive(prevEffectiveStart, prevEnd))
            change = current.avgMinutesPerDay - prev.avgMinutesPerDay
        }
    }

    return PeriodStats(
        windowDays = windowDays,
        daysCounted = daysCounted,
        partial = partial,
        avgMinutesPerDay = current.avgMinutesPerDay,
        daysMeditated = current.daysMeditated,
        avgSessionMinutes = current.avgSessionMinutes,
        changeVsPrevious = change,
    )
}

/** Trailing mean of up to 7 days of minutes for each day in [from, to], never earlier than firstDay. */
fun trend(dayTotals: Map<LocalDate, Long>, firstDay: LocalDate, from: LocalDate, to: LocalDate): List<Pair<LocalDate, Double>> {
    val out = ArrayList<Pair<LocalDate, Double>>()
    var d = if (from.isBefore(firstDay)) firstDay else from
    while (!d.isAfter(to)) {
        val start = d.minusDays(6).let { if (it.isBefore(firstDay)) firstDay else it }
        var sum = 0L
        var x = start
        while (!x.isAfter(d)) {
            sum += dayTotals[x] ?: 0L
            x = x.plusDays(1)
        }
        out.add(d to sum / 60.0 / daysInclusive(start, d))
        d = d.plusDays(1)
    }
    return out
}

/** All 90-day cycles from the first session to today, newest first. */
fun cycles(sessions: List<Session>, today: LocalDate, zone: ZoneId): List<CycleSummary> {
    if (sessions.isEmpty()) return emptyList()
    val firstDay = sessions.minOf { it.day(zone) }
    if (today.isBefore(firstDay)) return emptyList()
    val currentIndex = (ChronoUnit.DAYS.between(firstDay, today) / CYCLE_DAYS).toInt() + 1
    return (currentIndex downTo 1).map { n ->
        val start = firstDay.plusDays(CYCLE_DAYS.toLong() * (n - 1))
        val end = start.plusDays(CYCLE_DAYS - 1L)
        val isCurrent = n == currentIndex
        val dayOfCycle = if (isCurrent) daysInclusive(start, today) else CYCLE_DAYS
        val s = summarize(sessions, zone, start, if (isCurrent) today else end, dayOfCycle)
        CycleSummary(
            index = n,
            start = start,
            end = end,
            isCurrent = isCurrent,
            dayOfCycle = dayOfCycle,
            avgMinutesPerDay = s.avgMinutesPerDay,
            daysMeditated = s.daysMeditated,
            avgSessionMinutes = s.avgSessionMinutes,
            totalHours = s.totalSeconds / 3600.0,
        )
    }
}
