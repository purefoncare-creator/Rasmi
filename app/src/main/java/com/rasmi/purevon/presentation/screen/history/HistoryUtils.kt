package com.rasmi.purevon.presentation.screen.history

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.rasmi.purevon.data.local.entity.CallType
import com.rasmi.purevon.presentation.theme.PurevonError
import com.rasmi.purevon.presentation.theme.PurevonTertiary
import com.rasmi.purevon.presentation.theme.PurevonCallIncoming
import com.rasmi.purevon.presentation.theme.PurevonCallMissed
import com.rasmi.purevon.presentation.theme.PurevonCallOutgoing
import com.rasmi.purevon.presentation.theme.PurevonCallRejected
import com.rasmi.purevon.R
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

internal fun groupContactCallsByDay(
    groups: List<GroupedContactCalls>
): List<Pair<LocalDate, List<GroupedContactCalls>>> {
    val zone = ZoneId.systemDefault()
    return groups
        .groupBy { Instant.ofEpochMilli(it.lastTimestamp).atZone(zone).toLocalDate() }
        .entries
        .sortedByDescending { it.key }
        .map { (date, list) ->
            date to list.sortedByDescending { it.lastTimestamp }
        }
}

@Composable
internal fun getFilterIconAndColor(filter: CallFilter): Pair<ImageVector, Color> {
    return when (filter) {
        CallFilter.ALL -> Icons.Default.Phone to PurevonTertiary
        CallFilter.MISSED -> Icons.AutoMirrored.Filled.CallMissed to PurevonCallMissed
        CallFilter.INCOMING -> Icons.AutoMirrored.Filled.CallReceived to PurevonCallIncoming
        CallFilter.OUTGOING -> Icons.AutoMirrored.Filled.CallMade to PurevonCallOutgoing
        CallFilter.REJECTED -> Icons.Default.CallEnd to PurevonCallRejected
        CallFilter.BLOCKED -> Icons.Default.Block to PurevonError
    }
}

@Composable
internal fun CallFilter.getDisplayName(): String {
    val context = LocalContext.current
    return when (this) {
        CallFilter.ALL -> context.getString(R.string.history_filter_all)
        CallFilter.MISSED -> context.getString(R.string.history_filter_missed)
        CallFilter.INCOMING -> context.getString(R.string.history_filter_incoming)
        CallFilter.OUTGOING -> context.getString(R.string.history_filter_outgoing)
        CallFilter.REJECTED -> context.getString(R.string.history_filter_rejected)
        CallFilter.BLOCKED -> context.getString(R.string.history_filter_blocked)
    }
}

@Composable
internal fun CallType.getDisplayName(): String {
    val context = LocalContext.current
    return when (this) {
        CallType.INCOMING -> context.getString(R.string.history_call_type_incoming)
        CallType.OUTGOING -> context.getString(R.string.history_call_type_outgoing)
        CallType.MISSED -> context.getString(R.string.history_call_type_missed)
        CallType.REJECTED -> context.getString(R.string.history_call_type_rejected)
        CallType.BLOCKED -> context.getString(R.string.history_call_type_blocked)
        CallType.VOICEMAIL -> context.getString(R.string.history_type_voicemail)
    }
}

internal fun formatCompactDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60

    return when {
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}

@Composable
internal fun getCallTypeIconAndColor(callType: CallType, isBlocked: Boolean): Pair<ImageVector, Color> {
    return when {
        isBlocked -> Icons.Default.Block to PurevonError
        callType == CallType.MISSED -> Icons.AutoMirrored.Filled.CallMissed to PurevonCallMissed
        callType == CallType.INCOMING -> Icons.AutoMirrored.Filled.CallReceived to PurevonCallIncoming
        callType == CallType.OUTGOING -> Icons.AutoMirrored.Filled.CallMade to PurevonCallOutgoing
        callType == CallType.REJECTED -> Icons.Default.CallEnd to PurevonCallRejected
        else -> Icons.Default.Call to PurevonTertiary
    }
}

internal fun formatCallDurationHistory(seconds: Long): String {
    if (seconds == 0L) return ""

    val minutes = seconds / 60
    val secs = seconds % 60

    return when {
        minutes == 0L -> "${secs}s"
        secs == 0L -> "${minutes}m"
        else -> "${minutes}:${secs.toString().padStart(2, '0')}"
    }
}

internal fun formatHistoryClockTime(timestamp: Long): String {
    return try {
        val instant = Instant.ofEpochMilli(timestamp)
        SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(Date(timestamp))
    } catch (_: Exception) {
        "--:--"
    }
}

internal fun formatRelativeDateTime(timestamp: Long, context: android.content.Context): String {
    return try {
        val locale = java.util.Locale.ENGLISH
        val instant = Instant.ofEpochMilli(timestamp)
        val callDateTime = instant.atZone(ZoneId.systemDefault())
        val callDate = callDateTime.toLocalDate()
        val callTime = callDateTime.toLocalTime()
        val today = LocalDate.now()

        val dateText = when {
            callDate == today -> context.getString(R.string.history_today)
            callDate == today.minusDays(1) -> context.getString(R.string.history_yesterday)
            callDate.isAfter(today.minusDays(7)) -> {
                callDate.format(java.time.format.DateTimeFormatter.ofPattern("EEEE", locale))
            }
            callDate.year == today.year -> {
                callDate.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", locale))
            }
            else -> {
                callDate.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", locale))
            }
        }

        val timeText = callTime.format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a", locale))
        "$dateText, $timeText"
    } catch (e: Exception) {
        "--:--"
    }
}
