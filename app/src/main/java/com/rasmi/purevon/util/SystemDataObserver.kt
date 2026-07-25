package com.rasmi.purevon.util

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.Telephony
import android.util.Log
import com.rasmi.purevon.data.local.dao.CallMetadataDao
import com.rasmi.purevon.data.local.dao.MessageMetadataDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes system content providers and cleans up orphaned metadata
 * When user deletes a call/message from system, we delete our metadata
 */
@Singleton
class SystemDataObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callMetadataDao: CallMetadataDao,
    private val messageMetadataDao: MessageMetadataDao
) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // ✅ FIX #46: Use background looper to avoid blocking Main thread
    private val handlerThread = android.os.HandlerThread("SystemDataObserverThread").also { it.start() }
    private val handler = Handler(handlerThread.looper)
    
    companion object {
        private const val TAG = "SystemDataObserver"
    }
    
    private val smsObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            
            // Check if a specific message was deleted
            uri?.lastPathSegment?.toLongOrNull()?.let { messageId ->
                scope.launch {
                    if (!messageExistsInSystem(messageId)) {
                        Log.d(TAG, "Message $messageId deleted from system, cleaning metadata")
                        messageMetadataDao.deleteMetadata(messageId)
                    }
                }
            }
        }
    }
    
    private val callLogObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            
            // Check if a specific call was deleted
            uri?.lastPathSegment?.toLongOrNull()?.let { callId ->
                scope.launch {
                    if (!callExistsInSystem(callId)) {
                        Log.d(TAG, "Call $callId deleted from system, cleaning metadata")
                        callMetadataDao.deleteMetadata(callId)
                    }
                }
            }
        }
    }
    
    /**
     * Start observing system changes
     */
    fun startObserving() {
        Log.d(TAG, "Starting system data observation")
        
        // Observe SMS changes
        context.contentResolver.registerContentObserver(
            Telephony.Sms.CONTENT_URI,
            true,
            smsObserver
        )
        
        // Observe call log changes
        context.contentResolver.registerContentObserver(
            CallLog.Calls.CONTENT_URI,
            true,
            callLogObserver
        )
    }
    
    /**
     * Stop observing
     */
    fun stopObserving() {
        Log.d(TAG, "Stopping system data observation")
        context.contentResolver.unregisterContentObserver(smsObserver)
        context.contentResolver.unregisterContentObserver(callLogObserver)
        // ✅ FIX #46: Cancel scope and quit handler thread
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        handlerThread.quitSafely()
    }
    
    /**
     * Check if message exists in system database
     */
    private fun messageExistsInSystem(messageId: Long): Boolean {
        return try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                "${Telephony.Sms._ID} = ?",
                arrayOf(messageId.toString()),
                null
            )?.use { cursor ->
                cursor.count > 0
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if message exists", e)
            false
        }
    }
    
    /**
     * Check if call exists in system database
     */
    private fun callExistsInSystem(callId: Long): Boolean {
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID),
                "${CallLog.Calls._ID} = ?",
                arrayOf(callId.toString()),
                null
            )?.use { cursor ->
                cursor.count > 0
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if call exists", e)
            false
        }
    }
    
    /**
     * Clean all orphaned metadata (run on app start)
     * Note: This is a low-priority maintenance task
     * For now, we rely on system to clean up its data
     */
    suspend fun cleanOrphanedMetadata() {
        Log.d(TAG, "Cleaning orphaned metadata...")
        
        // This would require iterating through all metadata
        // and checking if corresponding system entries exist
        // For performance, only run periodically during idle time
        
        // Implementation deferred - not critical for core functionality
        // Orphaned metadata has minimal impact (just extra DB rows)
        
        Log.d(TAG, "Orphaned metadata cleanup deferred (low priority)")
    }
}
