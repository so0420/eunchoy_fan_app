package com.so0420.eunchoy.ui.cafe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.so0420.eunchoy.AppContainer
import com.so0420.eunchoy.data.model.CafePost
import com.so0420.eunchoy.ui.Async
import com.so0420.eunchoy.ui.components.CafeCard
import com.so0420.eunchoy.ui.components.EmptyBox
import com.so0420.eunchoy.ui.components.ErrorBox
import com.so0420.eunchoy.ui.components.LoadingBox
import com.so0420.eunchoy.ui.publish
import com.so0420.eunchoy.ui.runAsync
import com.so0420.eunchoy.ui.util.AutoRefresh
import com.so0420.eunchoy.ui.util.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CafeViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.repo
    private val _posts = MutableStateFlow<Async<List<CafePost>>>(Async.Loading)
    val posts = _posts.asStateFlow()

    fun refresh() {
        viewModelScope.launch { _posts.publish(runAsync { repo.cafePosts(maxPages = 3) }) }
    }
}

@Composable
fun CafeScreen(contentPadding: PaddingValues, onOpenArticle: (Long) -> Unit) {
    val vm: CafeViewModel = appViewModel { CafeViewModel(it) }
    val posts by vm.posts.collectAsState()

    AutoRefresh(intervalMs = 60_000L) { vm.refresh() }

    when (val s = posts) {
        is Async.Loading -> LoadingBox()
        is Async.Failure -> ErrorBox(s.message, onRetry = vm::refresh)
        is Async.Success -> if (s.data.isEmpty()) {
            EmptyBox("아직 카페 글이 없어요", emoji = "☕")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = contentPadding.calculateTopPadding() + 12.dp,
                    bottom = contentPadding.calculateBottomPadding() + 20.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(s.data, key = { it.id }) { post ->
                    CafeCard(post, onClick = { onOpenArticle(post.id) })
                }
            }
        }
    }
}
