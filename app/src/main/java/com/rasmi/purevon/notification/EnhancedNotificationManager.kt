package com.rasmi.purevon.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import android.util.Log
import com.rasmi.purevon.MainActivity
import com.rasmi.purevon.R
import com.rasmi.purevon.domain.model.Message
import com.rasmi.purevon.receiver.NotificationActionReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhanced Notification Manager
 * نظام إشعارات محسّن مع دعم الرد السريع والصور
 * ✅ Fixed: Added API checks for summary notifications
 * ✅ Fixed: Improved error handling
 * ✅ Fixed: Better notification grouping
 * ✅ Fixed: Now a Hilt @Singleton — channels created once, not on every instantiation
 */
@Singleton
class EnhancedNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val notificationManager = 
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    private val TAG = "EnhancedNotificationMgr"
    
    companion object {
        // ✅ Versioned channel IDs — bump version when notification sound changes
        // Android caches NotificationChannel settings permanently after first creation.
        // The ONLY way to update the sound is to delete the old channel and create
        // a new one with a different ID.
        private const val CHANNEL_VERSION = 3 // ← Bump this when changing notification sound
        const val CHANNEL_ID_MESSAGES = "messages_channel_v$CHANNEL_VERSION"
        const val CHANNEL_ID_IMPORTANT = "important_messages_channel_v$CHANNEL_VERSION"
        private val OLD_CHANNEL_IDS = listOf(
            "messages_channel", "messages_channel_v1", "messages_channel_v2",
            "important_messages_channel", "important_messages_channel_v1", "important_messages_channel_v2"
        )
        // Channel names now loaded from string resources (see createNotificationChannels)
        
        const val KEY_TEXT_REPLY = "key_text_reply"
        const val ACTION_REPLY = "action_reply"
        const val ACTION_MARK_READ = "action_mark_read"
        const val ACTION_ARCHIVE = "action_archive"
        
        const val EXTRA_THREAD_ID = "extra_thread_id"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
        const val EXTRA_SENDER = "extra_sender"
        
        // Group constants
        private const val GROUP_KEY_MESSAGES = "com.rasmi.purevon.MESSAGES"
        private const val SUMMARY_ID = 0
    }
    
    init {
        createNotificationChannels()
    }
    
    /**
     * إنشاء قنوات الإشعارات
     */
    private fun createNotificationChannels() {
        // ✅ Delete old versioned channels so Android picks up the new sound
        OLD_CHANNEL_IDS.forEach { oldId ->
            notificationManager.deleteNotificationChannel(oldId)
        }
        
        // قناة الرسائل العادية
        val customSound = android.net.Uri.parse(
            "android.resource://" + context.packageName + "/" + com.rasmi.purevon.R.raw.recieve
        )
        val messagesChannel = NotificationChannel(
            CHANNEL_ID_MESSAGES,
            context.getString(com.rasmi.purevon.R.string.notification_channel_sms),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(com.rasmi.purevon.R.string.notification_channel_sms_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 250, 250)
            enableLights(true)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(customSound, android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
        }
        
        // قناة الرسائل المهمة
        val importantChannel = NotificationChannel(
            CHANNEL_ID_IMPORTANT,
            context.getString(com.rasmi.purevon.R.string.notification_channel_important),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(com.rasmi.purevon.R.string.notification_channel_important_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 250, 250)
            enableLights(true)
            setShowBadge(true)
        }
        
        notificationManager.createNotificationChannel(messagesChannel)
        notificationManager.createNotificationChannel(importantChannel)
    }
    
    /**
     * Push a dynamic conversation shortcut for the given thread.
     * Required on Android 11+ (API 30+) for the system to display the contact photo
     * as the main notification icon instead of the app's small icon.
     */
    private fun pushConversationShortcut(
        threadId: Long,
        displayName: String,
        person: Person,
        photoIcon: IconCompat?
    ): String {
        val shortcutId = "conversation_$threadId"
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("navigate_to", "conversation")
                putExtra("thread_id", threadId)
            }
            
            val shortcut = ShortcutInfoCompat.Builder(context, shortcutId)
                .setShortLabel(displayName.take(25))
                .setLongLabel(displayName)
                .setLongLived(true)
                .setIcon(photoIcon ?: IconCompat.createWithResource(context, R.drawable.ic_default_avatar))
                .setIntent(intent)
                .setPerson(person)
                .setCategories(setOf("android.shortcut.conversation"))
                .build()
            
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
            Log.d(TAG, "Conversation shortcut pushed for thread $threadId")
        } catch (e: Exception) {
            Log.e(TAG, "Error pushing conversation shortcut for thread $threadId", e)
        }
        return shortcutId
    }
    
    /**
     * عرض إشعار رسالة جديدة
     * ✅ Added error handling
     */
    fun showNewMessageNotification(
        message: Message,
        senderName: String,
        threadId: Long,
        contactPhoto: Bitmap? = null,
        isImportant: Boolean = false,
        isGroup: Boolean = false
    ) {
        try {
            showNewMessageNotificationInternal(message, senderName, threadId, contactPhoto, isImportant, isGroup)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing new message notification", e)
        }
    }
    
    /**
     * Internal method for showing message notification
     */
    private fun showNewMessageNotificationInternal(
        message: Message,
        senderName: String,
        threadId: Long,
        contactPhoto: Bitmap?,
        isImportant: Boolean,
        isGroup: Boolean
    ) {
        val channelId = if (isImportant) CHANNEL_ID_IMPORTANT else CHANNEL_ID_MESSAGES
        
        // Intent للفتح عند الضغط على الإشعار
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "conversation")
            putExtra("thread_id", threadId)
        }
        
        val openPendingIntent = PendingIntent.getActivity(
            context,
            threadId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val finalPhoto = contactPhoto ?: try {
            BitmapFactory.decodeResource(context.resources, R.drawable.ic_default_avatar)
        } catch (e: Exception) {
            null
        }
        
        val photoIcon: IconCompat? = finalPhoto?.let { IconCompat.createWithBitmap(it) }
        
        // إنشاء Person للمُرسل
        val sender = Person.Builder()
            .setName(senderName)
            .setKey("person_$threadId")
            .apply { photoIcon?.let { setIcon(it) } }
            .build()
        
        // Push conversation shortcut for Android 11+ notification avatar
        val shortcutId = if (!isGroup) {
            pushConversationShortcut(threadId, senderName, sender, photoIcon)
        } else null
        
        // إنشاء MessagingStyle
        val messagingStyle = NotificationCompat.MessagingStyle(sender)
        messagingStyle.isGroupConversation = isGroup
        if (isGroup) {
            messagingStyle.setConversationTitle(senderName)
        }
        messagingStyle.addMessage(
            message.body,
            message.timestamp,
            sender
        )
        
        // إنشاء الإشعار (sound is handled by the notification channel)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .apply {
                // On Android 11+ 1-to-1 chats: the conversation shortcut handles the avatar
                if (isGroup || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    setLargeIcon(finalPhoto)
                }
                shortcutId?.let { setShortcutId(it) }
            }
            .setStyle(messagingStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setShowWhen(true) // ✅ Show timestamp
            .setWhen(message.timestamp) // ✅ Set timestamp
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // ✅ Lock screen visibility
            .apply {
                if (isGroup) {
                    setGroup(GROUP_KEY_MESSAGES) // ✅ Group only group messages
                }
            }
            .apply {
                // إضافة إجراءات الرد السريع
                addReplyAction(threadId, message.id)
                addMarkReadAction(threadId, message.id)
                addArchiveAction(threadId)
            }
            .build()
        
        notificationManager.notify(threadId.hashCode(), notification)
        Log.d(TAG, "Message notification shown for thread $threadId")
        
        // ✅ Update or create summary notification
        try {
            if (isGroup) {
                updateSummaryNotification()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating summary notification", e)
        }
    }
    
    /**
     * إضافة إجراء الرد السريع
     */
    private fun NotificationCompat.Builder.addReplyAction(threadId: Long, messageId: Long) {
        val replyLabel = context.getString(com.rasmi.purevon.R.string.notification_action_quick_reply)
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel(replyLabel)
            .build()
        
        val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_REPLY
            putExtra(EXTRA_THREAD_ID, threadId)
            putExtra(EXTRA_MESSAGE_ID, messageId)
        }
        
        // ✅ Use FLAG_MUTABLE only for RemoteInput (required for Android 12+)
        // But ensure proper security by setting explicit component
        replyIntent.setClass(context, NotificationActionReceiver::class.java)
        
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            threadId.hashCode(),
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            replyLabel,
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()
        
        addAction(replyAction)
    }
    
    /**
     * إضافة إجراء تعليم كمقروء
     */
    private fun NotificationCompat.Builder.addMarkReadAction(threadId: Long, messageId: Long) {
        val markReadIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_MARK_READ
            putExtra(EXTRA_THREAD_ID, threadId)
            putExtra(EXTRA_MESSAGE_ID, messageId)
        }
        
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context,
            (threadId + 1000).toInt(),
            markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val markReadAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_view,
            context.getString(com.rasmi.purevon.R.string.notification_action_mark_read),
            markReadPendingIntent
        ).build()
        
        addAction(markReadAction)
    }
    
    /**
     * إضافة إجراء الأرشفة
     */
    private fun NotificationCompat.Builder.addArchiveAction(threadId: Long) {
        val archiveIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_ARCHIVE
            putExtra(EXTRA_THREAD_ID, threadId)
        }
        
        val archivePendingIntent = PendingIntent.getBroadcast(
            context,
            (threadId + 2000).toInt(),
            archiveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val archiveAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_delete,
            context.getString(com.rasmi.purevon.R.string.notification_action_archive),
            archivePendingIntent
        ).build()
        
        addAction(archiveAction)
    }
    
    /**
     * Show simple SMS notification (for SmsReceiver)
     * More lightweight than full Message object notification
     * ✅ Added error handling
     */
    fun showSmsNotification(
        phoneNumber: String,
        senderName: String?,
        messageBody: String,
        threadId: Long,
        timestamp: Long = System.currentTimeMillis(),
        contactPhoto: Bitmap? = null,
        hideContent: Boolean = false,
        vibrationEnabled: Boolean = true,
        isGroup: Boolean = false
    ) {
        try {
            showSmsNotificationInternal(phoneNumber, senderName, messageBody, threadId, timestamp, contactPhoto, hideContent, vibrationEnabled, isGroup)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing SMS notification", e)
        }
    }
    
    /**
     * Internal method for showing SMS notification
     */
    private fun showSmsNotificationInternal(
        phoneNumber: String,
        senderName: String?,
        messageBody: String,
        threadId: Long,
        timestamp: Long,
        contactPhoto: Bitmap?,
        hideContent: Boolean = false,
        vibrationEnabled: Boolean = true,
        isGroup: Boolean = false
    ) {
        val displayName = senderName ?: phoneNumber
        val displayBody = if (hideContent) "•••" else messageBody
        
        // Intent to open conversation
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "conversation")
            putExtra("thread_id", threadId)
        }
        
        val openPendingIntent = PendingIntent.getActivity(
            context,
            threadId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val finalPhoto = contactPhoto ?: try {
            BitmapFactory.decodeResource(context.resources, R.drawable.ic_default_avatar)
        } catch (e: Exception) {
            null
        }
        
        val photoIcon: IconCompat? = finalPhoto?.let { IconCompat.createWithBitmap(it) }
        
        // Create Person for sender
        val sender = Person.Builder()
            .setName(displayName)
            .setKey("person_$threadId")
            .apply { photoIcon?.let { setIcon(it) } }
            .build()
        
        // Push conversation shortcut for Android 11+ notification avatar
        val shortcutId = if (!isGroup) {
            pushConversationShortcut(threadId, displayName, sender, photoIcon)
        } else null
        
        // Create MessagingStyle
        val messagingStyle = NotificationCompat.MessagingStyle(sender)
        messagingStyle.isGroupConversation = isGroup
        if (isGroup) {
            messagingStyle.setConversationTitle(displayName)
        }
        messagingStyle.addMessage(displayBody, timestamp, sender)
        
        // Create Reply action
        val replyLabel = context.getString(R.string.notification_action_reply)
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel(replyLabel)
            .build()
        
        val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_REPLY
            putExtra(EXTRA_THREAD_ID, threadId)
            putExtra(EXTRA_MESSAGE_ID, -1L) // No specific message ID
        }
        
        // ✅ Set explicit component for security
        replyIntent.setClass(context, NotificationActionReceiver::class.java)
        
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            threadId.hashCode(),
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            replyLabel,
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .build()
        
        // Create Mark Read action
        val markReadIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_MARK_READ
            putExtra(EXTRA_THREAD_ID, threadId)
            putExtra(EXTRA_MESSAGE_ID, -1L)
        }
        
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context,
            (threadId + 1000).toInt(),
            markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val markReadAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_view,
            context.getString(R.string.notification_action_mark_read_short),
            markReadPendingIntent
        ).build()
        
        // Build notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .apply {
                // On Android 11+ 1-to-1 chats: the conversation shortcut handles the avatar
                if (isGroup || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    setLargeIcon(finalPhoto)
                }
                shortcutId?.let { setShortcutId(it) }
            }
            .setStyle(messagingStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .setShowWhen(true)
            .setWhen(timestamp)
            .setVisibility(if (hideContent) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PUBLIC)
            .apply {
                if (isGroup) {
                    setGroup(GROUP_KEY_MESSAGES) // ✅ Group only group messages
                }
            }
            .apply { if (!vibrationEnabled) setVibrate(longArrayOf(0)) }
            .addAction(replyAction)
            .addAction(markReadAction)
            .build()
        
        notificationManager.notify(threadId.hashCode(), notification)
        Log.d(TAG, "SMS notification shown for thread $threadId")
        
        // ✅ Update or create summary notification
        try {
            if (isGroup) {
                updateSummaryNotification()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating summary notification after SMS", e)
        }
    }
    
    /**
     * Update summary notification for grouped messages
     * ✅ Fixed: Better error handling
     */
    private fun updateSummaryNotification() {
        try {
            // Get all active notifications
            val activeNotifications = notificationManager.activeNotifications
                .filter { it.id != SUMMARY_ID && it.notification.group == GROUP_KEY_MESSAGES }
            
            if (activeNotifications.isEmpty()) {
                // No messages, cancel summary
                notificationManager.cancel(SUMMARY_ID)
                Log.d(TAG, "No active message notifications, cancelled summary")
                return
            }
            
            if (activeNotifications.size == 1) {
                // Only one message, no need for summary
                notificationManager.cancel(SUMMARY_ID)
                Log.d(TAG, "Only one active notification, cancelled summary")
                return
            }
            
            // Create summary notification
            val summaryText = when {
                activeNotifications.size == 2 -> context.getString(R.string.notification_summary_2_messages)
                activeNotifications.size <= 9 -> context.resources.getQuantityString(R.plurals.notification_summary_n_messages, activeNotifications.size, activeNotifications.size)
                else -> context.getString(R.string.notification_summary_many_messages)
            }
            
            val summaryNotification = NotificationCompat.Builder(context, CHANNEL_ID_MESSAGES)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notification_summary_title))
                .setContentText(summaryText)
                .setStyle(NotificationCompat.InboxStyle()
                    .setBigContentTitle(context.getString(R.string.notification_summary_title))
                    .setSummaryText(summaryText))
                .setGroup(GROUP_KEY_MESSAGES)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .build()
            
            notificationManager.notify(SUMMARY_ID, summaryNotification)
            Log.d(TAG, "Summary notification updated: $summaryText")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating summary notification", e)
        }
    }
    
    /**
     * Get contact photo from URI
     * ✅ FIX 2.12: Use .use{} to ensure InputStream is always closed,
     * even if decodeStream returns null or throws.
     */
    fun getContactPhoto(context: Context, photoUri: String?): Bitmap? {
        if (photoUri.isNullOrBlank()) return null
        
        return try {
            context.contentResolver.openInputStream(Uri.parse(photoUri))?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * إلغاء إشعار محدد
     * ✅ Added summary update after cancellation
     */
    fun cancelNotification(threadId: Long) {
        try {
            notificationManager.cancel(threadId.hashCode())
            Log.d(TAG, "Notification cancelled for thread $threadId")
            
            // Update summary after cancelling a notification
            updateSummaryNotification()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling notification for thread $threadId", e)
        }
    }
    
    /**
     * إلغاء كل الإشعارات
     * ✅ Added error handling
     */
    fun cancelAllNotifications() {
        try {
            notificationManager.cancelAll()
            Log.d(TAG, "All notifications cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling all notifications", e)
        }
    }
}
