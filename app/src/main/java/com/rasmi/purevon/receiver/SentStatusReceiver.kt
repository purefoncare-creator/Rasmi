package com.rasmi.purevon.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import com.rasmi.purevon.util.event.EventBus
import com.rasmi.purevon.util.event.AppEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.rasmi.purevon.data.local.dao.MessageMetadataDao
import com.rasmi.purevon.data.local.entity.MultipartMessageStatus
import com.rasmi.purevon.data.local.entity.MessagePartStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Receiver for SMS sent status
 * Updates message status in database when SMS is sent (or fails to send)
 */
@AndroidEntryPoint
class SentStatusReceiver : BroadcastReceiver() {
    
    @Inject lateinit var messageMetadataDao: MessageMetadataDao

    
    companion object {
        const val ACTION_SMS_SENT_STATUS = "com.rasmi.purevon.SMS_SENT_STATUS"
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_PHONE_NUMBER = "phone_number"
        const val EXTRA_PART_INDEX = "part_index"
        const val EXTRA_TOTAL_PARTS = "total_parts"
        private const val TAG = "SentStatusReceiver"
        
        // Per-message locks to prevent race condition in multipart status updates
        // ✅ FIX M14: Track creation time to evict stale entries
        private data class TimedMutex(val mutex: Mutex, val createdAt: Long = System.currentTimeMillis())
        private val multipartLocks = java.util.concurrent.ConcurrentHashMap<Long, TimedMutex>()
        private const val LOCK_EXPIRY_MS = 5 * 60 * 1000L // 5 minutes
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        // ✅ FIX M41: Validate messageId
        if (messageId <= 0L) {
            Log.w(TAG, "⚠️ Invalid messageId: $messageId — ignoring")
            return
        }
        val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
        val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, -1)
        val totalParts = intent.getIntExtra(EXTRA_TOTAL_PARTS, 1)
        
        Log.d(TAG, "=== SMS Sent Status ===")
        Log.d(TAG, "Message ID: $messageId")
        Log.d(TAG, "Phone: ${com.rasmi.purevon.util.DebugLogger.maskPhoneNumber(phoneNumber)}")
        Log.d(TAG, "Part: ${partIndex + 1}/$totalParts")
        Log.d(TAG, "Result code: $resultCode")
        
        // Handle multipart messages
        if (totalParts > 1 && partIndex >= 0) {
            handleMultipartStatus(context, messageId, phoneNumber, partIndex, totalParts, resultCode)
        } else {
            // Single part message - handle immediately
            handleSinglePartStatus(context, messageId, phoneNumber, resultCode)
        }
    }
    
    private fun handleSinglePartStatus(context: Context, messageId: Long, phoneNumber: String, resultCode: Int) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        Log.d(TAG, "✅ SMS sent successfully")
                        updateMessageStatus(context, messageId, isSent = true, isFailed = false)
                        
                        // Emit event to UI using EventBus
                        EventBus.tryEmit(
                            AppEvent.SmsSent(
                                phoneNumber = phoneNumber,
                                success = true,
                                messageUri = messageId.toString()
                            )
                        )
                    }
                    
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                        Log.e(TAG, "❌ SMS failed: Generic failure")
                        updateMessageStatus(context, messageId, isSent = false, isFailed = true)
                        notifyFailure(context, phoneNumber, messageId, context.getString(com.rasmi.purevon.R.string.sms_generic_send_failure))
                    }
                    
                    SmsManager.RESULT_ERROR_NO_SERVICE -> {
                        Log.e(TAG, "❌ SMS failed: No service")
                        updateMessageStatus(context, messageId, isSent = false, isFailed = true)
                        notifyFailure(context, phoneNumber, messageId, context.getString(com.rasmi.purevon.R.string.sms_no_service))
                    }
                    
                    SmsManager.RESULT_ERROR_NULL_PDU -> {
                        Log.e(TAG, "❌ SMS failed: Null PDU")
                        updateMessageStatus(context, messageId, isSent = false, isFailed = true)
                        notifyFailure(context, phoneNumber, messageId, context.getString(com.rasmi.purevon.R.string.sms_error_null_pdu))
                    }
                    
                    SmsManager.RESULT_ERROR_RADIO_OFF -> {
                        Log.e(TAG, "❌ SMS failed: Radio off")
                        updateMessageStatus(context, messageId, isSent = false, isFailed = true)
                        notifyFailure(context, phoneNumber, messageId, context.getString(com.rasmi.purevon.R.string.sms_error_radio_off))
                    }
                    
                    SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> {
                        Log.e(TAG, "❌ SMS failed: Limit exceeded")
                        updateMessageStatus(context, messageId, isSent = false, isFailed = true)
                        notifyFailure(context, phoneNumber, messageId, context.getString(com.rasmi.purevon.R.string.sms_error_limit_exceeded))
                    }
                    
                    else -> {
                        Log.e(TAG, "❌ SMS failed: Unknown error ($resultCode)")
                        updateMessageStatus(context, messageId, isSent = false, isFailed = true)
                        notifyFailure(context, phoneNumber, messageId, context.getString(com.rasmi.purevon.R.string.sms_error_unknown))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling single part status", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
    
    private fun updateMessageStatus(
        context: Context, 
        messageId: Long, 
        isSent: Boolean, 
        isFailed: Boolean
    ) {
        try {
            val values = ContentValues().apply {
                // Update type based on status
                if (isFailed) {
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED)
                } else if (isSent) {
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                }
            }
            
            val uri = Telephony.Sms.CONTENT_URI
            val selection = "${Telephony.Sms._ID} = ?"
            val selectionArgs = arrayOf(messageId.toString())
            
            val updated = context.contentResolver.update(uri, values, selection, selectionArgs)
            Log.d(TAG, "Updated $updated message(s) in database")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update message status", e)
        }
    }
    
    private fun handleMultipartStatus(
        context: Context,
        messageId: Long,
        phoneNumber: String,
        partIndex: Int,
        totalParts: Int,
        resultCode: Int
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(25_000L) {
                // Synchronize per-message to prevent race condition when multiple parts
                // broadcast simultaneously — ensures atomic read-modify-write on Room metadata
                // ✅ FIX M14: Evict stale locks before acquiring
                val now = System.currentTimeMillis()
                multipartLocks.entries.removeIf { now - it.value.createdAt > LOCK_EXPIRY_MS }
                val timedMutex = multipartLocks.getOrPut(messageId) { TimedMutex(Mutex()) }
                timedMutex.mutex.withLock {
                // 1. Get existing metadata or create new
                var metadata = messageMetadataDao.getMetadata(messageId)
                
                // If no metadata or no multipart status, initialize it
                if (metadata == null || metadata.multipartStatus == null) {
                    val initialParts = List(totalParts) { i -> 
                        MessagePartStatus(i, isSent = false) 
                    }
                    val newStatus = MultipartMessageStatus(totalParts, initialParts)
                    
                    if (metadata == null) {
                        messageMetadataDao.insertMetadata(
                            com.rasmi.purevon.data.local.entity.MessageMetadataEntity(
                                systemMessageId = messageId,
                                multipartStatus = newStatus
                            )
                        )
                    } else {
                        messageMetadataDao.updateMetadata(
                            metadata.copy(multipartStatus = newStatus)
                        )
                    }
                    metadata = messageMetadataDao.getMetadata(messageId)
                }
                
                // 2. Update the specific part
                metadata?.let { meta ->
                    val currentStatus = meta.multipartStatus ?: return@let
                    val updatedParts = currentStatus.parts.toMutableList()
                    
                    val isSuccess = resultCode == Activity.RESULT_OK
                    if (partIndex < updatedParts.size) {
                        updatedParts[partIndex] = updatedParts[partIndex].copy(
                            isSent = isSuccess,
                            errorCode = if (isSuccess) null else resultCode
                        )
                    }
                    
                    val updatedStatus = currentStatus.copy(parts = updatedParts)
                    messageMetadataDao.updateMetadata(meta.copy(multipartStatus = updatedStatus))
                    
                    // 3. Check completion
                    val successCount = updatedParts.count { it.isSent }
                    val failedCount = updatedParts.count { !it.isSent && it.errorCode != null }
                    val isComplete = (successCount + failedCount) == totalParts
                    
                    if (isComplete) {
                        if (successCount == totalParts) {
                            Log.d(TAG, "✅ All parts sent successfully")
                            updateMessageStatus(context, messageId, isSent = true, isFailed = false)
                            EventBus.tryEmit(AppEvent.SmsSent(phoneNumber, true, messageId.toString()))
                        } else {
                            Log.e(TAG, "❌ Some parts failed ($failedCount failed)")
                            updateMessageStatus(context, messageId, isSent = false, isFailed = true)
                            notifyFailure(context, phoneNumber, messageId, context.getString(com.rasmi.purevon.R.string.sms_parts_send_failure, failedCount, totalParts))
                        }
                    } else {
                        Log.d(TAG, "⏳ Waiting for remaining parts: ${successCount + failedCount}/$totalParts completed")
                    }
                }
                } // withLock
                // Clean up lock if message is complete
                if (messageMetadataDao.getMetadata(messageId)?.multipartStatus?.let { s ->
                    s.parts.count { it.isSent } + s.parts.count { !it.isSent && it.errorCode != null } == s.totalParts
                } == true) {
                    multipartLocks.remove(messageId)
                }
                } // withTimeout
            } catch (e: Exception) {
                Log.e(TAG, "Error handling multipart status", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
    
    private fun notifyFailure(context: Context, phoneNumber: String, messageId: Long, reason: String) {
        // Emit failure event using EventBus
        EventBus.tryEmit(
            AppEvent.SmsSent(
                phoneNumber = phoneNumber,
                success = false,
                messageUri = messageId.toString()
            )
        )
        Log.d(TAG, "SMS failure event emitted for: ${com.rasmi.purevon.util.DebugLogger.maskPhoneNumber(phoneNumber)}, reason: $reason")
        
        // Show System Notification with Sound
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val soundUri = android.net.Uri.parse("android.resource://" + context.packageName + "/" + com.rasmi.purevon.R.raw.recieve)
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                // Use a dedicated channel for failures (or share the messages one)
                val channel = android.app.NotificationChannel(
                    "message_failure_channel",
                    context.getString(com.rasmi.purevon.R.string.notification_channel_failures),
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(com.rasmi.purevon.R.string.notification_channel_failures_desc)
                    enableLights(true)
                    enableVibration(true)
                    setSound(soundUri, android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                }
                notificationManager.createNotificationChannel(channel)
            }
            
            // PendingIntent to retry or open thread (just opens app for now)
            val intent = Intent(context, com.rasmi.purevon.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("navigate_to", "conversation")
            }
            
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                messageId.toInt(), // Use messageId as request code
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            
            val notification = androidx.core.app.NotificationCompat.Builder(context, "message_failure_channel")
                .setSmallIcon(com.rasmi.purevon.R.drawable.ic_notification)
                .setContentTitle(context.getString(com.rasmi.purevon.R.string.notification_send_failed_title))
                .setContentText(context.getString(com.rasmi.purevon.R.string.notification_send_failed_text, phoneNumber, reason))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ERROR)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setSound(soundUri)
                .build()
                
            notificationManager.notify(messageId.toInt(), notification)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing failure notification", e)
        }
    }
}
