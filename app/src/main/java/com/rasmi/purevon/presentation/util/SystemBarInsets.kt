package com.rasmi.purevon.presentation.util

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Bottom navigation-bar inset that should be reserved only when a legacy
 * 3-button navigation bar is present. A 3-button bar is typically 44-48dp
 * tall, while gesture navigation reports a small inset (~16-24dp) or none.
 *
 * Returns 0.dp on gesture navigation so content can sit flush with the
 * screen bottom (edge-to-edge).
 */
@Composable
fun threeButtonNavBarPadding(): Dp {
    val density = LocalDensity.current
    val navBarPx = WindowInsets.navigationBars.getBottom(density)
    return with(density) {
        if (navBarPx.toDp() > 32.dp) navBarPx.toDp() else 0.dp
    }
}
