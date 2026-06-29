package com.so0420.eunchoy.work

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Schedules background polling: WorkManager periodic (always) + optional fast foreground service. */
object PollScheduler {
    private const val PERIODIC = "eunchoy_poll"

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

    fun startFast(context: Context) {
        // Caller should already be foreground; guard anyway so a denied background start never crashes.
        runCatching {
            ContextCompat.startForegroundService(
                context, Intent(context, PollForegroundService::class.java),
            )
        }
    }

    fun stopFast(context: Context) {
        context.stopService(Intent(context, PollForegroundService::class.java))
    }
}
