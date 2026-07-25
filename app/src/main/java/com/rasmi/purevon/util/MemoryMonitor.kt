package com.rasmi.purevon.util

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Memory Monitor
 * 
 * Monitors app memory usage and provides statistics
 * Helps identify memory leaks and excessive memory consumption
 * 
 * Usage:
 * ```
 * MemoryMonitor.startMonitoring(context)
 * MemoryMonitor.logMemoryStats()
 * MemoryMonitor.stopMonitoring()
 * ```
 */
object MemoryMonitor {
    
    private const val TAG = "MemoryMonitor"
    private const val MONITORING_INTERVAL_MS = 300_000L // 5 minutes (avoids blocking Doze)
    
    private var isMonitoring = false
    private var monitoringScope: CoroutineScope? = null
    
    data class MemoryStats(
        val usedMemoryMB: Double,
        val freeMemoryMB: Double,
        val totalMemoryMB: Double,
        val maxMemoryMB: Double,
        val memoryPercentage: Double,
        val nativeHeapSizeMB: Double,
        val nativeHeapAllocatedMB: Double
    ) {
        override fun toString(): String {
            return """
                Memory Stats:
                  Used: ${usedMemoryMB.roundToInt()} MB (${memoryPercentage.roundToInt()}%)
                  Free: ${freeMemoryMB.roundToInt()} MB
                  Total: ${totalMemoryMB.roundToInt()} MB
                  Max: ${maxMemoryMB.roundToInt()} MB
                  Native Heap: ${nativeHeapAllocatedMB.roundToInt()} / ${nativeHeapSizeMB.roundToInt()} MB
            """.trimIndent()
        }
    }
    
    /**
     * Get current memory statistics
     */
    fun getMemoryStats(context: Context): MemoryStats {
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1048576.0
        val freeMemory = runtime.freeMemory() / 1048576.0
        val totalMemory = runtime.totalMemory() / 1048576.0
        val maxMemory = runtime.maxMemory() / 1048576.0
        val memoryPercentage = (usedMemory / maxMemory) * 100
        
        val nativeHeapSize = Debug.getNativeHeapSize() / 1048576.0
        val nativeHeapAllocated = Debug.getNativeHeapAllocatedSize() / 1048576.0
        
        return MemoryStats(
            usedMemoryMB = usedMemory,
            freeMemoryMB = freeMemory,
            totalMemoryMB = totalMemory,
            maxMemoryMB = maxMemory,
            memoryPercentage = memoryPercentage,
            nativeHeapSizeMB = nativeHeapSize,
            nativeHeapAllocatedMB = nativeHeapAllocated
        )
    }
    
    /**
     * Log current memory statistics
     */
    fun logMemoryStats(context: Context? = null) {
        context?.let {
            val stats = getMemoryStats(it)
            Log.d(TAG, stats.toString())
            
            // Warn if memory usage is high
            if (stats.memoryPercentage > 85) {
                Log.w(TAG, "⚠️ High memory usage: ${stats.memoryPercentage.roundToInt()}%")
            }
        }
    }
    
    /**
     * Start continuous memory monitoring
     */
    fun startMonitoring(context: Context, scope: CoroutineScope = CoroutineScope(Dispatchers.Default)) {
        if (isMonitoring) {
            Log.w(TAG, "Monitoring already started")
            return
        }
        
        isMonitoring = true
        monitoringScope = scope
        
        scope.launch {
            Log.d(TAG, "Started memory monitoring (interval: ${MONITORING_INTERVAL_MS}ms)")
            
            while (isActive && isMonitoring) {
                logMemoryStats(context)
                delay(MONITORING_INTERVAL_MS)
            }
            
            Log.d(TAG, "Stopped memory monitoring")
        }
    }
    
    /**
     * Stop memory monitoring
     */
    fun stopMonitoring() {
        isMonitoring = false
        monitoringScope = null
    }
    
    /**
     * Check if memory usage is critical
     */
    fun isMemoryCritical(context: Context, threshold: Double = 90.0): Boolean {
        val stats = getMemoryStats(context)
        return stats.memoryPercentage >= threshold
    }
    
    /**
     * Request garbage collection
     * Note: This is just a suggestion to the VM, not guaranteed
     */
    fun requestGC() {
        Log.d(TAG, "Requesting garbage collection")
        System.gc()
        System.runFinalization()
    }
    
    /**
     * Get available memory info from ActivityManager
     */
    fun getAvailableMemory(context: Context): ActivityManager.MemoryInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return ActivityManager.MemoryInfo().also { memoryInfo ->
            activityManager.getMemoryInfo(memoryInfo)
        }
    }
    
    /**
     * Check if device is in low memory state
     */
    fun isLowMemory(context: Context): Boolean {
        val memoryInfo = getAvailableMemory(context)
        return memoryInfo.lowMemory
    }
}
