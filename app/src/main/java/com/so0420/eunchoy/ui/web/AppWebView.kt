package com.so0420.eunchoy.ui.web

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.so0420.eunchoy.data.Config

/** Reusable JS-enabled WebView. [onPageFinished] fires after each page load (used for cookie capture). */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AppWebView(
    url: String,
    modifier: Modifier = Modifier,
    desktopUa: Boolean = false,
    onPageFinished: (WebView, String?) -> Unit = { _, _ -> },
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadsImagesAutomatically = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    if (desktopUa) userAgentString = Config.UA
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, finishedUrl: String?) {
                        onPageFinished(view, finishedUrl)
                    }
                }
                loadUrl(url)
            }
        },
    )
}
