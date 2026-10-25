package com.loudwhisper.rangedtimer.alarm

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager

object Haptics {
    /**
     * One short, firm tap confirming the session has started. Uses the alarm channel
     * because the phone is usually on Silent at this moment, where touch feedback is muted.
     */
    fun startConfirm(ctx: Context) {
        val v = ctx.getSystemService(VibratorManager::class.java).defaultVibrator
        v.vibrate(
            VibrationEffect.createOneShot(60L, 255),
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
        )
    }
}
