package com.so0420.eunchoy.ui.cafe

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.so0420.eunchoy.data.Config
import com.so0420.eunchoy.ui.theme.SkySurface
import com.so0420.eunchoy.ui.web.AppWebView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CafeArticleScreen(articleId: Long, onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val url = Config.cafeArticleUrl(articleId)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("카페 글") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = { uriHandler.openUri(url) }) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = "브라우저로 열기")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SkySurface),
            )
        },
    ) { inner ->
        AppWebView(
            url = url,
            modifier = Modifier.fillMaxSize().padding(inner),
        )
    }
}
