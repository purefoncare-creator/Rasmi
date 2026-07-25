package com.rasmi.purevon.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.rasmi.purevon.data.preferences.SettingsDataStore
import com.rasmi.purevon.domain.repository.MessageRepository
import com.rasmi.purevon.notification.EnhancedNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * Notification Action Receiver
 * استقبال إجراءات الإشعارات (رد سريع، تعليم كمقروء، أرشفة)
 * ✅ Fixed: Using goAsync() to prevent resource leak
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    
    @Inject
    lateinit var messageRepository: MessageRepository
    
    @Inject
    lateinit var enhancedNotificationManager: EnhancedNotificationManager
    
    @Inject
    lateinit var settingsDataStore: SettingsDataStore
    
    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.e("NotificationAction", "=== RECEIVED ACTION ===")
        val action = intent.action ?: return
        android.util.Log.e("NotificationAction", "Action: $action")
        val threadId = intent.getLongExtra(EnhancedNotificationManager.EXTRA_THREAD_ID, -1L)
        val messageId = intent.getLongExtra(EnhancedNotificationManager.EXTRA_MESSAGE_ID, -1L)
        android.util.Log.e("NotificationAction", "ThreadId: $threadId, MessageId: $messageId")
        
        if (threadId == -1L) {
            android.util.Log.e("NotificationAction", "ERROR: Invalid threadId")
            return
        }
        
        // ✅ Use goAsync() + withTimeout to handle long-running operations properly
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        
        scope.launch {
            try {
                withTimeout(25_000L) {
                    when (action) {
                        EnhancedNotificationManager.ACTION_REPLY -> {
                            android.util.Log.e("NotificationAction", "Handling REPLY action")
                            handleReplyAction(context, intent, threadId)
                        }
                        EnhancedNotificationManager.ACTION_MARK_READ -> {
                            android.util.Log.e("NotificationAction", "Handling MARK_READ action")
                            handleMarkReadAction(context, threadId, messageId)
                        }
                        EnhancedNotificationManager.ACTION_ARCHIVE -> {
                            android.util.Log.e("NotificationAction", "Handling ARCHIVE action")
                            handleArchiveAction(context, threadId)
                        }
                        else -> {
                            android.util.Log.e("NotificationAction", "Unknown action: $action")
                        }
                    }
                } // withTimeout
            } catch (e: Exception) {
                android.util.Log.e("NotificationAction", "Error handling action", e)
            } finally {
                // ✅ Cancel scope to clean up any remaining coroutines
                scope.cancel()
                // ✅ Always finish the pending result
                pendingResult.finish()
            }
        }
    }
    
    /**
     * معالجة الرد السريع
     * ✅ Made suspend for proper async handling
     */
    private suspend fun handleReplyAction(context: Context, intent: Intent, threadId: Long) {
        android.util.Log.e("NotificationAction", "Handling reply for thread $threadId")
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence(
            EnhancedNotificationManager.KEY_TEXT_REPLY
        )?.toString()
        
        android.util.Log.e("NotificationAction", "Reply text: [${replyText?.length ?: 0} chars]")
        if (replyText.isNullOrBlank()) {
            android.util.Log.e("NotificationAction", "Reply text is empty, aborting")
            return
        }
        
        try {
            // الحصول على عنوان المستقبل من threadId
            val address = messageRepository.getAddressFromThreadId(threadId)
            android.util.Log.e("NotificationAction", "Recipient address: ${com.rasmi.purevon.util.DebugLogger.maskPhoneNumber(address ?: "")}")
            if (address != null) {
                // إرسال الرسالة مع تفضيل شريحة SMS
                android.util.Log.e("NotificationAction", "Sending message...")
                val simSlot = settingsDataStore.defaultSmsSimSubscriptionId.first()
                    .takeIf { it > 0 && it != Int.MAX_VALUE }
                messageRepository.sendMessage(address, replyText, simSlot = simSlot)
                
                // إلغاء الإشعار
                enhancedNotificationManager.cancelNotification(threadId)
                android.util.Log.e("NotificationAction", "Reply sent successfully")
            } else {
                android.util.Log.e("NotificationAction", "Could not find recipient address")
            }
        } catch (e: Exception) {
            android.util.Log.e("NotificationAction", "Error sending reply", e)
        }
    }
    
    /**
     * معالجة تعليم كمقروء
     * ✅ Made suspend for proper async handling
     */
    private suspend fun handleMarkReadAction(context: Context, threadId: Long, messageId: Long) {
        android.util.Log.e("NotificationAction", "Mark as read: threadId=$threadId, messageId=$messageId")
        try {
            if (messageId != -1L) {
                android.util.Log.e("NotificationAction", "Marking message $messageId as read")
                messageRepository.markMessageAsRead(messageId)
            } else {
                android.util.Log.e("NotificationAction", "Marking thread $threadId as read")
                messageRepository.markThreadAsRead(threadId)
            }
            
            android.util.Log.e("NotificationAction", "Cancelling notification for thread $threadId")
            // إلغاء الإشعار
            enhancedNotificationManager.cancelNotification(threadId)
            android.util.Log.e("NotificationAction", "Mark as read completed successfully")
        } catch (e: Exception) {
            android.util.Log.e("NotificationAction", "Error marking as read", e)
        }
    }
    
    /**
     * معالجة الأرشفة
     * ✅ Made suspend for proper async handling
     */
    private suspend fun handleArchiveAction(context: Context, threadId: Long) {
        try {
            messageRepository.archiveConversation(threadId)
            
            // إلغاء الإشعار
            enhancedNotificationManager.cancelNotification(threadId)
        } catch (e: Exception) {
            android.util.Log.e("NotificationAction", "Error archiving conversation", e)
        }
    }
}
