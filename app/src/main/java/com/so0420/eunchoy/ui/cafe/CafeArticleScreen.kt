package com.so0420.eunchoy.ui.cafe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.so0420.eunchoy.AppContainer
import com.so0420.eunchoy.data.Config
import com.so0420.eunchoy.data.model.CafeArticleContent
import com.so0420.eunchoy.ui.Async
import com.so0420.eunchoy.ui.components.Avatar
import com.so0420.eunchoy.ui.components.EmptyBox
import com.so0420.eunchoy.ui.components.ErrorBox
import com.so0420.eunchoy.ui.components.LoadingBox
import com.so0420.eunchoy.ui.runAsync
import com.so0420.eunchoy.ui.theme.SkyPrimary
import com.so0420.eunchoy.ui.theme.SkySurface
import com.so0420.eunchoy.ui.util.appViewModel
import com.so0420.eunchoy.ui.web.HtmlWebView
import com.so0420.eunchoy.util.DateUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CafeArticleViewModel(container: AppContainer, private val articleId: Long) : ViewModel() {
    private val repo = container.repo
    private val _article = MutableStateFlow<Async<CafeArticleContent>>(Async.Loading)
    val article = _article.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _article.value = Async.Loading
            _article.value = runAsync { repo.cafeArticle(articleId) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CafeArticleScreen(articleId: Long, onBack: () -> Unit, onLogin: () -> Unit) {
    val vm: CafeArticleViewModel = appViewModel { CafeArticleViewModel(it, articleId) }
    val state by vm.article.collectAsState()
    val uriHandler = LocalUriHandler.current
    val webUrl = Config.cafeArticleUrl(articleId)

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
                    IconButton(onClick = { uriHandler.openUri(webUrl) }) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = "웹에서 열기")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SkySurface),
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            when (val s = state) {
                is Async.Loading -> LoadingBox()
                is Async.Failure -> ErrorBox(s.message, onRetry = vm::load)
                is Async.Success -> ArticleBody(
                    article = s.data,
                    onLogin = onLogin,
                    onRetry = vm::load,
                    onOpenWeb = { uriHandler.openUri(webUrl) },
                )
            }
        }
    }
}

@Composable
private fun ArticleBody(
    article: CafeArticleContent,
    onLogin: () -> Unit,
    onRetry: () -> Unit,
    onOpenWeb: () -> Unit,
) {
    when {
        article.notFound -> EmptyBox("삭제되었거나 존재하지 않는 글이에요", emoji = "🗑️")
        article.needsLogin -> LoginNeeded(onLogin, onRetry, onOpenWeb)
        else -> {
            Column(Modifier.fillMaxSize()) {
                ArticleHeader(article)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                HtmlWebView(
                    html = wrapHtml(article.contentHtml.orEmpty()),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ArticleHeader(article: CafeArticleContent) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        if (article.subject.isNotBlank()) {
            Text(
                article.subject,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(article.authorImage, 32)
            Spacer(Modifier.width(8.dp))
            Text(
                article.authorNick ?: "은초이",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                DateUtil.formatKst(article.createdAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoginNeeded(onLogin: () -> Unit, onRetry: () -> Unit, onOpenWeb: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyBox("회원공개 글이에요.\n네이버 로그인 후 본문을 볼 수 있어요.", emoji = "🔒")
        Spacer(Modifier.height(8.dp))
        Button(onClick = onLogin) { Text("네이버 로그인") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onRetry) { Text("로그인했어요 · 다시 시도") }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onOpenWeb) { Text("웹에서 열기", color = SkyPrimary) }
    }
}

/** Wrap the SmartEditor HTML with a viewport + light-theme styling so it renders cleanly in-app. */
private fun wrapHtml(content: String): String = """
    <!DOCTYPE html><html><head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=2">
    <style>
      body { margin: 16px; color: #18313F; font-size: 16px; line-height: 1.7;
             word-break: break-word; -webkit-text-size-adjust: 100%;
             font-family: -apple-system, 'Noto Sans KR', sans-serif; background: #FFFFFF; }
      img, video { max-width: 100% !important; height: auto !important; border-radius: 10px; }
      a { color: #2E83C9; }
      iframe { max-width: 100%; }
      .se-component, .se-image, p { margin: 8px 0; }
      table { max-width: 100%; }
    </style></head><body>$content</body></html>
""".trimIndent()
