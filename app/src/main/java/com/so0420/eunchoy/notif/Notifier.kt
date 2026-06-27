package com.so0420.eunchoy.notif

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.so0420.eunchoy.MainActivity
import com.so0420.eunchoy.R
import com.so0420.eunchoy.data.SourceKey
import com.so0420.eunchoy.data.net.Net
import com.so0420.eunchoy.alarm.AlarmNotification
import com.so0420.eunchoy.alarm.AlarmRingerService

/** A single new-item alert ready to be posted. */
data class AlertPayload(
    val source: SourceKey,
    val title: String,
    val body: String,
    val url: String?,        // tap target; null => open the app home
    val imageUrl: String? = null,
)

/** Posts alerts: either a normal heads-up notification or the alarm-style ringer. */
class Notifier(private val context: Context) {

    private val accent = 0xFF4FA3E3.toInt()

    /** Post an alert. When [alarm] is true, fires the DND/silent-bypassing ringer + full-screen alarm. */
    suspend fun post(payload: AlertPayload, alarm: Boolean) {
        if (alarm) {
            postAlarm(payload)
        } else {
            postNormal(payload, payload.imageUrl?.let { loadBitmap(it) })
        }
    }

    /**
     * Post the full-screen-intent alarm notification (allowed from the background; the ALARM channel's
     * USAGE_ALARM sound gives immediate audibility) and also try to start the looping ringer directly —
     * which succeeds when the app is foregrounded and otherwise degrades to the FSI -> AlarmActivity path.
     */
    @SuppressLint("MissingPermission") // guarded by canPostNotifications()
    private fun postAlarm(payload: AlertPayload) {
        if (canPostNotifications()) {
            runCatching {
                NotificationManagerCompat.from(context).notify(
                    AlarmNotification.ID,
                    AlarmNotification.build(context, payload.title, payload.body, payload.url, payload.imageUrl),
                )
            }
        }
        runCatching { AlarmRingerService.start(context, payload) }
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // guarded by canPostNotifications()
    private fun postNormal(payload: AlertPayload, image: Bitmap?) {
        if (!canPostNotifications()) return
        val builder = NotificationCompat.Builder(context, NotificationChannels.ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(payload.title)
            .setContentText(payload.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.body))
            .setColor(accent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(payload))
        if (image != null) {
            builder.setLargeIcon(image)
                .setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(image)
                        .bigLargeIcon(null as Bitmap?)
                        .setSummaryText(payload.body),
                )
        }
        runCatching {
            NotificationManagerCompat.from(context).notify(payload.source.id.hashCode(), builder.build())
        }
    }

    fun contentIntent(payload: AlertPayload): PendingIntent {
        val intent = if (payload.url.isNullOrBlank() || payload.source == SourceKey.CHZZK_LIVE) {
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_SOURCE, payload.source.id)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(payload.url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return PendingIntent.getActivity(
            context,
            payload.source.id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private suspend fun loadBitmap(url: String): Bitmap? {
        val bytes = Net.getBytes(url) ?: return null
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
    }
}
