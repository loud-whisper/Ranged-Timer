package com.loudwhisper.rangedtimer.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Schedules the single wake-up. Uses setExactAndAllowWhileIdle on purpose:
 * setAlarmClock would publish the end time to the status bar and lock screen.
 */
object Scheduler {
    private const val REQUEST_SESSION = 1
    private const val REQUEST_TEST = 2

    private fun pending(ctx: Context, sessionId: String?, isTest: Boolean): PendingIntent {
        val intent = Intent(ctx, AlarmReceiver::class.java)
            .putExtra(AlarmReceiver.EXTRA_SESSION_ID, sessionId)
            .putExtra(AlarmReceiver.EXTRA_TEST, isTest)
        return PendingIntent.getBroadcast(
            ctx, if (isTest) REQUEST_TEST else REQUEST_SESSION, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun canScheduleExact(ctx: Context): Boolean =
        ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun schedule(ctx: Context, triggerElapsed: Long, sessionId: String?, isTest: Boolean) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx, sessionId, isTest)
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsed, pi)
        } else {
            // Without exact-alarm permission Android may deliver late; the setup check warns about this.
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsed, pi)
        }
    }

    fun cancel(ctx: Context, isTest: Boolean = false) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx, null, isTest))
    }
}
