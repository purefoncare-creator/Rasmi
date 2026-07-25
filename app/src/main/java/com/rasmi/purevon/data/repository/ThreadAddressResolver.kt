package com.rasmi.purevon.data.repository

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves phone numbers/addresses from thread IDs.
 * Tries SMS table → canonical_addresses → MMS addr table.
 * Extracted from MessageRepositoryImpl to reduce class size.
 */
internal class ThreadAddressResolver(
    private val context: Context
) {
    /**
     * الحصول على عنوان المستقبل من threadId
     * يستخدم في الرد السريع من الإشعارات
     */
    suspend fun getAddressFromThreadId(threadId: Long): String? = withContext(Dispatchers.IO) {
        try {
            // ✅ Step 1: Try SMS table — look for any message with a valid address
            val smsAddress = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS),
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.ADDRESS} IS NOT NULL AND ${Telephony.Sms.ADDRESS} != ''",
                arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} DESC LIMIT 1"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                    if (addressIndex != -1) cursor.getString(addressIndex) else null
                } else null
            }

            if (!smsAddress.isNullOrBlank() && smsAddress != "Unknown") {
                return@withContext smsAddress
            }

            // ✅ Step 2: Try canonical_addresses via thread's recipient_ids
            val canonicalAddress = resolveAddressFromCanonical(threadId)
            if (!canonicalAddress.isNullOrBlank()) {
                return@withContext canonicalAddress
            }

            // ✅ Step 3: Try MMS addr table
            val mmsAddress = context.contentResolver.query(
                Uri.parse("content://mms"),
                arrayOf("_id"),
                "thread_id = ?",
                arrayOf(threadId.toString()),
                "date DESC LIMIT 1"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val mmsId = cursor.getLong(0)
                    getMmsAddressById(mmsId)
                } else null
            }

            mmsAddress
        } catch (e: Exception) {
            Log.e("MessageRepository", "Error getting address from threadId: $threadId", e)
            null
        }
    }

    /**
     * ✅ Resolve phone number from thread's canonical_addresses
     */
    private fun resolveAddressFromCanonical(threadId: Long): String? {
        try {
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
                    val idx = cursor.getColumnIndex("recipient_ids")
                    if (idx >= 0) recipientIds = cursor.getString(idx)
                }
            }

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
                            val addrIdx = cursor.getColumnIndex("address")
                            if (addrIdx >= 0) {
                                val address = cursor.getString(addrIdx)
                                if (!address.isNullOrBlank() && address != "Unknown") {
                                    return address
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MessageRepository", "Error resolving canonical address for threadId=$threadId", e)
        }
        return null
    }

    /**
     * ✅ Get phone number from MMS addr table (type 137=FROM, 151=TO)
     */
    private fun getMmsAddressById(mmsId: Long): String? {
        try {
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
            Log.e("MessageRepository", "Error getting MMS address for mmsId=$mmsId", e)
        }
        return null
    }
}
