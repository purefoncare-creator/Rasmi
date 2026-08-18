package com.rasmi.purevon.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.rasmi.purevon.MainActivity
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.rasmi.purevon.R
import com.rasmi.purevon.data.local.dao.ScheduledMessageDao
import com.rasmi.purevon.data.local.entity.RepeatInterval
import com.rasmi.purevon.data.local.entity.ScheduleStatus
import com.rasmi.purevon.data.local.entity.ScheduledMessageEntity
import com.rasmi.purevon.domain.repository.MessageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltWorker
class ScheduledMessageWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scheduledMessageDao: ScheduledMessageDao,
    private val messageRepository: MessageRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = sendMutex.withLock {
        try {
            val scheduleId = inputData.getLong(KEY_SCHEDULE_ID, -1L)
            if (scheduleId == -1L) {
                return Result.failure()
            }
            
            // Get scheduled message
            val scheduledMessage = scheduledMessageDao.getMessageById(scheduleId)
                ?: return Result.failure()
            
            // Check if still pending
            if (scheduledMessage.status != ScheduleStatus.PENDING) {
                return Result.success()
            }
            
            // Send the message (MMS if attachments exist, SMS otherwise)
            val result = if (scheduledMessage.attachmentUris.isNotEmpty()) {
                messageRepository.sendMmsMessage(
                    phoneNumber = scheduledMessage.recipient,
                    message = scheduledMessage.messageBody,
                    attachmentUris = scheduledMessage.attachmentUris,
                    simSlot = scheduledMessage.simSlot
                )
            } else {
                messageRepository.sendMessage(
                    phoneNumber = scheduledMessage.recipient,
                    message = scheduledMessage.messageBody,
                    simSlot = scheduledMessage.simSlot
                )
            }
            
            // Update status
            if (result.isSuccess) {
                scheduledMessageDao.updateStatus(scheduleId, ScheduleStatus.SENT)
                
                // ✅ Handle repeat: create next occurrence if configured
                scheduleNextOccurrence(scheduledMessage)
                
                Result.success()
            } else {
                // Retry up to MAX_RETRIES times, then mark as failed
                if (runAttemptCount < MAX_RETRIES) {
                    Result.retry()
                } else {
                    scheduledMessageDao.updateStatus(scheduleId, ScheduleStatus.FAILED)
                    // ✅ Fix #9: Notify user about failed scheduled message
                    showFailureNotification(scheduledMessage)
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending scheduled message", e)
            // Retry up to MAX_RETRIES times, then mark as failed
            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                val scheduleId = inputData.getLong(KEY_SCHEDULE_ID, -1L)
                if (scheduleId != -1L) {
                    try {
                        scheduledMessageDao.updateStatus(scheduleId, ScheduleStatus.FAILED)
                        val msg = scheduledMessageDao.getMessageById(scheduleId)
                        if (msg != null) showFailureNotification(msg)
                    } catch (_: Exception) {}
                }
                Result.failure()
            }
        }
    }

    /**
     * If the message has a repeat interval, insert a new PENDING entity
     * with the next scheduled time and enqueue a WorkManager request for it.
     */
    private suspend fun scheduleNextOccurrence(original: ScheduledMessageEntity) {
        if (original.repeatInterval == RepeatInterval.NONE) return
        
        val nextTime = computeNextTime(original.scheduledTime, original.repeatInterval)
        if (nextTime <= System.currentTimeMillis()) {
            Log.w(TAG, "Computed next occurrence is in the past, skipping repeat")
            return
        }
        
        // ✅ FIX M36: Check for existing PENDING entries to prevent duplicates on Worker retry
        val existingPending = scheduledMessageDao.getPendingForRecipient(
            original.recipient, nextTime
        )
        if (existingPending != null) {
            Log.w(TAG, "⚠️ Duplicate next occurrence already exists (id=${existingPending.id}), skipping")
            return
        }
        
        // Insert new PENDING entity (copy with new id, time, status)
        val nextEntity = original.copy(
            id = 0,
            scheduledTime = nextTime,
            status = ScheduleStatus.PENDING,
            createdAt = System.currentTimeMillis()
        )
        val newId = scheduledMessageDao.insert(nextEntity)
        
        // Enqueue WorkManager request
        val delay = nextTime - System.currentTimeMillis()
        val workRequest = OneTimeWorkRequestBuilder<ScheduledMessageWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_SCHEDULE_ID to newId))
            .addTag("scheduled_message")
            .addTag("message_$newId")
            .build()
        WorkManager.getInstance(applicationContext).enqueue(workRequest)
        
        Log.d(TAG, "✅ Scheduled next ${original.repeatInterval} occurrence (id=$newId) at $nextTime")
    }
    
    /**
     * Compute the next occurrence timestamp using Calendar (handles month lengths & DST).
     */
    private fun computeNextTime(currentTime: Long, interval: RepeatInterval): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = currentTime }
        when (interval) {
            RepeatInterval.DAILY   -> cal.add(Calendar.DAY_OF_MONTH, 1)
            RepeatInterval.WEEKLY  -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            RepeatInterval.MONTHLY -> cal.add(Calendar.MONTH, 1)
            RepeatInterval.NONE    -> { /* no-op */ }
        }
        return cal.timeInMillis
    }

    private fun showFailureNotification(message: ScheduledMessageEntity) {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_SCHEDULED_FAILURES,
            applicationContext.getString(R.string.scheduled_message_failures),
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "new_conversation")
            putExtra("phone_number", message.recipient)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            message.id.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_SCHEDULED_FAILURES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.scheduled_message_failed))
            .setContentText(
                applicationContext.getString(
                    R.string.scheduled_message_failed_body,
                    message.recipient,
                    message.messageBody.take(50)
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(message.id.toInt(), notification)
    }

    companion object {
        // WorkManager may run multiple scheduled workers in the same process.
        // Serialize the read -> send -> status transition to avoid duplicate sends.
        private val sendMutex = Mutex()
        private const val TAG = "ScheduledMessageWorker"
        const val KEY_SCHEDULE_ID = "schedule_id"
        const val WORK_NAME_PREFIX = "scheduled_message_"
        private const val MAX_RETRIES = 3
        private const val CHANNEL_SCHEDULED_FAILURES = "scheduled_failures"
    }
}
