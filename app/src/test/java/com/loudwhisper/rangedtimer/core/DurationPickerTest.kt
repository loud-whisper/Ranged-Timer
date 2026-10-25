package com.loudwhisper.rangedtimer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DurationPickerTest {

    @Test
    fun `pick stays in inclusive bounds over many draws`() {
        val random = Random(42)
        val seen = HashSet<Long>()
        repeat(200_000) {
            val s = pickSeconds(60, 90, random)
            assertTrue("out of range: $s", s in 3600L..5400L)
            seen.add(s)
        }
        assertTrue("lowest bound never drawn", 3600L in seen)
        assertTrue("highest bound never drawn", 5400L in seen)
    }

    @Test
    fun `pick is roughly uniform`() {
        val random = Random(7)
        val draws = 300_000
        var lowerHalf = 0
        repeat(draws) { if (pickSeconds(0, 100, random) < 3000L) lowerHalf++ }
        val share = lowerHalf.toDouble() / draws
        assertTrue("lower half share $share", share in 0.49..0.51)
    }

    @Test
    fun `equal bounds returns exact value`() {
        assertEquals(2700L, pickSeconds(45, 45))
    }

    @Test
    fun `zero range returns zero`() {
        assertEquals(0L, pickSeconds(0, 0))
    }

    @Test
    fun `validateRange rejects shortest above longest`() {
        assertNotNull(validateRange(90, 60))
    }

    @Test
    fun `validateRange rejects above 1000`() {
        assertNotNull(validateRange(10, 1001))
    }

    @Test
    fun `validateRange rejects negative`() {
        assertNotNull(validateRange(-1, 10))
    }

    @Test
    fun `validateRange accepts 0 and 1000`() {
        assertNull(validateRange(0, 1000))
    }
}
