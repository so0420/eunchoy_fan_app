package com.so0420.eunchoy

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.so0420.eunchoy.data.SourceKey
import com.so0420.eunchoy.ui.MainScreen
import com.so0420.eunchoy.ui.theme.EunchoyTheme
import com.so0420.eunchoy.work.PollScheduler
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        maybeRequestNotificationPermission()
        val startRoute = routeForSource(intent?.getStringExtra(EXTRA_SOURCE))

        setContent {
            EunchoyTheme {
                MainScreen(startRoute = startRoute)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Re-arm the fast foreground poller from a guaranteed-foreground context (safe FGS start).
        lifecycleScope.launch {
            if (appContainer.settings.current().fastPolling) {
                PollScheduler.startFast(this@MainActivity)
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestNotif.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun routeForSource(sourceId: String?): String =
        when (sourceId?.let { SourceKey.from(it) }) {
            SourceKey.CAFE -> "cafe"
            SourceKey.YOUTUBE_MAIN, SourceKey.YOUTUBE_VOD -> "youtube"
            SourceKey.X -> "x"
            else -> "home"
        }

    companion object {
        const val EXTRA_SOURCE = "extra_source"
    }
}
