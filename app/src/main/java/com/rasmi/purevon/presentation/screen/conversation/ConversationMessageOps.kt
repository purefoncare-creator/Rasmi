package com.rasmi.purevon.presentation.screen.conversation

import com.rasmi.purevon.domain.usecase.message.*
import com.rasmi.purevon.util.message.MessageRetryManager
import javax.inject.Inject

/**
 * Aggregate holder for message-related use cases.
 * Injected into [ConversationViewModel] to reduce constructor parameter count (Finding D).
 */
class ConversationMessageOps @Inject constructor(
    val getMessagesByThread: GetMessagesByThreadUseCase,
    val getMessagesByThreadPaged: GetMessagesByThreadPagedUseCase,
    val getAddressFromThreadId: GetAddressFromThreadIdUseCase,
    val markThreadAsRead: MarkThreadAsReadUseCase,
    val deleteConversation: DeleteConversationUseCase,
    val sendMessage: SendMessageUseCase,
    val sendMmsMessage: SendMmsMessageUseCase,
    val retryFailedMessage: RetryFailedMessageUseCase,
    val deleteMessage: DeleteMessageUseCase,
    val toggleMessageStarred: ToggleMessageStarredUseCase,
    val getOrCreateThreadId: GetOrCreateThreadIdUseCase,
    val syncThreadMessages: SyncThreadMessagesUseCase,
    val getMessageChangeEvents: GetMessageChangeEventsUseCase,
    val messageRetryManager: MessageRetryManager,
)
