package com.rasmi.purevon.data.local.dao

import androidx.room.*
import com.rasmi.purevon.data.local.entity.CachedMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedMessageDao {
    @Query("SELECT * FROM cached_messages WHERE threadId = :threadId ORDER BY timestamp ASC")
    fun getMessagesByThread(threadId: Long): Flow<List<CachedMessageEntity>>
    
    @Query("SELECT * FROM cached_messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<CachedMessageEntity>>

    @Query("SELECT COUNT(*) FROM cached_messages")
    fun getTotalMessageCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM cached_messages WHERE type = :type")
    fun getMessageCountByType(type: Int): Flow<Int>
    
    @Query("SELECT * FROM cached_messages WHERE threadId = :threadId ORDER BY timestamp ASC")
    suspend fun getMessagesByThreadSync(threadId: Long): List<CachedMessageEntity>

    @Query("SELECT * FROM cached_messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: Long): CachedMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<CachedMessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: CachedMessageEntity)

    /** Upsert a single message without touching the rest of the thread — O(1). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: CachedMessageEntity)

    @Query("DELETE FROM cached_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM cached_messages WHERE threadId = :threadId")
    suspend fun deleteByThreadId(threadId: Long)
    
    @Transaction
    suspend fun updateMessagesForThread(threadId: Long, messages: List<CachedMessageEntity>) {
        deleteByThreadId(threadId)
        insertAll(messages)
    }

    @Query("UPDATE cached_messages SET contactName = :contactName WHERE phoneNumber = :phoneNumber")
    suspend fun updateContactNameByPhoneNumber(phoneNumber: String, contactName: String?)

    @Query("UPDATE cached_messages SET category = :category WHERE id = :messageId")
    suspend fun updateCategory(messageId: Long, category: com.rasmi.purevon.data.local.entity.MessageCategory)

    @Query("SELECT DISTINCT phoneNumber FROM cached_messages WHERE phoneNumber IS NOT NULL AND phoneNumber != ''")
    suspend fun getAllUniquePhoneNumbers(): List<String>

    @Query("DELETE FROM cached_messages")
    suspend fun clearAll()

    @Query("SELECT id FROM cached_messages")
    suspend fun getAllIds(): List<Long>

    @Query("SELECT * FROM cached_messages WHERE id IN (:ids)")
    suspend fun getMessagesByIds(ids: List<Long>): List<CachedMessageEntity>

    @Query("DELETE FROM cached_messages WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE cached_messages SET isRead = 1 WHERE threadId = :threadId")
    suspend fun markThreadAsRead(threadId: Long)

    @Query("UPDATE cached_messages SET isRead = 1 WHERE id = :messageId")
    suspend fun markMessageAsRead(messageId: Long)
}
