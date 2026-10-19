package com.loudwhisper.rangedtimer.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.loudwhisper.rangedtimer.MainActivity
import com.loudwhisper.rangedtimer.R

object Notifications {
    private const val CHANNEL_SESSION = "session"
    private const val CHANNEL_ALERT = "alert"
    const val SESSION_ID = 1
    const val ALERT_ID = 2

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SESSION, "Session in progress", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Quiet reminder that a session is running. Never shows the end time."
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, "Session end alert", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shown while the end-of-session alert is going. The app plays its own sound and vibration."
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun showSession(ctx: Context, startedText: String) {
        ensureChannels(ctx)
        val n = Notification.Builder(ctx, CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Session in progress")
            .setContentText("Started $startedText")
            .setOngoing(true)
            .setContentIntent(openApp(ctx))
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(SESSION_ID, n) }
    }

    fun cancelSession(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).cancel(SESSION_ID)
    }

    fun alert(ctx: Context, isTest: Boolean): Notification {
        ensureChannels(ctx)
        val stop = PendingIntent.getService(
            ctx, 1, Intent(ctx, AlertService::class.java).setAction(AlertService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(ctx, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (isTest) "Test alert" else "Session complete")
            .setContentText("Tap Stop to end the alert")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(openApp(ctx))
            .setDeleteIntent(stop)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }
}
