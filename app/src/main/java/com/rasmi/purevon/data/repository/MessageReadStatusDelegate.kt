package com.rasmi.purevon.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.rasmi.purevon.util.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles marking messages and threads as read (SMS + MMS).
 * Extracted from MessageRepositoryImpl to reduce class size.
 */
internal class MessageReadStatusDelegate(
    private val context: Context,
    private val syncDelegate: MessageSyncDelegate,
    private val cachedConversationDao: com.rasmi.purevon.data.local.dao.CachedConversationDao,
    private val cachedMessageDao: com.rasmi.purevon.data.local.dao.CachedMessageDao
) {
    companion object {
        private const val TAG = "MessageRepository"
    }

    /**
     * تعليم رسالة معينة كمقروءة
     */
    suspend fun markMessageAsRead(messageId: Long): Unit = withContext(Dispatchers.IO) {
        try {
            // Optimistically update cached message immediately
            cachedMessageDao.markMessageAsRead(messageId)

            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            }

            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms._ID} = ?",
                arrayOf(messageId.toString())
            )
        } catch (e: Exception) {
            Log.e("MessageRepository", "Error marking message as read: $messageId", e)
        }
    }

    /**
     * تعليم كل رسائل محادثة معينة كمقروءة
     */
    suspend fun markThreadAsRead(threadId: Long): Unit = withContext(Dispatchers.IO) {
        try {
            // Optimistically update local Room database cache immediately
            cachedConversationDao.markThreadAsRead(threadId)
            cachedMessageDao.markThreadAsRead(threadId)

            // Update SMS messages
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            }

            val smsUpdated = context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString())
            )

            // Update MMS messages
            val mmsValues = ContentValues().apply {
                put("read", 1)
                put("seen", 1)
            }

            val mmsUpdated = context.contentResolver.update(
                Uri.parse("content://mms"),
                mmsValues,
                "thread_id = ?",
                arrayOf(threadId.toString())
            )

            DebugLogger.d(TAG, "✅ Marked thread $threadId as read: $smsUpdated SMS, $mmsUpdated MMS")

            // ✅ OPTIMIZED: Smart invalidation - only sync affected thread
            syncDelegate.syncMessagesBackground(threadId)
            // ContentObserver will handle conversation list update automatically
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error marking thread as read: $threadId", e)
        }
    }
}
