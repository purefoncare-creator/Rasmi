package com.rasmi.purevon.domain.usecase.call

import com.rasmi.purevon.domain.repository.CallLogRepository
import javax.inject.Inject

/**
 * Use case to delete call logs for multiple phone numbers in a single batch
 */
class DeleteCallLogsByNumbersUseCase @Inject constructor(
    private val repository: CallLogRepository
) {
    suspend operator fun invoke(phoneNumbers: List<String>) {
        repository.deleteCallLogsByNumbers(phoneNumbers)
    }
}
