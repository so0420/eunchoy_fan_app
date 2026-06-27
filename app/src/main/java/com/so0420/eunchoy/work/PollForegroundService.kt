package com.so0420.eunchoy.work

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.so0420.eunchoy.EunchoyApp
import com.so0420.eunchoy.MainActivity
import com.so0420.eunchoy.R
import com.so0420.eunchoy.notif.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Optional always-on poller for ~1-minute cadence (faster than WorkManager's 15-min floor).
 * Runs as a dataSync foreground service while "실시간 감시" is enabled in settings.
 */
class PollForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            FG_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        if (!started) {
            started = true
            val container = (applicationContext as EunchoyApp).container
            scope.launch {
                while (isActive) {
                    runCatching { Poller(container).runOnce() }
                    delay(INTERVAL_MS)
                }
            }
        }
        return START_STICKY
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, NotificationChannels.SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("은초이 실시간 알림 켜짐")
            .setContentText("새 소식을 실시간으로 확인하고 있어요")
            .setColor(0xFF4FA3E3.toInt())
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

    override fun onDestroy() {
        scope.cancel()
        started = false
        super.onDestroy()
    }

    companion object {
        private const val FG_ID = 7
        private const val INTERVAL_MS = 60_000L
        @Volatile
        private var started = false
    }
}
