package com.so0420.eunchoy.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.so0420.eunchoy.EunchoyApp

/** Battery-friendly periodic poll (WorkManager, 15-min floor). */
class PollWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as EunchoyApp).container
        return try {
            Poller(container).runOnce()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
