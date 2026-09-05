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
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

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
            com.rasmi.purevon.presentation.component.FavoriteContactAvatar(
                size = 40.dp,
                photoUri = suggestion.photoUri,
                isFavorite = suggestion.isFavorite
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

/**
 * Wire-style date divider formatting.
 * Returns UPPERCASED string based on time difference from now.
 */
internal fun formatDateLabelWire(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - timestamp
    val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(diffMs)
    val diffHours = TimeUnit.MILLISECONDS.toHours(diffMs)
    val diffDays = TimeUnit.MILLISECONDS.toDays(diffMs)

    val messageDate = java.time.Instant.ofEpochMilli(timestamp)
        .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val today = java.time.LocalDate.now()
    val yesterday = today.minusDays(1)

    return when {
        // < 1 minute: "Just now"
        diffMinutes < 1 -> "JUST NOW"
        // 1-30 minutes: "X min ago"
        diffMinutes <= 30 -> "${diffMinutes} MIN AGO"
        // Same day (>30 min): "Today"
        messageDate == today -> "TODAY"
        // Yesterday: "Yesterday"
        messageDate == yesterday -> "YESTERDAY"
        // Within 7 days: "Tuesday, August 19"
        diffDays <= 7 -> {
            val formatter = DateFormat.getDateInstance(DateFormat.LONG, Locale.getDefault())
            formatter.format(Date(timestamp)).uppercase(Locale.getDefault())
        }
        // Same year: "August 19"
        messageDate.year == today.year -> {
            val formatter = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
            formatter.format(Date(timestamp)).uppercase(Locale.getDefault())
        }
        // Different year: "August 19, 2024"
        else -> {
            val formatter = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())
            formatter.format(Date(timestamp)).uppercase(Locale.getDefault())
        }
    }
}

/**
 * Format a timestamp for individual message display (Wire-style).
 * Uses DateFormat.SHORT for locale-dependent time (e.g., "6:02 PM" or "18:02").
 */
internal fun formatMessageTimeWire(timestamp: Long): String {
    val formatter = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
    return formatter.format(Date(timestamp))
}
