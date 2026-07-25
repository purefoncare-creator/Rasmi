package com.rasmi.purevon.presentation.screen.conversation

import android.content.Context
import android.util.Log
import com.rasmi.purevon.R
import com.rasmi.purevon.data.preferences.SettingsDataStore
import com.rasmi.purevon.domain.model.Message
import com.rasmi.purevon.domain.usecase.message.GetOrCreateThreadIdUseCase
import com.rasmi.purevon.domain.usecase.message.RetryFailedMessageUseCase
import com.rasmi.purevon.domain.usecase.message.SendMessageUseCase
import com.rasmi.purevon.domain.usecase.message.SendMmsMessageUseCase
import com.rasmi.purevon.domain.usecase.message.SyncThreadMessagesUseCase
import com.rasmi.purevon.presentation.util.getLocalizedMessage
import com.rasmi.purevon.presentation.util.getLocalizedSuggestion
import com.rasmi.purevon.util.message.MessageRetryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Delegate that handles sending messages (SMS & MMS) and retrying failed messages.
 * Extracted from ConversationViewModel for maintainability.
 */
internal class MessageSendingDelegate(
    private val context: Context,
    private val _uiState: MutableStateFlow<ConversationUiState>,
    private val viewModelScope: CoroutineScope,
    private val sendMessageUseCase: SendMessageUseCase,
    private val sendMmsMessageUseCase: SendMmsMessageUseCase,
    private val retryFailedMessageUseCase: RetryFailedMessageUseCase,
    private val settingsDataStore: SettingsDataStore,
    private val messageRetryManager: MessageRetryManager,
    private val syncThreadMessagesUseCase: SyncThreadMessagesUseCase,
    private val getOrCreateThreadIdUseCase: GetOrCreateThreadIdUseCase,
    private val isSmsAskMode: () -> Boolean,
    private val cachedResolvedPhone: () -> String?,
    private val isValidPhoneNumber: (String?) -> Boolean,
    private val clearDraft: (String) -> Unit,
    private val loadConversation: (Long) -> Unit
) {
    companion object {
        private const val TAG = "ConversationViewModel"
    }

    /** Active send job — stored so it can be cancelled by the user */
    private var sendJob: Job? = null

    fun sendMessage(simSlotOverride: Int? = null) {
        val currentState = _uiState.value

        // Guard against double-send
        if (currentState.isSending) {
            Log.w(TAG, "Already sending, ignoring duplicate send request")
            return
        }

        Log.w(TAG, "=== sendMessage called ===")
        Log.w(TAG, "phoneNumber: '${currentState.phoneNumber}'")
        Log.w(TAG, "messageText: '${currentState.messageText.take(30)}'")
        Log.w(TAG, "attachments: ${currentState.attachments.size}")

        // Must have either text or attachments
        if (currentState.messageText.isBlank() && currentState.attachments.isEmpty()) {
            Log.w(TAG, "No message text or attachments, returning")
            return
        }

        // Validate phone number
        val validatedPhone: String = when {
            isValidPhoneNumber(currentState.phoneNumber) -> currentState.phoneNumber!!
            isValidPhoneNumber(cachedResolvedPhone()) -> {
                _uiState.update { it.copy(phoneNumber = cachedResolvedPhone()) }
                cachedResolvedPhone()!!
            }
            else -> {
                Log.e(TAG, "Phone number is blank! Cannot send message")
                _uiState.update { it.copy(error = context.getString(R.string.msg_phone_number_missing)) }
                return
            }
        }

        // ASK mode: show SIM picker before sending
        if (isSmsAskMode() && simSlotOverride == null) {
            Log.d(TAG, "ASK mode active — showing SIM picker")
            _uiState.update { it.copy(showSmsSimPickerForSend = true) }
            return
        }

        // Optimistic update: create temp message for immediate UI feedback
        val tempMessage = Message(
            id = -System.currentTimeMillis(),
            threadId = currentState.threadId ?: -1L,
            phoneNumber = validatedPhone,
            contactName = currentState.contactName,
            body = currentState.messageText.takeIf { it.isNotBlank() },
            timestamp = System.currentTimeMillis(),
            type = com.rasmi.purevon.data.local.entity.MessageType.OUTBOX.value, // ✅ FIX #8
            category = com.rasmi.purevon.data.local.entity.MessageCategory.PERSONAL,
            isRead = true,
            isSent = false,
            isDelivered = false,
            simSlot = null,
            isSpam = false,
            spamScore = 0f,
            isMms = currentState.attachments.isNotEmpty(),
            attachmentUris = currentState.attachments.map { it.uri },
            attachmentTypes = currentState.attachments.map { it.mimeType ?: "*/*" },
            status = com.rasmi.purevon.domain.model.MessageStatus.SENDING
        )

        _uiState.update { state ->
            state.copy(
                messages = state.messages + listOf(tempMessage),
                messageText = "",
                // ✅ FIX #63: Clear attachments to prevent them appearing twice
                attachments = emptyList(),
                isSending = true,
                isCompressingAttachments = currentState.attachments.isNotEmpty(),
                replyToMessage = null
            )
        }

        Log.w(TAG, "✅ Optimistic update: Added temp message ${tempMessage.id}")

        sendJob = viewModelScope.launch {
            try {
                val defaultSimSlot = simSlotOverride ?: settingsDataStore.defaultSmsSimSubscriptionId.first()
                    .takeIf { it > 0 && it != Int.MAX_VALUE }

                val result = if (currentState.attachments.isNotEmpty()) {
                    Log.w(TAG, "Sending MMS with ${currentState.attachments.size} attachments")
                    sendMmsMessageUseCase(
                        phoneNumber = validatedPhone,
                        message = currentState.messageText.takeIf { it.isNotBlank() },
                        attachmentUris = currentState.attachments.map { it.uri },
                        simSlot = defaultSimSlot
                    )
                } else {
                    Log.w(TAG, "Sending SMS")
                    sendMessageUseCase(
                        phoneNumber = validatedPhone,
                        message = currentState.messageText,
                        simSlot = defaultSimSlot
                    )
                }

                Log.w(TAG, "Send operation returned: ${result.isSuccess}")

                result.onSuccess { messageId ->
                    Log.w(TAG, "✅ Message sent successfully with ID: $messageId")

                    val draftKey = currentState.threadId?.toString() ?: validatedPhone
                    clearDraft(draftKey)

                    // ✅ FIX M10: Remove temp message immediately on success, don't wait for sync
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages.filter { it.id != tempMessage.id },
                            isSending = false,
                            isCompressingAttachments = false,
                            attachments = emptyList(),
                            attachmentsTotalSize = 0L,
                            error = null
                        )
                    }

                    // New conversation: load the thread immediately using the threadId
                    // returned by the send operation — no extra content-provider round-trip.
                    if (currentState.threadId == null) {
                        val sentThreadId = (result as? com.rasmi.purevon.domain.model.MessageResult.Success)?.threadId ?: 0L
                        if (sentThreadId > 0L) {
                            Log.d(TAG, "[NEW_CONV] Loading thread $sentThreadId from send result (fast path)")
                            loadConversation(sentThreadId)
                        } else {
                            // Fallback: query the content provider (should rarely happen)
        sendJob = viewModelScope.launch {
                                try {
                                    val threadId = getOrCreateThreadIdUseCase(validatedPhone)
                                    if (threadId > 0) {
                                        Log.d(TAG, "[NEW_CONV] Loading thread $threadId from fallback query")
                                        loadConversation(threadId)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to load new thread", e)
                                }
                            }
                        }
                        return@onSuccess  // Skip the sync-for-existing-thread below
                    }

                    // Existing conversation: reload so real message replaces temp
                    val activeThreadId = _uiState.value.threadId
                    if (activeThreadId != null && activeThreadId > 0) {
                        viewModelScope.launch(Dispatchers.IO) {
                            syncThreadMessagesUseCase(activeThreadId)
                        }
                    }
                }.onFailure { error ->
                    Log.e(TAG, "❌ Send failed: ${error.message}")

                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages.map { msg ->
                                if (msg.id == tempMessage.id) {
                                    msg.copy(
                                        type = com.rasmi.purevon.data.local.entity.MessageType.FAILED.value, // ✅ FIX #8
                                        status = com.rasmi.purevon.domain.model.MessageStatus.FAILED
                                    )
                                } else msg
                            },
                            error = error.getLocalizedMessage(context),
                            isSending = false,
                            isCompressingAttachments = false
                        )
                    }

                    error.getLocalizedSuggestion(context)?.let { action ->
                        _uiState.update { s ->
                            s.copy(error = "${s.error}\n\n${context.getString(R.string.msg_suggestion_prefix, action)}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception sending message", e)
                _uiState.update {
                    it.copy(
                        error = context.getString(R.string.msg_unexpected_error, e.message ?: ""),
                        isSending = false,
                        isCompressingAttachments = false
                    )
                }
            } finally {
                if (_uiState.value.isSending) {
                    _uiState.update { it.copy(isSending = false, isCompressingAttachments = false) }
                }
            }
        }
    }

    fun retryMessage(messageId: Long) {
        // ✅ FIX M27: Guard against concurrent sends
        if (_uiState.value.isSending) {
            Log.w(TAG, "⚠️ Send already in progress, ignoring retry")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }

            try {
                Log.d(TAG, "🔄 Retrying message $messageId")

                if (!messageRetryManager.canRetry(messageId)) {
                    _uiState.update {
                        it.copy(
                            error = messageRetryManager.getRetryStatusMessage(messageId),
                            isSending = false
                        )
                    }
                    return@launch
                }

                val delay = messageRetryManager.getRetryDelay(messageId)
                if (delay > 0) {
                    kotlinx.coroutines.delay(delay)
                }

                val result = retryFailedMessageUseCase(messageId)

                when (result) {
                    is com.rasmi.purevon.domain.model.MessageResult.Success -> {
                        Log.d(TAG, "✅ Message retried successfully")
                        _uiState.update { it.copy(isSending = false, error = null) }
                        messageRetryManager.resetRetryCount(messageId)
                    }
                    is com.rasmi.purevon.domain.model.MessageResult.Failure -> {
                        Log.e(TAG, "❌ Retry failed: ${result.error.message}")
                        _uiState.update {
                            it.copy(
                                error = messageRetryManager.getRetryStatusMessage(messageId),
                                isSending = false
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception retrying message", e)
                _uiState.update {
                    it.copy(
                        error = context.getString(R.string.msg_retry_unexpected_error, e.message ?: ""),
                        isSending = false
                    )
                }
            }
        }
    }

    /**
     * Cancel the current MMS send operation.
     * Cancels the coroutine which also cancels any in-progress retry delays.
     */
    fun cancelSend() {
        val job = sendJob
        if (job != null && job.isActive) {
            Log.w(TAG, "🚫 Cancelling MMS send operation")
            job.cancel()
            sendJob = null
            _uiState.update { state ->
                state.copy(
                    messages = state.messages.map { msg ->
                        if (msg.type == com.rasmi.purevon.data.local.entity.MessageType.OUTBOX.value &&
                            msg.status == com.rasmi.purevon.domain.model.MessageStatus.SENDING) {
                            msg.copy(
                                type = com.rasmi.purevon.data.local.entity.MessageType.FAILED.value,
                                status = com.rasmi.purevon.domain.model.MessageStatus.FAILED
                            )
                        } else msg
                    },
                    isSending = false,
                    isCompressingAttachments = false,
                    error = context.getString(R.string.msg_send_cancelled)
                )
            }
        }
    }
}
