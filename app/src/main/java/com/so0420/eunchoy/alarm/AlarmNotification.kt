package com.so0420.eunchoy.alarm

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.so0420.eunchoy.R
import com.so0420.eunchoy.notif.NotificationChannels

/**
 * Shared builder for the alarm notification, used by BOTH the [com.so0420.eunchoy.notif.Notifier]
 * (background-safe post that carries the full-screen intent) and [AlarmRingerService] (its foreground
 * notification). Same id -> the two merge instead of stacking.
 */
object AlarmNotification {
    const val ID = 4242

    fun build(context: Context, title: String, body: String, url: String?, image: String?): Notification {
        val fullScreen = PendingIntent.getActivity(
            context,
            1,
            AlarmActivity.intent(context, title, body, url, image),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val dismiss = PendingIntent.getService(
            context,
            2,
            Intent(context, AlarmRingerService::class.java).setAction(AlarmRingerService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, NotificationChannels.ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setColor(0xFF4FA3E3.toInt())
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .addAction(R.drawable.ic_stop, "끄기", dismiss)
            .build()
    }
}
