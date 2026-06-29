package com.so0420.eunchoy.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.so0420.eunchoy.EunchoyApp
import kotlinx.coroutines.launch

/** Re-arms the WorkManager periodic poll after a reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return
        val app = context.applicationContext as? EunchoyApp ?: return
        // Keep the process alive while we read settings + reschedule (work runs after onReceive returns).
        val pending = goAsync()
        app.appScope.launch {
            try {
                val s = app.container.settings.current()
                PollScheduler.ensurePeriodic(context, s.pollMinutes)
                // Fast polling is AlarmManager-based now (no FGS), so re-arming it from boot is fine.
                if (s.fastPolling) PollScheduler.startFast(context)
            } finally {
                pending.finish()
            }
        }
    }
}
