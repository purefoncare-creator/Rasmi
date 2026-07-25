package com.rasmi.purevon.presentation.screen.conversation

import android.util.Log
import com.rasmi.purevon.domain.model.Conversation
import com.rasmi.purevon.domain.usecase.contact.GetAllContactsUseCase
import com.rasmi.purevon.domain.usecase.contact.GetContactByNumberUseCase
import com.rasmi.purevon.util.DebugLogger
import com.rasmi.purevon.domain.usecase.message.GetAllConversationsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles recipient autocomplete: caching contacts/conversations and filtering suggestions.
 * Extracted from ConversationViewModel to reduce class size.
 */
internal class RecipientAutocompleteDelegate(
    private val _uiState: MutableStateFlow<ConversationUiState>,
    private val viewModelScope: CoroutineScope,
    private val getAllContactsUseCase: GetAllContactsUseCase,
    private val getAllConversationsUseCase: GetAllConversationsUseCase,
    private val getContactByNumberUseCase: GetContactByNumberUseCase,
    private val loadConversation: (Long) -> Unit
) {
    companion object {
        private const val TAG = "ConversationViewModel"
    }

    private var cachedAllContacts: List<com.rasmi.purevon.domain.model.Contact> = emptyList()
    private var cachedConversations: List<Conversation> = emptyList()
    private var recipientFilterJob: Job? = null

    /**
     * Lazy-load contacts + conversations once (first keystroke in "To:" field).
     */
    suspend fun ensureRecipientCachesLoaded() {
        if (cachedAllContacts.isEmpty()) {
            cachedAllContacts = try {
                getAllContactsUseCase().first()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load contacts for autocomplete", e)
                emptyList()
            }
        }
        if (cachedConversations.isEmpty()) {
            cachedConversations = try {
                getAllConversationsUseCase().first()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load conversations for autocomplete", e)
                emptyList()
            }
        }
    }

    /**
     * Normalise a phone number for comparison by stripping spaces, dashes,
     * parentheses, and the leading '+'.
     */
    fun normalisePhone(phone: String): String {
        return phone.replace("[\\s\\-()]+".toRegex(), "")
            .removePrefix("+")
            .trimStart('0')
    }

    /**
     * Filter contacts & conversations to produce autocomplete suggestions.
     */
    fun filterRecipientSuggestions(query: String) {
        recipientFilterJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(recipientSuggestions = emptyList()) }
            return
        }
        recipientFilterJob = viewModelScope.launch(Dispatchers.Default) {
            delay(150)
            ensureRecipientCachesLoaded()

            val lowerQuery = query.lowercase().trim()
            val normalisedQuery = normalisePhone(query)

            // 1) Match contacts by name or phone number
            val matchedContacts = cachedAllContacts.filter { contact ->
                contact.displayName.lowercase().contains(lowerQuery) ||
                    normalisePhone(contact.phoneNumber).contains(normalisedQuery) ||
                    contact.phoneNumber.contains(query)
            }.take(8)

            // 2) For each matched contact, check if an existing conversation thread exists
            val suggestions = matchedContacts.map { contact ->
                val existingThread = cachedConversations.firstOrNull { conv ->
                    normalisePhone(conv.phoneNumber) == normalisePhone(contact.phoneNumber) ||
                        conv.phoneNumber == contact.phoneNumber
                }
                RecipientSuggestion(
                    contactName = contact.displayName,
                    phoneNumber = contact.phoneNumber,
                    photoUri = contact.photoUri,
                    existingThreadId = existingThread?.threadId
                )
            }

            // 3) Also match conversations that have no saved contact (unknown numbers)
            if (normalisedQuery.any { it.isDigit() }) {
                val contactPhones = matchedContacts.map { normalisePhone(it.phoneNumber) }.toSet()
                val extraConvSuggestions = cachedConversations
                    .filter { conv ->
                        normalisePhone(conv.phoneNumber) !in contactPhones && (
                            normalisePhone(conv.phoneNumber).contains(normalisedQuery) ||
                                conv.phoneNumber.contains(query) ||
                                (conv.contactName?.lowercase()?.contains(lowerQuery) == true)
                        )
                    }
                    .take(4)
                    .map { conv ->
                        RecipientSuggestion(
                            contactName = conv.contactName ?: conv.phoneNumber,
                            phoneNumber = conv.phoneNumber,
                            photoUri = conv.contactPhotoUri,
                            existingThreadId = conv.threadId
                        )
                    }
                val combined = suggestions + extraConvSuggestions
                _uiState.update { it.copy(recipientSuggestions = combined.take(10)) }
            } else {
                _uiState.update { it.copy(recipientSuggestions = suggestions) }
            }
        }
    }

    /**
     * After a recipient is chosen, check whether an existing SMS thread already exists.
     */
    fun resolveExistingThread(phoneNumber: String, contactName: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ensureRecipientCachesLoaded()
                val existingConv = cachedConversations.firstOrNull { conv ->
                    normalisePhone(conv.phoneNumber) == normalisePhone(phoneNumber) ||
                        conv.phoneNumber == phoneNumber
                }
                if (existingConv != null && existingConv.threadId > 0) {
                    Log.d(TAG, "✅ Found existing thread ${existingConv.threadId} for ${DebugLogger.maskPhoneNumber(phoneNumber)} — loading")
                    withContext(Dispatchers.Main) {
                        loadConversation(existingConv.threadId)
                    }
                } else {
                    if (contactName != null) {
                        _uiState.update { it.copy(contactName = contactName) }
                    } else {
                        val contact = getContactByNumberUseCase(phoneNumber)
                        if (contact != null) {
                            _uiState.update { it.copy(contactName = contact.displayName) }
                        }
                    }
                    Log.d(TAG, "No existing thread for ${DebugLogger.maskPhoneNumber(phoneNumber)} — staying in new conversation mode")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving existing thread for ${DebugLogger.maskPhoneNumber(phoneNumber)}", e)
            }
        }
    }
}
