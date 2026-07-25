package com.rasmi.purevon.data.repository

import com.rasmi.purevon.data.local.dao.ConversationPreferencesDao
import com.rasmi.purevon.data.local.entity.ConversationPreferencesEntity
import com.rasmi.purevon.domain.repository.ConversationPreferencesRepository
import com.rasmi.purevon.domain.repository.MessageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Implementation of [ConversationPreferencesRepository].
 * Encapsulates all Room DAO access for conversation preferences.
 */
class ConversationPreferencesRepositoryImpl @Inject constructor(
    private val conversationPreferencesDao: ConversationPreferencesDao,
    private val messageRepository: MessageRepository
) : ConversationPreferencesRepository {

    override suspend fun togglePin(threadId: Long): Boolean = withContext(Dispatchers.IO) {
        val settings = conversationPreferencesDao.getPreferences(threadId)
        val isPinned = settings?.isPinned != true

        if (settings == null) {
            conversationPreferencesDao.insertPreferences(
                ConversationPreferencesEntity(systemThreadId = threadId, isPinned = isPinned)
            )
        } else {
            conversationPreferencesDao.updatePinned(threadId, isPinned)
        }

        messageRepository.notifyConversationsChanged()
        isPinned
    }

    override suspend fun setMuted(threadId: Long, muted: Boolean, mutedUntil: Long?): Unit = withContext(Dispatchers.IO) {
        val settings = conversationPreferencesDao.getPreferences(threadId)

        if (settings == null) {
            conversationPreferencesDao.insertPreferences(
                ConversationPreferencesEntity(systemThreadId = threadId, isMuted = muted)
            )
        } else {
            conversationPreferencesDao.updateMuted(threadId, muted)
        }
        
        // ✅ FIX 2.2: Notify UI of change (was missing, unlike togglePin/toggleArchive)
        messageRepository.notifyConversationsChanged()
    }

    override suspend fun isMuted(threadId: Long): Boolean = withContext(Dispatchers.IO) {
        conversationPreferencesDao.getPreferences(threadId)?.isMuted ?: false
    }

    override suspend fun toggleArchive(threadId: Long): Boolean = withContext(Dispatchers.IO) {
        val settings = conversationPreferencesDao.getPreferences(threadId)
        val isArchived = settings?.isArchived != true

        if (settings == null) {
            conversationPreferencesDao.insertPreferences(
                ConversationPreferencesEntity(systemThreadId = threadId, isArchived = isArchived)
            )
        } else {
            conversationPreferencesDao.updateArchived(threadId, isArchived)
        }

        messageRepository.notifyConversationsChanged()
        isArchived
    }
}
