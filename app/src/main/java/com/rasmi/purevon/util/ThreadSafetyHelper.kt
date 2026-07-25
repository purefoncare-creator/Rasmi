package com.rasmi.purevon.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread Safety Helper
 * 
 * Provides utilities for thread-safe operations using Mutex
 * 
 * Usage:
 * ```
 * private val mutex = Mutex()
 * 
 * suspend fun criticalOperation() = mutex.withLock {
 *     // Thread-safe code here
 * }
 * ```
 * 
 * Features:
 * - Prevents race conditions
 * - Ensures sequential execution of critical sections
 * - Non-blocking (suspending) locks
 * - Automatic lock release on completion or exception
 */
object ThreadSafetyHelper {
    
    /**
     * Execute block with timeout protection
     * Returns null if operation times out
     */
    suspend fun <T> Mutex.withLockTimeout(
        timeoutMillis: Long = 5000,
        block: suspend () -> T
    ): T? {
        return try {
            kotlinx.coroutines.withTimeout(timeoutMillis) {
                withLock {
                    block()
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            android.util.Log.w("ThreadSafetyHelper", "Operation timed out after ${timeoutMillis}ms")
            null
        }
    }
    
    /**
     * Try to acquire lock without waiting
     * Returns null if lock is already held
     */
    suspend fun <T> Mutex.tryWithLock(block: suspend () -> T): T? {
        return if (tryLock()) {
            try {
                block()
            } finally {
                unlock()
            }
        } else {
            android.util.Log.d("ThreadSafetyHelper", "Lock already held, skipping operation")
            null
        }
    }
    
    /**
     * Statistics for monitoring lock contention
     */
    class LockStats {
        private var lockCount = 0
        private var contentionCount = 0
        private var totalWaitTime = 0L
        
        fun recordLockAcquired(waitTime: Long) {
            lockCount++
            if (waitTime > 0) {
                contentionCount++
                totalWaitTime += waitTime
            }
        }
        
        fun getStats(): String {
            val avgWaitTime = if (contentionCount > 0) totalWaitTime / contentionCount else 0
            return "Locks: $lockCount, Contentions: $contentionCount, Avg Wait: ${avgWaitTime}ms"
        }
    }
}
