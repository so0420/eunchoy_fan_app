package com.so0420.eunchoy.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val EunchoyLightColors = lightColorScheme(
    primary = SkyPrimary,
    onPrimary = SkyOnPrimary,
    primaryContainer = SkyPrimaryContainer,
    onPrimaryContainer = SkyOnPrimaryContainer,
    secondary = SkySecondary,
    onSecondary = SkyOnSecondary,
    secondaryContainer = SkySecondaryContainer,
    onSecondaryContainer = SkyOnSecondaryContainer,
    tertiary = SkyTertiary,
    onTertiary = SkyOnTertiary,
    tertiaryContainer = SkyTertiaryContainer,
    onTertiaryContainer = SkyOnTertiaryContainer,
    background = SkyBackground,
    onBackground = SkyOnBackground,
    surface = SkySurface,
    onSurface = SkyOnSurface,
    surfaceVariant = SkySurfaceVariant,
    onSurfaceVariant = SkyOnSurfaceVariant,
    outline = SkyOutline,
    outlineVariant = SkyOutlineVariant,
    error = LiveRed,
    errorContainer = LiveRedContainer,
)

/**
 * The app is intentionally a bright, light-only experience (per design brief).
 * [darkTheme] is accepted for API completeness but we keep the icy light palette.
 */
@Composable
fun EunchoyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = EunchoyLightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = EunchoyTypography,
        content = content,
    )
}
