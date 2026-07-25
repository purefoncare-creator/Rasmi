package com.rasmi.purevon.data.local.dao

import androidx.room.*
import com.rasmi.purevon.data.local.entity.ScheduledMessageEntity
import com.rasmi.purevon.data.local.entity.ScheduleStatus
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Scheduled Messages
 */
@Dao
interface ScheduledMessageDao {
    
    @Query("SELECT * FROM scheduled_messages ORDER BY scheduledTime ASC")
    fun getAllScheduledMessages(): Flow<List<ScheduledMessageEntity>>
    
    @Query("SELECT * FROM scheduled_messages WHERE status = :status ORDER BY scheduledTime ASC")
    fun getMessagesByStatus(status: ScheduleStatus): Flow<List<ScheduledMessageEntity>>
    
    @Query("SELECT * FROM scheduled_messages WHERE status = 'PENDING' ORDER BY scheduledTime ASC")
    fun getPendingMessages(): Flow<List<ScheduledMessageEntity>>
    
    @Query("SELECT * FROM scheduled_messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: Long): ScheduledMessageEntity?
    
    @Query("SELECT * FROM scheduled_messages WHERE id = :id LIMIT 1")
    fun getMessageByIdFlow(id: Long): Flow<ScheduledMessageEntity?>
    
    @Query("SELECT * FROM scheduled_messages WHERE scheduledTime <= :timestamp AND status = 'PENDING'")
    suspend fun getMessagesToSend(timestamp: Long = System.currentTimeMillis()): List<ScheduledMessageEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ScheduledMessageEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<ScheduledMessageEntity>)
    
    @Update
    suspend fun update(message: ScheduledMessageEntity)
    
    @Delete
    suspend fun delete(message: ScheduledMessageEntity)
    
    @Query("DELETE FROM scheduled_messages WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("UPDATE scheduled_messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ScheduleStatus)
    
    @Query("DELETE FROM scheduled_messages WHERE status = 'SENT' OR status = 'CANCELLED'")
    suspend fun deleteCompletedMessages()
    
    @Query("DELETE FROM scheduled_messages")
    suspend fun deleteAll()
    
    @Query("SELECT COUNT(*) FROM scheduled_messages WHERE status = 'PENDING'")
    fun getPendingCount(): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM scheduled_messages WHERE status = 'PENDING'")
    suspend fun getPendingCountSync(): Int
    
    @Query("SELECT * FROM scheduled_messages WHERE recipient = :recipient AND status = 'PENDING' ORDER BY scheduledTime ASC")
    suspend fun getPendingByRecipient(recipient: String): List<ScheduledMessageEntity>

    // ✅ FIX M36: Check for existing pending entry at specific time to prevent duplicates
    @Query("SELECT * FROM scheduled_messages WHERE recipient = :recipient AND scheduledTime = :scheduledTime AND status = 'PENDING' LIMIT 1")
    suspend fun getPendingForRecipient(recipient: String, scheduledTime: Long): ScheduledMessageEntity?
}
