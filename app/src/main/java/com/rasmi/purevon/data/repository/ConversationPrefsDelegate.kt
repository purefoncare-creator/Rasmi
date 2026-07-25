package com.rasmi.purevon.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.rasmi.purevon.data.local.dao.ConversationPreferencesDao
import com.rasmi.purevon.data.local.dao.CachedConversationDao
import com.rasmi.purevon.data.local.entity.ConversationPreferencesEntity
import com.rasmi.purevon.util.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles conversation-level preferences and deletion.
 * Extracted from MessageRepositoryImpl to reduce class size.
 */
internal class ConversationPrefsDelegate(
    private val context: Context,
    private val conversationPreferencesDao: ConversationPreferencesDao,
    private val cachedConversationDao: CachedConversationDao,
    private val syncDelegate: MessageSyncDelegate
) {
    companion object {
        private const val TAG = "MessageRepository"
    }

    suspend fun deleteThread(threadId: Long) = withContext(Dispatchers.IO) {
        try {
            // Delete from system database
            context.contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString())
            )

            // Delete MMS messages too
            context.contentResolver.delete(
                Uri.parse("content://mms"),
                "thread_id = ?",
                arrayOf(threadId.toString())
            )

            // Delete local preferences
            conversationPreferencesDao.deletePreferences(threadId)

            // Remove only this conversation from cache for instant, flash-free UI update
            cachedConversationDao.deleteByThreadId(threadId)

            DebugLogger.d(TAG, "✅ Deleted thread $threadId successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deleting thread $threadId", e)
            throw e
        }
    }

    // ✅ FIX 2.3: Added insert-or-update + cache invalidation + sync (like setConversationArchived)
    suspend fun setConversationPinned(threadId: Long, isPinned: Boolean) {
        val prefs = conversationPreferencesDao.getPreferences(threadId)
        if (prefs == null) {
            conversationPreferencesDao.insertPreferences(
                ConversationPreferencesEntity(systemThreadId = threadId, isPinned = isPinned)
            )
        } else {
            conversationPreferencesDao.updatePinned(threadId, isPinned)
        }
        syncDelegate.invalidateAllCaches()
        syncDelegate.syncConversations()
    }

    // ✅ FIX 2.3: Added insert-or-update + cache invalidation + sync (like setConversationArchived)
    suspend fun setConversationMuted(threadId: Long, isMuted: Boolean) {
        val prefs = conversationPreferencesDao.getPreferences(threadId)
        if (prefs == null) {
            conversationPreferencesDao.insertPreferences(
                ConversationPreferencesEntity(systemThreadId = threadId, isMuted = isMuted)
            )
        } else {
            conversationPreferencesDao.updateMuted(threadId, isMuted)
        }
        syncDelegate.invalidateAllCaches()
        syncDelegate.syncConversations()
    }

    suspend fun setConversationArchived(threadId: Long, isArchived: Boolean) {
        try {
            // Check if preferences exist
            val prefs = conversationPreferencesDao.getPreferences(threadId)
            if (prefs == null) {
                // Insert new preferences
                conversationPreferencesDao.insertPreferences(
                    ConversationPreferencesEntity(
                        systemThreadId = threadId,
                        isArchived = isArchived
                    )
                )
            } else {
                // Update existing
                conversationPreferencesDao.updateArchived(threadId, isArchived)
            }

            DebugLogger.d(TAG, "✅ ${if (isArchived) "Archived" else "Unarchived"} thread $threadId")

            // Invalidate cache immediately
            syncDelegate.invalidateAllCaches()

            // Reload conversations in background
            syncDelegate.syncConversations()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error archiving thread $threadId", e)
            throw e
        }
    }

    suspend fun deleteConversation(threadId: Long) = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString())
            )
            context.contentResolver.delete(
                Telephony.Mms.CONTENT_URI,
                "${Telephony.Mms.THREAD_ID} = ?",
                arrayOf(threadId.toString())
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deleting conversation $threadId", e)
            throw e
        }
        Unit
    }

    suspend fun archiveConversation(threadId: Long) = withContext(Dispatchers.IO) {
        setConversationArchived(threadId, true)
    }
}
