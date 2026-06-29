package com.so0420.eunchoy

import android.app.Application
import android.util.Log
import com.so0420.eunchoy.notif.NotificationChannels
import com.so0420.eunchoy.work.PollScheduler
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class EunchoyApp : Application() {

    val container: AppContainer by lazy { AppContainer(this) }

    // A stray failure in a fire-and-forget job must never crash the process.
    private val handler = CoroutineExceptionHandler { _, e -> Log.w("EunchoyApp", "appScope error", e) }
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + handler)

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        appScope.launch {
            container.settings.loadNaverIntoSession()
            val s = container.settings.current()
            // WorkManager periodic is the reliable background baseline (15-min floor, no notification).
            PollScheduler.ensurePeriodic(this@EunchoyApp, s.pollMinutes)
            // Fast (~1-min) polling is now AlarmManager-based (no notification), so it's safe to arm
            // from anywhere — scheduling an alarm has no foreground requirement.
            if (s.fastPolling) PollScheduler.startFast(this@EunchoyApp)
        }
    }
}
