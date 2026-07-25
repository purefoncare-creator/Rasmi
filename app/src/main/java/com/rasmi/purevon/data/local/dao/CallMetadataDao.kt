package com.rasmi.purevon.data.local.dao

import androidx.room.*
import com.rasmi.purevon.data.local.entity.CallMetadataEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for call metadata only (not call data itself)
 */
@Dao
interface CallMetadataDao {
    
    @Query("SELECT * FROM call_metadata WHERE systemCallLogId = :callLogId")
    suspend fun getMetadata(callLogId: Long): CallMetadataEntity?
    
    @Query("SELECT * FROM call_metadata WHERE systemCallLogId = :callLogId")
    fun getMetadataFlow(callLogId: Long): Flow<CallMetadataEntity?>
    
    @Query("SELECT * FROM call_metadata WHERE isMarkedAsSpam = 1")
    fun getSpamCallsMetadata(): Flow<List<CallMetadataEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: CallMetadataEntity)
    
    @Update
    suspend fun updateMetadata(metadata: CallMetadataEntity)
    
    @Query("DELETE FROM call_metadata WHERE systemCallLogId = :callLogId")
    suspend fun deleteMetadata(callLogId: Long)
    
    @Query("UPDATE call_metadata SET notes = :notes, updatedAt = :timestamp WHERE systemCallLogId = :callLogId")
    suspend fun updateNotes(callLogId: Long, notes: String?, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE call_metadata SET spamScore = :score, isMarkedAsSpam = :isSpam, updatedAt = :timestamp WHERE systemCallLogId = :callLogId")
    suspend fun updateSpamStatus(callLogId: Long, score: Float, isSpam: Boolean, timestamp: Long = System.currentTimeMillis())
    
    @Query("DELETE FROM call_metadata")
    suspend fun deleteAll()

    @Query("DELETE FROM call_metadata WHERE systemCallLogId IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
    
    @Query("SELECT * FROM call_metadata")
    fun getAllMetadata(): Flow<List<CallMetadataEntity>>

    @Query("SELECT * FROM call_metadata")
    suspend fun getAllMetadataSync(): List<CallMetadataEntity>

    @Query("SELECT systemCallLogId FROM call_metadata WHERE spamScore > 0.5 OR isMarkedAsSpam = 1")
    suspend fun getSpamCallIdsSync(): List<Long>
}
