package com.so0420.eunchoy.notif

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** Notification channels. Channel settings are immutable after creation — bump the version suffix to change. */
object NotificationChannels {
    const val ALARM = "alarm_v1"     // alarm-style (DND/silent bypass)
    const val ALERT = "alert_v1"     // normal heads-up

    fun alarmSoundUri(context: Context): Uri =
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: Settings.System.DEFAULT_ALARM_ALERT_URI

    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val alarm = NotificationChannel(ALARM, "알람형 알림", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "방송 시작 등 알람처럼 울리는 알림 (방해금지/무음 모드에서도)"
            setSound(alarmSoundUri(context), alarmAttrs)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 400, 600, 400, 600)
            enableLights(true)
            lightColor = 0xFF4FA3E3.toInt()
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setBypassDnd(true) // effective once Notification Policy Access is granted
        }

        val alert = NotificationChannel(ALERT, "일반 알림", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "새 게시글 / 새 영상 등 일반 알림"
            enableVibration(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        nm.createNotificationChannels(listOf(alarm, alert))
        // Fast polling no longer uses a foreground service — remove its old channels entirely.
        runCatching { nm.deleteNotificationChannel("service_v1") }
        runCatching { nm.deleteNotificationChannel("service_v2") }
    }
}
