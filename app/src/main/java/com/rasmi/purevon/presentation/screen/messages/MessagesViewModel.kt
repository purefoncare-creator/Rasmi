package com.rasmi.purevon.presentation.screen.messages

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rasmi.purevon.domain.model.Conversation
import com.rasmi.purevon.domain.usecase.conversation.ManageConversationUseCase
import com.rasmi.purevon.domain.usecase.message.ClearAllMessagesUseCase
import com.rasmi.purevon.domain.usecase.message.DeleteConversationUseCase
import com.rasmi.purevon.domain.usecase.message.MarkThreadAsReadUseCase
import com.rasmi.purevon.domain.usecase.message.SyncMessagesUseCase
import com.rasmi.purevon.util.event.AppEvent
import com.rasmi.purevon.util.event.EventBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.rasmi.purevon.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import javax.inject.Inject

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map

import kotlinx.coroutines.flow.merge

/**
 * ViewModel for Messages Screen
 * 
 * Note: For message delivery tracking, consider using SendMessageWithTrackingUseCase
 * instead of SendMessageUseCase to get sent/delivered status updates
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class MessagesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deleteConversationUseCase: DeleteConversationUseCase,
    private val clearAllMessagesUseCase: ClearAllMessagesUseCase,
    private val markThreadAsReadUseCase: MarkThreadAsReadUseCase,
    private val syncMessagesUseCase: SyncMessagesUseCase,
    private val manageConversationUseCase: ManageConversationUseCase,
    private val getAllConversationsUseCase: com.rasmi.purevon.domain.usecase.message.GetAllConversationsUseCase,
    private val searchConversationsUseCase: com.rasmi.purevon.domain.usecase.message.SearchConversationsUseCase,
    private val getStarredMessagesUseCase: com.rasmi.purevon.domain.usecase.message.GetStarredMessagesUseCase,
    private val getArchivedConversationsUseCase: com.rasmi.purevon.domain.usecase.message.GetArchivedConversationsUseCase,
    private val getUnreadCountUseCase: com.rasmi.purevon.domain.usecase.message.GetUnreadCountUseCase,
    private val messageRepository: com.rasmi.purevon.domain.repository.MessageRepository,
    private val getAllContactsUseCase: com.rasmi.purevon.domain.usecase.contact.GetAllContactsUseCase
) : ViewModel() {

    private fun normalizePhone(phone: String): String =
        phone.filter { it.isDigit() }.let { digits ->
            if (digits.startsWith("00")) digits.substring(2) else digits
        }

    private val favoritePhoneNumbers: MutableSet<String> =
        java.util.Collections.synchronizedSet(mutableSetOf())

    private val _uiState = MutableStateFlow(MessagesUiState(isLoading = true))
    val uiState: StateFlow<MessagesUiState> = _uiState.asStateFlow()

    private val searchQuery = MutableStateFlow("")
    
    // ✅ FINAL FIX: Direct Flow without Paging - like Realm's live queries
    // This keeps data in memory and only updates what changed
    // ✅ NEW: Direct Flow like Realm - data stays in memory, updates automatically
    @OptIn(ExperimentalCoroutinesApi::class)
    val conversationsFlow: Flow<List<Conversation>> = searchQuery
        .debounce(50) // ✅ OPTIMIZED: Match repository speed (50ms) for instant feel
        .flatMapLatest { query ->
            if (query.isBlank()) {
                getAllConversationsUseCase()
            } else {
                searchConversationsUseCase(query)
            }
        }
        .onEach {
            if (_uiState.value.isLoading) {
                _uiState.update { state -> state.copy(isLoading = false) }
            }
        }
        .map { conversations ->
            val favoritePhones = favoritePhoneNumbers
            if (favoritePhones.isEmpty()) {
                conversations
            } else {
                conversations.map { conv ->
                    if (conv.isFavorite) {
                        conv
                    } else {
                        conv.copy(isFavorite = normalizePhone(conv.phoneNumber) in favoritePhones)
                    }
                }
            }
        }
        .flowOn(Dispatchers.Default)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)
    
    // Broadcast receiver for new SMS
    init {
        // Don't trigger refresh here - let the screen trigger it when needed
        observeSmsEvents()
        observeStarredMessages()
        observeArchivedConversations()
        observeUnreadCount()
        observeFavoriteContacts()
    }
    
    private fun observeFavoriteContacts() {
        viewModelScope.launch {
            getAllContactsUseCase()
                .catch { e ->
                    Log.e("MessagesViewModel", "Error loading favorite contacts", e)
                }
                .collect { contacts ->
                    favoritePhoneNumbers.clear()
                    contacts
                        .filter { it.isFavorite }
                        .forEach { favoritePhoneNumbers.add(normalizePhone(it.phoneNumber)) }
                }
        }
    }

    private fun observeStarredMessages() {
        viewModelScope.launch {
            getStarredMessagesUseCase()
                .catch { e ->
                    Log.e("MessagesViewModel", "Error loading starred messages", e)
                }
                .collect { messages ->
                    _uiState.update { it.copy(starredMessages = messages) }
                }
        }
    }
    
    private fun observeArchivedConversations() {
        viewModelScope.launch {
            getArchivedConversationsUseCase()
                .catch { e ->
                    Log.e("MessagesViewModel", "Error loading archived conversations", e)
                }
                .collect { conversations ->
                    _uiState.update { it.copy(archivedConversations = conversations) }
                }
        }
    }
    
    /**
     * Observe SMS events using modern EventBus
     * ✅ FIX: Force sync on new SMS to update conversation order instantly
     */
    private fun observeSmsEvents() {
        viewModelScope.launch {
            EventBus.events.collect { event ->
                when (event) {
                    is AppEvent.SmsReceived -> {
                        Log.d("MessagesViewModel", "New SMS received - forcing conversation sync")
                        messageRepository.forceSyncConversations()
                    }
                    else -> { /* Ignore other events */ }
                }
            }
        }
    }

    private fun observeUnreadCount() {
        viewModelScope.launch {
            getUnreadCountUseCase()
                .catch { e ->
                    Log.e("MessagesViewModel", "Error loading unread count", e)
                }
                .collect { count ->
                    _uiState.update { it.copy(unreadCount = count) }
                }
        }
    }

    /**
     * Select all conversations from the visible list (called from the screen since
     * the conversations are collected as a separate Flow)
     */
    fun selectAllFromVisible(allIds: Set<Long>) {
        _uiState.update { it.copy(selectedConversationIds = allIds) }
    }

    fun onEvent(event: MessagesUiEvent) {
        when (event) {
            is MessagesUiEvent.InitializeScreen -> {
                // No action needed - PagingSource loads automatically
                // ContentObserver handles updates
            }
            
            is MessagesUiEvent.SearchQueryChanged -> {
                searchQuery.value = event.query
                _uiState.update { it.copy(searchQuery = event.query) }
            }

            is MessagesUiEvent.ConversationSelected -> {
                // Navigation handled by screen
            }

            is MessagesUiEvent.DeleteConversation -> {
                viewModelScope.launch {
                    try {
                        // PagingSource's ContentObserver will automatically refresh
                        deleteConversationUseCase(event.conversationId)
                    } catch (e: Exception) {
                        _uiState.update { it.copy(error = e.message) }
                    }
                }
            }

            is MessagesUiEvent.MarkAsRead -> {
                viewModelScope.launch {
                    try {
                        // PagingSource's ContentObserver will automatically refresh
                        markThreadAsReadUseCase(event.conversationId)
                    } catch (e: Exception) {
                        _uiState.update { it.copy(error = e.message) }
                    }
                }
            }

            is MessagesUiEvent.ArchiveConversation -> {
                viewModelScope.launch {
                    manageConversationUseCase.toggleArchive(event.conversationId)
                        .onFailure { error ->
                            _uiState.update { it.copy(error = error.getUserMessage()) }
                        }
                }
            }

            is MessagesUiEvent.DismissError -> {
                _uiState.update { it.copy(error = null) }
            }

            is MessagesUiEvent.NewMessage -> {
                // Navigation handled by screen
                // User should navigate to conversation with empty threadId
            }
            
            is MessagesUiEvent.ClearAllMessages -> {
                viewModelScope.launch {
                    try {
                        clearAllMessagesUseCase()
                    } catch (e: Exception) {
                        _uiState.update { it.copy(error = context.getString(R.string.error_failed_clear_messages, e.message ?: "")) }
                    }
                }
            }
            
            is MessagesUiEvent.TabChanged -> {
                _uiState.update { it.copy(selectedTab = event.tab) }
            }

            is MessagesUiEvent.ConversationLongPressed -> {
                _uiState.update {
                    it.copy(
                        isSelectionMode = true,
                        selectedConversationIds = setOf(event.conversationId)
                    )
                }
            }

            is MessagesUiEvent.ToggleConversationSelection -> {
                val currentSelected = _uiState.value.selectedConversationIds
                val newSelected = if (event.conversationId in currentSelected) {
                    currentSelected - event.conversationId
                } else {
                    currentSelected + event.conversationId
                }
                if (newSelected.isEmpty()) {
                    _uiState.update { it.copy(isSelectionMode = false, selectedConversationIds = emptySet()) }
                } else {
                    _uiState.update { it.copy(selectedConversationIds = newSelected) }
                }
            }

            is MessagesUiEvent.ExitSelectionMode -> {
                _uiState.update { it.copy(isSelectionMode = false, selectedConversationIds = emptySet()) }
            }

            is MessagesUiEvent.SelectAllConversations -> {
                // This is handled via selectAllFromVisible() called from the screen
            }

            is MessagesUiEvent.DeselectAllConversations -> {
                _uiState.update { it.copy(isSelectionMode = false, selectedConversationIds = emptySet()) }
            }

            is MessagesUiEvent.DeleteSelectedConversations -> {
                viewModelScope.launch {
                    val ids = _uiState.value.selectedConversationIds.toList()
                    _uiState.update { it.copy(isSelectionMode = false, selectedConversationIds = emptySet()) }
                    // ✅ FIX M22: Use supervisorScope so one failure doesn't cancel others
                    val errors = mutableListOf<String>()
                    kotlinx.coroutines.supervisorScope {
                        ids.map { id ->
                            async {
                                try {
                                    deleteConversationUseCase(id)
                                } catch (e: Exception) {
                                    errors.add(e.message ?: "Unknown error")
                                }
                            }
                        }.awaitAll()
                    }
                    if (errors.isNotEmpty()) {
                        _uiState.update { it.copy(error = errors.first()) }
                    }
                }
            }

            is MessagesUiEvent.ArchiveSelectedConversations -> {
                viewModelScope.launch {
                    val ids = _uiState.value.selectedConversationIds.toList()
                    _uiState.update { it.copy(isSelectionMode = false, selectedConversationIds = emptySet()) }
                    // ✅ FIX M22/M32: Use supervisorScope for independent failures
                    val errors = mutableListOf<String>()
                    kotlinx.coroutines.supervisorScope {
                        ids.map { id ->
                            async {
                                manageConversationUseCase.toggleArchive(id)
                                    .onFailure { error ->
                                        errors.add(error.getUserMessage())
                                    }
                            }
                        }.awaitAll()
                    }
                    if (errors.isNotEmpty()) {
                        _uiState.update { it.copy(error = errors.joinToString("\n")) }
                    }
                }
            }

            is MessagesUiEvent.MarkSelectedAsRead -> {
                viewModelScope.launch {
                    val ids = _uiState.value.selectedConversationIds.toList()
                    _uiState.update { it.copy(isSelectionMode = false, selectedConversationIds = emptySet()) }
                    // ✅ FIX M22: Use supervisorScope
                    val errors = mutableListOf<String>()
                    kotlinx.coroutines.supervisorScope {
                        ids.map { id ->
                            async {
                                try {
                                    markThreadAsReadUseCase(id)
                                } catch (e: Exception) {
                                    errors.add(e.message ?: "Unknown error")
                                }
                            }
                        }.awaitAll()
                    }
                    if (errors.isNotEmpty()) {
                        _uiState.update { it.copy(error = errors.first()) }
                    }
                }
            }

            is MessagesUiEvent.RefreshConversations -> {
                refreshConversations()
            }
        }
    }

    private fun refreshConversations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                syncMessagesUseCase.syncConversationsOnly()
            } catch (e: Exception) {
                Log.e("MessagesViewModel", "Error refreshing conversations", e)
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }
}
