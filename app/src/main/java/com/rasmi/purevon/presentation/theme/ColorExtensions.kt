package com.rasmi.purevon.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

fun Color.withAlpha(alpha: Float): Color {
    return this.copy(alpha = alpha.coerceIn(0f, 1f))
}

object AlphaLevels {
    const val Disabled = 0.38f
    const val Medium = 0.50f
    const val High = 0.70f
    const val Subtle = 0.12f
    const val Divider = 0.12f
    const val Hover = 0.08f
}

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

@Composable
fun disabledColor(): Color {
    return MaterialTheme.colorScheme.onSurface.withAlpha(AlphaLevels.Disabled)
}

@Composable
fun dividerColor(): Color {
    return MaterialTheme.colorScheme.onSurface.withAlpha(AlphaLevels.Divider)
}

@Composable
fun subtleBackgroundColor(): Color {
    return MaterialTheme.colorScheme.onSurface.withAlpha(AlphaLevels.Subtle)
}

// Status colors — fixed, no dark/light switching
@Composable
fun successColor(): Color = PurevonSuccess

@Composable
fun errorColor(): Color = PurevonError

@Composable
fun warningColor(): Color = PurevonWarning

@Composable
fun infoColor(): Color = PurevonInfo

// Call status colors — fixed
@Composable
fun incomingCallColor(): Color = PurevonCallIncoming

@Composable
fun outgoingCallColor(): Color = PurevonCallOutgoing

@Composable
fun missedCallColor(): Color = PurevonCallMissed

@Composable
fun rejectedCallColor(): Color = PurevonCallRejected

// Message status colors
@Composable
fun sentMessageColor(): Color = PurevonBubbleSent

@Composable
fun receivedMessageColor(): Color = PurevonBubbleReceived

@Composable
fun pendingMessageColor(): Color = PurevonSurfaceMuted

@Composable
fun failedMessageColor(): Color = PurevonErrorContainer
