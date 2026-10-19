package com.loudwhisper.rangedtimer.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Runs the end-of-session alert for at most three minutes.
 * The phone's ringer mode is read when the alert starts:
 * Silent or Vibrate gives vibration only; Normal gives the chosen ringtone plus vibration.
 */
class AlertService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val isTest = intent?.getBooleanExtra(EXTRA_TEST, false) ?: false
        startForeground(
            Notifications.ALERT_ID,
            Notifications.alert(this, isTest),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        if (!running) {
            running = true
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RangedTimer:alert")
                .apply { acquire(ALERT_MILLIS + 10_000L) }
            begin()
            handler.postDelayed({ stopSelf() }, ALERT_MILLIS)
        }
        return START_NOT_STICKY
    }

    private fun begin() {
        vibrate()
        val ringer = getSystemService(AudioManager::class.java).ringerMode
        if (ringer == AudioManager.RINGER_MODE_NORMAL) playRingtone()
    }

    private fun vibrate() {
        val v = getSystemService(VibratorManager::class.java).defaultVibrator
        vibrator = v
        // 1 s on, 0.5 s off, repeating from index 0 until cancelled.
        val effect = VibrationEffect.createWaveform(longArrayOf(1000L, 500L), intArrayOf(255, 0), 0)
        v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
    }

    private fun playRingtone() {
        val chosen = SessionController.store(this).load().settings.ringtoneUri?.let(Uri::parse)
        val candidates = listOfNotNull(
            chosen,
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
        for (uri in candidates) {
            val p = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(this@AlertService, uri)
                    isLooping = true
                    prepare()
                    start()
                }
            }.getOrNull()
            if (p != null) {
                player = p
                return
            }
        }
        // No playable sound: vibration alone continues.
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { player?.stop() }
        player?.release()
        player = null
        vibrator?.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        running = false
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.loudwhisper.rangedtimer.STOP_ALERT"
        private const val EXTRA_TEST = "test"
        const val ALERT_MILLIS = 180_000L

        fun start(ctx: Context, isTest: Boolean) {
            ctx.startForegroundService(Intent(ctx, AlertService::class.java).putExtra(EXTRA_TEST, isTest))
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, AlertService::class.java).setAction(ACTION_STOP))
        }
    }
}
