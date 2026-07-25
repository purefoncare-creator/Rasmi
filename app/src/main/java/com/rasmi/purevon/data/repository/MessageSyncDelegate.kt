package com.rasmi.purevon.data.repository

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import com.rasmi.purevon.data.mapper.toEntity
import com.rasmi.purevon.util.DebugLogger
import com.rasmi.purevon.util.sim.SimManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Handles message/conversation sync, cache management, and ContentObserver lifecycle.
 * Extracted from MessageRepositoryImpl to reduce class size (~300 lines).
 *
 * Responsibilities:
 * - ContentObserver registration/unregistration
 * - Debounced sync handlers for conversation and message changes
 * - All sync methods (conversations, messages, single message)
 * - Cache invalidation
 */
@OptIn(FlowPreview::class)
internal class MessageSyncDelegate(
    private val context: Context,
    private val cachedConversationDao: com.rasmi.purevon.data.local.dao.CachedConversationDao,
    private val cachedMessageDao: com.rasmi.purevon.data.local.dao.CachedMessageDao,
    private val systemQueryHelper: SystemQueryHelper,
    private val simManager: SimManager,
    private val contactResolver: ContactResolver,
    private val repositoryScope: CoroutineScope,
) {
    companion object {
        private const val TAG = "MessageRepository"
        private const val MIN_SYNC_INTERVAL_MS = 2000L
    }

    // ============================================
    // Shared Flow for message updates
    // ============================================
    private val _messageUpdates = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messageUpdates: SharedFlow<Unit> = _messageUpdates.asSharedFlow()

    /** Emit a message-update event (used by parent and other delegates). */
    fun emitMessageUpdate() {
        _messageUpdates.tryEmit(Unit)
    }

    // ============================================
    // Sync Guards & Throttling
    // ============================================
    private val syncStateMutex = Mutex()
    private var conversationSyncInProgress = false
    // ✅ FIX #59: Track pending sync requests to avoid dropping updates
    private var pendingSyncRequested = false
    private var lastConversationSyncTime = 0L
    // ✅ Handle for the delayed pending sync job
    private var pendingSyncDelayJob: kotlinx.coroutines.Job? = null

    // Debouncing for ContentObserver
    // ✅ FIX M24: Use incrementing counter instead of System.currentTimeMillis()
    // MutableStateFlow deduplicates same values — two events in same ms would be lost
    private val syncCounter = java.util.concurrent.atomic.AtomicLong(0L)
    private val conversationSyncJob = MutableStateFlow<Long>(0L)
    private val messageSyncJob = MutableStateFlow<Long>(0L)

    // Suppress observer during MMS send to prevent cascade
    @Volatile
    var mmsSendInProgress = false

    // ============================================
    // Singleton ContentObservers
    // ============================================
    private val conversationObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            DebugLogger.d(TAG, "📬 Conversation change detected - debouncing...")
            conversationSyncJob.value = syncCounter.incrementAndGet()
        }
    }

    private val messageObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            if (mmsSendInProgress) return
            val counter = syncCounter.incrementAndGet()
            messageSyncJob.value = counter
            // Also trigger conversation list refresh
            conversationSyncJob.value = counter
        }
    }

    private val contactsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            DebugLogger.d(TAG, "👤 Contacts change detected - debouncing...")
            conversationSyncJob.value = System.currentTimeMillis()
            // Also refresh cached message contact names
            repositoryScope.launch {
                refreshCachedContactNames()
            }
        }
    }

    // ============================================
    // Lifecycle
    // ============================================

    /** Register all ContentObservers and set up debounced sync handlers. Call once from init. */
    fun registerObservers() {
        try {
            context.contentResolver.registerContentObserver(
                Telephony.Sms.Conversations.CONTENT_URI, true, conversationObserver
            )
            DebugLogger.d(TAG, "✅ Conversation observer registered")

            context.contentResolver.registerContentObserver(
                Telephony.Sms.CONTENT_URI, true, messageObserver
            )
            DebugLogger.d(TAG, "✅ Message observer registered")

            context.contentResolver.registerContentObserver(
                Uri.parse("content://mms"), true, messageObserver
            )
            DebugLogger.d(TAG, "✅ MMS observer registered")

            context.contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI, true, contactsObserver
            )
            DebugLogger.d(TAG, "✅ Contacts observer registered")

            setupDebouncedSyncHandlers()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to register observers", e)
        }
    }

    /** Unregister all ContentObservers. Call on repository destroy. */
    fun cleanup() {
        try {
            context.contentResolver.unregisterContentObserver(conversationObserver)
            context.contentResolver.unregisterContentObserver(messageObserver)
            context.contentResolver.unregisterContentObserver(contactsObserver)
            DebugLogger.d(TAG, "✅ Observers unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to unregister observers", e)
        }
    }

    private fun setupDebouncedSyncHandlers() {
        DebugLogger.d(TAG, "🔧 Setting up debounced sync handlers...")

        // Debounce conversation changes — 200ms to batch burst changes
        // ✅ FIX M30: 30ms was too aggressive, causing multiple syncs for burst messages
        repositoryScope.launch {
            conversationSyncJob
                .debounce(200)
                .collect {
                    if (it > 0) {
                        DebugLogger.d(TAG, "🔄 Debounce complete - syncing conversations")
                        syncConversations()
                    }
                }
        }

        // Debounce message changes — 50ms, emit _messageUpdates for thread-view refresh
        repositoryScope.launch {
            messageSyncJob
                .debounce(50)
                .collect {
                    if (it > 0) {
                        DebugLogger.d(TAG, "🔄 Debounce complete - emitting message updates")
                        _messageUpdates.tryEmit(Unit)
                    }
                }
        }

        DebugLogger.d(TAG, "✅ Debounced sync handlers ready")
    }

    // ============================================
    // Sync Methods
    // ============================================

    /** Background sync (non-blocking) with throttling. */
    fun syncConversations() {
        repositoryScope.launch {
            // Throttling: Skip if sync in progress or too soon
            syncStateMutex.withLock {
                val now = System.currentTimeMillis()
                if (conversationSyncInProgress) {
                    // ✅ FIX #59: Mark pending instead of silently dropping
                    pendingSyncRequested = true
                    DebugLogger.d(TAG, "⏭️ Sync already in progress - marked pending")
                    return@launch
                }
                if (now - lastConversationSyncTime < MIN_SYNC_INTERVAL_MS) {
                    // ✅ FIX: Schedule a delayed sync instead of silently dropping
                    val delay = MIN_SYNC_INTERVAL_MS - (now - lastConversationSyncTime)
                    if (pendingSyncDelayJob?.isActive != true) {
                        DebugLogger.d(TAG, "⏳ Too soon (${now - lastConversationSyncTime}ms) - scheduling retry in ${delay}ms")
                        pendingSyncDelayJob = repositoryScope.launch {
                            kotlinx.coroutines.delay(delay)
                            syncConversations()
                        }
                    }
                    return@launch
                }

                conversationSyncInProgress = true
                lastConversationSyncTime = now
            }

            try {
                DebugLogger.d(TAG, "🔄 Background sync conversations...")
                val fresh = systemQueryHelper.querySystemConversationsOptimized(includeArchived = true)
                cachedConversationDao.insertAll(fresh.map { it.toEntity() })
                DebugLogger.d(TAG, "✅ Synced ${fresh.size} conversations")
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing conversations", e)
            } finally {
                syncStateMutex.withLock {
                    conversationSyncInProgress = false
                    // ✅ FIX #59 + M6: Re-trigger sync if a request came in during the current sync
                    // Reset lastConversationSyncTime so re-trigger isn't throttled
                    if (pendingSyncRequested) {
                        pendingSyncRequested = false
                        lastConversationSyncTime = 0L
                        DebugLogger.d(TAG, "🔄 Pending sync detected — re-triggering")
                        syncConversations()
                    }
                }
            }
        }
    }

    /**
     * ✅ Force immediate sync — bypasses throttle entirely.
     * Used when we *know* a new SMS arrived (via EventBus) so the user sees the
     * conversation jump to the top instantly.
     */
    fun forceSyncConversations() {
        repositoryScope.launch {
            syncStateMutex.withLock {
                if (conversationSyncInProgress) {
                    pendingSyncRequested = true
                    DebugLogger.d(TAG, "⏭️ Force sync requested but sync in progress — marked pending")
                    return@launch
                }
                conversationSyncInProgress = true
                lastConversationSyncTime = System.currentTimeMillis()
            }

            try {
                DebugLogger.d(TAG, "🔄 Force sync conversations (bypassing throttle)...")
                val fresh = systemQueryHelper.querySystemConversationsOptimized(includeArchived = true)
                cachedConversationDao.insertAll(fresh.map { it.toEntity() })
                DebugLogger.d(TAG, "✅ Force synced ${fresh.size} conversations")
            } catch (e: Exception) {
                Log.e(TAG, "Error in force sync", e)
            } finally {
                syncStateMutex.withLock {
                    conversationSyncInProgress = false
                    if (pendingSyncRequested) {
                        pendingSyncRequested = false
                        lastConversationSyncTime = 0L
                        DebugLogger.d(TAG, "🔄 Pending sync after force — re-triggering")
                        syncConversations()
                    }
                }
            }
        }
    }

    /** Immediate sync (blocking) - used for first load. */
    suspend fun syncConversationsBlocking() {
        withContext(Dispatchers.IO) {
            try {
                DebugLogger.d(TAG, "🔄 Immediate sync conversations...")
                val fresh = systemQueryHelper.querySystemConversationsOptimized(includeArchived = true)
                cachedConversationDao.insertAll(fresh.map { it.toEntity() })
                DebugLogger.d(TAG, "✅ Immediate sync complete: ${fresh.size} conversations")
            } catch (e: Exception) {
                Log.e(TAG, "Error in immediate sync", e)
            }
        }
    }

    /** Sync messages for specific thread (public, used by SyncRepository). */
    suspend fun syncMessages(threadId: Long) {
        withContext(Dispatchers.IO) {
            try {
                DebugLogger.d(TAG, "🔄 Syncing messages for thread $threadId...")
                val fresh = systemQueryHelper.querySystemMessages(threadId)
                cachedMessageDao.updateMessagesForThread(threadId, fresh.map { it.toEntity() })
                DebugLogger.d(TAG, "✅ Synced ${fresh.size} messages for thread $threadId")
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing messages for thread $threadId", e)
            }
        }
    }

    /**
     * Sync ALL messages in ONE bulk query — the Quik approach.
     * Opens a single cursor for all SMS + MMS without filtering by threadId.
     * Batch-inserts into Room using insertAll (OnConflictStrategy.REPLACE).
     */
    suspend fun syncAllMessages() {
        withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                DebugLogger.d(TAG, "🔄 [BULK] Syncing ALL messages in one shot (Quik style)...")
                val allMessages = systemQueryHelper.queryAllSystemMessages()
                if (allMessages.isNotEmpty()) {
                    val entities = allMessages.map { it.toEntity() }
                    val freshIds = entities.map { it.id }.toHashSet()
                    entities.chunked(500).forEach { batch ->
                        cachedMessageDao.insertAll(batch)
                    }
                    // Remove ghost messages that no longer exist in system SMS DB
                    val cachedIds = cachedMessageDao.getAllIds()
                    val staleIds = cachedIds.filter { it !in freshIds }
                    if (staleIds.isNotEmpty()) {
                        staleIds.chunked(500).forEach { batch ->
                            cachedMessageDao.deleteByIds(batch)
                        }
                        DebugLogger.d(TAG, "🗑️ [BULK] Removed ${staleIds.size} ghost messages")
                    }
                }
                val duration = System.currentTimeMillis() - startTime
                DebugLogger.d(TAG, "✅ [BULK] Synced ${allMessages.size} messages in ${duration}ms")
            } catch (e: Exception) {
                Log.e(TAG, "Error in syncAllMessages", e)
            }
        }
    }

    suspend fun hasCachedConversations(): Boolean =
        withContext(Dispatchers.IO) { cachedConversationDao.getCount() > 0 }

    /** Sync messages in background (non-blocking) - for internal use. */
    fun syncMessagesBackground(threadId: Long) {
        repositoryScope.launch {
            try {
                DebugLogger.d(TAG, "🔄 Background sync for thread $threadId...")
                val fresh = systemQueryHelper.querySystemMessages(threadId)
                cachedMessageDao.updateMessagesForThread(threadId, fresh.map { it.toEntity() })
                DebugLogger.d(TAG, "✅ Synced ${fresh.size} messages for thread $threadId")
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing messages for thread $threadId", e)
            }
        }
    }

    /** Sync messages immediately (blocking) - used for first load. */
    suspend fun syncMessagesBlocking(threadId: Long) {
        withContext(Dispatchers.IO) {
            try {
                DebugLogger.d(TAG, "🔄 Immediate sync for thread $threadId...")
                val fresh = systemQueryHelper.querySystemMessages(threadId)
                cachedMessageDao.updateMessagesForThread(threadId, fresh.map { it.toEntity() })
                DebugLogger.d(TAG, "✅ Immediate sync complete: ${fresh.size} messages")
            } catch (e: Exception) {
                Log.e(TAG, "Error in immediate sync for thread $threadId", e)
            }
        }
    }

    /**
     * Sync a single message by its content URI — like QKSMS syncMessage(uri).
     * Reads only ONE row from Telephony and upserts it into Room.
     * O(1) — much faster than syncMessages(threadId) which rewrites the whole thread.
     */
    suspend fun syncMessage(uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                val isMmsUri = uri.toString().contains("mms")
                if (isMmsUri) {
                    val threadId = context.contentResolver.query(
                        uri, arrayOf("thread_id"), null, null, null
                    )?.use { c -> if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow("thread_id")) else null }
                    if (threadId != null && threadId > 0L) syncMessages(threadId)
                    return@withContext
                }
                // SMS path
                context.contentResolver.query(
                    uri,
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
                    null, null, null
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) return@withContext
                    val id          = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID))
                    val threadId    = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID))
                    val address     = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: ""
                    val body        = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY))
                    val date        = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                    val type        = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE))
                    val read        = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ))
                    val subIdCol    = cursor.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)
                    val subId       = if (subIdCol != -1 && !cursor.isNull(subIdCol)) cursor.getInt(subIdCol) else null
                    val simSlot     = subId?.let { simManager.getSlotForSubscriptionId(it) }
                    val contactName = contactResolver.resolveContactName(address)
                    val entity = com.rasmi.purevon.data.local.entity.CachedMessageEntity(
                        id            = id,
                        threadId      = threadId,
                        phoneNumber   = address,
                        contactName   = contactName,
                        body          = body,
                        timestamp     = date,
                        type          = type,
                        category      = com.rasmi.purevon.data.local.entity.MessageCategory.PERSONAL,
                        isRead        = read == 1,
                        isSent        = type == Telephony.Sms.MESSAGE_TYPE_SENT,
                        isDelivered   = true,
                        simSlot       = simSlot,
                        isSpam        = false,
                        spamScore     = 0f,
                        isMms         = false,
                        attachmentUris  = emptyList(),
                        attachmentTypes = emptyList(),
                        status          = null,
                        isScheduled     = false,
                        scheduledTime   = null,
                        scheduleId      = null
                    )
                    cachedMessageDao.upsertMessage(entity)
                    // Also refresh conversation row (snippet + date)
                    conversationSyncJob.value = System.currentTimeMillis()
                    _messageUpdates.tryEmit(Unit)
                    DebugLogger.d(TAG, "⚡ syncMessage: upserted id=$id thread=$threadId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ syncMessage($uri) failed", e)
            }
        }
    }

    /** Refresh cached contact names in both conversations and messages when contacts change. */
    suspend fun refreshCachedContactNames() {
        withContext(Dispatchers.IO) {
            try {
                // Gather all unique phone numbers from cached messages
                val messagePhoneNumbers = cachedMessageDao.getAllUniquePhoneNumbers()
                    .filter { it.isNotBlank() && it != "Unknown" }
                    .toSet()
                
                if (messagePhoneNumbers.isEmpty()) return@withContext
                
                // Batch-resolve fresh names from system contacts
                val freshNames = contactResolver.batchResolveContactNamesOptimized(messagePhoneNumbers)
                
                // Update cached message contact names
                freshNames.forEach { (phone, name) ->
                    cachedMessageDao.updateContactNameByPhoneNumber(phone, name)
                }
                
                // Also update conversation contact names for consistency
                val convPhoneNumbers = cachedConversationDao.getAllUniquePhoneNumbers()
                    .filter { it.isNotBlank() && it != "Unknown" }
                    .toSet()
                val convFreshNames = contactResolver.batchResolveContactNamesOptimized(convPhoneNumbers)
                convFreshNames.forEach { (phone, name) ->
                    cachedConversationDao.updateContactName(phone, name)
                }
                
                DebugLogger.d(TAG, "✅ Refreshed contact names: ${freshNames.size} message numbers, ${convFreshNames.size} conversation numbers")
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing cached contact names", e)
            }
        }
    }

    /** Invalidate cache for specific thread. */
    fun invalidateMessageCache(threadId: Long) {
        syncMessagesBackground(threadId)
    }

    /** Clear all caches. */
    fun invalidateAllCaches() {
        repositoryScope.launch {
            cachedConversationDao.clearAll()
            DebugLogger.d(TAG, "🗑️ Invalidated conversation cache in Room")
        }
    }
}
