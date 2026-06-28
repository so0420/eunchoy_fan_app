package com.so0420.eunchoy.ui.youtube

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.so0420.eunchoy.AppContainer
import com.so0420.eunchoy.data.model.YoutubeVideo
import com.so0420.eunchoy.ui.Async
import com.so0420.eunchoy.ui.components.EmptyBox
import com.so0420.eunchoy.ui.components.ErrorBox
import com.so0420.eunchoy.ui.components.LoadingBox
import com.so0420.eunchoy.ui.components.YoutubeCard
import com.so0420.eunchoy.ui.runAsync
import com.so0420.eunchoy.ui.theme.SkyPrimary
import com.so0420.eunchoy.ui.theme.SkySurface
import com.so0420.eunchoy.ui.util.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class YoutubeViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.repo
    private val _main = MutableStateFlow<Async<List<YoutubeVideo>>>(Async.Loading)
    val main = _main.asStateFlow()
    private val _vod = MutableStateFlow<Async<List<YoutubeVideo>>>(Async.Loading)
    val vod = _vod.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch { _main.value = runAsync { repo.youtubeMain() } }
        viewModelScope.launch { _vod.value = runAsync { repo.youtubeVod() } }
    }
}

@Composable
fun YoutubeScreen(contentPadding: PaddingValues) {
    val vm: YoutubeViewModel = appViewModel { YoutubeViewModel(it) }
    val main by vm.main.collectAsState()
    val vod by vm.vod.collectAsState()
    val uriHandler = LocalUriHandler.current
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = SkySurface,
            contentColor = SkyPrimary,
        ) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                text = { Text("메인", fontWeight = FontWeight.SemiBold) },
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                text = { Text("다시보기", fontWeight = FontWeight.SemiBold) },
            )
        }
        // Swipe left/right to switch between 메인 and 다시보기.
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val state = if (page == 0) main else vod
            VideoList(state, contentPadding) { uriHandler.openUri(it.url) }
        }
    }
}

@Composable
private fun VideoList(state: Async<List<YoutubeVideo>>, contentPadding: PaddingValues, onClick: (YoutubeVideo) -> Unit) {
    when (state) {
        is Async.Loading -> LoadingBox()
        is Async.Failure -> ErrorBox(state.message)
        is Async.Success -> if (state.data.isEmpty()) {
            EmptyBox("아직 영상이 없어요", emoji = "📺")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 12.dp,
                    bottom = contentPadding.calculateBottomPadding() + 20.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(state.data, key = { it.id }) { v -> YoutubeCard(v, onClick = { onClick(v) }) }
            }
        }
    }
}
