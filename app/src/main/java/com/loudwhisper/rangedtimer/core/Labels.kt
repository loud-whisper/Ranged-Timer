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

fun labelFor(settings: Settings): String = when (settings.labelChoice) {
    LabelChoice.MEDITATION -> "Meditation"
    LabelChoice.READING -> "Reading"
    LabelChoice.WORKOUT -> "Workout"
    LabelChoice.OTHER -> settings.otherLabelName
}

fun filterSessions(sessions: List<Session>, filter: LabelFilter): List<Session> = when (filter) {
    LabelFilter.ALL -> sessions
    LabelFilter.MEDITATION -> sessions.filter { it.label == "Meditation" }
    LabelFilter.READING -> sessions.filter { it.label == "Reading" }
    LabelFilter.WORKOUT -> sessions.filter { it.label == "Workout" }
    LabelFilter.OTHER -> sessions.filter { it.label !in FIXED_LABELS }
}

/** Returns null when the name can be used for the Other label, otherwise a message. */
fun validateOtherName(name: String): String? {
    val t = name.trim()
    return when {
        t.isEmpty() -> "Enter a name."
        t.length > MAX_LABEL_LENGTH -> "Keep it to $MAX_LABEL_LENGTH characters."
        FIXED_LABELS.any { it.equals(t, ignoreCase = true) } -> "That name is already a label."
        else -> null
    }
}
