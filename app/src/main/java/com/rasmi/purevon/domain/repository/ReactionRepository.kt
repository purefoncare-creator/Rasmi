package com.rasmi.purevon.domain.repository

import com.rasmi.purevon.domain.model.MessageReaction
import com.rasmi.purevon.domain.model.ReactionSummary
import kotlinx.coroutines.flow.Flow

/**
 * Repository for Message Reactions
 */
interface ReactionRepository {
    
    /**
     * Add reaction to message
     */
    suspend fun addReaction(messageId: Long, emoji: String): Result<Unit>
    
    /**
     * Remove reaction from message
     */
    suspend fun removeReaction(messageId: Long): Result<Unit>
    
    /**
     * Toggle reaction (add if not exists, remove if exists, or change)
     */
    suspend fun toggleReaction(messageId: Long, emoji: String): Result<Unit>
    
    /**
     * Get all reactions for a message
     */
    suspend fun getReactions(messageId: Long): List<MessageReaction>
    
    /**
     * Get reactions with Flow
     */
    fun getReactionsFlow(messageId: Long): Flow<List<MessageReaction>>
    
    /**
     * Get reaction summary (aggregated)
     */
    suspend fun getReactionSummary(messageId: Long): ReactionSummary
    
    /**
     * Get reaction summary with Flow
     */
    fun getReactionSummaryFlow(messageId: Long): Flow<ReactionSummary>
    
    /**
     * Get user's reaction for a message
     */
    suspend fun getUserReaction(messageId: Long): String?
    
    /**
     * Get messages with specific reaction
     */
    suspend fun getMessagesWithReaction(emoji: String, limit: Int = 100): List<Long>
    
    /**
     * Get most used reactions by user
     */
    suspend fun getMostUsedReactions(): List<Pair<String, Int>>
}
