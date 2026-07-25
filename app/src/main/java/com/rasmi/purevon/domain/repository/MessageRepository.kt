package com.rasmi.purevon.domain.repository

import androidx.paging.PagingData
import com.rasmi.purevon.domain.model.MessageCategory
import com.rasmi.purevon.domain.model.Conversation
import com.rasmi.purevon.domain.model.Message
import com.rasmi.purevon.domain.model.MessageResult
import com.rasmi.purevon.domain.model.MessageTemplate
import com.rasmi.purevon.domain.model.RepeatInterval
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Message operations
 */
interface MessageRepository {
    
    fun getAllConversations(): Flow<List<Conversation>>
    
    fun searchConversations(query: String): Flow<List<Conversation>>
    
    fun getAllConversationsPaged(query: String = ""): Flow<PagingData<Conversation>>
    
    fun getAllMessages(): Flow<List<Message>>

    fun getTotalMessageCount(): Flow<Int>

    fun getMessageCountByType(type: Int): Flow<Int>
    
    fun getArchivedConversations(): Flow<List<Conversation>>
    
    fun getMessagesByThread(threadId: Long): Flow<List<Message>>
    
    fun getMessagesByThreadPaged(threadId: Long): Flow<PagingData<Message>>
    
    fun getMessagesByCategory(category: MessageCategory): Flow<List<Message>>
    
    fun getUnreadMessages(): Flow<List<Message>>
    
    fun getUnreadCount(): Flow<Int>
    
    fun searchMessages(query: String): Flow<List<Message>>
    
    suspend fun markThreadAsRead(threadId: Long)
    
    suspend fun markMessageAsRead(messageId: Long)
    
    suspend fun deleteThread(threadId: Long)
    
    suspend fun deleteMessage(messageId: Long)
    
    suspend fun setConversationPinned(threadId: Long, isPinned: Boolean)
    
    suspend fun setConversationMuted(threadId: Long, isMuted: Boolean)
    
    suspend fun setConversationArchived(threadId: Long, isArchived: Boolean)
    
    /**
     * Notify observers that conversations have changed
     * Used to trigger immediate UI updates
     */
    suspend fun notifyConversationsChanged()
    
    suspend fun sendMessage(phoneNumber: String, message: String, simSlot: Int?): MessageResult<Long>
    
    suspend fun sendMmsMessage(phoneNumber: String, message: String?, attachmentUris: List<String>, simSlot: Int?): MessageResult<Long>
    
    // ✅ NEW: Retry failed message
    suspend fun retryFailedMessage(messageId: Long): MessageResult<Long>
    
    suspend fun getMessageById(messageId: Long): Message?
    
    suspend fun updateMessage(message: Message)
    
    suspend fun syncWithSystemMessages()
    
    /**
     * Sync all conversations from system
     * Used by SyncRepository
     */
    suspend fun syncAllConversations()

    /**
     * Force sync conversations immediately, bypassing throttle.
     * Used when a new SMS is received to ensure the conversation list updates instantly.
     */
    fun forceSyncConversations()
    
    /**
     * Sync messages for a specific thread
     * Used by SyncRepository for incremental sync
     */
    suspend fun syncMessages(threadId: Long)

    /**
     * Sync ALL messages in ONE bulk ContentProvider query.
     * Inspired by Quik (QKSMS): opens a single messageCursor for all threads
     * instead of one query per thread (N+1 problem).
     * Use this for the initial full sync — O(1) queries regardless of thread count.
     */
    suspend fun syncAllMessages()

    /** Returns true if the local Room cache contains at least one conversation. */
    suspend fun hasCachedConversations(): Boolean

    suspend fun clearAllMessages()
    
    suspend fun deleteConversation(threadId: Long)
    
    suspend fun scheduleMessage(
        phoneNumber: String, 
        message: String, 
        scheduledTimeMillis: Long,
        attachmentUris: List<String> = emptyList(),
        simSlot: Int? = null,
        repeatInterval: RepeatInterval = RepeatInterval.NONE
    ): Long
    
    suspend fun cancelScheduledMessage(scheduleId: Long)
    
    // Starred/Favorite messages
    suspend fun toggleMessageStarred(messageId: Long)
    
    fun getStarredMessages(): Flow<List<Message>>
    
    // Get or create thread ID for phone number
    suspend fun getOrCreateThreadIdForNumber(phoneNumber: String): Long
    
    // إضافات جديدة للإشعارات والإجراءات السريعة
    suspend fun getAddressFromThreadId(threadId: Long): String?
    
    suspend fun archiveConversation(threadId: Long)
    
    // Message Templates
    fun getAllTemplates(): Flow<List<MessageTemplate>>
    
    suspend fun addTemplate(template: MessageTemplate)
    
    suspend fun updateTemplateUsage(templateId: Long)
    
    suspend fun deleteTemplate(template: MessageTemplate)

    // ✅ NEW: Observe global message changes (triggers UI refresh for active threads)
    fun getMessageChangeEvents(): Flow<Unit>

    /**
     * Sync a single message by its Telephony URI (e.g. content://sms/inbox/42).
     * Much faster than syncMessages(threadId) — only reads & upserts one record.
     * Inspired by QKSMS SyncRepository.syncMessage(uri).
     */
    suspend fun syncMessage(uri: android.net.Uri)

    /**
     * Cleanup resources (ContentObservers, coroutine scope).
     * Called when the app process is being destroyed.
     */
    fun cleanup() {}
}
