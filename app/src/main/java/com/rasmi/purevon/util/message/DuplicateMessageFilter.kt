package com.rasmi.purevon.util.message

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Duplicate Message Filter
 * Prevents duplicate SMS from appearing in the UI
 * 
 * Inspired by QKSMS implementation
 * 
 * Use case:
 * Sometimes Android system sends the same SMS twice due to:
 * - Multiple receivers registered
 * - System retries
 * - Carrier issues
 */
@Singleton
class DuplicateMessageFilter @Inject constructor() {

    companion object {
        private const val TAG = "DuplicateMessageFilter"
        
        // Time window to consider messages as duplicates (5 seconds)
        private const val DUPLICATE_WINDOW_MS = 5000L
        
        // Max size of cache to prevent memory issues
        private const val MAX_CACHE_SIZE = 200
    }

    /**
     * Cache structure: Key -> Timestamp
     * Key format: "address:body:timestamp_rounded"
     */
    private val messageCache = ConcurrentHashMap<String, MessageInfo>()

    data class MessageInfo(
        val firstSeen: Long,
        val count: Int = 1
    )

    /**
     * Check if a message is a duplicate
     * 
     * @param address Phone number
     * @param body Message text
     * @param timestamp Message timestamp
     * @return true if duplicate, false if unique
     */
    fun isDuplicate(address: String, body: String, timestamp: Long): Boolean {
        // Clean up old entries periodically
        if (messageCache.size > MAX_CACHE_SIZE) {
            cleanupOldEntries(timestamp)
        }

        // Create cache key — no timestamp rounding in key, window check handles proximity
        val key = createKey(address, body)

        val existingInfo = messageCache[key]
        
        return if (existingInfo != null) {
            // Check if within duplicate window
            val timeDiff = kotlin.math.abs(timestamp - existingInfo.firstSeen)
            
            if (timeDiff < DUPLICATE_WINDOW_MS) {
                // It's a duplicate!
                Log.d(TAG, "Duplicate message detected: $address (${timeDiff}ms apart)")
                
                // Update count
                messageCache[key] = existingInfo.copy(count = existingInfo.count + 1)
                true
            } else {
                // Too old, consider it unique
                messageCache[key] = MessageInfo(timestamp)
                false
            }
        } else {
            // First time seeing this message
            messageCache[key] = MessageInfo(timestamp)
            false
        }
    }

    /**
     * Check if duplicate with body hash (for long messages)
     */
    fun isDuplicateByHash(address: String, bodyHash: Int, timestamp: Long): Boolean {
        val normalizedAddress = address.replace(Regex("[\\s-]"), "")
        val key = "$normalizedAddress:$bodyHash"
        val existingTimestamp = messageCache[key]?.firstSeen ?: 0L
        
        val timeDiff = kotlin.math.abs(timestamp - existingTimestamp)
        
        return if (existingTimestamp > 0 && timeDiff < DUPLICATE_WINDOW_MS) {
            Log.d(TAG, "Duplicate message detected (by hash): $address")
            true
        } else {
            messageCache[key] = MessageInfo(timestamp)
            false
        }
    }

    /**
     * Mark a message as processed to prevent duplicates
     */
    fun markAsProcessed(address: String, body: String, timestamp: Long) {
        val key = createKey(address, body)
        messageCache[key] = MessageInfo(timestamp)
    }

    /**
     * Clean up old entries from cache
     */
    private fun cleanupOldEntries(currentTimestamp: Long) {
        val cutoffTime = currentTimestamp - DUPLICATE_WINDOW_MS
        
        val iterator = messageCache.entries.iterator()
        var removed = 0
        
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.firstSeen < cutoffTime) {
                iterator.remove()
                removed++
            }
        }
        
        if (removed > 0) {
            Log.d(TAG, "Cleaned up $removed old entries from duplicate filter")
        }
    }

    /**
     * Clear all cache (use sparingly)
     */
    fun clearCache() {
        messageCache.clear()
        Log.d(TAG, "Duplicate filter cache cleared")
    }

    /**
     * Create cache key from message components.
     * Uses SHA-256 truncated to 16 hex chars to avoid hashCode() collisions
     * that could silently drop legitimate messages.
     */
    private fun createKey(address: String, body: String): String {
        // Normalize address (remove spaces, dashes)
        val normalizedAddress = address.replace(Regex("[\\s-]"), "")
        
        // Use SHA-256 truncated hash instead of hashCode() to avoid collisions
        val bodyDigest = try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val hash = md.digest(body.toByteArray(Charsets.UTF_8))
            hash.take(8).joinToString("") { "%02x".format(it) } // 16 hex chars = 64 bits
        } catch (e: Exception) {
            // Fallback: combine hashCode + length for uniqueness
            "${body.hashCode()}_${body.length}"
        }
        
        return "$normalizedAddress:$bodyDigest"
    }

    /**
     * Get statistics about duplicate detection
     */
    fun getStats(): DuplicateStats {
        val duplicateCount = messageCache.values.count { it.count > 1 }
        return DuplicateStats(
            totalTracked = messageCache.size,
            duplicatesDetected = duplicateCount,
            cacheSize = messageCache.size
        )
    }

    data class DuplicateStats(
        val totalTracked: Int,
        val duplicatesDetected: Int,
        val cacheSize: Int
    )
}
