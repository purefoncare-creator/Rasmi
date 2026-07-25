package com.rasmi.purevon.domain.usecase.spam

import com.rasmi.purevon.domain.repository.SpamRepository
import javax.inject.Inject

/**
 * Use case to check if a number is spam
 */
class CheckIfSpamUseCase @Inject constructor(
    private val repository: SpamRepository
) {
    suspend operator fun invoke(phoneNumber: String): SpamCheckResult {
        val (isSpam, score) = repository.checkIfSpam(phoneNumber)
        return SpamCheckResult(
            isSpam = isSpam,
            spamScore = score,
            riskLevel = when {
                score >= 0.8f -> SpamRiskLevel.HIGH
                score >= 0.5f -> SpamRiskLevel.MEDIUM
                score >= 0.3f -> SpamRiskLevel.LOW
                else -> SpamRiskLevel.SAFE
            }
        )
    }
}

data class SpamCheckResult(
    val isSpam: Boolean,
    val spamScore: Float,
    val riskLevel: SpamRiskLevel
)

enum class SpamRiskLevel {
    SAFE,
    LOW,
    MEDIUM,
    HIGH
}

/**
 * Use case to report a number as spam
 */
class ReportSpamUseCase @Inject constructor(
    private val repository: SpamRepository
) {
    suspend operator fun invoke(phoneNumber: String) {
        repository.reportSpam(phoneNumber)
    }
}

/**
 * Use case to mark a number as not spam
 */
class MarkAsNotSpamUseCase @Inject constructor(
    private val repository: SpamRepository
) {
    suspend operator fun invoke(phoneNumber: String) {
        repository.markAsNotSpam(phoneNumber)
    }
}
