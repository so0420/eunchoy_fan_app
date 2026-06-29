package com.so0420.eunchoy.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationManagerCompat
import com.so0420.eunchoy.data.Config

/**
 * No-UI activity launched by a full-screen intent when "방송 자동 열기" is on: it opens the Chzzk
 * live page (browser / Chzzk app) and finishes. Routing through our own activity is what lets the
 * page open even when the trigger came from a background poll (background activity-start is blocked).
 */
class LiveAutoOpenActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(KEYGUARD_SERVICE) as? KeyguardManager)?.requestDismissKeyguard(this, null)
        }
        val url = intent.getStringExtra(EXTRA_URL) ?: Config.chzzkLiveUrl()
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        runCatching { NotificationManagerCompat.from(this).cancel(NOTIF_ID) }
        finish()
    }

    companion object {
        const val EXTRA_URL = "url"
        const val NOTIF_ID = 77

        fun intent(context: Context, url: String): Intent =
            Intent(context, LiveAutoOpenActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_URL, url)
    }
}
