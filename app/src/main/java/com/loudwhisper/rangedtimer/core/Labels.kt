package com.loudwhisper.rangedtimer.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val DEFAULT_LABEL = "Meditation"
const val DEFAULT_OTHER_NAME = "Other"
const val MAX_LABEL_LENGTH = 24

/** The three built-in labels, in the order they are shown. */
val FIXED_LABELS = listOf("Meditation", "Reading", "Workout")

/** Which label chip is selected for the next session. */
@Serializable
enum class LabelChoice {
    @SerialName("meditation") MEDITATION,
    @SerialName("reading") READING,
    @SerialName("workout") WORKOUT,
    @SerialName("other") OTHER,
}

/** History filter. OTHER groups every custom name, past and present. */
enum class LabelFilter { ALL, MEDITATION, READING, WORKOUT, OTHER }

const val MAX_RECENT_LABELS = 5

fun labelFor(settings: Settings): String = when (settings.labelChoice) {
    LabelChoice.MEDITATION -> "Meditation"
    LabelChoice.READING -> "Reading"
    LabelChoice.WORKOUT -> "Workout"
    LabelChoice.OTHER -> normalizeLabel(settings.otherLabelName) ?: DEFAULT_OTHER_NAME
}

/**
 * Trims a typed label and maps any spelling of a built-in name to that name,
 * so "reading" counts as Reading. Returns null when the name cannot be used.
 */
fun normalizeLabel(raw: String): String? {
    val t = raw.trim()
    if (t.isEmpty() || t.length > MAX_LABEL_LENGTH) return null
    return FIXED_LABELS.firstOrNull { it.equals(t, ignoreCase = true) } ?: t
}

/** A message for the user when a typed label cannot be used, otherwise null. */
fun labelError(raw: String): String? = when {
    raw.isBlank() -> "Enter a name."
    raw.trim().length > MAX_LABEL_LENGTH -> "Keep it to $MAX_LABEL_LENGTH characters."
    else -> null
}

/** Adds a custom name to the recent picks. Built-in names are not stored there. */
fun rememberCustom(settings: Settings, label: String): Settings {
    if (label in FIXED_LABELS) return settings
    val recent = (listOf(label) + settings.recentCustomLabels.filterNot { it.equals(label, ignoreCase = true) })
        .take(MAX_RECENT_LABELS)
    return settings.copy(recentCustomLabels = recent)
}

/** Changes the label of one recorded session, for fixing a session noted under the wrong label. */
fun relabel(data: AppData, sessionId: String, label: String): AppData =
    data.copy(sessions = data.sessions.map { if (it.id == sessionId) it.copy(label = label) else it })

fun filterSessions(sessions: List<Session>, filter: LabelFilter): List<Session> = when (filter) {
    LabelFilter.ALL -> sessions
    LabelFilter.MEDITATION -> sessions.filter { it.label == "Meditation" }
    LabelFilter.READING -> sessions.filter { it.label == "Reading" }
    LabelFilter.WORKOUT -> sessions.filter { it.label == "Workout" }
    LabelFilter.OTHER -> sessions.filter { it.label !in FIXED_LABELS }
}
