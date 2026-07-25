package com.rasmi.purevon.presentation.screen.conversation

import android.util.Log
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.rasmi.purevon.domain.model.Message
import com.rasmi.purevon.domain.usecase.contact.GetContactByNumberUseCase
import com.rasmi.purevon.domain.usecase.message.GetAddressFromThreadIdUseCase
import com.rasmi.purevon.domain.usecase.message.GetMessagesByThreadPagedUseCase
import com.rasmi.purevon.domain.usecase.message.GetMessagesByThreadUseCase
import com.rasmi.purevon.domain.usecase.message.MarkThreadAsReadUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

/**
 * Delegate that handles loading conversation messages and resolving phone/contact info.
 * Extracted from ConversationViewModel for maintainability.
 */
internal class ConversationLoaderDelegate(
    private val _uiState: MutableStateFlow<ConversationUiState>,
    private val viewModelScope: CoroutineScope,
    private val getMessagesByThreadUseCase: GetMessagesByThreadUseCase,
    private val getMessagesByThreadPagedUseCase: GetMessagesByThreadPagedUseCase,
    private val getAddressFromThreadIdUseCase: GetAddressFromThreadIdUseCase,
    private val getContactByNumberUseCase: GetContactByNumberUseCase,
    private val markThreadAsReadUseCase: MarkThreadAsReadUseCase,
    private val scheduledMessageDao: com.rasmi.purevon.data.local.dao.ScheduledMessageDao,
    private val isValidPhoneNumber: (String?) -> Boolean,
    private val isUsableAddress: (String?) -> Boolean,
    private val loadDraft: (String) -> String?,
    private val getCachedResolvedPhone: () -> String?,
    private val setCachedResolvedPhone: (String?) -> Unit
) {
    companion object {
        private const val TAG = "ConversationViewModel"
        private const val SCHEDULED_CACHE_TTL_MS = 5_000L // ✅ FIX #64: Cache scheduled messages for 5 seconds
    }

    private var loadConversationJob: Job? = null
    private var lastScheduledQueryTime = 0L
    private var cachedScheduledMessages: List<Message> = emptyList()

    fun loadConversation(threadId: Long) {
        // Cancel previous job to prevent multiple collectors
        loadConversationJob?.cancel()
        // Clear cached phone when loading a new/different conversation
        setCachedResolvedPhone(null)

        loadConversationJob = viewModelScope.launch {
            _uiState.update { it.copy(threadId = threadId) }

            var draftApplied = false

            // Pre-resolve phoneNumber from system thread metadata (once)
            if (getCachedResolvedPhone() == null) {
                try {
                    val addr = withContext(Dispatchers.IO) {
                        getAddressFromThreadIdUseCase(threadId)
                    }
                    if (isUsableAddress(addr) && addr != null) {
                        setCachedResolvedPhone(addr)
                        if (isValidPhoneNumber(addr)) {
                            val contact = withContext(Dispatchers.IO) {
                                getContactByNumberUseCase(addr)
                            }
                            if (contact != null) {
                                _uiState.update { it.copy(phoneNumber = addr, contactName = contact.name) }
                            } else {
                                _uiState.update { it.copy(phoneNumber = addr) }
                            }
                        } else {
                            // Alphanumeric sender (e.g. "TwilioAuthy", "Amazon")
                            _uiState.update { it.copy(phoneNumber = addr, contactName = addr) }
                        }
                    }
                } catch (_: Exception) { /* best-effort */ }
            }

            try {
                getMessagesByThreadUseCase(threadId)
                    .catch { e ->
                        if (e is CancellationException) throw e
                        _uiState.update { it.copy(error = e.message) }
                    }
                    .distinctUntilChanged()
                    .collect { messages ->
                        val rawPhone = messages.firstOrNull()?.phoneNumber ?: ""
                        val phoneNumber = if (isUsableAddress(rawPhone)) rawPhone
                                          else getCachedResolvedPhone() ?: rawPhone
                        val contactName = messages.firstOrNull()?.contactName
                            ?: if (!isValidPhoneNumber(phoneNumber) && isUsableAddress(phoneNumber)) phoneNumber else null

                        val draftToApply: String? = if (!draftApplied) {
                            draftApplied = true
                            loadDraft(threadId.toString())
                        } else null

                        // Remove temp messages when real messages arrive
                        val currentTempMessages = _uiState.value.messages.filter { it.id < 0 }
                        val remainingTempMessages = currentTempMessages.filter { temp ->
                            val bodyMatched = messages.any { real ->
                                !real.body.isNullOrBlank() &&
                                real.body == temp.body &&
                                kotlin.math.abs(real.timestamp - temp.timestamp) < 30_000
                            }
                            val timeMatched = messages.any { real ->
                                kotlin.math.abs(real.timestamp - temp.timestamp) < 30_000
                            }
                            !bodyMatched && !timeMatched
                        }

                        // Merge with scheduled messages from DB
                        // ✅ FIX #64: Use time-based cache — scheduled messages rarely change
                        val now = System.currentTimeMillis()
                        val scheduledMessages = if (now - lastScheduledQueryTime > SCHEDULED_CACHE_TTL_MS) {
                            val result = try {
                                val resolvedPhone = phoneNumber.takeIf { it.isNotBlank() } ?: getCachedResolvedPhone() ?: ""
                                if (resolvedPhone.isNotBlank()) {
                                    scheduledMessageDao.getPendingByRecipient(resolvedPhone).map { entity ->
                                        Message(
                                            id = -entity.id,
                                            threadId = threadId,
                                            phoneNumber = resolvedPhone,
                                            contactName = contactName,
                                            body = entity.messageBody,
                                            timestamp = entity.scheduledTime,
                                            type = com.rasmi.purevon.data.local.entity.MessageType.QUEUED.value, // ✅ FIX #8
                                            category = com.rasmi.purevon.data.local.entity.MessageCategory.PERSONAL,
                                            isRead = true,
                                            isSent = false,
                                            isDelivered = false,
                                            simSlot = entity.simSlot,
                                            isSpam = false,
                                            spamScore = 0f,
                                            isMms = entity.attachmentUris.isNotEmpty(),
                                            attachmentUris = entity.attachmentUris,
                                            attachmentTypes = emptyList(),
                                            status = com.rasmi.purevon.domain.model.MessageStatus.SENDING,
                                            isScheduled = true,
                                            scheduledTime = entity.scheduledTime,
                                            scheduleId = entity.id
                                        )
                                    }
                                } else emptyList()
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to load scheduled messages", e)
                                emptyList()
                            }
                            lastScheduledQueryTime = now
                            cachedScheduledMessages = result
                            result
                        } else {
                            cachedScheduledMessages
                        }

                        val existingScheduleIds = (messages + remainingTempMessages).mapNotNull { it.scheduleId }.toSet()
                        val newScheduledMessages = scheduledMessages.filter { it.scheduleId !in existingScheduleIds }
                        val mergedMessages = messages + remainingTempMessages + newScheduledMessages

                        _uiState.update { currentState ->
                            currentState.copy(
                                messages = mergedMessages,
                                phoneNumber = if (isUsableAddress(phoneNumber)) phoneNumber
                                              else if (isUsableAddress(currentState.phoneNumber)) currentState.phoneNumber
                                              else phoneNumber.takeIf { it.isNotBlank() } ?: currentState.phoneNumber,
                                contactName = contactName ?: currentState.contactName,
                                messageText = draftToApply ?: currentState.messageText,
                                isNewConversation = false
                            )
                        }

                        markThreadAsReadUseCase(threadId)
                    }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun loadConversationPaged(threadId: Long): Flow<PagingData<Message>> {
        _uiState.update { it.copy(threadId = threadId) }

        viewModelScope.launch {
            try {
                markThreadAsReadUseCase(threadId)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to mark thread as read", e)
            }
        }

        return getMessagesByThreadPagedUseCase(threadId)
            .cachedIn(viewModelScope)
    }
}
