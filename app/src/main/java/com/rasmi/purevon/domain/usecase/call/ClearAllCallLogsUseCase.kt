package com.rasmi.purevon.domain.usecase.call

import com.rasmi.purevon.domain.repository.CallLogRepository
import javax.inject.Inject

/**
 * Use case to clear all call logs
 */
class ClearAllCallLogsUseCase @Inject constructor(
    private val repository: CallLogRepository
) {
    suspend operator fun invoke() {
        repository.clearAllCallLogs()
    }
}
