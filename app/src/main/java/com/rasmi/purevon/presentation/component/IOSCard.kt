package com.rasmi.purevon.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.rasmi.purevon.presentation.theme.CornerRadius
import com.rasmi.purevon.presentation.theme.Elevation
import com.rasmi.purevon.presentation.theme.Spacing

/**
 * iOS-style Card with subtle shadow
 */
@Composable
fun IOSCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    cornerRadius: Dp = CornerRadius.Large,
    elevation: Dp = Elevation.Small,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            ),
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = Elevation.None
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.default),
            content = content
        )
    }
}

/**
 * iOS-style Grouped Card (for lists/settings)
 */
@Composable
fun IOSGroupedCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerRadius.Medium))
            .background(backgroundColor)
    ) {
        Column(content = content)
    }
}
