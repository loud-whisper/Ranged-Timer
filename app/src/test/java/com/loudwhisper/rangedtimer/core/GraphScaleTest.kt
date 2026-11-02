package com.loudwhisper.rangedtimer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class GraphScaleTest {

    @Test
    fun guideLinesEvery30MinutesUpToTheRoundedTop() {
        val s = graphScale(77.0)
        assertEquals(90, s.topMinutes)
        assertEquals(listOf(30, 60, 90), s.lineMinutes)
    }

    @Test
    fun aDayJustOver90AddsThe120Line() {
        assertEquals(listOf(30, 60, 90, 120), graphScale(95.0).lineMinutes)
    }

    @Test
    fun emptyGraphStillShowsThe30Line() {
        val s = graphScale(0.0)
        assertEquals(30, s.topMinutes)
        assertEquals(listOf(30), s.lineMinutes)
    }

    @Test
    fun exactMultipleIsTheTop() {
        assertEquals(120, graphScale(120.0).topMinutes)
    }

    @Test
    fun longDaysWidenTheStepSoTheGraphStaysReadable() {
        // 30-minute lines up to 240 min (8 lines), then wider steps keep it to 8 or fewer.
        assertEquals(8, graphScale(240.0).lineMinutes.size)
        val s = graphScale(300.0)
        assertEquals(listOf(60, 120, 180, 240, 300), s.lineMinutes)
        val huge = graphScale(1000.0)
        assert(huge.lineMinutes.size <= 8)
        assert(huge.topMinutes >= 1000)
        assertEquals(0, huge.topMinutes % 30)
    }
}
