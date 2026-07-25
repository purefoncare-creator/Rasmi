package com.rasmi.purevon.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = iOSBlueDark,
    onPrimary = DarkBackground,
    primaryContainer = ColorTokens.DarkPrimaryContainer,
    onPrimaryContainer = DarkOnBackground,
    
    secondary = DarkSecondary,
    onSecondary = DarkOnBackground,
    secondaryContainer = ColorTokens.DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSurface,
    
    tertiary = iOSTealDark,
    onTertiary = DarkBackground,
    tertiaryContainer = ColorTokens.DarkTertiaryContainer,
    onTertiaryContainer = DarkOnSurface,
    
    error = iOSRedDark,
    onError = DarkBackground,
    errorContainer = ColorTokens.DarkErrorContainer,
    onErrorContainer = iOSRedDark,
    
    background = DarkBackground,
    onBackground = DarkOnBackground,
    
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkSecondary,
    
    outline = DarkDivider,
    outlineVariant = DarkTertiary,
    
    scrim = DarkBackground.copy(alpha = 0.8f)
)

private val LightColorScheme = lightColorScheme(
    primary = iOSBlue,
    onPrimary = ColorTokens.OnBrand,
    primaryContainer = ColorTokens.LightPrimaryContainer,
    onPrimaryContainer = iOSBluePressed,
    
    secondary = LightSecondary,
    onSecondary = ColorTokens.OnBrand,
    secondaryContainer = ColorTokens.LightSecondaryContainer,
    onSecondaryContainer = LightOnSurface,
    
    tertiary = iOSTeal,
    onTertiary = ColorTokens.OnBrand,
    tertiaryContainer = ColorTokens.LightTertiaryContainer,
    onTertiaryContainer = LightOnSurface,
    
    error = Error,
    onError = ColorTokens.OnBrand,
    errorContainer = ColorTokens.LightErrorContainer,
    onErrorContainer = Error,
    
    background = LightBackground,
    onBackground = LightOnBackground,
    
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightSecondary,
    
    outline = LightDivider,
    outlineVariant = LightTertiary,
    
    scrim = LightOnBackground.copy(alpha = 0.5f)
)

@Composable
fun PurevonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Safe cast - only apply window styling if context is an Activity
            // This prevents crashes when using theme in Services
            (view.context as? Activity)?.window?.let { window ->
                @Suppress("DEPRECATION")
                window.statusBarColor = colorScheme.background.toArgb()
                @Suppress("DEPRECATION")
                // Make navigation bar transparent for blur effect
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
