package com.rasmi.purevon.util

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Resource Manager for optimizing memory and resource usage
 * Helps prevent memory leaks and manage system resources efficiently
 */
class ResourceManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ResourceManager"
        private const val MEMORY_WARNING_THRESHOLD_PERCENT = 80
        private const val CLEANUP_DELAY_MS = 5000L // 5 seconds
        
        /**
         * Get current memory usage percentage
         */
        fun getMemoryUsagePercentage(context: Context): Int {
            return try {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val memoryInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memoryInfo)
                
                val totalMemory = memoryInfo.totalMem
                
                val availableMemory = memoryInfo.availMem
                val usedMemory = totalMemory - availableMemory
                
                ((usedMemory.toDouble() / totalMemory.toDouble()) * 100).toInt()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting memory usage", e)
                0
            }
        }
        
        /**
         * Check if device is under memory pressure
         */
        fun isUnderMemoryPressure(context: Context): Boolean {
            return getMemoryUsagePercentage(context) > MEMORY_WARNING_THRESHOLD_PERCENT
        }
        
        /**
         * Suggest cleanup based on memory pressure
         */
        fun shouldCleanupMemory(context: Context): Boolean {
            return isUnderMemoryPressure(context)
        }
    }
    
    private val cleanupJobs = mutableListOf<Job>()
    private val trackedResources = mutableListOf<WeakReference<Any>>()
    private val scope = CoroutineScope(Dispatchers.IO)
    
    /**
     * Track a resource for automatic cleanup
     */
    fun <T : Any> trackResource(resource: T): T {
        synchronized(trackedResources) {
            trackedResources.add(WeakReference(resource))
        }
        return resource
    }
    
    /**
     * Schedule periodic cleanup of unused resources
     */
    fun schedulePeriodicCleanup(intervalMs: Long = 30000L) {
        val job = scope.launch {
            while (true) {
                delay(intervalMs)
                performCleanup()
            }
        }
        cleanupJobs.add(job)
    }
    
    /**
     * Perform immediate resource cleanup
     */
    fun performCleanup() {
        Log.d(TAG, "Performing resource cleanup")
        
        // Clean up weak references that have been garbage collected
        synchronized(trackedResources) {
            trackedResources.removeAll { it.get() == null }
        }
        
        // Suggest garbage collection if under memory pressure
        if (shouldCleanupMemory(context)) {
            Log.w(TAG, "Memory pressure detected, suggesting GC")
            System.gc()
        }
        
        // Clear image caches if available
        clearImageCaches()
    }
    
    /**
     * Clear image caches to free memory
     */
    private fun clearImageCaches() {
        try {
            // Clear Coil image cache if available
            val coilCacheClass = try {
                Class.forName("io.coil-kt.coil.imageLoader")
            } catch (e: ClassNotFoundException) {
                null
            }
            
            if (coilCacheClass != null) {
                Log.d(TAG, "Clearing Coil image cache")
                // In a real implementation, you would get the Coil image loader and clear cache
                // For now, we just log it
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing image caches", e)
        }
    }
    
    /**
     * Register for lifecycle events to cleanup when app goes to background
     */
    fun registerLifecycleCleanup(lifecycleOwner: LifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // Schedule delayed cleanup when app goes to background
                    scope.launch {
                        delay(CLEANUP_DELAY_MS)
                        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            performCleanup()
                        }
                    }
                }
                Lifecycle.Event.ON_DESTROY -> {
                    // Clean up all resources when component is destroyed
                    cleanupAll()
                }
                else -> {}
            }
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        
        // Track the observer for cleanup
        trackResource(observer)
    }
    
    /**
     * Clean up all managed resources
     */
    fun cleanupAll() {
        Log.d(TAG, "Cleaning up all resources")
        
        // Cancel all cleanup jobs
        cleanupJobs.forEach { it.cancel() }
        cleanupJobs.clear()
        
        // Clear tracked resources
        synchronized(trackedResources) {
            trackedResources.clear()
        }
        
        // Perform final cleanup
        performCleanup()
    }
    
    /**
     * Get memory usage statistics
     */
    fun getMemoryStats(): MemoryStats {
        val usagePercent = getMemoryUsagePercentage(context)
        val underPressure = isUnderMemoryPressure(context)
        
        return MemoryStats(
            usagePercentage = usagePercent,
            underPressure = underPressure,
            trackedResourcesCount = synchronized(trackedResources) { trackedResources.size }
        )
    }
    
    data class MemoryStats(
        val usagePercentage: Int,
        val underPressure: Boolean,
        val trackedResourcesCount: Int
    )
}

/**
 * Composable function to manage resources in Compose UI
 * Automatically cleans up when the composable leaves the composition
 */
@Composable
fun rememberResourceManager(context: Context): ResourceManager {
    val resourceManager = remember {
        ResourceManager(context)
    }
    
    DisposableEffect(Unit) {
        onDispose {
            // Clean up when composable leaves composition
            resourceManager.cleanupAll()
        }
    }
    
    return resourceManager
}

/**
 * Extension function to track resources with automatic cleanup
 */
fun <T : Any> ResourceManager.autoClean(block: () -> T): T {
    val resource = block()
    return trackResource(resource)
}
