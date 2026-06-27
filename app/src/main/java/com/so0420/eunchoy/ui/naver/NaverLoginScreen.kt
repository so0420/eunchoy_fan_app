package com.so0420.eunchoy.ui.naver

import android.webkit.CookieManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.so0420.eunchoy.ui.theme.SkySurface
import com.so0420.eunchoy.ui.web.AppWebView

/**
 * In-app Naver login. After the user signs in, NID_AUT/NID_SES land in the WebView cookie jar;
 * we harvest them on each page load and report back once both are present.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NaverLoginScreen(onLoggedIn: (aut: String, ses: String) -> Unit, onBack: () -> Unit) {
    // onPageFinished fires on every redirect; only report the captured cookies once.
    var reported by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("네이버 로그인") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SkySurface),
            )
        },
    ) { inner ->
        AppWebView(
            url = "https://nid.naver.com/nidlogin.login?mode=form&url=https%3A%2F%2Fm.cafe.naver.com",
            modifier = Modifier.fillMaxSize().padding(inner),
            onPageFinished = { view, _ ->
                CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
                val cookie = CookieManager.getInstance().getCookie("https://naver.com")
                val aut = cookieValue(cookie, "NID_AUT")
                val ses = cookieValue(cookie, "NID_SES")
                if (!reported && !aut.isNullOrBlank() && !ses.isNullOrBlank()) {
                    reported = true
                    onLoggedIn(aut, ses)
                }
            },
        )
    }
}

private fun cookieValue(cookies: String?, key: String): String? =
    cookies?.split(";")?.map { it.trim() }
        ?.firstOrNull { it.startsWith("$key=") }
        ?.substringAfter("=")
