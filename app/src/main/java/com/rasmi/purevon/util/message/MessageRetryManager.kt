package com.rasmi.purevon.util.message

import android.util.Log
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

/**
 * ✅ P1: Message Retry Manager with Exponential Backoff
 * 
 * Inspired by QKSMS retry logic:
 * - 1st retry: 1 second
 * - 2nd retry: 2 seconds
 * - 3rd retry: 4 seconds
 * - 4th retry: 8 seconds
 * - 5th retry: 16 seconds
 * - Max: 32 seconds
 * - Max retries: 5
 */
@Singleton
class MessageRetryManager @Inject constructor() {
    
    companion object {
        private const val TAG = "MessageRetryManager"
        private const val BASE_DELAY_MS = 1000L // 1 second
        private const val MAX_DELAY_MS = 32_000L // 32 seconds
        private const val MAX_RETRIES = 5
    }
    
    // Track retry attempts per message ID
    // ✅ FIXED: Use ConcurrentHashMap for thread-safe access from coroutines
    private val retryAttempts = ConcurrentHashMap<Long, Int>()
    
    /**
     * Calculate delay for next retry using exponential backoff
     * 
     * Formula: delay = BASE_DELAY * (2 ^ attempt)
     * Example:
     * - Attempt 0: 1s
     * - Attempt 1: 2s
     * - Attempt 2: 4s
     * - Attempt 3: 8s
     * - Attempt 4: 16s
     * - Attempt 5: 32s
     */
    fun getRetryDelay(messageId: Long): Long {
        val attempt = retryAttempts[messageId] ?: 0
        
        if (attempt >= MAX_RETRIES) {
            Log.w(TAG, "❌ Message $messageId exceeded max retries ($MAX_RETRIES)")
            retryAttempts.remove(messageId) // ✅ Evict to prevent unbounded map growth
            return -1 // Signal to stop retrying
        }
        
        // ✅ FIX M39: Increment counter so callers using getRetryDelay directly get proper backoff
        retryAttempts.compute(messageId) { _, v -> (v ?: 0) + 1 }
        
        val delay = min(
            BASE_DELAY_MS * (2.0.pow(attempt.toDouble())).toLong(),
            MAX_DELAY_MS
        )
        
        Log.d(TAG, "📊 Retry delay for message $messageId (attempt $attempt): ${delay}ms")
        
        return delay
    }
    
    /**
     * Execute retry with automatic exponential backoff
     * 
     * @return true if retry should continue, false if max retries reached
     */
    suspend fun <T> retryWithBackoff(
        messageId: Long,
        operation: suspend () -> Result<T>
    ): Result<T> {
        val delay = getRetryDelay(messageId)
        
        if (delay < 0) {
            return Result.failure(
                MaxRetriesExceededException("Max retries ($MAX_RETRIES) reached for message $messageId")
            )
        }
        
        // Wait before retry
        if (delay > 0) {
            Log.d(TAG, "⏳ Waiting ${delay}ms before retry...")
            delay(delay)
        }
        
        // Execute operation
        val result = operation()
        
        // Reset counter on success
        if (result.isSuccess) {
            retryAttempts.remove(messageId)
            Log.d(TAG, "✅ Retry successful for message $messageId")
        } else {
            Log.w(TAG, "❌ Retry failed for message $messageId: ${result.exceptionOrNull()?.message}")
        }
        
        return result
    }
    
    /**
     * Reset retry counter for a message
     */
    fun resetRetryCount(messageId: Long) {
        retryAttempts.remove(messageId)
        Log.d(TAG, "🔄 Reset retry count for message $messageId")
    }
    
    /**
     * Get current retry attempt for a message
     */
    fun getRetryAttempt(messageId: Long): Int {
        return retryAttempts[messageId] ?: 0
    }
    
    /**
     * Check if message can be retried.
     * Evicts entry if max retries reached to prevent unbounded map growth.
     */
    fun canRetry(messageId: Long): Boolean {
        val attempt = retryAttempts[messageId] ?: 0
        if (attempt >= MAX_RETRIES) {
            retryAttempts.remove(messageId) // ✅ Evict exhausted entries
            return false
        }
        return true
    }
    
    /**
     * Clear all retry tracking entries.
     * Call on app lifecycle events to prevent stale entries.
     */
    fun clearAll() {
        retryAttempts.clear()
        Log.d(TAG, "🧹 Cleared all retry tracking entries")
    }
    
    /**
     * Get retry status message for UI
     */
    fun getRetryStatusMessage(messageId: Long): String {
        val attempt = retryAttempts[messageId] ?: 0
        return when {
            attempt >= MAX_RETRIES -> "فشل الإرسال بعد $MAX_RETRIES محاولات"
            attempt > 0 -> "إعادة المحاولة ($attempt/$MAX_RETRIES)..."
            else -> "فشل الإرسال"
        }
    }
}

/**
 * Exception thrown when max retries exceeded
 */
class MaxRetriesExceededException(message: String) : Exception(message)
