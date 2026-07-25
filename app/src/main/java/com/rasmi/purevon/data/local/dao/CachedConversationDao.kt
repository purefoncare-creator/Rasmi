package com.rasmi.purevon.data.local.dao

import androidx.room.*
import com.rasmi.purevon.data.local.entity.CachedConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedConversationDao {
    @Query("SELECT * FROM cached_conversations ORDER BY isPinned DESC, lastMessageTimestamp DESC")
    fun getAllConversations(): Flow<List<CachedConversationEntity>>

    @Query("SELECT * FROM cached_conversations WHERE isArchived = 1 ORDER BY lastMessageTimestamp DESC")
    fun getArchivedConversations(): Flow<List<CachedConversationEntity>>

    @Query("SELECT * FROM cached_conversations WHERE lastMessage LIKE '%' || :query || '%' OR contactName LIKE '%' || :query || '%' OR phoneNumber LIKE '%' || :query || '%' ORDER BY lastMessageTimestamp DESC")
    fun searchConversations(query: String): Flow<List<CachedConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(conversations: List<CachedConversationEntity>)

    @Query("DELETE FROM cached_conversations")
    suspend fun clearAll()
    
    @Query("DELETE FROM cached_conversations WHERE threadId = :threadId")
    suspend fun deleteByThreadId(threadId: Long)

    @Query("SELECT COUNT(*) FROM cached_conversations")
    suspend fun getCount(): Int

    @Query("SELECT DISTINCT phoneNumber FROM cached_conversations WHERE phoneNumber IS NOT NULL AND phoneNumber != ''")
    suspend fun getAllUniquePhoneNumbers(): List<String>

    @Query("UPDATE cached_conversations SET contactName = :contactName WHERE phoneNumber = :phoneNumber")
    suspend fun updateContactName(phoneNumber: String, contactName: String?)

    @Query("UPDATE cached_conversations SET unreadCount = 0 WHERE threadId = :threadId")
    suspend fun markThreadAsRead(threadId: Long)
}
