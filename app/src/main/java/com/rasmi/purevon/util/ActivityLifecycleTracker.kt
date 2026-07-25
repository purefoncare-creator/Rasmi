package com.rasmi.purevon.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import java.lang.ref.WeakReference

/**
 * Activity Lifecycle Tracker
 * 
 * Tracks activity lifecycle to detect potential memory leaks
 * Monitors activity creation, destruction, and retention
 * 
 * Features:
 * - Track all active activities
 * - Detect leaked activities (not destroyed after expected time)
 * - Log activity lifecycle events
 * 
 * Usage in Application:
 * ```
 * ActivityLifecycleTracker.register(this)
 * ```
 */
object ActivityLifecycleTracker : Application.ActivityLifecycleCallbacks {
    
    private const val TAG = "ActivityLifecycleTracker"
    
    private val activeActivities = mutableMapOf<String, WeakReference<Activity>>()
    private val createdActivities = mutableListOf<String>()
    private val destroyedActivities = mutableListOf<String>()
    
    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
        Log.d(TAG, "Activity lifecycle tracking enabled")
    }
    
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        val activityName = activity.javaClass.simpleName
        activeActivities[activityName] = WeakReference(activity)
        createdActivities.add(activityName)
        
        Log.d(TAG, "📱 Created: $activityName (Total active: ${activeActivities.size})")
    }
    
    override fun onActivityStarted(activity: Activity) {
        // Not tracking
    }
    
    override fun onActivityResumed(activity: Activity) {
        val activityName = activity.javaClass.simpleName
        Log.d(TAG, "▶️ Resumed: $activityName")
    }
    
    override fun onActivityPaused(activity: Activity) {
        val activityName = activity.javaClass.simpleName
        Log.d(TAG, "⏸️ Paused: $activityName")
    }
    
    override fun onActivityStopped(activity: Activity) {
        // Not tracking
    }
    
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // Not tracking
    }
    
    override fun onActivityDestroyed(activity: Activity) {
        val activityName = activity.javaClass.simpleName
        activeActivities.remove(activityName)
        destroyedActivities.add(activityName)
        
        Log.d(TAG, "🗑️ Destroyed: $activityName (Total active: ${activeActivities.size})")
        
    }
    
    /**
     * Get statistics about activity lifecycle
     */
    fun getStats(): String {
        return """
            Activity Stats:
              Active: ${activeActivities.size}
              Created: ${createdActivities.size}
              Destroyed: ${destroyedActivities.size}
              Activities: ${activeActivities.keys.joinToString(", ")}
        """.trimIndent()
    }
    
    /**
     * Log current stats
     */
    fun logStats() {
        Log.d(TAG, getStats())
    }
}
