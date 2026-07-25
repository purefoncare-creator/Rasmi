package com.rasmi.purevon.domain.usecase.call

import com.rasmi.purevon.domain.model.CallType
import com.rasmi.purevon.domain.model.CallLog
import com.rasmi.purevon.domain.repository.CallLogRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case to get all call logs
 */
class GetAllCallLogsUseCase @Inject constructor(
    private val repository: CallLogRepository
) {
    operator fun invoke(): Flow<List<CallLog>> {
        return repository.getAllCallLogs()
    }
}

/**
 * Use case to get call logs by type
 */
class GetCallLogsByTypeUseCase @Inject constructor(
    private val repository: CallLogRepository
) {
    operator fun invoke(type: CallType): Flow<List<CallLog>> {
        return repository.getCallLogsByType(type)
    }
}

/**
 * Use case to get call logs for a specific number
 */
class GetCallLogsByNumberUseCase @Inject constructor(
    private val repository: CallLogRepository
) {
    operator fun invoke(phoneNumber: String): Flow<List<CallLog>> {
        return repository.getCallLogsByNumber(phoneNumber)
    }
}

/**
 * Use case to search call logs
 */
class SearchCallLogsUseCase @Inject constructor(
    private val repository: CallLogRepository
) {
    operator fun invoke(query: String): Flow<List<CallLog>> {
        return repository.searchCallLogs(query)
    }
}

/**
 * Use case to delete a call log
 */
class DeleteCallLogUseCase @Inject constructor(
    private val repository: CallLogRepository
) {
    suspend operator fun invoke(callLogId: Long) {
        repository.deleteCallLog(callLogId)
    }
}

/**
 * Deletes every system call log row whose number matches [phoneNumber].
 */
class DeleteCallLogsByNumberUseCase @Inject constructor(
    private val repository: CallLogRepository
) {
    suspend operator fun invoke(phoneNumber: String) {
        repository.deleteCallLogsByNumber(phoneNumber)
    }
}

/**
 * Use case to sync call logs with system
 */
class SyncCallLogsUseCase @Inject constructor(
    private val repository: CallLogRepository
) {
    suspend operator fun invoke() {
        repository.syncWithSystemCallLog()
    }
}

/**
 * Use case to get call statistics
 */
class GetCallStatisticsUseCase @Inject constructor(
    private val repository: CallLogRepository
) {
    suspend operator fun invoke(): CallStatistics {
        val incomingCount = repository.getCallCountByType(CallType.INCOMING)
        val outgoingCount = repository.getCallCountByType(CallType.OUTGOING)
        val missedCount = repository.getCallCountByType(CallType.MISSED)
        val totalDuration = repository.getTotalDuration(listOf(CallType.INCOMING, CallType.OUTGOING))
        
        return CallStatistics(
            incomingCount = incomingCount,
            outgoingCount = outgoingCount,
            missedCount = missedCount,
            totalDurationSeconds = totalDuration
        )
    }
}

data class CallStatistics(
    val incomingCount: Int,
    val outgoingCount: Int,
    val missedCount: Int,
    val totalDurationSeconds: Long
)
