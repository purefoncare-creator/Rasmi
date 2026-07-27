package com.rasmi.purevon.data.repository

import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.rasmi.purevon.data.local.entity.MessageCategory
import com.rasmi.purevon.domain.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "SystemQueryHelper"

// ============================================
// Batch Helper Functions
// ============================================

internal fun SystemQueryHelper.resolveCanonicalAddresses(ids: Set<Long>, cache: MutableMap<Long, String>) {
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

internal suspend fun SystemQueryHelper.batchGetUnreadCounts(threadIds: List<Long>): Map<Long, Int> {
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

internal suspend fun SystemQueryHelper.batchGetMessageCounts(threadIds: List<Long>): Map<Long, Int> {
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

internal fun SystemQueryHelper.getUnreadCountForThreadFast(threadId: Long): Int {
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

internal fun SystemQueryHelper.batchGetMmsAddresses(mmsIds: List<Long>): Map<Long, String> {
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

internal fun SystemQueryHelper.batchGetMmsBodiesAndAttachments(mmsIds: List<Long>): Map<Long, Triple<String?, List<String>, List<String>>> {
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

// ============================================
// Thread Helpers
// ============================================

internal suspend fun SystemQueryHelper.getLastMessageForThread(threadId: Long): Message? {
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

internal fun SystemQueryHelper.getUnreadCountForThread(threadId: Long): Int {
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

internal fun SystemQueryHelper.getRecipientsForThread(threadId: Long): List<String> {
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
