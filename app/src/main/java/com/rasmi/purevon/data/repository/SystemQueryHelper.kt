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
    internal val context: Context,
    internal val contactResolver: ContactResolver,
    internal val simManager: SimManager,
    internal val messageMetadataDao: MessageMetadataDao,
    internal val conversationPreferencesDao: ConversationPreferencesDao
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
}
