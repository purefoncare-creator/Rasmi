package com.rasmi.purevon.presentation.screen.scheduled

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rasmi.purevon.data.local.dao.ScheduledMessageDao
import com.rasmi.purevon.domain.model.RepeatInterval
import com.rasmi.purevon.data.mapper.toEntity
import com.rasmi.purevon.data.local.entity.ScheduleStatus
import com.rasmi.purevon.data.local.entity.ScheduledMessageEntity
import com.rasmi.purevon.presentation.screen.conversation.EditScheduledMessageData
import com.rasmi.purevon.util.message.MessageScheduler
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "ScheduledMsgVM"

/**
 * ViewModel for Scheduled Messages Screen
 */
@HiltViewModel
class ScheduledMessagesViewModel @Inject constructor(
    private val scheduledMessageDao: ScheduledMessageDao,
    private val messageScheduler: MessageScheduler
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ScheduledMessagesUiState())
    val uiState: StateFlow<ScheduledMessagesUiState> = _uiState.asStateFlow()
    
    init {
        loadScheduledMessages()
    }
    
    private fun loadScheduledMessages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            scheduledMessageDao.getAllScheduledMessages().collect { messages ->
                _uiState.update {
                    it.copy(
                        scheduledMessages = messages.map { entity ->
                            ScheduledMessageItem(
                                id = entity.id,
                                recipient = entity.recipient,
                                messageBody = entity.messageBody,
                                scheduledTime = entity.scheduledTime,
                                status = entity.status
                            )
                        },
                        isLoading = false
                    )
                }
            }
        }
    }
    
    fun onEvent(event: ScheduledMessagesUiEvent) {
        when (event) {
            is ScheduledMessagesUiEvent.CancelMessage -> cancelMessage(event.messageId)
            is ScheduledMessagesUiEvent.EditMessage -> {
                val item = _uiState.value.scheduledMessages.find { it.id == event.messageId }
                if (item != null) {
                    _uiState.update {
                        it.copy(
                            editingMessage = EditScheduledMessageData(
                                scheduleId = item.id,
                                body = item.messageBody,
                                scheduledTime = item.scheduledTime,
                                repeatInterval = RepeatInterval.NONE
                            )
                        )
                    }
                }
            }
            is ScheduledMessagesUiEvent.ConfirmEdit -> {
                confirmEditMessage(event.scheduleId, event.newBody, event.newTime, event.newRepeat)
            }
            ScheduledMessagesUiEvent.DismissEdit -> {
                _uiState.update { it.copy(editingMessage = null) }
            }
        }
    }
    
    private fun cancelMessage(messageId: Long) {
        viewModelScope.launch {
            try {
                messageScheduler.cancelScheduledMessage(messageId)
                scheduledMessageDao.updateStatus(messageId, ScheduleStatus.CANCELLED)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel message $messageId", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    private fun confirmEditMessage(
        scheduleId: Long,
        newBody: String,
        newTime: Long,
        newRepeat: RepeatInterval
    ) {
        viewModelScope.launch {
            try {
                val existing = scheduledMessageDao.getMessageById(scheduleId) ?: return@launch
                messageScheduler.cancelScheduledMessage(scheduleId)
                scheduledMessageDao.update(
                    existing.copy(messageBody = newBody, scheduledTime = newTime, repeatInterval = newRepeat.toEntity())
                )
                messageScheduler.scheduleMessage(
                    messageId = scheduleId,
                    recipient = existing.recipient,
                    messageBody = newBody,
                    conversationId = 0L,
                    scheduledTime = newTime
                )
                _uiState.update { it.copy(editingMessage = null) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to edit message $scheduleId", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}

/**
 * UI State for Scheduled Messages Screen
 */
data class ScheduledMessagesUiState(
    val scheduledMessages: List<ScheduledMessageItem> = emptyList(),
    val isLoading: Boolean = false,
    val editingMessage: EditScheduledMessageData? = null,
    val error: String? = null
)

/**
 * Scheduled message item for UI
 */
data class ScheduledMessageItem(
    val id: Long,
    val recipient: String,
    val messageBody: String,
    val scheduledTime: Long,
    val status: ScheduleStatus = ScheduleStatus.PENDING
)

/**
 * UI Events for Scheduled Messages Screen
 */
sealed class ScheduledMessagesUiEvent {
    data class CancelMessage(val messageId: Long) : ScheduledMessagesUiEvent()
    data class EditMessage(val messageId: Long) : ScheduledMessagesUiEvent()
    data class ConfirmEdit(
        val scheduleId: Long,
        val newBody: String,
        val newTime: Long,
        val newRepeat: RepeatInterval
    ) : ScheduledMessagesUiEvent()
    data object DismissEdit : ScheduledMessagesUiEvent()
}
