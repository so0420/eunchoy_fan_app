package com.so0420.eunchoy.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.so0420.eunchoy.AppContainer
import com.so0420.eunchoy.data.model.ChzzkVod
import com.so0420.eunchoy.data.model.CommunityPost
import com.so0420.eunchoy.data.model.LiveStatus
import com.so0420.eunchoy.ui.Async
import com.so0420.eunchoy.ui.publish
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

    /** Re-fetch everything. Safe to call periodically — no Loading flash, no player churn. */
    fun refresh() {
        viewModelScope.launch {
            val res = runAsync { repo.liveStatus() }
            _live.publish(res)
            if (res is Async.Success) {
                when {
                    !res.data.isLive -> _hls.value = null
                    // Resolve the HLS url only once per live session so the player isn't recreated.
                    _hls.value == null -> _hls.value = runCatching { repo.resolveLiveHls(preferLowLatency = true) }.getOrNull()
                }
            }
        }
        viewModelScope.launch { _community.publish(runAsync { repo.communityPosts(limit = 30) }) }
        viewModelScope.launch { _vods.publish(runAsync { repo.chzzkVods(size = 20) }) }
    }
}
