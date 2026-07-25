package com.rasmi.purevon.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.rasmi.purevon.MainActivity
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rasmi.purevon.R
import com.rasmi.purevon.data.preferences.SettingsDataStore
import com.rasmi.purevon.domain.repository.MessageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Worker for sending delayed messages
 * Uses Hilt for dependency injection
 * ✅ FIX: Uses MessageRepository directly to support both SMS and MMS
 */
@HiltWorker
class DelayedMessageWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val messageRepository: MessageRepository,
    private val settingsDataStore: SettingsDataStore
) : CoroutineWorker(context, workerParams) {
    
    companion object {
        private const val TAG = "DelayedMessageWorker"
        private const val MAX_RETRIES = 3
        const val KEY_PHONE_NUMBER = "phone_number"
        const val KEY_MESSAGE_TEXT = "message_text"
        const val KEY_ATTACHMENT_URIS = "attachment_uris"
        const val KEY_SIM_SLOT = "sim_slot"
    }
    
    override suspend fun doWork(): Result {
        // Fail permanently after MAX_RETRIES attempts
        if (runAttemptCount >= MAX_RETRIES) {
            Log.e(TAG, "❌ Delayed message failed after $MAX_RETRIES attempts, giving up")
            val phoneNumber = inputData.getString(KEY_PHONE_NUMBER) ?: "unknown"
            val messageText = inputData.getString(KEY_MESSAGE_TEXT) ?: ""
            showFailureNotification(phoneNumber, messageText)
            return Result.failure()
        }
        
        return try {
            val phoneNumber = inputData.getString(KEY_PHONE_NUMBER)
                ?: return Result.failure()
            
            val messageText = inputData.getString(KEY_MESSAGE_TEXT)
                ?: return Result.failure()
            
            val attachmentUris = inputData.getStringArray(KEY_ATTACHMENT_URIS)?.toList()
            
            Log.d(TAG, "⏰ Sending delayed message to ${com.rasmi.purevon.util.DebugLogger.maskPhoneNumber(phoneNumber)}")
            
            // ✅ FIX S5: Use SIM slot saved at scheduling time, fall back to current default
            val simSlot = inputData.getInt(KEY_SIM_SLOT, -1)
                .takeIf { it > 0 && it != Int.MAX_VALUE }
                ?: settingsDataStore.defaultSmsSimSubscriptionId.first()
                    .takeIf { it > 0 && it != Int.MAX_VALUE }
            
            // ✅ FIX: Send as MMS if attachments exist, otherwise as SMS (like ScheduledMessageWorker)
            val result = if (!attachmentUris.isNullOrEmpty()) {
                messageRepository.sendMmsMessage(
                    phoneNumber = phoneNumber,
                    message = messageText,
                    attachmentUris = attachmentUris,
                    simSlot = simSlot
                )
            } else {
                messageRepository.sendMessage(
                    phoneNumber = phoneNumber,
                    message = messageText,
                    simSlot = simSlot
                )
            }
            
            when {
                result.isSuccess -> {
                    Log.d(TAG, "✅ Delayed message sent successfully")
                    Result.success()
                }
                else -> {
                    Log.e(TAG, "❌ Failed to send delayed message")
                    Result.retry()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in DelayedMessageWorker", e)
            Result.retry()
        }
    }
    
    private fun showFailureNotification(phoneNumber: String, messageText: String) {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "scheduled_failures",
                applicationContext.getString(R.string.scheduled_message_failures),
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "new_conversation")
            putExtra("phone_number", phoneNumber)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, "scheduled_failures")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.scheduled_message_failed))
            .setContentText(
                applicationContext.getString(
                    R.string.scheduled_message_failed_body,
                    phoneNumber,
                    messageText.take(50)
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // ✅ FIX M49: Use abs() to ensure positive notification ID
        notificationManager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }
}
