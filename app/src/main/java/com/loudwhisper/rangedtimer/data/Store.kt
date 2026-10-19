package com.loudwhisper.rangedtimer.data

import com.loudwhisper.rangedtimer.core.AppData
import com.loudwhisper.rangedtimer.core.Codec
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The single JSON file that holds settings, the running session and all history.
 * Plain java.io only, so it is unit tested on the JVM.
 */
class Store(private val dir: File) {

    private val file get() = File(dir, FILE_NAME)

    fun load(): AppData = synchronized(LOCK) {
        val f = file
        if (!f.exists()) return AppData()
        val text = runCatching { f.readText() }.getOrNull()
        val decoded = text?.let { Codec.decode(it).getOrNull() }
        if (decoded != null) return decoded
        // Unreadable: keep it aside for inspection instead of overwriting it.
        f.renameTo(File(dir, "ranged-timer.corrupt-${System.currentTimeMillis()}.json"))
        AppData()
    }

    fun save(data: AppData): Unit = synchronized(LOCK) {
        dir.mkdirs()
        val tmp = File(dir, "$FILE_NAME.tmp")
        tmp.writeText(Codec.encode(data))
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun update(block: (AppData) -> AppData): AppData = synchronized(LOCK) {
        val next = block(load())
        save(next)
        next
    }

    fun exportTo(out: OutputStream) {
        val text = synchronized(LOCK) { Codec.encode(load()) }
        out.write(text.toByteArray())
    }

    /**
     * Replaces the session history with the one in [text]. The running session and
     * this phone's settings are kept. The previous file is copied aside first.
     */
    fun importFrom(text: String): Result<AppData> = synchronized(LOCK) {
        Codec.decode(text).map { imported ->
            val f = file
            if (f.exists()) f.copyTo(File(dir, "ranged-timer.before-import-${System.currentTimeMillis()}.json"))
            update { current -> current.copy(sessions = imported.sessions) }
        }
    }

    /**
     * Permanently deletes all recorded sessions, including the copies kept by import
     * and corrupt-file recovery. Settings and a running session are kept.
     */
    fun clearHistory(): Unit = synchronized(LOCK) {
        update { it.copy(sessions = emptyList()) }
        dir.listFiles { f -> f.name.startsWith("ranged-timer.before-import-") || f.name.startsWith("ranged-timer.corrupt-") }
            ?.forEach { it.delete() }
    }

    companion object {
        const val FILE_NAME = "ranged-timer.json"
        private val LOCK = Any()
    }
}
