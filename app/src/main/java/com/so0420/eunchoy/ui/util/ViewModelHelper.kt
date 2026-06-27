package com.so0420.eunchoy.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.so0420.eunchoy.AppContainer
import com.so0420.eunchoy.appContainer

/** Builds a ViewModel wired to the app's [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline factory: (AppContainer) -> VM): VM {
    val container = LocalContext.current.appContainer
    return viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = factory(container) as T
        },
    )
}
