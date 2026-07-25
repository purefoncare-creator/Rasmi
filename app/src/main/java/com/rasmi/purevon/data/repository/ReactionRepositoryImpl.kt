package com.rasmi.purevon.data.repository

import android.util.Log
import com.rasmi.purevon.data.local.dao.MessageReactionDao
import com.rasmi.purevon.data.local.entity.MessageReactionEntity
import com.rasmi.purevon.data.local.entity.MessageReactionSummary
import com.rasmi.purevon.domain.model.MessageReaction
import com.rasmi.purevon.domain.model.ReactionSummary
import com.rasmi.purevon.domain.repository.ReactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ReactionRepository
 */
@Singleton
class ReactionRepositoryImpl @Inject constructor(
    private val reactionDao: MessageReactionDao
) : ReactionRepository {
    
    companion object {
        private const val TAG = "ReactionRepository"
        private const val USER_ID = "me" // Current user
    }
    
    override suspend fun addReaction(messageId: Long, emoji: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val reaction = MessageReactionEntity(
                messageId = messageId,
                emoji = emoji,
                userId = USER_ID
            )
            
            reactionDao.insertReaction(reaction)
            Log.d(TAG, "✅ Added reaction $emoji to message $messageId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding reaction", e)
            Result.failure(e)
        }
    }
    
    override suspend fun removeReaction(messageId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            reactionDao.removeUserReaction(messageId, USER_ID)
            Log.d(TAG, "❌ Removed reaction from message $messageId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing reaction", e)
            Result.failure(e)
        }
    }
    
    override suspend fun toggleReaction(messageId: Long, emoji: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            reactionDao.toggleReaction(messageId, emoji, USER_ID)
            Log.d(TAG, "🔄 Toggled reaction $emoji on message $messageId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling reaction", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getReactions(messageId: Long): List<MessageReaction> = withContext(Dispatchers.IO) {
        return@withContext reactionDao.getReactionsForMessage(messageId).map { it.toDomain() }
    }
    
    override fun getReactionsFlow(messageId: Long): Flow<List<MessageReaction>> {
        return reactionDao.getReactionsForMessageFlow(messageId).map { list -> list.map { it.toDomain() } }
    }
    
    override suspend fun getReactionSummary(messageId: Long): ReactionSummary = withContext(Dispatchers.IO) {
        val reactions = reactionDao.getReactionsForMessage(messageId)
        val myReaction = reactions.firstOrNull { it.userId == USER_ID }?.emoji
        
        val reactionCounts = reactions.groupBy { it.emoji }
            .mapValues { it.value.size }
        
        return@withContext ReactionSummary(
            messageId = messageId,
            reactions = reactionCounts,
            myReaction = myReaction,
            totalCount = reactions.size
        )
    }
    
    override fun getReactionSummaryFlow(messageId: Long): Flow<ReactionSummary> {
        return reactionDao.getReactionsForMessageFlow(messageId).map { reactions ->
            val myReaction = reactions.firstOrNull { it.userId == USER_ID }?.emoji
            val reactionCounts = reactions.groupBy { it.emoji }
                .mapValues { it.value.size }
            
            ReactionSummary(
                messageId = messageId,
                reactions = reactionCounts,
                myReaction = myReaction,
                totalCount = reactions.size
            )
        }
    }
    
    private fun MessageReactionEntity.toDomain() = MessageReaction(
        id = id,
        messageId = messageId,
        emoji = emoji,
        timestamp = timestamp,
        userId = userId,
        synced = synced
    )
    
    override suspend fun getUserReaction(messageId: Long): String? = withContext(Dispatchers.IO) {
        return@withContext reactionDao.getUserReaction(messageId, USER_ID)?.emoji
    }
    
    override suspend fun getMessagesWithReaction(emoji: String, limit: Int): List<Long> = withContext(Dispatchers.IO) {
        return@withContext reactionDao.getMessagesWithReaction(emoji, limit)
    }
    
    override suspend fun getMostUsedReactions(): List<Pair<String, Int>> = withContext(Dispatchers.IO) {
        return@withContext reactionDao.getUserReactionStats(USER_ID)
            .toList()
            .sortedByDescending { it.second }
    }
}
