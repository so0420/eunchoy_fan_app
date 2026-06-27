package com.so0420.eunchoy.ui

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
