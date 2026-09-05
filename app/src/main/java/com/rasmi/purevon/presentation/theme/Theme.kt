package com.rasmi.purevon.presentation.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val PurevonColorScheme = darkColorScheme(
    primary = PurevonPrimary,
    onPrimary = PurevonOnPrimary,
    primaryContainer = PurevonPrimaryContainer,
    onPrimaryContainer = PurevonOnPrimaryContainer,

    secondary = PurevonSecondary,
    onSecondary = PurevonOnSecondary,
    secondaryContainer = PurevonSecondaryContainer,
    onSecondaryContainer = PurevonOnSecondaryContainer,

    tertiary = PurevonTertiary,
    onTertiary = PurevonOnTertiary,
    tertiaryContainer = PurevonTertiaryContainer,
    onTertiaryContainer = PurevonOnTertiaryContainer,

    error = PurevonError,
    onError = PurevonOnPrimary,
    errorContainer = PurevonErrorContainer,
    onErrorContainer = PurevonOnPrimaryContainer,

    background = PurevonBackground,
    onBackground = PurevonTextPrimary,

    surface = PurevonSurface,
    onSurface = PurevonTextPrimary,
    surfaceVariant = PurevonSurfaceMuted,
    onSurfaceVariant = PurevonTextSecondary,

    outline = PurevonBorder,
    outlineVariant = PurevonBorderMedium,

    scrim = PurevonScrim,
    inverseSurface = PurevonTextPrimary,
    inverseOnSurface = PurevonSurface,
    inversePrimary = PurevonPrimaryLight
)

@Composable
fun PurevonTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = PurevonColorScheme,
        typography = Typography,
        content = content
    )
}
