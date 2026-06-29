package com.so0420.eunchoy.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/**
 * Keeps a screen live: calls [onRefresh] immediately whenever it becomes RESUMED (entered, or the
 * app returns to the foreground) and then every [intervalMs] while it stays resumed. The loop is
 * cancelled when the screen is paused/left, so only the visible screen polls.
 */
@Composable
fun AutoRefresh(intervalMs: Long, onRefresh: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, intervalMs) {
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                onRefresh()
                delay(intervalMs)
            }
        }
    }
}
