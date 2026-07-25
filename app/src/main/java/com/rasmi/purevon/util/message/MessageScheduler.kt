package com.rasmi.purevon.util.message

import android.content.Context
import android.util.Log
import androidx.work.*
import com.rasmi.purevon.worker.ScheduledMessageWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages scheduled messages
 * ✅ Fixed: Now uses the unified ScheduledMessageWorker (DB-driven, with retry limits)
 *    instead of a duplicate inline worker that bypassed the repository.
 */
@Singleton
class MessageScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager
) {
    
    companion object {
        private const val TAG = "MessageScheduler"
        const val WORK_TAG_SCHEDULED_MESSAGE = "scheduled_message"
        // Keep legacy keys for reference but use ScheduledMessageWorker.KEY_SCHEDULE_ID
        const val KEY_MESSAGE_ID = "message_id"
        const val KEY_RECIPIENT = "recipient"
        const val KEY_MESSAGE_BODY = "message_body"
        const val KEY_CONVERSATION_ID = "conversation_id"
    }
    
    /**
     * Schedule a message to be sent at specific time.
     * Uses ScheduledMessageWorker which looks up the message from DB by scheduleId,
     * sends via MessageRepository, and has a MAX_RETRIES limit.
     */
    fun scheduleMessage(
        messageId: Long,
        recipient: String,
        messageBody: String,
        conversationId: Long,
        scheduledTime: Long
    ): Result<Unit> {
        return try {
            val currentTime = System.currentTimeMillis()
            val delay = scheduledTime - currentTime
            
            if (delay < 0) {
                return Result.failure(IllegalArgumentException("Scheduled time must be in the future"))
            }
            
            // Use the unified ScheduledMessageWorker (DB-driven approach)
            val inputData = workDataOf(
                ScheduledMessageWorker.KEY_SCHEDULE_ID to messageId
            )
            
            val workRequest = OneTimeWorkRequestBuilder<ScheduledMessageWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .addTag(WORK_TAG_SCHEDULED_MESSAGE)
                .addTag("message_$messageId")
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
            
            workManager.enqueue(workRequest)
            
            Log.d(TAG, "Scheduled message $messageId for ${delay}ms from now")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling message", e)
            Result.failure(e)
        }
    }
    
    /**
     * Cancel scheduled message
     */
    fun cancelScheduledMessage(messageId: Long): Result<Unit> {
        return try {
            workManager.cancelAllWorkByTag("message_$messageId")
            Log.d(TAG, "Cancelled scheduled message $messageId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling scheduled message", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get all pending scheduled messages
     */
    suspend fun getPendingScheduledMessages(): List<WorkInfo> {
        return try {
            workManager.getWorkInfosByTag(WORK_TAG_SCHEDULED_MESSAGE).get()
                .filter { it.state == WorkInfo.State.ENQUEUED }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting pending scheduled messages", e)
            emptyList()
        }
    }
    
    /**
     * Cancel all scheduled messages
     */
    fun cancelAllScheduledMessages(): Result<Unit> {
        return try {
            workManager.cancelAllWorkByTag(WORK_TAG_SCHEDULED_MESSAGE)
            Log.d(TAG, "Cancelled all scheduled messages")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling all scheduled messages", e)
            Result.failure(e)
        }
    }
}
