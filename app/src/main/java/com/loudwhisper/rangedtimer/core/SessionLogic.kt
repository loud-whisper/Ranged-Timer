package com.loudwhisper.rangedtimer.core

/** How long after the planned end the app waits before calling a session missed. */
const val MISSED_GRACE_MILLIS = 60_000L

sealed interface BootDecision {
    data class Reschedule(val remainingMillis: Long) : BootDecision
    data class FireNow(val session: Session) : BootDecision
}

private fun ActiveSession.toSession(actualSeconds: Long, outcome: Outcome) = Session(
    id = id,
    startEpochMillis = startEpochMillis,
    pickedSeconds = pickedSeconds,
    actualSeconds = actualSeconds.coerceIn(0L, pickedSeconds),
    outcome = outcome,
)

fun endEarly(active: ActiveSession, nowElapsed: Long): Session =
    active.toSession((nowElapsed - active.startElapsedMillis) / 1000, Outcome.ENDED_EARLY)

fun complete(active: ActiveSession): Session =
    active.toSession(active.pickedSeconds, Outcome.COMPLETED)

/**
 * After a restart, elapsed time has reset, so the decision uses the wall clock.
 * Android does not tell a stopped app when the phone shuts down, so a session whose
 * end passed while the phone was off is recorded at its picked length, marked interrupted.
 */
fun onBoot(active: ActiveSession, nowEpoch: Long): BootDecision {
    val end = active.startEpochMillis + active.pickedSeconds * 1000
    if (end > nowEpoch) return BootDecision.Reschedule(end - nowEpoch)
    return BootDecision.FireNow(active.toSession(active.pickedSeconds, Outcome.INTERRUPTED))
}

/** Re-expresses a session that survived a restart in the new boot's elapsed clock. */
fun rebase(active: ActiveSession, nowEpoch: Long, nowElapsed: Long, bootCount: Int): ActiveSession =
    active.copy(
        startElapsedMillis = nowElapsed - (nowEpoch - active.startEpochMillis),
        bootCount = bootCount,
    )

/** When the app opens and the end passed without an alert, the session is recorded as interrupted. */
fun onOpen(active: ActiveSession, nowElapsed: Long, currentBootCount: Int, nowEpoch: Long): Session? {
    val sinceStart = if (currentBootCount == active.bootCount) {
        nowElapsed - active.startElapsedMillis
    } else {
        nowEpoch - active.startEpochMillis
    }
    return if (sinceStart > active.pickedSeconds * 1000 + MISSED_GRACE_MILLIS) {
        active.toSession(active.pickedSeconds, Outcome.INTERRUPTED)
    } else {
        null
    }
}

fun alarmMatches(active: ActiveSession?, sessionId: String?): Boolean =
    active != null && sessionId != null && active.id == sessionId
