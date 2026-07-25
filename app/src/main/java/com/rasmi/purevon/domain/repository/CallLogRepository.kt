package com.rasmi.purevon.domain.repository

import com.rasmi.purevon.domain.model.CallType
import com.rasmi.purevon.domain.model.CallLog
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Call Log operations
 */
interface CallLogRepository {
    
    fun getAllCallLogs(): Flow<List<CallLog>>
    
    fun getCallLogsByType(type: CallType): Flow<List<CallLog>>
    
    fun getCallLogsByNumber(phoneNumber: String): Flow<List<CallLog>>
    
    fun getSpamCalls(): Flow<List<CallLog>>
    
    fun searchCallLogs(query: String): Flow<List<CallLog>>
    
    suspend fun getLastOutgoingCall(): CallLog? // ✅ جلب آخر مكالمة صادرة
    
    suspend fun insertCallLog(callLog: CallLog): Long
    
    suspend fun updateCallLog(callLog: CallLog)
    
    suspend fun deleteCallLog(callLogId: Long)
    
    suspend fun deleteCallLogsByNumber(phoneNumber: String)

    suspend fun deleteCallLogsByNumbers(phoneNumbers: List<String>)
    
    suspend fun clearAllCallLogs()
    
    suspend fun getCallCountByType(type: CallType): Int
    
    suspend fun getTotalDuration(types: List<CallType>): Long
    
    suspend fun syncWithSystemCallLog()

    // ✅ System call log queries (moved from ViewModels)
    
    /**
     * Get call statistics for a specific phone number from the system call log.
     * Returns incoming/outgoing/missed counts and total duration.
     */
    suspend fun getSystemCallStatisticsForNumber(phoneNumber: String): SystemCallStatistics
    
    /**
     * Get the most recent call entry for a phone number from the system call log.
     */
    suspend fun getLastSystemCallForNumber(phoneNumber: String): LastSystemCall?
    
    /**
     * Get recent unique phone numbers from system call log (for picker UI).
     */
    suspend fun getRecentUniqueSystemCalls(limit: Int = 50): List<RecentSystemCall>
}

/**
 * Call statistics from system call log for a specific phone number.
 */
data class SystemCallStatistics(
    val incomingCalls: Int = 0,
    val outgoingCalls: Int = 0,
    val missedCalls: Int = 0,
    val totalCalls: Int = 0,
    val totalDurationSeconds: Long = 0,
    val lastCallTimestamp: Long? = null
)

/**
 * Last call entry from system call log.
 */
data class LastSystemCall(
    val type: Int,
    val timestamp: Long,
    val duration: Long
)

/**
 * Recent call entry from system call log (for picker UI).
 */
data class RecentSystemCall(
    val phoneNumber: String,
    val contactName: String?,
    val callType: Int,
    val timestamp: Long,
    val photoUri: String? = null
)
