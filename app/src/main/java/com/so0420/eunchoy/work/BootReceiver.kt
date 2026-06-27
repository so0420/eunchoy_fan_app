package com.so0420.eunchoy.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.so0420.eunchoy.EunchoyApp
import kotlinx.coroutines.launch

/** Re-arms polling after a reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return
        val app = context.applicationContext as? EunchoyApp ?: return
        app.appScope.launch {
            val s = app.container.settings.current()
            PollScheduler.ensurePeriodic(context, s.pollMinutes)
            if (s.fastPolling) PollScheduler.startFast(context)
        }
    }
}
