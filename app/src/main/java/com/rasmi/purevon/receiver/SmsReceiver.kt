package com.rasmi.purevon.receiver

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.rasmi.purevon.R
import com.rasmi.purevon.util.DebugLogger
import com.rasmi.purevon.util.event.EventBus
import com.rasmi.purevon.util.event.AppEvent
import com.rasmi.purevon.util.message.OtpManager
import com.rasmi.purevon.util.SoundManager
import com.rasmi.purevon.data.preferences.SettingsDataStore
import com.rasmi.purevon.PurevonApp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * EntryPoint for accessing Hilt dependencies in BroadcastReceiver
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SmsReceiverEntryPoint {
    fun otpManager(): OtpManager
    fun settingsDataStore(): SettingsDataStore
    fun soundManager(): SoundManager
    fun duplicateMessageFilter(): com.rasmi.purevon.util.message.DuplicateMessageFilter
    fun enhancedNotificationManager(): com.rasmi.purevon.notification.EnhancedNotificationManager
    fun messageRepository(): com.rasmi.purevon.domain.repository.MessageRepository
    fun blockRepository(): com.rasmi.purevon.domain.repository.BlockRepository
    fun cachedMessageDao(): com.rasmi.purevon.data.local.dao.CachedMessageDao
}

/**
 * Receiver for incoming SMS messages
 * Uses simple approach without Hilt injection for reliability
 */
class SmsReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "SmsReceiver"
        // Deprecated: Use EventBus instead
        @Deprecated("Use EventBus.AppEvent.SmsReceived")
        const val ACTION_SMS_RECEIVED = "com.rasmi.purevon.SMS_RECEIVED"
        @Deprecated("Use EventBus.AppEvent.SmsReceived")
        const val EXTRA_THREAD_ID = "thread_id"
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        
        Log.d(TAG, "=== SMS Broadcast received! Action: ${intent.action} ===")
        
        // IMPORTANT: Only handle SMS_DELIVER_ACTION to avoid duplicate messages
        // SMS_DELIVER_ACTION is sent only to the default SMS app
        // SMS_RECEIVED_ACTION is sent to all apps and can cause duplicates
        val action = intent.action
        if (action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            Log.d(TAG, "Ignoring action: $action (only handling SMS_DELIVER_ACTION)")
            return
        }
        
        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages == null || messages.isEmpty()) {
                Log.d(TAG, "No messages in intent")
                return
            }
            
            Log.d(TAG, "Processing ${messages.size} SMS message(s)")
            
            // Use goAsync to handle in background with a scoped coroutine
            val pendingResult = goAsync()
            
            // Create a local scope that will be cancelled when work is done
            val job = SupervisorJob()
            val scope = CoroutineScope(job + Dispatchers.IO)
            
            scope.launch {
                try {
                    kotlinx.coroutines.withTimeout(20_000L) { // Safe 20s timeout to avoid 30s goAsync() ANR/crash
                    // Group messages by sender to handle multipart SMS correctly
                    // Multipart messages have the same originatingAddress
                    val groupedMessages = messages.groupBy { it.displayOriginatingAddress ?: "" }
                    
                    for ((sender, messageParts) in groupedMessages) {
                        if (sender.isBlank()) continue
                        
                        // Combine all parts into a single message body
                        // Parts are usually in order, just concatenate them
                        val combinedBody = messageParts.joinToString("") { it.messageBody ?: "" }
                        
                        // Use timestamp from first part
                        val timestamp = messageParts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
                        
                        Log.d(TAG, "Processing multipart SMS: ${messageParts.size} parts from ${DebugLogger.maskPhoneNumber(sender)}")
                        
                        handleCombinedSmsMessage(sender, combinedBody, timestamp, context)
                    }
                    } // withTimeout
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    Log.e(TAG, "❌ SMS processing TIMEOUT! Message may have been lost. Consider increasing timeout.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing SMS messages", e)
                } finally {
                    job.cancel() // Cancel the job to clean up
                    pendingResult.finish()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error receiving SMS", e)
        }
    }
    
    /**
     * Handle a combined SMS message (all parts merged)
     * This is the main handler for both single and multipart SMS
     */
    private suspend fun handleCombinedSmsMessage(
        phoneNumber: String,
        messageBody: String,
        timestamp: Long,
        context: Context
    ) {
        try {
            if (messageBody.isBlank()) return
            
            // ✅ NEW: Check for duplicate messages before processing
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                SmsReceiverEntryPoint::class.java
            )
            val duplicateFilter = entryPoint.duplicateMessageFilter()
            
            if (duplicateFilter.isDuplicate(phoneNumber, messageBody, timestamp)) {
                Log.w(TAG, "⚠️ Duplicate message detected from ${DebugLogger.maskPhoneNumber(phoneNumber)} - skipping")
                return
            }

            // ✅ FIX: Honor blocked-number list for incoming SMS
            // - Blocked numbers (or matching wildcards) are silently dropped: no DB insert, no notification.
            // - This mirrors CallScreeningService behavior for incoming calls.
            try {
                val blockRepository = entryPoint.blockRepository()
                if (blockRepository.shouldBlockMessage(phoneNumber)) {
                    Log.w(TAG, "🚫 SMS BLOCKED from: ${DebugLogger.maskPhoneNumber(phoneNumber)} (blocked-number rule)")
                    return
                }
            } catch (e: Exception) {
                // On unexpected errors in the block check, fail-open (deliver) to avoid losing real messages.
                Log.e(TAG, "Block-check failed; allowing SMS to proceed", e)
            }

            DebugLogger.sensitive(TAG, "SMS received from: ${DebugLogger.maskPhoneNumber(phoneNumber)}")
            DebugLogger.sensitive(TAG, "Message (${messageBody.length} chars): ${DebugLogger.maskMessage(messageBody, 30)}")
            
            // CRITICAL: Insert SMS into system database first
            var threadId = getOrCreateThreadId(context, phoneNumber)
            if (threadId <= 0L) {
                // ✅ FIX M4: Don't insert with threadId=0 — message becomes invisible
                // Retry once with Telephony.Threads API directly
                threadId = try {
                    Telephony.Threads.getOrCreateThreadId(context, phoneNumber)
                } catch (e: Exception) {
                    Log.e(TAG, "Retry getOrCreateThreadId also failed", e)
                    0L
                }
                if (threadId <= 0L) {
                    Log.e(TAG, "❌ Cannot create thread for ${DebugLogger.maskPhoneNumber(phoneNumber)} — inserting without thread to prevent data loss")
                }
            }
            val messageId = insertNewSMS(
                context = context,
                address = phoneNumber,
                body = messageBody,
                date = timestamp,
                read = 0,
                threadId = threadId,
                type = Telephony.Sms.MESSAGE_TYPE_INBOX
            )
            
            if (messageId > 0) {
                DebugLogger.d(TAG, "SMS inserted into system database with ID: $messageId")

                // ✅ CRITICAL: Sync cache immediately from receiver (independent of UI/ViewModel lifecycle)
                // This makes message list + conversation update instantly even if the screen is not open.
                try {
                    val messageUri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, messageId.toString())
                    val messageRepository = entryPoint.messageRepository()
                    messageRepository.syncMessage(messageUri)
                    messageRepository.forceSyncConversations()
                    DebugLogger.d(TAG, "Immediate cache sync completed for messageId=$messageId")
                } catch (e: Exception) {
                    Log.e(TAG, "Immediate cache sync failed for messageId=$messageId", e)
                }
                
                // Sound is handled by the notification channel (R.raw.recieve)
                
                // Check and handle OTP auto-copy if enabled
                // Returns true if OTP bubble was shown
                val otpBubbleShown = handleOtpAutoCopy(context, messageBody, phoneNumber, messageId)
                
                if (otpBubbleShown) {
                    // ✅ OTP bubble is shown → Don't show notification at all
                    Log.d(TAG, "OTP bubble shown - notification suppressed")
                } else {
                    // Regular message - Show notification if needed
                    // Smart notification logic:
                    // - Check if user is viewing this conversation → Don't show notification
                    // - App in background OR user in different screen → Show notification
                    val shouldShowNotif = com.rasmi.purevon.util.AppStateHelper.shouldShowNotification(threadId)
                    
                    if (shouldShowNotif) {
                        Log.d(TAG, "Regular message - showing enhanced notification")
                        showEnhancedNotification(context, phoneNumber, messageBody, threadId, timestamp)
                    } else {
                        Log.d(TAG, "Notification suppressed - user viewing conversation")
                    }
                }
                
                // Emit event to update UI immediately using EventBus (modern approach)
                EventBus.tryEmit(
                    AppEvent.SmsReceived(
                        phoneNumber = phoneNumber,
                        messageBody = messageBody,
                        timestamp = System.currentTimeMillis(),
                        threadId = threadId
                    )
                )
                DebugLogger.d(TAG, "Event emitted for new SMS from: ${DebugLogger.maskPhoneNumber(phoneNumber)}")
            } else {
                Log.e(TAG, "❌ Failed to insert SMS into system database")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling SMS message", e)
        }
    }
    
    
    /**
     * Handle OTP auto-copy if enabled in settings
     * Shows floating bubble instead of auto-copy (Android 14+ compatibility)
     * @return true if OTP bubble was shown, false otherwise
     */
    private suspend fun handleOtpAutoCopy(
        context: Context,
        messageBody: String,
        sender: String,
        messageId: Long
    ): Boolean {
        try {
            // Get dependencies via EntryPoint
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                SmsReceiverEntryPoint::class.java
            )
            
            val settingsDataStore = entryPoint.settingsDataStore()
            val otpManager = entryPoint.otpManager()
            
            // Check if auto-copy is enabled
            val autoCopyEnabled = settingsDataStore.otpAutoCopyEnabled.first()
            if (!autoCopyEnabled) {
                Log.d(TAG, "OTP auto-copy is disabled")
                return false
            }
            
            // Check trusted senders if enabled
            val onlyTrusted = settingsDataStore.otpOnlyTrustedSenders.first()
            if (onlyTrusted) {
                val trustedSenders = settingsDataStore.otpTrustedSenders.first()
                val senderLower = sender.lowercase()
                val isTrusted = trustedSenders.any { 
                    senderLower.contains(it.lowercase()) || it.lowercase().contains(senderLower)
                }
                if (!isTrusted) {
                    Log.d(TAG, "Sender ${DebugLogger.maskPhoneNumber(sender)} is not in trusted list")
                    return false
                }
            }
            
            // Sync min-length preference before detection so stale cache doesn't affect result
            otpManager.refreshMinLength()

            // Detect OTP
            val otp = otpManager.detectOtp(messageBody)
            if (otp != null) {
                // Check minimum length
                val minLength = settingsDataStore.otpMinLength.first()
                if (otp.length >= minLength) {
                    // ✅ Show floating OTP bubble instead of auto-copy
                    // This works on Android 14+ even when app is in background
                    // Mark the cached message as OTP category so inbox filters work
                    try {
                        entryPoint.cachedMessageDao().updateCategory(
                            messageId,
                            com.rasmi.purevon.data.local.entity.MessageCategory.OTP
                        )
                        DebugLogger.d(TAG, "Marked message $messageId as OTP category")
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not update OTP message category", e)
                    }

                    val shown = com.rasmi.purevon.service.FloatingOtpBubbleService.show(
                        context = context,
                        otpCode = otp,
                        sender = sender
                    )
                    if (shown) {
                        Log.d(TAG, "OTP bubble shown - Quick Reply popup will be suppressed")
                    } else {
                        Log.w(TAG, "OTP bubble failed to show (overlay permission denied?) — falling back to notification")
                    }
                    return shown
                } else {
                    Log.d(TAG, "OTP is shorter than minimum length $minLength")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling OTP auto-copy", e)
        }
        return false // No OTP bubble shown
    }
    
    
    /**
     * Get or create thread ID for phone number
     */
    private fun getOrCreateThreadId(context: Context, address: String): Long {
        return try {
            val uri = Uri.parse("content://mms-sms/threadID")
            val projection = arrayOf("_id")
            
            // First try to get existing thread
            context.contentResolver.query(
                uri.buildUpon().appendQueryParameter("recipient", address).build(),
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIndex = cursor.getColumnIndex("_id")
                    if (idIndex != -1) {
                        return cursor.getLong(idIndex)
                    }
                }
            }
            
            // If no existing thread, create one by querying the canonical address
            Telephony.Threads.getOrCreateThreadId(context, address)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting thread ID", e)
            0L
        }
    }
    
    /**
     * Insert new SMS into system database
     * CRITICAL: Required for default SMS app to save received messages
     */
    private fun insertNewSMS(
        context: Context,
        address: String,
        body: String,
        date: Long,
        read: Int,
        threadId: Long,
        type: Int
    ): Long {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, date)
            put(Telephony.Sms.DATE_SENT, date)
            put(Telephony.Sms.READ, read)
            put(Telephony.Sms.THREAD_ID, threadId)
            put(Telephony.Sms.TYPE, type)
            put(Telephony.Sms.SEEN, if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) 0 else 1)
        }
        
        return try {
            val uri = context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
            val id = uri?.lastPathSegment?.toLongOrNull() ?: 0L
            if (id > 0) {
                Log.d(TAG, "ContentResolver.insert returned ID: $id")
            }
            id
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting SMS into Telephony.Sms.CONTENT_URI", e)
            0L
        }
    }
    
    /**
     * Show enhanced notification using EnhancedNotificationManager
     */
    private suspend fun showEnhancedNotification(
        context: Context,
        phoneNumber: String,
        messageBody: String,
        threadId: Long,
        timestamp: Long
    ) {
        try {
            // Get contact name
            val contactName = getContactName(context, phoneNumber)
            
            // Get contact photo
            val contactPhoto = getContactPhotoUri(context, phoneNumber)?.let { photoUri ->
                getContactPhotoBitmap(context, photoUri)
            }
            
            // Use EnhancedNotificationManager (Hilt @Singleton via EntryPoint)
            val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                SmsReceiverEntryPoint::class.java
            )
            val notificationManager = entryPoint.enhancedNotificationManager()
            // ✅ FIX M12: Use suspend directly without runBlocking
            val hideContent = entryPoint.settingsDataStore().hideSensitiveNotifications.first()
            notificationManager.showSmsNotification(
                phoneNumber = phoneNumber,
                senderName = contactName,
                messageBody = messageBody,
                threadId = threadId,
                timestamp = timestamp,
                contactPhoto = contactPhoto,
                hideContent = hideContent
            )
            
            DebugLogger.d(TAG, "Enhanced notification shown for thread: $threadId")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing enhanced notification", e)
            // Fallback to simple notification
            showNotification(context, phoneNumber, messageBody, threadId)
        }
    }
    
    /**
     * Get contact photo URI
     */
    private fun getContactPhotoUri(context: Context, phoneNumber: String): String? {
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI))
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting contact photo URI", e)
            null
        }
    }
    
    /**
     * Get contact photo bitmap
     */
    private fun getContactPhotoBitmap(context: Context, photoUri: String): android.graphics.Bitmap? {
        return try {
            // ✅ FIXED: Use .use{} to guarantee InputStream is closed even if decodeStream returns null
            context.contentResolver.openInputStream(Uri.parse(photoUri))?.use { inputStream ->
                android.graphics.BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading contact photo", e)
            null
        }
    }
    
    /**
     * Get contact name from phone number
     */
    private fun getContactName(context: Context, phoneNumber: String): String? {
        // ✅ FIX #43: Check READ_CONTACTS permission before querying
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting contact name", e)
            null
        }
    }
    
    /**
     * Show notification for new SMS
     */
    private fun showNotification(
        context: Context,
        phoneNumber: String,
        messageBody: String,
        threadId: Long
    ) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            
            // Custom sound URI
            val soundUri = android.net.Uri.parse("android.resource://" + context.packageName + "/" + com.rasmi.purevon.R.raw.recieve)
            
            // Create notification channel if needed
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                // Changing channel ID to force update sound settings
                val channel = android.app.NotificationChannel(
                    com.rasmi.purevon.notification.EnhancedNotificationManager.CHANNEL_ID_MESSAGES,
                    context.getString(com.rasmi.purevon.R.string.notification_channel_messages),
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(com.rasmi.purevon.R.string.notification_channel_messages_desc)
                    enableLights(true)
                    enableVibration(true)
                    setSound(soundUri, android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                }
                notificationManager.createNotificationChannel(channel)
            }
            
            // Create intent to open conversation
            val intent = Intent(context, com.rasmi.purevon.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("navigate_to", "conversation")
                putExtra("thread_id", threadId)
            }
            
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                threadId.toInt(),
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            
            // Build notification
            val notification = androidx.core.app.NotificationCompat.Builder(context, com.rasmi.purevon.notification.EnhancedNotificationManager.CHANNEL_ID_MESSAGES)
                .setSmallIcon(com.rasmi.purevon.R.drawable.ic_notification)
                .setContentTitle(phoneNumber)
                .setContentText(messageBody)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(messageBody))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(longArrayOf(0, 250, 250, 250))
                .build()
            
            notificationManager.notify(threadId.toInt(), notification)
            DebugLogger.d(TAG, "Notification shown for thread: $threadId")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification", e)
        }
    }
}
