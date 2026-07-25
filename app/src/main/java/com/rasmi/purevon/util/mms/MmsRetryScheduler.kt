package com.rasmi.purevon.util.mms

import android.util.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Exponential backoff retry scheduler for MMS send operations.
 * Inspired by QKSMS RetryScheduler/DefaultRetryScheme but simplified for coroutine-based architecture.
 *
 * Retry schedule (5 attempts total):
 *   Attempt 0: immediate
 *   Attempt 1: 30 seconds
 *   Attempt 2: 1 minute
 *   Attempt 3: 2 minutes
 *   Attempt 4: 5 minutes
 *
 * Usage:
 *   val result = MmsRetryScheduler.executeWithRetry("mms-$messageId") {
 *       sendMmsViaSystem(...)
 *   }
 */
object MmsRetryScheduler {

    private const val TAG = "MmsRetryScheduler"

    /** Maximum number of retry attempts (excluding the initial attempt) */
    const val MAX_RETRIES = 5

    /**
     * Delay schedule in milliseconds.
     * Index 0 = immediate retry, index 4 = final retry after 5 minutes.
     * Total worst-case time: ~8.5 minutes.
     */
    private val RETRY_DELAYS_MS = longArrayOf(
        0L,                 // Attempt 0: immediate
        30_000L,            // Attempt 1: 30 seconds
        60_000L,            // Attempt 2: 1 minute
        120_000L,           // Attempt 3: 2 minutes
        300_000L            // Attempt 4: 5 minutes
    )

    /** Tracks retry count per operation key to prevent duplicate retries */
    private val retryCounters = mutableMapOf<String, Int>()

    /**
     * Execute an operation with exponential backoff retry.
     *
     * @param operationKey Unique key for this operation (e.g., "mms-$messageId")
     * @param isRetryable Error predicate — return true if the error should trigger a retry
     * @param block The suspend operation to execute
     * @return The result of the operation, or the last failure
     */
    suspend fun <T> executeWithRetry(
        operationKey: String,
        isRetryable: (Exception) -> Boolean = { true },
        block: suspend () -> T
    ): Result<T> {
        val startCount = getRetryCount(operationKey)
        var lastException: Exception? = null

        for (attempt in startCount until MAX_RETRIES) {
            if (attempt > 0) {
                // Check for cancellation before sleeping
                if (!currentCoroutineContext().isActive) {
                    Log.d(TAG, "🚫 Coroutine cancelled before retry attempt $attempt for '$operationKey'")
                    resetRetryCount(operationKey)
                    return Result.failure(kotlinx.coroutines.CancellationException("MMS send cancelled"))
                }
                val delayMs = getDelayForAttempt(attempt)
                Log.d(TAG, "⏳ Retry attempt $attempt/$MAX_RETRIES for '$operationKey' — waiting ${delayMs / 1000}s...")
                delay(delayMs)
            }

            try {
                incrementRetryCount(operationKey)
                val result = block()
                resetRetryCount(operationKey)
                Log.d(TAG, "✅ Operation '$operationKey' succeeded on attempt ${attempt + 1}")
                return Result.success(result)
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "⚠️ Attempt ${attempt + 1}/${MAX_RETRIES} failed for '$operationKey': ${e.message}")

                if (!isRetryable(e)) {
                    Log.d(TAG, "🚫 Error is not retryable, aborting for '$operationKey'")
                    resetRetryCount(operationKey)
                    return Result.failure(e)
                }
            }
        }

        resetRetryCount(operationKey)
        Log.e(TAG, "❌ All $MAX_RETRIES attempts exhausted for '$operationKey'")
        return Result.failure(lastException ?: Exception("All retry attempts exhausted"))
    }

    /**
     * Get the delay in milliseconds for a given attempt number.
     * Uses the schedule: [0s, 30s, 1m, 2m, 5m]
     */
    fun getDelayForAttempt(attempt: Int): Long {
        return if (attempt in RETRY_DELAYS_MS.indices) {
            RETRY_DELAYS_MS[attempt]
        } else {
            RETRY_DELAYS_MS.last()
        }
    }

    /**
     * Check if more retries are available for this operation.
     */
    fun canRetry(operationKey: String): Boolean {
        val count = getRetryCount(operationKey)
        return count < MAX_RETRIES
    }

    /**
     * Get the number of retries already performed for this operation.
     */
    fun getRetryCount(operationKey: String): Int {
        return synchronized(retryCounters) {
            retryCounters[operationKey] ?: 0
        }
    }

    private fun incrementRetryCount(operationKey: String) {
        synchronized(retryCounters) {
            retryCounters[operationKey] = (retryCounters[operationKey] ?: 0) + 1
        }
    }

    private fun resetRetryCount(operationKey: String) {
        synchronized(retryCounters) {
            retryCounters.remove(operationKey)
        }
    }

    /**
     * Get a human-readable summary of the retry schedule for debugging/UI.
     */
    fun getScheduleSummary(): String {
        return buildString {
            appendLine("MMS Retry Schedule (max $MAX_RETRIES retries):")
            RETRY_DELAYS_MS.forEachIndexed { index, delayMs ->
                val seconds = delayMs / 1000
                val timeStr = when {
                    seconds == 0L -> "immediate"
                    seconds < 60 -> "${seconds}s"
                    else -> "${seconds / 60}m${if (seconds % 60 > 0) " ${seconds % 60}s" else ""}"
                }
                appendLine("  Attempt $index: $timeStr")
            }
        }
    }
}
