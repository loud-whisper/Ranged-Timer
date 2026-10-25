package com.loudwhisper.rangedtimer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class StatsTest {

    private val zone: ZoneId = ZoneId.of("America/Toronto")
    private val today: LocalDate = LocalDate.of(2026, 10, 19)
    private var nextId = 0

    private fun session(day: LocalDate, minutes: Long, hour: Int = 6, minute: Int = 0) = Session(
        id = "s${nextId++}",
        startEpochMillis = LocalDateTime.of(day, java.time.LocalTime.of(hour, minute))
            .atZone(zone).toInstant().toEpochMilli(),
        pickedSeconds = minutes * 60,
        actualSeconds = minutes * 60,
        outcome = Outcome.COMPLETED,
    )

    private fun close(expected: Double, actual: Double?) =
        assertEquals(expected, actual ?: Double.NaN, 1e-9)

    @Test
    fun `session crossing midnight counts on start day`() {
        val day = LocalDate.of(2026, 3, 1)
        val totals = dayTotals(listOf(session(day, 60, hour = 23, minute = 30)), zone)
        assertEquals(mapOf(day to 3600L), totals)
    }

    @Test
    fun `two sessions same day sum`() {
        val totals = dayTotals(listOf(session(today, 30), session(today, 45, hour = 18)), zone)
        assertEquals(4500L, totals[today])
    }

    @Test
    fun `skipped days count as zero`() {
        val sessions = listOf(
            session(today.minusDays(29), 10),
            session(today.minusDays(5), 60),
            session(today, 60),
        )
        val p = periodStats(sessions, today, 7, zone)!!
        assertFalse(p.partial)
        assertEquals(7, p.daysCounted)
        close(120.0 / 7, p.avgMinutesPerDay)
        assertEquals(2, p.daysMeditated)
        close(60.0, p.avgSessionMinutes)
    }

    @Test
    fun `partial window divides by days so far`() {
        val sessions = listOf(session(today.minusDays(3), 60), session(today, 80))
        val p = periodStats(sessions, today, 90, zone)!!
        assertTrue(p.partial)
        assertEquals(4, p.daysCounted)
        close(140.0 / 4, p.avgMinutesPerDay)
        assertNull(p.changeVsPrevious)
    }

    @Test
    fun `change vs previous only when full`() {
        val sessions = listOf(session(today.minusDays(13), 60), session(today, 90))
        val p = periodStats(sessions, today, 7, zone)!!
        close(90.0 / 7 - 60.0 / 7, p.changeVsPrevious)
    }

    @Test
    fun `previous window without any history gives no change`() {
        val sessions = listOf(session(today.minusDays(6), 60), session(today, 90))
        val p = periodStats(sessions, today, 7, zone)!!
        assertFalse(p.partial)
        assertNull(p.changeVsPrevious)
    }

    @Test
    fun `no sessions in window gives zero average and no session length`() {
        val p = periodStats(listOf(session(today.minusDays(40), 60)), today, 7, zone)!!
        close(0.0, p.avgMinutesPerDay)
        assertEquals(0, p.daysMeditated)
        assertNull(p.avgSessionMinutes)
    }

    @Test
    fun `no sessions at all gives null`() {
        assertNull(periodStats(emptyList(), today, 7, zone))
    }

    @Test
    fun `DST spring forward day is still one day`() {
        val dstDay = LocalDate.of(2026, 3, 8)
        val sessions = listOf(
            session(dstDay.minusDays(20), 10),
            session(dstDay, 30, hour = 1, minute = 30),
            session(dstDay, 30, hour = 3, minute = 30),
        )
        assertEquals(3600L, dayTotals(sessions, zone)[dstDay])
        val p = periodStats(sessions, dstDay.plusDays(3), 7, zone)!!
        assertEquals(7, p.daysCounted)
        assertEquals(1, p.daysMeditated)
    }

    @Test
    fun `cycle boundaries`() {
        val first = LocalDate.of(2026, 1, 1)
        val sessions = listOf(session(first, 60))

        val oneCycle = cycles(sessions, first.plusDays(89), zone)
        assertEquals(1, oneCycle.size)
        assertTrue(oneCycle[0].isCurrent)
        assertEquals(90, oneCycle[0].dayOfCycle)
        assertEquals(first.plusDays(89), oneCycle[0].end)

        val twoCycles = cycles(sessions, first.plusDays(90), zone)
        assertEquals(2, twoCycles.size)
        assertEquals(2, twoCycles[0].index)
        assertTrue(twoCycles[0].isCurrent)
        assertEquals(1, twoCycles[0].dayOfCycle)
        assertEquals(first.plusDays(90), twoCycles[0].start)
        assertFalse(twoCycles[1].isCurrent)
        close(60.0 / 90, twoCycles[1].avgMinutesPerDay)
        close(1.0, twoCycles[1].totalHours)
    }

    @Test
    fun `cycle with no sessions still appears`() {
        val first = LocalDate.of(2026, 1, 1)
        val c = cycles(listOf(session(first, 60)), first.plusDays(200), zone)
        assertEquals(3, c.size)
        val middle = c.first { it.index == 2 }
        assertEquals(0, middle.daysMeditated)
        close(0.0, middle.avgMinutesPerDay)
        assertNull(middle.avgSessionMinutes)
    }

    @Test
    fun `trend never starts before first day`() {
        val first = LocalDate.of(2026, 5, 1)
        val totals = mapOf(first to 3600L, first.plusDays(1) to 1800L)
        val t = trend(totals, first, first.minusDays(3), first.plusDays(1))
        assertEquals(listOf(first, first.plusDays(1)), t.map { it.first })
        close(60.0, t[0].second)
        close(45.0, t[1].second)
    }

    @Test
    fun `trend uses trailing seven days`() {
        val first = LocalDate.of(2026, 5, 1)
        val totals = (0L..9L).associate { first.plusDays(it) to it * 60 }
        val t = trend(totals, first, first.plusDays(9), first.plusDays(9))
        // days 3..9 minutes = 3..9, mean 6
        close(6.0, t.single().second)
    }
}
