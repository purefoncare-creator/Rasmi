package com.rasmi.purevon.data.repository

import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.rasmi.purevon.data.local.entity.MessageCategory
import com.rasmi.purevon.domain.model.Message
import com.rasmi.purevon.util.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "SystemQueryHelper"

// ============================================
// Message Queries
// ============================================

/**
 * Quik-master approach: exactly 4 ContentProvider queries total.
 */
internal suspend fun SystemQueryHelper.queryAllSystemMessages(): List<Message> = withContext(Dispatchers.IO) {
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
                val isFailed = msgBox == 5
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
internal suspend fun SystemQueryHelper.querySystemMessages(
    threadId: Long? = null,
    unreadOnly: Boolean = false,
    lightweightMms: Boolean = false,
    searchQuery: String? = null,
    messageIds: List<Long>? = null
): List<Message> = withContext(Dispatchers.IO) {
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
            val isDelivered = st == 129 || st == 130 || (isSent && st != -1)
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

    uniqueMessages
}
