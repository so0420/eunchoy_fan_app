package com.so0420.eunchoy.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.so0420.eunchoy.MainActivity
import com.so0420.eunchoy.R
import com.so0420.eunchoy.ui.theme.EunchoyTheme
import com.so0420.eunchoy.ui.theme.SkyBackground
import com.so0420.eunchoy.ui.theme.SkyPrimary
import com.so0420.eunchoy.ui.theme.SkyPrimaryContainer

/** Full-screen alarm shown when an alarm-style alert fires (over the lock screen, screen on). */
class AlarmActivity : ComponentActivity() {

    private data class AlarmData(val title: String, val body: String, val url: String?, val image: String?)

    private var data by mutableStateOf(AlarmData("은초이 알림", "", null, null))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockscreen()
        data = readData(intent)
        // Started from a foreground Activity -> always an allowed FGS start, even when the alarm
        // originated from a background poll (the FSI launched us here).
        AlarmRingerService.startRaw(this, data.title, data.body, data.url, data.image)

        setContent {
            EunchoyTheme {
                BackHandler { dismiss() }
                AlarmScreen(
                    title = data.title,
                    body = data.body,
                    imageUrl = data.image,
                    onOpen = { open() },
                    onDismiss = { dismiss() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        data = readData(intent)
        AlarmRingerService.startRaw(this, data.title, data.body, data.url, data.image)
    }

    private fun readData(i: Intent?) = AlarmData(
        title = i?.getStringExtra(EXTRA_TITLE) ?: "은초이 알림",
        body = i?.getStringExtra(EXTRA_BODY).orEmpty(),
        url = i?.getStringExtra(EXTRA_URL),
        image = i?.getStringExtra(EXTRA_IMAGE),
    )

    private fun dismiss() {
        AlarmRingerService.stop(this)
        finish()
    }

    private fun open() {
        AlarmRingerService.stop(this)
        openTarget(data.url)
        finish()
    }

    private fun showOverLockscreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(KEYGUARD_SERVICE) as? KeyguardManager)
                ?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }
    }

    private fun openTarget(url: String?) {
        val intent = if (url.isNullOrBlank()) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_URL = "url"
        const val EXTRA_IMAGE = "image"

        fun intent(context: Context, title: String, body: String, url: String?, image: String?): Intent =
            Intent(context, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_BODY, body)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_IMAGE, image)
    }
}

@Composable
private fun AlarmScreen(
    title: String,
    body: String,
    imageUrl: String?,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(SkyPrimaryContainer, SkyBackground)),
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(SkyPrimary),
        )
        Spacer(Modifier.height(20.dp))
        Text("🔔 은초이 알림", color = SkyPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (body.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!imageUrl.isNullOrBlank()) {
            Spacer(Modifier.height(20.dp))
            Card(shape = RoundedCornerShape(20.dp)) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                )
            }
        }
        Spacer(Modifier.height(36.dp))
        Button(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SkyPrimary),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("보러 가기", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("끄기", fontSize = 17.sp)
        }
    }
}
