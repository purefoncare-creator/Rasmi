package com.rasmi.purevon.domain.usecase.block

import com.rasmi.purevon.domain.repository.BlockRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case to get all blocked numbers
 */
class GetBlockedNumbersUseCase @Inject constructor(
    private val repository: BlockRepository
) {
    operator fun invoke(): Flow<List<String>> {
        return repository.getAllBlockedNumbers()
    }
}

/**
 * Use case to block a number
 */
class BlockNumberUseCase @Inject constructor(
    private val repository: BlockRepository
) {
    suspend operator fun invoke(phoneNumber: String, reason: String? = null) {
        repository.blockNumber(phoneNumber, reason)
    }
}

/**
 * Use case to unblock a number
 */
class UnblockNumberUseCase @Inject constructor(
    private val repository: BlockRepository
) {
    suspend operator fun invoke(phoneNumber: String) {
        repository.unblockNumber(phoneNumber)
    }
}

/**
 * Use case to check if a number is blocked
 */
class IsNumberBlockedUseCase @Inject constructor(
    private val repository: BlockRepository
) {
    suspend operator fun invoke(phoneNumber: String): Boolean {
        return repository.isNumberBlocked(phoneNumber)
    }
}

/**
 * Use case to block wildcard pattern
 */
class BlockWildcardUseCase @Inject constructor(
    private val repository: BlockRepository
) {
    suspend operator fun invoke(pattern: String, reason: String? = null) {
        repository.blockWildcard(pattern, reason)
    }
}
