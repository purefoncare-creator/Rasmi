package com.rasmi.purevon.data.local.dao

import androidx.room.*
import com.rasmi.purevon.data.local.entity.MessageCategory
import com.rasmi.purevon.data.local.entity.MessageMetadataEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for message metadata only (not message data itself)
 */
@Dao
interface MessageMetadataDao {
    
    @Query("SELECT * FROM message_metadata WHERE systemMessageId = :messageId")
    suspend fun getMetadata(messageId: Long): MessageMetadataEntity?
    
    @Query("SELECT * FROM message_metadata WHERE systemMessageId IN (:messageIds)")
    suspend fun getMetadataForMessages(messageIds: List<Long>): List<MessageMetadataEntity>
    
    @Query("SELECT * FROM message_metadata WHERE systemMessageId = :messageId")
    fun getMetadataFlow(messageId: Long): Flow<MessageMetadataEntity?>
    
    @Query("SELECT * FROM message_metadata WHERE category = :category")
    fun getMetadataByCategory(category: MessageCategory): Flow<List<MessageMetadataEntity>>
    
    @Query("SELECT * FROM message_metadata WHERE category = :category")
    suspend fun getMetadataByCategorySuspend(category: MessageCategory): List<MessageMetadataEntity>
    
    @Query("SELECT * FROM message_metadata WHERE spamScore > :threshold")
    fun getSpamMessagesMetadata(threshold: Float = 0.5f): Flow<List<MessageMetadataEntity>>
    
    @Query("SELECT * FROM message_metadata WHERE isStarred = 1")
    fun getStarredMessagesMetadata(): Flow<List<MessageMetadataEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: MessageMetadataEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadataList(metadataList: List<MessageMetadataEntity>)
    
    @Update
    suspend fun updateMetadata(metadata: MessageMetadataEntity)
    
    @Query("DELETE FROM message_metadata WHERE systemMessageId = :messageId")
    suspend fun deleteMetadata(messageId: Long)
    
    @Query("UPDATE message_metadata SET spamScore = :score, category = :category, updatedAt = :timestamp WHERE systemMessageId = :messageId")
    suspend fun updateSpamInfo(messageId: Long, score: Float, category: MessageCategory, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE message_metadata SET isStarred = :isStarred, updatedAt = :timestamp WHERE systemMessageId = :messageId")
    suspend fun updateStarred(messageId: Long, isStarred: Boolean, timestamp: Long = System.currentTimeMillis())
    
    @Query("DELETE FROM message_metadata")
    suspend fun deleteAll()
    
    @Query("SELECT * FROM message_metadata")
    fun getAllMetadata(): Flow<List<MessageMetadataEntity>>
}
