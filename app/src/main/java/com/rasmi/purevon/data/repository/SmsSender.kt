package com.rasmi.purevon.data.repository

import android.Manifest
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import com.rasmi.purevon.data.local.entity.MessageCategory
import com.rasmi.purevon.data.local.entity.MessageMetadataEntity
import com.rasmi.purevon.data.local.entity.MessagePartStatus
import com.rasmi.purevon.data.local.entity.MultipartMessageStatus
import com.rasmi.purevon.domain.model.MessageError
import com.rasmi.purevon.domain.model.MessageResult
import com.rasmi.purevon.receiver.DeliveryStatusReceiver
import com.rasmi.purevon.receiver.SentStatusReceiver
import com.rasmi.purevon.util.DebugLogger
import com.rasmi.purevon.util.ErrorHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Handles SMS message sending operations.
 * Extracted from MessageRepositoryImpl to reduce class size (~300 lines).
 *
 * Thread safety: The caller (MessageRepositoryImpl) holds sendMessageMutex.
 */
internal class SmsSender(
    private val context: Context,
    private val cachedMessageDao: com.rasmi.purevon.data.local.dao.CachedMessageDao,
    private val messageMetadataDao: com.rasmi.purevon.data.local.dao.MessageMetadataDao,
    private val repositoryScope: CoroutineScope
) {

    companion object {
        private const val TAG = "SmsSender"
    }

    /**
     * Get or create thread ID for a phone number.
     * Uses content://mms-sms/threadID and Telephony.Threads API.
     */
    fun getOrCreateThreadId(phoneNumber: String): Long {
        return try {
            // Try to get existing thread first
            val uri = Uri.parse("content://mms-sms/threadID")
            val cursor = context.contentResolver.query(
                uri.buildUpon().appendQueryParameter("recipient", phoneNumber).build(),
                arrayOf("_id"),
                null,
                null,
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val threadId = it.getLong(0)
                    if (threadId > 0) {
                        return threadId
                    }
                }
            }

            // If no thread exists, create one using the system API
            val threadId = Telephony.Threads.getOrCreateThreadId(context, phoneNumber)
            DebugLogger.d(TAG, "Created new thread ID: $threadId for ${DebugLogger.maskPhoneNumber(phoneNumber)}")
            threadId
        } catch (e: Exception) {
            Log.e(TAG, "Error getting/creating thread ID", e)
            // Last resort: try to create thread
            try {
                Telephony.Threads.getOrCreateThreadId(context, phoneNumber)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to create thread even with fallback", e2)
                0L
            }
        }
    }

    /**
     * Send an SMS message.
     * @param onSent callback invoked with threadId after send for post-send sync
     */
    suspend fun sendSms(
        phoneNumber: String,
        message: String,
        simSlot: Int?,
        onSent: suspend (threadId: Long) -> Unit
    ): MessageResult<Long> {
        // Validate permissions
        val permissionCheck = ErrorHandler.checkPermission(context, Manifest.permission.SEND_SMS)
        if (permissionCheck.isFailure) {
            return MessageResult.Failure(permissionCheck.errorOrNull() ?: MessageError.PermissionError(Manifest.permission.SEND_SMS, "Permission denied"))
        }

        // Validate phone number
        val numberValidation = ErrorHandler.validatePhoneNumber(phoneNumber)
        if (numberValidation.isFailure) {
            return MessageResult.Failure(numberValidation.errorOrNull() ?: MessageError.InvalidNumberError(phoneNumber, "Invalid phone number"))
        }
        val validNumber = numberValidation.getOrNull() ?: return MessageResult.Failure(MessageError.InvalidNumberError(phoneNumber, "Invalid phone number"))

        // Validate message size
        val sizeCheck = ErrorHandler.checkMessageSize(message, emptyList<String>())
        if (sizeCheck.isFailure) {
            return MessageResult.Failure(sizeCheck.errorOrNull() ?: MessageError.MessageTooLargeError(actualSize = message.toByteArray().size.toLong(), maxSize = 0))
        }

        return try {
            // First, get or create thread ID
            val threadId = getOrCreateThreadId(validNumber)

            // Guard: threadId == 0 means getOrCreateThreadId failed completely.
            if (threadId == 0L) {
                Log.e(TAG, "Failed to get or create thread ID for $validNumber — aborting send")
                return MessageResult.Failure(
                    MessageError.ThreadNotFoundError(0, "Failed to get/create thread for $validNumber")
                )
            }

            // Optimistic UI Insert — user sees "sent" instantly
            val tempId = -System.currentTimeMillis() // Negative ID to avoid collision
            val timestamp = System.currentTimeMillis()

            val tempMessage = com.rasmi.purevon.data.local.entity.CachedMessageEntity(
                id = tempId,
                threadId = threadId,
                phoneNumber = validNumber,
                contactName = null,
                body = message,
                timestamp = timestamp,
                type = 2, // MESSAGE_TYPE_SENT
                category = MessageCategory.PERSONAL,
                isRead = true,
                isSent = false, // Not sent yet
                isDelivered = false,
                simSlot = simSlot,
                isSpam = false,
                spamScore = 0f,
                isMms = false,
                attachmentUris = emptyList(),
                attachmentTypes = emptyList(),
                status = "SENDING",
                isScheduled = false,
                scheduledTime = null,
                scheduleId = null
            )

            // ✅ FIX #18: Await cache insert before sending to prevent race condition
            // Previously used fire-and-forget launch{}, so the message could be sent
            // before it appeared in the cache (optimistic UI wouldn't show it)
            cachedMessageDao.insert(tempMessage)

            DebugLogger.d(TAG, "Sending SMS:")
            DebugLogger.d(TAG, "  - Phone: $validNumber")
            DebugLogger.d(TAG, "  - simSlot param: $simSlot")

            // ✅ FIX: Validate subscription ID before using it.
            // INVALID_SUBSCRIPTION_ID (Integer.MAX_VALUE) or <= 0 must fall back to default.
            val validSubId = simSlot?.takeIf { subId ->
                subId > 0 && subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID &&
                (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP_MR1 ||
                 try {
                     val subMgr = context.getSystemService(android.content.Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                     subMgr?.activeSubscriptionInfoList?.any { it.subscriptionId == subId } == true
                 } catch (_: SecurityException) { true /* assume valid if no permission to check */ })
            }
            if (simSlot != null && validSubId == null) {
                Log.w(TAG, "  - Invalid subscriptionId $simSlot, falling back to default SmsManager")
            }

            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (validSubId != null) {
                    DebugLogger.d(TAG, "  - Using custom SIM slot: $validSubId")
                    context.getSystemService(SmsManager::class.java)
                        .createForSubscriptionId(validSubId)
                } else {
                    DebugLogger.d(TAG, "  - Using default SmsManager")
                    context.getSystemService(SmsManager::class.java)
                }
            } else if (validSubId != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                DebugLogger.d(TAG, "  - Using SIM slot (legacy): $validSubId")
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(validSubId)
            } else {
                DebugLogger.d(TAG, "  - Using default SmsManager (legacy)")
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            // Insert the message into system DB BEFORE sending
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, validNumber)
                put(Telephony.Sms.BODY, message)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.DATE_SENT, timestamp)
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.THREAD_ID, threadId)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_PENDING)
                // ✅ FIX S4: Record which SIM was used so it shows correctly in conversation
                if (validSubId != null) {
                    put(Telephony.Sms.SUBSCRIPTION_ID, validSubId)
                }
            }

            val messageUri = context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
            val messageId = messageUri?.lastPathSegment?.toLongOrNull()

            if (messageId == null || messageId <= 0) {
                Log.e(TAG, "Failed to insert message into database")
                return MessageResult.Failure(
                    MessageError.DatabaseError("insert", "Failed to insert message into database")
                )
            }

            DebugLogger.d(TAG, "Message inserted with ID: $messageId, now sending...")

            // Create PendingIntents for sent and delivery tracking.
            // Use (messageId and 0x7FFF_FFFF) to avoid Long→Int overflow.
            val safeReqCode = (messageId and 0x7FFF_FFFFL).toInt()
            val sentIntent = PendingIntent.getBroadcast(
                context,
                safeReqCode,
                Intent(context, SentStatusReceiver::class.java).apply {
                    action = SentStatusReceiver.ACTION_SMS_SENT_STATUS
                    putExtra(SentStatusReceiver.EXTRA_MESSAGE_ID, messageId)
                    putExtra(SentStatusReceiver.EXTRA_PHONE_NUMBER, validNumber)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // ✅ FIX #60: Use bitwise OR instead of +100_000 to avoid collision
            val deliveredIntent = PendingIntent.getBroadcast(
                context,
                safeReqCode or 0x40000000.toInt(),
                Intent(context, DeliveryStatusReceiver::class.java).apply {
                    action = DeliveryStatusReceiver.ACTION_SMS_DELIVERY_STATUS
                    putExtra(DeliveryStatusReceiver.EXTRA_MESSAGE_ID, messageId)
                    putExtra(DeliveryStatusReceiver.EXTRA_PHONE_NUMBER, validNumber)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // Always use divideMessage() — handles Arabic/English/Emoji automatically
            val parts = smsManager.divideMessage(message)
            DebugLogger.d(TAG, "Message divided into ${parts.size} part(s)")

            if (parts.size > 1) {
                // Multipart message
                val multipartStatus = MultipartMessageStatus(
                    totalParts = parts.size,
                    parts = List(parts.size) { i -> MessagePartStatus(i, isSent = false) }
                )

                messageMetadataDao.insertMetadata(
                    MessageMetadataEntity(
                        systemMessageId = messageId,
                        multipartStatus = multipartStatus
                    )
                )

                val sentIntents = ArrayList<PendingIntent>()
                val deliveredIntents = ArrayList<PendingIntent>()

                for (i in parts.indices) {
                    // Use 22 bits of messageId + 8 bits of partIndex = 30 bits (fits in Int)
                    val partSentReqCode = ((safeReqCode and 0x3FFFFF) shl 8) or (i and 0xFF)
                    val partSentIntent = PendingIntent.getBroadcast(
                        context,
                        partSentReqCode,
                        Intent(context, SentStatusReceiver::class.java).apply {
                            action = SentStatusReceiver.ACTION_SMS_SENT_STATUS
                            putExtra(SentStatusReceiver.EXTRA_MESSAGE_ID, messageId)
                            putExtra(SentStatusReceiver.EXTRA_PHONE_NUMBER, validNumber)
                            putExtra(SentStatusReceiver.EXTRA_PART_INDEX, i)
                            putExtra(SentStatusReceiver.EXTRA_TOTAL_PARTS, parts.size)
                        },
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        } else {
                            PendingIntent.FLAG_UPDATE_CURRENT
                        }
                    )

                    // Delivery request code — offset by 0x40000000 to avoid collision with sent intents
                    val partDeliveredReqCode = ((safeReqCode and 0x3FFFFF) shl 8) or (i and 0xFF) or 0x40000000
                    val partDeliveredIntent = PendingIntent.getBroadcast(
                        context,
                        partDeliveredReqCode,
                        Intent(context, DeliveryStatusReceiver::class.java).apply {
                            action = DeliveryStatusReceiver.ACTION_SMS_DELIVERY_STATUS
                            putExtra(DeliveryStatusReceiver.EXTRA_MESSAGE_ID, messageId)
                            putExtra(DeliveryStatusReceiver.EXTRA_PHONE_NUMBER, validNumber)
                            putExtra("part_index", i)
                            putExtra("total_parts", parts.size)
                        },
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        } else {
                            PendingIntent.FLAG_UPDATE_CURRENT
                        }
                    )

                    sentIntents.add(partSentIntent)
                    deliveredIntents.add(partDeliveredIntent)
                }

                DebugLogger.d(TAG, "Sending multipart message: ${parts.size} parts")
                smsManager.sendMultipartTextMessage(
                    validNumber,
                    null,
                    parts,
                    sentIntents,
                    deliveredIntents
                )
            } else {
                // Single part message
                DebugLogger.d(TAG, "Sending single part message")
                smsManager.sendTextMessage(
                    validNumber,
                    null,
                    message,
                    sentIntent,
                    deliveredIntent
                )
            }

            DebugLogger.d(TAG, "✅ SMS queued for sending to ${DebugLogger.maskPhoneNumber(phoneNumber)} (ID: $messageId)")

            // Post-send: remove optimistic message and sync
            // ✅ FIX M8: Separate temp cleanup from sync — ensure temp is always cleaned
            repositoryScope.launch {
                try {
                    cachedMessageDao.deleteById(tempId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting temp message tempId=$tempId, retrying", e)
                    try { cachedMessageDao.deleteById(tempId) } catch (_: Exception) {}
                }
                try {
                    onSent(threadId)
                    DebugLogger.d(TAG, "✅ Synced messages instantly for thread $threadId")
                } catch (e: Exception) {
                    Log.e(TAG, "Error syncing after send — ContentObserver will pick it up", e)
                }
            }

            // Return both messageId AND threadId so the caller can open the conversation
            // immediately without an extra content-provider round-trip.
            MessageResult.Success(messageId, threadId)
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied", e)
            MessageResult.Failure(MessageError.PermissionError(Manifest.permission.SEND_SMS, e.message ?: ""))
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid argument", e)
            MessageResult.Failure(MessageError.InvalidNumberError(phoneNumber, e.message ?: ""))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message", e)
            MessageResult.Failure(MessageError.UnknownError(e))
        }
    }
}
