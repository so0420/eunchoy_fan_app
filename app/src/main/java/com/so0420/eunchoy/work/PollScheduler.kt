package com.so0420.eunchoy.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Background polling:
 * - [ensurePeriodic]: WorkManager periodic (15-min floor) — always on, battery-friendly, no notification.
 * - fast (~1-min) polling: a self-rescheduling AlarmManager alarm (NO foreground-service notification).
 *   Best-effort while the device sleeps (Doze stretches the interval), exact-ish while in use. The
 *   "AndAllowWhileIdle" alarm also grants a brief allowlist so the alarm ringer can still start.
 */
object PollScheduler {
    private const val PERIODIC = "eunchoy_poll"
    private const val FAST_REQ = 91
    private const val FAST_INTERVAL_MS = 60_000L

    fun ensurePeriodic(context: Context, minutes: Int = 15) {
        val interval = minutes.toLong().coerceAtLeast(15)
        val request = PeriodicWorkRequestBuilder<PollWorker>(interval, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancelPeriodic(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }

    /** Kick off ~1-min polling (schedules the first tick; the receiver reschedules itself). */
    fun startFast(context: Context) = scheduleNextFast(context)

    /** Schedule the next fast-poll tick ~1 min out. Called by [PollAlarmReceiver] to form the loop. */
    fun scheduleNextFast(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = System.currentTimeMillis() + FAST_INTERVAL_MS
        runCatching {
            if (Build.VERSION.SDK_INT >= 23) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, fastPendingIntent(context))
            } else {
                @Suppress("DEPRECATION")
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, fastPendingIntent(context))
            }
        }
    }

    fun stopFast(context: Context) {
        runCatching { context.getSystemService(AlarmManager::class.java)?.cancel(fastPendingIntent(context)) }
    }

    private fun fastPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        FAST_REQ,
        Intent(context, PollAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
