package com.loudwhisper.rangedtimer.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val SCHEMA_VERSION = 1

@Serializable
enum class Outcome {
    @SerialName("completed") COMPLETED,
    @SerialName("ended_early") ENDED_EARLY,
    @SerialName("interrupted") INTERRUPTED,
}

@Serializable
enum class Theme {
    @SerialName("system") SYSTEM,
    @SerialName("light") LIGHT,
    @SerialName("dark") DARK,
}

/** One finished session. Lengths are in seconds. */
@Serializable
data class Session(
    val id: String,
    val startEpochMillis: Long,
    val pickedSeconds: Long,
    val actualSeconds: Long,
    val outcome: Outcome,
)

/**
 * The session currently running. Both clocks are kept: elapsed realtime is immune
 * to clock changes but resets on reboot, so the wall-clock start covers reboots.
 */
@Serializable
data class ActiveSession(
    val id: String,
    val startEpochMillis: Long,
    val startElapsedMillis: Long,
    val bootCount: Int,
    val pickedSeconds: Long,
    val shutdownEpochMillis: Long? = null,
)

@Serializable
data class Settings(
    val shortestMinutes: Int = 60,
    val longestMinutes: Int = 90,
    val ringtoneUri: String? = null,
    val theme: Theme = Theme.SYSTEM,
)

@Serializable
data class AppData(
    val schemaVersion: Int = SCHEMA_VERSION,
    val settings: Settings = Settings(),
    val activeSession: ActiveSession? = null,
    val sessions: List<Session> = emptyList(),
)
