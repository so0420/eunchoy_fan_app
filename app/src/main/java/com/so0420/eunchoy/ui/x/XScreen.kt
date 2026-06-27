package com.so0420.eunchoy.ui.x

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
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
import com.so0420.eunchoy.ui.util.appViewModel
import com.so0420.eunchoy.ui.web.AppWebView
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
    var tab by remember { mutableIntStateOf(0) }
    val uriHandler = LocalUriHandler.current

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
            SegmentedButton(
                selected = tab == 0,
                onClick = { tab = 0 },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("타임라인") }
            SegmentedButton(
                selected = tab == 1,
                onClick = { tab = 1 },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("최근 글 알림") }
        }

        if (tab == 0) {
            AppWebView(
                url = Config.xProfileUrl(),
                desktopUa = false,
                modifier = Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding()),
            )
        } else {
            when (val s = tweets) {
                is Async.Loading -> LoadingBox()
                is Async.Failure -> XBridgeUnavailable { uriHandler.openUri(Config.xProfileUrl()) }
                is Async.Success -> if (s.data.isEmpty()) {
                    XBridgeUnavailable { uriHandler.openUri(Config.xProfileUrl()) }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 4.dp,
                            bottom = contentPadding.calculateBottomPadding() + 20.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(s.data, key = { it.id }) { t ->
                            TweetCard(t, onClick = { uriHandler.openUri(t.permalink) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun XBridgeUnavailable(onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        EmptyBox("새 글 알림 브리지를 사용할 수 없어요.\n타임라인 탭에서 직접 볼 수 있어요.", emoji = "🐦")
        TextButton(onClick = onOpen) { Text("X에서 열기", color = MaterialTheme.colorScheme.primary) }
    }
}
