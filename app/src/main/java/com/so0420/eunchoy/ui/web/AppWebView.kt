package com.so0420.eunchoy.ui.web

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
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

/**
 * Renders a raw HTML string in-app (e.g. a Naver Cafe SmartEditor article body) via
 * loadDataWithBaseURL — so the post content shows directly inside the app instead of navigating
 * to a cafe page (which can deep-link out to the Naver Cafe app). Clicked links open externally.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlWebView(
    html: String,
    modifier: Modifier = Modifier,
    baseUrl: String = "https://cafe.naver.com",
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
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        // Keep the rendered article stable; send taps on links to the system browser/app.
                        val target = request.url ?: return false
                        return runCatching {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                            true
                        }.getOrDefault(false)
                    }
                }
            }
        },
        update = { view ->
            // Only (re)load when the html actually changes, to avoid reload loops on recomposition.
            if (view.tag != html) {
                view.tag = html
                view.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
            }
        },
    )
}
