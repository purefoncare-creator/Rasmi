package com.rasmi.purevon.util.message

import android.content.Context
import android.util.Log
import androidx.work.*
import com.rasmi.purevon.worker.DelayedMessageWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for delayed/scheduled messages
 * Improved version with better reliability
 * 
 * Features:
 * - Delay messages by minutes/hours
 * - Cancel delayed messages
 * - Retry on failure
 * - Network-aware sending
 */
@Singleton
class DelayedSendManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager
) {
    
    companion object {
        private const val TAG = "DelayedSendManager"
        private const val WORK_TAG_PREFIX = "delayed_message_"
    }
    
    /**
     * Schedule a delayed message send
     * 
     * @param phoneNumber Recipient
     * @param messageText Message content
     * @param delayMillis Delay in milliseconds
     * @param requiresNetwork Whether to require network connection
     * @return Work request ID for tracking/cancellation
     */
    fun scheduleDelayedSend(
        phoneNumber: String,
        messageText: String,
        delayMillis: Long,
        attachmentUris: List<String>? = null,
        requiresNetwork: Boolean = false,
        simSlot: Int? = null
    ): String {
        
        val inputData = workDataOf(
            DelayedMessageWorker.KEY_PHONE_NUMBER to phoneNumber,
            DelayedMessageWorker.KEY_MESSAGE_TEXT to messageText,
            DelayedMessageWorker.KEY_ATTACHMENT_URIS to attachmentUris?.toTypedArray(),
            DelayedMessageWorker.KEY_SIM_SLOT to (simSlot ?: -1)
        )
        
        val constraints = Constraints.Builder()
            .apply {
                if (requiresNetwork) {
                    setRequiredNetworkType(NetworkType.CONNECTED)
                }
            }
            .build()
        
        val workRequest = OneTimeWorkRequestBuilder<DelayedMessageWorker>()
            .setInputData(inputData)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .addTag("$WORK_TAG_PREFIX$phoneNumber")
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10000L,
                TimeUnit.MILLISECONDS
            )
            .build()
        
        workManager.enqueue(workRequest)
        
        val workId = workRequest.id.toString()
        Log.d(TAG, "✅ Scheduled delayed message to $phoneNumber (delay: ${delayMillis}ms, workId: $workId)")
        
        return workId
    }
    
    /**
     * Schedule message to send at specific time
     */
    fun scheduleAt(
        phoneNumber: String,
        messageText: String,
        scheduledTime: Long,
        attachmentUris: List<String>? = null,
        simSlot: Int? = null
    ): String {
        val currentTime = System.currentTimeMillis()
        val delayMillis = (scheduledTime - currentTime).coerceAtLeast(0)
        
        return scheduleDelayedSend(
            phoneNumber = phoneNumber,
            messageText = messageText,
            delayMillis = delayMillis,
            attachmentUris = attachmentUris,
            simSlot = simSlot
        )
    }
    
    /**
     * Cancel a delayed message
     */
    fun cancelDelayedMessage(workId: String) {
        val uuid = try {
            java.util.UUID.fromString(workId)
        } catch (e: Exception) {
            Log.e(TAG, "Invalid work ID: $workId", e)
            return
        }
        
        workManager.cancelWorkById(uuid)
        Log.d(TAG, "❌ Cancelled delayed message: $workId")
    }
    
    /**
     * Cancel all delayed messages to a specific recipient
     */
    fun cancelAllToRecipient(phoneNumber: String) {
        workManager.cancelAllWorkByTag("$WORK_TAG_PREFIX$phoneNumber")
        Log.d(TAG, "❌ Cancelled all delayed messages to: $phoneNumber")
    }
    
    /**
     * Get pending delayed messages count
     */
    suspend fun getPendingDelayedMessagesCount(): Int {
        return try {
            workManager
                .getWorkInfosByTag(WORK_TAG_PREFIX)
                .get()
                .count { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Quick delay presets
     */
    object DelayPresets {
        const val ONE_MINUTE = 60_000L
        const val FIVE_MINUTES = 5 * ONE_MINUTE
        const val TEN_MINUTES = 10 * ONE_MINUTE
        const val THIRTY_MINUTES = 30 * ONE_MINUTE
        const val ONE_HOUR = 60 * ONE_MINUTE
        const val TWO_HOURS = 2 * ONE_HOUR
        
        fun getPresetLabel(delayMillis: Long): String {
            return when (delayMillis) {
                ONE_MINUTE -> "بعد دقيقة"
                FIVE_MINUTES -> "بعد 5 دقائق"
                TEN_MINUTES -> "بعد 10 دقائق"
                THIRTY_MINUTES -> "بعد 30 دقيقة"
                ONE_HOUR -> "بعد ساعة"
                TWO_HOURS -> "بعد ساعتين"
                else -> "مخصص"
            }
        }
    }
    
    data class DelayedMessageInfo(
        val workId: String,
        val phoneNumber: String,
        val state: String,
        val scheduledTime: Long
    )
}
