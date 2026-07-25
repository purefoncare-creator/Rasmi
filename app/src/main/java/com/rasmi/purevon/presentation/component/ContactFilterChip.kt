package com.rasmi.purevon.presentation.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rasmi.purevon.presentation.screen.contacts.ContactFilter
import com.rasmi.purevon.presentation.theme.iOSBlue

/**
 * iOS-style Filter Chip
 */
@Composable
fun ContactFilterChip(
    filter: ContactFilter,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) iOSBlue else MaterialTheme.colorScheme.surfaceVariant,
        label = "chip_background"
    )
    
    val textColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "chip_text"
    )

    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = filter.displayName,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor
        )
    }
}

/**
 * Row of Filter Chips
 */
@Composable
fun ContactFilterRow(
    selectedFilter: ContactFilter,
    onFilterSelected: (ContactFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ContactFilter.entries.forEach { filter ->
            ContactFilterChip(
                filter = filter,
                isSelected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) }
            )
        }
    }
}

private val ContactFilter.displayName: String
    get() = when (this) {
        ContactFilter.ALL -> "All"
        ContactFilter.FAVORITES -> "Favorites"
        ContactFilter.BLOCKED -> "Blocked"
    }
