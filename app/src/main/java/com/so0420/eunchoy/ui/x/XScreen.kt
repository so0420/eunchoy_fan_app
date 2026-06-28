package com.so0420.eunchoy.ui.x

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.so0420.eunchoy.AppContainer
import com.so0420.eunchoy.data.Config
import com.so0420.eunchoy.data.model.Tweet
import com.so0420.eunchoy.ui.Async
import com.so0420.eunchoy.ui.components.EmptyBox
import com.so0420.eunchoy.ui.components.ErrorBox
import com.so0420.eunchoy.ui.components.LoadingBox
import com.so0420.eunchoy.ui.components.TweetCard
import com.so0420.eunchoy.ui.runAsync
import com.so0420.eunchoy.ui.theme.SkyPrimary
import com.so0420.eunchoy.ui.util.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class XViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.repo
    private val settings = container.settings
    private val _tweets = MutableStateFlow<Async<List<Tweet>>>(Async.Loading)
    val tweets = _tweets.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val s = settings.current()
            _tweets.value = runAsync { repo.tweets(s.xBridgeUrl) }
        }
    }
}

@Composable
fun XScreen(contentPadding: PaddingValues) {
    val vm: XViewModel = appViewModel { XViewModel(it) }
    val tweets by vm.tweets.collectAsState()
    val uriHandler = LocalUriHandler.current
    val topInset = contentPadding.calculateTopPadding()
    val bottomInset = contentPadding.calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        when (val s = tweets) {
            is Async.Loading -> Box(Modifier.padding(top = topInset)) { LoadingBox() }
            is Async.Failure -> Box(Modifier.padding(top = topInset)) {
                ErrorBox(s.message, onRetry = vm::refresh)
            }
            is Async.Success -> if (s.data.isEmpty()) {
                Box(Modifier.padding(top = topInset)) {
                    EmptyBox("최근 글을 불러오지 못했어요.\n오른쪽 위 ‘X에서 열기’로 직접 볼 수 있어요.", emoji = "🐦")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        // leave room for the floating "X에서 열기" button in the corner
                        top = topInset + 56.dp,
                        bottom = bottomInset + 20.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(s.data, key = { it.id }) { t ->
                        TweetCard(t, onClick = { uriHandler.openUri(t.permalink) })
                    }
                }
            }

            else -> Unit
        }

        // Corner action: always available, never depends on a fragile RSS bridge.
        OpenInXButton(
            onClick = { uriHandler.openUri(Config.xProfileUrl()) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = topInset + 10.dp, end = 12.dp),
        )
    }
}

@Composable
private fun OpenInXButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = SkyPrimary,
        shadowElevation = 3.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("X에서 열기", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}
