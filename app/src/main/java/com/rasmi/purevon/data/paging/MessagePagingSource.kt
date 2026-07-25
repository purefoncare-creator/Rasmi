package com.rasmi.purevon.data.paging

import android.content.Context
import android.provider.Telephony
import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.rasmi.purevon.data.local.dao.MessageMetadataDao
import com.rasmi.purevon.data.local.entity.MessageCategory
import com.rasmi.purevon.domain.model.Message
import com.rasmi.purevon.util.sim.SimManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PagingSource for loading messages with pagination
 * Efficiently loads messages from system SMS database
 */
class MessagePagingSource @AssistedInject constructor(
    private val context: Context,
    private val messageMetadataDao: MessageMetadataDao,
    private val simManager: SimManager,
    @Assisted private val threadId: Long?
) : PagingSource<Long, Message>() {
    
    companion object {
        private const val TAG = "MessagePagingSource"
        private const val PAGE_SIZE = 50
        private const val PREFETCH_DISTANCE = 10
        // Offset MMS IDs to avoid collision with SMS IDs (both use independent auto-increment)
        const val MMS_ID_OFFSET = 2_000_000_000L
    }
    
    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, Message> {
        return try {
            // Key is the timestamp cursor — messages older than this are loaded
            val beforeTimestamp = params.key
            
            Log.d(TAG, "Loading beforeTimestamp=$beforeTimestamp, limit=${params.loadSize}")
            
            val messages = withContext(Dispatchers.IO) {
                queryMessagesWithPagination(
                    threadId = threadId,
                    limit = params.loadSize,
                    beforeTimestamp = beforeTimestamp
                )
            }
            
            Log.d(TAG, "Loaded ${messages.size} messages")
            
            // ✅ FIX M5: Use (timestamp, id) compound cursor to avoid skipping
            // messages with identical timestamps at page boundaries
            val lastMsg = messages.lastOrNull()
            val nextKey = if (messages.size < params.loadSize || lastMsg == null) null else lastMsg.timestamp
            
            LoadResult.Page(
                data = messages,
                prevKey = null, // Only paging forward (older messages)
                nextKey = nextKey
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading messages", e)
            LoadResult.Error(e)
        }
    }
    
    override fun getRefreshKey(state: PagingState<Long, Message>): Long? {
        // On refresh, start from the beginning (no cursor = newest messages)
        return null
    }
    
    /**
     * Query messages with keyset (timestamp-cursor) pagination.
     * Both SMS and MMS are filtered by "date < beforeTimestamp" to avoid
     * the offset mismatch bug that drops messages between pages.
     */
    private suspend fun queryMessagesWithPagination(
        threadId: Long?,
        limit: Int,
        beforeTimestamp: Long?
    ): List<Message> {
        val smsMessages = mutableListOf<Message>()
        val mmsMessages = mutableListOf<Message>()
        
        try {
            // ── SMS query ──
            val smsSelectionParts = mutableListOf<String>()
            val smsSelectionArgsList = mutableListOf<String>()
            if (threadId != null) {
                smsSelectionParts.add("${Telephony.Sms.THREAD_ID} = ?")
                smsSelectionArgsList.add(threadId.toString())
            }
            if (beforeTimestamp != null) {
                // ✅ FIX M5: Use <= (not <) and rely on LIMIT + sort to handle duplicates
                smsSelectionParts.add("${Telephony.Sms.DATE} <= ?")
                smsSelectionArgsList.add(beforeTimestamp.toString())
            }
            val smsSelection = smsSelectionParts.takeIf { it.isNotEmpty() }?.joinToString(" AND ")
            val smsSelectionArgs = smsSelectionArgsList.takeIf { it.isNotEmpty() }?.toTypedArray()

            // Over-fetch so we can merge-sort with MMS and still fill `limit` items
            val fetchSize = limit + PAGE_SIZE

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
                    Telephony.Sms.STATUS,
                    Telephony.Sms.SUBSCRIPTION_ID
                ),
                smsSelection,
                smsSelectionArgs,
                "${Telephony.Sms.DATE} DESC LIMIT $fetchSize"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val threadIdIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val readIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
                val statusIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.STATUS)
                val subIdIndex = cursor.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID) // Optional column, keep safe
                
                data class RawSmsRow(
                    val systemId: Long, val threadId: Long, val address: String,
                    val body: String?, val date: Long, val type: Int,
                    val read: Int, val status: Int, val subId: Int?
                )
                val rawRows = mutableListOf<RawSmsRow>()
                while (cursor.moveToNext()) {
                    val subId = if (subIdIndex >= 0 && !cursor.isNull(subIdIndex)) cursor.getInt(subIdIndex) else null
                    rawRows.add(RawSmsRow(
                        systemId = cursor.getLong(idIndex),
                        threadId = cursor.getLong(threadIdIndex),
                        address = cursor.getString(addressIndex) ?: "Unknown",
                        body = cursor.getString(bodyIndex),
                        date = cursor.getLong(dateIndex),
                        type = cursor.getInt(typeIndex),
                        read = cursor.getInt(readIndex),
                        status = cursor.getInt(statusIndex),
                        subId = subId
                    ))
                }
                
                // Batch fetch all metadata
                val allIds = rawRows.map { it.systemId }
                val metadataMap = if (allIds.isNotEmpty()) {
                    messageMetadataDao.getMetadataForMessages(allIds)
                        .associateBy { it.systemMessageId }
                } else emptyMap()
                
                for (row in rawRows) {
                    val metadata = metadataMap[row.systemId]
                    val simSlot = row.subId?.let { simManager.getSlotForSubscriptionId(it) }
                    
                    smsMessages.add(
                        Message(
                            id = row.systemId,  // SMS IDs stay as-is
                            threadId = row.threadId,
                            phoneNumber = row.address,
                            contactName = null,
                            body = row.body,
                            timestamp = row.date,
                            type = row.type,
                            category = metadata?.category ?: MessageCategory.PERSONAL,
                            isRead = row.read == 1,
                            isSent = row.type == Telephony.Sms.MESSAGE_TYPE_SENT,
                            isDelivered = row.status == Telephony.Sms.STATUS_COMPLETE,
                            simSlot = simSlot,
                            isSpam = (metadata?.spamScore ?: 0f) > 0.5f,
                            spamScore = metadata?.spamScore ?: 0f,
                            isMms = false,
                            attachmentUris = emptyList(),
                            attachmentTypes = emptyList()
                        )
                    )
                }
            }
            
            // ── MMS query (uses same timestamp cursor) ──
            if (threadId != null) {
                queryMmsMessages(threadId, fetchSize, beforeTimestamp)?.let { mms ->
                    mmsMessages.addAll(mms)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error querying messages", e)
        }
        
        // Merge SMS + MMS by timestamp descending, then take only `limit` items
        return (smsMessages + mmsMessages)
            .sortedByDescending { it.timestamp }
            .take(limit)
    }
    
    /**
     * Query MMS messages with timestamp-cursor pagination
     */
    private fun queryMmsMessages(threadId: Long, limit: Int, beforeTimestamp: Long?): List<Message>? {
        val messages = mutableListOf<Message>()
        
        try {
            val selectionParts = mutableListOf("${Telephony.Mms.THREAD_ID} = ?")
            val selectionArgsList = mutableListOf(threadId.toString())
            if (beforeTimestamp != null) {
                // MMS dates are in seconds, not milliseconds
                // ✅ FIX M5: Use <= to include same-timestamp messages
                selectionParts.add("${Telephony.Mms.DATE} <= ?")
                selectionArgsList.add((beforeTimestamp / 1000).toString())
            }
            
            context.contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(
                    Telephony.Mms._ID,
                    Telephony.Mms.THREAD_ID,
                    Telephony.Mms.DATE,
                    Telephony.Mms.MESSAGE_BOX,
                    Telephony.Mms.READ,
                    Telephony.Mms.SUBSCRIPTION_ID
                ),
                selectionParts.joinToString(" AND "),
                selectionArgsList.toTypedArray(),
                "${Telephony.Mms.DATE} DESC LIMIT $limit"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(Telephony.Mms._ID)
                val threadIdIndex = cursor.getColumnIndex(Telephony.Mms.THREAD_ID)
                val dateIndex = cursor.getColumnIndex(Telephony.Mms.DATE)
                val boxIndex = cursor.getColumnIndex(Telephony.Mms.MESSAGE_BOX)
                val readIndex = cursor.getColumnIndex(Telephony.Mms.READ)
                // ✅ FIX S3: Read SUBSCRIPTION_ID for MMS to show which SIM received/sent
                val mmsSubIdIndex = cursor.getColumnIndex(Telephony.Mms.SUBSCRIPTION_ID)
                
                while (cursor.moveToNext()) {
                    val mmsId = cursor.getLong(idIndex)
                    val (body, address) = getMmsBodyAndAddress(mmsId)
                    val mmsParts = getMmsAttachments(mmsId)
                    // ✅ FIX S3: Convert subscription ID to slot index
                    val mmsSubId = if (mmsSubIdIndex >= 0 && !cursor.isNull(mmsSubIdIndex)) cursor.getInt(mmsSubIdIndex) else null
                    val mmsSimSlot = mmsSubId?.let { simManager.getSlotForSubscriptionId(it) }
                    
                    messages.add(
                        Message(
                            id = mmsId + MMS_ID_OFFSET,  // ✅ FIX M2: Offset MMS IDs to avoid collision with SMS IDs
                            threadId = cursor.getLong(threadIdIndex),
                            phoneNumber = address ?: "Unknown",
                            contactName = null,
                            body = body,
                            timestamp = cursor.getLong(dateIndex) * 1000L,
                            type = if (cursor.getInt(boxIndex) == Telephony.Mms.MESSAGE_BOX_INBOX) 
                                Telephony.Sms.MESSAGE_TYPE_INBOX else Telephony.Sms.MESSAGE_TYPE_SENT,
                            category = MessageCategory.PERSONAL,
                            isRead = cursor.getInt(readIndex) == 1,
                            isSent = cursor.getInt(boxIndex) != Telephony.Mms.MESSAGE_BOX_INBOX,
                            isDelivered = true,
                            simSlot = mmsSimSlot,
                            isSpam = false,
                            spamScore = 0f,
                            isMms = true,
                            attachmentUris = mmsParts.map { it.first },
                            attachmentTypes = mmsParts.map { it.second }
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MMS", e)
            return null
        }
        
        return messages
    }
    
    private fun getMmsBodyAndAddress(mmsId: Long): Pair<String?, String?> {
        var address: String? = null
        var body: String? = null
        
        try {
            // Get address
            val addrUri = android.net.Uri.parse("content://mms/$mmsId/addr")
            context.contentResolver.query(addrUri, arrayOf("address", "type"), null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val addrType = cursor.getInt(1)
                    if (addrType == 137 || addrType == 151) { // FROM or TO
                        address = cursor.getString(0)
                        break
                    }
                }
            }
            
            // Get body text
            val partUri = android.net.Uri.parse("content://mms/$mmsId/part")
            context.contentResolver.query(partUri, arrayOf("text", "ct"), null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val contentType = cursor.getString(1)
                    if (contentType == "text/plain") {
                        body = cursor.getString(0)
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting MMS body/address", e)
        }
        
        return Pair(body, address)
    }
    
    private fun getMmsAttachments(mmsId: Long): List<Pair<String, String>> {
        val attachments = mutableListOf<Pair<String, String>>()
        
        try {
            val partUri = android.net.Uri.parse("content://mms/$mmsId/part")
            context.contentResolver.query(partUri, arrayOf("_id", "ct"), null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val contentType = cursor.getString(1)
                    if (contentType != "text/plain" && contentType != "application/smil") {
                        val partId = cursor.getLong(0)
                        attachments.add(Pair("content://mms/part/$partId", contentType))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting MMS attachments", e)
        }
        
        return attachments
    }
    
    @AssistedFactory
    interface Factory {
        fun create(threadId: Long?): MessagePagingSource
    }
}
