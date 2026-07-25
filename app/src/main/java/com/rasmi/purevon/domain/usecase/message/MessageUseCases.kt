package com.rasmi.purevon.domain.usecase.message

import com.rasmi.purevon.domain.model.MessageCategory
import com.rasmi.purevon.domain.model.Conversation
import com.rasmi.purevon.domain.model.Message
import com.rasmi.purevon.domain.model.MessageResult
import com.rasmi.purevon.domain.repository.MessageRepository
import android.util.Log
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case to get all conversations
 */
class GetAllConversationsUseCase @Inject constructor(
    private val repository: MessageRepository
) {
    operator fun invoke(): Flow<List<Conversation>> {
        return repository.getAllConversations()
    }
}

/**
 * Use case to get messages by thread
 */
class GetMessagesByThreadUseCase @Inject constructor(
    private val repository: MessageRepository
) {
    operator fun invoke(threadId: Long): Flow<List<Message>> {
        return repository.getMessagesByThread(threadId)
    }
}

/**
 * Use case to get messages by category
 */
class GetMessagesByCategoryUseCase @Inject constructor(
    private val repository: MessageRepository
) {
    operator fun invoke(category: MessageCategory): Flow<List<Message>> {
        return repository.getMessagesByCategory(category)
    }
}

/**
 * Use case to get unread messages
 */
class GetUnreadMessagesUseCase @Inject constructor(
    private val repository: MessageRepository
) {
    operator fun invoke(): Flow<List<Message>> {
        return repository.getUnreadMessages()
    }
}

/**
 * Use case to get unread message count
 */
class GetUnreadCountUseCase @Inject constructor(
    private val repository: MessageRepository
) {
    operator fun invoke(): Flow<Int> {
        return repository.getUnreadCount()
    }
}

/**
 * Use case to search messages
 */
class SearchMessagesUseCase @Inject constructor(
    private val repository: MessageRepository
) {
    operator fun invoke(query: String): Flow<List<Message>> {
        return repository.searchMessages(query)
    }
}

/**
 * Use case to mark thread as read
 */
class MarkThreadAsReadUseCase @Inject constructor(
    private val repository: MessageRepository
) {
    suspend operator fun invoke(threadId: Long) {
        try {
            repository.markThreadAsRead(threadId)
        } catch (e: Exception) {
            Log.e("MarkThreadAsReadUseCase", "Failed to mark thread $threadId as read", e)
            throw e
        }
    }
}

/**
 * Use case to delete a conversation
 */
class DeleteConversationUseCase @Inject constructor(
    private val repository: MessageRepository
) {
    suspend operator fun invoke(threadId: Long) {
        try {
            repository.deleteThread(threadId)
        } catch (e: Exception) {
            Log.e("DeleteConversationUseCase", "Failed to delete thread $threadId", e)
            throw e
        }
    }
}

/**
 * Use case to pin/unpin conversation
 */
class ToggleConversationPinUseCase @Inject constructor(
    private val repository: MessageRepository
) {
    suspend operator fun invoke(threadId: Long, isPinned: Boolean) {
        repository.setConversationPinned(threadId, isPinned)
    }
}

/**
 * Use case to sync messages with system
 * Enhanced with Quik-inspired full sync strategy
 */
class SyncMessagesUseCase @Inject constructor(
    private val repository: MessageRepository,
    private val syncRepository: com.rasmi.purevon.domain.repository.SyncRepository
) {
    /**
     * Perform full sync of all messages (first-time only)
     * Shows progress for UX
     */
    suspend fun performFullSync() {
        syncRepository.performFullSync()
    }
    
    /**
     * Sync a specific conversation
     */
    suspend fun syncConversation(threadId: Long) {
        syncRepository.syncConversation(threadId)
    }
    
    /**
     * Sync only conversations (fast)
     */
    suspend fun syncConversationsOnly() {
        syncRepository.syncConversationsOnly()
    }
    
    /**
     * Check if initial sync has been completed
     */
    suspend fun hasCompletedInitialSync(): Boolean {
        return syncRepository.hasCompletedInitialSync()
    }
    
    /**
     * Observe sync progress
     */
    fun observeSyncProgress(): kotlinx.coroutines.flow.Flow<com.rasmi.purevon.domain.repository.SyncRepository.SyncProgress> {
        return syncRepository.syncProgress
    }
    
    /**
     * Cancel ongoing sync
     */
    fun cancelSync() {
        syncRepository.cancelSync()
    }
    
    /**
     * Legacy method - sync with system (old approach)
     */
    suspend operator fun invoke() {
        repository.syncWithSystemMessages()
    }
}

/**
 * Use case to search conversations
 */
class SearchConversationsUseCase @Inject constructor(
    private val repository: MessageRepository
) {
    operator fun invoke(query: String): Flow<List<Conversation>> {
        return repository.searchConversations(query)
    }
}

/**
 * Use case to get starred/favorited messages
 */
class GetStarredMessagesUseCase @Inject constructor(
    private val repository: MessageRepository
) {
    operator fun invoke(): Flow<List<Message>> {
        return repository.getStarredMessages()
    }
}

/**
 * Use case to get archived conversations
 */
class GetArchivedConversationsUseCase @Inject constructor(
    private val repository: MessageRepository
) {
    operator fun invoke(): Flow<List<Conversation>> {
        return repository.getArchivedConversations()
    }
}

