package com.rasmi.purevon.util

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Utility functions for date and time formatting
 */
object DateTimeUtils {
    
    /**
     * Format timestamp for message list display
     * Shows: Time (today), "Yesterday", "Mon-Sun" (this week), "Jan 1" (this year), "01/01/24" (older)
     */
    fun formatMessageTime(timestamp: Long, context: Context? = null): String {
        val locale = Locale.ENGLISH
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", locale)
        
        val instant = Instant.ofEpochMilli(timestamp)
        val messageDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val messageDate = messageDateTime.toLocalDate()
        
        return if (messageDate == today) {
            messageDateTime.format(timeFormatter)
        } else {
            val year = messageDateTime.year
            val month = String.format(locale, "%02d", messageDateTime.monthValue)
            val day = String.format(locale, "%02d", messageDateTime.dayOfMonth)
            "$year\\$month\\$day"
        }
    }
    
    /**
     * Format timestamp for starred messages display
     * Shows: Time (today), "Yesterday", "Mon" (this week), "Jan 1" (older)
     */
    fun formatStarredMessageTime(timestamp: Long, context: Context? = null): String {
        val locale = Locale.ENGLISH
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", locale)
        val shortDateFormatter = DateTimeFormatter.ofPattern("MMM d", locale)
        val dayOfWeekFormatter = DateTimeFormatter.ofPattern("EEE", locale)
        
        val instant = Instant.ofEpochMilli(timestamp)
        val messageDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val messageDate = messageDateTime.toLocalDate()
        
        return when {
            messageDate == today -> messageDateTime.format(timeFormatter)
            messageDate == today.minusDays(1) -> {
                // Use localized "Yesterday" from resources if context available
                context?.getString(com.rasmi.purevon.R.string.label_yesterday) ?: "Yesterday"
            }
            messageDate.isAfter(today.minusDays(7)) -> messageDateTime.format(dayOfWeekFormatter)
            else -> messageDateTime.format(shortDateFormatter)
        }
    }
    
    /**
     * Check if date is today
     */
    fun isToday(timestamp: Long): Boolean {
        val messageDate = Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        return messageDate == LocalDate.now()
    }
    
    /**
     * Check if date is yesterday
     */
    fun isYesterday(timestamp: Long): Boolean {
        val messageDate = Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        return messageDate == LocalDate.now().minusDays(1)
    }
}

