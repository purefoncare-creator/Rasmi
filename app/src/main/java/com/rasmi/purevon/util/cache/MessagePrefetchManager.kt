package com.rasmi.purevon.util.cache

import android.util.Log
import com.rasmi.purevon.domain.model.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Message Prefetching Manager
 * Predictive loading for smooth scrolling
 * 
 * Inspired by QKSMS prefetching strategy
 * 
 * Features:
 * - Prefetch next page of messages
 * - Prefetch attachments (images/videos)
 * - Smart prediction based on scroll direction
 * - Background processing
 */
@Singleton
class MessagePrefetchManager @Inject constructor(
    private val imageCacheManager: ImageCacheManager
) {
    
    companion object {
        private const val TAG = "MessagePrefetch"
        private const val PREFETCH_DELAY_MS = 500L
        private const val PREFETCH_AHEAD_COUNT = 20
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var prefetchJob: Job? = null
    
    /**
     * Prefetch messages and their attachments
     */
    fun prefetchMessages(messages: List<Message>) {
        // Cancel previous prefetch job
        prefetchJob?.cancel()
        
        prefetchJob = scope.launch {
            delay(PREFETCH_DELAY_MS) // Debounce rapid scrolling
            
            Log.d(TAG, "🔄 Prefetching ${messages.size} messages...")
            
            // Extract image URIs from messages
            val imageUris = messages.flatMap { message: Message ->
                message.attachmentUris.mapIndexed { index: Int, uriString: String ->
                    if (message.attachmentTypes.getOrNull(index)?.startsWith("image/") == true) {
                        try {
                            android.net.Uri.parse(uriString)
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                }
            }.filterNotNull()
            
            if (imageUris.isNotEmpty()) {
                Log.d(TAG, "📸 Prefetching ${imageUris.size} images...")
                imageCacheManager.prefetchImages(imageUris)
            }
            
            Log.d(TAG, "✅ Prefetch completed")
        }
    }
    
    /**
     * Prefetch based on scroll position
     * @param currentIndex Current visible item index
     * @param totalCount Total items in list
     * @param messages All messages
     */
    fun prefetchOnScroll(
        currentIndex: Int,
        totalCount: Int,
        messages: List<Message>
    ) {
        // Prefetch ahead
        val startIndex = (currentIndex + 1).coerceAtMost(totalCount - 1)
        val endIndex = (startIndex + PREFETCH_AHEAD_COUNT).coerceAtMost(totalCount)
        
        if (startIndex < endIndex && endIndex <= messages.size) {
            val messagesToPrefetch = messages.subList(startIndex, endIndex)
            prefetchMessages(messagesToPrefetch)
        }
    }
    
    /**
     * Cancel ongoing prefetch
     */
    fun cancelPrefetch() {
        prefetchJob?.cancel()
        Log.d(TAG, "❌ Prefetch cancelled")
    }
    
    /**
     * Get prefetch statistics
     */
    fun getStats(): PrefetchStats {
        return PrefetchStats(
            isActive = prefetchJob?.isActive == true,
            cacheStats = imageCacheManager.getCacheStats()
        )
    }
    
    data class PrefetchStats(
        val isActive: Boolean,
        val cacheStats: ImageCacheManager.CacheStats
    )
}
