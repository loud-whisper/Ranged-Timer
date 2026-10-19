package com.loudwhisper.rangedtimer.core

import com.loudwhisper.rangedtimer.data.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CodecTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val sample = AppData(
        settings = Settings(shortestMinutes = 55, longestMinutes = 95, ringtoneUri = "content://x", theme = Theme.DARK),
        activeSession = ActiveSession("a", 1000L, 50L, 3, 4000L, shutdownEpochMillis = 2000L),
        sessions = listOf(
            Session("s1", 1L, 4000L, 4000L, Outcome.COMPLETED),
            Session("s2", 2L, 5000L, 1200L, Outcome.ENDED_EARLY),
            Session("s3", 3L, 3600L, 3000L, Outcome.INTERRUPTED),
        ),
    )

    @Test
    fun `round trip preserves everything`() {
        assertEquals(sample, Codec.decode(Codec.encode(sample)).getOrThrow())
    }

    @Test
    fun `outcome uses readable names in the file`() {
        val text = Codec.encode(sample)
        assertTrue(text.contains("\"ended_early\""))
        assertTrue(text.contains("\"schemaVersion\""))
    }

    @Test
    fun `unknown keys ignored`() {
        val text = """{"schemaVersion":1,"futureField":true,"sessions":[]}"""
        assertEquals(AppData(), Codec.decode(text).getOrThrow())
    }

    @Test
    fun `wrong schemaVersion fails`() {
        assertTrue(Codec.decode("""{"schemaVersion":2}""").isFailure)
    }

    @Test
    fun `negative seconds fail`() {
        val text = """{"schemaVersion":1,"sessions":[{"id":"x","startEpochMillis":1,"pickedSeconds":-5,"actualSeconds":1,"outcome":"completed"}]}"""
        assertTrue(Codec.decode(text).isFailure)
    }

    @Test
    fun `garbage text fails`() {
        assertTrue(Codec.decode("not json at all").isFailure)
    }

    @Test
    fun `store save then load round trips`() {
        val store = Store(tmp.root)
        store.save(sample)
        assertEquals(sample, Store(tmp.root).load())
    }

    @Test
    fun `store load of missing file returns empty data`() {
        assertEquals(AppData(), Store(tmp.root).load())
    }

    @Test
    fun `store load of corrupt file returns empty and keeps the file aside`() {
        File(tmp.root, Store.FILE_NAME).writeText("{ broken")
        val loaded = Store(tmp.root).load()
        assertEquals(AppData(), loaded)
        val kept = tmp.root.listFiles()!!.filter { it.name.startsWith("ranged-timer.corrupt-") }
        assertEquals(1, kept.size)
        assertEquals("{ broken", kept[0].readText())
    }

    @Test
    fun `import replaces data and keeps a backup of the old file`() {
        val store = Store(tmp.root)
        store.save(AppData(sessions = listOf(Session("old", 1L, 60L, 60L, Outcome.COMPLETED))))
        val result = store.importFrom(Codec.encode(sample))
        assertTrue(result.isSuccess)
        assertEquals(sample.sessions, store.load().sessions)
        val backups = tmp.root.listFiles()!!.filter { it.name.startsWith("ranged-timer.before-import-") }
        assertEquals(1, backups.size)
    }

    @Test
    fun `import keeps the running session and settings`() {
        val store = Store(tmp.root)
        val running = ActiveSession("live", 5L, 6L, 1, 100L)
        store.save(AppData(settings = Settings(shortestMinutes = 70), activeSession = running))
        store.importFrom(Codec.encode(sample)).getOrThrow()
        val after = store.load()
        assertEquals(running, after.activeSession)
        assertEquals(70, after.settings.shortestMinutes)
    }

    @Test
    fun `failed import leaves data untouched`() {
        val store = Store(tmp.root)
        store.save(sample)
        assertTrue(store.importFrom("nope").isFailure)
        assertEquals(sample, store.load())
    }
}
