package com.so0420.eunchoy.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.so0420.eunchoy.data.model.UpdateInfo
import com.so0420.eunchoy.data.net.Net
import com.so0420.eunchoy.data.update.ApkInstaller
import com.so0420.eunchoy.ui.theme.SkyPrimary
import kotlinx.coroutines.launch

@Composable
fun UpdateDialog(info: UpdateInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var message by remember { mutableStateOf<String?>(null) }

    fun startUpdate() {
        if (!ApkInstaller.canInstall(context)) {
            message = "‘출처를 알 수 없는 앱 설치’를 허용한 뒤 다시 눌러주세요."
            ApkInstaller.openInstallPermissionSettings(context)
            return
        }
        downloading = true
        message = null
        progress = 0f
        scope.launch {
            val file = ApkInstaller.apkFile(context)
            val ok = Net.download(info.downloadUrl, file) { progress = it }
            downloading = false
            if (ok) ApkInstaller.install(context, file) else message = "다운로드에 실패했어요. 다시 시도해주세요."
        }
    }

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        icon = { Text("🚀", fontSize = 22.sp) },
        title = { Text("새 버전 v${info.version}") },
        text = {
            Column {
                if (downloading) {
                    Text(
                        "다운로드 중... ${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = SkyPrimary,
                    )
                } else {
                    Text(
                        info.notes.ifBlank { "새로운 업데이트가 있어요." },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                    message?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            if (!downloading) TextButton(onClick = { startUpdate() }) { Text("업데이트") }
        },
        dismissButton = {
            if (!downloading) TextButton(onClick = onDismiss) { Text("나중에") }
        },
    )
}
