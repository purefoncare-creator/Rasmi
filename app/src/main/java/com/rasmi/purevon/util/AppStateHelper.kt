package com.rasmi.purevon.util

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Helper class to check app state
 * Determines if app is in foreground/background and which screen is active
 */
object AppStateHelper {
    private const val TAG = "AppStateHelper"
    
    /**
     * Current active conversation thread ID
     * Set by ConversationScreen when opened, null when closed
     */
    @Volatile
    var activeConversationThreadId: Long? = null
        private set
    
    /**
     * Set active conversation thread ID
     * Should be called when ConversationScreen is opened
     */
    fun setActiveConversation(threadId: Long?) {
        activeConversationThreadId = threadId
        Log.d(TAG, "Active conversation set to: $threadId")
    }
    
    /**
     * Check if app is in foreground
     * Uses ProcessLifecycleOwner for accurate detection
     */
    fun isAppInForeground(): Boolean {
        val isForeground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        Log.d(TAG, "App in foreground: $isForeground")
        return isForeground
    }
    
    /**
     * Check if user is currently viewing a specific conversation
     * @param threadId The thread ID to check
     * @return true if user is viewing this conversation and app is in foreground
     */
    fun isUserInConversation(threadId: Long): Boolean {
        val inConversation = isAppInForeground() && activeConversationThreadId == threadId
        Log.d(TAG, "User in conversation $threadId: $inConversation (active: $activeConversationThreadId)")
        return inConversation
    }
    
    /**
     * Check if app is in foreground using ActivityManager (fallback method)
     */
    fun isAppInForegroundCompat(context: Context): Boolean {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val appProcesses = activityManager.runningAppProcesses ?: return false
            
            val packageName = context.packageName
            appProcesses.any { processInfo ->
                processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                processInfo.processName == packageName
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking foreground state", e)
            false
        }
    }
    
    /**
     * Determine if notification should be shown
     * @param threadId The thread ID of the incoming message
     * @return true if notification should be shown
     */
    fun shouldShowNotification(threadId: Long): Boolean {
        // Don't show notification if user is actively viewing this conversation
        if (isUserInConversation(threadId)) {
            Log.d(TAG, "Notification suppressed - user viewing conversation $threadId")
            return false
        }
        
        // Show notification if app is in background OR user is in different screen
        val shouldShow = !isAppInForeground() || activeConversationThreadId != threadId
        Log.d(TAG, "Should show notification for $threadId: $shouldShow")
        return shouldShow
    }
}
