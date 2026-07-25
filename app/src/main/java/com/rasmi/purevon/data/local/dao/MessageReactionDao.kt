package com.rasmi.purevon.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Transaction
import com.rasmi.purevon.data.local.entity.MessageReactionEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Message Reactions
 */
@Dao
interface MessageReactionDao {
    
    /**
     * Add reaction to a message
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReaction(reaction: MessageReactionEntity): Long
    
    /**
     * Remove reaction
     */
    @Delete
    suspend fun deleteReaction(reaction: MessageReactionEntity)
    
    /**
     * Remove user's reaction from message
     */
    @Query("DELETE FROM message_reactions WHERE message_id = :messageId AND user_id = :userId")
    suspend fun removeUserReaction(messageId: Long, userId: String = "me")
    
    /**
     * Get all reactions for a message
     */
    @Query("SELECT * FROM message_reactions WHERE message_id = :messageId ORDER BY timestamp ASC")
    suspend fun getReactionsForMessage(messageId: Long): List<MessageReactionEntity>
    
    /**
     * Get reactions with Flow (reactive)
     */
    @Query("SELECT * FROM message_reactions WHERE message_id = :messageId ORDER BY timestamp ASC")
    fun getReactionsForMessageFlow(messageId: Long): Flow<List<MessageReactionEntity>>
    
    /**
     * Get user's reaction for a message
     */
    @Query("SELECT * FROM message_reactions WHERE message_id = :messageId AND user_id = :userId LIMIT 1")
    suspend fun getUserReaction(messageId: Long, userId: String = "me"): MessageReactionEntity?
    
    /**
     * Get reaction count by emoji for a message
     */
    @Query("""
        SELECT emoji, COUNT(*) as count 
        FROM message_reactions 
        WHERE message_id = :messageId 
        GROUP BY emoji
    """)
    suspend fun getReactionCounts(messageId: Long): Map<@MapColumn(columnName = "emoji") String, @MapColumn(columnName = "count") Int>
    
    /**
     * Get total reaction count for a message
     */
    @Query("SELECT COUNT(*) FROM message_reactions WHERE message_id = :messageId")
    suspend fun getTotalReactionCount(messageId: Long): Int
    
    /**
     * Toggle reaction (add if not exists, remove if exists)
     */
    @Transaction
    suspend fun toggleReaction(messageId: Long, emoji: String, userId: String = "me") {
        val existing = getUserReaction(messageId, userId)
        
        if (existing != null) {
            if (existing.emoji == emoji) {
                // Same emoji - remove it
                deleteReaction(existing)
            } else {
                // Different emoji - replace it
                deleteReaction(existing)
                insertReaction(
                    MessageReactionEntity(
                        messageId = messageId,
                        emoji = emoji,
                        userId = userId
                    )
                )
            }
        } else {
            // No reaction - add it
            insertReaction(
                MessageReactionEntity(
                    messageId = messageId,
                    emoji = emoji,
                    userId = userId
                )
            )
        }
    }
    
    /**
     * Get messages with specific reaction
     */
    @Query("""
        SELECT DISTINCT message_id 
        FROM message_reactions 
        WHERE emoji = :emoji
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun getMessagesWithReaction(emoji: String, limit: Int = 100): List<Long>
    
    /**
     * Delete all reactions for a message
     */
    @Query("DELETE FROM message_reactions WHERE message_id = :messageId")
    suspend fun deleteAllReactionsForMessage(messageId: Long)
    
    /**
     * Get reaction statistics
     */
    @Query("""
        SELECT emoji, COUNT(*) as count 
        FROM message_reactions 
        WHERE user_id = :userId
        GROUP BY emoji 
        ORDER BY count DESC
    """)
    suspend fun getUserReactionStats(userId: String = "me"): Map<@MapColumn(columnName = "emoji") String, @MapColumn(columnName = "count") Int>
}
