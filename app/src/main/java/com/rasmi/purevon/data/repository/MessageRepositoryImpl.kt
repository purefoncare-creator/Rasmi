package com.rasmi.purevon.data.repository

import android.content.ContentValues
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import android.provider.Telephony
import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.rasmi.purevon.util.DebugLogger
import com.rasmi.purevon.data.local.dao.ConversationPreferencesDao
import com.rasmi.purevon.data.local.dao.MessageMetadataDao
import com.rasmi.purevon.data.local.entity.MessageCategory
import com.rasmi.purevon.data.paging.ConversationPagingSourceFactory
import com.rasmi.purevon.data.paging.MessagePagingSource
import com.rasmi.purevon.domain.model.Conversation
import com.rasmi.purevon.domain.model.Message
import com.rasmi.purevon.domain.model.MessageError
import com.rasmi.purevon.domain.model.MessageResult
import com.rasmi.purevon.domain.repository.MessageRepository
import com.rasmi.purevon.data.mapper.toDomain
import com.rasmi.purevon.data.mapper.toEntity
import com.rasmi.purevon.util.mms.ApnManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of MessageRepository
 * Reads directly from system Telephony.Sms (ContentProvider)
 * Stores only metadata (spam scores, categories) locally
 * 
 * Thread Safety: Uses Mutex to protect critical operations
 * Performance: Implements caching layer to reduce ContentProvider queries
 * 
 * ✅ OPTIMIZED: Cache Layer + Singleton ContentObserver (Dec 2025)
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
class MessageRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messageMetadataDao: MessageMetadataDao,
    private val conversationPreferencesDao: ConversationPreferencesDao,
    private val cachedConversationDao: com.rasmi.purevon.data.local.dao.CachedConversationDao, // ✅ NEW: Persistent Cache
    private val cachedMessageDao: com.rasmi.purevon.data.local.dao.CachedMessageDao, // ✅ NEW: Persistent Cache
    private val scheduledMessageDao: com.rasmi.purevon.data.local.dao.ScheduledMessageDao,
    private val messageTemplateDao: com.rasmi.purevon.data.local.dao.MessageTemplateDao,
    private val messagePagingSourceFactory: MessagePagingSource.Factory,
    private val conversationPagingSourceFactory: ConversationPagingSourceFactory,
    private val imageCompressor: com.rasmi.purevon.util.ImageCompressor, // ✅ جديد: ضغط الصور
    private val simManager: com.rasmi.purevon.util.sim.SimManager, // ✅ تحديد الشريحة بشكل صحيح
    private val apnManager: ApnManager // ✅ APN detection for MMS
) : MessageRepository {
    
    companion object {
        private const val TAG = "MessageRepository"
    }
    
    // ============================================
    // Mutexes for thread-safe operations
    // ============================================
    
    private val sendMessageMutex = Mutex()
    private val databaseMutex = Mutex()
    
    // ✅ Repository Scope for background operations
    private val repositoryScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    
    // ============================================
    // Delegate Helpers (extracted from God class)
    // ============================================
    
    private val contactResolver = ContactResolver(context)
    @androidx.annotation.VisibleForTesting
    internal val systemQueryHelper = SystemQueryHelper(context, contactResolver, simManager, messageMetadataDao, conversationPreferencesDao)
    private val smsSender = SmsSender(context, cachedMessageDao, messageMetadataDao, repositoryScope)
    
    // ✅ Sync delegate: ContentObserver lifecycle + sync/cache operations
    private val syncDelegate = MessageSyncDelegate(
        context, cachedConversationDao, cachedMessageDao, systemQueryHelper,
        simManager, contactResolver, repositoryScope
    )
    
    private val mmsSender = MmsSender(context, imageCompressor, apnManager, setSendInProgress = { syncDelegate.mmsSendInProgress = it })
    
    // ✅ Template delegate: CRUD for message templates
    private val templateDelegate = MessageTemplateDelegate(messageTemplateDao)
    
    // ✅ Thread address resolution delegate
    private val threadAddressResolver = ThreadAddressResolver(context)
    
    // ✅ Conversation preferences delegate
    private val conversationPrefsDelegate = ConversationPrefsDelegate(
        context, conversationPreferencesDao, cachedConversationDao, syncDelegate
    )
    
    // ✅ Read status delegate
    private val readStatusDelegate = MessageReadStatusDelegate(context, syncDelegate, cachedConversationDao, cachedMessageDao)
    
    // ✅ Initial sync tracking
    private var initialSyncCompleted = false
    private val _isInitialSyncInProgress = MutableStateFlow(false)
    val isInitialSyncInProgress: StateFlow<Boolean> = _isInitialSyncInProgress.asStateFlow()
    
    init {
        syncDelegate.registerObservers()
        // Refresh all cached contact names on startup (fixes stale names from before bug fixes)
        repositoryScope.launch {
            try {
                syncDelegate.refreshCachedContactNames()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh contact names on startup", e)
            }
        }
    }
    
    /**
     * Cleanup method - unregisters observers and cancels repositoryScope.
     * Called via ProcessLifecycleOwner when app process terminates.
     */
    override fun cleanup() {
        syncDelegate.cleanup()
        // DO NOT cancel repositoryScope here, as the app needs to process incoming messages
        // even when in the background. The scope will be destroyed when the app process dies.
        // repositoryScope.coroutineContext[Job]?.cancelChildren()
    }
    
    // ============================================
    // Optimized Flow Methods with Cache
    // ============================================
    
    // ✅ ALWAYS SYNC: Emit from Room cache AND trigger a background sync every time.
    // Room's live Flow auto-emits when insertAll completes, so the UI updates seamlessly.
    // First launch: blocking sync so user sees data immediately.
    // Subsequent calls: non-blocking background sync to pick up new/missing conversations.
    override fun getAllConversations(): Flow<List<Conversation>> {
        repositoryScope.launch {
            try {
                if (cachedConversationDao.getCount() == 0) {
                    DebugLogger.d(TAG, "Cache empty — blocking sync for first load...")
                    syncDelegate.syncConversationsBlocking()
                } else {
                    // ✅ Always re-sync in background to pick up ALL conversations
                    // (fixes stale cache from old MAX_CONVERSATIONS limit)
                    DebugLogger.d(TAG, "Cache has data — background sync to refresh...")
                    syncDelegate.syncConversations()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Background sync failed", e)
            }
        }
        return cachedConversationDao.getAllConversations()
            .map { entities -> entities.map { it.toDomain() } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
    }
    
    // ============================================
    // Sync Methods — delegated to MessageSyncDelegate
    // ============================================
    
    override suspend fun syncMessages(threadId: Long) = syncDelegate.syncMessages(threadId)

    override suspend fun syncAllMessages() = syncDelegate.syncAllMessages()
    
    override suspend fun hasCachedConversations(): Boolean = syncDelegate.hasCachedConversations()
    
    /**
     * Sync a single message by its content URI — like QKSMS syncMessage(uri).
     * Reads only ONE row from Telephony and upserts it into Room.
     * O(1) — much faster than syncMessages(threadId) which rewrites the whole thread.
     */
    override suspend fun syncMessage(uri: android.net.Uri) = syncDelegate.syncMessage(uri)
    
    fun invalidateMessageCache(threadId: Long) = syncDelegate.invalidateMessageCache(threadId)
    
    fun invalidateAllCaches() = syncDelegate.invalidateAllCaches()
    
    /** Refresh all cached contact names (conversations + messages) from system contacts. */
    suspend fun refreshCachedContactNames() = syncDelegate.refreshCachedContactNames()
    
    // ============================================
    // Query Methods (Room + System)
    // ============================================
    
    override fun searchConversations(query: String): Flow<List<Conversation>> = 
        cachedConversationDao.searchConversations(query)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
            .distinctUntilChanged()
    
    override fun getAllMessages(): Flow<List<Message>> = 
        cachedMessageDao.getAllMessages()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
            .distinctUntilChanged()

    override fun getTotalMessageCount(): Flow<Int> =
        cachedMessageDao.getTotalMessageCount()
            .flowOn(Dispatchers.IO)
            .distinctUntilChanged()

    override fun getMessageCountByType(type: Int): Flow<Int> =
        cachedMessageDao.getMessageCountByType(type)
            .flowOn(Dispatchers.IO)
            .distinctUntilChanged()
    
    override fun getArchivedConversations(): Flow<List<Conversation>> = 
        cachedConversationDao.getArchivedConversations()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    
    override fun getMessagesByThread(threadId: Long): Flow<List<Message>> = flow {
        val cached = cachedMessageDao.getMessagesByThread(threadId).first()
        
        if (cached.isEmpty()) {
            DebugLogger.d(TAG, "📥 First load for thread $threadId - syncing...")
            syncDelegate.syncMessagesBlocking(threadId)
        } else {
            DebugLogger.d(TAG, "✅ Cache hit for thread $threadId (${cached.size} msgs) - background sync")
            syncDelegate.syncMessagesBackground(threadId)
        }
        
        emitAll(
            combine(
                cachedMessageDao.getMessagesByThread(threadId),
                messageMetadataDao.getStarredMessagesMetadata()
            ) { entities, starredMetadata ->
                val starredIds = starredMetadata.map { it.systemMessageId }.toSet()
                entities.map { entity -> entity.toDomain().copy(isStarred = entity.id in starredIds) }
            }
        )
    }.flowOn(Dispatchers.IO)
    
    override fun getMessagesByThreadPaged(threadId: Long): Flow<PagingData<Message>> {
        return Pager(
            config = PagingConfig(
                pageSize = 50,
                prefetchDistance = 10,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { messagePagingSourceFactory.create(threadId) }
        ).flow
    }
    
    override fun getAllConversationsPaged(query: String): Flow<PagingData<Conversation>> {
        return Pager(
            config = PagingConfig(
                pageSize = 30,
                prefetchDistance = 5,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { conversationPagingSourceFactory.create(query) }
        ).flow
    }
    
    override fun getMessagesByCategory(category: MessageCategory): Flow<List<Message>> = 
        syncDelegate.messageUpdates
            .onStart { emit(Unit) }
            .debounce(300)
            .map {
                val result: List<Message> = withContext(Dispatchers.IO) {
                    // Query metadata IDs first from Room, then load only matching messages
                    val metadataList = messageMetadataDao.getMetadataByCategorySuspend(category)
                    if (metadataList.isEmpty()) {
                        emptyList()
                    } else {
                        val ids = metadataList.map { it.systemMessageId }
                        systemQueryHelper.querySystemMessages(messageIds = ids)
                    }
                }
                result
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    
    override fun getUnreadMessages(): Flow<List<Message>> = 
        syncDelegate.messageUpdates
            .onStart { emit(Unit) }
            .debounce(300)
            .map {
                val result: List<Message> = withContext(Dispatchers.IO) {
                    systemQueryHelper.querySystemMessages(unreadOnly = true)
                }
                result
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    
    override fun getUnreadCount(): Flow<Int> = 
        syncDelegate.messageUpdates
            .onStart { emit(Unit) }
            .debounce(50) // ✅ OPTIMIZED: Fast unread count updates
            .map {
                val result: Int = withContext(Dispatchers.IO) {
                    // Use optimized query
                    systemQueryHelper.queryUnreadCountOptimized()
                }
                result
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    override fun searchMessages(query: String): Flow<List<Message>> = 
        syncDelegate.messageUpdates
            .onStart { emit(Unit) }
            .debounce(300)
            .map {
                val result: List<Message> = withContext(Dispatchers.IO) {
                    systemQueryHelper.querySystemMessages(searchQuery = query)
                }
                result
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    
    override suspend fun deleteThread(threadId: Long) = conversationPrefsDelegate.deleteThread(threadId)
    
    override suspend fun deleteMessage(messageId: Long) {
        context.contentResolver.delete(
            Telephony.Sms.CONTENT_URI,
            "${Telephony.Sms._ID} = ?",
            arrayOf(messageId.toString())
        )
        
        messageMetadataDao.deleteMetadata(messageId)
        // ✅ FIX #6: Also delete from Room cache so deleted message doesn't reappear
        cachedMessageDao.deleteById(messageId)
    }
    
    override suspend fun setConversationPinned(threadId: Long, isPinned: Boolean) =
        conversationPrefsDelegate.setConversationPinned(threadId, isPinned)
    
    override suspend fun setConversationMuted(threadId: Long, isMuted: Boolean) =
        conversationPrefsDelegate.setConversationMuted(threadId, isMuted)
    
    override suspend fun setConversationArchived(threadId: Long, isArchived: Boolean) =
        conversationPrefsDelegate.setConversationArchived(threadId, isArchived)
    
    
    override suspend fun sendMessage(phoneNumber: String, message: String, simSlot: Int?): MessageResult<Long> = sendMessageMutex.withLock {
        smsSender.sendSms(phoneNumber, message, simSlot) { threadId ->
            syncMessages(threadId)
        }
    }
    
    override suspend fun sendMmsMessage(
        phoneNumber: String,
        message: String?,
        attachmentUris: List<String>,
        simSlot: Int?
    ): MessageResult<Long> = sendMessageMutex.withLock {
        // ✅ FIX #38: Protected by same Mutex as sendMessage to prevent race conditions
        mmsSender.sendMms(
            phoneNumber, message, attachmentUris, simSlot,
            getOrCreateThreadId = { smsSender.getOrCreateThreadId(it) },
            onComplete = { threadId ->
                syncMessages(threadId)
                syncDelegate.syncConversations()
            }
        )
    }
    
    /**
     * ✅ NEW: Retry failed message
     * Deletes the failed message and resends it
     */
    override suspend fun retryFailedMessage(messageId: Long): MessageResult<Long> = sendMessageMutex.withLock {
        // ✅ FIX M15: Protected by sendMessageMutex to prevent duplicate retry
        try {
            // Get the failed message
            val message = getMessageById(messageId)
            if (message == null) {
                Log.e(TAG, "Message $messageId not found for retry")
                return MessageResult.Failure(
                    MessageError.DatabaseError("getMessageById", "Message not found")
                )
            }
            
            // Verify it's actually failed
            if (message.type != com.rasmi.purevon.data.local.entity.MessageType.FAILED.value) {
                Log.w(TAG, "Message $messageId is not in failed state (type=${message.type})")
                return MessageResult.Failure(
                    MessageError.InvalidNumberError(
                        message.phoneNumber, 
                        "Message is not in failed state"
                    )
                )
            }
            
            DebugLogger.d(TAG, "🔄 Retrying failed message $messageId to ${DebugLogger.maskPhoneNumber(message.phoneNumber)}")
            
            // ✅ FIX M3: Resend FIRST, only delete old message on success
            // Previously deleted before resend — if resend failed, message was permanently lost
            val result = if (message.isMms) {
                sendMmsMessage(
                    phoneNumber = message.phoneNumber,
                    message = message.body,
                    attachmentUris = message.attachmentUris,
                    simSlot = message.simSlot
                )
            } else {
                sendMessage(
                    phoneNumber = message.phoneNumber,
                    message = message.body ?: "",
                    simSlot = message.simSlot
                )
            }
            
            result.onSuccess { newMessageId ->
                // Only delete the old failed message after successful resend
                deleteMessage(messageId)
                invalidateMessageCache(message.threadId)
                DebugLogger.d(TAG, "✅ Message retried successfully, new ID: $newMessageId")
            }.onFailure { error ->
                Log.e(TAG, "❌ Retry failed: ${error.message}")
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error retrying message $messageId", e)
            MessageResult.Failure(MessageError.UnknownError(e))
        }
    }
    
    override suspend fun syncWithSystemMessages() {
        // Deprecated - we read directly from system
    }
    
    // ✅ NEW: Sync all conversations - called by SyncRepository
    override suspend fun syncAllConversations() {
        syncDelegate.syncConversationsBlocking()
    }

    override fun forceSyncConversations() {
        syncDelegate.forceSyncConversations()
    }
    
    override suspend fun getMessageById(messageId: Long): Message? {
        var message: Message? = null
        
        // ✅ FIX: First try SMS, then try MMS if not found
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ,
                Telephony.Sms.SUBSCRIPTION_ID
            ),
            "${Telephony.Sms._ID} = ?",
            arrayOf(messageId.toString()),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val systemId = cursor.getLong(0)
                val phoneNumber = cursor.getString(2) ?: "Unknown"
                val metadata = messageMetadataDao.getMetadata(systemId)
                
                // ✅ FIX S1: Read SUBSCRIPTION_ID to preserve SIM info for retry
                val subIdCol = cursor.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)
                val subId = if (subIdCol >= 0 && !cursor.isNull(subIdCol)) cursor.getInt(subIdCol) else null
                val simSlotValue = subId?.let { simManager.getSlotForSubscriptionId(it) }
                
                // Resolve contact name
                val contactNamesMap = contactResolver.batchResolveContactNamesOptimized(setOf(phoneNumber))
                val contactName = contactNamesMap[phoneNumber]
                
                message = Message(
                    id = systemId,
                    threadId = cursor.getLong(1),
                    phoneNumber = phoneNumber,
                    contactName = contactName,
                    body = cursor.getString(3) ?: "",
                    timestamp = cursor.getLong(4),
                    type = cursor.getInt(5),
                    category = metadata?.category ?: MessageCategory.PERSONAL,
                    isRead = cursor.getInt(6) == 1,
                    isSent = cursor.getInt(5) == 2,
                    isDelivered = true,
                    simSlot = subId,
                    isSpam = (metadata?.spamScore ?: 0f) > 0.5f,
                    spamScore = metadata?.spamScore ?: 0f,
                    isMms = false,
                    attachmentUris = emptyList(),
                    attachmentTypes = emptyList()
                )
            }
        }
        
        // ✅ FIX: If not found in SMS, try MMS content provider
        if (message == null) {
            try {
                val mmsUri = android.net.Uri.parse("content://mms/$messageId")
                context.contentResolver.query(
                    mmsUri,
                    arrayOf("_id", "thread_id", "date", "msg_box", "read", "sub_id"),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val systemId = cursor.getLong(0)
                        val threadId = cursor.getLong(1)
                        val date = cursor.getLong(2) * 1000L // MMS date is in seconds
                        val msgBox = cursor.getInt(3)
                        val isRead = cursor.getInt(4) == 1
                        
                        // ✅ FIX S2: Read sub_id to preserve SIM info for retry
                        val mmsSubIdCol = cursor.getColumnIndex("sub_id")
                        val mmsSubId = if (mmsSubIdCol >= 0 && !cursor.isNull(mmsSubIdCol)) cursor.getInt(mmsSubIdCol) else null
                        
                        // Resolve MMS address using MmsUtils
                        val phoneNumber = com.rasmi.purevon.util.mms.MmsUtils.getMmsAddress(context, systemId) ?: "Unknown"
                        val contactNamesMap = contactResolver.batchResolveContactNamesOptimized(setOf(phoneNumber))
                        val contactName = contactNamesMap[phoneNumber]
                        
                        // Get MMS body text
                        val body = com.rasmi.purevon.util.mms.MmsUtils.getMmsText(context, systemId)
                        
                        // Get MMS attachments inline
                        val attachmentUris = mutableListOf<String>()
                        val attachmentTypes = mutableListOf<String>()
                        try {
                            val partUri = android.net.Uri.parse("content://mms/$systemId/part")
                            context.contentResolver.query(partUri, arrayOf("_id", "ct"), null, null, null)?.use { partCursor ->
                                // ✅ FIX #39: Use getColumnIndexOrThrow instead of hardcoded indices
                                val ctIndex = partCursor.getColumnIndexOrThrow("ct")
                                val idIdx = partCursor.getColumnIndexOrThrow("_id")
                                while (partCursor.moveToNext()) {
                                    val contentType = partCursor.getString(ctIndex)
                                    if (contentType != "text/plain" && contentType != "application/smil") {
                                        val partId = partCursor.getLong(idIdx)
                                        attachmentUris.add("content://mms/part/$partId")
                                        attachmentTypes.add(contentType)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error getting MMS attachments for message $systemId", e)
                        }
                        
                        val metadata = messageMetadataDao.getMetadata(systemId)
                        
                        // MMS msg_box: 1=received, 2=sent, 3=draft, 4=outbox, 5=failed
                        val type = when (msgBox) {
                            1 -> 1  // received
                            2 -> 2  // sent
                            4 -> 4  // outbox (sending)
                            else -> msgBox
                        }
                        
                        message = Message(
                            id = systemId,
                            threadId = threadId,
                            phoneNumber = phoneNumber,
                            contactName = contactName,
                            body = body ?: "",
                            timestamp = date,
                            type = type,
                            category = metadata?.category ?: MessageCategory.PERSONAL,
                            isRead = isRead,
                            isSent = msgBox == 2,
                            isDelivered = true,
                            simSlot = mmsSubId,
                            isSpam = (metadata?.spamScore ?: 0f) > 0.5f,
                            spamScore = metadata?.spamScore ?: 0f,
                            isMms = true,
                            attachmentUris = attachmentUris,
                            attachmentTypes = attachmentTypes
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying MMS for message $messageId", e)
            }
        }
        
        return message
    }
    
    override suspend fun updateMessage(message: Message) {
        // Update system message
        // ✅ FIX M19: Update both SMS and MMS, not just SMS
        if (message.isMms) {
            val mmsValues = ContentValues().apply {
                put(Telephony.Mms.READ, if (message.isRead) 1 else 0)
            }
            context.contentResolver.update(
                android.net.Uri.parse("content://mms"),
                mmsValues,
                "${Telephony.Mms._ID} = ?",
                arrayOf(message.id.toString())
            )
        } else {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, if (message.isRead) 1 else 0)
                put(Telephony.Sms.BODY, message.body)
            }
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms._ID} = ?",
                arrayOf(message.id.toString())
            )
        }
        
        // Update metadata
        val metadata = messageMetadataDao.getMetadata(message.id)
        if (metadata != null) {
            messageMetadataDao.updateMetadata(
                metadata.copy(
                    spamScore = message.spamScore,
                    category = message.category,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }
    
    // ✅ FIX #9: Clear ALL messages (SMS + MMS + cache + metadata)
    override suspend fun clearAllMessages() {
        // Delete SMS from system
        context.contentResolver.delete(
            Telephony.Sms.CONTENT_URI,
            null,
            null
        )
        // Delete MMS from system
        try {
            context.contentResolver.delete(
                android.net.Uri.parse("content://mms"),
                null,
                null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting MMS messages", e)
        }
        // Clean Room caches
        cachedMessageDao.clearAll()
        // ✅ FIX M20: Also clear cached conversations to prevent ghost entries
        cachedConversationDao.clearAll()
        messageMetadataDao.deleteAll()
    }
    
    override suspend fun deleteConversation(threadId: Long) =
        conversationPrefsDelegate.deleteConversation(threadId)
    
    override suspend fun scheduleMessage(
        phoneNumber: String,
        message: String,
        scheduledTimeMillis: Long,
        attachmentUris: List<String>,
        simSlot: Int?,
        repeatInterval: com.rasmi.purevon.domain.model.RepeatInterval
    ): Long {
        val scheduledMessage = com.rasmi.purevon.data.local.entity.ScheduledMessageEntity(
            recipient = phoneNumber,
            messageBody = message,
            scheduledTime = scheduledTimeMillis,
            status = com.rasmi.purevon.data.local.entity.ScheduleStatus.PENDING,
            simSlot = simSlot,
            repeatInterval = repeatInterval.toEntity(),
            attachmentUris = attachmentUris
        )
        return scheduledMessageDao.insert(scheduledMessage)
    }
    
    override suspend fun cancelScheduledMessage(scheduleId: Long) {
        scheduledMessageDao.updateStatus(
            scheduleId,
            com.rasmi.purevon.data.local.entity.ScheduleStatus.CANCELLED
        )
    }
    
    override suspend fun toggleMessageStarred(messageId: Long) = databaseMutex.withLock {
        val metadata = messageMetadataDao.getMetadata(messageId)
        if (metadata != null) {
            messageMetadataDao.updateStarred(messageId, !metadata.isStarred)
        } else {
            // Create new metadata with starred = true
            messageMetadataDao.insertMetadata(
                com.rasmi.purevon.data.local.entity.MessageMetadataEntity(
                    systemMessageId = messageId,
                    isStarred = true
                )
            )
        }
    }
    
    override fun getStarredMessages(): Flow<List<Message>> = callbackFlow {
        messageMetadataDao.getStarredMessagesMetadata().collect { starredMetadata ->
            val starredMessageIds = starredMetadata.map { it.systemMessageId }
            if (starredMessageIds.isEmpty()) {
                trySend(emptyList())
            } else {
                // Batch query from Room cache instead of N individual ContentProvider queries
                val cachedEntities = cachedMessageDao.getMessagesByIds(starredMessageIds)
                val messages = cachedEntities.map { it.toDomain().copy(isStarred = true) }
                trySend(messages)
            }
        }
        awaitClose { }
    }
    
    override suspend fun getOrCreateThreadIdForNumber(phoneNumber: String): Long {
        return smsSender.getOrCreateThreadId(phoneNumber)
    }
    
    override suspend fun getAddressFromThreadId(threadId: Long): String? =
        threadAddressResolver.getAddressFromThreadId(threadId)
    
    override suspend fun markMessageAsRead(messageId: Long) =
        readStatusDelegate.markMessageAsRead(messageId)
    
    override suspend fun markThreadAsRead(threadId: Long) =
        readStatusDelegate.markThreadAsRead(threadId)
    
    override suspend fun archiveConversation(threadId: Long) =
        conversationPrefsDelegate.archiveConversation(threadId)
    
    /**
     * إطلاق إشعار بتحديث المحادثات
     * ✅ يطلق تحديث فوري للـ UI و PagingSource
     */
    override suspend fun notifyConversationsChanged() {
        try {
            // ✅ FIX: Don't wipe the cache — just trigger a background re-sync.
            // Calling invalidateAllCaches() (clearAll) caused the list to flash empty.
            syncDelegate.syncConversations()
            DebugLogger.d(TAG, "✅ Conversations update notification sent")
        } catch (e: Exception) {
            Log.e(TAG, "Error notifying conversations changed", e)
        }
    }
    
    // ============================================
    // Template Methods — delegated to MessageTemplateDelegate
    // ============================================
    
    override fun getAllTemplates(): Flow<List<com.rasmi.purevon.domain.model.MessageTemplate>> =
        templateDelegate.getAllTemplates().map { list -> list.map { it.toDomain() } }
    
    override suspend fun addTemplate(template: com.rasmi.purevon.domain.model.MessageTemplate) {
        templateDelegate.addTemplate(template.toEntity())
    }
    
    override suspend fun updateTemplateUsage(templateId: Long) {
        templateDelegate.updateTemplateUsage(templateId)
    }
    
    override suspend fun deleteTemplate(template: com.rasmi.purevon.domain.model.MessageTemplate) =
        templateDelegate.deleteTemplate(template.toEntity())

    override fun getMessageChangeEvents(): Flow<Unit> = syncDelegate.messageUpdates
}
