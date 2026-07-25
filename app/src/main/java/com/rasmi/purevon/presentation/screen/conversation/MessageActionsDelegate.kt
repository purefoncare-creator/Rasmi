package com.rasmi.purevon.presentation.screen.conversation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.rasmi.purevon.R
import com.rasmi.purevon.domain.model.Message
import com.rasmi.purevon.domain.usecase.contact.GetAllContactsUseCase
import com.rasmi.purevon.domain.usecase.message.DeleteConversationUseCase
import com.rasmi.purevon.domain.usecase.message.DeleteMessageUseCase
import com.rasmi.purevon.domain.usecase.message.RemoveReactionUseCase
import com.rasmi.purevon.domain.usecase.message.SendMessageUseCase
import com.rasmi.purevon.domain.usecase.message.SendMmsMessageUseCase
import com.rasmi.purevon.domain.usecase.message.ToggleMessageStarredUseCase
import com.rasmi.purevon.domain.usecase.message.ToggleReactionUseCase
import com.rasmi.purevon.util.SoundManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Delegate that handles message-level actions: delete, copy, star, reactions,
 * forwarding, image viewer, and the message actions bottom sheet.
 * Extracted from ConversationViewModel for maintainability.
 */
internal class MessageActionsDelegate(
    private val context: Context,
    private val _uiState: MutableStateFlow<ConversationUiState>,
    private val viewModelScope: CoroutineScope,
    private val _contacts: MutableStateFlow<List<com.rasmi.purevon.domain.model.Contact>>,
    private val deleteConversationUseCase: DeleteConversationUseCase,
    private val toggleMessageStarredUseCase: ToggleMessageStarredUseCase,
    private val deleteMessageUseCase: DeleteMessageUseCase,
    private val toggleReactionUseCase: ToggleReactionUseCase,
    private val removeReactionUseCase: RemoveReactionUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val sendMmsMessageUseCase: SendMmsMessageUseCase,
    private val getAllContactsUseCase: GetAllContactsUseCase,
    private val soundManager: SoundManager
) {
    companion object {
        private const val TAG = "ConversationViewModel"
    }

    fun deleteConversation(threadId: Long) {
        viewModelScope.launch {
            try {
                deleteConversationUseCase(threadId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to delete conversation: ${e.message}") }
            }
        }
    }

    fun toggleFavorite(messageId: Long) {
        viewModelScope.launch {
            try {
                toggleMessageStarredUseCase(messageId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle message favorite", e)
                _uiState.update { it.copy(error = e.message ?: "Failed to toggle favorite") }
            }
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            try {
                deleteMessageUseCase(messageId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete message", e)
                _uiState.update { it.copy(error = "Failed to delete message: ${e.message}") }
            }
        }
    }

    fun copyMessage(body: String?) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("message", body ?: "")
        clipboard?.setPrimaryClip(clip)
        soundManager.playCopySound()
    }

    fun setReplyTo(message: Message?) {
        _uiState.update { it.copy(replyToMessage = message) }
    }

    fun cancelReply() {
        _uiState.update { it.copy(replyToMessage = null) }
    }

    fun showReactionPicker(message: Message) {
        _uiState.update {
            it.copy(
                showReactionPicker = true,
                selectedMessageForReaction = message
            )
        }
    }

    fun hideReactionPicker() {
        _uiState.update {
            it.copy(
                showReactionPicker = false,
                selectedMessageForReaction = null
            )
        }
    }

    fun addReaction(messageId: Long, emoji: String) {
        viewModelScope.launch {
            toggleReactionUseCase(messageId, emoji)
                .onSuccess {
                    Log.d(TAG, "Reaction $emoji toggled on message $messageId")
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to add reaction: ${error.getUserMessage()}")
                    _uiState.update { it.copy(error = error.getUserMessage()) }
                }
        }
    }

    fun removeReaction(messageId: Long) {
        viewModelScope.launch {
            removeReactionUseCase(messageId)
                .onSuccess {
                    Log.d(TAG, "Reaction removed from message $messageId")
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to remove reaction: ${error.getUserMessage()}")
                    _uiState.update { it.copy(error = error.getUserMessage()) }
                }
        }
    }

    fun showMessageActions(message: Message) {
        _uiState.update {
            it.copy(
                showMessageActions = true,
                selectedMessageForActions = message
            )
        }
    }

    fun hideMessageActions() {
        _uiState.update {
            it.copy(
                showMessageActions = false,
                selectedMessageForActions = null
            )
        }
    }

    fun showImageViewer(urls: List<String>, initialIndex: Int) {
        _uiState.update {
            it.copy(
                showImageViewer = true,
                imageViewerUrls = urls,
                imageViewerInitialIndex = initialIndex
            )
        }
    }

    fun hideImageViewer() {
        _uiState.update { it.copy(showImageViewer = false) }
    }

    fun forwardMessage(message: Message) {
        _uiState.update {
            it.copy(
                showForwardDialog = true,
                messageToForward = message
            )
        }
        viewModelScope.launch {
            _contacts.value = getAllContactsUseCase().first()
        }
    }

    fun hideForwardDialog() {
        _uiState.update {
            it.copy(
                showForwardDialog = false,
                messageToForward = null
            )
        }
    }

    fun confirmForward(contacts: List<com.rasmi.purevon.domain.model.Contact>) {
        val message = _uiState.value.messageToForward ?: return
        viewModelScope.launch {
            try {
                val totalRecipients = contacts.size
                var successCount = 0
                val failures = mutableListOf<String>()

                // ✅ FIX #49: Limit concurrent forwards to 5 to prevent overwhelming SmsManager
                val semaphore = Semaphore(5)
                coroutineScope {
                    val jobs = contacts.map { contact ->
                        async {
                            semaphore.withPermit {
                                try {
                                    if (message.isMms && message.attachmentUris.isNotEmpty()) {
                                        sendMmsMessageUseCase(
                                            phoneNumber = contact.phoneNumber,
                                            message = message.body,
                                            attachmentUris = message.attachmentUris,
                                            simSlot = null
                                        )
                                    } else {
                                        sendMessageUseCase(
                                            phoneNumber = contact.phoneNumber,
                                            message = message.body ?: ""
                                        )
                                    }
                                    true
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to forward to ${contact.phoneNumber}", e)
                                    failures.add(contact.displayName)
                                    false
                                }
                            }
                        }
                    }

                    jobs.forEach { job ->
                        if (job.await()) successCount++
                    }
                }

                _uiState.update {
                    it.copy(
                        showForwardDialog = false,
                        messageToForward = null,
                        error = if (failures.isNotEmpty() && successCount > 0) {
                            context.getString(R.string.msg_forward_partial_success, successCount, totalRecipients, failures.size)
                        } else if (failures.isNotEmpty()) {
                            context.getString(R.string.msg_unexpected_error, failures.joinToString())
                        } else null
                    )
                }

                Log.d(TAG, "✅ Message forwarded to $successCount/$totalRecipients contacts")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to forward message", e)
                _uiState.update { it.copy(error = context.getString(R.string.msg_unexpected_error, e.message ?: "")) }
            }
        }
    }
}
