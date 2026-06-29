package com.so0420.eunchoy.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.so0420.eunchoy.alarm.AlarmRingerService
import com.so0420.eunchoy.appContainer
import com.so0420.eunchoy.data.Config
import com.so0420.eunchoy.data.NotifyMode
import com.so0420.eunchoy.data.model.UpdateInfo
import com.so0420.eunchoy.data.update.UpdateChecker
import com.so0420.eunchoy.data.update.UpdateResult
import com.so0420.eunchoy.notif.NotifPermissions
import com.so0420.eunchoy.ui.cafe.CafeArticleScreen
import com.so0420.eunchoy.ui.cafe.CafeScreen
import com.so0420.eunchoy.ui.home.HomeScreen
import com.so0420.eunchoy.ui.naver.NaverLoginScreen
import com.so0420.eunchoy.ui.settings.SettingsScreen
import com.so0420.eunchoy.ui.theme.SkyBackground
import com.so0420.eunchoy.ui.theme.SkyPrimary
import com.so0420.eunchoy.ui.theme.SkyPrimaryContainer
import com.so0420.eunchoy.ui.theme.SkySurface
import com.so0420.eunchoy.ui.update.UpdateDialog
import com.so0420.eunchoy.ui.x.XScreen
import com.so0420.eunchoy.ui.youtube.YoutubeScreen
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "홈", Icons.Filled.Home),
    Tab("cafe", "카페", Icons.Filled.LocalCafe),
    Tab("youtube", "유튜브", Icons.Filled.SmartDisplay),
    Tab("x", "X", Icons.Filled.AlternateEmail),
    Tab("settings", "설정", Icons.Filled.Settings),
)

private fun titleFor(route: String?): String = when (route) {
    "cafe" -> "네이버 카페"
    "youtube" -> "유튜브"
    "x" -> "X · @Eun_choy"
    "settings" -> "설정"
    else -> "은초이 모아보기"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(startRoute: String = "home") {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBars = TABS.any { it.route == route } || route == null
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    LaunchedEffect(Unit) {
        (UpdateChecker.check() as? UpdateResult.Available)?.let { update = it.info }
    }

    // Universal alarm dismiss: works even if the notification/full-screen UI was suppressed.
    val alarmRinging by AlarmRingerService.ringing.collectAsState()
    val uriHandler = LocalUriHandler.current

    // If alarm-style alerts are on but the permissions that make them appear on ANY screen are
    // missing, prompt the user — otherwise the alarm rings without a system notification/full-screen.
    var showAlarmPermPrompt by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val s = context.appContainer.settings.current()
        val alarmEnabled = s.sources.values.any { it.mode == NotifyMode.ALARM }
        val missing = !NotifPermissions.notificationsEnabled(context) ||
            !NotifPermissions.canUseFullScreenIntent(context)
        if (alarmEnabled && missing) showAlarmPermPrompt = true
    }

    // Single navigation path for switching tabs — used by the bottom bar AND any in-app deep link
    // (e.g. the alarm-permission dialog), so the saved-state back stack never gets corrupted.
    fun navigateTab(route: String) {
        nav.navigate(route) {
            popUpTo(nav.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = SkyBackground,
        topBar = {
            if (showBars) {
                TopAppBar(
                    title = { Text(titleFor(route), fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SkySurface,
                        titleContentColor = SkyPrimary,
                    ),
                )
            }
        },
        bottomBar = {
            if (showBars) {
                NavigationBar(containerColor = SkySurface, tonalElevation = 0.dp) {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { navigateTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = SkyPrimary,
                                selectedTextColor = SkyPrimary,
                                indicatorColor = SkyPrimaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = startRoute,
            // Instant tab switches — no slide/fade so no elevation-edge shadow during transitions.
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable("home") { HomeScreen(inner) }
            composable("cafe") { CafeScreen(inner, onOpenArticle = { nav.navigate("cafe_article/$it") }) }
            composable("youtube") { YoutubeScreen(inner) }
            composable("x") { XScreen(inner) }
            composable("settings") { SettingsScreen(inner, onNaverLogin = { nav.navigate("naver_login") }) }

            composable(
                "cafe_article/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                CafeArticleScreen(
                    articleId = entry.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onLogin = { nav.navigate("naver_login") },
                )
            }
            composable("naver_login") {
                NaverLoginScreen(
                    onLoggedIn = { aut, ses ->
                        scope.launch { context.appContainer.settings.setNaverCookies(aut, ses) }
                        nav.popBackStack()
                    },
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }

    update?.let { info -> UpdateDialog(info) { update = null } }

    if (alarmRinging) {
        AlertDialog(
            onDismissRequest = { },
            icon = { Text("🔔") },
            title = { Text("알람이 울리고 있어요") },
            text = { Text("은초이 방송 시작 알람입니다.") },
            confirmButton = {
                TextButton(onClick = { AlarmRingerService.stop(context) }) { Text("끄기") }
            },
            dismissButton = {
                TextButton(onClick = {
                    AlarmRingerService.stop(context)
                    uriHandler.openUri(Config.chzzkLiveUrl())
                }) { Text("방송 보기") }
            },
        )
    }

    if (showAlarmPermPrompt) {
        AlertDialog(
            onDismissRequest = { showAlarmPermPrompt = false },
            icon = { Text("🔔") },
            title = { Text("알람 권한이 필요해요") },
            text = {
                Text(
                    "알람이 잠금화면·다른 앱 위에서도 뜨고 알림에서 바로 끌 수 있으려면 " +
                        "‘알림 표시’와 ‘전체화면 알림’ 권한을 켜주세요.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showAlarmPermPrompt = false
                    navigateTab("settings")
                }) { Text("설정 열기") }
            },
            dismissButton = {
                TextButton(onClick = { showAlarmPermPrompt = false }) { Text("나중에") }
            },
        )
    }
}
