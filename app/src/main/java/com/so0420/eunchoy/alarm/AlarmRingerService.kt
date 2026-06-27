package com.so0420.eunchoy.alarm

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.so0420.eunchoy.R
import com.so0420.eunchoy.notif.AlertPayload
import com.so0420.eunchoy.notif.NotificationChannels

/**
 * Foreground service that *guarantees* audibility: it plays the device alarm sound on the ALARM
 * audio stream (USAGE_ALARM), which is not silenced by the ringer (silent/vibrate) and sounds
 * through Do-Not-Disturb. Pairs with a full-screen-intent notification -> [AlarmActivity].
 */
class AlarmRingerService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val sourceId = intent?.getStringExtra(EXTRA_SOURCE).orEmpty()
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "은초이 알림"
        val body = intent?.getStringExtra(EXTRA_BODY).orEmpty()
        val url = intent?.getStringExtra(EXTRA_URL)
        val image = intent?.getStringExtra(EXTRA_IMAGE)

        val notification = buildNotification(sourceId, title, body, url, image)
        ServiceCompat.startForeground(
            this,
            FG_ID,
            notification,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )

        acquireWakeLock()
        startSound()
        startVibration()
        return START_NOT_STICKY
    }

    private fun buildNotification(
        sourceId: String,
        title: String,
        body: String,
        url: String?,
        image: String?,
    ): android.app.Notification {
        val fullScreenIntent = PendingIntent.getActivity(
            this,
            REQ_FULLSCREEN,
            AlarmActivity.intent(this, title, body, url, image),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val dismiss = PendingIntent.getService(
            this,
            REQ_DISMISS,
            Intent(this, AlarmRingerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, NotificationChannels.ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setColor(0xFF4FA3E3.toInt())
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .addAction(R.drawable.ic_stop, "끄기", dismiss)
            .build()
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
        val pattern = longArrayOf(0, 700, 500)
        runCatching {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        }
    }

    override fun onDestroy() {
        runCatching { player?.run { if (isPlaying) stop(); release() } }
        player = null
        runCatching { vibrator?.cancel() }
        if (wakeLock?.isHeld == true) runCatching { wakeLock?.release() }
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        private const val FG_ID = 4242
        private const val REQ_FULLSCREEN = 1
        private const val REQ_DISMISS = 2
        private const val MAX_RING_MS = 3 * 60 * 1000L

        const val ACTION_STOP = "com.so0420.eunchoy.action.STOP_ALARM"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_URL = "url"
        const val EXTRA_IMAGE = "image"

        fun start(context: Context, payload: AlertPayload) {
            val intent = Intent(context, AlarmRingerService::class.java)
                .putExtra(EXTRA_SOURCE, payload.source.id)
                .putExtra(EXTRA_TITLE, payload.title)
                .putExtra(EXTRA_BODY, payload.body)
                .putExtra(EXTRA_URL, payload.url)
                .putExtra(EXTRA_IMAGE, payload.imageUrl)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, AlarmRingerService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
