package com.rasmi.purevon.presentation.screen.conversation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun getNavigationBarPadding(): Dp {
    val density = LocalDensity.current
    val navBarHeightPx = WindowInsets.navigationBars.getBottom(density)
    val navBarHeightDp = with(density) { navBarHeightPx.toDp() }
    
    // If height is greater than 40dp, it's likely a 3-button navigation bar.
    // Otherwise, it's a gesture pill (or hidden), so we return 0dp to let the UI touch the bottom.
    return if (navBarHeightDp > 40.dp) navBarHeightDp else 0.dp
}
