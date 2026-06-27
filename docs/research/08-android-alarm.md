# Android "Alarm-Style" Notification — Implementation Research (API 26–35, 2025/2026)

Goal: when a watched source (e.g. a streamer goes live) triggers, the app must **ring like an alarm** —
audible even in **Do-Not-Disturb (DND)** and **silent/vibrate** mode, optionally light up the screen
(full-screen), vibrate, and be **toggleable per source**. This doc covers the verified Android platform
behavior, exact permission/constant names, manifest, and Kotlin code.

> Scope note: Targets minSdk 26 (Android 8.0, first version with mandatory `NotificationChannel`) through
> targetSdk 35 (Android 15). Forward note on Android 16 / API 36 at the end (lower confidence).
> All "auto-granted" / Play-policy statements below are about apps **distributed via Google Play**.

---

## 0. TL;DR recommended architecture

For "ring like an alarm even in DND" the **single reliable mechanism is `AudioAttributes.USAGE_ALARM`
audio played on the alarm stream**, NOT the notification channel sound. The notification channel + DND
bypass + full-screen intent are the *UX wrapper*; the actual guaranteed sound comes from your own
`MediaPlayer`/`Ringtone` with `USAGE_ALARM` running inside a **foreground service** (or fired by an exact
`AlarmManager.setAlarmClock`).

Recommended stack:

1. **Per-source toggle** → one shared high-importance `NotificationChannel` (`alarm_alerts`) +
   an app-level (DataStore/Room) boolean per source. Do **not** create a channel per source if you have
   many sources; gate posting in code. (Channel-per-source is fine only if the set is small and stable,
   because users expect per-channel system controls.)
2. **Detection cadence** → a **persistent foreground service** (`type=specialUse` or `dataSync`) doing
   ~1-min polling **only while the user has armed alerts**; otherwise **WorkManager** periodic (15-min
   floor) as the battery-friendly default. See §6.
3. **When a trigger fires** → post a notification on `alarm_alerts` with `setBypassDnd(true)` channel +
   **full-screen intent** to an alarm Activity (`turnScreenOn`/`showWhenLocked`), AND start/keep a
   foreground service that plays a looping `USAGE_ALARM` sound + vibrate + partial wakelock. The sound is
   what actually guarantees audibility; the channel/FSI guarantee visibility.
4. **Exact-time variant** (scheduled stream start time) → `AlarmManager.setAlarmClock()` (see §5), which is
   the most DND/Doze-resilient scheduler and shows the system alarm icon.

---

## 1. Permissions cheat-sheet (AndroidManifest + runtime)

```xml
<!-- Notifications -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />            <!-- runtime, API 33+ -->
<uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY" />   <!-- to make setBypassDnd effective -->
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />       <!-- normal perm; gated on API 34+ (see §2) -->

<!-- Sound / vibration -->
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />        <!-- optional; helps audio routing -->
<uses-permission android:name="android.permission.WAKE_LOCK" />

<!-- Foreground service (see §3/§6) -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />                    <!-- API 28+ -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />        <!-- API 34+ if type=specialUse -->
<!-- or -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />     <!-- API 34+ if type=mediaPlayback -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />          <!-- API 34+ if type=dataSync (polling) -->

<!-- Exact alarms (see §5) -->
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />   <!-- API 31+; user-grantable, denied-by-default on 33+ installs -->
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />        <!-- API 33+; auto-granted but ONLY for true alarm/clock apps (Play-reviewed) -->

<!-- Survive reboot if you re-arm alarms/services -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- Battery exemption (see §6) — Play-restricted, justify carefully -->
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

Runtime-prompt permissions: `POST_NOTIFICATIONS` (API 33+). The rest are either normal/manifest, or
"special access" granted through a Settings screen (DND policy, full-screen intent, exact alarm,
battery optimization) — you cannot prompt for those with `requestPermissions()`; you must deep-link
to Settings.

---

## 2. NotificationChannel: high importance, alarm sound, bypass DND

### 2.1 Key facts (verified)

- `NotificationChannel` is **mandatory** for posting on API 26+ (Android 8.0). Importance is per-channel.
  `NotificationManager.IMPORTANCE_HIGH` = makes a sound + shows as heads-up.
- **Channel settings are immutable after creation.** Once `createNotificationChannel()` runs, changing
  importance/sound/bypassDnd in code is ignored; only the user can change them in system settings. To
  change behavior in an update you must use a **new channel id** (or delete+recreate, which is user-hostile).
  So decide sound/importance/bypassDnd up front.
- `setSound(uri, audioAttributes)` sets the channel's sound. For an alarm timbre use the device alarm
  sound and **`AudioAttributes` with `USAGE_ALARM` + `CONTENT_TYPE_SONIFICATION`**.
- `setBypassDnd(true)` lets notifications on this channel interrupt in DND — **but it only takes effect if
  your app holds Notification Policy Access** (`ACCESS_NOTIFICATION_POLICY` granted via the special-access
  Settings screen). Without policy access the call is silently ineffective. (Verified: Android channels
  docs + `NotificationChannel.setBypassDnd` reference.)
- IMPORTANT nuance: the **channel sound does NOT reliably play through silent/DND just because importance
  is high.** DND bypass affects *whether the notification alerts*; for guaranteed *alarm-volume* audio that
  ignores the ringer, you still want your own `USAGE_ALARM` player (see §3). Treat the channel sound as a
  best-effort fallback.

### 2.2 Code — create the alarm channel once (e.g. in `Application.onCreate`)

```kotlin
const val CHANNEL_ALARM = "alarm_alerts"  // bump id if you ever change behavior

fun createAlarmChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val nm = context.getSystemService(NotificationManager::class.java)

    val alarmUri: Uri = RingtoneManager.getActualDefaultRingtoneUri(
        context, RingtoneManager.TYPE_ALARM
    ) ?: Settings.System.DEFAULT_ALARM_ALERT_URI            // or a bundled raw resource

    val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)              // routes to STREAM_ALARM, ignores ringer/DND
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    val channel = NotificationChannel(
        CHANNEL_ALARM,
        "Live alerts",
        NotificationManager.IMPORTANCE_HIGH                 // heads-up + sound
    ).apply {
        description = "Rings when a followed source goes live"
        setSound(alarmUri, attrs)
        enableVibration(true)
        vibrationPattern = longArrayOf(0, 600, 400, 600, 400, 600)
        enableLights(true)
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        setBypassDnd(true)                                  // effective ONLY with policy access (below)
    }
    nm.createNotificationChannel(channel)
}
```

### 2.3 Notification Policy Access (required for bypassDnd)

```kotlin
fun hasDndAccess(context: Context): Boolean {
    val nm = context.getSystemService(NotificationManager::class.java)
    return nm.isNotificationPolicyAccessGranted
}

fun openDndAccessSettings(context: Context) {
    // No runtime prompt exists; deep-link to the special-access list.
    context.startActivity(
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
```

Flow: check `isNotificationPolicyAccessGranted` → if false, explain why and send the user to
`Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`. Re-create (or first-create) the channel **after**
access is granted, since the bypassDnd attribute is only honored when access exists and the channel is
immutable afterward.

### 2.4 Per-source toggle

Recommended: ONE channel (`alarm_alerts`) + an app-side enabled flag per source (Room/DataStore). Before
posting, check the flag; skip posting (and don't start the sound service) if the source is muted. This
keeps the system notification settings simple and avoids dozens of system channels.
Use stable, distinct notification IDs per source so a live alert can be updated/cancelled independently:

```kotlin
val notifId = sourceId.hashCode()
```

If you instead expose per-source system channels, create one channel per source id (each separately
bypass-DND), but be aware each appears individually in Android Settings.

---

## 3. Guaranteed sound regardless of ringer/DND — the actual ringing engine

### 3.1 Why a dedicated player

`AudioAttributes.USAGE_ALARM` audio is played on **`STREAM_ALARM`**, which is **not silenced by the
ringer (silent/vibrate) and is allowed to sound through DND** (DND silences ring/notification/media/system
streams by default but leaves the **alarm** stream audible — that is exactly how the clock app rings in
DND). This is the most robust path and is independent of notification-channel behavior. (Verified:
AOSP audio attributes / AudioAttributes reference; widely relied on by alarm + critical-alert apps. Note
real-world caveat: a handful of OEM ROMs misroute the alarm stream — see §7.)

### 3.2 Foreground service that plays a looping alarm + vibrates + holds a wakelock

Manifest:

```xml
<service
    android:name=".alarm.AlarmRingerService"
    android:exported="false"
    android:foregroundServiceType="specialUse">           <!-- or "mediaPlayback"; see 3.4 -->
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="alarm_alert_playback" />           <!-- free-form; reviewed by Play -->
</service>
```

Service (Kotlin, abbreviated):

```kotlin
class AlarmRingerService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sourceId = intent?.getStringExtra("sourceId").orEmpty()

        // 1) Promote to foreground IMMEDIATELY (must call within ~5s of start, with the type).
        startForeground(
            FG_ID,
            buildAlarmNotification(this, sourceId),         // includes full-screen intent (see §4)
            if (Build.VERSION.SDK_INT >= 34)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )

        // 2) Partial wakelock so CPU/audio keeps running with screen off.
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "app:alarm")
            .also { it.acquire(2 * 60 * 1000L) }            // bounded timeout

        // 3) Looping alarm on the ALARM stream — bypasses silent & DND.
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: Settings.System.DEFAULT_ALARM_ALERT_URI
        player = MediaPlayer().apply {
            setAudioAttributes(attrs)
            setDataSource(this@AlarmRingerService, uri)
            isLooping = true
            prepare()
            start()
        }

        // 4) Vibrate in a loop (index 0 = repeat from start).
        vibrator = if (Build.VERSION.SDK_INT >= 31)
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") (getSystemService(VIBRATOR_SERVICE) as Vibrator)
        val pattern = longArrayOf(0, 700, 500)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, /* repeat */ 0))

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        player?.run { if (isPlaying) stop(); release() }; player = null
        vibrator?.cancel()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        super.onDestroy()
    }
    override fun onBind(i: Intent?): IBinder? = null
    companion object { const val FG_ID = 42 }
}
```

Stop ringing by `stopService()` / `stopSelf()` (e.g. from the "Dismiss" action or when the alarm Activity
is dismissed). Optionally raise `STREAM_ALARM` volume via `AudioManager` if it is 0 (ask user consent;
do not silently override volume in Play-published apps).

### 3.3 Alternative player: `Ringtone`

`RingtoneManager.getRingtone(ctx, uri)` then `ringtone.audioAttributes = USAGE_ALARM attrs`;
`ringtone.isLooping = true` (API 28+). `MediaPlayer` gives more control (gain ramp, prepareAsync,
completion) and is preferred for a long-running service.

### 3.4 Which foreground-service type? (API 34+ mandatory typing)

On **targetSdk 34+ every FGS must declare a type** in the manifest AND hold the matching
`FOREGROUND_SERVICE_*` permission, else `startForeground()` throws
`MissingForegroundServiceTypeException`/`SecurityException`. (Verified: fgs-types-required.)

Options for the ringer:
- **`mediaPlayback`** (`FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`):
  semantically "continue audio playback." No runtime permission. Reasonable since you literally play audio.
  Caveat: Play may scrutinize whether it's "user-engaged media"; an alarm is arguably playback but not a
  media player. On Android 15+, `mediaPlayback` FGS **cannot be started from a `BOOT_COMPLETED` receiver.**
- **`specialUse`** (`FOREGROUND_SERVICE_SPECIAL_USE`, `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`): catch-all;
  requires the `<property ... PROPERTY_SPECIAL_USE_FGS_SUBTYPE>` with a free-form justification that
  **Google Play reviews**. Best fit for "alarm alert that isn't user-initiated media playback."

Recommendation: `specialUse` for the brief ringer service, with a clear subtype string. If you also run a
long polling service, type that one `dataSync` (or `specialUse`). Both `mediaPlayback` and `specialUse`
require **no runtime permission**; only the manifest `FOREGROUND_SERVICE_*` declaration.

### 3.5 Waking / showing on the lock screen

Use a partial wakelock (above) to keep audio alive with the screen off. To **turn the screen on** and show
your alarm UI, do it from the **full-screen-intent Activity** (§4) using `setTurnScreenOn(true)` +
`setShowWhenLocked(true)` (API 27+) rather than `FULL_WAKE_LOCK` (deprecated).

---

## 4. Full-screen intent (light up the screen like an incoming call/alarm)

### 4.1 The Android 14 (API 34) restriction (verified)

- `USE_FULL_SCREEN_INTENT` is a *normal* permission, but for apps **targeting API 34+** Google Play
  **auto-grants it only to apps whose core function is calling or alarms/clock.** All other apps installed
  from Play have it **revoked/denied by default** (policy effective **May 31, 2024**; from **Jan 22, 2025**
  non-qualifying apps must surface an in-app prompt to the user on new installs on Android 14+).
- Check at runtime: **`NotificationManager.canUseFullScreenIntent()`** (API 34+). Returns whether the
  full-screen intent will actually launch full-screen.
- If denied, deep-link the user to grant it:
  **`Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`** (the "Manage full screen intents" / "Use full
  screen intents" page under **Special app access**). Pass your package via the intent data Uri.
- **Fallback when not granted:** the system does **not** launch full-screen; it **demotes the notification
  to a heads-up notification** (HUN) instead. So your alert still appears, just not full-screen. (Verified:
  AOSP fsi-limits.)

Because this app is arguably an "alarm" use case, you *may* qualify for auto-grant via Play, but do NOT
rely on it — always check `canUseFullScreenIntent()` and provide the Settings deep-link.

```kotlin
fun ensureFsi(context: Context) {
    val nm = context.getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                   Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
```

### 4.2 Build the notification with a full-screen intent

```kotlin
fun buildAlarmNotification(context: Context, sourceId: String): Notification {
    val fullScreen = PendingIntent.getActivity(
        context, sourceId.hashCode(),
        Intent(context, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("sourceId", sourceId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    return NotificationCompat.Builder(context, CHANNEL_ALARM)
        .setSmallIcon(R.drawable.ic_live)
        .setContentTitle("LIVE now")
        .setContentText("Your source just went live")
        .setPriority(NotificationCompat.PRIORITY_HIGH)        // pre-O heads-up
        .setCategory(NotificationCompat.CATEGORY_ALARM)        // or CATEGORY_CALL
        .setOngoing(true)
        .setAutoCancel(false)
        .setFullScreenIntent(fullScreen, /* highPriority */ true)
        .addAction(R.drawable.ic_stop, "Dismiss", dismissPendingIntent(context, sourceId))
        .build()
}
```

### 4.3 The full-screen Activity (turn screen on, show over lockscreen)

```kotlin
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(KEYGUARD_SERVICE) as KeyguardManager)
                .requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        // setContent { ... Dismiss / Open buttons that stopService(AlarmRingerService) ... }
    }
}
```

`setCategory(CATEGORY_CALL)` + full-screen intent is the canonical "incoming call screen" pattern;
`CATEGORY_ALARM` aligns with the alarm framing. Use whichever matches your Play declaration.

---

## 5. Exact-time variant — `AlarmManager` (for known stream start times)

When you know the scheduled start time, an exact alarm is more battery-friendly and Doze-resilient than
polling. `setAlarmClock()` is the strongest: it is **exempt from Doze**, shows the system "next alarm"
icon, and the system treats it as a user-visible alarm.

### 5.1 Permissions (verified)

- **`SCHEDULE_EXACT_ALARM`** (API 31+): needed for `setExact*` / `setAlarmClock`. On devices running
  Android 14, **denied by default** for newly installed apps targeting API 33+. Check
  `AlarmManager.canScheduleExactAlarms()`; if false, send the user to
  `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`. Listen for
  `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` to react when granted.
- **`USE_EXACT_ALARM`** (API 33+): a *normal*, auto-granted permission that needs **no** user grant — but
  **Google Play restricts it to apps whose core purpose is alarms/clock/calendar.** If your app is
  genuinely an alarm-style app you can declare it and skip the `SCHEDULE_EXACT_ALARM` prompt; otherwise
  expect Play rejection. Pick exactly one strategy.
- Note: `setAlarmClock()` and `setExactAndAllowWhileIdle()` both require one of the above. Plain
  `setExact()` via an `OnAlarmListener` does not require the permission but is less reliable for wake-from-idle.

### 5.2 Code

```kotlin
fun scheduleExact(context: Context, triggerAtMillis: Long, sourceId: String) {
    val am = context.getSystemService(AlarmManager::class.java)
    if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                   Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return
    }
    val fire = PendingIntent.getBroadcast(
        context, sourceId.hashCode(),
        Intent(context, AlarmReceiver::class.java).putExtra("sourceId", sourceId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    // setAlarmClock: Doze-exempt, shows status-bar alarm icon.
    val show = PendingIntent.getActivity(context, 0,
        Intent(context, AlarmActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE)
    am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, show), fire)
}
```

`AlarmReceiver.onReceive` then starts `AlarmRingerService` (§3) and posts the FSI notification (§4).
Since Android 12+, an app can start a foreground service from an exact-alarm broadcast (exact alarms are
an allowed exemption from background-FGS-start restrictions).

---

## 6. Background polling cadence — WorkManager vs persistent FGS

| Need | Mechanism | Notes |
|---|---|---|
| Energy-friendly, ≥15 min checks | **WorkManager `PeriodicWorkRequest`** | **Minimum interval is 15 min** — anything smaller is silently clamped. Survives reboot/process death, respects Doze/maintenance windows (may run *less* often than 15 min in Doze). Use with `Constraints` (network). |
| "Immediate", short, one-off | **`OneTimeWorkRequest` + `setExpedited()`** | WorkManager 2.7+. Expedited quota-limited; on pre-Android 12 runs via a FGS (provide `getForegroundInfo`). Best for short bursts, completes within minutes. |
| ~1-min live polling | **Persistent foreground service** | The only way to poll faster than 15 min reliably. Keep it running only while alerts are armed. Type `dataSync` (`FOREGROUND_SERVICE_DATA_SYNC`) or `specialUse`. |
| Exact known time | **`AlarmManager.setAlarmClock`** (§5) | No polling at all; most efficient + Doze-exempt. |

### 6.1 Sub-15-min "trick" with WorkManager (use sparingly)

A common pattern is a self-rescheduling `OneTimeWorkRequest` with an `initialDelay` (e.g. 1 min) that
re-enqueues itself at the end. It works but the OS still throttles under Doze/standby, so it is **not** a
guaranteed 1-min cadence. For true ~1-min cadence with the screen off, use the foreground service.

### 6.2 `POST_NOTIFICATIONS` runtime permission (API 33+, verified)

On Android 13+ you must request `POST_NOTIFICATIONS` at runtime before any notification is shown:

```kotlin
if (Build.VERSION.SDK_INT >= 33 &&
    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED) {
    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), RC_NOTIF)
}
```

If denied, your notifications (and the FSI) are suppressed — gate the whole alert UX on this.
A foreground service's own notification is also suppressed visually if denied, though the service still runs.

### 6.3 FGS type permissions (API 34+, verified) — recap

Declare in manifest both the `<service android:foregroundServiceType="...">` AND the matching
`FOREGROUND_SERVICE_*` permission. Pass the same type constant to `startForeground(id, notif, type)`.
Polling service → `dataSync`; ringer → `specialUse`/`mediaPlayback` (§3.4). On **Android 15+** a `dataSync`
FGS has a **cumulative ~6h/24h runtime cap** and additional `BOOT_COMPLETED` start restrictions — for a
24/7 poller prefer `specialUse` with justification, or design around the cap.

### 6.4 Battery-optimization exemption (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, verified)

Doze/App-Standby can delay polling and (on aggressive OEMs) kill background services. To make ~1-min
polling reliable you often need the app whitelisted:

```kotlin
val pm = context.getSystemService(PowerManager::class.java)
if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
    context.startActivity(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
               Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
```

**Google Play caveat (verified):** apps may request this exemption only when a **core feature** genuinely
requires it (real-time alerts plausibly qualify, but be ready to justify in the data-safety/declaration
form). The safer alternative is the generic
`Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (shows the list, no direct prompt) which is not
Play-restricted. OEM-specific "auto-start"/"protected apps" managers (Xiaomi/Huawei/Oppo/Samsung) are an
additional, non-standardized hurdle — see §7.

---

## 7. Gotchas / device caveats

- **DND bypass needs BOTH**: channel `setBypassDnd(true)` AND app-level Notification Policy Access. Missing
  either = no bypass.
- **Channel immutability**: you cannot toggle bypassDnd/sound/importance after first creation. Get policy
  access first, then create the channel. To change later, ship a new channel id.
- **`USAGE_ALARM` is the real guarantee**, not the channel sound. Always run your own alarm-stream player
  for "must be heard" alerts.
- **OEM quirks**: some ROMs route the alarm stream oddly or kill background services aggressively. Test on
  Samsung (One UI), Xiaomi (MIUI/HyperOS), Huawei, Oppo/Realme/OnePlus (ColorOS). Provide an in-app
  "battery/auto-start setup" helper screen.
- **Full-screen intent fallback**: if `USE_FULL_SCREEN_INTENT` isn't effectively granted, the alert
  silently degrades to a heads-up notification. Still post the FSI; just don't depend on it lighting the
  screen.
- **Exact alarm vs polling**: prefer `setAlarmClock` whenever a real timestamp exists; reserve FGS polling
  for "live now" detection where no timestamp is known.
- **`setFullScreenIntent` second arg**: pass `highPriority=true`; on Android 14+ the boolean-PendingIntent
  legacy overload is deprecated — use the `PendingIntent` form shown.

## 8. Forward note — Android 16 / API 36 (lower confidence)

As of early 2026, Android 16 (API 36) tightens notifications/FGS further (e.g. additional
"notification minimization"/intrusiveness controls and stricter FGS-from-background rules). Re-verify
`canUseFullScreenIntent()`, FGS-type runtime caps, and any new "live activity"-style APIs against the
official API 36 behavior-changes page before targeting it. Treat specific API-36 claims as **unverified**
here.

---

## 9. Sources (verified)

- Foreground service types — https://developer.android.com/develop/background-work/services/fgs/service-types
- FGS types required (Android 14) — https://developer.android.com/about/versions/14/changes/fgs-types-required
- Declare FGS & request permissions — https://developer.android.com/develop/background-work/services/fgs/declare
- Behavior changes Android 14 — https://developer.android.com/about/versions/14/behavior-changes-14
- Full-screen intent limits (AOSP) — https://source.android.com/docs/core/permissions/fsi-limits
- Play full-screen-intent / FGS requirements — https://support.google.com/googleplay/android-developer/answer/13392821
- Notification channels guide — https://developer.android.com/develop/ui/views/notifications/channels
- NotificationChannel.setBypassDnd / setSound reference (Microsoft mirror of Android API) —
  https://learn.microsoft.com/dotnet/api/android.app.notificationchannel.setbypassdnd
- AudioAttributes reference — https://developer.android.com/reference/android/media/AudioAttributes
- Audio attributes (AOSP) — https://source.android.com/docs/core/audio/attributes
- Schedule exact alarms denied by default (Android 14) — https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
- Schedule alarms guide — https://developer.android.com/develop/background-work/services/alarms
- AlarmManager reference — https://developer.android.com/reference/android/app/AlarmManager
- Define work requests (WorkManager 15-min floor, expedited) — https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- Doze & App Standby / battery exemption — https://developer.android.com/training/monitoring-device-state/doze-standby
- Android 15 FGS changes — https://developer.android.com/about/versions/15/changes/foreground-service-types
