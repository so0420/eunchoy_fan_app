package com.so0420.eunchoy.ui

import kotlinx.coroutines.flow.MutableStateFlow

/** Simple async UI state for one-shot loads. */
sealed interface Async<out T> {
    data object Loading : Async<Nothing>
    data class Success<T>(val data: T) : Async<T>
    data class Failure(val message: String) : Async<Nothing>
}

inline fun <T> runAsync(block: () -> T): Async<T> =
    try {
        Async.Success(block())
    } catch (e: Exception) {
        Async.Failure(e.message ?: e.javaClass.simpleName)
    }

/**
 * Updates the flow for a periodic refresh without UI churn: never flashes Loading and never
 * clobbers already-loaded data with a transient failure (keeps the last good value).
 */
fun <T> MutableStateFlow<Async<T>>.publish(result: Async<T>) {
    if (result is Async.Failure && value is Async.Success) return
    value = result
}
