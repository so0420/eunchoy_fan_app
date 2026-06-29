package com.so0420.eunchoy.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import coil.compose.AsyncImage
import com.so0420.eunchoy.data.Config
import com.so0420.eunchoy.data.model.LiveStatus
import com.so0420.eunchoy.ui.Async
import com.so0420.eunchoy.ui.components.Avatar
import com.so0420.eunchoy.ui.components.CommunityCard
import com.so0420.eunchoy.ui.components.EmptyBox
import com.so0420.eunchoy.ui.components.ErrorBox
import com.so0420.eunchoy.ui.components.LoadingBox
import com.so0420.eunchoy.ui.components.Pill
import com.so0420.eunchoy.ui.components.SectionHeader
import com.so0420.eunchoy.ui.components.VodCard
import com.so0420.eunchoy.ui.components.formatCount
import com.so0420.eunchoy.ui.theme.LiveRed
import com.so0420.eunchoy.ui.theme.OfflineGray
import com.so0420.eunchoy.ui.theme.SkyPrimary
import com.so0420.eunchoy.ui.theme.SkySurface
import com.so0420.eunchoy.ui.theme.SkySurfaceVariant
import com.so0420.eunchoy.ui.util.AutoRefresh
import com.so0420.eunchoy.ui.util.appViewModel
import com.so0420.eunchoy.util.DateUtil

@Composable
fun HomeScreen(contentPadding: PaddingValues) {
    val vm: HomeViewModel = appViewModel { HomeViewModel(it) }
    val live by vm.live.collectAsState()
    val hls by vm.hls.collectAsState()
    val community by vm.community.collectAsState()
    val vods by vm.vods.collectAsState()
    val uriHandler = LocalUriHandler.current

    // Live-status etc. refresh on entry, on app-return, and every 30s while visible.
    AutoRefresh(intervalMs = 30_000L) { vm.refresh() }

    LazyColumn(
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                when (val s = live) {
                    is Async.Loading -> LiveHeroPlaceholder()
                    is Async.Failure -> ErrorBox(s.message, onRetry = vm::refresh)
                    is Async.Success -> LiveHeroCard(s.data, hls) { uriHandler.openUri(Config.chzzkLiveUrl()) }
                }
            }
        }

        item {
            SectionHeader("커뮤니티") {
                Text(
                    "더보기",
                    style = MaterialTheme.typography.labelLarge,
                    color = SkyPrimary,
                    modifier = Modifier.clickable { uriHandler.openUri(Config.chzzkCommunityUrl()) },
                )
            }
        }
        when (val c = community) {
            is Async.Loading -> item { LoadingBox() }
            is Async.Failure -> item { ErrorBox(c.message, onRetry = vm::refresh) }
            is Async.Success -> if (c.data.isEmpty()) {
                item { EmptyBox("아직 커뮤니티 글이 없어요") }
            } else {
                items(c.data, key = { it.id }) { post ->
                    CommunityCard(
                        post = post,
                        onClick = { uriHandler.openUri(Config.chzzkCommunityUrl()) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                    )
                }
            }
        }

        item { SectionHeader("다시보기") }
        when (val v = vods) {
            is Async.Loading -> item { LoadingBox() }
            is Async.Failure -> item { ErrorBox(v.message) }
            is Async.Success -> if (v.data.isEmpty()) {
                item { EmptyBox("아직 다시보기가 없어요", emoji = "🎬") }
            } else {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(v.data, key = { it.videoNo }) { vod ->
                            VodCard(vod, onClick = { uriHandler.openUri(Config.chzzkVideoUrl(vod.videoNo)) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveHeroCard(live: LiveStatus, hls: String?, onOpen: () -> Unit) {
    var muted by remember { mutableStateOf(true) }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SkySurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(SkySurfaceVariant),
        ) {
            if (live.isLive && hls != null) {
                LivePlayer(hlsUrl = hls, muted = muted, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                IconButton(
                    onClick = { muted = !muted },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .size(36.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.45f)),
                ) {
                    Icon(
                        if (muted) Icons.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "음소거",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else {
                AsyncImage(
                    model = live.thumbnailUrl ?: live.channelImageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                )
            }
            StatusBadge(live.isLive, live.viewerCount, Modifier.align(Alignment.TopStart).padding(10.dp))
        }

        Column(Modifier.padding(16.dp)) {
            Text(
                live.title ?: if (live.isLive) "방송 중" else "오프라인",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!live.category.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Pill(live.category)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(live.channelImageUrl, 36)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        live.channelName ?: "은초이 Choy",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val sub = buildString {
                        live.followerCount?.let { append("팔로워 ${formatCount(it)}") }
                        if (live.isLive && live.openDate != null) {
                            if (isNotEmpty()) append(" · ")
                            append("${DateUtil.relative(live.openDate)} 시작")
                        }
                    }
                    if (sub.isNotBlank()) {
                        Text(
                            sub,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                FilledTonalButton(onClick = onOpen) { Text(if (live.isLive) "방송 보기" else "채널") }
            }
        }
    }
}

@Composable
private fun StatusBadge(isLive: Boolean, viewers: Int?, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (isLive) LiveRed else OfflineGray.copy(alpha = 0.9f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(Color.White))
        Spacer(Modifier.width(6.dp))
        Text(
            if (isLive) "LIVE${viewers?.let { " · ${formatCount(it)}명" } ?: ""}" else "오프라인",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LiveHeroPlaceholder() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SkySurface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(SkySurfaceVariant),
            contentAlignment = Alignment.Center,
        ) { LoadingBox() }
        Box(Modifier.height(72.dp).fillMaxWidth())
    }
}
