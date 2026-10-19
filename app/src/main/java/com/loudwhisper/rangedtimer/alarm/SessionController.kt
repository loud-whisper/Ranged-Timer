package com.loudwhisper.rangedtimer.alarm

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.loudwhisper.rangedtimer.core.ActiveSession
import com.loudwhisper.rangedtimer.core.BootDecision
import com.loudwhisper.rangedtimer.core.Session
import com.loudwhisper.rangedtimer.core.alarmMatches
import com.loudwhisper.rangedtimer.core.complete
import com.loudwhisper.rangedtimer.core.endEarly
import com.loudwhisper.rangedtimer.core.labelFor
import com.loudwhisper.rangedtimer.core.rememberCustom
import com.loudwhisper.rangedtimer.core.onBoot
import com.loudwhisper.rangedtimer.core.onOpen
import com.loudwhisper.rangedtimer.core.pickSeconds
import com.loudwhisper.rangedtimer.core.rebase
import com.loudwhisper.rangedtimer.data.Store
import java.text.DateFormat
import java.util.Date
import java.util.UUID

/** Connects the pure session rules to storage, the alarm and the alert. */
object SessionController {

    private const val TEST_DELAY_MILLIS = 10_000L

    fun store(ctx: Context) = Store(ctx.applicationContext.filesDir)

    private fun bootCount(ctx: Context): Int =
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT, 0)

    private fun startedText(active: ActiveSession): String =
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(active.startEpochMillis))

    fun start(ctx: Context): ActiveSession? {
        var started: ActiveSession? = null
        store(ctx).update { data ->
            if (data.activeSession != null) return@update data
            val active = ActiveSession(
                id = UUID.randomUUID().toString(),
                startEpochMillis = System.currentTimeMillis(),
                startElapsedMillis = SystemClock.elapsedRealtime(),
                bootCount = bootCount(ctx),
                pickedSeconds = pickSeconds(data.settings.shortestMinutes, data.settings.longestMinutes),
                label = labelFor(data.settings),
            )
            started = active
            data.copy(activeSession = active, settings = rememberCustom(data.settings, active.label))
        }
        started?.let {
            Scheduler.schedule(ctx, it.startElapsedMillis + it.pickedSeconds * 1000, it.id, isTest = false)
            Notifications.showSession(ctx, startedText(it))
        }
        return started
    }

    fun endEarly(ctx: Context): Session? {
        var recorded: Session? = null
        store(ctx).update { data ->
            val active = data.activeSession ?: return@update data
            val s = endEarly(active, SystemClock.elapsedRealtime())
            recorded = s
            data.copy(activeSession = null, sessions = data.sessions + s)
        }
        Scheduler.cancel(ctx)
        Notifications.cancelSession(ctx)
        return recorded
    }

    /** Called when the scheduled wake-up arrives. */
    fun onAlarm(ctx: Context, sessionId: String?, isTest: Boolean) {
        if (isTest) {
            AlertService.start(ctx, isTest = true)
            return
        }
        var fire = false
        store(ctx).update { data ->
            val active = data.activeSession
            if (!alarmMatches(active, sessionId)) return@update data
            fire = true
            data.copy(activeSession = null, sessions = data.sessions + complete(active!!))
        }
        if (fire) {
            Notifications.cancelSession(ctx)
            AlertService.start(ctx, isTest = false)
        }
    }

    fun onBootCompleted(ctx: Context) {
        var fire = false
        store(ctx).update { data ->
            val active = data.activeSession ?: return@update data
            val now = System.currentTimeMillis()
            when (val d = onBoot(active, now)) {
                is BootDecision.Reschedule -> {
                    val rebased = rebase(active, now, SystemClock.elapsedRealtime(), bootCount(ctx))
                    Scheduler.schedule(ctx, SystemClock.elapsedRealtime() + d.remainingMillis, rebased.id, isTest = false)
                    Notifications.showSession(ctx, startedText(rebased))
                    data.copy(activeSession = rebased)
                }
                is BootDecision.FireNow -> {
                    fire = true
                    data.copy(activeSession = null, sessions = data.sessions + d.session)
                }
            }
        }
        if (fire) AlertService.start(ctx, isTest = false)
    }

    /** After an app update the alarm may be gone; schedule it again in the same boot. */
    fun onPackageReplaced(ctx: Context) {
        val active = store(ctx).load().activeSession ?: return
        if (active.bootCount != bootCount(ctx)) return onBootCompleted(ctx)
        Scheduler.schedule(ctx, active.startElapsedMillis + active.pickedSeconds * 1000, active.id, isTest = false)
    }

    /** Returns a session recorded because its alert was missed, or null. */
    fun recoverOnOpen(ctx: Context): Session? {
        var recorded: Session? = null
        store(ctx).update { data ->
            val active = data.activeSession ?: return@update data
            val s = onOpen(active, SystemClock.elapsedRealtime(), bootCount(ctx), System.currentTimeMillis())
                ?: return@update data
            recorded = s
            data.copy(activeSession = null, sessions = data.sessions + s)
        }
        if (recorded != null) {
            Scheduler.cancel(ctx)
            Notifications.cancelSession(ctx)
        }
        return recorded
    }

    fun testIn10s(ctx: Context) {
        Scheduler.schedule(ctx, SystemClock.elapsedRealtime() + TEST_DELAY_MILLIS, null, isTest = true)
    }
}
