package com.rasmi.purevon.presentation.screen.messages

import com.rasmi.purevon.domain.model.Message
import com.rasmi.purevon.domain.model.Conversation

/**
 * Message tabs
 */
enum class MessageTab { ALL, STARRED, ARCHIVED }

/**
 * UI State for Messages Screen (Conversation List)
 */
data class MessagesUiState(
    val starredMessages: List<Message> = emptyList(),
    val archivedConversations: List<Conversation> = emptyList(),
    val selectedTab: MessageTab = MessageTab.ALL,
    val searchQuery: String = "",
    val unreadCount: Int = 0,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val isSelectionMode: Boolean = false,
    val selectedConversationIds: Set<Long> = emptySet()
)

/**
 * UI Events for Messages Screen
 */
sealed class MessagesUiEvent {
    data object InitializeScreen : MessagesUiEvent()
    data class SearchQueryChanged(val query: String) : MessagesUiEvent()
    data class ConversationSelected(val conversationId: Long) : MessagesUiEvent()
    data class DeleteConversation(val conversationId: Long) : MessagesUiEvent()
    data class MarkAsRead(val conversationId: Long) : MessagesUiEvent()
    data class ArchiveConversation(val conversationId: Long) : MessagesUiEvent()
    data class TabChanged(val tab: MessageTab) : MessagesUiEvent()
    data object DismissError : MessagesUiEvent()
    data object NewMessage : MessagesUiEvent()
    data object ClearAllMessages : MessagesUiEvent()
    // Multi-select events
    data class ConversationLongPressed(val conversationId: Long) : MessagesUiEvent()
    data class ToggleConversationSelection(val conversationId: Long) : MessagesUiEvent()
    data object ExitSelectionMode : MessagesUiEvent()
    data object SelectAllConversations : MessagesUiEvent()
    data object DeselectAllConversations : MessagesUiEvent()
    data object DeleteSelectedConversations : MessagesUiEvent()
    data object ArchiveSelectedConversations : MessagesUiEvent()
    data object MarkSelectedAsRead : MessagesUiEvent()
    data object RefreshConversations : MessagesUiEvent()
}
