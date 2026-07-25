package com.rasmi.purevon.util.message

import com.rasmi.purevon.domain.model.MessageError

/**
 * Message Send Result sealed class
 */
sealed class MessageSendResult {
    data class Success(
        val messageId: Long,
        val sentTime: Long = System.currentTimeMillis()
    ) : MessageSendResult()
    
    data class Failure(
        val error: MessageError,
        val canRetry: Boolean = true,
        val retryDelay: Long = 0L
    ) : MessageSendResult()
    
    data class Pending(
        val messageId: Long,
        val estimatedTime: Long? = null
    ) : MessageSendResult()
}

/**
 * Retry Strategy
 */
data class RetryStrategy(
    val attempt: Int = 0,
    val maxAttempts: Int = 3,
    val baseDelay: Long = 10_000L,
    val useExponentialBackoff: Boolean = true
) {
    val canRetry: Boolean get() = attempt < maxAttempts
    
    /**
     * Calculate next retry delay with exponential backoff
     */
    fun getNextDelay(): Long {
        return if (useExponentialBackoff) {
            // Exponential backoff: baseDelay * 2^attempt
            baseDelay * (1 shl attempt).coerceAtMost(8)
        } else {
            baseDelay
        }
    }
    
    /**
     * Get next retry strategy
     */
    fun next(): RetryStrategy {
        return copy(attempt = attempt + 1)
    }
}
