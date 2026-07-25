package com.rasmi.purevon.presentation.screen.conversation

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import com.rasmi.purevon.data.local.entity.MessageCategory
import com.rasmi.purevon.domain.model.RepeatInterval
import com.rasmi.purevon.data.preferences.SettingsDataStore
import com.rasmi.purevon.domain.model.Message
import com.rasmi.purevon.domain.model.MessageStatus
import com.rasmi.purevon.domain.usecase.message.SaveScheduledMessageUseCase
import com.rasmi.purevon.util.message.MessageScheduler
import com.rasmi.purevon.worker.ScheduledMessageWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Handles scheduling messages for later delivery.
 * Extracted from ConversationViewModel to reduce class size.
 */
internal class MessageSchedulingDelegate(
    private val context: Context,
    private val _uiState: MutableStateFlow<ConversationUiState>,
    private val viewModelScope: CoroutineScope,
    private val saveScheduledMessageUseCase: SaveScheduledMessageUseCase,
    private val settingsDataStore: SettingsDataStore
) {
    fun scheduleMessage(scheduledTimeMillis: Long, repeatInterval: RepeatInterval = RepeatInterval.NONE) {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                if (currentState.messageText.isBlank() && currentState.attachments.isEmpty()) {
                    _uiState.update { it.copy(showScheduleDialog = false) }
                    return@launch
                }

                // ✅ Fix #4: Validate time is in the future BEFORE saving to DB
                val currentTime = System.currentTimeMillis()
                val delay = scheduledTimeMillis - currentTime
                if (delay <= 0) {
                    _uiState.update { it.copy(error = context.getString(com.rasmi.purevon.R.string.msg_schedule_past_error), showScheduleDialog = false) }
                    return@launch
                }

                // Get default SMS SIM from settings
                val defaultSimSlot = settingsDataStore.defaultSmsSimSubscriptionId.first().takeIf { it > 0 }

                // Save to database
                val scheduleId = saveScheduledMessageUseCase(
                    phoneNumber = currentState.phoneNumber ?: "",
                    message = currentState.messageText,
                    scheduledTimeMillis = scheduledTimeMillis,
                    attachmentUris = currentState.attachments.map { it.uri },
                    simSlot = defaultSimSlot,
                    repeatInterval = repeatInterval
                )

                // ✅ Fix #13: Use type=6 (TYPE_QUEUED) instead of 2 (TYPE_SENT) for pending messages
                // ✅ Fix #12: Use scheduledTimeMillis as timestamp so message sorts by scheduled time
                val scheduledMessage = Message(
                    id = -scheduleId,
                    threadId = currentState.threadId ?: 0,
                    phoneNumber = currentState.phoneNumber ?: "",
                    contactName = currentState.contactName,
                    body = currentState.messageText,
                    timestamp = scheduledTimeMillis, // ✅ Sort by scheduled delivery time
                    type = com.rasmi.purevon.data.local.entity.MessageType.QUEUED.value, // ✅ FIX #8
                    category = MessageCategory.PERSONAL,
                    isRead = true,
                    isSent = false,
                    isDelivered = false,
                    simSlot = defaultSimSlot,
                    isSpam = false,
                    spamScore = 0f,
                    isMms = currentState.attachments.isNotEmpty(),
                    attachmentUris = currentState.attachments.map { it.uri },
                    attachmentTypes = currentState.attachments.mapNotNull { it.mimeType },
                    status = MessageStatus.SENDING,
                    isScheduled = true,
                    scheduledTime = scheduledTimeMillis,
                    scheduleId = scheduleId
                )

                // ✅ Fix #6: Format success message for snackbar
                val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                val dateFmt = SimpleDateFormat("MMM dd", Locale.getDefault())
                val timeStr = timeFmt.format(Date(scheduledTimeMillis))
                val dateStr = dateFmt.format(Date(scheduledTimeMillis))
                val successMsg = context.getString(com.rasmi.purevon.R.string.msg_schedule_success, dateStr, timeStr)

                // Add to messages list + show success snackbar
                _uiState.update {
                    it.copy(
                        messages = it.messages + scheduledMessage,
                        messageText = "",
                        attachments = emptyList(),
                        showScheduleDialog = false,
                        error = null,
                        scheduledSuccess = successMsg // ✅ Fix #6: Success feedback
                    )
                }

                // ✅ Fix #2: Add tags matching MessageScheduler for consistent cancellation
                // ✅ Fix #8: Add network constraint + exponential backoff
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val workRequest = OneTimeWorkRequestBuilder<ScheduledMessageWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setInputData(
                        workDataOf(ScheduledMessageWorker.KEY_SCHEDULE_ID to scheduleId)
                    )
                    .addTag(MessageScheduler.WORK_TAG_SCHEDULED_MESSAGE) // ✅ Fix #2
                    .addTag("message_$scheduleId") // ✅ Fix #2
                    .setConstraints(constraints) // ✅ Fix #8
                    .setBackoffCriteria( // ✅ Fix #8
                        BackoffPolicy.EXPONENTIAL,
                        WorkRequest.MIN_BACKOFF_MILLIS,
                        TimeUnit.MILLISECONDS
                    )
                    .build()

                WorkManager.getInstance(context).enqueue(workRequest)

                Log.d("ConversationViewModel", "Scheduled message ID: $scheduleId at $scheduledTimeMillis")
            } catch (e: Exception) {
                Log.e("ConversationViewModel", "Error scheduling message", e)
                _uiState.update { it.copy(error = e.message, showScheduleDialog = false) }
            }
        }
    }
}
