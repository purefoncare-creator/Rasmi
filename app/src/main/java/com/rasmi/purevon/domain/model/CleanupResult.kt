package com.rasmi.purevon.domain.model

/**
 * Result of cleanup operation
 */
data class CleanupResult(
    val totalProcessed: Int,
    val successCount: Int,
    val failedCount: Int,
    val failures: List<CleanupFailure>
) {
    /**
     * Get success percentage
     */
    fun getSuccessPercentage(): Int {
        return if (totalProcessed > 0) {
            (successCount * 100) / totalProcessed
        } else 0
    }
    
    /**
     * Whether cleanup was completely successful
     */
    fun isFullySuccessful(): Boolean = failedCount == 0
}

/**
 * Information about a failed cleanup
 */
data class CleanupFailure(
    val contactId: Long,
    val displayName: String,
    val phoneNumber: String,
    val errorMessage: String
)
