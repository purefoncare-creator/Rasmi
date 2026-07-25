package com.rasmi.purevon.data.paging

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.rasmi.purevon.BuildConfig
import com.rasmi.purevon.data.local.dao.ConversationPreferencesDao
import com.rasmi.purevon.data.repository.ContactResolver
import com.rasmi.purevon.domain.model.Conversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PagingSource for loading conversations with pagination
 * 
 * ✅ OPTIMIZED: Uses lightweight ContentObserver with caching
 */
class ConversationPagingSource(
    private val context: Context,
    private val conversationPreferencesDao: ConversationPreferencesDao,
    private val query: String = ""
) : PagingSource<Int, Conversation>() {
    
    private val contactResolver = ContactResolver(context)
    
    companion object {
        private const val TAG = "ConversationPagingSource"
        private const val PAGE_SIZE = 30
        private const val CACHE_VALIDITY_MS = 10000L // 10 seconds
        
        // ✅ Static cache shared across all instances
        // This allows switching between tabs without losing loaded data
        private data class CachedPage(
            val data: List<Conversation>,
            val timestamp: Long,
            val query: String
        ) {
            val isValid: Boolean
                get() = System.currentTimeMillis() - timestamp < CACHE_VALIDITY_MS
        }
        
        private val pageCache = java.util.concurrent.ConcurrentHashMap<Int, CachedPage>()
        
        fun invalidateCache() {
            pageCache.clear()
        }
        
        /**
         * Remove expired entries from cache.
         * Called before loading to ensure stale data is not served.
         */
        fun evictExpired() {
            pageCache.entries.removeAll { !it.value.isValid }
        }
    }

    init {
        // ✅ FIXED: ContentObserver invalidates PagingSource, but keeps cache
        // Cache is only cleared after successful load of new data
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                invalidate()
                // Don't clear cache immediately - let load() decide
            }
        }
        
        // Register observers
        context.contentResolver.registerContentObserver(
            Telephony.Sms.CONTENT_URI,
            true,
            observer
        )
        
        context.contentResolver.registerContentObserver(
            Telephony.Sms.Conversations.CONTENT_URI,
            true,
            observer
        )
        
        // ✅ FIX: Also observe MMS changes for MMS-only conversations
        context.contentResolver.registerContentObserver(
            Uri.parse("content://mms"),
            true,
            observer
        )
        
        // Cleanup
        registerInvalidatedCallback {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
    
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Conversation> {
        return try {
            val page = params.key ?: 0
            val offset = page * PAGE_SIZE
            
            // ✅ Check cache first
            val cached = pageCache[page]
            if (cached != null && cached.isValid && cached.query == query) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "✅ Cache hit for page=$page")
                }
                return LoadResult.Page(
                    data = cached.data,
                    prevKey = if (page == 0) null else page - 1,
                    // ✅ FIX M31: Don't set nextKey for partial pages
                    nextKey = if (cached.data.size < PAGE_SIZE) null else page + 1
                )
            }
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "📥 Cache miss - Loading conversations page=$page, offset=$offset, query=$query")
            }
            
            val conversations = withContext(Dispatchers.IO) {
                queryConversationsWithPagination(
                    limit = params.loadSize,
                    offset = offset
                )
            }
            
            // ✅ Update cache - only for this page
            // Don't clear entire cache unless query changed
            if (pageCache.values.firstOrNull()?.query != query) {
                // Query changed - clear old cache
                pageCache.clear()
            }
            
            pageCache[page] = CachedPage(
                data = conversations,
                timestamp = System.currentTimeMillis(),
                query = query
            )
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "✅ Loaded and cached ${conversations.size} conversations")
            }
            
            LoadResult.Page(
                data = conversations,
                prevKey = if (page == 0) null else page - 1,
                // ✅ FIX M31: Don't set nextKey for partial pages (avoids unnecessary empty load)
                nextKey = if (conversations.size < params.loadSize) null else page + 1
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading conversations", e)
            LoadResult.Error(e)
        }
    }
    
    override fun getRefreshKey(state: PagingState<Int, Conversation>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
    
    private suspend fun queryConversationsWithPagination(
        limit: Int,
        offset: Int
    ): List<Conversation> {
        val conversations = mutableListOf<Conversation>()
        
        try {
            val selection = if (query.isNotBlank()) {
                "${Telephony.Sms.Conversations.SNIPPET} LIKE ?"
            } else null
            
            val selectionArgs = if (query.isNotBlank()) {
                arrayOf("%$query%")
            } else null

            // ✅ STEP 1: Query conversations using mms-sms provider (includes both SMS and MMS-only threads)
            val threadIds = mutableListOf<Long>()
            val threadsUri = Uri.parse("content://mms-sms/conversations?simple=true")
            context.contentResolver.query(
                threadsUri,
                arrayOf("_id", "date", "message_count"),
                null,
                null,
                "date DESC LIMIT $limit OFFSET $offset"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    threadIds.add(cursor.getLong(0))
                }
            }
            
            if (threadIds.isEmpty()) return emptyList()
            
            // ✅ STEP 2: Batch get last messages for all threads (1 query instead of N)
            val lastMessagesMap = batchGetLastMessages(threadIds).toMutableMap()
            
            // ✅ STEP 2.5: Handle MMS-only threads + fix "Unknown" phone numbers
            val missingThreads = threadIds.filter { it !in lastMessagesMap }
            val unknownThreads = lastMessagesMap.filter { it.value.phoneNumber == "Unknown" }.keys
            
            // For MMS-only threads, get last MMS data
            if (missingThreads.isNotEmpty()) {
                val mmsData = getMmsThreadsData(missingThreads)
                lastMessagesMap.putAll(mmsData)
            }
            
            // For threads with "Unknown" phone numbers, try to resolve via canonical_addresses
            val threadsNeedingPhoneResolve = unknownThreads + 
                lastMessagesMap.filter { it.value.phoneNumber == "Unknown" }.keys
            for (threadId in threadsNeedingPhoneResolve) {
                val resolvedPhone = resolveThreadAddress(threadId)
                if (resolvedPhone != null) {
                    val existing = lastMessagesMap[threadId]
                    if (existing != null) {
                        lastMessagesMap[threadId] = existing.copy(phoneNumber = resolvedPhone)
                    }
                }
            }
            
            // ✅ STEP 3: Batch get unread counts for all threads (1 query instead of N)
            val unreadCountsMap = batchGetUnreadCounts(threadIds)
            
            // ✅ STEP 4: Batch resolve contact names (1 query instead of N)
            val phoneNumbers = lastMessagesMap.values.map { it.phoneNumber }.toSet()
            val contactNamesMap = batchResolveContactNames(phoneNumbers)
            
            // ✅ STEP 5: Batch get preferences for all threads (1 query instead of N)
            val prefsMap = conversationPreferencesDao.getPreferencesForThreads(threadIds)
                .associateBy { it.systemThreadId }
            
            // ✅ STEP 6: Build conversations
            threadIds.forEach { threadId ->
                val lastMessageData = lastMessagesMap[threadId]
                if (lastMessageData != null) {
                    val prefs = prefsMap[threadId]
                    
                    val conversation = Conversation(
                        threadId = threadId,
                        phoneNumber = lastMessageData.phoneNumber,
                        contactName = contactNamesMap[lastMessageData.phoneNumber],
                        contactPhotoUri = null,
                        lastMessage = lastMessageData.body ?: "",
                        lastMessageTimestamp = lastMessageData.timestamp,
                        lastMessageType = if (lastMessageData.type == Telephony.Sms.MESSAGE_TYPE_SENT) 
                            "sent" else "received",
                        unreadCount = unreadCountsMap[threadId] ?: 0,
                        messageCount = 0,
                        isPinned = prefs?.isPinned ?: false,
                        isMuted = prefs?.isMuted ?: false,
                        isArchived = prefs?.isArchived ?: false
                    )
                    
                    // ✅ Filter out archived conversations HERE
                    if (!conversation.isArchived) {
                        conversations.add(conversation)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying conversations", e)
        }
        
        return conversations
    }
    
    /**
     * ✅ OPTIMIZED: Batch get last message for multiple threads in one query
     */
    private fun batchGetLastMessages(threadIds: List<Long>): Map<Long, LastMessageData> {
        val result = mutableMapOf<Long, LastMessageData>()
        if (threadIds.isEmpty()) return result
        
        try {
            // Build selection: thread_id IN (1,2,3,...)
            val placeholders = threadIds.joinToString(",") { "?" }
            val selection = "${Telephony.Sms.THREAD_ID} IN ($placeholders)"
            val selectionArgs = threadIds.map { it.toString() }.toTypedArray()
            
            // Get messages sorted by date, then group by thread_id
            // ✅ FIX M21: Add LIMIT to prevent full table scan with many messages
            val limitPerThread = threadIds.size
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms.THREAD_ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE
                ),
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} DESC LIMIT $limitPerThread"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val threadId = cursor.getLong(0)
                    // Only take first message per thread (most recent)
                    if (!result.containsKey(threadId)) {
                        result[threadId] = LastMessageData(
                            phoneNumber = cursor.getString(1) ?: "Unknown",
                            body = cursor.getString(2),
                            timestamp = cursor.getLong(3),
                            type = cursor.getInt(4)
                        )
                        // ✅ FIX M21: Stop scanning once all threads found
                        if (result.size == threadIds.size) break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error batch getting last messages", e)
        }
        
        return result
    }
    
    /**
     * ✅ OPTIMIZED: Batch get unread counts for multiple threads in one query
     */
    /**
     * ✅ FIX 2.11: ContentProviders don't support GROUP BY, so the aggregate query
     * (COUNT(*) grouped by THREAD_ID) returned incorrect results — one row with the
     * total for all threads. Instead, query individual unread rows and count in-memory.
     */
    private fun batchGetUnreadCounts(threadIds: List<Long>): Map<Long, Int> {
        val result = mutableMapOf<Long, Int>()
        if (threadIds.isEmpty()) return result
        
        try {
            val placeholders = threadIds.joinToString(",") { "?" }
            val selection = "${Telephony.Sms.THREAD_ID} IN ($placeholders) AND ${Telephony.Sms.READ} = 0"
            val selectionArgs = threadIds.map { it.toString() }.toTypedArray()
            
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.THREAD_ID),
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val threadId = cursor.getLong(0)
                    result[threadId] = (result[threadId] ?: 0) + 1
                }
            }
        } catch (e: Exception) {
            // Fallback: count manually
            threadIds.forEach { threadId ->
                result[threadId] = getUnreadCount(threadId)
            }
        }
        
        return result
    }
    
    /**
     * ✅ OPTIMIZED: Batch resolve contact names using real batch query
     * Delegates to ContactResolver which uses IN (?) SQL + PhoneLookup fallback
     */
    private fun batchResolveContactNames(phoneNumbers: Set<String>): Map<String, String> {
        return contactResolver.batchResolveContactNamesOptimized(phoneNumbers)
    }
    
    private fun getUnreadCount(threadId: Long): Int {
        return try {
            var count = 0
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf("COUNT(*)"),
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString()),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    count = cursor.getInt(0)
                }
            }
            count
        } catch (e: Exception) {
            0
        }
    }
    
    private data class LastMessageData(
        val phoneNumber: String,
        val body: String?,
        val timestamp: Long,
        val type: Int
    )

    /**
     * ✅ Resolve phone number for a thread via canonical_addresses table.
     * This works even when the last message has a null ADDRESS field.
     */
    private fun resolveThreadAddress(threadId: Long): String? {
        try {
            // Step 1: Get recipient_ids from the thread
            val threadsUri = Uri.parse("content://mms-sms/conversations?simple=true")
            var recipientIds: String? = null
            
            context.contentResolver.query(
                threadsUri,
                arrayOf("_id", "recipient_ids"),
                "_id = ?",
                arrayOf(threadId.toString()),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val recipientIdsIndex = cursor.getColumnIndex("recipient_ids")
                    if (recipientIdsIndex >= 0) {
                        recipientIds = cursor.getString(recipientIdsIndex)
                    }
                }
            }
            
            // Step 2: Look up each recipient ID in canonical_addresses
            val ids = recipientIds?.trim()?.split("\\s+".toRegex())?.filter { it.isNotBlank() }
            if (!ids.isNullOrEmpty()) {
                for (id in ids) {
                    val canonicalUri = Uri.parse("content://mms-sms/canonical-addresses")
                    context.contentResolver.query(
                        canonicalUri,
                        arrayOf("_id", "address"),
                        "_id = ?",
                        arrayOf(id),
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val addressIndex = cursor.getColumnIndex("address")
                            if (addressIndex >= 0) {
                                val address = cursor.getString(addressIndex)
                                if (!address.isNullOrBlank() && address != "Unknown") {
                                    return address
                                }
                            }
                        }
                    }
                }
            }
            
            // Step 3: Fallback - try to get address from any SMS in this thread
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS),
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.ADDRESS} IS NOT NULL AND ${Telephony.Sms.ADDRESS} != ''",
                arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} DESC LIMIT 1"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val address = cursor.getString(0)
                    if (!address.isNullOrBlank() && address != "Unknown") {
                        return address
                    }
                }
            }
            
            // Step 4: Fallback - try MMS addr table
            context.contentResolver.query(
                Uri.parse("content://mms"),
                arrayOf("_id"),
                "thread_id = ?",
                arrayOf(threadId.toString()),
                "date DESC LIMIT 1"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val mmsId = cursor.getLong(0)
                    val addr = getMmsAddress(mmsId)
                    if (addr != null) return addr
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving thread address for threadId=$threadId", e)
        }
        return null
    }
    
    /**
     * ✅ Get last message data for MMS-only threads
     */
    private fun getMmsThreadsData(threadIds: List<Long>): Map<Long, LastMessageData> {
        val result = mutableMapOf<Long, LastMessageData>()
        if (threadIds.isEmpty()) return result
        
        try {
            for (threadId in threadIds) {
                context.contentResolver.query(
                    Uri.parse("content://mms"),
                    arrayOf("_id", "date", "msg_box", "sub"),
                    "thread_id = ?",
                    arrayOf(threadId.toString()),
                    "date DESC LIMIT 1"
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val mmsId = cursor.getLong(0)
                        // MMS date is in seconds, not milliseconds
                        val timestamp = cursor.getLong(1) * 1000L
                        val msgBox = cursor.getInt(2)
                        val subject = cursor.getString(3)
                        
                        // Get phone number from MMS address table
                        val phoneNumber = getMmsAddress(mmsId) ?: "Unknown"
                        
                        // Get MMS text content
                        val body = getMmsTextContent(mmsId) ?: subject ?: "\uD83D\uDCCE MMS"
                        
                        // msg_box: 1=received, 2=sent
                        val type = if (msgBox == 2) Telephony.Sms.MESSAGE_TYPE_SENT 
                                   else Telephony.Sms.MESSAGE_TYPE_INBOX
                        
                        result[threadId] = LastMessageData(
                            phoneNumber = phoneNumber,
                            body = body,
                            timestamp = timestamp,
                            type = type
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting MMS threads data", e)
        }
        
        return result
    }
    
    /**
     * ✅ Get phone number from MMS addr table (type 137 = FROM, type 151 = TO)
     */
    private fun getMmsAddress(mmsId: Long): String? {
        try {
            // Try FROM address first (for received MMS), then TO (for sent MMS)
            for (type in listOf(137, 151)) {
                context.contentResolver.query(
                    Uri.parse("content://mms/$mmsId/addr"),
                    arrayOf("address"),
                    "type = ?",
                    arrayOf(type.toString()),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val address = cursor.getString(0)
                        if (!address.isNullOrBlank() && address != "Unknown" 
                            && !address.contains("insert-address-token")) {
                            return address
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting MMS address for mmsId=$mmsId", e)
        }
        return null
    }
    
    /**
     * ✅ Get text content from MMS parts table
     */
    private fun getMmsTextContent(mmsId: Long): String? {
        try {
            context.contentResolver.query(
                Uri.parse("content://mms/part"),
                arrayOf("text", "ct"),
                "mid = ? AND ct = 'text/plain'",
                arrayOf(mmsId.toString()),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val text = cursor.getString(0)
                    if (!text.isNullOrBlank()) return text
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting MMS text content for mmsId=$mmsId", e)
        }
        return null
    }
}
