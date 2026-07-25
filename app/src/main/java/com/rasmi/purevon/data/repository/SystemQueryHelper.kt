package com.rasmi.purevon.data.repository

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.rasmi.purevon.data.local.dao.ConversationPreferencesDao
import com.rasmi.purevon.data.local.dao.MessageMetadataDao
import com.rasmi.purevon.data.local.entity.MessageCategory
import com.rasmi.purevon.domain.model.Conversation
import com.rasmi.purevon.domain.model.Message
import com.rasmi.purevon.util.DebugLogger
import com.rasmi.purevon.util.sim.SimManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Handles all system ContentProvider queries for SMS/MMS messages and conversations.
 * Extracted from MessageRepositoryImpl to reduce class size (~800 lines).
 *
 * Thread Safety: Uses contentProviderReadMutex for concurrent read protection.
 */
internal class SystemQueryHelper(
    private val context: Context,
    private val contactResolver: ContactResolver,
    private val simManager: SimManager,
    private val messageMetadataDao: MessageMetadataDao,
    private val conversationPreferencesDao: ConversationPreferencesDao
) {

    companion object {
        private const val TAG = "SystemQueryHelper"
        private const val MAX_CONVERSATIONS = 5000
    }

    private val contentProviderReadMutex = Mutex()

    // ============================================
    // Data Classes
    // ============================================

    internal data class ConversationRow(
        val threadId: Long,
        val date: Long,
        val snippet: String?,
        val recipientIds: List<Long>,
        val read: Int,
        val count: Int
    )

    internal data class LastMessageData(
        val id: Long,
        val threadId: Long,
        val phoneNumber: String,
        val body: String?,
        val timestamp: Long,
        val type: Int
    ) {
        val isSent: Boolean get() = (type == 2)
    }

    // ============================================
    // Conversation Queries
    // ============================================

    /**
     * PRO query using Threads API (WhatsApp/Messenger style).
     * Uses content://mms-sms/conversations for instant loading.
     */
    suspend fun querySystemConversationsOptimized(includeArchived: Boolean = false): List<Conversation> = contentProviderReadMutex.withLock {
        val conversations = mutableListOf<Conversation>()
        val startTime = System.currentTimeMillis()

        DebugLogger.d(TAG, "📊 Starting PRO querySystemConversations (Threads API)...")

        try {
            val uri = Uri.parse("content://mms-sms/conversations?simple=true")
            val projection = arrayOf(
                "_id", "date", "snippet", "recipient_ids", "read", "message_count"
            )

            val addressCache = mutableMapOf<Long, String>()
            val rows = mutableListOf<ConversationRow>()
            val allRecipientIds = mutableSetOf<Long>()

            context.contentResolver.query(
                uri, projection, null, null,
                "date DESC LIMIT $MAX_CONVERSATIONS"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex("_id")
                val dateIndex = cursor.getColumnIndex("date")
                val snippetIndex = cursor.getColumnIndex("snippet")
                val recipientIdsIndex = cursor.getColumnIndex("recipient_ids")
                val readIndex = cursor.getColumnIndex("read")
                val countIndex = cursor.getColumnIndex("message_count")

                while (cursor.moveToNext()) {
                    val rIdsStr = cursor.getString(recipientIdsIndex) ?: ""
                    val rIds = rIdsStr.split("\\s+".toRegex()).mapNotNull { it.toLongOrNull() }
                    allRecipientIds.addAll(rIds)

                    rows.add(ConversationRow(
                        threadId = cursor.getLong(idIndex),
                        date = cursor.getLong(dateIndex),
                        snippet = cursor.getString(snippetIndex),
                        recipientIds = rIds,
                        read = cursor.getInt(readIndex),
                        count = cursor.getInt(countIndex)
                    ))
                }
            }

            // ============================================
            // Phase 2: Discover threads MISSING from the threads table
            // Some devices/ROMs don't populate the threads table for all conversations.
            // Scan SMS + MMS tables for distinct thread_ids not found above.
            // ============================================
            val knownThreadIds = rows.map { it.threadId }.toMutableSet()
            val missingThreadIds = mutableSetOf<Long>()

            // Scan SMS table for distinct thread_ids
            try {
                context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    arrayOf("DISTINCT ${Telephony.Sms.THREAD_ID}"),
                    "${Telephony.Sms.THREAD_ID} IS NOT NULL AND ${Telephony.Sms.THREAD_ID} > 0",
                    null, null
                )?.use { cursor ->
                    val tidIdx = cursor.getColumnIndex(Telephony.Sms.THREAD_ID)
                    while (cursor.moveToNext()) {
                        val tid = cursor.getLong(tidIdx)
                        if (tid > 0 && tid !in knownThreadIds) {
                            missingThreadIds.add(tid)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning SMS for missing threads", e)
            }

            // Scan MMS table for distinct thread_ids
            try {
                context.contentResolver.query(
                    Telephony.Mms.CONTENT_URI,
                    arrayOf("DISTINCT ${Telephony.Mms.THREAD_ID}"),
                    "${Telephony.Mms.THREAD_ID} IS NOT NULL AND ${Telephony.Mms.THREAD_ID} > 0",
                    null, null
                )?.use { cursor ->
                    val tidIdx = cursor.getColumnIndex(Telephony.Mms.THREAD_ID)
                    while (cursor.moveToNext()) {
                        val tid = cursor.getLong(tidIdx)
                        if (tid > 0 && tid !in knownThreadIds) {
                            missingThreadIds.add(tid)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning MMS for missing threads", e)
            }

            if (missingThreadIds.isNotEmpty()) {
                DebugLogger.d(TAG, "🔍 Found ${missingThreadIds.size} threads missing from threads table — recovering...")
                // Build minimal conversation entries from the last SMS/MMS for each missing thread
                for (tid in missingThreadIds) {
                    try {
                        // Try SMS first
                        var found = false
                        context.contentResolver.query(
                            Telephony.Sms.CONTENT_URI,
                            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.READ, Telephony.Sms.TYPE),
                            "${Telephony.Sms.THREAD_ID} = ?",
                            arrayOf(tid.toString()),
                            "${Telephony.Sms.DATE} DESC LIMIT 1"
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val address = cursor.getString(0) ?: "Unknown"
                                val body = cursor.getString(1) ?: ""
                                val date = cursor.getLong(2)
                                val read = cursor.getInt(3)
                                val type = cursor.getInt(4)
                                rows.add(ConversationRow(
                                    threadId = tid,
                                    date = date,
                                    snippet = body,
                                    recipientIds = emptyList(),
                                    read = read,
                                    count = 0
                                ))
                                // We don't have recipient_ids, but we have the address directly
                                // Store it for later use
                                knownThreadIds.add(tid)
                                found = true
                            }
                        }
                        // If no SMS, try MMS
                        if (!found) {
                            context.contentResolver.query(
                                Telephony.Mms.CONTENT_URI,
                                arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.READ),
                                "${Telephony.Mms.THREAD_ID} = ?",
                                arrayOf(tid.toString()),
                                "${Telephony.Mms.DATE} DESC LIMIT 1"
                            )?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val mmsId = cursor.getLong(0)
                                    val date = cursor.getLong(1) * 1000L // MMS dates are in seconds
                                    val read = cursor.getInt(2)
                                    rows.add(ConversationRow(
                                        threadId = tid,
                                        date = date,
                                        snippet = "(MMS)",
                                        recipientIds = emptyList(),
                                        read = read,
                                        count = 0
                                    ))
                                    knownThreadIds.add(tid)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error recovering thread $tid", e)
                    }
                }
            }

            resolveCanonicalAddresses(allRecipientIds, addressCache)

            val threadIds = rows.map { it.threadId }
            val prefsMap = conversationPreferencesDao.getPreferencesForThreads(threadIds)
                .associateBy { it.systemThreadId }

            // Batch get actual unread counts per thread
            val unreadCountsMap = batchGetUnreadCounts(threadIds)

            // Map to resolve addresses for recovered threads (those with empty recipientIds)
            val recoveredAddresses = mutableMapOf<Long, String>()
            rows.filter { it.recipientIds.isEmpty() }.forEach { row ->
                try {
                    // Get address from the last SMS in this thread
                    context.contentResolver.query(
                        Telephony.Sms.CONTENT_URI,
                        arrayOf(Telephony.Sms.ADDRESS),
                        "${Telephony.Sms.THREAD_ID} = ?",
                        arrayOf(row.threadId.toString()),
                        "${Telephony.Sms.DATE} DESC LIMIT 1"
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            cursor.getString(0)?.let { address ->
                                recoveredAddresses[row.threadId] = address
                            }
                        }
                    }
                    // If no SMS address, try MMS addr table
                    if (row.threadId !in recoveredAddresses) {
                        context.contentResolver.query(
                            Telephony.Mms.CONTENT_URI,
                            arrayOf(Telephony.Mms._ID),
                            "${Telephony.Mms.THREAD_ID} = ?",
                            arrayOf(row.threadId.toString()),
                            "${Telephony.Mms.DATE} DESC LIMIT 1"
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val mmsId = cursor.getLong(0)
                                context.contentResolver.query(
                                    Uri.parse("content://mms/$mmsId/addr"),
                                    arrayOf("address"),
                                    "type = 137", // FROM address
                                    null, null
                                )?.use { addrCursor ->
                                    if (addrCursor.moveToFirst()) {
                                        val addr = addrCursor.getString(0)
                                        if (addr != null && !addr.contains("insert-address")) {
                                            recoveredAddresses[row.threadId] = addr
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error resolving address for recovered thread ${row.threadId}", e)
                }
            }

            rows.forEach { row ->
                val addresses = if (row.recipientIds.isNotEmpty()) {
                    row.recipientIds.mapNotNull { addressCache[it] }
                } else {
                    // Recovered thread — use directly resolved address
                    listOfNotNull(recoveredAddresses[row.threadId])
                }
                val phoneNumber = if (addresses.isNotEmpty()) addresses.first() else "Unknown"
                val isGroup = addresses.size > 1
                val prefs = prefsMap[row.threadId]
                val isArchived = prefs?.isArchived == true

                if (includeArchived || !isArchived) {
                    conversations.add(
                        Conversation(
                            threadId = row.threadId,
                            phoneNumber = phoneNumber,
                            contactName = null,
                            contactPhotoUri = null,
                            lastMessage = row.snippet ?: "",
                            lastMessageTimestamp = row.date,
                            lastMessageType = "received",
                            unreadCount = unreadCountsMap[row.threadId] ?: 0,
                            messageCount = row.count,
                            isPinned = prefs?.isPinned ?: false,
                            isMuted = prefs?.isMuted ?: false,
                            isArchived = isArchived,
                            isGroup = isGroup,
                            groupParticipants = addresses
                        )
                    )
                }
            }

            val phoneNumbers = conversations.map { it.phoneNumber }.toSet()
            val contactNamesMap = contactResolver.batchResolveContactNamesOptimized(phoneNumbers)
            val contactPhotoUriMap = contactResolver.batchResolveContactPhotoUrisOptimized(phoneNumbers)

            val finalConversations = conversations.map {
                it.copy(
                    contactName = contactNamesMap[it.phoneNumber],
                    contactPhotoUri = contactPhotoUriMap[it.phoneNumber]
                )
            }

            val elapsed = System.currentTimeMillis() - startTime
            DebugLogger.d(TAG, "✅ PRO query completed in ${elapsed}ms, ${finalConversations.size} conversations (${missingThreadIds.size} recovered)")

            return finalConversations.sortedByDescending { it.lastMessageTimestamp }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in PRO query, falling back to legacy", e)
            // IMPORTANT: Cannot call querySystemConversations() here — Kotlin Mutex is NOT
            // reentrant. Calling it while holding contentProviderReadMutex deadlocks forever.
            // Return empty list; the ContentObserver will trigger a background retry.
            emptyList<Conversation>()
        }
    }

    /**
     * Legacy conversation query (kept as fallback).
     */
    suspend fun querySystemConversations(): List<Conversation> = contentProviderReadMutex.withLock {
        val conversations = mutableListOf<Conversation>()

        DebugLogger.d(TAG, "Starting BATCH-OPTIMIZED querySystemConversations...")
        val startTime = System.currentTimeMillis()

        try {
            val lastMessagesMap = mutableMapOf<Long, LastMessageData>()

            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID, Telephony.Sms.THREAD_ID,
                    Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
                    Telephony.Sms.DATE, Telephony.Sms.TYPE
                ),
                null, null, "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(Telephony.Sms._ID)
                val threadIdIndex = cursor.getColumnIndex(Telephony.Sms.THREAD_ID)
                val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE)
                val typeIndex = cursor.getColumnIndex(Telephony.Sms.TYPE)

                while (cursor.moveToNext()) {
                    val threadId = cursor.getLong(threadIdIndex)
                    if (!lastMessagesMap.containsKey(threadId)) {
                        val phoneNumber = cursor.getString(addressIndex) ?: "Unknown"
                        lastMessagesMap[threadId] = LastMessageData(
                            id = cursor.getLong(idIndex),
                            threadId = threadId,
                            phoneNumber = phoneNumber,
                            body = cursor.getString(bodyIndex),
                            timestamp = cursor.getLong(dateIndex),
                            type = cursor.getInt(typeIndex)
                        )
                    }
                    if (lastMessagesMap.size >= 100) break
                }
            }

            DebugLogger.d(TAG, "Found ${lastMessagesMap.size} unique threads in BATCH query")

            val phoneNumbers = lastMessagesMap.values.map { it.phoneNumber }.toSet()
            val contactNamesMap = contactResolver.batchResolveContactNamesOptimized(phoneNumbers)
            val contactPhotoUriMap = contactResolver.batchResolveContactPhotoUrisOptimized(phoneNumbers)
            val unreadCountsMap = batchGetUnreadCounts(lastMessagesMap.keys.toList())
            val messageCountsMap = batchGetMessageCounts(lastMessagesMap.keys.toList())
            val prefsMap = lastMessagesMap.keys.toList().let { threadIds ->
                conversationPreferencesDao.getPreferencesForThreads(threadIds)
                    .associateBy { it.systemThreadId }
            }

            lastMessagesMap.forEach { (threadId: Long, lastMsgData: LastMessageData) ->
                try {
                    conversations.add(Conversation(
                        threadId = threadId,
                        phoneNumber = lastMsgData.phoneNumber,
                        contactName = contactNamesMap[lastMsgData.phoneNumber],
                        contactPhotoUri = contactPhotoUriMap[lastMsgData.phoneNumber],
                        lastMessage = lastMsgData.body ?: "",
                        lastMessageTimestamp = lastMsgData.timestamp,
                        lastMessageType = if (lastMsgData.isSent) "sent" else "received",
                        unreadCount = unreadCountsMap[threadId] ?: 0,
                        messageCount = messageCountsMap[threadId] ?: 0,
                        isPinned = prefsMap[threadId]?.isPinned ?: false,
                        isMuted = prefsMap[threadId]?.isMuted ?: false,
                        isArchived = prefsMap[threadId]?.isArchived ?: false,
                        isGroup = false,
                        groupParticipants = emptyList()
                    ))
                } catch (e: Exception) {
                    Log.e(TAG, "Error building conversation for thread $threadId", e)
                }
            }

            conversations.sortByDescending { it.lastMessageTimestamp }

        } catch (e: Exception) {
            Log.e(TAG, "Error in querySystemConversations", e)
        }

        val duration = System.currentTimeMillis() - startTime
        DebugLogger.d(TAG, "BATCH querySystemConversations completed in ${duration}ms, found ${conversations.size} conversations")

        return conversations
    }

    // ============================================
    // Message Queries
    // ============================================

    /**
     * Quik-master approach: exactly 4 ContentProvider queries total.
     */
    suspend fun queryAllSystemMessages(): List<Message> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<Message>()
        val startTime = System.currentTimeMillis()
        DebugLogger.d(TAG, "🚀 [BULK] Starting quik-master 4-query sync...")

        // Step 1: ALL MMS parts
        val mmsPartsMap = mutableMapOf<Long, Pair<String?, MutableList<Pair<String, String>>>>()
        try {
            context.contentResolver.query(
                Uri.parse("content://mms/part"),
                arrayOf("_id", "mid", "ct", "text"),
                null, null, null
            )?.use { cursor ->
                val idIdx  = cursor.getColumnIndex("_id")
                val midIdx = cursor.getColumnIndex("mid")
                val ctIdx  = cursor.getColumnIndex("ct")
                val txtIdx = cursor.getColumnIndex("text")
                while (cursor.moveToNext()) {
                    val partId = if (idIdx  >= 0) cursor.getLong(idIdx)  else continue
                    val mmsId  = if (midIdx >= 0) cursor.getLong(midIdx) else continue
                    val ct     = if (ctIdx  >= 0) cursor.getString(ctIdx) ?: "*/*" else "*/*"
                    val entry  = mmsPartsMap.getOrPut(mmsId) { Pair(null, mutableListOf()) }
                    when {
                        ct == "text/plain" && txtIdx >= 0 -> {
                            val text = cursor.getString(txtIdx)
                            if (text != null) mmsPartsMap[mmsId] = Pair(text, entry.second)
                        }
                        ct != "application/smil" -> {
                            entry.second.add(Pair("content://mms/part/$partId", ct))
                        }
                    }
                }
            }
            DebugLogger.d(TAG, "[BULK] Step 1 done — parts for ${mmsPartsMap.size} MMS messages")
        } catch (e: Exception) {
            Log.w(TAG, "MMS parts batch query failed — MMS bodies will be empty", e)
        }

        // Step 2: ALL MMS addresses
        val mmsAddrMap = mutableMapOf<Long, String>()
        try {
            context.contentResolver.query(
                Uri.parse("content://mms/addr"),
                arrayOf("msg_id", "address", "type"),
                null, null, null
            )?.use { cursor ->
                val msgIdIdx = cursor.getColumnIndex("msg_id")
                val addrIdx  = cursor.getColumnIndex("address")
                val typeIdx  = cursor.getColumnIndex("type")
                while (cursor.moveToNext()) {
                    val mmsId = if (msgIdIdx >= 0) cursor.getLong(msgIdIdx) else continue
                    val addr  = if (addrIdx  >= 0) cursor.getString(addrIdx) ?: continue else continue
                    val type  = if (typeIdx  >= 0) cursor.getInt(typeIdx) else 0
                    if ((type == 137 || type == 151) &&
                        addr != "insert-address-token" &&
                        !mmsAddrMap.containsKey(mmsId)) {
                        mmsAddrMap[mmsId] = addr
                    }
                }
            }
            DebugLogger.d(TAG, "[BULK] Step 2 done — addresses for ${mmsAddrMap.size} MMS messages")
        } catch (e: Exception) {
            Log.w(TAG, "MMS addr batch query failed — MMS senders will show as Unknown", e)
        }

        // Step 3: ALL SMS
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID, Telephony.Sms.THREAD_ID,
                    Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
                    Telephony.Sms.DATE, Telephony.Sms.TYPE,
                    Telephony.Sms.READ, Telephony.Sms.SUBSCRIPTION_ID
                ),
                null, null, null
            )?.use { cursor ->
                val idIdx    = cursor.getColumnIndex(Telephony.Sms._ID)
                val thrIdx   = cursor.getColumnIndex(Telephony.Sms.THREAD_ID)
                val addrIdx  = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx  = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx  = cursor.getColumnIndex(Telephony.Sms.DATE)
                val typeIdx  = cursor.getColumnIndex(Telephony.Sms.TYPE)
                val readIdx  = cursor.getColumnIndex(Telephony.Sms.READ)
                val subIdIdx = cursor.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)
                while (cursor.moveToNext()) {
                    val smsType = if (typeIdx >= 0) cursor.getInt(typeIdx) else 1
                    messages.add(Message(
                        id          = if (idIdx   >= 0) cursor.getLong(idIdx)    else 0L,
                        threadId    = if (thrIdx  >= 0) cursor.getLong(thrIdx)   else 0L,
                        phoneNumber = if (addrIdx >= 0) cursor.getString(addrIdx) ?: "Unknown" else "Unknown",
                        contactName = null,
                        body        = if (bodyIdx >= 0) cursor.getString(bodyIdx) else null,
                        timestamp   = if (dateIdx >= 0) cursor.getLong(dateIdx)  else 0L,
                        type        = smsType,
                        category    = MessageCategory.PERSONAL,
                        isRead      = if (readIdx >= 0) cursor.getInt(readIdx) == 1 else true,
                        isSent      = smsType == 2,
                        isDelivered = true,
                        simSlot     = if (subIdIdx >= 0 && !cursor.isNull(subIdIdx))
                                          simManager.getSlotForSubscriptionId(cursor.getInt(subIdIdx))
                                      else null,
                        isSpam      = false,
                        spamScore   = 0f,
                        isMms       = false,
                        attachmentUris  = emptyList(),
                        attachmentTypes = emptyList()
                    ))
                }
            }
            DebugLogger.d(TAG, "[BULK] Step 3 done — ${messages.size} SMS")
        } catch (e: Exception) {
            Log.e(TAG, "SMS bulk query failed", e)
        }

        val smsCount = messages.size

        // Step 4: ALL MMS, join with maps
        try {
            context.contentResolver.query(
                Uri.parse("content://mms"),
                arrayOf("_id", "thread_id", "date", "msg_box", "read", "sub_id"),
                null, null, null
            )?.use { cursor ->
                val idIdx    = cursor.getColumnIndex("_id")
                val thrIdx   = cursor.getColumnIndex("thread_id")
                val dateIdx  = cursor.getColumnIndex("date")
                val boxIdx   = cursor.getColumnIndex("msg_box")
                val readIdx  = cursor.getColumnIndex("read")
                val subIdIdx = cursor.getColumnIndex("sub_id")
                while (cursor.moveToNext()) {
                    val mmsId  = if (idIdx  >= 0) cursor.getLong(idIdx)  else 0L
                    val msgBox = if (boxIdx >= 0) cursor.getInt(boxIdx)  else 1
                    val parts  = mmsPartsMap[mmsId]
                    val isFailed = msgBox == 5 // ✅ FIX 2.10: msgBox 4 is outbox (sending), only 5 is failed
                    messages.add(Message(
                        id          = mmsId,
                        threadId    = if (thrIdx  >= 0) cursor.getLong(thrIdx)  else 0L,
                        phoneNumber = mmsAddrMap[mmsId] ?: "Unknown",
                        contactName = null,
                        body        = parts?.first,
                        timestamp   = (if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L) * 1000L,
                        type        = if (isFailed) 5 else msgBox,
                        category    = MessageCategory.PERSONAL,
                        isRead      = if (readIdx >= 0) cursor.getInt(readIdx) == 1 else true,
                        isSent      = msgBox == 2,
                        isDelivered = false,
                        simSlot     = if (subIdIdx >= 0 && !cursor.isNull(subIdIdx))
                                          simManager.getSlotForSubscriptionId(cursor.getInt(subIdIdx))
                                      else null,
                        isSpam      = false,
                        spamScore   = 0f,
                        isMms       = true,
                        attachmentUris  = parts?.second?.map { it.first }  ?: emptyList(),
                        attachmentTypes = parts?.second?.map { it.second } ?: emptyList()
                    ))
                }
            }
            DebugLogger.d(TAG, "[BULK] Step 4 done — ${messages.size - smsCount} MMS")
        } catch (e: Exception) {
            Log.e(TAG, "MMS bulk query failed", e)
        }

        val elapsed = System.currentTimeMillis() - startTime
        DebugLogger.d(TAG, "✅ [BULK] ${messages.size} messages in ${elapsed}ms (4 queries — quik-master approach)")

        messages.distinctBy { "${it.id}_${it.isMms}" }
    }

    /**
     * Query system messages with filtering options.
     */
    suspend fun querySystemMessages(
        threadId: Long? = null,
        unreadOnly: Boolean = false,
        lightweightMms: Boolean = false,
        searchQuery: String? = null,
        messageIds: List<Long>? = null
    ): List<Message> = contentProviderReadMutex.withLock {
        val messages = mutableListOf<Message>()
        val startTime = System.currentTimeMillis()

        DebugLogger.d(TAG, "Querying messages for thread=$threadId, unreadOnly=$unreadOnly, search=${searchQuery != null}, ids=${messageIds?.size}")

        // 1. Query SMS messages
        val smsSelectionArgsList = mutableListOf<String>()
        val smsSelection = buildString {
            if (threadId != null) {
                append("${Telephony.Sms.THREAD_ID} = ?")
                smsSelectionArgsList.add(threadId.toString())
            }
            if (unreadOnly) {
                if (isNotEmpty()) append(" AND ")
                append("${Telephony.Sms.READ} = 0")
            }
            if (!searchQuery.isNullOrBlank()) {
                if (isNotEmpty()) append(" AND ")
                append("(${Telephony.Sms.BODY} LIKE ? OR ${Telephony.Sms.ADDRESS} LIKE ?)")
                smsSelectionArgsList.add("%$searchQuery%")
                smsSelectionArgsList.add("%$searchQuery%")
            }
            if (!messageIds.isNullOrEmpty()) {
                if (isNotEmpty()) append(" AND ")
                val placeholders = messageIds.joinToString(",") { "?" }
                append("${Telephony.Sms._ID} IN ($placeholders)")
                smsSelectionArgsList.addAll(messageIds.map { it.toString() })
            }
        }.takeIf { it.isNotEmpty() }

        val smsSelectionArgs = smsSelectionArgsList.takeIf { it.isNotEmpty() }?.toTypedArray()

        val collectedMessageIds = mutableListOf<Long>()

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
            smsSelection,
            smsSelectionArgs,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(Telephony.Sms._ID)
            val threadIdIndex = cursor.getColumnIndex(Telephony.Sms.THREAD_ID)
            val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE)
            val typeIndex = cursor.getColumnIndex(Telephony.Sms.TYPE)
            val readIndex = cursor.getColumnIndex(Telephony.Sms.READ)
            val subIdIndex = cursor.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)

            while (cursor.moveToNext()) {
                val systemId = cursor.getLong(idIndex)
                collectedMessageIds.add(systemId)

                val subscriptionId = if (subIdIndex != -1 && !cursor.isNull(subIdIndex)) {
                    cursor.getInt(subIdIndex)
                } else null

                val simSlot = subscriptionId?.let { subId ->
                    simManager.getSlotForSubscriptionId(subId)
                }

                messages.add(Message(
                    id = systemId,
                    threadId = cursor.getLong(threadIdIndex),
                    phoneNumber = cursor.getString(addressIndex) ?: "Unknown",
                    contactName = null,
                    body = cursor.getString(bodyIndex),
                    timestamp = cursor.getLong(dateIndex),
                    type = cursor.getInt(typeIndex),
                    category = MessageCategory.PERSONAL,
                    isRead = cursor.getInt(readIndex) == 1,
                    isSent = cursor.getInt(typeIndex) == 2,
                    isDelivered = true,
                    simSlot = simSlot,
                    isSpam = false,
                    spamScore = 0f,
                    isMms = false,
                    attachmentUris = emptyList(),
                    attachmentTypes = emptyList()
                ))
            }
        }

        // Batch load metadata for all messages
        val metadataMap = if (collectedMessageIds.isNotEmpty()) {
            messageMetadataDao.getMetadataForMessages(collectedMessageIds)
                .associateBy { it.systemMessageId }
        } else {
            emptyMap()
        }

        val phoneNumbers = messages.map { it.phoneNumber }.toSet()
        val contactNamesMap = contactResolver.batchResolveContactNamesOptimized(phoneNumbers)

        messages.replaceAll { message ->
            val metadata = metadataMap[message.id]
            val contactName = contactNamesMap[message.phoneNumber]

            if (metadata != null || contactName != null) {
                message.copy(
                    category = metadata?.category ?: message.category,
                    isSpam = (metadata?.spamScore ?: 0f) > 0.5f,
                    spamScore = metadata?.spamScore ?: 0f,
                    contactName = contactName
                )
            } else {
                message
            }
        }

        // 2. Query MMS messages
        val mmsUri = Uri.parse("content://mms")
        val mmsSelectionArgsList = mutableListOf<String>()
        val mmsSelection = buildString {
            if (threadId != null) {
                append("thread_id = ?")
                mmsSelectionArgsList.add(threadId.toString())
            }
            if (unreadOnly) {
                if (isNotEmpty()) append(" AND ")
                append("read = 0")
            }
            if (!messageIds.isNullOrEmpty()) {
                if (isNotEmpty()) append(" AND ")
                val placeholders = messageIds.joinToString(",") { "?" }
                append("_id IN ($placeholders)")
                mmsSelectionArgsList.addAll(messageIds.map { it.toString() })
            }
        }.takeIf { it.isNotEmpty() }

        val mmsSelectionArgs = mmsSelectionArgsList.takeIf { it.isNotEmpty() }?.toTypedArray()

        val mmsIds = mutableListOf<Long>()

        context.contentResolver.query(
            mmsUri,
            arrayOf("_id", "thread_id", "date", "msg_box", "read", "sub", "sub_id", "st", "m_type"),
            mmsSelection,
            mmsSelectionArgs,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex("_id")
            val threadIdIndex = cursor.getColumnIndex("thread_id")
            val dateIndex = cursor.getColumnIndex("date")
            val msgBoxIndex = cursor.getColumnIndex("msg_box")
            val readIndex = cursor.getColumnIndex("read")
            val subIndex = cursor.getColumnIndex("sub")
            val subIdIndex = cursor.getColumnIndex("sub_id")
            val stIndex = cursor.getColumnIndex("st")
            val mTypeIndex = cursor.getColumnIndex("m_type")

            while (cursor.moveToNext()) {
                val mmsId = cursor.getLong(idIndex)
                val mmsThreadId = cursor.getLong(threadIdIndex)
                val date = cursor.getLong(dateIndex) * 1000
                val msgBox = cursor.getInt(msgBoxIndex)
                val isRead = cursor.getInt(readIndex) == 1
                
                val st = if (stIndex != -1 && !cursor.isNull(stIndex)) cursor.getInt(stIndex) else -1
                val mType = if (mTypeIndex != -1 && !cursor.isNull(mTypeIndex)) cursor.getInt(mTypeIndex) else -1

                mmsIds.add(mmsId)

                val subscriptionId = when {
                    subIdIndex != -1 && !cursor.isNull(subIdIndex) -> cursor.getInt(subIdIndex)
                    subIndex != -1 && !cursor.isNull(subIndex) -> cursor.getInt(subIndex)
                    else -> null
                }

                val simSlot = subscriptionId?.let { subId ->
                    simManager.getSlotForSubscriptionId(subId)
                }

                val type = when (msgBox) {
                    1 -> 1; 2 -> 2; 3 -> 3; 4 -> 4; 5 -> 5; else -> msgBox
                }

                val isSent = msgBox == 2
                // Delivery logic: If status is 129 (retrieved) or 130 (forwarded) or it's a delivery report
                val isDelivered = st == 129 || st == 130 || (isSent && st != -1) // Basic heuristic, otherwise fall back to isSent for MMS if needed
                val isFailed = msgBox == 5

                messages.add(Message(
                    id = mmsId,
                    threadId = mmsThreadId,
                    phoneNumber = "Unknown",
                    contactName = null,
                    body = null,
                    timestamp = date,
                    type = type,
                    category = MessageCategory.PERSONAL,
                    isRead = isRead,
                    isSent = isSent,
                    isDelivered = isDelivered,
                    simSlot = simSlot,
                    isSpam = false,
                    spamScore = 0f,
                    isMms = true,
                    attachmentUris = emptyList(),
                    attachmentTypes = emptyList()
                ))
            }
        }

        // Batch MMS details
        if (mmsIds.isNotEmpty() && !lightweightMms) {
            val addressMap = batchGetMmsAddresses(mmsIds)
            val bodyAttachmentMap = batchGetMmsBodiesAndAttachments(mmsIds)

            messages.replaceAll { message ->
                if (message.isMms) {
                    val (body, attUris, attTypes) = bodyAttachmentMap[message.id] ?: Triple(null, emptyList(), emptyList())
                    message.copy(
                        phoneNumber = addressMap[message.id] ?: "Unknown",
                        body = body,
                        attachmentUris = attUris,
                        attachmentTypes = attTypes
                    )
                } else message
            }
        }

        if (mmsIds.isNotEmpty() && !lightweightMms) {
            val mmsMetadataMap = messageMetadataDao.getMetadataForMessages(mmsIds)
                .associateBy { it.systemMessageId }

            val mmsPhoneNumbers = messages.filter { it.isMms }.map { it.phoneNumber }.toSet()
            val mmsContactNamesMap = contactResolver.batchResolveContactNamesOptimized(mmsPhoneNumbers)

            messages.replaceAll { message ->
                if (message.isMms) {
                    val metadata = mmsMetadataMap[message.id]
                    val contactName = mmsContactNamesMap[message.phoneNumber]

                    if (metadata != null || contactName != null) {
                        message.copy(
                            category = metadata?.category ?: message.category,
                            isSpam = (metadata?.spamScore ?: 0f) > 0.5f,
                            spamScore = metadata?.spamScore ?: 0f,
                            contactName = contactName
                        )
                    } else {
                        message
                    }
                } else {
                    message
                }
            }
        }

        messages.sortBy { it.timestamp }

        val duration = System.currentTimeMillis() - startTime
        DebugLogger.d(TAG, "querySystemMessages completed in ${duration}ms, found ${messages.size} messages (SMS + MMS)")

        val uniqueMessages = messages.distinctBy { "${it.id}_${it.isMms}" }
        if (uniqueMessages.size != messages.size) {
            Log.w(TAG, "⚠️ Found ${messages.size - uniqueMessages.size} true duplicate messages, removing")
        }

        return uniqueMessages
    }

    // ============================================
    // Helper Query Functions
    // ============================================

    fun queryUnreadCountOptimized(): Int {
        return try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf("COUNT(*)"),
                "${Telephony.Sms.READ} = 0",
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            } ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error counting unread messages", e)
            0
        }
    }

    suspend fun queryMessageById(messageId: Long): Message? {
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            null,
            "${Telephony.Sms._ID} = ?",
            arrayOf(messageId.toString()),
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val idIndex = it.getColumnIndex(Telephony.Sms._ID)
                val threadIdIndex = it.getColumnIndex(Telephony.Sms.THREAD_ID)
                val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
                val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)
                val readIndex = it.getColumnIndex(Telephony.Sms.READ)

                val phoneNumber = it.getString(addressIndex) ?: ""
                val type = it.getInt(typeIndex)
                
                val metadata = messageMetadataDao.getMetadata(it.getLong(idIndex))

                return Message(
                    id = it.getLong(idIndex),
                    threadId = it.getLong(threadIdIndex),
                    phoneNumber = phoneNumber,
                    contactName = contactResolver.resolveContactName(phoneNumber),
                    body = it.getString(bodyIndex),
                    timestamp = it.getLong(dateIndex),
                    type = type,
                    isStarred = true, // Only called from getStarredMessages()
                    category = metadata?.category ?: MessageCategory.PERSONAL,
                    isRead = it.getInt(readIndex) == 1,
                    isSent = type == 2,
                    isDelivered = false,
                    simSlot = null,
                    isSpam = (metadata?.spamScore ?: 0f) > 0.5f,
                    spamScore = metadata?.spamScore ?: 0f,
                    isMms = false,
                    attachmentUris = emptyList(),
                    attachmentTypes = emptyList()
                )
            }
        }
        return null
    }

    // ============================================
    // Batch Helpers
    // ============================================

    private fun resolveCanonicalAddresses(ids: Set<Long>, cache: MutableMap<Long, String>) {
        if (ids.isEmpty()) return
        try {
            ids.chunked(50).forEach { chunk ->
                val placeholders = chunk.joinToString(",") { "?" }
                context.contentResolver.query(
                    Uri.parse("content://mms-sms/canonical-addresses"),
                    arrayOf("_id", "address"),
                    "_id IN ($placeholders)",
                    chunk.map { it.toString() }.toTypedArray(),
                    null
                )?.use { c ->
                    while(c.moveToNext()) {
                        cache[c.getLong(0)] = c.getString(1)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving canonical addresses", e)
        }
    }

    private suspend fun batchGetUnreadCounts(threadIds: List<Long>): Map<Long, Int> {
        val countsMap = mutableMapOf<Long, Int>()
        if (threadIds.isEmpty()) return countsMap
        threadIds.forEach { countsMap[it] = 0 }

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
                val threadIdIndex = cursor.getColumnIndex(Telephony.Sms.THREAD_ID)
                while (cursor.moveToNext()) {
                    val threadId = cursor.getLong(threadIdIndex)
                    countsMap[threadId] = (countsMap[threadId] ?: 0) + 1
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error batch getting unread counts", e)
            threadIds.forEach { threadId ->
                countsMap[threadId] = getUnreadCountForThreadFast(threadId)
            }
        }

        return countsMap
    }

    private suspend fun batchGetMessageCounts(threadIds: List<Long>): Map<Long, Int> {
        val countsMap = mutableMapOf<Long, Int>()
        if (threadIds.isEmpty()) return countsMap

        try {
            val placeholders = threadIds.joinToString(",") { "?" }
            val selection = "${Telephony.Sms.THREAD_ID} IN ($placeholders)"
            val selectionArgs = threadIds.map { it.toString() }.toTypedArray()

            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.THREAD_ID),
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val threadIdIndex = cursor.getColumnIndex(Telephony.Sms.THREAD_ID)
                while (cursor.moveToNext()) {
                    val threadId = cursor.getLong(threadIdIndex)
                    countsMap[threadId] = (countsMap[threadId] ?: 0) + 1
                }
            }
            threadIds.forEach { threadId ->
                if (!countsMap.containsKey(threadId)) {
                    countsMap[threadId] = 0
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error batch getting message counts", e)
        }

        return countsMap
    }

    private fun getUnreadCountForThreadFast(threadId: Long): Int {
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

    fun batchGetMmsAddresses(mmsIds: List<Long>): Map<Long, String> {
        if (mmsIds.isEmpty()) return emptyMap()
        val result = mutableMapOf<Long, String>()
        try {
            mmsIds.chunked(500).forEach { chunk ->
                val addrUri = Uri.parse("content://mms").buildUpon().appendPath("addr").build()
                val placeholders = chunk.joinToString(",") { "?" }
                val selection = "msg_id IN ($placeholders)"
                val selectionArgs = chunk.map { it.toString() }.toTypedArray()
                context.contentResolver.query(
                    addrUri, arrayOf("msg_id", "address", "type"),
                    selection, selectionArgs, null
                )?.use { cursor ->
                    val msgIdIndex = cursor.getColumnIndex("msg_id")
                    val addressIndex = cursor.getColumnIndex("address")
                    val typeIndex = cursor.getColumnIndex("type")
                    while (cursor.moveToNext()) {
                        val msgId = cursor.getLong(msgIdIndex)
                        val addrType = cursor.getInt(typeIndex)
                        if ((addrType == 151 || addrType == 137) && !result.containsKey(msgId)) {
                            result[msgId] = cursor.getString(addressIndex) ?: "Unknown"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error batch getting MMS addresses", e)
        }
        return result
    }

    fun batchGetMmsBodiesAndAttachments(mmsIds: List<Long>): Map<Long, Triple<String?, List<String>, List<String>>> {
        if (mmsIds.isEmpty()) return emptyMap()
        val bodies = mutableMapOf<Long, String?>()
        val attachmentUris = mutableMapOf<Long, MutableList<String>>()
        val attachmentTypes = mutableMapOf<Long, MutableList<String>>()

        try {
            mmsIds.chunked(500).forEach { chunk ->
                val partUri = Uri.parse("content://mms/part")
                val placeholders = chunk.joinToString(",") { "?" }
                val selection = "mid IN ($placeholders)"
                val selectionArgs = chunk.map { it.toString() }.toTypedArray()
                context.contentResolver.query(
                    partUri, arrayOf("_id", "mid", "ct", "text"),
                    selection, selectionArgs, null
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndex("_id")
                    val midIndex = cursor.getColumnIndex("mid")
                    val ctIndex = cursor.getColumnIndex("ct")
                    val textIndex = cursor.getColumnIndex("text")

                    while (cursor.moveToNext()) {
                        val mmsId = cursor.getLong(midIndex)
                        val contentType = cursor.getString(ctIndex) ?: continue

                        if (contentType == "text/plain") {
                            if (!bodies.containsKey(mmsId) && textIndex >= 0) {
                                bodies[mmsId] = cursor.getString(textIndex)
                            }
                        } else if (contentType != "application/smil" &&
                                   !contentType.startsWith("application/vnd.wap")) {
                            val partId = cursor.getLong(idIndex)
                            attachmentUris.getOrPut(mmsId) { mutableListOf() }
                                .add("content://mms/part/$partId")
                            attachmentTypes.getOrPut(mmsId) { mutableListOf() }
                                .add(contentType)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error batch getting MMS bodies/attachments", e)
        }

        return mmsIds.associateWith { id ->
            Triple(bodies[id], attachmentUris[id] ?: emptyList(), attachmentTypes[id] ?: emptyList())
        }
    }

    suspend fun getLastMessageForThread(threadId: Long): Message? {
        var lastMessage: Message? = null
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID, Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE
                ),
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} DESC LIMIT 1"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    val metadata = messageMetadataDao.getMetadata(id)
                    lastMessage = Message(
                        id = id,
                        threadId = threadId,
                        phoneNumber = cursor.getString(1) ?: "Unknown",
                        contactName = null,
                        body = cursor.getString(2) ?: "",
                        timestamp = cursor.getLong(3),
                        type = cursor.getInt(4),
                        category = metadata?.category ?: MessageCategory.PERSONAL,
                        isRead = true,
                        isSent = cursor.getInt(4) == 2,
                        isDelivered = true,
                        simSlot = null,
                        isSpam = (metadata?.spamScore ?: 0f) > 0.5f,
                        spamScore = metadata?.spamScore ?: 0f,
                        isMms = false,
                        attachmentUris = emptyList(),
                        attachmentTypes = emptyList()
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting last message for thread $threadId", e)
        }
        return lastMessage
    }

    fun getUnreadCountForThread(threadId: Long): Int {
        var count = 0
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString()),
                null
            )?.use { cursor ->
                count = cursor.count
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting unread count for thread $threadId", e)
        }
        return count
    }

    fun getRecipientsForThread(threadId: Long): List<String> {
        val recipients = mutableSetOf<String>()
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS),
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Telephony.Sms.ADDRESS} ASC LIMIT 10"
            )?.use { cursor ->
                val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                if (addressIndex != -1) {
                    while (cursor.moveToNext()) {
                        val address = cursor.getString(addressIndex)
                        if (address != null && address.isNotBlank()) {
                            recipients.add(address)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recipients for thread $threadId", e)
        }
        return recipients.toList()
    }
}
