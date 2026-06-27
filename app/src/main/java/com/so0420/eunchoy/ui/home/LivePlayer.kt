package com.so0420.eunchoy.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import com.so0420.eunchoy.data.Config

/** Embedded Chzzk live HLS player. [hlsUrl] is a master .m3u8 resolved fresh by the repository. */
@OptIn(UnstableApi::class)
@Composable
fun LivePlayer(
    hlsUrl: String,
    muted: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val exo = remember(hlsUrl) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(Config.UA)
            .setDefaultRequestProperties(mapOf("Referer" to "https://chzzk.naver.com/"))
            .setAllowCrossProtocolRedirects(true)
        val source = HlsMediaSource.Factory(httpFactory)
            .setAllowChunklessPreparation(true)
            .createMediaSource(
                MediaItem.Builder()
                    .setUri(hlsUrl)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .build(),
            )
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(source)
            prepare()
            playWhenReady = true
            volume = 0f
        }
    }

    LaunchedEffect(muted, exo) { exo.volume = if (muted) 0f else 1f }

    // Pause buffering/decoding while the app is backgrounded; resume when it returns.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exo) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> exo.playWhenReady = false
                Lifecycle.Event.ON_START -> exo.playWhenReady = true
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(exo) { onDispose { exo.release() } }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exo
                useController = true
                setShowNextButton(false)
                setShowPreviousButton(false)
            }
        },
    )
}
