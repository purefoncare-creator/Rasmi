package com.rasmi.purevon.data.local.dao

import androidx.room.*
import com.rasmi.purevon.data.local.entity.ConversationSettingsEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for conversation settings
 */
@Dao
interface ConversationSettingsDao {
    
    @Query("SELECT * FROM conversation_settings WHERE threadId = :threadId")
    fun getSettingsFlow(threadId: Long): Flow<ConversationSettingsEntity?>
    
    @Query("SELECT * FROM conversation_settings WHERE threadId = :threadId")
    suspend fun getSettings(threadId: Long): ConversationSettingsEntity?
    
    @Query("SELECT * FROM conversation_settings WHERE isPinned = 1 ORDER BY pinnedAt DESC")
    fun getPinnedConversations(): Flow<List<ConversationSettingsEntity>>
    
    @Query("SELECT * FROM conversation_settings WHERE isArchived = 1 ORDER BY archivedAt DESC")
    fun getArchivedConversations(): Flow<List<ConversationSettingsEntity>>
    
    @Query("SELECT * FROM conversation_settings WHERE isMuted = 1")
    fun getMutedConversations(): Flow<List<ConversationSettingsEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(settings: ConversationSettingsEntity)
    
    @Query("UPDATE conversation_settings SET isPinned = :isPinned, pinnedAt = :pinnedAt WHERE threadId = :threadId")
    suspend fun updatePinned(threadId: Long, isPinned: Boolean, pinnedAt: Long?)
    
    @Query("UPDATE conversation_settings SET isMuted = :isMuted, mutedUntil = :mutedUntil WHERE threadId = :threadId")
    suspend fun updateMuted(threadId: Long, isMuted: Boolean, mutedUntil: Long?)
    
    @Query("UPDATE conversation_settings SET isArchived = :isArchived, archivedAt = :archivedAt WHERE threadId = :threadId")
    suspend fun updateArchived(threadId: Long, isArchived: Boolean, archivedAt: Long?)
    
    @Query("DELETE FROM conversation_settings WHERE threadId = :threadId")
    suspend fun delete(threadId: Long)
    
    @Query("DELETE FROM conversation_settings")
    suspend fun deleteAll()
    
    /**
     * Check if conversation is muted and still within mute period
     */
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM conversation_settings 
            WHERE threadId = :threadId 
            AND isMuted = 1 
            AND (mutedUntil IS NULL OR mutedUntil > :currentTime)
        )
    """)
    suspend fun isMuted(threadId: Long, currentTime: Long): Boolean
    
    /**
     * Unmute conversations where mute period has expired
     */
    @Query("""
        UPDATE conversation_settings 
        SET isMuted = 0, mutedUntil = NULL 
        WHERE isMuted = 1 
        AND mutedUntil IS NOT NULL 
        AND mutedUntil <= :currentTime
    """)
    suspend fun expireMutes(currentTime: Long)
}
