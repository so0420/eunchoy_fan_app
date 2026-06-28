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
                    html = wrapHtml(article.contentHtml.orEmpty() + commentsHtml(article)),
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
      /* comments */
      .cmts { border-top: 10px solid #EAF4FD; margin: 24px -16px 0; padding: 14px 16px 4px; }
      .cmts-h { font-weight: 700; color: #2E83C9; margin-bottom: 4px; font-size: 15px; }
      .cmt { display: flex; gap: 10px; padding: 11px 0; border-bottom: 1px solid #EFF5FA;
             align-items: flex-start; }
      .cmt.reply { margin-left: 28px; }
      .av { width: 36px; height: 36px; min-width: 36px; min-height: 36px; border-radius: 50%;
            flex: 0 0 36px; align-self: flex-start; object-fit: cover; background: #E7F1F9; }
      .b { flex: 1; min-width: 0; }
      .nick { font-weight: 600; color: #18313F; font-size: 14px; }
      .badge { background: #D4ECFC; color: #2E83C9; font-size: 11px; padding: 1px 7px;
               border-radius: 8px; margin-left: 6px; vertical-align: middle; }
      .date { color: #9DB2C0; font-size: 12px; margin-left: 6px; }
      .txt { color: #22404F; font-size: 15px; margin-top: 3px; white-space: pre-wrap; word-break: break-word; }
      .txt.del { color: #9DB2C0; font-style: italic; }
      .stk { width: 110px !important; max-width: 110px !important; height: auto !important;
             margin-top: 4px; border-radius: 8px; }
      .cmt-empty { color: #9DB2C0; padding: 12px 0; }
    </style></head><body>$content</body></html>
""".trimIndent()

private const val DEFAULT_CAFE_AVATAR =
    "https://ssl.pstatic.net/static/cafe/cafe_pc/default/cafe_profile_77.png"

private fun escapeHtml(s: String): String = s
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("\r", "")

/** Builds the comment thread as styled HTML appended to the article body. */
private fun commentsHtml(article: CafeArticleContent): String {
    if (article.commentCount <= 0 && article.comments.isEmpty()) return ""
    val sb = StringBuilder()
    sb.append("<div class=\"cmts\"><div class=\"cmts-h\">댓글 ${article.commentCount}</div>")
    if (article.comments.isEmpty()) {
        sb.append("<div class=\"cmt-empty\">댓글을 불러오지 못했어요.</div>")
    }
    for (c in article.comments) {
        val cls = if (c.isReply) "cmt reply" else "cmt"
        val avatar = c.authorImage ?: DEFAULT_CAFE_AVATAR
        val nick = escapeHtml(c.authorNick ?: "익명")
        val badge = if (c.isArticleWriter) "<span class=\"badge\">작성자</span>" else ""
        val date = DateUtil.formatKst(c.createdAt)
        val body = when {
            c.isDeleted -> "<div class=\"txt del\">삭제된 댓글입니다</div>"
            c.stickerUrl != null -> {
                val txt = if (c.content.isNotBlank()) "<div class=\"txt\">${escapeHtml(c.content)}</div>" else ""
                "$txt<img class=\"stk\" src=\"${c.stickerUrl}\">"
            }
            else -> "<div class=\"txt\">${escapeHtml(c.content)}</div>"
        }
        sb.append(
            "<div class=\"$cls\"><img class=\"av\" src=\"$avatar\">" +
                "<div class=\"b\"><div><span class=\"nick\">$nick</span>$badge" +
                "<span class=\"date\">$date</span></div>$body</div></div>",
        )
    }
    sb.append("</div>")
    return sb.toString()
}
