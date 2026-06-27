package com.so0420.eunchoy.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.so0420.eunchoy.appContainer
import com.so0420.eunchoy.ui.cafe.CafeArticleScreen
import com.so0420.eunchoy.ui.cafe.CafeScreen
import com.so0420.eunchoy.ui.home.HomeScreen
import com.so0420.eunchoy.ui.naver.NaverLoginScreen
import com.so0420.eunchoy.ui.settings.SettingsScreen
import com.so0420.eunchoy.ui.theme.SkyBackground
import com.so0420.eunchoy.ui.theme.SkyPrimary
import com.so0420.eunchoy.ui.theme.SkyPrimaryContainer
import com.so0420.eunchoy.ui.theme.SkySurface
import com.so0420.eunchoy.ui.x.XScreen
import com.so0420.eunchoy.ui.youtube.YoutubeScreen
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "홈", Icons.Filled.Home),
    Tab("cafe", "카페", Icons.Filled.LocalCafe),
    Tab("youtube", "영상", Icons.Filled.SmartDisplay),
    Tab("x", "X", Icons.Filled.AlternateEmail),
    Tab("settings", "설정", Icons.Filled.Settings),
)

private fun titleFor(route: String?): String = when (route) {
    "cafe" -> "네이버 카페"
    "youtube" -> "유튜브"
    "x" -> "X · @Eun_choy"
    "settings" -> "설정"
    else -> "은초이 팬앱"
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
                NavigationBar(containerColor = SkySurface, tonalElevation = 3.dp) {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
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
        NavHost(navController = nav, startDestination = startRoute, modifier = Modifier) {
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
}
