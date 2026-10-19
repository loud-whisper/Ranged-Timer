package com.loudwhisper.rangedtimer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLogicTest {

    // Started at epoch 1_000_000 ms, elapsed 50_000 ms, boot 4, picked 74 minutes.
    private val active = ActiveSession(
        id = "abc",
        startEpochMillis = 1_000_000L,
        startElapsedMillis = 50_000L,
        bootCount = 4,
        pickedSeconds = 74 * 60L,
    )
    private val pickedMillis = 74 * 60_000L

    @Test
    fun `end early records elapsed and flag`() {
        val s = endEarly(active, nowElapsed = 50_000L + 52 * 60_000L)
        assertEquals(Outcome.ENDED_EARLY, s.outcome)
        assertEquals(52 * 60L, s.actualSeconds)
        assertEquals(74 * 60L, s.pickedSeconds)
        assertEquals("abc", s.id)
        assertEquals(1_000_000L, s.startEpochMillis)
    }

    @Test
    fun `end early never exceeds picked`() {
        val s = endEarly(active, nowElapsed = 50_000L + pickedMillis + 30_000L)
        assertEquals(74 * 60L, s.actualSeconds)
    }

    @Test
    fun `complete records picked`() {
        val s = complete(active)
        assertEquals(Outcome.COMPLETED, s.outcome)
        assertEquals(74 * 60L, s.actualSeconds)
    }

    @Test
    fun `boot before end reschedules remaining`() {
        val d = onBoot(active, nowEpoch = 1_000_000L + 10 * 60_000L)
        assertEquals(BootDecision.Reschedule(pickedMillis - 10 * 60_000L), d)
    }

    @Test
    fun `boot after end records picked as interrupted`() {
        val d = onBoot(active, nowEpoch = 1_000_000L + 90 * 60_000L) as BootDecision.FireNow
        assertEquals(Outcome.INTERRUPTED, d.session.outcome)
        assertEquals(74 * 60L, d.session.actualSeconds)
    }

    @Test
    fun `rebase moves the elapsed start into the new boot`() {
        val r = rebase(active, nowEpoch = 1_000_000L + 10 * 60_000L, nowElapsed = 20_000L, bootCount = 5)
        assertEquals(20_000L - 10 * 60_000L, r.startElapsedMillis)
        assertEquals(5, r.bootCount)
    }

    @Test
    fun `open within grace returns null`() {
        val now = 50_000L + pickedMillis + 59_000L
        assertNull(onOpen(active, nowElapsed = now, currentBootCount = 4, nowEpoch = 0L))
    }

    @Test
    fun `open after grace returns interrupted`() {
        val now = 50_000L + pickedMillis + 61_000L
        val s = onOpen(active, nowElapsed = now, currentBootCount = 4, nowEpoch = 0L)!!
        assertEquals(Outcome.INTERRUPTED, s.outcome)
        assertEquals(74 * 60L, s.actualSeconds)
    }

    @Test
    fun `open after a reboot uses wall clock`() {
        val s = onOpen(active, nowElapsed = 1L, currentBootCount = 5, nowEpoch = 1_000_000L + pickedMillis + 120_000L)
        assertEquals(Outcome.INTERRUPTED, s!!.outcome)
        assertNull(onOpen(active, nowElapsed = 1L, currentBootCount = 5, nowEpoch = 1_000_000L + 60_000L))
    }

    @Test
    fun `stale alarm id does not match`() {
        assertFalse(alarmMatches(active, "other"))
        assertTrue(alarmMatches(active, "abc"))
    }

    @Test
    fun `null active does not match`() {
        assertFalse(alarmMatches(null, "abc"))
        assertFalse(alarmMatches(active, null))
    }
}
