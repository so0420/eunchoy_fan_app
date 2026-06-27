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
            // WorkManager periodic is the reliable background baseline. The fast foreground service is
            // NOT started here: onCreate can run in a background process (e.g. when WorkManager spins the
            // process up), and starting a foreground service from the background throws on Android 12+.
            // MainActivity re-arms the fast service from the foreground instead.
            PollScheduler.ensurePeriodic(this@EunchoyApp, s.pollMinutes)
        }
    }
}
