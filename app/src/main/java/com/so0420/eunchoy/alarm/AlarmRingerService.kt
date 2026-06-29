package com.so0420.eunchoy.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.so0420.eunchoy.notif.AlertPayload
import com.so0420.eunchoy.notif.NotificationChannels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Foreground service that *guarantees* audibility: it plays the device alarm sound on the ALARM
 * audio stream (USAGE_ALARM), which is not silenced by the ringer (silent/vibrate) and sounds
 * through Do-Not-Disturb. Started from a foreground context (the full-screen [AlarmActivity], or the
 * Notifier when the app itself is foregrounded) — never relied on from a background WorkManager start.
 */
class AlarmRingerService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val autoStop = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "은초이 알림"
        val body = intent?.getStringExtra(EXTRA_BODY).orEmpty()
        val url = intent?.getStringExtra(EXTRA_URL)
        val image = intent?.getStringExtra(EXTRA_IMAGE)

        // Promote to foreground first; if the OS denies a background FGS start, degrade gracefully
        // (the full-screen-intent notification still alerts) instead of crashing the process.
        try {
            ServiceCompat.startForeground(
                this,
                AlarmNotification.ID,
                AlarmNotification.build(this, title, body, url, image),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
            )
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Idempotent: a second alarm while ringing must not leak the prior player/wakelock.
        stopRinging()
        acquireWakeLock()
        startSound()
        startVibration()
        autoStop.postDelayed({ stopSelf() }, MAX_RING_MS)
        _ringing.value = true // lets the app show an in-app "끄기" even if the notification is hidden
        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "eunchoy:alarm").apply {
            setReferenceCounted(false)
            acquire(MAX_RING_MS)
        }
    }

    private fun startSound() {
        runCatching {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            player = MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(this@AlarmRingerService, NotificationChannels.alarmSoundUri(this@AlarmRingerService))
                isLooping = true
                setOnErrorListener { _, _, _ -> true } // swallow async prepare errors; FSI + vibration remain
                setOnPreparedListener { start() }
                prepareAsync()
            }
        }
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0)
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                val attrs = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_ALARM)
                    .build()
                vibrator?.vibrate(effect, attrs)
            } else {
                val audioAttrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                @Suppress("DEPRECATION")
                vibrator?.vibrate(effect, audioAttrs)
            }
        }
    }

    /** Releases all ringing resources. Safe to call repeatedly. */
    private fun stopRinging() {
        _ringing.value = false
        autoStop.removeCallbacksAndMessages(null)
        runCatching { player?.run { if (isPlaying) stop(); release() } }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        if (wakeLock?.isHeld == true) runCatching { wakeLock?.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        stopRinging()
        runCatching { NotificationManagerCompat.from(this).cancel(AlarmNotification.ID) }
        super.onDestroy()
    }

    companion object {
        private const val MAX_RING_MS = 3 * 60 * 1000L

        /** True while the alarm is actively ringing — the app observes this to offer an in-app stop. */
        private val _ringing = MutableStateFlow(false)
        val ringing: StateFlow<Boolean> = _ringing

        const val ACTION_STOP = "com.so0420.eunchoy.action.STOP_ALARM"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_URL = "url"
        const val EXTRA_IMAGE = "image"

        private fun intent(context: Context, title: String, body: String, url: String?, image: String?) =
            Intent(context, AlarmRingerService::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_BODY, body)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_IMAGE, image)

        fun start(context: Context, payload: AlertPayload) {
            ContextCompat.startForegroundService(
                context, intent(context, payload.title, payload.body, payload.url, payload.imageUrl),
            )
        }

        fun startRaw(context: Context, title: String, body: String, url: String?, image: String?) {
            ContextCompat.startForegroundService(context, intent(context, title, body, url, image))
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, AlarmRingerService::class.java).setAction(ACTION_STOP),
                )
            }
        }
    }
}
