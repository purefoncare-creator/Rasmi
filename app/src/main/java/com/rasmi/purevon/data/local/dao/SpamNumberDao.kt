package com.rasmi.purevon.data.local.dao

import androidx.room.*
import com.rasmi.purevon.data.local.entity.SpamNumberEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Spam Numbers
 */
@Dao
interface SpamNumberDao {
    
    @Query("SELECT * FROM spam_numbers ORDER BY spamScore DESC")
    fun getAllSpamNumbers(): Flow<List<SpamNumberEntity>>
    
    @Query("SELECT * FROM spam_numbers WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun getSpamNumberByPhone(phoneNumber: String): SpamNumberEntity?
    
    @Query("SELECT * FROM spam_numbers WHERE spamScore >= :minScore ORDER BY spamScore DESC")
    fun getHighSpamNumbers(minScore: Float = 0.7f): Flow<List<SpamNumberEntity>>
    
    @Query("SELECT * FROM spam_numbers WHERE isWhitelisted = 1")
    fun getWhitelistedNumbers(): Flow<List<SpamNumberEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpamNumber(spamNumber: SpamNumberEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpamNumbers(spamNumbers: List<SpamNumberEntity>)
    
    @Update
    suspend fun updateSpamNumber(spamNumber: SpamNumberEntity)
    
    @Query("UPDATE spam_numbers SET reportCount = reportCount + 1, lastReportedAt = :timestamp WHERE phoneNumber = :phoneNumber")
    suspend fun incrementReportCount(phoneNumber: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE spam_numbers SET isWhitelisted = :isWhitelisted WHERE phoneNumber = :phoneNumber")
    suspend fun setWhitelisted(phoneNumber: String, isWhitelisted: Boolean)
    
    @Delete
    suspend fun deleteSpamNumber(spamNumber: SpamNumberEntity)
    
    @Query("DELETE FROM spam_numbers WHERE phoneNumber = :phoneNumber")
    suspend fun deleteSpamNumberByPhone(phoneNumber: String)
    
    @Query("DELETE FROM spam_numbers WHERE lastReportedAt < :beforeTimestamp")
    suspend fun deleteOldSpamNumbers(beforeTimestamp: Long)
}
