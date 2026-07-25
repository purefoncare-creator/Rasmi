package com.rasmi.purevon.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Extension functions for Color manipulation and theme-aware colors
 * Helps maintain consistent color usage across the app
 */

/**
 * Apply alpha to any color in a consistent way
 */
fun Color.withAlpha(alpha: Float): Color {
    return this.copy(alpha = alpha.coerceIn(0f, 1f))
}

/**
 * Predefined alpha levels for consistency
 */
object AlphaLevels {
    const val Disabled = 0.38f      // Material Design disabled state
    const val Medium = 0.50f         // Semi-transparent elements
    const val High = 0.70f           // Slightly transparent
    const val Subtle = 0.12f         // Very subtle backgrounds
    const val Divider = 0.12f        // Divider lines
    const val Hover = 0.08f          // Hover state backgrounds
}

/**
 * Common theme-aware color extensions
 */
@Composable
fun surfaceVariantAlpha(alpha: Float = AlphaLevels.Subtle): Color {
    return MaterialTheme.colorScheme.surfaceVariant.withAlpha(alpha)
}

@Composable
fun primaryAlpha(alpha: Float = AlphaLevels.Medium): Color {
    return MaterialTheme.colorScheme.primary.withAlpha(alpha)
}

@Composable
fun onSurfaceAlpha(alpha: Float = AlphaLevels.Medium): Color {
    return MaterialTheme.colorScheme.onSurface.withAlpha(alpha)
}

@Composable
fun onPrimaryAlpha(alpha: Float = AlphaLevels.Medium): Color {
    return MaterialTheme.colorScheme.onPrimary.withAlpha(alpha)
}

/**
 * Get disabled version of any color
 */
@Composable
fun disabledColor(): Color {
    return MaterialTheme.colorScheme.onSurface.withAlpha(AlphaLevels.Disabled)
}

/**
 * Get divider color
 */
@Composable
fun dividerColor(): Color {
    return MaterialTheme.colorScheme.onSurface.withAlpha(AlphaLevels.Divider)
}

/**
 * Get subtle background color for hover/pressed states
 */
@Composable
fun subtleBackgroundColor(): Color {
    return MaterialTheme.colorScheme.onSurface.withAlpha(AlphaLevels.Subtle)
}

/**
 * Status colors (using adaptive iOS colors for dark mode support)
 */
@Composable
fun successColor(): Color {
    return if (isSystemInDarkTheme()) iOSGreenDark else iOSGreen
}

@Composable
fun errorColor(): Color {
    return if (isSystemInDarkTheme()) iOSRedDark else iOSRed
}

@Composable
fun warningColor(): Color {
    return if (isSystemInDarkTheme()) iOSOrangeDark else iOSOrange
}

@Composable
fun infoColor(): Color {
    return if (isSystemInDarkTheme()) iOSBlueDark else iOSBlue
}

/**
 * Call status colors (using adaptive iOS colors for dark mode support)
 */
@Composable
fun incomingCallColor(): Color {
    return if (isSystemInDarkTheme()) iOSGreenDark else iOSGreen
}

@Composable
fun outgoingCallColor(): Color {
    return if (isSystemInDarkTheme()) iOSBlueDark else iOSBlue
}

@Composable
fun missedCallColor(): Color {
    return if (isSystemInDarkTheme()) iOSRedDark else iOSRed
}

@Composable
fun rejectedCallColor(): Color {
    return if (isSystemInDarkTheme()) iOSOrangeDark else iOSOrange
}

/**
 * Message status colors
 */
@Composable
fun sentMessageColor(): Color {
    return MaterialTheme.colorScheme.primaryContainer
}

@Composable
fun receivedMessageColor(): Color {
    return MaterialTheme.colorScheme.secondaryContainer
}

@Composable
fun pendingMessageColor(): Color {
    return MaterialTheme.colorScheme.surfaceVariant
}

@Composable
fun failedMessageColor(): Color {
    return MaterialTheme.colorScheme.errorContainer
}


