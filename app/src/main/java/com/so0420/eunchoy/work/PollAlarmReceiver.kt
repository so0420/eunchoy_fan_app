package com.so0420.eunchoy.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.so0420.eunchoy.EunchoyApp
import kotlinx.coroutines.launch

/**
 * Fires for the ~1-min "실시간 감시" poll (no foreground-service notification). Runs one poll, then
 * reschedules the next tick if fast polling is still enabled. An AndAllowWhileIdle alarm grants a
 * brief background allowlist, so the alarm ringer can still be started from here when needed.
 */
class PollAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? EunchoyApp ?: return
        val pending = goAsync()
        app.appScope.launch {
            try {
                Poller(app.container).runOnce()
            } catch (_: Exception) {
                // ignore; next tick will retry
            } finally {
                val stillOn = runCatching { app.container.settings.current().fastPolling }.getOrDefault(false)
                if (stillOn) PollScheduler.scheduleNextFast(context)
                pending.finish()
            }
        }
    }
}
