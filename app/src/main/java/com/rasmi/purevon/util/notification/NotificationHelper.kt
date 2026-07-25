package com.rasmi.purevon.util.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.rasmi.purevon.MainActivity
import com.rasmi.purevon.R
import com.rasmi.purevon.data.preferences.SettingsDataStore
import com.rasmi.purevon.util.sim.SimCallRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for managing all app notifications
 * ✅ Fixed: Removed runBlocking to prevent ANR
 * ✅ Fixed: Removed Full Screen Intent from missed calls
 * ✅ Fixed: Added comprehensive error handling
 */
@Singleton
class NotificationHelper @Inject constructor(
    private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val simCallRouter: SimCallRouter
) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val TAG = "NotificationHelper"
    
    companion object {
        // Notification Channel IDs — versioned so updates to sound/importance are picked up
        // (Android only applies channel settings on first creation, so bump the suffix when changing).
        const val CHANNEL_CALLS = "calls_channel_v2"
        const val CHANNEL_MESSAGES = com.rasmi.purevon.notification.EnhancedNotificationManager.CHANNEL_ID_MESSAGES
        const val CHANNEL_SPAM = "spam_channel_v2"
        const val CHANNEL_GENERAL = "general_channel_v2"

        // Old channel IDs to clean up on init so they don't pile up in system settings.
        private val OLD_CHANNEL_IDS = listOf(
            "calls_channel",
            "spam_channel",
            "general_channel"
        )
        
        // Notification IDs
        const val NOTIFICATION_MISSED_CALL = 1001
        const val NOTIFICATION_NEW_MESSAGE = 1002
        const val NOTIFICATION_SPAM_CALL = 1003
        const val NOTIFICATION_NEW_MMS = 1004
        
        private const val GROUP_MESSAGES = "group_messages"
        private const val GROUP_CALLS = "group_calls"
    }
    
    private val notificationManager = NotificationManagerCompat.from(context)
    
    init {
        createNotificationChannels()
    }
    
    /**
     * Create all notification channels
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Clean up legacy channels (older sound settings can't be changed in place).
            OLD_CHANNEL_IDS.forEach { runCatching { manager.deleteNotificationChannel(it) } }

            // Unified custom notification sound for all app channels (recieve.mp3).
            val customSoundUri = Uri.parse(
                "android.resource://" + context.packageName + "/" + com.rasmi.purevon.R.raw.recieve
            )
            val soundAttrs = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            // CHANNEL_MESSAGES is created by EnhancedNotificationManager with custom sound —
            // do NOT recreate it here (Android only applies settings on first creation).
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_CALLS,
                    context.getString(com.rasmi.purevon.R.string.notification_channel_calls),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(com.rasmi.purevon.R.string.notification_channel_calls_desc)
                    enableLights(true)
                    lightColor = Color.BLUE
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 300, 200, 300)
                    setShowBadge(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setSound(customSoundUri, soundAttrs)
                },
                
                NotificationChannel(
                    CHANNEL_SPAM,
                    context.getString(com.rasmi.purevon.R.string.notification_channel_spam),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(com.rasmi.purevon.R.string.notification_channel_spam_desc)
                    enableLights(true)
                    lightColor = Color.RED
                    setShowBadge(false)
                    setSound(customSoundUri, soundAttrs)
                },
                
                NotificationChannel(
                    CHANNEL_GENERAL,
                    context.getString(com.rasmi.purevon.R.string.notification_channel_general),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(com.rasmi.purevon.R.string.notification_channel_general_desc)
                    setShowBadge(false)
                    setSound(customSoundUri, soundAttrs)
                }
            )
            
            channels.forEach { manager.createNotificationChannel(it) }
        }
    }
    
    /**
     * Show notification for missed call
     * ✅ Fixed: Async check of settings to prevent ANR
     * ✅ Fixed: Removed Full Screen Intent (only for incoming calls)
     */
    fun showMissedCallNotification(phoneNumber: String, contactName: String? = null) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "Notification permission not granted")
            return
        }
        
        scope.launch {
            try {
                
                showMissedCallNotificationInternal(phoneNumber, contactName)
            } catch (e: Exception) {
                Log.e(TAG, "Error showing missed call notification", e)
            }
        }
    }
    
    /**
     * Resolve a contact display name from a phone number using ContactsContract.
     * Returns null if the number is not in contacts.
     */
    private fun resolveContactName(phoneNumber: String): String? {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up contact for $phoneNumber", e)
            null
        }
    }

    private fun getContactPhotoUri(phoneNumber: String): String? {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
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
    
    private fun getContactPhotoBitmap(photoUri: String): Bitmap? {
        return try {
            context.contentResolver.openInputStream(Uri.parse(photoUri))?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading contact photo", e)
            null
        }
    }

    private fun pushConversationShortcut(
        phoneNumber: String,
        displayName: String,
        person: Person,
        photoIcon: IconCompat?
    ): String {
        val shortcutId = "missed_call_${phoneNumber.hashCode()}"
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("navigate_to", "history")
            }
            
            val shortcut = ShortcutInfoCompat.Builder(context, shortcutId)
                .setShortLabel(displayName.take(25))
                .setLongLabel(displayName)
                .setLongLived(true)
                .setIcon(photoIcon ?: IconCompat.createWithResource(context, com.rasmi.purevon.R.drawable.ic_default_avatar))
                .setIntent(intent)
                .setPerson(person)
                .setCategories(setOf("android.shortcut.conversation"))
                .build()
            
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
            Log.d(TAG, "Missed call conversation shortcut pushed for $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Error pushing missed call shortcut for $phoneNumber", e)
        }
        return shortcutId
    }

    /**
     * Internal method to show missed call notification
     */
    private suspend fun showMissedCallNotificationInternal(phoneNumber: String, contactName: String?) {
        val vibrationEnabled = settingsDataStore.vibrationEnabled.first()

        // Resolve contact name if not provided
        val resolvedName = contactName ?: resolveContactName(phoneNumber)

        val displayName = resolvedName ?: phoneNumber
        val displayTitle = if (resolvedName != null) {
            context.getString(com.rasmi.purevon.R.string.notification_missed_call_title_named, resolvedName)
        } else {
            context.getString(com.rasmi.purevon.R.string.notification_missed_call_title)
        }
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "history")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_MISSED_CALL,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Unique notification ID per caller so multiple missed calls are all visible
        val notifId = NOTIFICATION_MISSED_CALL + (phoneNumber.hashCode() and 0x0FFF)

        // Resolve SIM subscription ID for proper routing
        val subscriptionId = simCallRouter.resolveSubscriptionId()

        // Build callback intent — use ACTION_CALL with SIM info if possible, otherwise ACTION_DIAL
        val callBackIntent = if (subscriptionId != null && context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            Intent(Intent.ACTION_CALL).apply {
                data = android.net.Uri.fromParts("tel", phoneNumber, null)
                // Attach SIM info via TelecomManager phone account
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val phoneAccountHandle = com.rasmi.purevon.util.PhoneUtil.getPhoneAccountForSubscription(context, subscriptionId)
                    if (phoneAccountHandle != null) {
                        putExtra(android.telecom.TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
                    }
                }
            }
        } else {
            Intent(Intent.ACTION_DIAL).apply {
                data = android.net.Uri.fromParts("tel", phoneNumber, null)
            }
        }
        val callBackPendingIntent = PendingIntent.getActivity(
            context,
            notifId + 100,
            callBackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val contactPhotoUri = getContactPhotoUri(phoneNumber)
        val contactPhoto = contactPhotoUri?.let { getContactPhotoBitmap(it) }

        val photoIcon = contactPhoto?.let { IconCompat.createWithBitmap(it) }
        
        val sender = Person.Builder()
            .setName(displayName)
            .setImportant(true)
            .setKey("person_${phoneNumber.hashCode()}")
            .apply { photoIcon?.let { setIcon(it) } }
            .build()

        // Build MessagingStyle so Android 11+ treats this as a conversation notification
        // and shows the contact photo as the main avatar icon
        val missedCallText = if (resolvedName != null) {
            context.getString(com.rasmi.purevon.R.string.notification_missed_call_title_named, resolvedName)
        } else {
            context.getString(com.rasmi.purevon.R.string.notification_missed_call_title)
        }
        val messagingStyle = NotificationCompat.MessagingStyle(sender)
            .setConversationTitle(null)
        messagingStyle.isGroupConversation = false
        messagingStyle.addMessage(
            missedCallText,
            System.currentTimeMillis(),
            sender
        )

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(com.rasmi.purevon.R.drawable.ic_notification)
            .setStyle(messagingStyle)
            .setSubText(context.getString(com.rasmi.purevon.R.string.notification_missed_call_subtext))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setGroup(GROUP_CALLS)
            .setSound(
                Uri.parse("android.resource://" + context.packageName + "/" + com.rasmi.purevon.R.raw.recieve)
            )
            .setLights(Color.BLUE, 1000, 3000)
            .addAction(
                android.R.drawable.ic_menu_call,
                context.getString(com.rasmi.purevon.R.string.notification_missed_call_action_callback),
                callBackPendingIntent
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val shortcutId = pushConversationShortcut(
                phoneNumber,
                displayName,
                sender,
                photoIcon
            )
            notificationBuilder.setShortcutId(shortcutId)
        } else {
            if (contactPhoto != null) {
                notificationBuilder.setLargeIcon(contactPhoto)
            }
        }
        
        // Add vibration only if enabled
        if (vibrationEnabled) {
            notificationBuilder.setVibrate(longArrayOf(0, 300, 200, 300, 200, 300))
        }
        
        try {
            val notification = notificationBuilder.build()
            notificationManager.notify(notifId, notification)
            Log.d(TAG, "Missed call notification shown for $phoneNumber (id=$notifId)")
        } catch (e: Exception) {
            Log.e(TAG, "Error notifying missed call", e)
        }
    }
    
    /**
     * Show notification for spam call
     * ✅ Added error handling
     */
    fun showSpamCallNotification(phoneNumber: String, spamScore: Float) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "Notification permission not granted")
            return
        }
        
        try {
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "history")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_SPAM_CALL,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val riskLevel = when {
            spamScore >= 0.8f -> context.getString(R.string.risk_high)
            spamScore >= 0.5f -> context.getString(R.string.risk_medium)
            else -> context.getString(R.string.risk_low)
        }
        
            val notification = NotificationCompat.Builder(context, CHANNEL_SPAM)
                .setSmallIcon(com.rasmi.purevon.R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notification_spam_call_title))
                .setContentText(context.getString(R.string.notification_spam_call_text, phoneNumber, riskLevel))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setColor(Color.RED)
                .build()
            
            notificationManager.notify(NOTIFICATION_SPAM_CALL, notification)
            Log.d(TAG, "Spam call notification shown for $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing spam notification", e)
        }
    }
    
    /**
     * Clear all notifications
     */
    fun clearAllNotifications() {
        try {
            notificationManager.cancelAll()
            Log.d(TAG, "All notifications cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing notifications", e)
        }
    }
    
    /**
     * Cancel specific notification by ID
     */
    fun cancelNotification(notificationId: Int) {
        try {
            notificationManager.cancel(notificationId)
            Log.d(TAG, "Notification $notificationId cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling notification $notificationId", e)
        }
    }
    
    /**
     * Check if notification permission is granted
     */
    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
