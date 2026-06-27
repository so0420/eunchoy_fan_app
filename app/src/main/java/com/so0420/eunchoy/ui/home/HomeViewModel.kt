package com.so0420.eunchoy.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.so0420.eunchoy.AppContainer
import com.so0420.eunchoy.data.model.ChzzkVod
import com.so0420.eunchoy.data.model.CommunityPost
import com.so0420.eunchoy.data.model.LiveStatus
import com.so0420.eunchoy.ui.Async
import com.so0420.eunchoy.ui.runAsync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.repo

    private val _live = MutableStateFlow<Async<LiveStatus>>(Async.Loading)
    val live = _live.asStateFlow()

    private val _hls = MutableStateFlow<String?>(null)
    val hls = _hls.asStateFlow()

    private val _community = MutableStateFlow<Async<List<CommunityPost>>>(Async.Loading)
    val community = _community.asStateFlow()

    private val _vods = MutableStateFlow<Async<List<ChzzkVod>>>(Async.Loading)
    val vods = _vods.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            _live.value = Async.Loading
            val res = runAsync { repo.liveStatus() }
            _live.value = res
            _hls.value = if (res is Async.Success && res.data.isLive) {
                runCatching { repo.resolveLiveHls(preferLowLatency = true) }.getOrNull()
            } else {
                null
            }
            _refreshing.value = false
        }
        viewModelScope.launch { _community.value = runAsync { repo.communityPosts(limit = 30) } }
        viewModelScope.launch { _vods.value = runAsync { repo.chzzkVods(size = 20) } }
    }
}
