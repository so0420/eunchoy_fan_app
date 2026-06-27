package com.so0420.eunchoy

import android.content.Context
import com.so0420.eunchoy.data.EunchoyRepository
import com.so0420.eunchoy.data.settings.SettingsRepository
import com.so0420.eunchoy.notif.Notifier

/** Minimal manual DI graph, created once and held by [EunchoyApp]. */
class AppContainer(context: Context) {
    val settings = SettingsRepository(context)
    val repo = EunchoyRepository()
    val notifier = Notifier(context.applicationContext)
}

/** Convenience accessor from any Context. */
val Context.appContainer: AppContainer
    get() = (applicationContext as EunchoyApp).container
