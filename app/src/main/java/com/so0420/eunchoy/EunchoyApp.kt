package com.so0420.eunchoy

import android.app.Application
import com.so0420.eunchoy.notif.NotificationChannels
import com.so0420.eunchoy.work.PollScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class EunchoyApp : Application() {

    val container: AppContainer by lazy { AppContainer(this) }
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        appScope.launch {
            container.settings.loadNaverIntoSession()
            val s = container.settings.current()
            PollScheduler.ensurePeriodic(this@EunchoyApp, s.pollMinutes)
            if (s.fastPolling) PollScheduler.startFast(this@EunchoyApp)
        }
    }
}
