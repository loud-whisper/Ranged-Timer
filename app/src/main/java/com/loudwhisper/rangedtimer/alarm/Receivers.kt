package com.loudwhisper.rangedtimer.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SessionController.onAlarm(
            context,
            intent.getStringExtra(EXTRA_SESSION_ID),
            intent.getBooleanExtra(EXTRA_TEST, false),
        )
    }

    companion object {
        const val EXTRA_SESSION_ID = "sessionId"
        const val EXTRA_TEST = "test"
    }
}

/** Restart and app-update events. */
class SystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> SessionController.onBootCompleted(context)
            Intent.ACTION_MY_PACKAGE_REPLACED -> SessionController.onPackageReplaced(context)
        }
    }
}
