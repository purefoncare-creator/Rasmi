package com.rasmi.purevon.presentation.screen.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Single suggestion row for the recipient autocomplete dropdown.
 * Shows contact avatar placeholder, name, phone number, and an existing-conversation badge.
 */
@Composable
internal fun RecipientSuggestionItem(
    suggestion: RecipientSuggestion,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar placeholder
            com.rasmi.purevon.presentation.component.UnifiedContactAvatar(
                size = 40.dp,
                photoUri = suggestion.photoUri
            )

            // Name + phone
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = suggestion.contactName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = suggestion.phoneNumber,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        maxLines = 1
                    )
                }
            }

            // Badge: existing conversation indicator
            if (suggestion.existingThreadId != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(14.dp)
                    )
                }
            }
        }
    }
}

/** Check if two timestamps fall on the same calendar day */
internal fun isSameDay(ts1: Long, ts2: Long): Boolean {
    val d1 = java.time.Instant.ofEpochMilli(ts1).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val d2 = java.time.Instant.ofEpochMilli(ts2).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    return d1 == d2
}

/** Format a timestamp into a user-friendly date label */
internal fun formatDateLabel(timestamp: Long, todayLabel: String, yesterdayLabel: String): String {
    val date = java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val today = java.time.LocalDate.now()
    return if (date == today) {
        todayLabel
    } else {
        val year = date.year
        val month = String.format(java.util.Locale.ENGLISH, "%02d", date.monthValue)
        val day = String.format(java.util.Locale.ENGLISH, "%02d", date.dayOfMonth)
        "$year\\$month\\$day"
    }
}
