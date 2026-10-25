package com.loudwhisper.rangedtimer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class LabelsTest {

    private val zone: ZoneId = ZoneId.of("America/Toronto")

    private fun s(label: String, day: LocalDate = LocalDate.of(2026, 10, 19), minutes: Long = 60, outcome: Outcome = Outcome.COMPLETED) = Session(
        id = label + day,
        startEpochMillis = LocalDateTime.of(day, LocalTime.of(6, 5)).atZone(zone).toInstant().toEpochMilli(),
        pickedSeconds = minutes * 60,
        actualSeconds = minutes * 60,
        outcome = outcome,
        label = label,
    )

    @Test
    fun `sessions saved before labels existed decode as Meditation`() {
        val old = """{"schemaVersion":1,"sessions":[{"id":"x","startEpochMillis":1,"pickedSeconds":60,"actualSeconds":60,"outcome":"completed"}]}"""
        val data = Codec.decode(old).getOrThrow()
        assertEquals("Meditation", data.sessions.single().label)
        assertEquals(LabelChoice.MEDITATION, data.settings.labelChoice)
        assertEquals("Other", data.settings.otherLabelName)
    }

    @Test
    fun `label for a choice uses the custom Other name`() {
        val settings = Settings(labelChoice = LabelChoice.OTHER, otherLabelName = "Guitar")
        assertEquals("Guitar", labelFor(settings))
        assertEquals("Reading", labelFor(settings.copy(labelChoice = LabelChoice.READING)))
    }

    @Test
    fun `filter Other groups every custom name`() {
        val all = listOf(s("Meditation"), s("Reading"), s("Workout"), s("Guitar"), s("Yoga"))
        assertEquals(listOf("Guitar", "Yoga"), filterSessions(all, LabelFilter.OTHER).map { it.label })
        assertEquals(listOf("Reading"), filterSessions(all, LabelFilter.READING).map { it.label })
        assertEquals(5, filterSessions(all, LabelFilter.ALL).size)
    }

    @Test
    fun `label names are trimmed and built-in names are matched regardless of case`() {
        assertEquals("Guitar", normalizeLabel("  Guitar "))
        assertEquals("Reading", normalizeLabel("reading"))
        assertNull(normalizeLabel("   "))
        assertNull(normalizeLabel("x".repeat(25)))
        assertNotNull(labelError(""))
        assertNull(labelError("HIIT"))
    }

    @Test
    fun `typed fourth label is normalized when a session starts`() {
        assertEquals("Workout", labelFor(Settings(labelChoice = LabelChoice.OTHER, otherLabelName = "workout")))
        assertEquals("Other", labelFor(Settings(labelChoice = LabelChoice.OTHER, otherLabelName = "  ")))
    }

    @Test
    fun `recent custom names keep the newest five without duplicates or built-ins`() {
        var st = Settings()
        listOf("Cardio", "HIIT", "Guitar", "cardio", "Yoga", "Swim", "Reading", "Piano").forEach { st = rememberCustom(st, it) }
        assertEquals(listOf("Piano", "Swim", "Yoga", "cardio", "Guitar"), st.recentCustomLabels)
    }

    @Test
    fun `relabel changes only the chosen session`() {
        val data = AppData(sessions = listOf(s("Cardio"), s("Meditation", LocalDate.of(2026, 10, 20))))
        val id = data.sessions[0].id
        val after = relabel(data, id, "HIIT")
        assertEquals(listOf("HIIT", "Meditation"), after.sessions.map { it.label })
    }

    @Test
    fun `report has a header and one row per session in time order`() {
        val sessions = listOf(
            s("Reading", LocalDate.of(2026, 10, 20), 30),
            s("Meditation", LocalDate.of(2026, 10, 19), 67).copy(pickedSeconds = 67 * 60),
            s("Workout", LocalDate.of(2026, 10, 21), 12, Outcome.ENDED_EARLY).copy(pickedSeconds = 40 * 60),
        )
        val lines = csvReport(sessions, zone).trimEnd().lines()
        assertEquals("Date,Start time,Label,Minutes sat,Minutes picked,Outcome", lines[0])
        assertEquals("2026-10-19,06:05,Meditation,67.0,67.0,Completed", lines[1])
        assertEquals("2026-10-20,06:05,Reading,30.0,30.0,Completed", lines[2])
        assertEquals("2026-10-21,06:05,Workout,12.0,40.0,Ended early", lines[3])
    }

    @Test
    fun `report escapes commas and quotes and defuses spreadsheet formulas`() {
        val lines = csvReport(listOf(s("Guitar, \"loud\""), s("=SUM(A1)", LocalDate.of(2026, 10, 20))), zone).trimEnd().lines()
        assertEquals("2026-10-19,06:05,\"Guitar, \"\"loud\"\"\",60.0,60.0,Completed", lines[1])
        assertEquals("2026-10-20,06:05,'=SUM(A1),60.0,60.0,Completed", lines[2])
    }

    @Test
    fun `cycles can be anchored to the first session of any label`() {
        val first = LocalDate.of(2026, 1, 1)
        val reading = listOf(s("Reading", first.plusDays(100)))
        val c = cycles(reading, first.plusDays(100), zone, anchor = first)
        assertEquals(2, c.first().index)
        assertEquals(first.plusDays(90), c.first().start)
    }
}
