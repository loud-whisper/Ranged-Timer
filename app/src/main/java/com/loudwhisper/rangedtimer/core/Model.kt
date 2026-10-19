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
    /** The label as it was named when the session ran. Older files have none: Meditation. */
    val label: String = DEFAULT_LABEL,
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
    val label: String = DEFAULT_LABEL,
)

@Serializable
data class Settings(
    val shortestMinutes: Int = 60,
    val longestMinutes: Int = 90,
    val ringtoneUri: String? = null,
    val theme: Theme = Theme.SYSTEM,
    val labelChoice: LabelChoice = LabelChoice.MEDITATION,
    /** The name typed for the fourth label; normalized when a session starts. */
    val otherLabelName: String = DEFAULT_OTHER_NAME,
    /** Up to five recent custom names, newest first, offered as one-tap picks. */
    val recentCustomLabels: List<String> = emptyList(),
)

@Serializable
data class AppData(
    val schemaVersion: Int = SCHEMA_VERSION,
    val settings: Settings = Settings(),
    val activeSession: ActiveSession? = null,
    val sessions: List<Session> = emptyList(),
)
