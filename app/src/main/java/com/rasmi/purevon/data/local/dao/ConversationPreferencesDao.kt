package com.rasmi.purevon.data.local.dao

import androidx.room.*
import com.rasmi.purevon.data.local.entity.ConversationPreferencesEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for conversation preferences (thread_id from system)
 */
@Dao
interface ConversationPreferencesDao {
    
    @Query("SELECT * FROM conversation_preferences WHERE systemThreadId = :threadId")
    suspend fun getPreferences(threadId: Long): ConversationPreferencesEntity?
    
    @Query("SELECT * FROM conversation_preferences WHERE systemThreadId IN (:threadIds)")
    suspend fun getPreferencesForThreads(threadIds: List<Long>): List<ConversationPreferencesEntity>
    
    @Query("SELECT * FROM conversation_preferences WHERE systemThreadId = :threadId")
    fun getPreferencesFlow(threadId: Long): Flow<ConversationPreferencesEntity?>
    
    @Query("SELECT * FROM conversation_preferences WHERE isPinned = 1 ORDER BY updatedAt DESC")
    fun getPinnedConversations(): Flow<List<ConversationPreferencesEntity>>
    
    @Query("SELECT * FROM conversation_preferences WHERE isMuted = 1")
    fun getMutedConversations(): Flow<List<ConversationPreferencesEntity>>
    
    @Query("SELECT * FROM conversation_preferences WHERE isArchived = 1")
    fun getArchivedConversations(): Flow<List<ConversationPreferencesEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreferences(preferences: ConversationPreferencesEntity)
    
    @Update
    suspend fun updatePreferences(preferences: ConversationPreferencesEntity)
    
    @Query("DELETE FROM conversation_preferences WHERE systemThreadId = :threadId")
    suspend fun deletePreferences(threadId: Long)
    
    @Query("UPDATE conversation_preferences SET isPinned = :isPinned, updatedAt = :timestamp WHERE systemThreadId = :threadId")
    suspend fun updatePinned(threadId: Long, isPinned: Boolean, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE conversation_preferences SET isMuted = :isMuted, updatedAt = :timestamp WHERE systemThreadId = :threadId")
    suspend fun updateMuted(threadId: Long, isMuted: Boolean, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE conversation_preferences SET isArchived = :isArchived, updatedAt = :timestamp WHERE systemThreadId = :threadId")
    suspend fun updateArchived(threadId: Long, isArchived: Boolean, timestamp: Long = System.currentTimeMillis())
    
    @Query("DELETE FROM conversation_preferences")
    suspend fun deleteAll()
    
    @Query("SELECT * FROM conversation_preferences")
    fun getAllPreferences(): Flow<List<ConversationPreferencesEntity>>
}
