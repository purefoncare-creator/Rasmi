package com.rasmi.purevon.util

import android.telecom.Call
import android.util.Log
import com.rasmi.purevon.data.local.entity.CallType
import com.rasmi.purevon.domain.model.CallLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility for grouping calls by various criteria
 */
@Singleton
class CallGrouper @Inject constructor() {
    
    companion object {
        private const val TAG = "CallGrouper"
    }
    
    /**
     * Group calls by date
     */
    fun groupByDate(calls: List<CallLog>): Map<String, List<CallLog>> {
        return calls.groupBy { call ->
            getDateLabel(call.date)
        }
    }
    
    /**
     * Group calls by contact
     */
    fun groupByContact(calls: List<CallLog>): Map<String, List<CallLog>> {
        return calls.groupBy { call ->
            call.name ?: call.number
        }
    }
    
    /**
     * Group calls by type (incoming, outgoing, missed)
     */
    fun groupByType(calls: List<CallLog>): Map<CallType, List<CallLog>> {
        return calls.groupBy { it.callType }
    }
    
    /**
     * Merge consecutive calls with same number
     */
    fun mergeConsecutiveCalls(calls: List<CallLog>): List<CallGroup> {
        val groups = mutableListOf<CallGroup>()
        var currentGroup: CallGroup? = null
        
        calls.forEach { call ->
            val lastGroup = currentGroup
            
            if (lastGroup == null ||
                lastGroup.number != call.number ||
                lastGroup.type != call.type ||
                call.date - lastGroup.lastDate > 3600_000 // 1 hour
            ) {
                // Start new group
                currentGroup = CallGroup(
                    number = call.number,
                    name = call.name,
                    type = call.type,
                    calls = mutableListOf(call),
                    count = 1,
                    firstDate = call.date,
                    lastDate = call.date,
                    totalDuration = call.duration
                )
                currentGroup?.let { groups.add(it) }
            } else {
                // Add to existing group
                lastGroup.calls.add(call)
                lastGroup.count++
                lastGroup.lastDate = call.date
                lastGroup.totalDuration += call.duration
            }
        }
        
        return groups
    }
    
    /**
     * Get date label for call
     */
    private fun getDateLabel(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 86400_000 -> "Today"
            diff < 172800_000 -> "Yesterday"
            diff < 604800_000 -> {
                val dayFormat = java.text.SimpleDateFormat("EEEE", java.util.Locale.ENGLISH)
                dayFormat.format(java.util.Date(timestamp))
            }
            else -> {
                val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.ENGLISH)
                dateFormat.format(java.util.Date(timestamp))
            }
        }
    }
    
    /**
     * Get most frequent contacts
     */
    fun getMostFrequentContacts(calls: List<CallLog>, limit: Int = 10): List<ContactFrequency> {
        return calls
            .groupBy { it.number }
            .map { (number, callList) ->
                ContactFrequency(
                    number = number,
                    name = callList.firstOrNull()?.name,
                    callCount = callList.size,
                    totalDuration = callList.sumOf { it.duration },
                    lastCallDate = callList.maxOfOrNull { it.date } ?: 0
                )
            }
            .sortedByDescending { it.callCount }
            .take(limit)
    }
    
    /**
     * Get call statistics
     */
    fun getCallStatistics(calls: List<CallLog>): CallStatistics {
        val incoming = calls.count { it.callType == CallType.INCOMING }
        val outgoing = calls.count { it.callType == CallType.OUTGOING }
        val missed = calls.count { it.callType == CallType.MISSED }
        val blocked = calls.count { it.callType == CallType.BLOCKED }
        
        val totalDuration = calls.sumOf { it.duration }
        val averageDuration = if (calls.isNotEmpty()) totalDuration / calls.size else 0
        
        return CallStatistics(
            totalCalls = calls.size,
            incomingCalls = incoming,
            outgoingCalls = outgoing,
            missedCalls = missed,
            blockedCalls = blocked,
            totalDuration = totalDuration,
            averageDuration = averageDuration
        )
    }
}

/**
 * Call group
 */
data class CallGroup(
    val number: String,
    val name: String?,
    val type: Int,
    val calls: MutableList<CallLog>,
    var count: Int,
    val firstDate: Long,
    var lastDate: Long,
    var totalDuration: Long
)

/**
 * Contact frequency
 */
data class ContactFrequency(
    val number: String,
    val name: String?,
    val callCount: Int,
    val totalDuration: Long,
    val lastCallDate: Long
)

/**
 * Call statistics
 */
data class CallStatistics(
    val totalCalls: Int,
    val incomingCalls: Int,
    val outgoingCalls: Int,
    val missedCalls: Int,
    val blockedCalls: Int,
    val totalDuration: Long,
    val averageDuration: Long
)
