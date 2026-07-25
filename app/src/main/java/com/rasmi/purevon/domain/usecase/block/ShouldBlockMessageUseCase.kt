package com.rasmi.purevon.domain.usecase.block

import com.rasmi.purevon.domain.repository.BlockRepository
import javax.inject.Inject

/**
 * Use case to check if a message should be blocked
 */
class ShouldBlockMessageUseCase @Inject constructor(
    private val repository: BlockRepository
) {
    suspend operator fun invoke(phoneNumber: String): Boolean {
        return repository.shouldBlockMessage(phoneNumber)
    }
}
