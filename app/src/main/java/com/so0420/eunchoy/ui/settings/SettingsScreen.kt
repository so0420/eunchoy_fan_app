package com.so0420.eunchoy.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.widget.Toast
import com.so0420.eunchoy.BuildConfig
import com.so0420.eunchoy.data.NotifyMode
import com.so0420.eunchoy.data.SourceKey
import com.so0420.eunchoy.data.model.UpdateInfo
import com.so0420.eunchoy.data.update.UpdateChecker
import com.so0420.eunchoy.notif.NotifPermissions
import com.so0420.eunchoy.ui.update.UpdateDialog
import kotlinx.coroutines.launch
import com.so0420.eunchoy.ui.components.LoadingBox
import com.so0420.eunchoy.ui.components.SectionHeader
import com.so0420.eunchoy.ui.theme.SkyPrimary
import com.so0420.eunchoy.ui.theme.SkySurface
import com.so0420.eunchoy.ui.theme.SuccessGreen
import com.so0420.eunchoy.ui.util.appViewModel

@Composable
fun SettingsScreen(contentPadding: PaddingValues, onNaverLogin: () -> Unit) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val vm: SettingsViewModel = appViewModel { SettingsViewModel(it, appCtx) }
    val state by vm.state.collectAsState()
    val s = state

    val tick = rememberResumeTick()
    val notifOk = remember(tick) { NotifPermissions.hasPostNotifications(ctx) && NotifPermissions.notificationsEnabled(ctx) }
    val dndOk = remember(tick) { NotifPermissions.hasDndAccess(ctx) }
    val fsiOk = remember(tick) { NotifPermissions.canUseFullScreenIntent(ctx) }
    val exactOk = remember(tick) { NotifPermissions.canScheduleExactAlarms(ctx) }
    val batteryOk = remember(tick) { NotifPermissions.isIgnoringBatteryOptimizations(ctx) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    fun runUpdateCheck() {
        checking = true
        scope.launch {
            val info = UpdateChecker.check()
            checking = false
            if (info != null) update = info
            else Toast.makeText(ctx, "이미 최신 버전이에요", Toast.LENGTH_SHORT).show()
        }
    }

    if (s == null) {
        LoadingBox()
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 28.dp,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item { SectionHeader("알림 권한 설정") }
        item {
            PermissionCard {
                PermissionRow(
                    "알림 표시", "앱이 알림을 보낼 수 있도록 허용", notifOk,
                ) {
                    if (Build.VERSION.SDK_INT >= 33 && !NotifPermissions.hasPostNotifications(ctx)) {
                        notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        NotifPermissions.openAppNotificationSettings(ctx)
                    }
                }
                PermissionRow(
                    "방해금지 모드 통과", "DND/무음에서도 알람이 울리게 함", dndOk,
                ) { NotifPermissions.openDndAccessSettings(ctx) }
                PermissionRow(
                    "전체화면 알림", "잠금화면 위로 알람 화면 띄우기", fsiOk,
                ) { NotifPermissions.openFullScreenIntentSettings(ctx) }
                PermissionRow(
                    "정확한 알람", "정시 알람 예약 허용", exactOk,
                ) { NotifPermissions.openExactAlarmSettings(ctx) }
                PermissionRow(
                    "배터리 최적화 제외", "백그라운드에서 안정적으로 감시", batteryOk, last = true,
                ) { NotifPermissions.openBatteryOptimizationSettings(ctx) }
            }
        }

        item { SectionHeader("실시간 감시") }
        item {
            PermissionCard {
                ToggleRow(
                    title = "실시간 감시 (약 1분 간격)",
                    subtitle = "더 빠른 알림을 위해 항상 켜둠 (배터리 사용 ↑)",
                    checked = s.fastPolling,
                    onChange = vm::setFastPolling,
                    last = true,
                )
            }
            if (!s.fastPolling) {
                Text(
                    "기본 모드에서는 약 ${s.pollMinutes}분마다 새 소식을 확인해요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
        }

        item { SectionHeader("소스별 알림") }
        item {
            PermissionCard {
                SourceKey.entries.forEachIndexed { i, key ->
                    SourceRow(
                        emoji = key.emoji,
                        label = key.label,
                        mode = s.prefs(key).mode,
                        onMode = { vm.setMode(key, it) },
                        last = i == SourceKey.entries.lastIndex,
                    )
                }
            }
            Text(
                "‘알람’은 방해금지·무음 모드에서도 알람처럼 계속 울려요. ‘알림’은 일반 푸시 알림이에요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        item { SectionHeader("네이버 카페 로그인") }
        item {
            PermissionCard {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (s.naverLoggedIn) "로그인됨" else "로그인 안 됨",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "회원공개 카페 글까지 보려면 로그인하세요",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (s.naverLoggedIn) {
                        TextButton(onClick = vm::logoutNaver) { Text("로그아웃") }
                    } else {
                        OutlinedButton(onClick = onNaverLogin) { Text("로그인") }
                    }
                }
            }
        }

        item { SectionHeader("고급") }
        item { XBridgeField(initial = s.xBridgeUrl, onSave = vm::setXBridge) }

        item { SectionHeader("정보 · 업데이트") }
        item {
            PermissionCard {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "현재 버전 v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "새 버전이 나오면 앱에서 바로 업데이트할 수 있어요",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = SkyPrimary,
                        )
                    } else {
                        OutlinedButton(onClick = { runUpdateCheck() }) { Text("업데이트 확인") }
                    }
                }
            }
        }

        item {
            Text(
                "은초이 모아보기 · 비공식 팬 제작물\n각 플랫폼의 비공개 API/RSS를 사용하며, 정책 변경 시 일부 기능이 동작하지 않을 수 있어요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        }
    }

    update?.let { info -> UpdateDialog(info) { update = null } }
}

@Composable
private fun PermissionCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SkySurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) { Column { content() } }
}

@Composable
private fun PermissionRow(
    title: String,
    desc: String,
    granted: Boolean,
    last: Boolean = false,
    onFix: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (granted) Icons.Filled.CheckCircle else Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = if (granted) SuccessGreen else SkyPrimary,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!granted) {
            TextButton(onClick = onFix) { Text("설정") }
        }
    }
    if (!last) Divider()
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    last: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = switchColors())
    }
    if (!last) Divider()
}

private val MODE_OPTIONS = listOf(
    NotifyMode.OFF to "끄기",
    NotifyMode.NORMAL to "알림",
    NotifyMode.ALARM to "알람",
)

@Composable
private fun SourceRow(
    emoji: String,
    label: String,
    mode: NotifyMode,
    onMode: (NotifyMode) -> Unit,
    last: Boolean,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            "$emoji  $label",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            MODE_OPTIONS.forEachIndexed { i, (m, lbl) ->
                SegmentedButton(
                    selected = mode == m,
                    onClick = { onMode(m) },
                    shape = SegmentedButtonDefaults.itemShape(i, MODE_OPTIONS.size),
                ) { Text(lbl) }
            }
        }
    }
    if (!last) Divider()
}

@Composable
private fun XBridgeField(initial: String, onSave: (String) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("X 새 글 알림 브리지 (RSS) URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onSave(text.trim()) }) { Text("저장") }
        }
    }
}

@Composable
private fun Divider() {
    androidx.compose.material3.HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(start = 16.dp),
    )
}

@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = SkySurface,
    checkedTrackColor = SkyPrimary,
)

@Composable
private fun rememberResumeTick(): Int {
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return tick
}
